package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossCuttingRecoveryMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/V2_9_3__cross_cutting_recovery_contract.sql";

    @Test
    void verificationCodeHasDedicatedRecoverableClaimContract()
            throws IOException {
        String sql = compact(resource());

        assertTrue(sql.contains(
                "add column verify_claim_owner varchar(64) default null"));
        assertTrue(sql.contains(
                "add column verify_lease_until datetime default null"));
        assertTrue(sql.contains(
                "add column verify_claimed_time datetime default null"));
        assertTrue(sql.contains("add key idx_verify_code_claim"));
        assertTrue(sql.contains(
                "constraint chk_verify_code_claim_owner"));
        assertTrue(sql.contains(
                "verify_lease_until > verify_claimed_time"));
        assertTrue(sql.contains("send_status = 1"));
        assertTrue(sql.contains("verify_status = 0"));
        assertTrue(sql.contains("status = 0"));
    }

    @Test
    void supportManualReviewWithoutAiTaskRequiresFrozenReason()
            throws IOException {
        String sql = compact(resource());

        assertTrue(sql.contains(
                "drop check t_support_submission_chk_12"));
        assertTrue(sql.contains(
                "constraint chk_support_submission_review_task"));
        assertTrue(sql.contains(
                "constraint chk_support_manual_without_ai_task"));
        assertTrue(sql.contains(
                "status <> 'manual_review' "
                        + "or ai_review_task_id is not null "
                        + "or ( ai_review_task_id is null "
                        + "and outcome_reason_code is not null "
                        + "and outcome_reason_code = "
                        + "'ai_review_manual_required' )"));
        assertFalse(sql.contains(
                "'manual_review', 'accepted', 'spam_rejected', "
                        + "'converted_to_ticket' ) "
                        + "or ai_review_task_id is not null"));
    }

    @Test
    void legacyAiRunningClaimsAreRecoveredBeforeNamedCheck()
            throws IOException {
        String sql = compact(resource());
        int recovery = sql.indexOf("update t_ai_review_task");
        int constraint = sql.indexOf(
                "constraint chk_ai_review_task_claim_owner");

        assertTrue(recovery >= 0);
        assertTrue(constraint > recovery);
        assertTrue(sql.contains(
                "where task_status = 'running' and ( worker_id is null "
                        + "or lease_until is null or claimed_time is null "
                        + "or lease_until <= claimed_time )"));
        assertTrue(sql.contains(
                "last_error_code = 'legacy_running_claim_recovered'"));
        assertTrue(sql.contains("lease_until > claimed_time"));
        assertTrue(sql.contains(
                "task_status <> 'running' and worker_id is null "
                        + "and lease_until is null "
                        + "and claimed_time is null"));
    }

    @Test
    void migrationOnlyPerformsTheFrozenLegacyDataRepair()
            throws IOException {
        String sql = resource().toLowerCase();

        assertEquals(1, occurrences(sql, "update t_ai_review_task"));
        assertFalse(Pattern.compile("(?m)^\\s*delete\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?m)^\\s*insert\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?m)^\\s*create\\s+table")
                .matcher(sql).find());
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

    private static String compact(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String resource() throws IOException {
        try (InputStream input =
                     CrossCuttingRecoveryMigrationScriptTest.class
                             .getClassLoader()
                             .getResourceAsStream(MIGRATION)) {
            assertTrue(input != null, MIGRATION);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(
                    output.toByteArray(),
                    StandardCharsets.UTF_8);
        }
    }
}
