package com.plagod.service;

import com.plagod.dto.entitlement.EntitlementOrderCreateRequest;
import com.plagod.vo.entitlement.EntitlementOrderPageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementProductVO;

import java.util.List;

public interface EntitlementOrderService {

    List<EntitlementProductVO> listProducts();

    EntitlementOrderVO createOrder(Long tenantId, Long userId, EntitlementOrderCreateRequest request);

    EntitlementOrderPageResult pageOwnOrders(Long tenantId, Long userId, long current, long size, String status);

    EntitlementOrderVO getOwnOrder(Long tenantId, Long userId, String orderNo);

    EntitlementOrderVO cancelOwnOrder(Long tenantId, Long userId, String orderNo);

    int closeExpiredOrders(int batchSize);
}
