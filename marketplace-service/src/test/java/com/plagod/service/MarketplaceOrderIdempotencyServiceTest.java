package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;
import com.plagod.entity.MarketplaceOrder;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.MarketplaceFulfillmentMapper;
import com.plagod.mapper.MarketplaceOrderMapper;
import com.plagod.test.RecordingTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketplaceOrderIdempotencyServiceTest {

    private MarketplaceOrderMapper orderMapper;
    private MarketplaceFulfillmentMapper fulfillmentMapper;
    private RecordingTransactionManager transactionManager;
    private MarketplaceOrderIdempotencyService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(MarketplaceOrderMapper.class);
        fulfillmentMapper = mock(MarketplaceFulfillmentMapper.class);
        transactionManager = new RecordingTransactionManager();
        service = new MarketplaceOrderIdempotencyService(
                orderMapper,
                fulfillmentMapper,
                transactionManager);
    }

    @Test
    void sameKeyAndFingerprintReplaysFirstOrderWithoutCreatingAgain() {
        MarketplaceOrderCreation creation =
                creation("req-market-1", fingerprint('a'));
        MarketplaceOrder order = creation.getOrder();

        when(orderMapper.selectByIdempotencyKey(
                10L, "USER", "USER:20", "req-market-1"))
                .thenReturn(null, order);
        stubSuccessfulAtomicInsert();

        MarketplaceOrderReplayResult first =
                service.createOrReplay(creation);
        MarketplaceOrderCreation replayCreation =
                creation("req-market-1", fingerprint('a'));
        MarketplaceOrderReplayResult replay =
                service.createOrReplay(replayCreation);

        assertFalse(first.isDuplicate());
        assertTrue(replay.isDuplicate());
        assertSame(order, replay.getOrder());
        verify(orderMapper).insert(order);
        verify(fulfillmentMapper).insert(creation.getFulfillment());
        assertFalse(TransactionSynchronizationManager
                .isActualTransactionActive());
    }

    @Test
    void sameKeyWithDifferentFingerprintReturnsFrozenConflict() {
        MarketplaceOrderCreation creation =
                creation("req-market-2", fingerprint('b'));
        MarketplaceOrder existing =
                order("req-market-2", fingerprint('c'));
        existing.setOrderId(102L);

        when(orderMapper.selectByIdempotencyKey(
                10L, "USER", "USER:20", "req-market-2"))
                .thenReturn(existing);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.createOrReplay(creation));

        assertEquals(409, exception.getHttpStatus());
        assertEquals(ApiErrorKey.IDEMPOTENCY_KEY_CONFLICT.value(),
                exception.getErrorKey());
        verify(orderMapper, never()).insert(any());
        verifyNoInteractions(fulfillmentMapper);
    }

    @Test
    void concurrentUniqueKeyWinnerIsReplayedAfterLoserRollsBack() {
        MarketplaceOrderCreation creation =
                creation("req-market-3", fingerprint('d'));
        MarketplaceOrder winner =
                order("req-market-3", fingerprint('d'));
        winner.setOrderId(103L);

        when(orderMapper.selectByIdempotencyKey(
                10L, "USER", "USER:20", "req-market-3"))
                .thenReturn(null, winner);
        when(orderMapper.insert(creation.getOrder()))
                .thenThrow(new DuplicateKeyException(
                        "unique request key"));

        MarketplaceOrderReplayResult result =
                service.createOrReplay(creation);

        assertTrue(result.isDuplicate());
        assertSame(winner, result.getOrder());
        verifyNoInteractions(fulfillmentMapper);
    }

    @Test
    void orderAndFulfillmentOutboxAreAppendedInSameLocalTransaction() {
        MarketplaceOrderCreation creation =
                creation("req-market-4", fingerprint('e'));
        AtomicReference<Long> orderTransactionId =
                new AtomicReference<>();
        AtomicReference<Long> fulfillmentTransactionId =
                new AtomicReference<>();
        when(orderMapper.selectByIdempotencyKey(
                10L, "USER", "USER:20", "req-market-4"))
                .thenReturn(null);
        when(orderMapper.insert(creation.getOrder()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    orderTransactionId.set(
                            transactionManager.currentTransactionId());
                    creation.getOrder().setOrderId(104L);
                    return 1;
                });
        when(fulfillmentMapper.insert(creation.getFulfillment()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    fulfillmentTransactionId.set(
                            transactionManager.currentTransactionId());
                    creation.getFulfillment().setFulfillmentId(304L);
                    return 1;
                });

        MarketplaceOrderReplayResult result =
                service.createOrReplay(creation);

        assertFalse(result.isDuplicate());
        assertNotNull(orderTransactionId.get());
        assertEquals(
                orderTransactionId.get(),
                fulfillmentTransactionId.get());
        assertEquals(1, transactionManager.getCommitCount());
        assertEquals(0, transactionManager.getRollbackCount());
    }

    @Test
    void fulfillmentInsertFailureRollsBackOrderTransaction() {
        MarketplaceOrderCreation creation =
                creation("req-market-5", fingerprint('f'));
        when(orderMapper.selectByIdempotencyKey(
                10L, "USER", "USER:20", "req-market-5"))
                .thenReturn(null);
        when(orderMapper.insert(creation.getOrder()))
                .thenAnswer(invocation -> {
                    creation.getOrder().setOrderId(105L);
                    return 1;
                });
        when(fulfillmentMapper.insert(creation.getFulfillment()))
                .thenThrow(new IllegalStateException(
                        "outbox insert failed"));

        assertThrows(
                IllegalStateException.class,
                () -> service.createOrReplay(creation));

        assertEquals(0, transactionManager.getCommitCount());
        assertEquals(1, transactionManager.getRollbackCount());
    }

    private void stubSuccessfulAtomicInsert() {
        when(orderMapper.insert(any(MarketplaceOrder.class)))
                .thenAnswer(invocation -> {
                    MarketplaceOrder order =
                            invocation.getArgument(0);
                    order.setOrderId(101L);
                    return 1;
                });
        when(fulfillmentMapper.insert(
                any(MarketplaceFulfillment.class)))
                .thenAnswer(invocation -> {
                    MarketplaceFulfillment fulfillment =
                            invocation.getArgument(0);
                    fulfillment.setFulfillmentId(301L);
                    return 1;
                });
    }

    private MarketplaceOrderCreation creation(
            String requestId,
            String fingerprint) {
        MarketplaceFulfillment fulfillment =
                new MarketplaceFulfillment();
        fulfillment.setOrderItemId(201L);
        fulfillment.setEventKey("event-" + requestId);
        fulfillment.setFulfillmentType("PERSONAL_ENTITLEMENT");
        fulfillment.setFulfillmentMode("TARGET_DOMAIN");
        fulfillment.setTargetBusinessKey("ENTITLEMENT:201");
        return new MarketplaceOrderCreation(
                order(requestId, fingerprint),
                fulfillment);
    }

    private MarketplaceOrder order(String requestId, String fingerprint) {
        MarketplaceOrder order = new MarketplaceOrder();
        order.setOrderNo("MO-" + requestId);
        order.setTenantId(10L);
        order.setSubjectType("USER");
        order.setUserId(20L);
        order.setActorUserId(20L);
        order.setClientRequestId(requestId);
        order.setRequestFingerprint(fingerprint);
        return order;
    }

    private String fingerprint(char value) {
        StringBuilder builder = new StringBuilder(64);
        for (int index = 0; index < 64; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
