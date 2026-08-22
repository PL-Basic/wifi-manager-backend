package com.plagod.service;

import com.plagod.entity.device.DeviceCommandRecord;

import java.time.LocalDateTime;

public interface DeviceCommandDispatchTransactionService {

    DeviceCommandRecord claim(
            Long commandId,
            String workerId,
            LocalDateTime now,
            LocalDateTime leaseUntil,
            int publishMaxAttempts,
            long retryDelaySeconds);

    boolean finalizePublished(
            Long commandId,
            String workerId,
            LocalDateTime now,
            LocalDateTime deadlineTime);

    boolean finalizeFailure(
            Long commandId,
            String workerId,
            LocalDateTime now,
            int publishMaxAttempts,
            long retryDelaySeconds);
}
