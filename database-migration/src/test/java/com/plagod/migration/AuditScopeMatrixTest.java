package com.plagod.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditScopeMatrixTest {

    private static final Pattern AUDITED = Pattern.compile(
            "@Audited\\s*\\((.*?)\\)",
            Pattern.DOTALL);
    private static final Pattern ACTION = Pattern.compile(
            "action\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern SCOPE = Pattern.compile(
            "scope\\s*=\\s*Audited\\.Scope\\.([A-Z]+)");
    private static final Pattern TENANT_ID_SOURCE = Pattern.compile(
            "tenantIdSource\\s*=\\s*"
                    + "Audited\\.TenantIdSource\\.([A-Z]+)");

    private static final Set<String> PLATFORM_ACTIONS =
            setOf(
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
                    "user.update");

    private static final Set<String> TENANT_ACTIONS =
            setOf(
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
                    "session.portal-authorize");

    private static final Set<String> MODULES =
            setOf(
                    "auth-service",
                    "user-service",
                    "device-service",
                    "tenant-service",
                    "monitor-service");

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("scopeMatrix")
    void auditedActionMatchesFrozenScopeMatrix(
            String action,
            String expectedScopeAndSource) throws IOException {
        assertEquals(
                expectedScopeAndSource,
                auditedCallSites().get(action));
    }

    @Test
    void auditedActionSetHasNoMissingOrAdditionalEntry()
            throws IOException {
        assertEquals(expectedMatrix(), auditedCallSites());
    }

    @Test
    void onlyAsyncRuleActionsUseTenantArgumentMarker()
            throws IOException {
        Map<String, Integer> markerCounts = new LinkedHashMap<>();
        for (String module : MODULES) {
            Path sourceRoot = modulePath(module, "src/main/java");
            try (java.util.stream.Stream<Path> files =
                         Files.walk(sourceRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> countMarkers(path, markerCounts));
            }
        }

        assertEquals(1, markerCounts.size());
        String ruleActionSource = markerCounts.keySet().iterator().next();
        assertTrue(ruleActionSource.endsWith(
                "device-service/src/main/java/com/plagod/service/"
                        + "RuleActionExecutor.java")
                || ruleActionSource.endsWith(
                "device-service\\src\\main\\java\\com\\plagod\\service\\"
                        + "RuleActionExecutor.java"));
        assertEquals(2, markerCounts.get(ruleActionSource));
    }

    private void collect(Path path, Map<String, String> actual) {
        String source = read(path);
        Matcher audited = AUDITED.matcher(source);
        while (audited.find()) {
            String body = audited.group(1);
            Matcher action = ACTION.matcher(body);
            Matcher scope = SCOPE.matcher(body);
            Matcher tenantIdSource =
                    TENANT_ID_SOURCE.matcher(body);
            assertTrue(action.find(),
                    "missing audit action: " + path);
            assertTrue(scope.find(),
                    "missing explicit audit scope: " + path);
            assertTrue(tenantIdSource.find(),
                    "missing explicit tenant id source: " + path);
            String previous = actual.put(
                    action.group(1),
                    scope.group(1) + "/"
                            + tenantIdSource.group(1));
            assertFalse(previous != null,
                    "duplicate audit action "
                            + action.group(1) + ": " + path);
        }
    }

    private Map<String, String> auditedCallSites() throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        for (String module : MODULES) {
            Path sourceRoot = modulePath(module, "src/main/java");
            try (java.util.stream.Stream<Path> files =
                         Files.walk(sourceRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collect(path, actual));
            }
        }
        return actual;
    }

    private static Stream<Arguments> scopeMatrix() {
        return expectedMatrix().entrySet().stream()
                .map(entry -> Arguments.of(
                        entry.getKey(),
                        entry.getValue()));
    }

    private static Map<String, String> expectedMatrix() {
        Map<String, String> expected = new LinkedHashMap<>();
        PLATFORM_ACTIONS.forEach(action ->
                expected.put(action, "PLATFORM/REQUEST"));
        TENANT_ACTIONS.forEach(action ->
                expected.put(
                        action,
                        action.startsWith("monitor.auto.")
                                ? "TENANT/ARGUMENT"
                                : "TENANT/REQUEST"));
        return expected;
    }

    private void countMarkers(Path path,
                              Map<String, Integer> markerCounts) {
        String source = read(path);
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(
                "@AuditTenantId",
                cursor)) >= 0) {
            count++;
            cursor += "@AuditTenantId".length();
        }
        if (count > 0) {
            markerCounts.put(
                    path.toString().replace('\\', '/'),
                    count);
        }
    }

    private String read(Path path) {
        try {
            return new String(
                    Files.readAllBytes(path),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path modulePath(String module, String relative) {
        return Paths.get("..", module, relative).normalize();
    }

    private static Set<String> setOf(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
