package com.plagod.service.impl;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.entity.entitlement.EntitlementOrder;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.user.User;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementOrderMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.TradeStatusLogMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementRewardOrderServiceImplTest {

    private static final Long TENANT_ID = 3L;
    private static final Long USER_ID = 7L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private EntitlementOrderMapper orderMapper;
    @Mock
    private NetworkEntitlementMapper entitlementMapper;
    @Mock
    private DurationPurchaseMapper purchaseMapper;
    @Mock
    private EntitlementUsageLogMapper usageLogMapper;
    @Mock
    private TradeStatusLogMapper statusLogMapper;

    private EntitlementRewardOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EntitlementRewardOrderServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "entitlementMapper", entitlementMapper);
        ReflectionTestUtils.setField(service, "purchaseMapper", purchaseMapper);
        ReflectionTestUtils.setField(service, "usageLogMapper", usageLogMapper);
        ReflectionTestUtils.setField(service, "statusLogMapper", statusLogMapper);
    }

    @Test
    void subscriptionRewardUsesCalendarMonthsFromExistingEndTime() {
        User user = new User();
        user.setStatus(1);

        NetworkEntitlement entitlement = new NetworkEntitlement();
        entitlement.setEntitlementId(21L);
        entitlement.setTenantId(TENANT_ID);
        entitlement.setUserId(USER_ID);
        entitlement.setMode(EntitlementTradeConstants.MODE_SUBSCRIPTION);
        entitlement.setSubscriptionStartTime(LocalDateTime.of(2098, 12, 31, 10, 0));
        entitlement.setSubscriptionEndTime(LocalDateTime.of(2099, 1, 31, 10, 0));
        entitlement.setRemainingSeconds(0L);
        entitlement.setStatus(1);
        entitlement.setVersion(0);

        AtomicReference<EntitlementOrder> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        }).when(orderMapper).insertOrResolveExisting(any(EntitlementOrder.class));

        when(orderMapper.selectByUserRequestForUpdate(
                TENANT_ID, USER_ID, "REWARD:reward-month"))
                .thenAnswer(invocation -> inserted.get());
        when(userMapper.selectByIdForUpdate(USER_ID)).thenReturn(user);
        when(entitlementMapper.selectByUserIdForUpdate(TENANT_ID, USER_ID))
                .thenReturn(entitlement);
        when(entitlementMapper.updateById(entitlement)).thenReturn(1);
        when(usageLogMapper.insert(any(EntitlementUsageLog.class))).thenReturn(1);

        EntitlementRewardOrderRequest request = new EntitlementRewardOrderRequest();
        request.setRequestId("reward-month");
        request.setMode(EntitlementTradeConstants.MODE_SUBSCRIPTION);
        request.setGrantMonths(1);
        request.setAmountCents(0L);
        request.setReason("月末顺延验证");

        EntitlementOrderVO result =
                service.create(TENANT_ID, USER_ID, 9L, "root", request);

        assertEquals(1, result.getGrantMonths());
        assertEquals(LocalDateTime.of(2099, 2, 28, 10, 0),
                entitlement.getSubscriptionEndTime());
    }
}
