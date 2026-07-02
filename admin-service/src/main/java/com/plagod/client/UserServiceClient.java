package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserOperationReviewDTO;
import com.plagod.dto.user.UserPurgeRequestDTO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.vo.user.UserOperationRequestPageResult;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.vo.user.UserVO;
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

    @GetMapping("/users")
    ApiResponse<UserPageResult> pageUsers(@RequestParam("current") Long current,
                                          @RequestParam("size") Long size,
                                          @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/users/{userId}")
    ApiResponse<UserVO> getUser(@PathVariable("userId") Long userId);

    @PutMapping("/users/{userId}")
    ApiResponse<UserVO> updateUser(@PathVariable("userId") Long userId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                   @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                   @RequestBody UserUpdateDTO updateDTO);

    @PutMapping("/users/{userId}/status")
    ApiResponse<Void> updateStatus(@PathVariable("userId") Long userId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                   @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole,
                                   @RequestBody UserStatusDTO statusDTO);

    @DeleteMapping("/users/{userId}")
    ApiResponse<Void> deleteUser(@PathVariable("userId") Long userId,
                                 @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                 @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @DeleteMapping("/users/{userId}/purge")
    ApiResponse<Void> purgeUser(@PathVariable("userId") Long userId,
                                @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole);

    @PostMapping("/users/{userId}/purge-requests")
    ApiResponse<Long> requestPurgeUser(@PathVariable("userId") Long userId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
                                       @RequestHeader(value = "X-User-Name", required = false) String requesterName,
                                       @RequestBody UserPurgeRequestDTO purgeRequestDTO);

    @GetMapping("/users/operation-requests")
    ApiResponse<UserOperationRequestPageResult> pageOperationRequests(@RequestParam("current") Long current,
                                                                      @RequestParam("size") Long size,
                                                                      @RequestParam(value = "status", required = false) Integer status);

    @PutMapping("/users/operation-requests/{id}/review")
    ApiResponse<Void> reviewOperationRequest(@PathVariable("id") Long id,
                                             @RequestHeader(value = "X-User-Id", required = false) Long approverId,
                                             @RequestHeader(value = "X-User-Name", required = false) String approverName,
                                             @RequestBody UserOperationReviewDTO dto);

    @GetMapping("/users/stats")
    ApiResponse<UserStatsVO> getUserStats();
}
