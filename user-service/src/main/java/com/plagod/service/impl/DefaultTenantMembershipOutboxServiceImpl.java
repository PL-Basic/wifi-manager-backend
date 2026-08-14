package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.client.TenantMembershipClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import com.plagod.mapper.DefaultTenantMembershipOutboxMapper;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DefaultTenantMembershipOutboxServiceImpl implements DefaultTenantMembershipOutboxService {

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
                throw new IllegalStateException(response == null ? "tenant-service未返回结果" : response.getMessage());
            }
            outboxMapper.update(null, new UpdateWrapper<DefaultTenantMembershipOutbox>()
                    .eq("outbox_id", outbox.getOutboxId())
                    .ne("status", "SUCCEEDED")
                    .set("status", "SUCCEEDED")
                    .set("last_error", null));
        } catch (RuntimeException exception) {
            int retryCount = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
            String error = compactError(exception);
            outboxMapper.update(null, new UpdateWrapper<DefaultTenantMembershipOutbox>()
                    .eq("outbox_id", outbox.getOutboxId())
                    .ne("status", "SUCCEEDED")
                    .set("status", "RETRY")
                    .set("retry_count", retryCount)
                    .set("next_retry_time", LocalDateTime.now())
                    .set("last_error", error));
            log.warn("默认租户成员事件投递失败：eventId={}，retryCount={}，error={}",
                    outbox.getEventId(), retryCount, error);
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

    private String compactError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
