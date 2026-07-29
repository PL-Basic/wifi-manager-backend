package com.plagod.service;

import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.vo.user.EntitlementSnapshotVO;

public interface EntitlementAdjustmentService {

    EntitlementSnapshotVO adjust(Long userId, Long operatorId, String operatorName, EntitlementAdjustmentRequest request);
}