package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlTestDatabaseTest {

    @Test
    void missingEnvironmentFailsBeforeAnyDriverConnection() {
        assertThrows(
                IllegalStateException.class,
                () -> MySqlTestDatabase.open(
                        new HashMap<>(),
                        TestAccessMode.READ_ONLY));
    }

    @Test
    void dangerousSchemaFailsBeforeAnyDriverConnection() {
        Map<String, String> environment = mysqlEnvironment(
                "jdbc:mysql://127.0.0.1:3306/wifi_production_copy");

        assertThrows(
                IllegalArgumentException.class,
                () -> MySqlTestDatabase.open(
                        environment,
                        TestAccessMode.READ_ONLY));
    }

    @Test
    void writeModeRequiresDedicatedAccountBeforeAnyDriverConnection() {
        Map<String, String> environment = mysqlEnvironment(
                "jdbc:mysql://127.0.0.1:3306/wifi_test_stage_e");

        assertThrows(
                IllegalStateException.class,
                () -> MySqlTestDatabase.open(
                        environment,
                        TestAccessMode.WRITE));
    }

    private static Map<String, String> mysqlEnvironment(String jdbcUrl) {
        Map<String, String> environment = new HashMap<>();
        environment.put("WIFI_TEST_MYSQL_ENABLED", "true");
        environment.put("WIFI_TEST_JDBC_URL", jdbcUrl);
        environment.put("WIFI_TEST_JDBC_USERNAME", "test-user");
        environment.put("WIFI_TEST_JDBC_PASSWORD", "test-only-password");
        return environment;
    }
}
