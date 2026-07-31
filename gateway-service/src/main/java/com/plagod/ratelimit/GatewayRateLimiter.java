package com.plagod.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class GatewayRateLimiter {

    /*
     * 返回负数表示允许，绝对值是当前窗口剩余毫秒数；
     * 返回正数表示拒绝，值是 Retry-After 对应的剩余毫秒数。
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(
                    "local count = redis.call('INCR', KEYS[1]); " +
                            "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; " +
                            "local ttl = redis.call('PTTL', KEYS[1]); " +
                            "if ttl < 1 then " +
                            "  redis.call('PEXPIRE', KEYS[1], ARGV[1]); " +
                            "  ttl = tonumber(ARGV[1]); " +
                            "end; " +
                            "if count > tonumber(ARGV[2]) then return ttl; end; " +
                            "return -ttl;",
                    Long.class
            );

    private final ReactiveStringRedisTemplate redisTemplate;

    /*
     * LinkedHashMap 使用访问顺序，达到上限时淘汰最久未使用的窗口，
     * 防止 Redis 故障期间攻击来源无限占用 Gateway 内存。
     */
    private final Map<String, LocalWindow> localWindows = new LinkedHashMap<String, LocalWindow>(128, 0.75F, true);

    private final Object localLock = new Object();
    private final AtomicLong lastRedisWarningTime = new AtomicLong(0L);
    private final AtomicLong redisUnavailableUntil = new AtomicLong(0L);
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);
    private final AtomicBoolean redisProbeInProgress = new AtomicBoolean(false);

    @Value("${wifi.rate-limit.redis-enabled:false}")
    private boolean redisEnabled;

    @Value("${wifi.rate-limit.redis-fallback-cooldown-millis:30000}")
    private long redisFallbackCooldownMillis;

    @Value("${wifi.rate-limit.fallback-max-keys:4096}")
    private int fallbackMaxKeys;

    public GatewayRateLimiter(ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider) {

        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public Mono<Decision> acquire(String scene, String subject, int limit, Duration window) {

        long windowMillis = Math.max(1000L, window.toMillis());

        if (limit <= 0) {
            return Mono.just(Decision.rejected(toRetryAfter(windowMillis), true));
        }

        String redisKey = buildKey(scene, subject);

        /*
         * 本地开发默认不启用 Redis。云端设置环境变量后，
         * 相同代码自动切换到分布式限流。
         */
        if (!redisEnabled || redisTemplate == null) {
            return localDecision(redisKey, limit, windowMillis);
        }

        long now = System.currentTimeMillis();

        if (!redisAvailable.get()) {
            if (now < redisUnavailableUntil.get()) {
                return localDecision(redisKey, limit, windowMillis);
            }

            /*
             * Redis 状态未知或熔断期刚结束时，只允许一个请求探测。
             * 其他并发请求立即使用本地限流。
             */
            if (!redisProbeInProgress.compareAndSet(false, true)) {
                return localDecision(redisKey, limit, windowMillis);
            }
        }

        return Mono.defer(() ->
                        redisTemplate.execute(RATE_LIMIT_SCRIPT,
                                        Collections.singletonList(redisKey),
                                        java.util.Arrays.asList(String.valueOf(windowMillis), String.valueOf(limit))
                                ).next()
                                .switchIfEmpty(Mono.error(new IllegalStateException("Redis 限流脚本没有返回结果")))
                                .map(this::decodeRedisResult)
                )
                .doOnNext(decision -> {
                    redisAvailable.set(true);
                    redisUnavailableUntil.set(0L);
                })
                .onErrorResume(exception -> {
                    redisAvailable.set(false);
                    redisUnavailableUntil.set(System.currentTimeMillis() + Math.max(1000L, redisFallbackCooldownMillis));
                    logRedisFallback(exception);
                    return localDecision(redisKey, limit, windowMillis);
                })
                .doFinally(signalType -> redisProbeInProgress.set(false));
    }

    private Mono<Decision> localDecision(String redisKey, int limit, long windowMillis) {
        return Mono.fromSupplier(() -> acquireLocally(redisKey, limit, windowMillis));
    }

    private Decision decodeRedisResult(Long result) {
        if (result == null) {
            throw new IllegalStateException("Redis 限流脚本没有返回结果");
        }

        long remainingMillis = Math.max(1L, Math.abs(result));

        if (result <= 0L) {
            return Decision.allowed(toRetryAfter(remainingMillis), false);
        }

        return Decision.rejected(toRetryAfter(remainingMillis), false);
    }

    private Decision acquireLocally(String key, int limit, long windowMillis) {

        long now = System.currentTimeMillis();

        synchronized (localLock) {
            LocalWindow window = localWindows.get(key);

            if (window == null || now >= window.expireAt) {
                ensureLocalCapacity();

                window = new LocalWindow(now + windowMillis);
                localWindows.put(key, window);
            }

            long retryAfter = toRetryAfter(window.expireAt - now);

            if (window.count >= limit) {
                return Decision.rejected(retryAfter, true);
            }

            window.count++;
            return Decision.allowed(retryAfter, true);
        }
    }

    private void ensureLocalCapacity() {
        int maxKeys = Math.max(64, fallbackMaxKeys);

        while (localWindows.size() >= maxKeys) {
            Iterator<String> iterator = localWindows.keySet().iterator();

            if (!iterator.hasNext()) {
                return;
            }

            iterator.next();
            iterator.remove();
        }
    }

    private String buildKey(String scene, String subject) {
        String cleanScene = StringUtils.hasText(scene) ? scene.replaceAll("[^A-Za-z0-9_-]", "_") : "unknown";

        /*
         * IP、邮箱和手机号都不会直接进入 Redis Key。
         * Gateway 当前主要传 IP 和 userId，统一摘要可以避免身份信息泄露。
         */
        return "wifi:rate-limit:v1:{" + cleanScene + "}:" + sha256(subject);
    }

    private String sha256(String value) {
        String source = StringUtils.hasText(value) ? value : "unknown";

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);

            for (byte current : bytes) {
                result.append(String.format("%02x", current & 0xff));
            }

            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成限流标识摘要", exception);
        }
    }

    private long toRetryAfter(long remainingMillis) {
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    private void logRedisFallback(Throwable exception) {
        long now = System.currentTimeMillis();
        long previous = lastRedisWarningTime.get();

        /*
         * Redis 持续故障时最多每分钟打印一次，避免每个请求都刷错误日志。
         */
        if (now - previous >= 60_000L && lastRedisWarningTime.compareAndSet(previous, now)) {

            log.warn("Redis 限流不可用，Gateway 已降级为有界本地限流: {}", exception.getMessage());
        }
    }

    public static final class Decision {

        private final boolean allowed;
        private final long retryAfterSeconds;
        private final boolean localFallback;

        private Decision(boolean allowed, long retryAfterSeconds, boolean localFallback) {

            this.allowed = allowed;
            this.retryAfterSeconds = retryAfterSeconds;
            this.localFallback = localFallback;
        }

        public static Decision allowed(long retryAfterSeconds, boolean localFallback) {

            return new Decision(true, retryAfterSeconds, localFallback);
        }

        public static Decision rejected(long retryAfterSeconds, boolean localFallback) {

            return new Decision(false, retryAfterSeconds, localFallback);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public boolean isLocalFallback() {
            return localFallback;
        }
    }

    private static final class LocalWindow {

        private final long expireAt;
        private int count;

        private LocalWindow(long expireAt) {
            this.expireAt = expireAt;
        }
    }
}