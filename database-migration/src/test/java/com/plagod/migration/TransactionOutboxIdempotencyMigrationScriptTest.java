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

class TransactionOutboxIdempotencyMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/"
                    + "V2_9_2__transaction_outbox_idempotency_contract.sql";

    @Test
    void defaultMembershipOutboxReusesExistingCarriers() throws IOException {
        String sql = compact(resource());
        String alter = section(
                sql,
                "alter table t_default_tenant_membership_outbox",
                "create table t_user_auth_session_revoke_outbox");

        assertTrue(sql.contains(
                "@v2_9_2_required_default_outbox_column_count = 15"));
        assertTrue(sql.contains(
                "@v2_9_2_default_claim_index_column_count = 4"));
        assertTrue(sql.contains("idx_default_membership_claim"));
        assertTrue(sql.contains("idempotency_key"));
        assertTrue(sql.contains("request_fingerprint"));
        assertFalse(alter.contains("add column"));
        assertFalse(alter.contains("add key"));
        assertFalse(alter.contains("add index"));
        assertTrue(alter.contains(
                "comment='user-owned default tenant membership outbox'"));
    }

    @Test
    void defaultMembershipStatusAndClaimChecksAreNamedAndFrozen()
            throws IOException {
        String sql = compact(resource());

        assertTrue(sql.contains(
                "constraint chk_default_membership_outbox_status"));
        assertTrue(sql.contains(
                "status in ( 'pending', 'processing', 'retry', "
                        + "'succeeded', 'dead' )"));
        assertTrue(sql.contains(
                "constraint chk_default_membership_claim_owner"));
        assertTrue(sql.contains("lease_until > claimed_time"));
        assertTrue(sql.contains(
                "status <> 'processing' and worker_id is null "
                        + "and lease_until is null"));
        assertTrue(sql.contains(
                "@v2_9_2_invalid_existing_default_outbox_count ="));
        assertTrue(sql.contains(
                "status not in ('pending', 'retry', 'succeeded')"));
    }

    @Test
    void authRevokeOutboxFreezesEventClaimRetryAndTerminalContract()
            throws IOException {
        String sql = compact(resource());
        String table = section(
                sql,
                "create table t_user_auth_session_revoke_outbox",
                "create table t_entitlement_lease_receipt");

        assertTrue(table.contains(
                "unique key uk_user_auth_revoke_event (event_id)"));
        assertTrue(table.contains("idx_user_auth_revoke_claim"));
        assertTrue(table.contains("idx_user_auth_revoke_user"));
        assertTrue(table.contains("chk_user_auth_revoke_status"));
        assertTrue(table.contains("chk_user_auth_revoke_retry"));
        assertTrue(table.contains("chk_user_auth_revoke_claim_owner"));
        assertTrue(table.contains("chk_user_auth_revoke_terminal"));
        assertTrue(table.contains("lease_until > claimed_time"));
        assertTrue(table.contains(
                "status in ('succeeded', 'dead') "
                        + "and completed_time is not null "
                        + "and next_retry_time is null"));
        assertFalse(table.contains("event_id varchar(64) default null"));
        assertFalse(table.contains("foreign key"));
    }

    @Test
    void entitlementReceiptFreezesKeyFingerprintAndStoredResult()
            throws IOException {
        String sql = compact(resource());
        String table = section(
                sql,
                "create table t_entitlement_lease_receipt",
                "set @v2_9_2_post_object_count");

        assertTrue(table.contains(
                "unique key uk_entitlement_lease_request "
                        + "( tenant_id, request_id )"));
        assertTrue(table.contains("request_fingerprint char(64) not null"));
        assertTrue(table.contains("entitlement_id bigint default null"));
        assertTrue(table.contains("user_id bigint not null"));
        assertTrue(table.contains("session_id bigint not null"));
        assertTrue(table.contains("usage_seconds bigint not null"));
        assertTrue(table.contains("requested_ttl_seconds int not null"));
        assertTrue(table.contains("idx_entitlement_lease_subject"));
        assertTrue(table.contains("chk_entitlement_lease_completion"));
        assertTrue(table.contains("chk_entitlement_lease_allowed_result"));
        assertTrue(table.contains(
                "receipt_status = 'completed' "
                        + "and result_allowed is not null "
                        + "and result_charged_seconds is not null "
                        + "and result_reason is not null "
                        + "and completed_time is not null"));
        assertTrue(table.contains(
                "result_allowed = 0 "
                        + "and result_ttl_seconds is null"));
        assertTrue(table.contains(
                "result_ttl_seconds is not null "
                        + "and result_ttl_seconds > 0"));
        assertTrue(table.contains(
                "result_mode = 'duration' "
                        + "and result_remaining_seconds > 0"));
        assertTrue(table.contains(
                "result_mode = 'subscription' "
                        + "and result_subscription_end_time is not null"));
    }

    @Test
    void migrationDoesNotMutateBusinessRows() throws IOException {
        String sql = resource();

        assertEquals(2, occurrences(
                sql.toLowerCase(),
                "create table t_"));
        assertFalse(Pattern.compile("(?m)^\\s*update\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?m)^\\s*delete\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?m)^\\s*insert\\s+")
                .matcher(sql).find());
    }

    private static String section(
            String value,
            String start,
            String end) {
        int startIndex = value.indexOf(start);
        int endIndex = value.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, start);
        assertTrue(endIndex > startIndex, end);
        return value.substring(startIndex, endIndex);
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
                     TransactionOutboxIdempotencyMigrationScriptTest.class
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
