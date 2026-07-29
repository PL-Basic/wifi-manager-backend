package com.plagod.service;

import com.plagod.dto.entitlement.RefundApplyRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import com.plagod.vo.entitlement.RefundVO;

public interface RefundService {

    RefundVO apply(Long userId, RefundApplyRequest request);

    RefundVO review(String refundNo, Long reviewerId, String reviewerName, RefundReviewRequest request);

    RefundVO handleChannelResult(VerifiedRefundResult result);
}