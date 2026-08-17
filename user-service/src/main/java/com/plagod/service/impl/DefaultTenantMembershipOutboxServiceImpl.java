package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.client.TenantMembershipClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import com.plagod.support.StructuredRedactor;
import com.plagod.web.SafeExceptionLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class DefaultTenantMembershipOutboxServiceImpl
        implements DefaultTenantMembershipOutboxService {

    static final int MAX_RETRY_COUNT = 3;
    static final int LEASE_SECONDS = 30;
    private static final int MAX_BATCH_SIZE = 100;
    private static final String DELIVERY_ERROR =
            "TENANT_MEMBERSHIP_DELIVERY_FAILED";
    private static final String RETRY_EXHAUSTED_ERROR =
            "RETRY_LIMIT_EXHAUSTED";
    private static final Set<String> SAFE_FAILURE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(
                    Arrays.asList(
                            "eventId",
                            "retryCount",
                            "failureType")));

    private final DefaultTenantMembershipOutboxMapper outboxMapper;
    private final TenantMembershipClient tenantMembershipClient;
    private final TransactionTemplate transactionTemplate;

    public DefaultTenantMembershipOutboxServiceImpl(
            DefaultTenantMembershipOutboxMapper outboxMapper,
            TenantMembershipClient tenantMembershipClient,
            PlatformTransactionManager transactionManager) {
        this.outboxMapper = outboxMapper;
        this.tenantMembershipClient = tenantMembershipClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void dispatchForUser(Long userId) {
        DefaultTenantMembershipOutbox outbox = findByUserId(userId);
        if (outbox != null) {
            dispatchOne(outbox.getOutboxId());
        }
    }

    @Override
    public void dispatchPending(int batchSize) {
        int boundedBatchSize = Math.max(
                1,
                Math.min(batchSize, MAX_BATCH_SIZE));
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.execute(status -> {
            outboxMapper.finalizeExhausted(
                    now,
                    MAX_RETRY_COUNT,
                    RETRY_EXHAUSTED_ERROR);
            return null;
        });
        List<Long> outboxIds = outboxMapper.selectDispatchableIds(
                now,
                MAX_RETRY_COUNT,
                boundedBatchSize);
        for (Long outboxId : outboxIds) {
            dispatchOne(outboxId);
        }
    }

    @Override
    public boolean isMembershipReady(Long userId) {
        DefaultTenantMembershipOutbox outbox = findByUserId(userId);
        return outbox == null || "SUCCEEDED".equals(outbox.getStatus());
    }

    private void dispatchOne(Long outboxId) {
        String workerId = UUID.randomUUID().toString();
        DefaultTenantMembershipOutbox claimed =
                claim(outboxId, workerId);
        if (claimed == null) {
            return;
        }

        try {
            DefaultTenantMembershipRequest request =
                    new DefaultTenantMembershipRequest();
            request.setEventId(claimed.getEventId());
            request.setUserId(claimed.getUserId());
            request.setRole(claimed.getRole());

            ApiResponse<Void> response =
                    tenantMembershipClient.ensureDefaultMembership(request);
            if (response == null || response.getCode() != 200) {
                throw new IllegalStateException(
                        "default membership dependency rejected request");
            }
            transactionTemplate.execute(status -> {
                outboxMapper.finalizeSucceeded(outboxId, workerId);
                return null;
            });
        } catch (RuntimeException exception) {
            int nextRetryCount = claimed.getRetryCount() == null
                    ? 1
                    : claimed.getRetryCount() + 1;
            LocalDateTime now = LocalDateTime.now();
            transactionTemplate.execute(status -> {
                outboxMapper.finalizeFailed(
                        outboxId,
                        workerId,
                        now.plusSeconds(5),
                        DELIVERY_ERROR,
                        MAX_RETRY_COUNT);
                return null;
            });
            logFailure(claimed, nextRetryCount, exception);
        }
    }

    private DefaultTenantMembershipOutbox claim(
            Long outboxId,
            String workerId) {
        LocalDateTime claimedTime = LocalDateTime.now();
        return transactionTemplate.execute(status -> {
            int updated = outboxMapper.claim(
                    outboxId,
                    workerId,
                    claimedTime,
                    claimedTime.plusSeconds(LEASE_SECONDS),
                    MAX_RETRY_COUNT);
            return updated == 1
                    ? outboxMapper.selectById(outboxId)
                    : null;
        });
    }

    private DefaultTenantMembershipOutbox findByUserId(Long userId) {
        return outboxMapper.selectOne(
                new QueryWrapper<DefaultTenantMembershipOutbox>()
                        .eq("user_id", userId)
                        .orderByDesc("outbox_id")
                        .last("limit 1"));
    }

    private void logFailure(
            DefaultTenantMembershipOutbox outbox,
            int retryCount,
            RuntimeException exception) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventId", outbox.getEventId());
        fields.put("retryCount", retryCount);
        fields.put("failureType", exception.getClass().getName());
        log.warn(
                "默认租户成员事件投递失败：fields={}，failure={}",
                StructuredRedactor.redact(
                        fields,
                        SAFE_FAILURE_FIELDS,
                        Collections.<String>emptySet()),
                SafeExceptionLogFormatter.format(exception));
    }
}
