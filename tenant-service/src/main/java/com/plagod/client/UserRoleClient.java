package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserRoleBatchRequest;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserRoleClient {

    @PostMapping("/internal/users/role-snapshots")
    ApiResponse<List<UserRoleSnapshotVO>> getRoleSnapshots(@RequestBody UserRoleBatchRequest request);
}
