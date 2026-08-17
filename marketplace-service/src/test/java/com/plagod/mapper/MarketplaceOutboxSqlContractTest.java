package com.plagod.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceOutboxSqlContractTest {

    @Test
    void orderReplayUsesFrozenUniqueKeyColumns() throws Exception {
        Method method = MarketplaceOrderMapper.class.getDeclaredMethod(
                "selectByIdempotencyKey",
                Long.class,
                String.class,
                String.class,
                String.class);
        String sql = normalize(method.getAnnotation(Select.class).value());

        assertContains(sql, "tenant_id = #{tenantid}");
        assertContains(sql, "subject_type = #{subjecttype}");
        assertContains(sql, "subject_key = #{subjectkey}");
        assertContains(sql, "client_request_id = #{clientrequestid}");
    }

    @Test
    void claimIsConditionalAndCanReclaimOnlyExpiredProcessingLease()
            throws Exception {
        Method method = MarketplaceFulfillmentMapper.class.getDeclaredMethod(
                "claim",
                Long.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Integer.class);
        String sql = normalize(method.getAnnotation(Update.class).value());

        assertContains(sql, "set status = 'processing'");
        assertContains(sql, "worker_id = #{workerid}");
        assertContains(sql, "lease_until = #{leaseuntil}");
        assertContains(sql, "attempt_count = attempt_count + 1");
        assertContains(sql, "attempt_count < #{maxattempts}");
        assertContains(sql,
                "status = 'pending' and next_attempt_time <= #{now}");
        assertContains(sql,
                "status = 'processing' and lease_until <= #{now}");
    }

    @Test
    void workerScanIncludesDuePendingAndExpiredProcessingLease()
            throws Exception {
        Method method = MarketplaceFulfillmentMapper.class.getDeclaredMethod(
                "selectDispatchableIds",
                LocalDateTime.class,
                Integer.class);
        String sql = normalize(method.getAnnotation(Select.class).value());

        assertContains(sql,
                "status = 'pending' and next_attempt_time <= #{now}");
        assertContains(sql,
                "status = 'processing' and lease_until <= #{now}");
        assertContains(sql, "order by fulfillment_id asc");
        assertContains(sql, "limit #{limit}");
    }

    @Test
    void finalizeRequiresProcessingClaimOwnedByExactWorker()
            throws Exception {
        String success = updateSql(
                "completeSuccess",
                Long.class,
                String.class,
                String.class,
                LocalDateTime.class);
        String failure = updateSql(
                "completeFailure",
                Long.class,
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class,
                Integer.class);

        assertContains(success, "status = 'processing'");
        assertContains(success, "worker_id = #{workerid}");
        assertContains(success, "lease_until > #{now}");
        assertContains(failure, "status = 'processing'");
        assertContains(failure, "worker_id = #{workerid}");
        assertContains(failure, "lease_until > #{now}");
        assertContains(failure,
                "attempt_count >= #{maxattempts} then 'failed'");
        assertContains(failure,
                "attempt_count >= #{maxattempts} then #{now}");
    }

    @Test
    void exhaustedExpiredLeaseBecomesFailedWithoutAnotherNetworkAttempt()
            throws Exception {
        String sql = updateSql(
                "failExhausted",
                Long.class,
                LocalDateTime.class,
                Integer.class,
                String.class);

        assertContains(sql, "set status = 'failed'");
        assertContains(sql, "attempt_count >= #{maxattempts}");
        assertContains(sql,
                "status = 'processing' and lease_until <= #{now}");
        assertContains(sql, "worker_id = null");
        assertContains(sql, "lease_until = null");
    }

    private String updateSql(String methodName, Class<?>... parameterTypes)
            throws Exception {
        Method method = MarketplaceFulfillmentMapper.class.getDeclaredMethod(
                methodName,
                parameterTypes);
        return normalize(method.getAnnotation(Update.class).value());
    }

    private String normalize(String[] fragments) {
        return String.join(" ", fragments)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private void assertContains(String sql, String expected) {
        assertTrue(
                sql.contains(expected),
                () -> "SQL 缺少契约片段: " + expected + "\n" + sql);
    }
}
