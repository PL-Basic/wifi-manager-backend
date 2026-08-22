package com.plagod.ai.task;

import com.plagod.mapper.AiReviewTaskInputMapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReviewTaskInputMapperSqlContractTest {

    @Test
    void sourceQueriesFreezeBusinessVersionOwnershipAndHash()
            throws Exception {
        String announcement = selectSql(
                "selectAnnouncementVersion",
                Long.class,
                Long.class,
                Integer.class,
                String.class,
                String.class);
        String support = selectSql(
                "selectSupportSubmission",
                Long.class,
                Long.class,
                Integer.class,
                String.class,
                String.class);

        assertTrue(announcement.contains(
                "content.announcement_id = #{businessId}"));
        assertTrue(announcement.contains(
                "content.content_version = #{contentVersion}"));
        assertTrue(announcement.contains(
                "content.review_request_id = #{reviewRequestId}"));
        assertTrue(announcement.contains(
                "announcement.tenant_id = #{tenantId}"));
        assertTrue(announcement.contains(
                "content.content_hash = #{contentHash}"));

        assertTrue(support.contains(
                "submission_id = #{businessId}"));
        assertTrue(support.contains("tenant_id = #{tenantId}"));
        assertTrue(support.contains(
                "content_version = #{contentVersion}"));
        assertTrue(support.contains(
                "review_request_id = #{reviewRequestId}"));
        assertTrue(support.contains("content_hash = #{contentHash}"));
        assertTrue(support.contains("content_text as body_text"));

        Method announcementMethod = mapperMethod(
                "selectAnnouncementVersion",
                Long.class,
                Long.class,
                Integer.class,
                String.class,
                String.class);
        assertTrue(Arrays.stream(
                        announcementMethod.getAnnotation(Results.class)
                                .value())
                .anyMatch(result -> "body_text".equals(result.column())
                        && "body".equals(result.property())));

        Method supportMethod = mapperMethod(
                "selectSupportSubmission",
                Long.class,
                Long.class,
                Integer.class,
                String.class,
                String.class);
        assertEquals(
                "aiReviewTaskInputSnapshot",
                supportMethod.getAnnotation(ResultMap.class).value()[0]);
    }

    @Test
    void productionWorkerHasSchedulingActivation() throws Exception {
        Method scheduledMethod = AiReviewTaskWorker.class
                .getMethod("dispatchDueTasks");
        assertNotNull(scheduledMethod.getAnnotation(Scheduled.class));
        assertNotNull(AiReviewTaskWorkerConfiguration.class
                .getAnnotation(EnableScheduling.class));
    }

    private String selectSql(
            String name,
            Class<?>... parameterTypes) throws Exception {
        Method method = mapperMethod(
                name,
                parameterTypes);
        return String.join(
                " ",
                method.getAnnotation(Select.class).value());
    }

    private Method mapperMethod(
            String name,
            Class<?>... parameterTypes) throws Exception {
        return AiReviewTaskInputMapper.class.getMethod(
                name,
                parameterTypes);
    }
}
