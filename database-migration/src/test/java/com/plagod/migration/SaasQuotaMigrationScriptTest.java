package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaasQuotaMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/V2_5__saas_quota_and_assignment_schema.sql";
    private static final String TENANT_FOUNDATION =
            "db/migration/V2_1__tenant_foundation.sql";

    @Test
    void migrationReusesV21AndCreatesOnlyAssignmentAndReservation()
            throws IOException {
        String sql = resource(MIGRATION);

        assertEquals(2, occurrences(sql, "create table t_tenant_"));
        assertFalse(sql.contains("create table t_saas_plan "));
        assertFalse(sql.contains("create table t_saas_plan_version "));
        assertFalse(sql.contains("create table t_tenant_subscription "));
        assertFalse(sql.contains("create table t_tenant_quota "));
        assertFalse(sql.contains("create table t_tenant_usage_daily "));
        assertTrue(sql.contains("create table t_tenant_plan_assignment"));
        assertTrue(sql.contains("create table t_tenant_quota_reservation"));
        assertFalse(sql.contains("foreign key"));
    }

    @Test
    void migrationCarriesDataAndPartialDdlGuards() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("V2_5_REQUIRED_V2_1_TABLES"));
        assertTrue(sql.contains("PLAN_VERSION_REFERENCE"));
        assertTrue(sql.contains("SUBSCRIPTION_DATA"));
        assertTrue(sql.contains("QUOTA_DATA"));
        assertTrue(sql.contains("USAGE_DATA"));
        assertTrue(sql.contains("V2_5_PARTIAL_DDL_STATE"));
        assertTrue(sql.contains("used_value > quota_record.limit_value"));
    }

    @Test
    void idempotencyAndReservationConstraintsAreExplicit() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("uk_plan_assignment_request"));
        assertTrue(sql.contains("tenant_id,\n            source_type,\n            client_request_id"));
        assertTrue(sql.contains("request_fingerprint char(64) not null"));
        assertTrue(sql.contains("plan_snapshot_hash char(64) not null"));
        assertTrue(sql.contains("uk_quota_reservation_business"));
        assertTrue(sql.contains("business_type,\n            business_key"));
        assertTrue(sql.contains("check (amount > 0)"));

        for (String status : new String[]{
                "RESERVED",
                "CONFIRMED",
                "RELEASED",
                "EXPIRED"}) {
            assertTrue(sql.contains("''" + status + "''"), status);
        }
    }

    @Test
    void v21StillEnforcesOneActiveSubscriptionPerTenant() throws IOException {
        String sql = resource(TENANT_FOUNDATION);

        assertTrue(sql.contains("active_subscription_tenant_guard bigint"));
        assertTrue(sql.contains(
                "case when status in ('TRIAL', 'ACTIVE') then tenant_id else null end"));
        assertTrue(sql.contains(
                "unique key uk_tenant_active_subscription " +
                        "(active_subscription_tenant_guard)"));
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
        try (InputStream input = SaasQuotaMigrationScriptTest.class
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
