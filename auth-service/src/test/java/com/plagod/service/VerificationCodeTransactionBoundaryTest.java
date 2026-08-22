package com.plagod.service;

import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.entity.auth.VerifyCode;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.ratelimit.VerificationCodeRedisRateLimiter;
import com.plagod.sender.VerifyCodeSender;
import com.plagod.sender.phone.PhoneVerificationCheckResult;
import com.plagod.sender.phone.PhoneVerificationProvider;
import com.plagod.sender.phone.PhoneVerificationProviderRegistry;
import com.plagod.sender.phone.PhoneVerificationSendResult;
import com.plagod.service.impl.VerificationCodeServiceImpl;
import com.plagod.transaction.TestTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeTransactionBoundaryTest {

    private VerifyCodeMapper verifyCodeMapper;
    private PhoneVerificationProvider provider;
    private PhoneVerificationProviderRegistry registry;
    private PhoneVerificationProperties properties;
    private TestTransactionManager transactionManager;
    private VerificationCodeStateService service;

    @BeforeEach
    void setUp() {
        verifyCodeMapper = mock(VerifyCodeMapper.class);
        registry = mock(PhoneVerificationProviderRegistry.class);
        provider = mock(PhoneVerificationProvider.class);
        properties =
                new PhoneVerificationProperties(mock(Environment.class));
        transactionManager = new TestTransactionManager();

        when(registry.get("aliyun-number-auth")).thenReturn(provider);
        when(verifyCodeMapper.updateById(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return 1;
        });
        service = new VerificationCodeStateService(
                verifyCodeMapper,
                registry,
                properties,
                transactionManager);
    }

    @Test
    void consumptionEntryKeepsProviderOutsideAndConsumeInsideTransaction() {
        VerifyCode record = phoneRecord();
        when(verifyCodeMapper.selectLatestUsableForUpdate(
                record.getTarget(),
                record.getScene())).thenReturn(record);
        when(verifyCodeMapper.selectByIdForUpdate(record.getId()))
                .thenReturn(record);
        allowClaim(record);
        when(provider.verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("ABC123"),
                any())).thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return providerResult(true);
                });
        when(verifyCodeMapper.consumeVerifiedCode(
                eq(record.getId()),
                any(),
                eq("192.168.1.23"),
                eq("command-7"))).thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return 1;
                });

        VerificationCodeServiceImpl commandService =
                new VerificationCodeServiceImpl(
                        new VerificationCodeProperties(),
                        properties,
                        verifyCodeMapper,
                        mock(VerifyCodeSender.class),
                        registry,
                        service,
                        mock(VerificationCodeRedisRateLimiter.class),
                        transactionManager);

        commandService.consumeCodeForRequest(
                record.getTarget(),
                record.getScene(),
                "ABC123",
                "192.168.1.23",
                "command-7");
    }

    @Test
    void providerSuccessSuspendsCallingTransactionAndPersistsResult() {
        VerifyCode record = phoneRecord();
        when(verifyCodeMapper.selectLatestUsableForUpdate(
                record.getTarget(),
                record.getScene())).thenReturn(record);
        when(verifyCodeMapper.selectByIdForUpdate(record.getId()))
                .thenReturn(record);
        allowClaim(record);
        when(provider.verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("ABC123"),
                any())).thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return providerResult(true);
                });

        VerificationCodeStateService.Decision decision =
                new TransactionTemplate(transactionManager).execute(status -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return service.verifyAndRemember(
                            record.getTarget(),
                            record.getScene(),
                            "ABC123");
                });

        assertTrue(decision.isVerified());
    }

    @Test
    void providerRejectionIsPersistedWithoutAcceptingCode() {
        VerifyCode record = phoneRecord();
        when(verifyCodeMapper.selectLatestUsableForUpdate(
                record.getTarget(),
                record.getScene())).thenReturn(record);
        when(verifyCodeMapper.selectByIdForUpdate(record.getId()))
                .thenReturn(record);
        allowClaim(record);
        when(provider.verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("WRONG1"),
                any())).thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return providerResult(false);
                });

        VerificationCodeStateService.Decision decision =
                service.verifyAndRemember(
                        record.getTarget(),
                        record.getScene(),
                        "WRONG1");

        assertFalse(decision.isVerified());
        assertFalse(decision.isProviderUnavailable());
    }

    @Test
    void concurrentVerificationClaimsOnceAndCallsProviderOnce()
            throws Exception {
        VerifyCode record = phoneRecord();
        AtomicReference<String> owner = new AtomicReference<>();
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch finishProvider = new CountDownLatch(1);
        when(verifyCodeMapper.selectLatestUsableForUpdate(
                record.getTarget(),
                record.getScene())).thenReturn(record);
        when(verifyCodeMapper.tryClaimVerification(
                eq(record.getId()),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    String candidate = invocation.getArgument(1);
                    if (!owner.compareAndSet(null, candidate)) {
                        return 0;
                    }
                    record.setVerifyClaimOwner(candidate);
                    record.setVerifyClaimedTime(invocation.getArgument(2));
                    record.setVerifyLeaseUntil(invocation.getArgument(3));
                    return 1;
                });
        when(verifyCodeMapper.selectByIdForUpdate(record.getId()))
                .thenReturn(record);
        when(verifyCodeMapper.releaseVerificationClaim(
                eq(record.getId()),
                anyString())).thenAnswer(invocation ->
                owner.compareAndSet(invocation.getArgument(1), null)
                        ? 1 : 0);
        when(provider.verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("ABC123"),
                any())).thenAnswer(invocation -> {
                    providerEntered.countDown();
                    assertTrue(finishProvider.await(5, TimeUnit.SECONDS));
                    return providerResult(true);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<VerificationCodeStateService.Decision> first =
                    executor.submit(() -> service.verifyAndRemember(
                            record.getTarget(),
                            record.getScene(),
                            "ABC123"));
            assertTrue(providerEntered.await(5, TimeUnit.SECONDS));

            VerificationCodeStateService.Decision concurrent =
                    service.verifyAndRemember(
                            record.getTarget(),
                            record.getScene(),
                            "ABC123");
            assertTrue(concurrent.isProviderUnavailable());

            finishProvider.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).isVerified());
        } finally {
            finishProvider.countDown();
            executor.shutdownNow();
        }

        verify(provider, times(1)).verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("ABC123"),
                any());
    }

    @Test
    void expiredVerificationClaimCanBeReclaimed() {
        VerifyCode record = phoneRecord();
        record.setVerifyClaimOwner("expired-owner");
        record.setVerifyClaimedTime(
                LocalDateTime.now().minusMinutes(2));
        record.setVerifyLeaseUntil(
                LocalDateTime.now().minusMinutes(1));
        AtomicReference<String> reclaimedOwner = new AtomicReference<>();
        when(verifyCodeMapper.selectLatestUsableForUpdate(
                record.getTarget(),
                record.getScene())).thenReturn(record);
        when(verifyCodeMapper.tryClaimVerification(
                eq(record.getId()),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    String owner = invocation.getArgument(1);
                    assertNotEquals("expired-owner", owner);
                    reclaimedOwner.set(owner);
                    record.setVerifyClaimOwner(owner);
                    record.setVerifyClaimedTime(invocation.getArgument(2));
                    record.setVerifyLeaseUntil(invocation.getArgument(3));
                    return 1;
                });
        when(verifyCodeMapper.selectByIdForUpdate(record.getId()))
                .thenReturn(record);
        when(verifyCodeMapper.releaseVerificationClaim(
                eq(record.getId()),
                anyString())).thenReturn(1);
        when(provider.verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("ABC123"),
                any())).thenReturn(providerResult(true));

        VerificationCodeStateService.Decision decision =
                service.verifyAndRemember(
                        record.getTarget(),
                        record.getScene(),
                        "ABC123");

        assertTrue(decision.isVerified());
        assertTrue(reclaimedOwner.get() != null);
        verify(verifyCodeMapper).releaseVerificationClaim(
                record.getId(),
                reclaimedOwner.get());
    }

    @Test
    void staleVerificationOwnerCannotFinalizeNewClaim() {
        VerifyCode record = phoneRecord();
        AtomicReference<String> staleOwner = new AtomicReference<>();
        when(verifyCodeMapper.selectLatestUsableForUpdate(
                record.getTarget(),
                record.getScene())).thenReturn(record);
        when(verifyCodeMapper.tryClaimVerification(
                eq(record.getId()),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    String owner = invocation.getArgument(1);
                    staleOwner.set(owner);
                    record.setVerifyClaimOwner(owner);
                    record.setVerifyClaimedTime(invocation.getArgument(2));
                    record.setVerifyLeaseUntil(invocation.getArgument(3));
                    return 1;
                });
        when(provider.verify(
                eq(record.getTarget()),
                eq(record.getProviderOutId()),
                eq("ABC123"),
                any())).thenAnswer(invocation -> {
                    record.setVerifyClaimOwner("replacement-owner");
                    record.setVerifyClaimedTime(LocalDateTime.now());
                    record.setVerifyLeaseUntil(
                            LocalDateTime.now().plusMinutes(1));
                    return providerResult(true);
                });
        when(verifyCodeMapper.selectByIdForUpdate(record.getId()))
                .thenReturn(record);

        VerificationCodeStateService.Decision decision =
                service.verifyAndRemember(
                        record.getTarget(),
                        record.getScene(),
                        "ABC123");

        assertTrue(decision.isProviderUnavailable());
        verify(verifyCodeMapper, never()).releaseVerificationClaim(
                record.getId(),
                staleOwner.get());
        verify(verifyCodeMapper, never()).updateById(any());
    }

    @Test
    void phoneSendCommitsPreparationBeforeProviderAndFinalizesSeparately() {
        VerificationCodeRedisRateLimiter rateLimiter =
                mock(VerificationCodeRedisRateLimiter.class);
        when(rateLimiter.acquire(anyString(), anyString(), any(), any()))
                .thenReturn(true);
        when(registry.current()).thenReturn(provider);
        when(provider.providerName()).thenReturn("local");
        when(verifyCodeMapper.insert(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            VerifyCode record = invocation.getArgument(0);
            record.setId(9L);
            return 1;
        });
        when(verifyCodeMapper.finalizeSendSuccess(any()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    assertEquals(1, transactionManager.getCommitCount());
                    return 1;
                });
        when(provider.send(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    assertEquals(1, transactionManager.getCommitCount());
                    return PhoneVerificationSendResult.builder()
                            .successful(true)
                            .provider("local")
                            .outId(invocation.getArgument(2))
                            .providerCode("OK")
                            .localCodeHash("local-code-hash")
                            .build();
                });
        VerificationCodeServiceImpl commandService =
                new VerificationCodeServiceImpl(
                        new VerificationCodeProperties(),
                        properties,
                        verifyCodeMapper,
                        mock(VerifyCodeSender.class),
                        registry,
                        service,
                        rateLimiter,
                        transactionManager);

        new TransactionTemplate(transactionManager).execute(status -> {
            commandService.sendCode(
                    "13800138000",
                    "login",
                    "192.168.1.23");
            return null;
        });

        assertEquals(3, transactionManager.getCommitCount());
        verify(provider).send(
                eq("13800138000"),
                eq("login"),
                anyString());
    }

    private void allowClaim(VerifyCode record) {
        when(verifyCodeMapper.tryClaimVerification(
                eq(record.getId()),
                anyString(),
                any(),
                any())).thenAnswer(invocation -> {
                    record.setVerifyClaimOwner(invocation.getArgument(1));
                    record.setVerifyClaimedTime(invocation.getArgument(2));
                    record.setVerifyLeaseUntil(invocation.getArgument(3));
                    return 1;
                });
        when(verifyCodeMapper.releaseVerificationClaim(
                eq(record.getId()),
                anyString())).thenReturn(1);
    }

    private VerifyCode phoneRecord() {
        VerifyCode record = new VerifyCode();
        record.setId(7L);
        record.setTarget("13800138000");
        record.setTargetType("phone");
        record.setScene("step_up");
        record.setVerificationProvider("aliyun-number-auth");
        record.setProviderOutId("provider-out-7");
        record.setSendStatus(1);
        record.setVerifyStatus(0);
        record.setVerifyAttemptCount(0);
        record.setStatus(0);
        record.setExpireTime(LocalDateTime.now().plusMinutes(5));
        return record;
    }

    private PhoneVerificationCheckResult providerResult(
            boolean verified) {
        return PhoneVerificationCheckResult.builder()
                .requestSuccessful(true)
                .verified(verified)
                .provider("aliyun-number-auth")
                .outId("provider-out-7")
                .providerCode("OK")
                .providerResult(verified ? "PASS" : "FAIL")
                .message(verified ? null : "验证码错误")
                .build();
    }
}
