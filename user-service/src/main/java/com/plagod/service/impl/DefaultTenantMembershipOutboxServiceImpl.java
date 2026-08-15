package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.client.TenantMembershipClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import com.plagod.support.StructuredRedactor;
import com.plagod.web.SafeExceptionLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DefaultTenantMembershipOutboxServiceImpl implements DefaultTenantMembershipOutboxService {

    private static final Set<String> SAFE_FAILURE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(
                    Arrays.asList(
                            "eventId",
                            "retryCount",
                            "failureType")));

    private final DefaultTenantMembershipOutboxMapper outboxMapper;
    private final TenantMembershipClient tenantMembershipClient;
    private final String internalToken;

    public DefaultTenantMembershipOutboxServiceImpl(DefaultTenantMembershipOutboxMapper outboxMapper, TenantMembershipClient tenantMembershipClient, @Value("${wifi.internal.token}") String internalToken) {
        this.outboxMapper = outboxMapper;
        this.tenantMembershipClient = tenantMembershipClient;
        this.internalToken = internalToken;
    }

    @Override
    public void dispatchForUser(Long userId) {
        DefaultTenantMembershipOutbox outbox = findByUserId(userId);
        if (outbox == null || "SUCCEEDED".equals(outbox.getStatus())) {
            return;
        }

        DefaultTenantMembershipRequest request = new DefaultTenantMembershipRequest();
        request.setEventId(outbox.getEventId());
        request.setUserId(outbox.getUserId());
        request.setRole(outbox.getRole());

        try {
            ApiResponse<Void> response = tenantMembershipClient.ensureDefaultMembership(internalToken, request);
            if (response == null || response.getCode() != 200) {
                throw new IllegalStateException(
                        "default membership dependency rejected request");
            }
            outboxMapper.update(null, new UpdateWrapper<DefaultTenantMembershipOutbox>()
                    .eq("outbox_id", outbox.getOutboxId())
                    .ne("status", "SUCCEEDED")
                    .set("status", "SUCCEEDED")
                    .set("last_error", null));
        } catch (RuntimeException exception) {
            int retryCount = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
            String error = safeFailureType(exception);
            outboxMapper.update(null, new UpdateWrapper<DefaultTenantMembershipOutbox>()
                    .eq("outbox_id", outbox.getOutboxId())
                    .ne("status", "SUCCEEDED")
                    .set("status", "RETRY")
                    .set("retry_count", retryCount)
                    .set("next_retry_time", LocalDateTime.now())
                    .set("last_error", error));
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("eventId", outbox.getEventId());
            fields.put("retryCount", retryCount);
            fields.put("failureType", error);
            log.warn(
                    "默认租户成员事件投递失败：fields={}，failure={}",
                    StructuredRedactor.redact(
                            fields,
                            SAFE_FAILURE_FIELDS,
                            Collections.<String>emptySet()),
                    SafeExceptionLogFormatter.format(exception));
        }
    }

    @Override
    public boolean isMembershipReady(Long userId) {
        DefaultTenantMembershipOutbox outbox = findByUserId(userId);
        return outbox == null || "SUCCEEDED".equals(outbox.getStatus());
    }

    private DefaultTenantMembershipOutbox findByUserId(Long userId) {
        return outboxMapper.selectOne(
                new QueryWrapper<DefaultTenantMembershipOutbox>().eq("user_id", userId));
    }

    private String safeFailureType(RuntimeException exception) {
        return exception == null
                ? RuntimeException.class.getName()
                : exception.getClass().getName();
    }
}
