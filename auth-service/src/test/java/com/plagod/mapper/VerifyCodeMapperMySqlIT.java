package com.plagod.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.plagod.testkit.TestEnvironmentGuard;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyCodeMapperMySqlIT {

    private static final String ENABLED =
            "WIFI_TEST_AUTH_MAPPER_ENABLED";
    private static final String ALLOW_SCHEMA_DDL =
            "WIFI_TEST_AUTH_MAPPER_ALLOW_SCHEMA_DDL";
    private static final String SERVER_URL =
            "WIFI_TEST_AUTH_MAPPER_SERVER_URL";
    private static final String USERNAME =
            "WIFI_TEST_AUTH_MAPPER_JDBC_USERNAME";
    private static final String PASSWORD =
            "WIFI_TEST_AUTH_MAPPER_JDBC_PASSWORD";
    private static final String OWNERSHIP_MODE =
            "WIFI_TEST_AUTH_MAPPER_OWNERSHIP_MODE";
    private static final Pattern SCHEMA_PATTERN =
            Pattern.compile("wifi_test_auth_claim_[a-z0-9]{12}");

    @Test
    void expiredClaimCanBeReclaimedAndStaleOwnerFinalizeReturnsZero()
            throws Exception {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(environment.get(ENABLED)),
                ENABLED + " is not true");
        Configuration configuration = Configuration.from(environment);
        String runId = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        String schema = "wifi_test_auth_claim_" + runId;
        String ownerToken = "auth-claim-" + UUID.randomUUID();
        assertTrue(SCHEMA_PATTERN.matcher(schema).matches());
        TestEnvironmentGuard.requireSafeMySqlSchema(
                configuration.urlForSchema(schema));

        boolean schemaCreated = false;
        boolean ownershipMarked = false;
        try (Connection server = configuration.openServer()) {
            execute(server, "create database " + quote(schema)
                    + " default charset utf8mb4 "
                    + "collate utf8mb4_unicode_ci");
            schemaCreated = true;
        }

        try {
            try (Connection connection = configuration.openSchema(schema)) {
                createOwnershipMarker(connection, ownerToken);
                ownershipMarked = true;
                createVerifyCodeTable(connection);
                seedExpiredClaim(connection);
                executeMapperAssertions(connection);
            }
        } finally {
            if (schemaCreated) {
                if (ownershipMarked) {
                    cleanupOwnedSchema(configuration, schema, ownerToken);
                } else {
                    cleanupEmptySchema(configuration, schema);
                }
            }
        }
        try (Connection server = configuration.openServer()) {
            assertEquals(0L, queryLong(
                    server,
                    "select count(*) from information_schema.schemata "
                            + "where schema_name = '" + schema + "'"));
        }
    }

    private void executeMapperAssertions(Connection connection)
            throws Exception {
        SingleConnectionDataSource dataSource =
                new SingleConnectionDataSource(connection, true);
        MybatisConfiguration mybatis = new MybatisConfiguration();
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.setEnvironment(new Environment(
                "auth-claim-mysql",
                new JdbcTransactionFactory(),
                dataSource));
        mybatis.addMapper(VerifyCodeMapper.class);
        SqlSessionFactory factory =
                new MybatisSqlSessionFactoryBuilder().build(mybatis);

        LocalDateTime claimedTime =
                LocalDateTime.of(2026, 8, 17, 18, 0);
        try (SqlSession session = factory.openSession(false)) {
            VerifyCodeMapper mapper =
                    session.getMapper(VerifyCodeMapper.class);
            assertEquals(1, mapper.tryClaimVerification(
                    1L,
                    "new-owner",
                    claimedTime,
                    claimedTime.plusSeconds(30)));
            session.commit();

            assertEquals(
                    0,
                    mapper.releaseVerificationClaim(1L, "old-owner"));
            assertEquals(
                    1,
                    mapper.releaseVerificationClaim(1L, "new-owner"));
            session.commit();
        }

        assertEquals(0L, queryLong(
                connection,
                "select count(*) from t_verify_code "
                        + "where id = 1 and verify_claim_owner is not null"));
    }

    private void createOwnershipMarker(
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

    private void createVerifyCodeTable(Connection connection)
            throws SQLException {
        execute(
                connection,
                "create table t_verify_code ("
                        + "id bigint not null auto_increment,"
                        + "target varchar(128) not null,"
                        + "target_type varchar(16) not null,"
                        + "scene varchar(32) not null,"
                        + "send_status tinyint not null default 0,"
                        + "verify_status tinyint not null default 0,"
                        + "status tinyint not null default 0,"
                        + "expire_time datetime not null,"
                        + "verify_claim_owner varchar(64) default null,"
                        + "verify_lease_until datetime default null,"
                        + "verify_claimed_time datetime default null,"
                        + "primary key (id),"
                        + "constraint ck_verify_code_claim_lease check ("
                        + "(verify_claim_owner is null "
                        + "and verify_lease_until is null "
                        + "and verify_claimed_time is null) or "
                        + "(verify_claim_owner is not null "
                        + "and char_length(trim(verify_claim_owner)) > 0 "
                        + "and verify_lease_until is not null "
                        + "and verify_claimed_time is not null "
                        + "and verify_lease_until > verify_claimed_time))"
                        + ") default charset=utf8mb4 "
                        + "collate=utf8mb4_unicode_ci");
    }

    private void seedExpiredClaim(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into t_verify_code("
                        + "id,target,target_type,scene,send_status,"
                        + "verify_status,status,expire_time,"
                        + "verify_claim_owner,verify_lease_until,"
                        + "verify_claimed_time) "
                        + "values (1,'13800138000','phone','step_up',"
                        + "1,0,0,?,?,?,?)")) {
            statement.setObject(
                    1,
                    LocalDateTime.of(2026, 8, 17, 18, 10));
            statement.setString(2, "old-owner");
            statement.setObject(
                    3,
                    LocalDateTime.of(2026, 8, 17, 17, 59));
            statement.setObject(
                    4,
                    LocalDateTime.of(2026, 8, 17, 17, 58));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void cleanupOwnedSchema(
            Configuration configuration,
            String schema,
            String ownerToken) throws SQLException {
        try (Connection connection = configuration.openSchema(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from __wifi_test_ownership "
                             + "where owner_token = ?")) {
            statement.setString(1, ownerToken);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1L, resultSet.getLong(1));
            }
        }
        try (Connection server = configuration.openServer()) {
            execute(server, "drop database " + quote(schema));
        }
    }

    private void cleanupEmptySchema(
            Configuration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openSchema(schema)) {
            assertEquals(0L, queryLong(
                    connection,
                    "select count(*) from information_schema.tables "
                            + "where table_schema = '" + schema + "'"));
        }
        try (Connection server = configuration.openServer()) {
            execute(server, "drop database " + quote(schema));
        }
    }

    private static void execute(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long queryLong(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }

    private static String quote(String identifier) {
        if (!SCHEMA_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("unsafe MySQL identifier");
        }
        return "`" + identifier + "`";
    }

    private static final class Configuration {

        private final String serverUrl;
        private final String username;
        private final String password;

        private Configuration(
                String serverUrl,
                String username,
                String password) {
            this.serverUrl = serverUrl;
            this.username = username;
            this.password = password;
        }

        private static Configuration from(
                Map<String, String> environment) {
            TestEnvironmentGuard.requireEnabled(
                    environment,
                    ALLOW_SCHEMA_DDL);
            assertEquals(
                    "ephemeral-owned-schema",
                    require(environment, OWNERSHIP_MODE));
            String serverUrl = require(environment, SERVER_URL);
            URI uri = URI.create(serverUrl.substring("jdbc:".length()));
            assertEquals("mysql", uri.getScheme());
            assertTrue("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()));
            assertEquals(3306, uri.getPort());
            assertTrue(uri.getPath() == null
                    || uri.getPath().isEmpty()
                    || "/".equals(uri.getPath()));
            assertTrue(uri.getUserInfo() == null);
            return new Configuration(
                    serverUrl,
                    require(environment, USERNAME),
                    environment.getOrDefault(PASSWORD, ""));
        }

        private Connection openServer() throws SQLException {
            return DriverManager.getConnection(
                    serverUrl,
                    username,
                    password);
        }

        private Connection openSchema(String schema) throws SQLException {
            return DriverManager.getConnection(
                    urlForSchema(schema),
                    username,
                    password);
        }

        private String urlForSchema(String schema) {
            int queryStart = serverUrl.indexOf('?');
            String prefix = queryStart < 0
                    ? serverUrl
                    : serverUrl.substring(0, queryStart);
            String query = queryStart < 0
                    ? ""
                    : serverUrl.substring(queryStart);
            return (prefix.endsWith("/") ? prefix : prefix + "/")
                    + schema
                    + query;
        }

        private static String require(
                Map<String, String> environment,
                String name) {
            TestEnvironmentGuard.requireVariables(environment, name);
            return environment.get(name).trim();
        }
    }
}
