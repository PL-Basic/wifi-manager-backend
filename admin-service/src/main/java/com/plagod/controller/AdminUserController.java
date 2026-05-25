package com.plagod.controller;

import com.plagod.client.UserServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.UserStatsVO;
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

import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserServiceClient userServiceClient;

    @GetMapping
    public ApiResponse<Object> pageUsers(@RequestParam(defaultValue = "1") Long current,
                                         @RequestParam(defaultValue = "10") Long size,
                                         @RequestParam(required = false) String keyword) {
        return userServiceClient.pageUsers(current, size, keyword);
    }

    @GetMapping("/stats")
    public ApiResponse<UserStatsVO> getUserStats() {
        return userServiceClient.getUserStats();
    }

    @GetMapping("/{userId}")
    public ApiResponse<Object> getUser(@PathVariable Long userId) {
        return userServiceClient.getUser(userId);
    }

    @PutMapping("/{userId}")
    public ApiResponse<Object> updateUser(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                          @RequestBody Map<String, Object> body) {
        return userServiceClient.updateUser(userId, operatorId, operatorRole, body);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                          @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                          @RequestBody Map<String, Object> body) {
        return userServiceClient.updateStatus(userId, operatorId, operatorRole, body);
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
                                              @RequestBody(required = false) Map<String, Object> body) {
        return userServiceClient.requestPurgeUser(userId, requesterId, requesterName, body);
    }

    @GetMapping("/operation-requests")
    public ApiResponse<Object> pageOperationRequests(@RequestParam(defaultValue = "1") Long current,
                                                     @RequestParam(defaultValue = "10") Long size,
                                                     @RequestParam(required = false) Integer status) {
        return userServiceClient.pageOperationRequests(current, size, status);
    }

    @PutMapping("/operation-requests/{id}/review")
    public ApiResponse<Void> reviewOperationRequest(@PathVariable Long id,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                                    @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                                    @RequestBody Map<String, Object> body) {
        return userServiceClient.reviewOperationRequest(id, approverId, approverName, body);
    }
}
