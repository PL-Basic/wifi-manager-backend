package com.plagod.service;

import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.user.EntitlementSnapshotVO;

public interface EntitlementQueryService {

    EntitlementSnapshotVO getSnapshot(Long tenantId, Long userId, Long entitlementId);

    EntitlementSnapshotVO getByUserId(Long tenantId, Long userId);

    DurationPurchasePageResult pagePurchases(Long tenantId, Long userId, long current, long size);

    EntitlementUsagePageResult pageUsageLogs(Long tenantId, Long userId, long current, long size);
}
