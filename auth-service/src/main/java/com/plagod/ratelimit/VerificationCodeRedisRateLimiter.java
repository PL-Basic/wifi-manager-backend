package com.plagod.ratelimit;

import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.exception.VerificationCodeRateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class VerificationCodeRedisRateLimiter {

    private static final long RESULT_OFFSET = 1_000_000_000L;


    /*
     * 第一轮只检查所有维度；全部通过后第二轮统一增加。
     * 因此不会出现目标计数增加成功、IP 计数检查失败的半完成状态。
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(
                    "for i = 1, #KEYS do " +
                            "  local limit = tonumber(ARGV[(i - 1) * 2 + 1]); " +
                            "  local duration = tonumber(ARGV[(i - 1) * 2 + 2]); " +
                            "  local current = tonumber(redis.call('GET', KEYS[i]) or '0'); " +
                            "  if current >= limit then " +
                            "    local ttl = redis.call('PTTL', KEYS[i]); " +
                            "    if ttl < 1 then " +
                            "      redis.call('PEXPIRE', KEYS[i], duration); " +
                            "      ttl = duration; " +
                            "    end; " +
                            "    return i * 1000000000 + ttl; " +
                            "  end; " +
                            "end; " +
                            "for i = 1, #KEYS do " +
                            "  local duration = tonumber(ARGV[(i - 1) * 2 + 2]); " +
                            "  local current = redis.call('INCR', KEYS[i]); " +
                            "  if current == 1 then " +
                            "    redis.call('PEXPIRE', KEYS[i], duration); " +
                            "  end; " +
                            "end; " +
                            "return 0;",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final VerificationCodeProperties properties;
    private final AtomicLong lastFallbackWarningTime = new AtomicLong();
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);
    private final AtomicBoolean redisProbeInProgress = new AtomicBoolean(false);
    private final AtomicLong redisUnavailableUntil = new AtomicLong(0L);

    @Value("${wifi.rate-limit.redis-fallback-cooldown-millis:30000}")
    private long redisFallbackCooldownMillis;

    @Value("${wifi.rate-limit.redis-enabled:false}")
    private boolean redisEnabled;

    public VerificationCodeRedisRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider, VerificationCodeProperties properties) {

        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.properties = properties;
    }

    /*
     * 返回 true 表示 Redis 已完成原子限流；
     * 返回 false 表示 Redis 不可用，调用方必须回退数据库检查。
     */
    public boolean acquire(String target, String scene, String sendIp, LocalDateTime now) {

        if (!redisEnabled || redisTemplate == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();

        if (!redisAvailable.get()) {
            if (currentTime < redisUnavailableUntil.get()) {
                return false;
            }

            if (!redisProbeInProgress.compareAndSet(false, true)) {
                return false;
            }
        }
        validateConfiguration();

        List<String> keys = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        String prefix = "wifi:rate-limit:v1:{verification}:";
        String cleanScene = sanitizeScene(scene);
        String targetHash = sha256(normalizeIdentity(target));
        String date = now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);

        addDimension(keys, arguments, prefix + "target-interval:" + cleanScene + ":" + targetHash, 1, Duration.ofSeconds(properties.getTargetIntervalSeconds()).toMillis());

        addDimension(keys, arguments, prefix + "target-day:" + date + ":" + cleanScene + ":" + targetHash, properties.getTargetDailyLimit(), millisUntilTomorrow(now));

        if (StringUtils.hasText(sendIp)) {
            String ipHash = sha256(normalizeIdentity(sendIp));

            addDimension(keys, arguments, prefix + "ip-minute:" + cleanScene + ":" + ipHash, properties.getIpMinuteLimit(), Duration.ofMinutes(1).toMillis());

            addDimension(keys, arguments, prefix + "ip-day:" + date + ":" + cleanScene + ":" + ipHash, properties.getIpDailyLimit(), millisUntilTomorrow(now));
        }

        try {
            Long result = redisTemplate.execute(RATE_LIMIT_SCRIPT, keys, arguments.toArray(new Object[0]));

            if (result == null) {
                throw new IllegalStateException("Redis 验证码限流脚本没有返回结果");
            }

            redisAvailable.set(true);
            redisUnavailableUntil.set(0L);

            if (result == 0L) {
                return true;
            }

            throw decodeRejection(result);
        } catch (VerificationCodeRateLimitException exception) {
            /*
             * 这是 Redis 正常返回的业务拒绝，不得把 Redis 标记为故障。
             */
            throw exception;
        } catch (RuntimeException exception) {
            redisAvailable.set(false);

            redisUnavailableUntil.set(System.currentTimeMillis() + Math.max(1000L, redisFallbackCooldownMillis));

            logFallback(exception);
            return false;
        } finally {
            redisProbeInProgress.set(false);
        }
    }

    private void addDimension(List<String> keys, List<Object> arguments, String key, int limit, long durationMillis) {

        keys.add(key);
        arguments.add(String.valueOf(limit));
        arguments.add(String.valueOf(Math.max(1000L, durationMillis)));
    }

    private VerificationCodeRateLimitException decodeRejection(long result) {
        int dimension = (int) (result / RESULT_OFFSET);
        long remainingMillis = result % RESULT_OFFSET;
        long retryAfter = Math.max(1L, (remainingMillis + 999L) / 1000L);

        if (dimension == 1) {
            return new VerificationCodeRateLimitException("验证码发送太频繁，请稍后再试", retryAfter);
        }

        if (dimension == 2) {
            return new VerificationCodeRateLimitException("今日验证码发送次数已达上限", retryAfter);
        }

        if (dimension == 3) {
            return new VerificationCodeRateLimitException("当前网络验证码发送太频繁，请稍后再试", retryAfter);
        }

        return new VerificationCodeRateLimitException("当前网络验证码请求次数已达上限", retryAfter);
    }

    private long millisUntilTomorrow(LocalDateTime now) {
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();

        return Math.max(1000L, Duration.between(now, tomorrow).toMillis());
    }

    private String sanitizeScene(String scene) {
        return StringUtils.hasText(scene) ? scene.replaceAll("[^A-Za-z0-9_-]", "_") : "unknown";
    }

    private String normalizeIdentity(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);

            for (byte current : bytes) {
                result.append(String.format("%02x", current & 0xff));
            }

            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成验证码限流摘要", exception);
        }
    }

    private void validateConfiguration() {
        if (properties.getTargetIntervalSeconds() <= 0
                || properties.getTargetDailyLimit() <= 0
                || properties.getIpMinuteLimit() <= 0
                || properties.getIpDailyLimit() <= 0) {

            throw new IllegalStateException("验证码限流配置必须大于零");
        }
    }

    private void logFallback(Throwable exception) {
        long now = System.currentTimeMillis();
        long previous = lastFallbackWarningTime.get();

        if (now - previous >= 60_000L && lastFallbackWarningTime.compareAndSet(previous, now)) {

            log.warn("Redis 验证码限流不可用，已回退数据库检查: {}", exception.getMessage());
        }
    }
}