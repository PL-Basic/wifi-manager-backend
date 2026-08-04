package com.plagod.controller;

import com.plagod.client.UserServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.user.UserOperationReviewDTO;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.exception.ApiStatusException;
import com.plagod.vo.entitlement.DurationPurchasePageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementUsagePageResult;
import com.plagod.vo.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;


@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserServiceClient userServiceClient;

    @GetMapping
    public ApiResponse<UserPageResult> pageUsers(@RequestParam(defaultValue = "1") Long current,
                                                 @RequestParam(defaultValue = "10") Long size,
                                                 @RequestParam(required = false) String keyword) {
        return userServiceClient.pageUsers(current, size, keyword);
    }

    @GetMapping("/stats")
    public ApiResponse<UserStatsVO> getUserStats() {
        return userServiceClient.getUserStats();
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserVO> getUser(@PathVariable Long userId) {
        return userServiceClient.getUser(userId);
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserVO> updateUser(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                          @Valid @RequestBody UserUpdateDTO updateDTO) {
        return userServiceClient.updateUser(userId, operatorId, operatorRole, updateDTO);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                          @Valid @RequestBody UserStatusDTO statusDTO) {
        return userServiceClient.updateStatus(userId, operatorId, operatorRole, statusDTO);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId,
                                        @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                        @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {
        return userServiceClient.deleteUser(userId, operatorId, operatorRole);
    }

    @DeleteMapping("/{userId}/purge")
    public ApiResponse<Void> purgeUser(@PathVariable Long userId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                       @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {
        return userServiceClient.purgeUser(userId, operatorId, operatorRole);
    }

    @PostMapping("/{userId}/purge-requests")
    public ApiResponse<Long> requestPurgeUser(@PathVariable Long userId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
                                              @RequestHeader(value = "X-User-Name", required = false) String requesterName,
                                              @RequestHeader(value = "X-User-Role", required = false) Integer requesterRole,
                                              @RequestBody(required = false)UserPurgeRequestDTO userPurgeRequestDTO) {
        return userServiceClient.requestPurgeUser(
                userId,
                requesterId,
                requesterName,
                requesterRole,
                userPurgeRequestDTO);
    }

    @GetMapping("/operation-requests")
    public ApiResponse<UserOperationRequestPageResult> pageOperationRequests(@RequestParam(defaultValue = "1") Long current,
                                                                             @RequestParam(defaultValue = "10") Long size,
                                                                             @RequestParam(required = false) Integer status) {
        return userServiceClient.pageOperationRequests(current, size, status);
    }

    @PutMapping("/operation-requests/{id}/review")
    public ApiResponse<Void> reviewOperationRequest(@PathVariable Long id,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                                    @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                                    @RequestHeader(value = "X-User-Role", required = false) Integer approverRole,
                                                    @RequestBody UserOperationReviewDTO dto) {
        return userServiceClient.reviewOperationRequest(id, approverId, approverName, approverRole, dto);
    }

    @GetMapping("/{userId}/entitlement")
    public ApiResponse<EntitlementSnapshotVO> getEntitlement(@PathVariable Long userId) {
        return userServiceClient.getEntitlement(userId);
    }

    @GetMapping("/{userId}/entitlement/purchases")
    public ApiResponse<DurationPurchasePageResult> pagePurchases(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size) {
        return userServiceClient.pagePurchases(userId, current, size);
    }

    @GetMapping("/{userId}/entitlement/usage-logs")
    public ApiResponse<EntitlementUsagePageResult> pageUsageLogs(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size) {
        return userServiceClient.pageUsageLogs(userId, current, size);
    }

    @PostMapping("/{userId}/entitlement/adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjustEntitlement(@PathVariable Long userId,
                                                                @RequestHeader("X-User-Id") Long operatorId,
                                                                @RequestHeader("X-User-Name") String operatorName,
                                                                @Valid @RequestBody EntitlementAdjustmentRequest request) {

        return userServiceClient.adjustEntitlement(userId, operatorId, operatorName, request);
    }

    @PostMapping("/{userId}/entitlement/reward-orders")
    public ApiResponse<EntitlementOrderVO> createRewardOrder(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long operatorId,
            @RequestHeader("X-User-Name") String operatorName,
            @RequestHeader("X-User-Role") Integer operatorRole,
            @Valid @RequestBody EntitlementRewardOrderRequest request) {

        if (!Integer.valueOf(0).equals(operatorRole)) {
            throw ApiStatusException.forbidden("仅超级管理员可以创建奖励订单");
        }

        return userServiceClient.createRewardOrder(userId, operatorId, operatorName, operatorRole, request);
    }
}
