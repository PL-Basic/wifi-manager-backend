package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.UserStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/users")
    ApiResponse<Object> pageUsers(@RequestParam("current") Long current,
                                  @RequestParam("size") Long size,
                                  @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/users/{userId}")
    ApiResponse<Object> getUser(@PathVariable("userId") Long userId);

    @PutMapping("/users/{userId}")
    ApiResponse<Object> updateUser(@PathVariable("userId") Long userId, @RequestBody Map<String, Object> body);

    @PutMapping("/users/{userId}/status")
    ApiResponse<Void> updateStatus(@PathVariable("userId") Long userId, @RequestBody Map<String, Object> body);

    @DeleteMapping("/users/{userId}")
    ApiResponse<Void> deleteUser(@PathVariable("userId") Long userId);

    @DeleteMapping("/users/{userId}/purge")
    ApiResponse<Void> purgeUser(@PathVariable("userId") Long userId);

    @PostMapping("/users/{userId}/purge-requests")
    ApiResponse<Long> requestPurgeUser(@PathVariable("userId") Long userId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
                                       @RequestHeader(value = "X-User-Name", required = false) String requesterName,
                                       @RequestBody Map<String, Object> body);

    @GetMapping("/users/operation-requests")
    ApiResponse<Object> pageOperationRequests(@RequestParam("current") Long current,
                                              @RequestParam("size") Long size,
                                              @RequestParam(value = "status", required = false) Integer status);

    @PutMapping("/users/operation-requests/{id}/review")
    ApiResponse<Void> reviewOperationRequest(@PathVariable("id") Long id,
                                             @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                             @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                             @RequestBody Map<String, Object> body);

    @GetMapping("/users/stats")
    ApiResponse<UserStatsVO> getUserStats();
}
