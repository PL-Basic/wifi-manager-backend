package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementAiMigrationScriptTest {

    private static final String SUPPORT_MIGRATION =
            "db/migration/V2_6__announcement_schema.sql";
    private static final String AI_MIGRATION =
            "db/migration/V2_7__ai_review_schema.sql";

    @Test
    void supportMigrationCreatesOnlyTheFiveBatchFTables() throws IOException {
        String sql = resource(SUPPORT_MIGRATION);

        assertEquals(5, occurrences(sql, "create table t_"));
        assertTrue(sql.contains("create table t_announcement ("));
        assertTrue(sql.contains("create table t_announcement_content_version"));
        assertTrue(sql.contains("create table t_announcement_comment"));
        assertTrue(sql.contains("create table t_user_capability_restriction"));
        assertTrue(sql.contains("create table t_support_content_review_outbox"));
        assertTrue(sql.contains("V2_6_REQUIRED_MARKETPLACE_TABLES"));
        assertTrue(sql.contains("V2_6_PARTIAL_DDL_STATE"));

        assertFalse(sql.contains("create table t_support_submission"));
        assertFalse(sql.contains("create table t_support_ticket"));
        assertFalse(sql.contains("create table t_support_ticket_message"));
    }

    @Test
    void announcementScopeVersionAndCreationIdempotencyAreExplicit()
            throws IOException {
        String sql = resource(SUPPORT_MIGRATION);

        assertTrue(sql.contains("scope_key varchar(96)"));
        assertTrue(sql.contains("scope_type = ''PLATFORM'' and tenant_id is null"));
        assertTrue(sql.contains("scope_type = ''TENANT'' and tenant_id is not null"));
        assertTrue(sql.contains("uk_announcement_create_request"));
        assertTrue(sql.contains("request_fingerprint char(64) not null"));
        assertTrue(sql.contains("published_content_version int default null"));
        assertTrue(sql.contains("uk_announcement_content_version"));
        assertTrue(sql.contains("status <> ''PUBLISHED'' or published_time is not null"));
    }

    @Test
    void commentsRestrictionsAndReviewOutboxHaveRequiredBoundaries()
            throws IOException {
        String sql = resource(SUPPORT_MIGRATION);
        String outbox = tableFragment(
                sql, "create table t_support_content_review_outbox");

        assertTrue(sql.contains("uk_announcement_comment_request"));
        assertTrue(sql.contains("tenant_id bigint not null"));
        assertTrue(sql.contains("uk_capability_restriction_scope"));
        assertTrue(sql.contains("''ANNOUNCEMENT_PUBLISH''"));
        assertTrue(sql.contains("''ANNOUNCEMENT_COMMENT''"));
        assertTrue(sql.contains("''SUPPORT_SUBMISSION''"));
        assertTrue(outbox.contains("uk_support_review_business_version"));
        assertTrue(outbox.contains("lease_until datetime default null"));
        assertTrue(outbox.contains("worker_id varchar(64)"));
        assertFalse(outbox.contains("body_text"));
        assertFalse(outbox.contains("content_text"));
        assertFalse(outbox.contains("provider_response"));
    }

    @Test
    void aiMigrationHasTwoScenesOneActiveVersionAndTaskDeduplication()
            throws IOException {
        String sql = resource(AI_MIGRATION);

        assertEquals(5, occurrences(sql, "create table t_ai_"));
        assertTrue(sql.contains("V2_7_REQUIRED_SUPPORT_TABLES"));
        assertTrue(sql.contains("V2_7_PARTIAL_DDL_STATE"));
        assertTrue(sql.contains("''ANNOUNCEMENT_REVIEW''"));
        assertTrue(sql.contains("''SUPPORT_SUBMISSION_REVIEW''"));
        assertTrue(sql.contains("uk_ai_policy_scene"));
        assertTrue(sql.contains("active_policy_key bigint"));
        assertTrue(sql.contains("uk_ai_policy_one_active"));
        assertTrue(sql.contains("uk_ai_review_business_version"));
        assertTrue(sql.contains("uk_ai_review_provider_event"));
        assertTrue(sql.contains("task_status <> ''SUCCEEDED''"));
        assertTrue(sql.contains(
                "decision_code in (''APPROVE'', ''REJECT'')"));
        assertTrue(sql.contains("task_status <> ''MANUAL_REQUIRED''"));
        assertTrue(sql.contains("decision_code = ''MANUAL''"));
    }

    @Test
    void aiSchemaStoresReferencesHashesAndMinimalResultsOnly()
            throws IOException {
        String sql = resource(AI_MIGRATION).toLowerCase();

        assertTrue(sql.contains("config_reference varchar(96)"));
        assertTrue(sql.contains("instruction_reference varchar(128)"));
        assertTrue(sql.contains("instruction_hash char(64)"));
        assertTrue(sql.contains("response_schema_reference varchar(128)"));
        assertTrue(sql.contains("content_hash char(64)"));
        assertTrue(sql.contains("risk_labels_json json"));
        assertTrue(sql.contains("remark_summary varchar(255)"));

        for (String forbidden : new String[]{
                "api_key",
                "access_token",
                "secret_value",
                "endpoint_url",
                "prompt_text",
                "response_body",
                "body_text",
                "content_text"}) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }

    private static String tableFragment(String sql, String tableStart) {
        int start = sql.indexOf(tableStart);
        assertTrue(start >= 0, tableStart);
        int end = sql.indexOf("prepare v2_6_ddl_stmt", start);
        assertTrue(end > start, tableStart);
        return sql.substring(start, end);
    }

    private static int occurrences(String value, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = AnnouncementAiMigrationScriptTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertTrue(input != null, path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
