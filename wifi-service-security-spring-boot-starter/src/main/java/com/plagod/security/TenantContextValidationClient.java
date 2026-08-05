package com.plagod.security;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.vo.tenant.TenantContextValidationVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通过服务发现调用 tenant-service 的实时上下文校验接口。
 */
public interface TenantContextValidationClient {

    @PostMapping("/internal/tenants/context/validate")
    ApiResponse<TenantContextValidationVO> validate(
            @RequestBody TenantContextValidationRequest request);
}
