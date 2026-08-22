package com.plagod.service.impl;

import com.plagod.client.AuthSessionClient;
import com.plagod.dto.ApiResponse;
import com.plagod.entity.auth.UserAuthSessionRevokeOutbox;
import com.plagod.mapper.UserAuthSessionRevokeOutboxMapper;
import com.plagod.service.UserAuthSessionRevokeOutboxService;
import com.plagod.web.SafeExceptionLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class UserAuthSessionRevokeOutboxServiceImpl
        implements UserAuthSessionRevokeOutboxService {

    static final int MAX_RETRY_COUNT = 3;
    static final int LEASE_SECONDS = 30;
    private static final int MAX_BATCH_SIZE = 100;
    private static final String DELIVERY_ERROR =
            "AUTH_SESSION_REVOKE_FAILED";

    private final UserAuthSessionRevokeOutboxMapper outboxMapper;
    private final AuthSessionClient authSessionClient;
    private final TransactionTemplate transactionTemplate;

    public UserAuthSessionRevokeOutboxServiceImpl(
            UserAuthSessionRevokeOutboxMapper outboxMapper,
            AuthSessionClient authSessionClient,
            PlatformTransactionManager transactionManager) {
        this.outboxMapper = outboxMapper;
        this.authSessionClient = authSessionClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void dispatchPending(int batchSize) {
        int boundedBatchSize = Math.max(
                1,
                Math.min(batchSize, MAX_BATCH_SIZE));
        List<Long> outboxIds = outboxMapper.selectDispatchableIds(
                LocalDateTime.now(),
                MAX_RETRY_COUNT,
                boundedBatchSize);
        for (Long outboxId : outboxIds) {
            dispatchOne(outboxId);
        }
    }

    private void dispatchOne(Long outboxId) {
        String workerId = UUID.randomUUID().toString();
        UserAuthSessionRevokeOutbox claimed =
                claim(outboxId, workerId);
        if (claimed == null) {
            return;
        }

        try {
            ApiResponse<Void> response = authSessionClient.revokeAll(
                    claimed.getUserId(),
                    claimed.getRevokeReason());
            if (response == null || response.getCode() != 200) {
                throw new IllegalStateException(
                        "auth session dependency rejected request");
            }
            LocalDateTime completedTime = LocalDateTime.now();
            transactionTemplate.execute(status -> {
                outboxMapper.finalizeSucceeded(
                        outboxId,
                        workerId,
                        completedTime);
                return null;
            });
        } catch (RuntimeException exception) {
            LocalDateTime now = LocalDateTime.now();
            transactionTemplate.execute(status -> {
                outboxMapper.finalizeFailed(
                        outboxId,
                        workerId,
                        now.plusSeconds(5),
                        now,
                        DELIVERY_ERROR,
                        MAX_RETRY_COUNT);
                return null;
            });
            log.warn(
                    "认证会话撤销事件投递失败：eventId={}，failure={}",
                    claimed.getEventId(),
                    SafeExceptionLogFormatter.format(exception));
        }
    }

    private UserAuthSessionRevokeOutbox claim(
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
}
