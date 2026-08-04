package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.client.TenantMembershipClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.event.DefaultTenantMembershipOutboxCreatedEvent;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DefaultTenantMembershipOutboxServiceImpl implements DefaultTenantMembershipOutboxService {

    private final DefaultTenantMembershipOutboxMapper outboxMapper;
    private final TenantMembershipClient tenantMembershipClient;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${wifi.internal.token}")
    private String internalToken;

    public DefaultTenantMembershipOutboxServiceImpl(
            DefaultTenantMembershipOutboxMapper outboxMapper,
            TenantMembershipClient tenantMembershipClient,
            ApplicationEventPublisher eventPublisher) {
        this.outboxMapper = outboxMapper;
        this.tenantMembershipClient = tenantMembershipClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void enqueue(Long userId, Integer role) {
        DefaultTenantMembershipOutbox outbox = new DefaultTenantMembershipOutbox();
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setUserId(userId);
        outbox.setRole(role);
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
        eventPublisher.publishEvent(new DefaultTenantMembershipOutboxCreatedEvent(outbox.getEventId()));
    }

    @Override
    public void dispatch(String eventId) {
        DefaultTenantMembershipOutbox outbox = outboxMapper.selectOne(
                new QueryWrapper<DefaultTenantMembershipOutbox>().eq("event_id", eventId));
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
                String message = response == null ? "tenant-service未返回结果" : response.getMessage();
                throw new IllegalStateException(message);
            }
            outboxMapper.update(null, new UpdateWrapper<DefaultTenantMembershipOutbox>()
                    .eq("outbox_id", outbox.getOutboxId())
                    .ne("status", "SUCCEEDED")
                    .set("status", "SUCCEEDED")
                    .set("last_error", null));
        } catch (RuntimeException exception) {
            int nextRetryCount = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
            long delaySeconds = Math.min(300L, 5L * (1L << Math.min(nextRetryCount, 6)));
            String error = compactError(exception);
            outboxMapper.update(null, new UpdateWrapper<DefaultTenantMembershipOutbox>()
                    .eq("outbox_id", outbox.getOutboxId())
                    .ne("status", "SUCCEEDED")
                    .set("status", "RETRY")
                    .set("retry_count", nextRetryCount)
                    .set("next_retry_time", LocalDateTime.now().plusSeconds(delaySeconds))
                    .set("last_error", error));
            log.warn("默认租户成员事件投递失败：eventId={}，retryCount={}，error={}",
                    outbox.getEventId(), nextRetryCount, error);
        }
    }

    @Override
    public void dispatchForUser(Long userId) {
        DefaultTenantMembershipOutbox outbox = outboxMapper.selectOne(
                new QueryWrapper<DefaultTenantMembershipOutbox>().eq("user_id", userId));
        if (outbox != null) {
            dispatch(outbox.getEventId());
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${wifi.tenant-membership-outbox.scan-delay-ms:5000}")
    public void retryDueEvents() {
        List<DefaultTenantMembershipOutbox> dueEvents = outboxMapper.selectList(
                new QueryWrapper<DefaultTenantMembershipOutbox>()
                        .in("status", Arrays.asList("PENDING", "RETRY"))
                        .le("next_retry_time", LocalDateTime.now())
                        .orderByAsc("outbox_id")
                        .last("limit 20"));
        for (DefaultTenantMembershipOutbox outbox : dueEvents) {
            dispatch(outbox.getEventId());
        }
    }

    @Override
    public boolean isMembershipReady(Long userId) {
        DefaultTenantMembershipOutbox outbox = outboxMapper.selectOne(
                new QueryWrapper<DefaultTenantMembershipOutbox>().eq("user_id", userId));
        // 迁移前已有用户没有 Outbox，V2.1 已直接回填其默认成员关系。
        return outbox == null || "SUCCEEDED".equals(outbox.getStatus());
    }

    private String compactError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
