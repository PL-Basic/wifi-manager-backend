package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.vo.user.UserConnectionPolicyVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", contextId = "userPolicyClient")
public interface UserPolicyClient {

    @GetMapping("/internal/users/{userId}/connection-policy")
    ApiResponse<UserConnectionPolicyVO> getConnectionPolicy(
            @PathVariable("userId") Long userId);
}
