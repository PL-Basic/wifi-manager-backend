package com.plagod.client;

import com.plagod.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", contextId = "authSessionClient")
public interface AuthSessionClient {

    @PostMapping("/internal/auth/sessions/users/{userId}/revoke")
    ApiResponse<Void> revokeAll(
            @PathVariable("userId") Long userId,
            @RequestParam("reason") String reason);
}
