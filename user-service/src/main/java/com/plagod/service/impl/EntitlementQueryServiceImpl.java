package com.plagod.service.impl;

import com.plagod.entity.NetworkEntitlement;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.service.EntitlementQueryService;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EntitlementQueryServiceImpl implements EntitlementQueryService {

    @Autowired
    private NetworkEntitlementMapper entitlementMapper;

    @Override
    public EntitlementSnapshotVO getSnapshot(Long userId, Long entitlementId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
        if (entitlementId == null || entitlementId <= 0) {
            throw new IllegalArgumentException("权益标识无效");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectById(entitlementId);

        // 不区分“不存在”和“不属于该用户”，避免通过内部接口探测权益归属。
        if (entitlement == null || !userId.equals(entitlement.getUserId())) {
            throw new IllegalArgumentException("权益不存在或不属于当前用户");
        }

        EntitlementSnapshotVO snapshot = new EntitlementSnapshotVO();
        snapshot.setEntitlementId(entitlement.getEntitlementId());
        snapshot.setUserId(entitlement.getUserId());
        snapshot.setMode(entitlement.getMode());
        snapshot.setSubscriptionStartTime(entitlement.getSubscriptionStartTime());
        snapshot.setSubscriptionEndTime(entitlement.getSubscriptionEndTime());
        snapshot.setRemainingSeconds(entitlement.getRemainingSeconds());
        snapshot.setStatus(entitlement.getStatus());
        return snapshot;
    }
}