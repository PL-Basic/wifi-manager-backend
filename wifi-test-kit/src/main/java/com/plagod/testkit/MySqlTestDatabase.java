package com.plagod.testkit;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MySqlTestDatabase implements AutoCloseable {

    private final String schema;
    private final TestAccessMode accessMode;
    private final Connection connection;

    private MySqlTestDatabase(
            String schema,
            TestAccessMode accessMode,
            Connection connection) {
        this.schema = schema;
        this.accessMode = accessMode;
        this.connection = connection;
    }

    public static MySqlTestDatabase open(
            Map<String, String> environment,
            TestAccessMode accessMode) throws SQLException {

        Objects.requireNonNull(accessMode, "accessMode");
        TestEnvironmentGuard.requireEnabled(environment, "WIFI_TEST_MYSQL_ENABLED");
        TestEnvironmentGuard.requireVariables(
                environment,
                "WIFI_TEST_JDBC_URL",
                "WIFI_TEST_JDBC_USERNAME",
                "WIFI_TEST_JDBC_PASSWORD");

        String jdbcUrl = environment.get("WIFI_TEST_JDBC_URL").trim();
        String schema = TestEnvironmentGuard.requireSafeMySqlSchema(jdbcUrl);
        if (accessMode.allowsWrites()) {
            TestEnvironmentGuard.requireDedicatedWriteAccess(environment);
        }

        Connection connection = DriverManager.getConnection(
                jdbcUrl,
                environment.get("WIFI_TEST_JDBC_USERNAME"),
                environment.get("WIFI_TEST_JDBC_PASSWORD"));
        boolean accepted = false;
        try {
            verifyConnection(connection, schema);
            connection.setReadOnly(!accessMode.allowsWrites());
            accepted = true;
            return new MySqlTestDatabase(schema, accessMode, connection);
        } finally {
            if (!accepted) {
                connection.close();
            }
        }
    }

    public String getSchema() {
        return schema;
    }

    public TestAccessMode getAccessMode() {
        return accessMode;
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static void verifyConnection(Connection connection, String expectedSchema)
            throws SQLException {

        DatabaseMetaData metadata = connection.getMetaData();
        String productName = metadata.getDatabaseProductName();
        if (productName == null
                || !productName.toLowerCase(Locale.ROOT).contains("mysql")) {
            throw new SQLException("test JDBC endpoint is not MySQL");
        }

        String catalog = connection.getCatalog();
        if (catalog == null || !expectedSchema.equalsIgnoreCase(catalog)) {
            throw new SQLException("connected MySQL catalog does not match approved schema");
        }
    }
}
