package com.plagod.service;

import com.plagod.vo.user.EntitlementSnapshotVO;

public interface EntitlementQueryService {

    EntitlementSnapshotVO getSnapshot(Long userId, Long entitlementId);
}