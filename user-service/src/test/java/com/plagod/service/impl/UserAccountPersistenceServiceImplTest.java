package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.entity.user.User;
import com.plagod.entity.user.UserAccountCommandReceipt;
import com.plagod.mapper.UserAccountCommandReceiptMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.DefaultTenantMembershipOutboxAppender;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountPersistenceServiceImplTest {

    private UserMapper userMapper;
    private UserAccountCommandReceiptMapper receiptMapper;
    private DefaultTenantMembershipOutboxAppender outboxAppender;
    private DefaultTenantMembershipOutboxService outboxService;
    private PlatformTransactionManager transactionManager;
    private UserAccountPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        receiptMapper = mock(UserAccountCommandReceiptMapper.class);
        outboxAppender = mock(DefaultTenantMembershipOutboxAppender.class);
        outboxService = mock(DefaultTenantMembershipOutboxService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service = new UserAccountPersistenceServiceImpl(
                userMapper,
                receiptMapper,
                outboxAppender,
                outboxService,
                transactionManager);
    }

    @Test
    void createPersistsUserOutboxAndReceiptInOrder() {
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMapper.insert(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(7L);
            return 1;
        });
        when(outboxService.isMembershipReady(7L)).thenReturn(false);

        UserAccountCreateResultVO result = service.createAccount(createRequest());

        assertEquals("SUCCESS", result.getStatus());
        assertFalse(result.getReplayed());
        assertEquals(7L, result.getAccount().getUserId());
        assertFalse(result.getAccount().getMembershipReady());

        InOrder writes = inOrder(userMapper, outboxAppender, receiptMapper);
        writes.verify(userMapper).insert(any(User.class));
        writes.verify(outboxAppender).append(
                7L,
                2,
                "register-7",
                "fingerprint-7");
        writes.verify(receiptMapper).insert(any(UserAccountCommandReceipt.class));

        ArgumentCaptor<UserAccountCommandReceipt> receiptCaptor =
                ArgumentCaptor.forClass(UserAccountCommandReceipt.class);
        verify(receiptMapper).insert(receiptCaptor.capture());
        UserAccountCommandReceipt receipt = receiptCaptor.getValue();
        assertEquals("REGISTER", receipt.getCommandType());
        assertEquals("register-7", receipt.getIdempotencyKey());
        assertEquals("fingerprint-7", receipt.getRequestFingerprint());
        assertEquals(7L, receipt.getUserId());
    }

    @Test
    void createReplaysMatchingReceiptWithoutWritingAgain() {
        UserAccountCommandReceipt receipt = receipt(
                "REGISTER",
                "register-7",
                "fingerprint-7",
                7L);
        when(receiptMapper.selectOne(any())).thenReturn(receipt);
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old-hash"));
        when(outboxService.isMembershipReady(7L)).thenReturn(true);

        UserAccountCreateResultVO result = service.createAccount(createRequest());

        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getReplayed());
        verify(userMapper, never()).insert(any());
        verify(outboxAppender, never()).append(
                any(),
                any(),
                any(),
                any());
        verify(receiptMapper, never()).insert(any());
    }

    @Test
    void createRejectsReusedIdempotencyKeyWithDifferentFingerprint() {
        when(receiptMapper.selectOne(any())).thenReturn(receipt(
                "REGISTER",
                "register-7",
                "other-fingerprint",
                7L));

        UserAccountCreateResultVO result = service.createAccount(createRequest());

        assertEquals("FINGERPRINT_CONFLICT", result.getStatus());
        verify(userMapper, never()).selectById(any());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void createReplaysReceiptAfterConcurrentTransactionRollsBack() {
        UserAccountCommandReceipt concurrentReceipt = receipt(
                "REGISTER",
                "register-7",
                "fingerprint-7",
                7L);
        when(receiptMapper.selectOne(any()))
                .thenReturn(null, null, concurrentReceipt);
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("concurrent receipt"));
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old-hash"));
        when(outboxService.isMembershipReady(7L)).thenReturn(false);

        UserAccountCreateResultVO result = service.createAccount(createRequest());

        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getReplayed());
        verify(transactionManager).rollback(any());
    }

    @Test
    void passwordReplaceUsesExpectedHashConditionWithoutLockingMapper() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old-hash"));
        when(userMapper.update(any(), any())).thenReturn(1);

        UserPasswordReplaceResultVO result =
                service.replacePassword(passwordRequest());

        assertEquals("REPLACED", result.getStatus());
        assertFalse(result.getReplayed());
        verify(userMapper, never()).selectByIdForUpdate(any());

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("password"));

        ArgumentCaptor<UserAccountCommandReceipt> receiptCaptor =
                ArgumentCaptor.forClass(UserAccountCommandReceipt.class);
        verify(receiptMapper).insert(receiptCaptor.capture());
        assertEquals(
                "PASSWORD_REPLACE",
                receiptCaptor.getValue().getCommandType());
    }

    @Test
    void passwordReplaceReplaysOnlyMatchingFingerprint() {
        when(receiptMapper.selectOne(any())).thenReturn(receipt(
                "PASSWORD_REPLACE",
                "password-7",
                "password-fingerprint-7",
                7L));

        UserPasswordReplaceResultVO replay =
                service.replacePassword(passwordRequest());
        assertEquals("REPLACED", replay.getStatus());
        assertTrue(replay.getReplayed());

        UserPasswordReplaceRequest conflictRequest = passwordRequest();
        conflictRequest.setRequestFingerprint("other-fingerprint");
        UserPasswordReplaceResultVO conflict =
                service.replacePassword(conflictRequest);
        assertEquals("FINGERPRINT_CONFLICT", conflict.getStatus());
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void passwordReplaceReplaysReceiptAfterConcurrentExpectedHashUpdate() {
        UserAccountCommandReceipt concurrentReceipt = receipt(
                "PASSWORD_REPLACE",
                "password-7",
                "password-fingerprint-7",
                7L);
        when(receiptMapper.selectOne(any()))
                .thenReturn(null, null, concurrentReceipt);
        when(userMapper.selectById(7L)).thenReturn(user(7L, "old-hash"));
        when(userMapper.update(any(), any())).thenReturn(0);

        UserPasswordReplaceResultVO result =
                service.replacePassword(passwordRequest());

        assertEquals("REPLACED", result.getStatus());
        assertTrue(result.getReplayed());
        verify(receiptMapper, never()).insert(any());
    }

    private UserAccountCreateRequest createRequest() {
        UserAccountCreateRequest request = new UserAccountCreateRequest();
        request.setIdempotencyKey("register-7");
        request.setRequestFingerprint("fingerprint-7");
        request.setUsername("alice");
        request.setPasswordHash("encoded-password");
        request.setNickname("Alice");
        request.setEmail("alice@example.com");
        return request;
    }

    private UserPasswordReplaceRequest passwordRequest() {
        UserPasswordReplaceRequest request = new UserPasswordReplaceRequest();
        request.setIdempotencyKey("password-7");
        request.setRequestFingerprint("password-fingerprint-7");
        request.setUserId(7L);
        request.setExpectedPasswordHash("old-hash");
        request.setNewPasswordHash("new-hash");
        return request;
    }

    private User user(Long userId, String password) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername("alice");
        user.setPassword(password);
        user.setNickname("Alice");
        user.setRole(2);
        user.setStatus(1);
        return user;
    }

    private UserAccountCommandReceipt receipt(
            String commandType,
            String idempotencyKey,
            String fingerprint,
            Long userId) {
        UserAccountCommandReceipt receipt = new UserAccountCommandReceipt();
        receipt.setCommandType(commandType);
        receipt.setIdempotencyKey(idempotencyKey);
        receipt.setRequestFingerprint(fingerprint);
        receipt.setUserId(userId);
        receipt.setResultStatus("SUCCEEDED");
        return receipt;
    }
}
