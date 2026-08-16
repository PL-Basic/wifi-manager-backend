package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.user.User;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementLeaseServiceImplTest {

    private static final Long TENANT_ID = 3L;
    private static final Long USER_ID = 7L;
    private static final Long ENTITLEMENT_ID = 21L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private NetworkEntitlementMapper entitlementMapper;
    @Mock
    private DurationPurchaseMapper purchaseMapper;
    @Mock
    private EntitlementUsageLogMapper usageLogMapper;

    private EntitlementLeaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EntitlementLeaseServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "entitlementMapper", entitlementMapper);
        ReflectionTestUtils.setField(service, "purchaseMapper", purchaseMapper);
        ReflectionTestUtils.setField(service, "usageLogMapper", usageLogMapper);
    }

    @Test
    void backgroundLeaseResolvesTenantFromPersistedEntitlementWithoutDeduction() {
        EntitlementLeaseRequest request = request();
        NetworkEntitlement entitlement = unlimitedEntitlement();
        User user = new User();
        user.setStatus(1);

        when(entitlementMapper.selectById(ENTITLEMENT_ID)).thenReturn(entitlement);
        when(usageLogMapper.selectByRequestId(TENANT_ID, request.getRequestId()))
                .thenReturn(Collections.emptyList());
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(entitlementMapper.selectByUserIdForUpdate(TENANT_ID, USER_ID))
                .thenReturn(entitlement);
        when(usageLogMapper.selectByRequestIdForUpdate(TENANT_ID, request.getRequestId()))
                .thenReturn(Collections.emptyList());

        EntitlementLeaseResult result = service.acquireLease(null, request);

        assertEquals(Boolean.TRUE, result.getAllowed());
        assertEquals(20, result.getTtlSeconds());
        assertEquals(0L, result.getChargedSeconds());
        assertEquals(3600L, result.getRemainingSeconds());
        assertEquals("UNLIMITED_ACTIVE", result.getReason());
        verify(entitlementMapper, never()).deductRemainingSeconds(
                anyLong(), anyLong(), anyLong());
    }

    private EntitlementLeaseRequest request() {
        EntitlementLeaseRequest request = new EntitlementLeaseRequest();
        request.setEntitlementId(ENTITLEMENT_ID);
        request.setRequestId("session-lease-31");
        request.setUserId(USER_ID);
        request.setSessionId(31L);
        request.setUsageSeconds(10L);
        request.setRequestedTtlSeconds(60);
        return request;
    }

    private NetworkEntitlement unlimitedEntitlement() {
        NetworkEntitlement entitlement = new NetworkEntitlement();
        entitlement.setEntitlementId(ENTITLEMENT_ID);
        entitlement.setTenantId(TENANT_ID);
        entitlement.setUserId(USER_ID);
        entitlement.setMode(EntitlementTradeConstants.MODE_UNLIMITED);
        entitlement.setRemainingSeconds(3600L);
        entitlement.setStatus(1);
        return entitlement;
    }
}
