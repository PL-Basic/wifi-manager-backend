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

class CrossDomainPersistenceCarrierMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/V2_9__cross_domain_persistence_carriers.sql";

    @Test
    void migrationIsAppendOnlyAndCreatesExactlyThreeCarrierTables()
            throws IOException {
        String sql = resource();

        assertTrue(sql.contains("v2_9_required_tables"));
        assertTrue(sql.contains("v2_9_partial_ddl_state"));
        assertEquals(3, occurrences(sql, "create table t_"));
        assertTrue(sql.contains("create table t_tenant_creation_receipt"));
        assertTrue(sql.contains("create table t_tenant_domain_outbox"));
        assertTrue(sql.contains("create table t_announcement_action_request"));
        assertFalse(Pattern.compile("(?m)^\\s*update\\s+").matcher(sql).find());
        assertFalse(Pattern.compile("(?m)^\\s*delete\\s+").matcher(sql).find());
        assertFalse(sql.contains("foreign key"));
    }

    @Test
    void tenantUserAndQuotaCarriersAreExplicit() throws IOException {
        String sql = resource();

        assertTrue(sql.contains("uk_tenant_creation_request"));
        assertTrue(sql.contains("uk_tenant_domain_event"));
        assertTrue(sql.contains("idx_tenant_domain_due"));
        assertTrue(sql.contains("aggregate_type in ('tenant', 'plan_assignment')"));
        assertTrue(sql.contains("uk_user_operation_request"));
        assertTrue(sql.contains("request_fingerprint char(64) default null"));
        assertTrue(sql.contains("uk_quota_reservation_event"));
        assertTrue(sql.contains("add column event_id varchar(64) default null"));
    }

    @Test
    void announcementAuthAndAiCarriersAreExplicit() throws IOException {
        String sql = resource();

        assertTrue(sql.contains("uk_announcement_action_request"));
        assertTrue(sql.contains(
                "action_type in ('update', 'publish', 'withdraw')"));
        assertTrue(sql.contains("idx_default_membership_claim"));
        assertTrue(sql.contains("idx_ai_review_due"));
        assertTrue(sql.contains("add column worker_id varchar(64) default null"));
        assertTrue(sql.contains("add column lease_until datetime default null"));
        assertTrue(sql.contains("add column last_error_code varchar(64) default null"));
    }

    @Test
    void deviceSessionAndCommandCarriersAreExplicit() throws IOException {
        String sql = resource();

        assertTrue(sql.contains("uk_node_create_request"));
        assertTrue(sql.contains("creator_user_id bigint default null"));
        assertTrue(sql.contains(
                "quota_reservation_reference varchar(128) default null"));
        assertTrue(sql.contains("uk_session_authorize_request"));
        assertTrue(sql.contains("uk_device_command_client_request"));
        assertTrue(sql.contains("idx_command_dispatch_lease"));
        assertTrue(sql.contains("dispatch_worker_id varchar(64) default null"));
        assertTrue(sql.contains("dispatch_lease_until datetime default null"));
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
        try (InputStream input =
                     CrossDomainPersistenceCarrierMigrationScriptTest.class
                             .getClassLoader()
                             .getResourceAsStream(MIGRATION)) {
            assertTrue(input != null, MIGRATION);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }
    }
}
