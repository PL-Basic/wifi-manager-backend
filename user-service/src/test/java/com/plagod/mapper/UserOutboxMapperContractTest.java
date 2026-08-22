package com.plagod.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserOutboxMapperContractTest {

    @Test
    void defaultMembershipClaimSupportsExpiredLeaseAndOwnedFinalize()
            throws Exception {
        String exhausted = updateSql(
                DefaultTenantMembershipOutboxMapper.class,
                "finalizeExhausted");
        String select = selectSql(
                DefaultTenantMembershipOutboxMapper.class,
                "selectDispatchableIds");
        String claim = updateSql(
                DefaultTenantMembershipOutboxMapper.class,
                "claim");
        String success = updateSql(
                DefaultTenantMembershipOutboxMapper.class,
                "finalizeSucceeded");
        String failure = updateSql(
                DefaultTenantMembershipOutboxMapper.class,
                "finalizeFailed");

        assertExhaustedRecoveryContract(exhausted);
        assertClaimContract(select, claim);
        assertOwnedFinalize(success, failure);
        assertDeadContract(failure);
    }

    @Test
    void authRevokeClaimSupportsExpiredLeaseOwnedFinalizeAndTerminalTime()
            throws Exception {
        String select = selectSql(
                UserAuthSessionRevokeOutboxMapper.class,
                "selectDispatchableIds");
        String claim = updateSql(
                UserAuthSessionRevokeOutboxMapper.class,
                "claim");
        String success = updateSql(
                UserAuthSessionRevokeOutboxMapper.class,
                "finalizeSucceeded");
        String failure = updateSql(
                UserAuthSessionRevokeOutboxMapper.class,
                "finalizeFailed");

        assertClaimContract(select, claim);
        assertOwnedFinalize(success, failure);
        assertDeadContract(failure);
        assertTrue(success.contains("completed_time = #{completedTime}"));
        assertTrue(failure.contains("then #{completedTime} else null"));
    }

    private void assertClaimContract(String select, String claim) {
        assertTrue(select.contains("status in ('PENDING', 'RETRY')"));
        assertTrue(select.contains("status = 'PROCESSING'"));
        assertTrue(select.contains("lease_until < #{now}"));
        assertTrue(select.contains("retry_count < #{maxRetryCount}"));
        assertTrue(claim.contains("lease_until < #{claimedTime}"));
        assertTrue(claim.contains("status = 'PROCESSING'"));
        assertTrue(claim.contains("worker_id = #{workerId}"));
    }

    private void assertOwnedFinalize(String success, String failure) {
        assertTrue(success.contains("status = 'PROCESSING'"));
        assertTrue(success.contains("worker_id = #{workerId}"));
        assertTrue(failure.contains("status = 'PROCESSING'"));
        assertTrue(failure.contains("worker_id = #{workerId}"));
        assertTrue(success.contains("worker_id = null"));
        assertTrue(failure.contains("worker_id = null"));
    }

    private void assertDeadContract(String failure) {
        assertTrue(failure.contains("then 'DEAD' else 'RETRY'"));
        assertTrue(failure.contains(
                "retry_count + 1 >= #{maxRetryCount}"));
        assertTrue(failure.contains(
                "retry_count = retry_count + 1"));
        assertTrue(failure.indexOf("status = case")
                < failure.indexOf("retry_count = retry_count + 1"));
    }

    private void assertExhaustedRecoveryContract(String exhausted) {
        assertTrue(exhausted.contains("set status = 'DEAD'"));
        assertTrue(exhausted.contains(
                "retry_count >= #{maxRetryCount}"));
        assertTrue(exhausted.contains(
                "status in ('PENDING', 'RETRY')"));
        assertTrue(exhausted.contains(
                "status = 'PROCESSING' and lease_until < #{now}"));
        assertTrue(exhausted.contains("worker_id = null"));
        assertTrue(exhausted.contains("lease_until = null"));
        assertTrue(exhausted.contains("next_retry_time = null"));
    }

    private String selectSql(
            Class<?> mapper,
            String methodName) throws Exception {
        Method method = findMethod(mapper, methodName);
        return String.join("", method.getAnnotation(Select.class).value());
    }

    private String updateSql(
            Class<?> mapper,
            String methodName) throws Exception {
        Method method = findMethod(mapper, methodName);
        return String.join("", method.getAnnotation(Update.class).value());
    }

    private Method findMethod(
            Class<?> mapper,
            String methodName) {
        for (Method method : mapper.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                return method;
            }
        }
        throw new IllegalArgumentException(
                "Mapper method not found: " + methodName);
    }
}
