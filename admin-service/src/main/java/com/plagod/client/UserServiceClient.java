package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.entitlement.LocalDemoRefundResultRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.user.UserOperationReviewDTO;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.entitlement.RefundPageResult;
import com.plagod.vo.entitlement.RefundVO;
import com.plagod.vo.user.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/internal/admin/users")
    ApiResponse<UserPageResult> pageUsers(@RequestParam("current") Long current,
                                          @RequestParam("size") Long size,
                                          @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/admin/users/{userId}")
    ApiResponse<UserVO> getUser(@PathVariable("userId") Long userId);

    @PutMapping("/internal/admin/users/{userId}")
    ApiResponse<UserVO> updateUser(@PathVariable("userId") Long userId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                   @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                   @RequestBody UserUpdateDTO updateDTO);

    @PutMapping("/internal/admin/users/{userId}/status")
    ApiResponse<Void> updateStatus(@PathVariable("userId") Long userId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                   @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                   @RequestBody UserStatusDTO statusDTO);


    @DeleteMapping("/internal/admin/users/{userId}")
    ApiResponse<Void> deleteUser(@PathVariable("userId") Long userId,
                                 @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                 @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @DeleteMapping("/internal/admin/users/{userId}/purge")
    ApiResponse<Void> purgeUser(@PathVariable("userId") Long userId,
                                @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @PostMapping("/internal/admin/users/{userId}/purge-requests")
    ApiResponse<Long> requestPurgeUser(@PathVariable("userId") Long userId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
                                       @RequestHeader(value = "X-User-Name", required = false) String requesterName,
                                       @RequestHeader(value = "X-User-Role", required = false) Integer requesterRole,
                                       @RequestBody UserPurgeRequestDTO purgeRequestDTO);

    @GetMapping("/internal/admin/users/operation-requests")
    ApiResponse<UserOperationRequestPageResult> pageOperationRequests(@RequestParam("current") Long current,
                                                                      @RequestParam("size") Long size,
                                                                      @RequestParam(value = "status", required = false) Integer status);

    @PutMapping("/internal/admin/users/operation-requests/{id}/review")
    ApiResponse<Void> reviewOperationRequest(@PathVariable("id") Long id,
                                             @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                             @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                             @RequestHeader(value = "X-User-Role", required = false) Integer approverRole,
                                             @RequestBody UserOperationReviewDTO dto);

    @GetMapping("/internal/admin/users/stats")
    ApiResponse<UserStatsVO> getUserStats();

    @GetMapping("/internal/admin/entitlements/users/{userId}")
    ApiResponse<EntitlementSnapshotVO> getEntitlement(@PathVariable("userId") Long userId);

    @GetMapping("/internal/admin/entitlements/users/{userId}/purchases")
    ApiResponse<DurationPurchasePageResult> pagePurchases(@PathVariable("userId") Long userId,
                                                          @RequestParam("current") Long current,
                                                          @RequestParam("size") Long size);

    @GetMapping("/internal/admin/entitlements/users/{userId}/usage-logs")
    ApiResponse<EntitlementUsagePageResult> pageUsageLogs(@PathVariable("userId") Long userId,
                                                          @RequestParam("current") Long current,
                                                          @RequestParam("size") Long size);

    @PostMapping("/internal/admin/entitlements/users/{userId}/adjustments")
    ApiResponse<EntitlementSnapshotVO> adjustEntitlement(@PathVariable("userId") Long userId,
                                                         @RequestHeader("X-User-Id") Long operatorId,
                                                         @RequestHeader("X-User-Name") String operatorName,
                                                         @RequestBody EntitlementAdjustmentRequest request);

    @PostMapping("/internal/admin/entitlements/users/{userId}/reward-orders")
    ApiResponse<EntitlementOrderVO> createRewardOrder(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestHeader("X-User-Name") String operatorName,
            @RequestHeader("X-User-Role") Integer operatorRole,
            @RequestBody EntitlementRewardOrderRequest request);

    @PutMapping("/internal/admin/entitlements/refunds/{refundNo}/review")
    ApiResponse<RefundVO> reviewRefund(@PathVariable("refundNo") String refundNo,
                                       @RequestHeader("X-User-Id") Long reviewerId,
                                       @RequestHeader("X-User-Name") String reviewerName,
                                       @RequestBody RefundReviewRequest request);

    @PostMapping("/internal/admin/entitlements/refunds/{refundNo}/demo-result")
    ApiResponse<RefundVO> completeDemoRefund(@PathVariable("refundNo") String refundNo,
                                             @RequestBody LocalDemoRefundResultRequest request);

    @GetMapping("/internal/admin/entitlements/refunds")
    ApiResponse<RefundPageResult> pageRefunds(@RequestParam("current") Long current,
                                              @RequestParam("size") Long size,
                                              @RequestParam(value = "userId", required = false) Long userId,
                                              @RequestParam(value = "status", required = false) String status);

    @GetMapping("/internal/admin/entitlements/refunds/{refundNo}")
    ApiResponse<RefundVO> getRefund(@PathVariable("refundNo") String refundNo);
}
