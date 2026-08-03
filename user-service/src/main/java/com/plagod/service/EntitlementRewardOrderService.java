package com.plagod.service;

import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.vo.entitlement.EntitlementOrderVO;

public interface EntitlementRewardOrderService {

    EntitlementOrderVO create(Long userId,
                              Long operatorId,
                              String operatorName,
                              EntitlementRewardOrderRequest request);
}
