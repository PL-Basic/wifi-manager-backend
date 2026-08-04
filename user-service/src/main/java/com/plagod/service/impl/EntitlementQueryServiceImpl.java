package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.entitlement.DurationPurchase;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.service.EntitlementQueryService;
import com.plagod.vo.entitlement.*;
import com.plagod.vo.user.EntitlementSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EntitlementQueryServiceImpl implements EntitlementQueryService {

    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;
    @Autowired
    private EntitlementUsageLogMapper usageLogMapper;

    @Override
    public EntitlementSnapshotVO getSnapshot(Long userId, Long entitlementId) {

        requireUserId(userId);
        if (entitlementId == null || entitlementId <= 0) {
            throw new IllegalArgumentException("权益标识无效");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectById(entitlementId);

        if (entitlement == null || !userId.equals(entitlement.getUserId())) {
            throw new IllegalArgumentException("权益不存在或不属于当前用户");
        }

        return toSnapshot(entitlement);
    }

    @Override
    public EntitlementSnapshotVO getByUserId(Long userId) {
        requireUserId(userId);

        QueryWrapper<NetworkEntitlement> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).last("limit 1");

        return toSnapshot(entitlementMapper.selectOne(wrapper));
    }

    @Override
    public DurationPurchasePageResult pagePurchases(Long userId, long current, long size) {

        requireUserId(userId);
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<DurationPurchase> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .orderByDesc("create_time")
                .orderByDesc("purchase_id");

        Page<DurationPurchase> page = purchaseMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);

        List<DurationPurchaseVO> records = new ArrayList<>();
        for (DurationPurchase purchase : page.getRecords()) {
            records.add(toPurchaseVO(purchase));
        }

        DurationPurchasePageResult result = new DurationPurchasePageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    public EntitlementUsagePageResult pageUsageLogs(Long userId, long current, long size) {

        requireUserId(userId);
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<EntitlementUsageLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .orderByDesc("create_time")
                .orderByDesc("id");

        Page<EntitlementUsageLog> page = usageLogMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);

        List<EntitlementUsageLogVO> records = new ArrayList<>();

        for (EntitlementUsageLog log : page.getRecords()) {
            records.add(toUsageVO(log));
        }

        EntitlementUsagePageResult result = new EntitlementUsagePageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    private EntitlementSnapshotVO toSnapshot(NetworkEntitlement entitlement) {

        if (entitlement == null) {
            return null;
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

    private DurationPurchaseVO toPurchaseVO(DurationPurchase purchase) {

        DurationPurchaseVO vo = new DurationPurchaseVO();
        vo.setPurchaseId(purchase.getOrderNo());
        vo.setOrderNo(purchase.getOrderNo());
        vo.setPurchasedSeconds(purchase.getPurchasedSeconds());
        vo.setRemainingSeconds(purchase.getRemainingSeconds());
        vo.setPaidAmountCents(purchase.getPaidAmountCents());
        vo.setRefundable(purchase.getRefundable());
        vo.setStatus(purchase.getStatus());
        vo.setRefundedAmountCents(purchase.getRefundedAmountCents());
        vo.setRefundTime(purchase.getRefundTime());
        vo.setCreateTime(purchase.getCreateTime());
        return vo;
    }

    private EntitlementUsageLogVO toUsageVO(EntitlementUsageLog log) {

        EntitlementUsageLogVO vo = new EntitlementUsageLogVO();
        vo.setId(log.getId());
        vo.setRequestId(log.getRequestId());
        vo.setLineNo(log.getLineNo());
        vo.setPurchaseId(log.getPurchaseId());
        vo.setAuthorizationMode(log.getAuthorizationMode());
        vo.setSessionId(log.getSessionId());
        vo.setChangeSeconds(log.getChangeSeconds());
        vo.setBeforeSeconds(log.getBeforeSeconds());
        vo.setAfterSeconds(log.getAfterSeconds());
        vo.setReason(log.getReason());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
    }
}
