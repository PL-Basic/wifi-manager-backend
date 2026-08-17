package com.plagod.service;

import com.plagod.client.UserEntitlementClient;
import com.plagod.client.UserPolicyClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.UserConnectionPolicyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceUserRemoteGateway {

    @Autowired
    private UserEntitlementClient userEntitlementClient;

    @Autowired
    private UserPolicyClient userPolicyClient;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApiResponse<EntitlementLeaseResult> acquireLease(
            EntitlementLeaseRequest request) {
        return userEntitlementClient.acquireLease(request);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApiResponse<UserConnectionPolicyVO> getConnectionPolicy(Long userId) {
        return userPolicyClient.getConnectionPolicy(userId);
    }
}
