package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementAdjustmentServiceImplTest {

    private static final Long TENANT_ID = 3L;
    private static final Long USER_ID = 7L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private NetworkEntitlementMapper entitlementMapper;
    @Mock
    private DurationPurchaseMapper purchaseMapper;
    @Mock
    private EntitlementUsageLogMapper usageLogMapper;

    private EntitlementAdjustmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EntitlementAdjustmentServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "entitlementMapper", entitlementMapper);
        ReflectionTestUtils.setField(service, "purchaseMapper", purchaseMapper);
        ReflectionTestUtils.setField(service, "usageLogMapper", usageLogMapper);
    }

    @Test
    void nonSuperAdminCannotGrantUnlimitedEntitlement() {
        assertThrows(ApiStatusException.class, () -> service.adjustUnlimited(
                TENANT_ID, USER_ID, 9L, "admin", 1, request("GRANT", "req-role")));

        verify(userMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void grantPreservesDurationBalanceAndDuplicateRequestDoesNotApplyAgain() {
        User user = availableUser();
        NetworkEntitlement entitlement = durationEntitlement();
        UnlimitedEntitlementRequest request = request("GRANT", "req-grant");

        when(userMapper.selectByIdForUpdate(USER_ID)).thenReturn(user);
        when(entitlementMapper.selectByUserIdForUpdate(TENANT_ID, USER_ID))
                .thenReturn(entitlement);
        when(usageLogMapper.selectByRequestIdForUpdate(TENANT_ID, "UNL:req-grant"))
                .thenReturn(Collections.emptyList());
        when(entitlementMapper.updateById(entitlement)).thenReturn(1);
        when(usageLogMapper.insert(any(EntitlementUsageLog.class))).thenReturn(1);

        service.adjustUnlimited(TENANT_ID, USER_ID, 9L, "root", 0, request);

        assertEquals(EntitlementTradeConstants.MODE_UNLIMITED, entitlement.getMode());
        assertEquals(3600L, entitlement.getRemainingSeconds());
        assertEquals(1, entitlement.getStatus());
        assertEquals(EntitlementTradeConstants.MODE_DURATION,
                entitlement.getUnlimitedPreviousMode());
        assertEquals(1, entitlement.getUnlimitedPreviousStatus());

        EntitlementUsageLog storedLog = new EntitlementUsageLog();
        storedLog.setTenantId(TENANT_ID);
        storedLog.setUserId(USER_ID);
        storedLog.setAuthorizationMode(EntitlementTradeConstants.MODE_UNLIMITED);
        storedLog.setChangeSeconds(1L);
        when(usageLogMapper.selectByRequestIdForUpdate(TENANT_ID, "UNL:req-grant"))
                .thenReturn(Collections.singletonList(storedLog));

        service.adjustUnlimited(TENANT_ID, USER_ID, 9L, "root", 0, request);

        verify(entitlementMapper).updateById(entitlement);
        verify(usageLogMapper).insert(any(EntitlementUsageLog.class));
    }

    @Test
    void revokeRestoresPreservedDurationEntitlement() {
        NetworkEntitlement entitlement = durationEntitlement();
        entitlement.setMode(EntitlementTradeConstants.MODE_UNLIMITED);
        entitlement.setUnlimitedPreviousMode(EntitlementTradeConstants.MODE_DURATION);
        entitlement.setUnlimitedPreviousStatus(1);

        when(userMapper.selectByIdForUpdate(USER_ID)).thenReturn(availableUser());
        when(entitlementMapper.selectByUserIdForUpdate(TENANT_ID, USER_ID))
                .thenReturn(entitlement);
        when(usageLogMapper.selectByRequestIdForUpdate(TENANT_ID, "UNL:req-revoke"))
                .thenReturn(Collections.emptyList());
        when(entitlementMapper.updateById(entitlement)).thenReturn(1);
        when(usageLogMapper.insert(any(EntitlementUsageLog.class))).thenReturn(1);

        service.adjustUnlimited(
                TENANT_ID, USER_ID, 9L, "root", 0, request("REVOKE", "req-revoke"));

        assertEquals(EntitlementTradeConstants.MODE_DURATION, entitlement.getMode());
        assertEquals(3600L, entitlement.getRemainingSeconds());
        assertEquals(1, entitlement.getStatus());

        ArgumentCaptor<EntitlementUsageLog> logCaptor =
                ArgumentCaptor.forClass(EntitlementUsageLog.class);
        verify(usageLogMapper).insert(logCaptor.capture());
        assertEquals(-1L, logCaptor.getValue().getChangeSeconds());
        assertEquals(0L, logCaptor.getValue().getAfterSeconds());
    }

    private UnlimitedEntitlementRequest request(String action, String requestId) {
        UnlimitedEntitlementRequest request = new UnlimitedEntitlementRequest();
        request.setRequestId(requestId);
        request.setAction(action);
        request.setReason("运营审批");
        return request;
    }

    private User availableUser() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setStatus(1);
        return user;
    }

    private NetworkEntitlement durationEntitlement() {
        NetworkEntitlement entitlement = new NetworkEntitlement();
        entitlement.setEntitlementId(21L);
        entitlement.setTenantId(TENANT_ID);
        entitlement.setUserId(USER_ID);
        entitlement.setMode(EntitlementTradeConstants.MODE_DURATION);
        entitlement.setRemainingSeconds(3600L);
        entitlement.setStatus(1);
        entitlement.setVersion(0);
        return entitlement;
    }
}
