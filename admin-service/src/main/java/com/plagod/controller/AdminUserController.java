package com.plagod.controller;

import com.plagod.client.UserServiceClient;
import com.plagod.configuration.AdminContextScopeRequired;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.user.UserOperationReviewDTO;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;


@RestController
@RequestMapping("/admin/users")
@AdminContextScopeRequired(
        AdminContextScopeRequired.Scope.PLATFORM)
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
                                          @Valid @RequestBody UserUpdateDTO updateDTO) {
        return userServiceClient.updateUser(userId, updateDTO);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @Valid @RequestBody UserStatusDTO statusDTO) {
        return userServiceClient.updateStatus(userId, statusDTO);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        return userServiceClient.deleteUser(userId);
    }

    @DeleteMapping("/{userId}/purge")
    public ApiResponse<Void> purgeUser(@PathVariable Long userId) {
        return userServiceClient.purgeUser(userId);
    }

    @PostMapping("/{userId}/purge-requests")
    public ApiResponse<Long> requestPurgeUser(@PathVariable Long userId,
                                              @RequestBody(required = false)UserPurgeRequestDTO userPurgeRequestDTO) {
        return userServiceClient.requestPurgeUser(
                userId,
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
                                                    @RequestBody UserOperationReviewDTO dto) {
        return userServiceClient.reviewOperationRequest(id, dto);
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
                                                                @Valid @RequestBody EntitlementAdjustmentRequest request) {

        return userServiceClient.adjustEntitlement(userId, request);
    }

    @PostMapping("/{userId}/entitlement/unlimited-adjustments")
    public ApiResponse<EntitlementSnapshotVO> adjustUnlimitedEntitlement(
            @PathVariable Long userId,
            @Valid @RequestBody UnlimitedEntitlementRequest request) {
        return userServiceClient.adjustUnlimitedEntitlement(
                userId, request);
    }

    @PostMapping("/{userId}/entitlement/reward-orders")
    public ApiResponse<EntitlementOrderVO> createRewardOrder(
            @PathVariable Long userId,
            @Valid @RequestBody EntitlementRewardOrderRequest request) {
        return userServiceClient.createRewardOrder(userId, request);
    }
}
