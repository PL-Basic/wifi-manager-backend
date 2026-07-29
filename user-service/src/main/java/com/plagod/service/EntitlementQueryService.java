package com.plagod.service;

import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.user.EntitlementSnapshotVO;

public interface EntitlementQueryService {

    EntitlementSnapshotVO getSnapshot(Long userId, Long entitlementId);

    EntitlementSnapshotVO getByUserId(Long userId);

    DurationPurchasePageResult pagePurchases(Long userId, long current, long size);

    EntitlementUsagePageResult pageUsageLogs(Long userId, long current, long size);
}