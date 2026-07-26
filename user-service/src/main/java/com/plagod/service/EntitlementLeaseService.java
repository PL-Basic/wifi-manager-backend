package com.plagod.service;

import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.vo.user.EntitlementLeaseResult;


public interface EntitlementLeaseService {
    // 判断当前用户是否续租，并结算已经发生的在线时长
    EntitlementLeaseResult acquireLease(EntitlementLeaseRequest request);
}