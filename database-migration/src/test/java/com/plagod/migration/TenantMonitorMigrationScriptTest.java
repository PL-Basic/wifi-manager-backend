package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantMonitorMigrationScriptTest {

    private static final String EXPAND =
            "db/migration/V2_4_1__tenant_monitor_expand.sql";
    private static final String CONTRACT =
            "db/migration/V2_10__tenant_monitor_contract.sql";

    @Test
    void expandCarriesAllMonitorScopeAndVersionColumns() throws IOException {
        String sql = resource(EXPAND);

        assertEquals(9, occurrences(sql, "add column tenant_id bigint null"));
        assertEquals(4, occurrences(sql, "add column version int not null default 0"));
        assertTrue(sql.contains("scope_type varchar(16) not null default ''UNCLASSIFIED''"));
        assertTrue(sql.contains("idx_audit_scope_tenant_time"));
        assertTrue(sql.contains("unknown_audit_action_count"));
    }

    @Test
    void expandClassifiesEveryFrozenAuditActionExactlyOnce() throws IOException {
        String sql = resource(EXPAND);
        Set<String> platform = actionSet(sql, "set scope_type = 'PLATFORM'", "set scope_type = 'TENANT'");
        Set<String> tenant = actionSet(sql, "set scope_type = 'TENANT'", "create temporary table");

        assertEquals(setOf(
                "auth.platform_context",
                "auth.platform_tenant_context",
                "auth.register",
                "auth.reset_password",
                "tenant.create",
                "tenant.status",
                "tenant.update",
                "user.delete",
                "user.purge",
                "user.status",
                "user.update"), platform);

        assertEquals(setOf(
                "alert.handle",
                "blacklist.add",
                "blacklist.remove",
                "device.allow",
                "device.allow-client",
                "device.create",
                "device.delete",
                "device.kick",
                "device.manual-block-traffic",
                "device.manual-disconnect-mac",
                "device.restore",
                "device.update",
                "device.wifi.stage",
                "entitlement.adjust",
                "entitlement.reward-order.create",
                "entitlement.unlimited.adjust",
                "geofence.create",
                "geofence.delete",
                "geofence.toggle",
                "geofence.update",
                "location.consent.grant",
                "location.consent.revoke",
                "location.history.clear",
                "location.report",
                "monitor.auto.block-traffic",
                "monitor.auto.disconnect-mac",
                "refund.apply",
                "refund.channel.result",
                "refund.review",
                "rule.create",
                "rule.delete",
                "rule.toggle",
                "rule.update",
                "session.admin-revoke",
                "session.logout",
                "session.portal-authorize"), tenant);
    }

    @Test
    void contractIsCodeGatedAndSwitchesOnlyTargetKeys() throws IOException {
        String sql = resource(CONTRACT);

        assertTrue(sql.contains("lower('${p3cCodeReady}')"));
        assertTrue(sql.contains("V2_10_TENANT_CODE_NOT_READY"));
        assertTrue(sql.contains("UNKNOWN_AUDIT_ACTION"));
        assertTrue(sql.contains("chk_audit_scope_tenant"));
        assertEquals(8, occurrences(sql, "modify column tenant_id bigint not null"));

        for (String index : Arrays.asList(
                "uk_rule_tenant_code",
                "primary key (tenant_id, user_id)",
                "uk_rule_hit_tenant_event",
                "uk_geofence_state_tenant_session",
                "uk_geofence_event_tenant_location")) {
            assertTrue(sql.contains(index), index);
        }
    }

    @Test
    void defaultDeploymentStopsBeforeDeferredContract() throws IOException {
        String yaml = resource("application.yml");

        assertTrue(yaml.contains(
                "target: \"${DB_MIGRATION_TARGET:2.9.2}\""));
        assertTrue(yaml.contains(
                "p3cCodeReady: \"${DB_MIGRATION_P3C_CODE_READY:false}\""));
        assertTrue(CONTRACT.startsWith("db/migration/V2_10__"));
    }

    private static Set<String> actionSet(String sql, String startMarker, String endMarker) {
        int start = sql.indexOf(startMarker);
        int end = sql.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start);

        Matcher matcher = Pattern.compile("'([a-z][a-z0-9._-]+)'")
                .matcher(sql.substring(start, end));
        Set<String> actions = new HashSet<>();
        while (matcher.find()) {
            String value = matcher.group(1);
            if (!"PLATFORM".equals(value) && !"TENANT".equals(value)) {
                actions.add(value);
            }
        }
        return actions;
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

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = TenantMonitorMigrationScriptTest.class
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
