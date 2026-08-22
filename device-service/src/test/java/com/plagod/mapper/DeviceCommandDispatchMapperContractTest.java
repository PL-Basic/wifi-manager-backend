package com.plagod.mapper;

import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceCommandDispatchMapperContractTest {

    @Test
    void claimIsConditionalOnPendingDueLeaseAndAttemptWindow()
            throws Exception {
        String sql = updateSql(
                "claimForDispatch",
                Long.class,
                String.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                Integer.class,
                Integer.class);

        assertTrue(sql.contains("status = #{pendingStatus}"));
        assertTrue(sql.contains("next_retry_time <= #{now}"));
        assertTrue(sql.contains("dispatch_lease_until <= #{now}"));
        assertTrue(sql.contains("retry_count, 0) < #{publishMaxAttempts}"));
        assertTrue(sql.contains("retry_count = coalesce(retry_count, 0) + 1"));
    }

    @Test
    void finalizeRequiresTheCurrentWorkerAndUnexpiredLease()
            throws Exception {
        String published = updateSql(
                "finalizePublished",
                Long.class,
                String.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                Integer.class,
                Integer.class);
        String failed = updateSql(
                "finalizePublishFailure",
                Long.class,
                String.class,
                java.time.LocalDateTime.class,
                Integer.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                String.class,
                Integer.class);

        assertOwnerGuard(published);
        assertOwnerGuard(failed);
        assertTrue(published.contains("dispatch_worker_id = null"));
        assertTrue(failed.contains("dispatch_lease_until = null"));
    }

    @Test
    void rapidResultOnlyAcceptsPublishedOrClaimedCommand()
            throws Exception {
        String sql = updateSql(
                "finalizeFromCommandResult",
                Long.class,
                Long.class,
                Integer.class,
                Integer.class,
                Integer.class,
                java.time.LocalDateTime.class,
                String.class);

        assertTrue(sql.contains("status = #{publishedStatus}"));
        assertTrue(sql.contains("status = #{pendingStatus}"));
        assertTrue(sql.contains("dispatch_worker_id is not null"));
        assertTrue(sql.contains("dispatch_lease_until is not null"));
        assertFalse(sql.contains("status not in"));
    }

    @Test
    void replacementRecoveryJoinsDurableSuccessWithWaitingSession()
            throws Exception {
        Method method = DeviceCommandRecordMapper.class.getMethod(
                "selectRecoverableForceReplacementCommands",
                Integer.class,
                Integer.class,
                Integer.class);
        Select select = method.getAnnotation(Select.class);
        if (select == null) {
            throw new AssertionError(
                    "selectRecoverableForceReplacementCommands 缺少 @Select");
        }
        String sql = String.join(" ", Arrays.asList(select.value()))
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("inner join t_session"));
        assertTrue(sql.contains("s.replaced_session_id = c.session_id"));
        assertTrue(sql.contains("s.status = #{waitingStatus}"));
        assertTrue(sql.contains("c.status = #{succeededStatus}"));
        assertTrue(sql.contains("c.purpose = 'FORCE_LOGIN_REPLACE'"));
    }

    private void assertOwnerGuard(String sql) {
        assertTrue(sql.contains("dispatch_worker_id = #{workerId}"));
        assertTrue(sql.contains("dispatch_lease_until > #{now}"));
    }

    private String updateSql(
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Method method = DeviceCommandRecordMapper.class.getMethod(
                methodName, parameterTypes);
        Update update = method.getAnnotation(Update.class);
        if (update == null) {
            throw new AssertionError(methodName + " 缺少 @Update");
        }
        return String.join(" ", Arrays.asList(update.value()))
                .replaceAll("\\s+", " ")
                .trim();
    }
}
