package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportTicketMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/V2_8__support_ticket_schema.sql";

    @Test
    void migrationCreatesOnlySixBatchGTablesAndReusesReviewOutbox()
            throws IOException {
        String sql = resource();

        assertEquals(6, occurrences(sql, "create table if not exists t_support_"));
        assertTrue(sql.contains("V2_8_REQUIRED_SUPPORT_AI_TABLES"));
        assertTrue(sql.contains("V2_8_PARTIAL_DDL_STATE"));
        assertTrue(sql.contains("t_support_user_guard"));
        assertTrue(sql.contains("t_support_daily_guard"));
        assertTrue(sql.contains("t_support_submission"));
        assertTrue(sql.contains("t_support_ticket_message"));
        assertTrue(sql.contains("t_support_ticket_transition"));
        assertFalse(sql.contains("create table if not exists t_support_review_outbox"));
        assertFalse(sql.contains("create table if not exists t_developer"));
        assertFalse(sql.contains("attachment"));
        assertFalse(sql.contains("notification"));
    }

    @Test
    void submissionIdempotencyQuotaAndCompensationAreExplicit()
            throws IOException {
        String sql = resource();

        assertTrue(sql.contains("uk_support_submission_request"));
        assertTrue(sql.contains("request_fingerprint char(64) not null"));
        assertTrue(sql.contains("uk_support_daily_guard"));
        assertTrue(sql.contains("limit_used int not null default 0"));
        assertTrue(sql.contains("attempt_count int not null default 0"));
        assertTrue(sql.contains("SYSTEM_ABANDONED"));
        assertTrue(sql.contains("uk_support_submission_refund_event"));
        assertTrue(sql.contains("limit_refunded = 1"));
        assertTrue(sql.contains("ai_review_task_id is null"));
    }

    @Test
    void acceptedSubmissionCreatesAtMostOneTicketAndImmutableFirstMessage()
            throws IOException {
        String sql = resource();

        assertTrue(sql.contains("uk_support_ticket_submission"));
        assertTrue(sql.contains("uk_support_submission_ticket"));
        assertTrue(sql.contains("title_snapshot varchar(160) not null"));
        assertTrue(sql.contains("sender_key varchar(96)"));
        assertTrue(sql.contains("uk_support_ticket_message_request"));
        assertFalse(tableFragment(sql, "t_support_ticket_message")
                .contains("update_time"));
    }

    @Test
    void ticketTransitionsMatchFrozenLifecycle() throws IOException {
        String sql = resource();
        String transition = tableFragment(
                sql, "t_support_ticket_transition");

        assertTrue(transition.contains(
                "from_status = 'OPEN' and to_status = 'IN_PROGRESS'"));
        assertTrue(transition.contains(
                "to_status in ('WAITING_USER', 'RESOLVED')"));
        assertTrue(transition.contains(
                "from_status = 'RESOLVED' and to_status = 'CLOSED'"));
        assertTrue(transition.contains(
                "uk_support_ticket_transition_event"));
        assertFalse(transition.contains("REOPENED"));
    }

    @Test
    void schemaStoresNoProviderOrCredentialPayloads() throws IOException {
        String sql = resource().toLowerCase();

        for (String forbidden : new String[]{
                "api_key",
                "access_token",
                "secret_value",
                "provider_response",
                "response_body",
                "prompt_text",
                "endpoint_url"}) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }

    private static String tableFragment(String sql, String tableName) {
        int start = sql.indexOf("create table if not exists " + tableName);
        assertTrue(start >= 0, tableName);
        int end = sql.indexOf(
                "create table if not exists ", start + 1);
        return end < 0 ? sql.substring(start) : sql.substring(start, end);
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

    private static String resource() throws IOException {
        try (InputStream input = SupportTicketMigrationScriptTest.class
                .getClassLoader()
                .getResourceAsStream(MIGRATION)) {
            assertTrue(input != null, MIGRATION);
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
