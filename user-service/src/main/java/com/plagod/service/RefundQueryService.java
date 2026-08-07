package com.plagod.service;

import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;

public interface RefundQueryService {

    RefundPageResult pageOwnRefunds(Long tenantId, Long userId, long current, long size, String status);

    RefundVO getOwnRefund(Long tenantId, Long userId, String refundNo);

    RefundPageResult pageForAdmin(Long tenantId, long current, long size, Long userId, String status);

    RefundVO getForAdmin(Long tenantId, String refundNo);
}
