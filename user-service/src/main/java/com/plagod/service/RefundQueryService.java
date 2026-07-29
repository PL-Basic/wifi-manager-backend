package com.plagod.service;

import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;

public interface RefundQueryService {

    RefundPageResult pageOwnRefunds(Long userId, long current, long size, String status);

    RefundVO getOwnRefund(Long userId, String refundNo);

    RefundPageResult pageForAdmin(long current, long size, Long userId, String status);
}