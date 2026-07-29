package com.plagod.service;

import com.plagod.dto.entitlement.EntitlementOrderCreateRequest;
import com.plagod.vo.entitlement.EntitlementOrderPageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementProductVO;

import java.util.List;

public interface EntitlementOrderService {

    List<EntitlementProductVO> listProducts();

    EntitlementOrderVO createOrder(Long userId, EntitlementOrderCreateRequest request);

    EntitlementOrderPageResult pageOwnOrders(Long userId, long current, long size, String status);

    EntitlementOrderVO getOwnOrder(Long userId, String orderNo);

    EntitlementOrderVO cancelOwnOrder(Long userId, String orderNo);

    int closeExpiredOrders(int batchSize);
}