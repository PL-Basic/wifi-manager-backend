package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementOrderCreateRequest;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.UserRequestContextPolicy;
import com.plagod.service.EntitlementOrderService;
import com.plagod.vo.entitlement.EntitlementOrderPageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/entitlements")
public class EntitlementOrderController {

    @Autowired
    private EntitlementOrderService orderService;

    @Autowired
    private UserRequestContextPolicy contextPolicy;

    @GetMapping("/products")
    public ApiResponse<List<EntitlementProductVO>> listProducts() {
        return ApiResponse.success(orderService.listProducts());
    }

    @PostMapping("/orders")
    public ApiResponse<EntitlementOrderVO> createOrder(
            HttpServletRequest servletRequest,
            @Valid @RequestBody EntitlementOrderCreateRequest request) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(servletRequest);
        return ApiResponse.success("订单创建成功", orderService.createOrder(
                contextPolicy.tenantId(context),
                context.getUserId(),
                request));
    }

    @GetMapping("/orders")
    public ApiResponse<EntitlementOrderPageResult> pageOrders(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(orderService.pageOwnOrders(
                contextPolicy.tenantId(context),
                context.getUserId(),
                current,
                size,
                status));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<EntitlementOrderVO> getOrder(
            HttpServletRequest request,
            @PathVariable String orderNo) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success(orderService.getOwnOrder(
                contextPolicy.tenantId(context),
                context.getUserId(),
                orderNo));
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public ApiResponse<EntitlementOrderVO> cancelOrder(
            HttpServletRequest request,
            @PathVariable String orderNo) {
        TrustedRequestContext context =
                contextPolicy.requireTenantBoundActor(request);
        return ApiResponse.success("订单取消完成", orderService.cancelOwnOrder(
                contextPolicy.tenantId(context),
                context.getUserId(),
                orderNo));
    }
}
