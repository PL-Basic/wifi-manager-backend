package com.plagod.ai.task;

import com.plagod.mapper.AiPolicyVersionMapper;
import com.plagod.mapper.AiReviewTaskMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReviewTaskMapperSqlContractTest {

    @Test
    void activePolicyVersionIsSelectedByAiOwnedScene() throws Exception {
        Method method = AiPolicyVersionMapper.class.getMethod(
                "selectActiveByScene",
                String.class);
        String sql = String.join(
                " ",
                method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("policy.scene = #{scene}"));
        assertTrue(sql.contains("policy.status = 'ENABLED'"));
        assertTrue(sql.contains("version.status = 'ACTIVE'"));
        assertFalse(sql.contains("policy_version_id = #{"));
    }

    @Test
    void dispatchScanAndClaimRecoverMigratedQueuedAndExpiredTasks()
            throws Exception {
        String scan = selectSql(
                "selectDispatchableIds",
                LocalDateTime.class,
                Integer.class);
        String claim = updateSql(
                "claim",
                Long.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Integer.class);

        assertTrue(scan.contains("task_status = 'QUEUED'"));
        assertTrue(scan.contains("next_retry_time <= #{now}"));
        assertTrue(scan.contains(
                "task_status = 'RUNNING' and lease_until <= #{now}"));
        assertTrue(claim.contains("attempt_count = attempt_count + 1"));
        assertTrue(claim.contains("attempt_count < #{maxAttempts}"));
        assertTrue(claim.contains("task_status = 'QUEUED'"));
        assertTrue(claim.contains("next_retry_time <= #{now}"));
        assertTrue(claim.contains(
                "task_status = 'RUNNING' and lease_until <= #{now}"));
        assertFalse(claim.contains("and last_error_code"));
    }

    @Test
    void finalizeRequiresCurrentWorkerAndUnexpiredLease() throws Exception {
        String success = updateSql(
                "completeResult",
                Long.class,
                String.class,
                Integer.class,
                String.class,
                String.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Long.class,
                LocalDateTime.class);
        String failure = updateSql(
                "completeFailure",
                Long.class,
                String.class,
                Integer.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Integer.class);

        assertOwnedLease(success);
        assertOwnedLease(failure);
        assertTrue(failure.contains(
                "then 'MANUAL_REQUIRED' else 'QUEUED' end"));
        assertTrue(failure.contains("then 'MANUAL' else null end"));
        assertFalse(failure.contains("exception"));
        assertFalse(failure.contains("response"));
    }

    private void assertOwnedLease(String sql) {
        assertTrue(sql.contains("task_status = 'RUNNING'"));
        assertTrue(sql.contains("worker_id = #{workerId}"));
        assertTrue(sql.contains("lease_until > #{now}"));
        assertTrue(sql.contains("version = #{expectedVersion}"));
    }

    private String selectSql(
            String name,
            Class<?>... parameterTypes) throws Exception {
        Method method = AiReviewTaskMapper.class.getMethod(
                name,
                parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }

    private String updateSql(
            String name,
            Class<?>... parameterTypes) throws Exception {
        Method method = AiReviewTaskMapper.class.getMethod(
                name,
                parameterTypes);
        return String.join(" ", method.getAnnotation(Update.class).value());
    }
}
