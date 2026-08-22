package com.plagod.service.impl;

import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.entitlement.DurationPurchase;
import com.plagod.entity.entitlement.EntitlementLeaseReceipt;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import com.plagod.entity.entitlement.NetworkEntitlement;
import com.plagod.entity.user.User;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementLeaseReceiptMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.support.NoOpTransactionManager;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
    @Mock
    private EntitlementLeaseReceiptMapper receiptMapper;

    private EntitlementLeaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EntitlementLeaseServiceImpl(
                userMapper,
                entitlementMapper,
                purchaseMapper,
                usageLogMapper,
                receiptMapper,
                new NoOpTransactionManager());
    }

    @Test
    void allowedResultReceiptDeductionAndUsageShareOneTransactionAndReplay() {
        EntitlementLeaseRequest request = request();
        AtomicReference<EntitlementLeaseReceipt> stored =
                installReceiptLifecycle();
        User user = new User();
        user.setStatus(1);
        NetworkEntitlement entitlement = durationEntitlement();
        DurationPurchase purchase = new DurationPurchase();
        purchase.setPurchaseId(51L);
        purchase.setRemainingSeconds(100L);

        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(entitlementMapper.selectByUserIdForUpdate(
                TENANT_ID,
                USER_ID)).thenReturn(entitlement);
        when(purchaseMapper.selectUsableLotsForUpdate(
                TENANT_ID,
                USER_ID)).thenReturn(
                        Collections.singletonList(purchase));
        when(entitlementMapper.deductRemainingSeconds(
                TENANT_ID,
                ENTITLEMENT_ID,
                10L)).thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });
        when(purchaseMapper.updateById(purchase)).thenReturn(1);
        when(usageLogMapper.insert(any(EntitlementUsageLog.class)))
                .thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });

        EntitlementLeaseResult first =
                service.acquireLease("3", request);
        EntitlementLeaseResult duplicate =
                service.acquireLease("3", request);

        assertEquals(Boolean.TRUE, first.getAllowed());
        assertFalse(first.getDuplicate());
        assertEquals(20, first.getTtlSeconds());
        assertEquals(10L, first.getChargedSeconds());
        assertEquals(90L, first.getRemainingSeconds());
        assertTrue(duplicate.getDuplicate());
        assertEquals(first.getAllowed(), duplicate.getAllowed());
        assertEquals(first.getTtlSeconds(), duplicate.getTtlSeconds());
        assertEquals(first.getChargedSeconds(), duplicate.getChargedSeconds());
        assertEquals(first.getRemainingSeconds(), duplicate.getRemainingSeconds());
        assertEquals(first.getReason(), duplicate.getReason());

        EntitlementLeaseReceipt receipt = stored.get();
        assertNotNull(receipt);
        assertEquals("COMPLETED", receipt.getReceiptStatus());
        assertEquals(64, receipt.getRequestFingerprint().length());
        assertEquals(Boolean.TRUE, receipt.getResultAllowed());
        verify(entitlementMapper).deductRemainingSeconds(
                TENANT_ID,
                ENTITLEMENT_ID,
                10L);
        verify(usageLogMapper).insert(any(EntitlementUsageLog.class));
        verify(receiptMapper).insert(any(EntitlementLeaseReceipt.class));
    }

    @Test
    void deniedResultIsStoredAndReplayedWithoutUsage() {
        EntitlementLeaseRequest request = request();
        AtomicReference<EntitlementLeaseReceipt> stored =
                installReceiptLifecycle();
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        EntitlementLeaseResult first =
                service.acquireLease("3", request);
        EntitlementLeaseResult duplicate =
                service.acquireLease("3", request);

        assertEquals(Boolean.FALSE, first.getAllowed());
        assertFalse(first.getDuplicate());
        assertNull(first.getTtlSeconds());
        assertEquals("USER_UNAVAILABLE", first.getReason());
        assertEquals(Boolean.FALSE, duplicate.getAllowed());
        assertTrue(duplicate.getDuplicate());
        assertNull(duplicate.getEntitlementId());
        assertNull(duplicate.getMode());
        assertNull(duplicate.getTtlSeconds());
        assertNull(duplicate.getRemainingSeconds());
        assertEquals("USER_UNAVAILABLE", duplicate.getReason());
        assertEquals(Boolean.FALSE, stored.get().getResultAllowed());
        assertNull(stored.get().getResultTtlSeconds());
        verify(entitlementMapper, never()).selectByUserIdForUpdate(
                anyLong(),
                anyLong());
        verify(usageLogMapper, never()).insert(any());
    }

    @Test
    void unknownModeStoresStableRejectionWithoutIllegalMode() {
        EntitlementLeaseRequest request = request();
        AtomicReference<EntitlementLeaseReceipt> stored =
                installReceiptLifecycle();
        User user = new User();
        user.setStatus(1);
        NetworkEntitlement entitlement = durationEntitlement();
        entitlement.setMode("LEGACY_UNKNOWN");

        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(entitlementMapper.selectByUserIdForUpdate(
                TENANT_ID,
                USER_ID)).thenReturn(entitlement);

        EntitlementLeaseResult first =
                service.acquireLease("3", request);
        EntitlementLeaseResult duplicate =
                service.acquireLease("3", request);

        assertEquals(Boolean.FALSE, first.getAllowed());
        assertFalse(first.getDuplicate());
        assertNull(first.getEntitlementId());
        assertNull(first.getMode());
        assertNull(first.getTtlSeconds());
        assertNull(first.getRemainingSeconds());
        assertNull(first.getSubscriptionEndTime());
        assertEquals(0L, first.getChargedSeconds());
        assertEquals("UNKNOWN_ENTITLEMENT_MODE", first.getReason());

        assertEquals(Boolean.FALSE, duplicate.getAllowed());
        assertTrue(duplicate.getDuplicate());
        assertNull(duplicate.getEntitlementId());
        assertNull(duplicate.getMode());
        assertNull(duplicate.getTtlSeconds());
        assertNull(duplicate.getRemainingSeconds());
        assertNull(duplicate.getSubscriptionEndTime());
        assertEquals(first.getReason(), duplicate.getReason());

        EntitlementLeaseReceipt receipt = stored.get();
        assertEquals("COMPLETED", receipt.getReceiptStatus());
        assertEquals(Boolean.FALSE, receipt.getResultAllowed());
        assertNull(receipt.getResultEntitlementId());
        assertNull(receipt.getResultMode());
        assertNull(receipt.getResultTtlSeconds());
        assertNull(receipt.getResultRemainingSeconds());
        assertNull(receipt.getResultSubscriptionEndTime());
        assertEquals(0L, receipt.getResultChargedSeconds());
        assertEquals(
                "UNKNOWN_ENTITLEMENT_MODE",
                receipt.getResultReason());
        verify(entitlementMapper, never()).deductRemainingSeconds(
                anyLong(),
                anyLong(),
                anyLong());
        verify(usageLogMapper, never()).insert(any());
    }

    @Test
    void everyFrozenFingerprintInputRejectsSameKeyWithDifferentValue() {
        EntitlementLeaseRequest request = request();
        installReceiptLifecycle();
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        service.acquireLease("3", request);

        EntitlementLeaseRequest[] conflicts = {
                copy(request, 22L, USER_ID, 31L, 10L, 60),
                copy(request, ENTITLEMENT_ID, 8L, 31L, 10L, 60),
                copy(request, ENTITLEMENT_ID, USER_ID, 32L, 10L, 60),
                copy(request, ENTITLEMENT_ID, USER_ID, 31L, 9L, 60),
                copy(request, ENTITLEMENT_ID, USER_ID, 31L, 10L, 59)
        };

        for (EntitlementLeaseRequest conflict : conflicts) {
            ApiStatusException exception = assertThrows(
                    ApiStatusException.class,
                    () -> service.acquireLease("3", conflict));
            assertEquals(409, exception.getHttpStatus());
            assertEquals(
                    ApiErrorKey.IDEMPOTENCY_KEY_CONFLICT.value(),
                    exception.getErrorKey());
        }
        verify(userMapper).selectById(USER_ID);
    }

    @Test
    void concurrentUniqueKeyWinnerIsReplayedInFreshTransaction() {
        EntitlementLeaseRequest request = request();
        AtomicReference<EntitlementLeaseReceipt> stored =
                installReceiptLifecycle();
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        service.acquireLease("3", request);
        EntitlementLeaseReceipt winner = stored.get();

        reset(receiptMapper);
        when(receiptMapper.selectByRequest(
                TENANT_ID,
                request.getRequestId()))
                .thenReturn(null, winner);
        when(receiptMapper.insert(any(EntitlementLeaseReceipt.class)))
                .thenThrow(new DuplicateKeyException("concurrent winner"));

        EntitlementLeaseResult duplicate =
                service.acquireLease("3", request);

        assertTrue(duplicate.getDuplicate());
        assertEquals("USER_UNAVAILABLE", duplicate.getReason());
    }

    private AtomicReference<EntitlementLeaseReceipt>
            installReceiptLifecycle() {
        AtomicReference<EntitlementLeaseReceipt> stored =
                new AtomicReference<>();
        when(receiptMapper.selectByRequest(
                TENANT_ID,
                "session-lease-31"))
                .thenAnswer(invocation -> stored.get());
        when(receiptMapper.insert(any(EntitlementLeaseReceipt.class)))
                .thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    EntitlementLeaseReceipt receipt =
                            invocation.getArgument(0);
                    receipt.setReceiptId(91L);
                    stored.set(receipt);
                    return 1;
                });
        when(receiptMapper.updateById(
                any(EntitlementLeaseReceipt.class)))
                .thenAnswer(invocation -> {
                    assertTrue(transactionActive());
                    return 1;
                });
        return stored;
    }

    private EntitlementLeaseRequest request() {
        return copy(
                null,
                ENTITLEMENT_ID,
                USER_ID,
                31L,
                10L,
                60);
    }

    private EntitlementLeaseRequest copy(
            EntitlementLeaseRequest source,
            Long entitlementId,
            Long userId,
            Long sessionId,
            Long usageSeconds,
            Integer requestedTtlSeconds) {
        EntitlementLeaseRequest request =
                new EntitlementLeaseRequest();
        request.setEntitlementId(entitlementId);
        request.setRequestId(source == null
                ? "session-lease-31"
                : source.getRequestId());
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setUsageSeconds(usageSeconds);
        request.setRequestedTtlSeconds(requestedTtlSeconds);
        return request;
    }

    private NetworkEntitlement durationEntitlement() {
        NetworkEntitlement entitlement =
                new NetworkEntitlement();
        entitlement.setEntitlementId(ENTITLEMENT_ID);
        entitlement.setTenantId(TENANT_ID);
        entitlement.setUserId(USER_ID);
        entitlement.setMode("DURATION");
        entitlement.setRemainingSeconds(100L);
        entitlement.setStatus(1);
        return entitlement;
    }

    private boolean transactionActive() {
        return TransactionSynchronizationManager
                .isActualTransactionActive();
    }
}
