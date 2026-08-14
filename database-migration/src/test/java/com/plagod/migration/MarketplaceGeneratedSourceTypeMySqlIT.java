package com.plagod.migration;

import com.plagod.testkit.TestEnvironmentGuard;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceGeneratedSourceTypeMySqlIT {

    private static final String ENABLED = "WIFI_TEST_13R3_ENABLED";
    private static final String JDBC_URL = "WIFI_TEST_13R3_JDBC_URL";
    private static final String USERNAME = "WIFI_TEST_13R3_JDBC_USERNAME";
    private static final String PASSWORD = "WIFI_TEST_13R3_JDBC_PASSWORD";
    private static final String OWNER_TOKEN = "WIFI_TEST_13R3_OWNER_TOKEN";
    private static final String SERVER_UUID =
            "WIFI_TEST_13R3_EXPECTED_SERVER_UUID";
    private static final String SERVER_HOSTNAME =
            "WIFI_TEST_13R3_EXPECTED_SERVER_HOSTNAME";
    private static final String ACCOUNT_MARKER = "WIFI_TEST_ACCOUNT_MARKER";
    private static final String ALLOW_WRITES = "WIFI_TEST_ALLOW_WRITES";
    private static final String MIGRATION =
            "db/migration/V2_5_1__marketplace_demo_schema.sql";
    private static final Pattern SCHEMA_PATTERN =
            Pattern.compile("wifi_test_13r3_[a-z0-9]{8,24}");
    private static final Pattern OWNER_TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{16,80}");
    private static final List<String> TABLES = Arrays.asList(
            "t_market_fulfillment",
            "t_market_order_item",
            "t_market_order",
            "t_market_sku",
            "t_market_product",
            "t_tenant_quota_reservation",
            "t_tenant_plan_assignment",
            "t_saas_plan_version",
            "t_entitlement_order",
            "__wifi_test_ownership");

    @Test
    void generatedSourceTypeMapsKnownTypesAndRejectsUnknownType()
            throws Exception {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(environment.get(ENABLED)),
                ENABLED + " is not true");

        Configuration configuration = Configuration.from(environment);
        try (Connection connection = DriverManager.getConnection(
                configuration.jdbcUrl,
                configuration.username,
                configuration.password)) {
            verifyIdentity(connection, configuration);
            execute(
                    connection,
                    "set names utf8mb4 collate utf8mb4_unicode_ci");
            assertEquals(0L, tableCount(connection));
            createOwnershipMarker(connection, configuration.ownerToken);
            try {
                createPredecessorTables(connection);
                seedExistingOrder(
                        connection,
                        "R3-PURCHASE",
                        "PURCHASE");
                seedExistingOrder(
                        connection,
                        "R3-REWARD",
                        "REWARD");

                ScriptUtils.executeSqlScript(
                        connection,
                        new ClassPathResource(MIGRATION));

                insertOrder(
                        connection,
                        "R3-MARKETPLACE",
                        "MARKETPLACE",
                        "MARKET-ORDER-1");

                ScriptUtils.executeSqlScript(
                        connection,
                        new ClassPathResource(MIGRATION));

                assertSourceType(
                        connection,
                        "R3-PURCHASE",
                        "DIRECT_PURCHASE");
                assertSourceType(
                        connection,
                        "R3-REWARD",
                        "ADMIN_REWARD");
                assertSourceType(
                        connection,
                        "R3-MARKETPLACE",
                        "MARKETPLACE");

                assertThrows(
                        SQLException.class,
                        () -> insertOrder(
                                connection,
                                "R3-UNKNOWN",
                                "UNKNOWN",
                                "UNKNOWN-1"));
                assertEquals(0L, queryLong(
                        connection,
                        "select count(*) from t_entitlement_order "
                                + "where order_no = 'R3-UNKNOWN'"));
                assertThrows(
                        SQLException.class,
                        () -> insertOrder(
                                connection,
                                "R3-MARKETPLACE-NULL",
                                "MARKETPLACE",
                                null));
                assertThrows(
                        SQLException.class,
                        () -> updateOrderType(
                                connection,
                                "R3-PURCHASE",
                                "UNKNOWN"));
                assertSourceType(
                        connection,
                        "R3-PURCHASE",
                        "DIRECT_PURCHASE");
            } finally {
                cleanupOwnedTables(connection, configuration.ownerToken);
            }
            assertEquals(0L, tableCount(connection));
        }
    }

    private static void createOwnershipMarker(
            Connection connection,
            String ownerToken) throws SQLException {
        execute(
                connection,
                "create table __wifi_test_ownership ("
                        + "owner_token varchar(80) not null primary key)");
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into __wifi_test_ownership(owner_token) "
                            + "values (?)")) {
                statement.setString(1, ownerToken);
                assertEquals(1, statement.executeUpdate());
            }
        } catch (SQLException | RuntimeException exception) {
            execute(connection, "drop table __wifi_test_ownership");
            throw exception;
        }
    }

    private static void createPredecessorTables(Connection connection)
            throws SQLException {
        execute(
                connection,
                "create table t_entitlement_order ("
                        + "order_id bigint not null auto_increment,"
                        + "order_no varchar(64) not null,"
                        + "tenant_id bigint not null,"
                        + "user_id bigint not null,"
                        + "order_type varchar(24) not null default 'PURCHASE',"
                        + "primary key (order_id)) "
                        + "default charset=utf8mb4 "
                        + "collate=utf8mb4_unicode_ci");
        execute(
                connection,
                "create table t_saas_plan_version (id bigint not null)");
        execute(
                connection,
                "create table t_tenant_plan_assignment (id bigint not null)");
        execute(
                connection,
                "create table t_tenant_quota_reservation (id bigint not null)");
    }

    private static void seedExistingOrder(
            Connection connection,
            String orderNo,
            String orderType) throws SQLException {
        insertOrder(connection, orderNo, orderType, null);
    }

    private static void insertOrder(
            Connection connection,
            String orderNo,
            String orderType,
            String sourceReference) throws SQLException {
        String sql = sourceReference == null
                ? "insert into t_entitlement_order "
                        + "(order_no, tenant_id, user_id, order_type) "
                        + "values (?, 101, 201, ?)"
                : "insert into t_entitlement_order "
                        + "(order_no, tenant_id, user_id, order_type, "
                        + "source_reference) values (?, 101, 201, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderNo);
            statement.setString(2, orderType);
            if (sourceReference != null) {
                statement.setString(3, sourceReference);
            }
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertSourceType(
            Connection connection,
            String orderNo,
            String expected) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select source_type from t_entitlement_order "
                        + "where order_no = ?")) {
            statement.setString(1, orderNo);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(expected, rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    private static void updateOrderType(
            Connection connection,
            String orderNo,
            String orderType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update t_entitlement_order set order_type = ? "
                        + "where order_no = ?")) {
            statement.setString(1, orderType);
            statement.setString(2, orderNo);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void cleanupOwnedTables(
            Connection connection,
            String ownerToken) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from __wifi_test_ownership "
                        + "where owner_token = ?")) {
            statement.setString(1, ownerToken);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1L, rows.getLong(1));
            }
        }
        for (String table : TABLES) {
            execute(connection, "drop table if exists `" + table + "`");
        }
    }

    private static void verifyIdentity(
            Connection connection,
            Configuration configuration) throws SQLException {
        assertEquals(
                configuration.schema.toLowerCase(Locale.ROOT),
                connection.getCatalog().toLowerCase(Locale.ROOT));
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select @@server_uuid, @@hostname, @@port")) {
            assertTrue(rows.next());
            assertEquals(
                    configuration.expectedServerUuid.toLowerCase(Locale.ROOT),
                    rows.getString(1).toLowerCase(Locale.ROOT));
            assertEquals(
                    configuration.expectedServerHostname.toLowerCase(
                            Locale.ROOT),
                    rows.getString(2).toLowerCase(Locale.ROOT));
            assertEquals(3306, rows.getInt(3));
            assertFalse(rows.next());
        }
    }

    private static long tableCount(Connection connection)
            throws SQLException {
        return queryLong(
                connection,
                "select count(*) from information_schema.tables "
                        + "where table_schema = database() "
                        + "and table_type = 'BASE TABLE'");
    }

    private static long queryLong(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            long value = rows.getLong(1);
            assertFalse(rows.next());
            return value;
        }
    }

    private static void execute(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String require(
            Map<String, String> environment,
            String name) {
        TestEnvironmentGuard.requireVariables(environment, name);
        return environment.get(name).trim();
    }

    private static final class Configuration {

        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String ownerToken;
        private final String expectedServerUuid;
        private final String expectedServerHostname;
        private final String schema;

        private Configuration(
                String jdbcUrl,
                String username,
                String password,
                String ownerToken,
                String expectedServerUuid,
                String expectedServerHostname,
                String schema) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.ownerToken = ownerToken;
            this.expectedServerUuid = expectedServerUuid;
            this.expectedServerHostname = expectedServerHostname;
            this.schema = schema;
        }

        private static Configuration from(
                Map<String, String> environment) {
            TestEnvironmentGuard.requireEnabled(environment, ENABLED);
            TestEnvironmentGuard.requireDedicatedWriteAccess(environment);
            assertEquals(
                    "true",
                    require(environment, ALLOW_WRITES).toLowerCase(
                            Locale.ROOT));
            assertEquals(
                    "dedicated-test-account",
                    require(environment, ACCOUNT_MARKER));

            String jdbcUrl = require(environment, JDBC_URL);
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            assertEquals("mysql", uri.getScheme());
            assertTrue("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()));
            assertEquals(3306, uri.getPort());
            assertTrue(uri.getUserInfo() == null);

            String schema =
                    TestEnvironmentGuard.requireSafeMySqlSchema(jdbcUrl);
            assertTrue(SCHEMA_PATTERN.matcher(schema).matches());
            String ownerToken = require(environment, OWNER_TOKEN);
            assertTrue(OWNER_TOKEN_PATTERN.matcher(ownerToken).matches());
            String expectedUuid = require(environment, SERVER_UUID);
            assertTrue(expectedUuid.matches("[0-9a-fA-F-]{36}"));
            String expectedHostname = require(environment, SERVER_HOSTNAME);
            assertTrue(expectedHostname.matches("[A-Za-z0-9._-]{1,128}"));

            return new Configuration(
                    jdbcUrl,
                    require(environment, USERNAME),
                    require(environment, PASSWORD),
                    ownerToken,
                    expectedUuid,
                    expectedHostname,
                    schema);
        }
    }
}
