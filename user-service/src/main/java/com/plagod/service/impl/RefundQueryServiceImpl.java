package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.entity.entitlement.RefundRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.RefundRecordMapper;
import com.plagod.service.RefundQueryService;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RefundQueryServiceImpl implements RefundQueryService {

    private static final Set<String> REFUND_STATUSES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    EntitlementTradeConstants.REFUND_REQUESTED,
                    EntitlementTradeConstants.REFUND_REJECTED,
                    EntitlementTradeConstants.REFUND_PROCESSING,
                    EntitlementTradeConstants.REFUND_SUCCEEDED,
                    EntitlementTradeConstants.REFUND_FAILED
            )));

    @Autowired
    private RefundRecordMapper refundMapper;

    @Override
    @Transactional(readOnly = true)
    public RefundPageResult pageOwnRefunds(Long tenantId, Long userId, long current, long size, String status) {

        requireTenantId(tenantId);
        requireUserId(userId);
        return page(tenantId, current, size, userId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundVO getOwnRefund(Long tenantId, Long userId, String refundNo) {
        requireTenantId(tenantId);
        requireUserId(userId);

        RefundRecord refund = refundMapper.selectOwnedRefund(
                tenantId, normalizeRefundNo(refundNo), userId);

        if (refund == null) {
            // 不区分不存在和不属于本人，避免枚举他人的退款单。
            throw ApiStatusException.notFound("退款单不存在");
        }

        return toVO(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundPageResult pageForAdmin(Long tenantId, long current, long size, Long userId, String status) {

        requireTenantId(tenantId);
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("用户编号无效");
        }

        return page(tenantId, current, size, userId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundVO getForAdmin(Long tenantId, String refundNo) {
        requireTenantId(tenantId);
        RefundRecord refund = refundMapper.selectByRefundNo(normalizeRefundNo(refundNo));
        if (refund == null || !tenantId.equals(refund.getTenantId())) {
            throw ApiStatusException.notFound("退款单不存在");
        }
        return toVO(refund);
    }

    private RefundPageResult page(Long tenantId, long current, long size, Long userId, String status) {

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<RefundRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId);

        if (userId != null) {
            wrapper.eq("user_id", userId);
        }

        if (StringUtils.hasText(status)) {
            wrapper.eq("status", normalizeStatus(status));
        }

        // 相同创建时间时再按主键排序，保证分页顺序稳定。
        wrapper.orderByDesc("create_time");
        wrapper.orderByDesc("refund_id");

        Page<RefundRecord> page = refundMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);

        List<RefundVO> records = new ArrayList<>();
        for (RefundRecord refund : page.getRecords()) {
            records.add(toVO(refund));
        }

        RefundPageResult result = new RefundPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);

        if (!REFUND_STATUSES.contains(value)) {
            throw new IllegalArgumentException("退款状态无效");
        }

        return value;
    }

    private String normalizeRefundNo(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            throw new IllegalArgumentException("退款单号不能为空");
        }

        String value = refundNo.trim().toUpperCase(Locale.ROOT);
        if (value.length() > 64) {
            throw new IllegalArgumentException("退款单号不能超过64个字符");
        }

        return value;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
    }

    private RefundVO toVO(RefundRecord refund) {
        RefundVO vo = new RefundVO();
        BeanUtils.copyProperties(refund, vo);
        vo.setTenantId(String.valueOf(refund.getTenantId()));
        vo.setPurchaseId(refund.getOrderNo());
        return vo;
    }

    private void requireTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户身份无效");
        }
    }
}
