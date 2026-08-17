package com.plagod.support;

import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportReviewMapperSqlContractTest {

    @Test
    void workerScanSelectsDueSupportAndExpiredClaimsOnly() throws Exception {
        Select select = SupportContentReviewOutboxMapper.class.getMethod(
                "selectDispatchableEventIds",
                LocalDateTime.class,
                Integer.class).getAnnotation(Select.class);
        String sql = normalize(select.value());

        assertTrue(sql.contains("scene = 'SUPPORT_SUBMISSION_REVIEW'"));
        assertTrue(sql.contains("business_type = 'SUPPORT_SUBMISSION'"));
        assertTrue(sql.contains("next_retry_time <= #{now}"));
        assertTrue(sql.contains("status = 'PENDING'"));
        assertTrue(sql.contains("status = 'PROCESSING'"));
        assertTrue(sql.contains("lease_until <= #{now}"));
        assertFalse(sql.contains("status = 'FAILED'"));
    }

    @Test
    void claimAllowsDuePendingAndExpiredProcessingOnly() throws Exception {
        String sql = updateSql(
                SupportContentReviewOutboxMapper.class.getMethod(
                        "claim",
                        Long.class,
                        String.class,
                        LocalDateTime.class,
                        LocalDateTime.class));

        assertTrue(sql.contains("next_retry_time <= #{now}"));
        assertTrue(sql.contains("status = 'PENDING'"));
        assertTrue(sql.contains("status = 'PROCESSING'"));
        assertTrue(sql.contains("lease_until <= #{now}"));
        assertTrue(sql.contains("worker_id = #{workerId}"));
        assertTrue(sql.contains("scene = 'SUPPORT_SUBMISSION_REVIEW'"));
        assertTrue(sql.contains("business_type = 'SUPPORT_SUBMISSION'"));
        assertFalse(sql.contains("status = 'FAILED'"));
        assertFalse(sql.contains("status = 'SENT'"));
    }

    @Test
    void finalizeRequiresCurrentWorkerAndUnexpiredLease() throws Exception {
        Method markSent = SupportContentReviewOutboxMapper.class.getMethod(
                "markSent",
                Long.class,
                String.class,
                LocalDateTime.class);
        Method release = SupportContentReviewOutboxMapper.class.getMethod(
                "releaseAfterFailure",
                Long.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                int.class,
                String.class,
                String.class);

        for (String sql : new String[]{
                updateSql(markSent),
                updateSql(release)}) {
            assertTrue(sql.contains("status = 'PROCESSING'"));
            assertTrue(sql.contains("worker_id = #{workerId}"));
            assertTrue(sql.contains("lease_until > #{now}"));
        }
        String releaseSql = updateSql(release);
        assertTrue(releaseSql.contains("retry_count + 1 >= #{maxAttempts}"));
        assertTrue(releaseSql.contains("then 'FAILED' else 'PENDING'"));
        assertTrue(releaseSql.contains("worker_id = null"));
        assertTrue(releaseSql.contains("lease_until = null"));
    }

    @Test
    void submissionUsesFrozenIdempotencyAndResultKeys() throws Exception {
        Select select = SupportSubmissionMapper.class.getMethod(
                "selectByRequestKey",
                Long.class,
                Long.class,
                String.class).getAnnotation(Select.class);
        String selectSql = normalize(select.value());
        assertTrue(selectSql.contains("tenant_id = #{tenantId}"));
        assertTrue(selectSql.contains("user_id = #{userId}"));
        assertTrue(selectSql.contains(
                "client_request_id = #{clientRequestId}"));

        Select lockingSelect = SupportSubmissionMapper.class.getMethod(
                "selectByRequestKeyForUpdate",
                Long.class,
                Long.class,
                String.class).getAnnotation(Select.class);
        assertTrue(normalize(lockingSelect.value()).endsWith("for update"));

        String attachSql = updateSql(
                SupportSubmissionMapper.class.getMethod(
                        "attachAiReviewTask",
                        Long.class,
                        String.class,
                        Long.class,
                        Integer.class));
        assertTrue(attachSql.contains("review_request_id = #{reviewRequestId}"));
        assertTrue(attachSql.contains("status = 'REVIEW_PENDING'"));
        assertTrue(attachSql.contains("ai_review_task_id is null"));
        assertTrue(attachSql.contains("version = #{expectedVersion}"));

        String manualSql = updateSql(
                SupportSubmissionMapper.class.getMethod(
                        "moveToManualReview",
                        Long.class,
                        String.class,
                        Integer.class,
                        String.class));
        assertTrue(manualSql.contains("status = 'MANUAL_REVIEW'"));
        assertTrue(manualSql.contains("review_decision = 'MANUAL'"));
        assertTrue(manualSql.contains(
                "outcome_reason_code = #{reasonCode}"));
        assertTrue(manualSql.contains("status = 'REVIEW_PENDING'"));
        assertTrue(manualSql.contains("ai_review_task_id is null"));
        assertTrue(manualSql.contains("version = #{expectedVersion}"));
    }

    private static String updateSql(Method method) {
        return normalize(method.getAnnotation(Update.class).value());
    }

    private static String normalize(String[] fragments) {
        return String.join(" ", fragments)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
