package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementOrderCreateRequest;
import com.plagod.service.EntitlementOrderService;
import com.plagod.vo.entitlement.EntitlementOrderPageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/entitlements")
public class EntitlementOrderController {

    @Autowired
    private EntitlementOrderService orderService;

    @GetMapping("/products")
    public ApiResponse<List<EntitlementProductVO>> listProducts() {
        return ApiResponse.success(orderService.listProducts());
    }

    @PostMapping("/orders")
    public ApiResponse<EntitlementOrderVO> createOrder(@RequestHeader("X-User-Id") Long userId,
                                                       @Valid @RequestBody EntitlementOrderCreateRequest request) {

        return ApiResponse.success("订单创建成功", orderService.createOrder(userId, request));
    }

    @GetMapping("/orders")
    public ApiResponse<EntitlementOrderPageResult> pageOrders(@RequestHeader("X-User-Id") Long userId,
                                                              @RequestParam(defaultValue = "1") Long current,
                                                              @RequestParam(defaultValue = "10") Long size,
                                                              @RequestParam(required = false) String status) {

        return ApiResponse.success(orderService.pageOwnOrders(userId, current, size, status));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<EntitlementOrderVO> getOrder(@RequestHeader("X-User-Id") Long userId,
                                                    @PathVariable String orderNo) {

        return ApiResponse.success(orderService.getOwnOrder(userId, orderNo));
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public ApiResponse<EntitlementOrderVO> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                                       @PathVariable String orderNo) {

        return ApiResponse.success("订单取消完成", orderService.cancelOwnOrder(userId, orderNo));
    }
}