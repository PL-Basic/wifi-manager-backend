package com.plagod.service;

import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.tenant.TenantContextValidationVO;

public interface TenantContextService {

    TenantContextVO resolve(TenantContextResolveRequest request);

    TenantContextValidationVO validate(TenantContextValidationRequest request);
}
