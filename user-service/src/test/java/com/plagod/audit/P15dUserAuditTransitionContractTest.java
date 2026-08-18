package com.plagod.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.EntitlementAdjustmentRequest;
import com.plagod.dto.entitlement.EntitlementRewardOrderRequest;
import com.plagod.dto.entitlement.RefundApplyRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.entitlement.UnlimitedEntitlementRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.entity.entitlement.RefundRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.RefundRecordMapper;
import com.plagod.mapper.TradeStatusLogMapper;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.service.impl.EntitlementAdjustmentServiceImpl;
import com.plagod.service.impl.EntitlementRewardOrderServiceImpl;
import com.plagod.service.impl.RefundServiceImpl;
import com.plagod.service.impl.UserManageServiceImpl;
import com.plagod.vo.entitlement.RefundVO;
import com.plagod.vo.user.EntitlementSnapshotVO;
import com.plagod.vo.user.UserVO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.plagod.security.UserRequestContextPolicyTest.platformRequest;
import static com.plagod.security.UserRequestContextPolicyTest.platformTenantRequest;
import static com.plagod.security.UserRequestContextPolicyTest.tenantAdminRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P15dUserAuditTransitionContractTest {

    private static final String CANARY_PASSWORD =
            "canary-password-secret";
    private static final String CANARY_TOKEN =
            "canary-token-secret";
    private static final String CANARY_CONTENT =
            "canary-content-secret";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void frozenUserEntriesExposeCompleteSafeAuditMetadata() throws Exception {
        List<Method> methods = new ArrayList<>();
        methods.add(assertAudit(
                UserManageServiceImpl.class,
                "updateUser",
                "user.update",
                "USER",
                Audited.Scope.CONTEXT,
                Audited.TenantIdSource.REQUEST,
                Long.class,
                UserUpdateDTO.class,
                Integer.class));
        methods.add(assertAudit(
                UserManageServiceImpl.class,
                "updateStatus",
                "user.status",
                "USER",
                Audited.Scope.PLATFORM,
                Audited.TenantIdSource.REQUEST,
                Long.class,
                UserStatusDTO.class));
        methods.add(assertAudit(
                UserManageServiceImpl.class,
                "deleteUser",
                "user.delete",
                "USER",
                Audited.Scope.PLATFORM,
                Audited.TenantIdSource.REQUEST,
                Long.class));
        methods.add(assertAudit(
                UserManageServiceImpl.class,
                "purgeUser",
                "user.purge",
                "USER",
                Audited.Scope.PLATFORM,
                Audited.TenantIdSource.REQUEST,
                Long.class));
        methods.add(assertAudit(
                RefundServiceImpl.class,
                "apply",
                "refund.apply",
                "REFUND",
                Audited.Scope.TENANT,
                Audited.TenantIdSource.ARGUMENT,
                Long.class,
                Long.class,
                RefundApplyRequest.class));
        methods.add(assertAudit(
                RefundServiceImpl.class,
                "review",
                "refund.review",
                "REFUND",
                Audited.Scope.TENANT,
                Audited.TenantIdSource.ARGUMENT,
                Long.class,
                String.class,
                Long.class,
                String.class,
                RefundReviewRequest.class));
        methods.add(assertAudit(
                RefundServiceImpl.class,
                "handleChannelResult",
                "refund.channel.result",
                "REFUND",
                Audited.Scope.TENANT,
                Audited.TenantIdSource.REQUEST,
                VerifiedRefundResult.class));
        methods.add(assertAudit(
                EntitlementRewardOrderServiceImpl.class,
                "create",
                "entitlement.reward-order.create",
                "USER_ENTITLEMENT",
                Audited.Scope.TENANT,
                Audited.TenantIdSource.ARGUMENT,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                EntitlementRewardOrderRequest.class));
        methods.add(assertAudit(
                EntitlementAdjustmentServiceImpl.class,
                "adjust",
                "entitlement.adjust",
                "USER_ENTITLEMENT",
                Audited.Scope.TENANT,
                Audited.TenantIdSource.ARGUMENT,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                EntitlementAdjustmentRequest.class));
        methods.add(assertAudit(
                EntitlementAdjustmentServiceImpl.class,
                "adjustUnlimited",
                "entitlement.unlimited.adjust",
                "USER_ENTITLEMENT",
                Audited.Scope.TENANT,
                Audited.TenantIdSource.ARGUMENT,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                Integer.class,
                UnlimitedEntitlementRequest.class));

        assertEquals(10, methods.size());
        Set<String> actions = new HashSet<>();
        for (Method method : methods) {
            Audited audited = method.getAnnotation(Audited.class);
            assertTrue(actions.add(audited.action()));
            assertTrue(hasTarget(method, audited));
            assertSafeAllowlist(method);
        }

        assertTenantArgument(methods.get(4), 0);
        assertTenantArgument(methods.get(5), 0);
        assertTenantArgument(methods.get(7), 0);
        assertTenantArgument(methods.get(8), 0);
        assertTenantArgument(methods.get(9), 0);
    }

    @Test
    void trustedPlatformTenantAndManagedScopesExcludeCanaryPayloads()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);

        UserUpdateDTO userUpdate = new UserUpdateDTO();
        userUpdate.setEmail(CANARY_PASSWORD);
        userUpdate.setAvatar(CANARY_TOKEN);
        userUpdate.setNickname(CANARY_CONTENT);
        bind(platformRequest());
        aspect.around(joinPoint(
                new UserManageServiceImpl(),
                "updateUser",
                new Class<?>[]{
                        Long.class,
                        UserUpdateDTO.class,
                        Integer.class
                },
                new Object[]{7L, userUpdate, 0},
                new UserVO(),
                null));

        AuditWriteRecord platform = records.records.get(0);
        assertEquals("PLATFORM", platform.getScopeType());
        assertEquals(1L, platform.getOperatorId());
        assertEquals("USER:7", platform.getTarget());
        assertTrue(platform.getDetail().contains(
                "\"fields\":{\"operatorRole\":0}"));
        assertNoCanary(platform.getDetail());

        RefundReviewRequest review = new RefundReviewRequest();
        review.setDecision("REJECT");
        review.setComment(CANARY_CONTENT);
        bind(tenantAdminRequest(42L, 9L));
        aspect.around(joinPoint(
                new RefundServiceImpl(),
                "review",
                new Class<?>[]{
                        Long.class,
                        String.class,
                        Long.class,
                        String.class,
                        RefundReviewRequest.class
                },
                new Object[]{
                        9L,
                        "REF-42",
                        42L,
                        CANARY_TOKEN,
                        review
                },
                new RefundVO(),
                null));

        AuditWriteRecord tenant = records.records.get(1);
        assertEquals(9L, tenant.getTenantId());
        assertEquals("TENANT", tenant.getScopeType());
        assertEquals(42L, tenant.getOperatorId());
        assertEquals("REFUND:REF-42", tenant.getTarget());
        assertTrue(tenant.getDetail().contains(
                "\"fields\":{\"reviewerId\":42}"));
        assertFalse(tenant.getDetail().contains("\"platformManaged\":true"));
        assertNoCanary(tenant.getDetail());

        VerifiedRefundResult channelResult = new VerifiedRefundResult();
        channelResult.setRefundNo("REF-42");
        channelResult.setChannel("LOCAL_DEMO");
        channelResult.setEventId(CANARY_TOKEN);
        channelResult.setChannelRefundNo("CHANNEL-42");
        channelResult.setPayloadHash(CANARY_PASSWORD);
        channelResult.setSuccess(false);
        channelResult.setFailureMessage(CANARY_CONTENT);
        aspect.around(joinPoint(
                new RefundServiceImpl(),
                "handleChannelResult",
                new Class<?>[]{VerifiedRefundResult.class},
                new Object[]{channelResult},
                new RefundVO(),
                null));

        AuditWriteRecord channel = records.records.get(2);
        assertEquals(9L, channel.getTenantId());
        assertEquals("REFUND:channel-result", channel.getTarget());
        assertNoCanary(channel.getDetail());

        UnlimitedEntitlementRequest unlimited =
                new UnlimitedEntitlementRequest();
        unlimited.setRequestId(CANARY_TOKEN);
        unlimited.setAction("GRANT");
        unlimited.setReason(CANARY_CONTENT);
        bind(platformTenantRequest(1L, 31L));
        aspect.around(joinPoint(
                new EntitlementAdjustmentServiceImpl(),
                "adjustUnlimited",
                new Class<?>[]{
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        Integer.class,
                        UnlimitedEntitlementRequest.class
                },
                new Object[]{
                        31L,
                        7L,
                        1L,
                        CANARY_PASSWORD,
                        0,
                        unlimited
                },
                new EntitlementSnapshotVO(),
                null));

        AuditWriteRecord managed = records.records.get(3);
        assertEquals(31L, managed.getTenantId());
        assertEquals("TENANT", managed.getScopeType());
        assertEquals(1L, managed.getOperatorId());
        assertEquals("USER_ENTITLEMENT:7", managed.getTarget());
        assertTrue(managed.getDetail().contains(
                "\"contextType\":\"PLATFORM_TENANT\""));
        assertTrue(managed.getDetail().contains(
                "\"platformManaged\":true"));
        assertTrue(managed.getDetail().contains(
                "\"operatorId\":1"));
        assertTrue(managed.getDetail().contains(
                "\"operatorRole\":0"));
        assertNoCanary(managed.getDetail());
    }

    @Test
    void deniedAndFailedAuditUseFixedKeysWithoutFailureContent()
            throws Throwable {
        RecordingAuditWriter records = new RecordingAuditWriter();
        AuditAspect aspect = aspect(records);
        bind(platformRequest());
        UserStatusDTO request = new UserStatusDTO();
        request.setStatus(0);

        ProceedingJoinPoint denied = joinPoint(
                new UserManageServiceImpl(),
                "updateStatus",
                new Class<?>[]{Long.class, UserStatusDTO.class},
                new Object[]{7L, request},
                null,
                ApiStatusException.forbidden(CANARY_PASSWORD));
        assertThrows(ApiStatusException.class, () -> aspect.around(denied));

        ProceedingJoinPoint failed = joinPoint(
                new UserManageServiceImpl(),
                "updateStatus",
                new Class<?>[]{Long.class, UserStatusDTO.class},
                new Object[]{7L, request},
                null,
                new IllegalStateException(
                        CANARY_TOKEN + CANARY_CONTENT));
        assertThrows(IllegalStateException.class, () -> aspect.around(failed));

        assertTrue(records.records.get(0).getDetail().contains(
                "\"outcome\":\"DENIED\""));
        assertTrue(records.records.get(0).getDetail().contains(
                "\"errorKey\":\"PERMISSION_DENIED\""));
        assertTrue(records.records.get(1).getDetail().contains(
                "\"outcome\":\"FAILED\""));
        assertTrue(records.records.get(1).getDetail().contains(
                "\"errorKey\":\"INTERNAL_ERROR\""));
        assertNoCanary(records.records.get(0).getDetail());
        assertNoCanary(records.records.get(1).getDetail());
    }

    @Test
    void refundTransitionRejectsDuplicateEventKey() {
        ApiStatusException exception = transitionFailure(
                true,
                EntitlementTradeConstants.REFUND_PROCESSING,
                2);
        assertEquals(409, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("已处理"));
    }

    @Test
    void refundTransitionRejectsVersionConflict() {
        ApiStatusException exception = transitionFailure(
                false,
                EntitlementTradeConstants.REFUND_REQUESTED,
                2);
        assertEquals(409, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("版本冲突"));
    }

    @Test
    void refundTransitionRejectsIllegalState() {
        ApiStatusException exception = transitionFailure(
                false,
                EntitlementTradeConstants.REFUND_REJECTED,
                1);
        assertEquals(409, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("不允许"));
    }

    private Method assertAudit(
            Class<?> type,
            String methodName,
            String action,
            String targetType,
            Audited.Scope scope,
            Audited.TenantIdSource tenantIdSource,
            Class<?>... parameterTypes) throws Exception {
        Method method = type.getMethod(methodName, parameterTypes);
        Audited audited = method.getAnnotation(Audited.class);
        assertNotNull(audited);
        assertEquals(action, audited.action());
        assertEquals(targetType, audited.targetType());
        assertEquals(scope, audited.scope());
        assertEquals(tenantIdSource, audited.tenantIdSource());
        assertTrue(audited.recordDenied());
        assertTrue(audited.recordFailed());
        assertFalse(audited.includeArgs());
        assertFalse(audited.includeResult());
        return method;
    }

    private boolean hasTarget(Method method, Audited audited) {
        int marked = 0;
        for (Annotation[] annotations : method.getParameterAnnotations()) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == AuditTargetId.class) {
                    marked++;
                }
            }
        }
        return marked == 1 && audited.target().isEmpty()
                || marked == 0 && !audited.target().isEmpty();
    }

    private void assertSafeAllowlist(Method method) {
        Annotation[][] annotations = method.getParameterAnnotations();
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (!(annotation instanceof AuditDetail)) {
                    continue;
                }
                AuditDetail detail = (AuditDetail) annotation;
                String key = detail.value().toLowerCase();
                assertFalse(key.contains("password"));
                assertFalse(key.contains("token"));
                assertFalse(key.contains("content"));
                assertTrue(isSafeScalar(parameterTypes[index]));
            }
        }
    }

    private boolean isSafeScalar(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || type == Character.class
                || type == String.class
                || type.isEnum();
    }

    private void assertTenantArgument(Method method, int index) {
        assertNotNull(method.getParameterAnnotations()[index]);
        boolean found = false;
        for (Annotation annotation :
                method.getParameterAnnotations()[index]) {
            found |= annotation.annotationType() == AuditTenantId.class;
        }
        assertTrue(found);
    }

    private ApiStatusException transitionFailure(
            boolean duplicateEvent,
            String currentStatus,
            int currentVersion) {
        RefundRecordMapper refundMapper = mock(RefundRecordMapper.class);
        TradeStatusLogMapper statusLogMapper =
                mock(TradeStatusLogMapper.class);
        RefundServiceImpl service = new RefundServiceImpl();
        ReflectionTestUtils.setField(
                service,
                "refundMapper",
                refundMapper);
        ReflectionTestUtils.setField(
                service,
                "statusLogMapper",
                statusLogMapper);

        RefundRecord updated = refund(
                EntitlementTradeConstants.REFUND_PROCESSING,
                2);
        RefundRecord current = refund(currentStatus, currentVersion);
        when(refundMapper.update(
                eq(updated),
                any(UpdateWrapper.class))).thenReturn(0);
        when(statusLogMapper.selectCount(
                any(QueryWrapper.class)))
                .thenReturn(duplicateEvent ? 1L : 0L);
        when(refundMapper.selectByTenantAndRefundNoForUpdate(
                3L,
                "REF-1")).thenReturn(current);

        return assertThrows(
                ApiStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "persistRefundTransition",
                        updated,
                        EntitlementTradeConstants.REFUND_REQUESTED,
                        1,
                        "REVIEW:APPROVE"));
    }

    private RefundRecord refund(String status, int version) {
        RefundRecord refund = new RefundRecord();
        refund.setRefundId(11L);
        refund.setTenantId(3L);
        refund.setRefundNo("REF-1");
        refund.setStatus(status);
        refund.setVersion(version);
        return refund;
    }

    private AuditAspect aspect(RecordingAuditWriter records) {
        AuditWriteFailureReporter reporter =
                new AuditWriteFailureReporter(null);
        TestTransactionManager transactionManager =
                new TestTransactionManager();
        return new AuditAspect(
                new AfterCommitAuditWriter(
                        records,
                        transactionManager,
                        reporter),
                new IndependentAuditWriter(
                        records,
                        transactionManager,
                        reporter),
                reporter,
                new TrustedRequestContextResolver(),
                new ObjectMapper(),
                "user-service");
    }

    private ProceedingJoinPoint joinPoint(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments,
            Object result,
            Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint =
                mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = target.getClass().getMethod(
                methodName,
                parameterTypes);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(signature.getMethod()).thenReturn(method);
        if (failure == null) {
            when(joinPoint.proceed()).thenReturn(result);
        } else {
            when(joinPoint.proceed()).thenThrow(failure);
        }
        return joinPoint;
    }

    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
    }

    private void assertNoCanary(String detail) {
        assertFalse(detail.contains(CANARY_PASSWORD));
        assertFalse(detail.contains(CANARY_TOKEN));
        assertFalse(detail.contains(CANARY_CONTENT));
        assertFalse(detail.contains("\"args\""));
        assertFalse(detail.contains("\"result\""));
    }

    private static final class RecordingAuditWriter
            implements AuditWriter {

        private final List<AuditWriteRecord> records =
                new ArrayList<>();

        @Override
        public void append(AuditWriteRecord record) {
            records.add(record);
        }
    }

    private static final class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(
                DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(
                DefaultTransactionStatus status) {
        }
    }
}
