package com.plagod.testkit;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class TestEnvironmentGuard {

    private static final Pattern DEFAULT_TEST_SCHEMA =
            Pattern.compile("wifi_test_[a-z0-9_-]+");
    private static final Set<String> FORBIDDEN_SCHEMAS =
            new java.util.HashSet<String>() {{
                add("wifi");
                add("mysql");
                add("information_schema");
                add("performance_schema");
                add("sys");
            }};

    private TestEnvironmentGuard() {
    }

    public static String requireSafeMySqlSchema(String jdbcUrl) {
        return requireSafeMySqlSchema(jdbcUrl, Collections.emptySet());
    }

    public static String requireSafeMySqlSchema(String jdbcUrl, Set<String> approvedSchemas) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL must be a MySQL JDBC URL");
        }

        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL cannot be parsed");
        }

        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL must name a host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL must not contain credentials");
        }

        String path = uri.getPath();
        String schema = path == null ? "" : path.replaceFirst("^/", "");
        if (schema.isEmpty() || schema.contains("/")) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL must name one test schema");
        }

        String normalized = schema.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_SCHEMAS.contains(normalized)) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL points to a forbidden schema");
        }

        Set<String> approved = approvedSchemas == null
                ? Collections.emptySet()
                : approvedSchemas;
        if (!DEFAULT_TEST_SCHEMA.matcher(normalized).matches()
                && !approved.contains(schema)) {
            throw new IllegalArgumentException("WIFI_TEST_JDBC_URL schema is not approved");
        }
        return schema;
    }

    public static void requireVariables(Map<String, String> environment, String... names) {
        if (environment == null) {
            throw new IllegalArgumentException("environment is required");
        }
        for (String name : names) {
            String value = environment.get(name);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalStateException("missing required test variable: " + name);
            }
        }
    }

    public static void requireEnabled(Map<String, String> environment, String name) {
        requireVariables(environment, name);
        if (!"true".equalsIgnoreCase(environment.get(name).trim())) {
            throw new IllegalStateException(name + " must be exactly true");
        }
    }

    public static URI requireHttpBaseUri(String value) {
        return requireNetworkUri(
                value,
                "WIFI_TEST_HTTP_BASE_URL",
                new HashSet<>(Arrays.asList("http", "https")));
    }

    public static URI requireMqttBrokerUri(String value) {
        return requireNetworkUri(
                value,
                "WIFI_TEST_MQTT_BROKER_URI",
                new HashSet<>(Arrays.asList("tcp", "ssl", "ws", "wss")));
    }

    public static void requireDedicatedWriteAccess(Map<String, String> environment) {
        requireVariables(
                environment,
                "WIFI_TEST_ALLOW_WRITES",
                "WIFI_TEST_ACCOUNT_MARKER");
        if (!"true".equalsIgnoreCase(environment.get("WIFI_TEST_ALLOW_WRITES"))
                || !"dedicated-test-account".equals(environment.get("WIFI_TEST_ACCOUNT_MARKER"))) {
            throw new IllegalStateException("external writes require a dedicated test account");
        }
    }

    private static URI requireNetworkUri(
            String value,
            String variableName,
            Set<String> allowedSchemes) {

        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(variableName + " cannot be parsed");
        }

        String scheme = uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!allowedSchemes.contains(scheme)
                || uri.getHost() == null
                || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException(variableName + " has an unsupported endpoint");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    variableName + " must not contain credentials or fragments");
        }
        return uri;
    }
}
