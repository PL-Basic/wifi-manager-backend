package com.plagod.service;

import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.vo.user.EntitlementSnapshotVO;

public interface EntitlementAdjustmentService {

    EntitlementSnapshotVO adjust(Long tenantId, Long userId, Long operatorId,
                                 String operatorName, EntitlementAdjustmentRequest request);

    EntitlementSnapshotVO adjustUnlimited(Long tenantId, Long userId, Long operatorId,
                                          String operatorName, Integer operatorRole,
                                          UnlimitedEntitlementRequest request);
}
