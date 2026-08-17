package com.plagod.migration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.AiPolicyVersion;
import com.plagod.entity.Announcement;
import com.plagod.entity.AnnouncementActionRequest;
import com.plagod.entity.MarketplaceOrder;
import com.plagod.entity.SupportTicketMessage;
import com.plagod.entity.TenantMember;
import com.plagod.entity.TenantSubscription;
import com.plagod.entity.UserCapabilityRestriction;
import com.plagod.entity.entitlement.EntitlementOrder;
import com.plagod.entity.user.User;
import com.plagod.mapper.AiPolicyVersionMapper;
import com.plagod.mapper.AnnouncementActionRequestMapper;
import com.plagod.mapper.AnnouncementMapper;
import com.plagod.mapper.EntitlementOrderMapper;
import com.plagod.mapper.MarketplaceOrderMapper;
import com.plagod.mapper.SupportTicketMessageMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.mapper.TenantSubscriptionMapper;
import com.plagod.mapper.UserCapabilityRestrictionMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.testkit.TestEnvironmentGuard;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationReplayMySqlIT {

    private static final String ENABLED = "WIFI_TEST_13H_ENABLED";
    private static final String ACTION = "WIFI_TEST_13H_ACTION";
    private static final String SERVER_URL = "WIFI_TEST_13H_SERVER_JDBC_URL";
    private static final String USERNAME = "WIFI_TEST_13H_JDBC_USERNAME";
    private static final String PASSWORD = "WIFI_TEST_13H_JDBC_PASSWORD";
    private static final String RUN_ID = "WIFI_TEST_13H_RUN_ID";
    private static final String OWNER_TOKEN = "WIFI_TEST_13H_OWNER_TOKEN";
    private static final String SERVER_UUID = "WIFI_TEST_13H_EXPECTED_SERVER_UUID";
    private static final String SERVER_HOSTNAME =
            "WIFI_TEST_13H_EXPECTED_SERVER_HOSTNAME";
    private static final String V231_DUMP = "WIFI_TEST_13H_V231_DUMP";
    private static final String V232_DUMP = "WIFI_TEST_13H_V232_DUMP";
    private static final String ACCOUNT_MARKER = "WIFI_TEST_ACCOUNT_MARKER";
    private static final String SCHEMA_DDL = "WIFI_TEST_13H_ALLOW_SCHEMA_DDL";

    private static final String EXPECTED_V231_DUMP_HASH =
            "0fb32b70b385d10ec34e3d87f7f16a8e02a31b926c097384902209328b6023fe";
    private static final String EXPECTED_V232_DUMP_HASH =
            "288cedf9a455cf0c1d6b570e03ab674b0d07409b7a812d2fb1279123536337d1";
    private static final Pattern RUN_ID_PATTERN =
            Pattern.compile("[a-z0-9]{8,24}");
    private static final Pattern OWNER_TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{16,80}");
    private static final Pattern CREATE_DATABASE_PATTERN = Pattern.compile(
            "(?m)^CREATE DATABASE .*`wifi`.*;$");
    private static final Pattern USE_DATABASE_PATTERN = Pattern.compile(
            "(?m)^USE `wifi`;$");

    @Test
    void executeExplicitReplayAction() throws Exception {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(environment.get(ENABLED)),
                ENABLED + " is not true");

        ReplayConfiguration configuration = ReplayConfiguration.from(environment);
        String action = requireValue(environment, ACTION).toLowerCase(Locale.ROOT);
        if ("identity".equals(action)) {
            verifyRealDatabaseIdentity(configuration);
            return;
        }
        if ("create".equals(action)) {
            createReplaySchemas(configuration);
            return;
        }
        if ("empty".equals(action)) {
            prepareEmptyChain(configuration);
            return;
        }
        if ("restore-v231".equals(action)) {
            restoreSnapshot(
                    configuration,
                    configuration.v231Schema,
                    configuration.v231Dump,
                    "2.3.1");
            return;
        }
        if ("upgrade-v231".equals(action)) {
            upgradeSnapshot(
                    configuration,
                    configuration.v231Schema,
                    "2.3.1",
                    9);
            return;
        }
        if ("restore-v232".equals(action)) {
            restoreSnapshot(
                    configuration,
                    configuration.v232Schema,
                    configuration.v232Dump,
                    "2.3.2");
            return;
        }
        if ("upgrade-v232".equals(action)) {
            upgradeSnapshot(
                    configuration,
                    configuration.v232Schema,
                    "2.3.2",
                    8);
            return;
        }
        if ("verify-expand".equals(action)) {
            verifyReplaySchemas(configuration, "2.9.3");
            return;
        }
        if ("generated-columns".equals(action)) {
            verifyGeneratedColumnMappings(configuration);
            return;
        }
        if ("contract-empty".equals(action)) {
            applyContract(configuration, configuration.emptySchema);
            return;
        }
        if ("contract-v231".equals(action)) {
            applyContract(configuration, configuration.v231Schema);
            return;
        }
        if ("contract-v232".equals(action)) {
            applyContract(configuration, configuration.v232Schema);
            return;
        }
        if ("verify".equals(action)) {
            verifyReplaySchemas(configuration, "2.10");
            return;
        }
        if ("diff".equals(action)) {
            diffReplaySchemas(configuration);
            return;
        }
        if ("cleanup".equals(action)) {
            cleanupReplaySchemas(configuration);
            return;
        }
        throw new IllegalArgumentException(
                ACTION
                        + " must be identity, create, empty, restore-v231, "
                        + "upgrade-v231, restore-v232, upgrade-v232, "
                        + "verify-expand, generated-columns, contract-empty, "
                        + "contract-v231, contract-v232, verify, diff, "
                        + "or cleanup");
    }

    private static void verifyRealDatabaseIdentity(
            ReplayConfiguration configuration) throws Exception {
        try (Connection connection = configuration.openSchema("wifi")) {
            connection.setReadOnly(true);
            DatabaseIdentity identity = readIdentity(connection);
            assertEquals("wifi", identity.catalog);
            assertEquals(configuration.expectedServerUuid, identity.serverUuid);
            assertEquals(
                    configuration.expectedServerHostname,
                    identity.serverHostname);
            assertEquals(3306, identity.serverPort);
            assertTrue(identity.version.startsWith("8.4."));

            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "select version, checksum, success "
                                 + "from flyway_schema_history "
                                 + "order by installed_rank desc limit 1")) {
                assertTrue(rows.next());
                assertEquals("2.4.1", rows.getString("version"));
                assertEquals(848930306, rows.getInt("checksum"));
                assertTrue(rows.getBoolean("success"));
                assertFalse(rows.next());
            }

            assertEquals(0L, queryLong(
                    connection,
                    "select count(*) from flyway_schema_history "
                            + "where version in "
                            + "('2.4.2','2.5','2.5.1','2.6','2.7','2.8',"
                            + "'2.9','2.9.1','2.9.2','2.9.3','2.10')"));
            assertEquals(0L, queryLong(
                    connection,
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() "
                            + "and table_name in ("
                            + "'t_support_user_guard',"
                            + "'t_support_daily_guard',"
                            + "'t_support_submission',"
                            + "'t_support_ticket',"
                            + "'t_support_ticket_message',"
                            + "'t_support_ticket_transition',"
                            + "'t_tenant_creation_receipt',"
                            + "'t_tenant_domain_outbox',"
                            + "'t_announcement_action_request',"
                            + "'t_user_auth_session_revoke_outbox',"
                            + "'t_entitlement_lease_receipt')"));
        }
    }

    private static void createReplaySchemas(
            ReplayConfiguration configuration) throws Exception {
        verifyDump(
                configuration.v231Dump,
                EXPECTED_V231_DUMP_HASH);
        verifyDump(
                configuration.v232Dump,
                EXPECTED_V232_DUMP_HASH);

        try (Connection server = configuration.openServer()) {
            assertServerIdentity(server, configuration.expectedServerUuid);
            for (String schema : configuration.schemas()) {
                assertEquals(0L, schemaCount(server, schema));
            }
            createOwnedSchemas(server, configuration);
            for (String schema : configuration.schemas()) {
                assertEquals(1L, schemaCount(server, schema));
                assertOwnership(server, schema, configuration);
            }
        }
        for (String schema : configuration.replaySchemas()) {
            assertEquals(0L, businessTableCount(configuration, schema));
        }
    }

    private static void prepareEmptyChain(
            ReplayConfiguration configuration) throws Exception {
        assertOwnership(configuration, configuration.emptySchema);
        assertEquals(
                0L,
                businessTableCount(
                        configuration,
                        configuration.emptySchema));
        Flyway firstStage = flyway(
                configuration,
                configuration.emptySchema,
                "1.7",
                false);
        assertEquals(7, firstStage.migrate());

        try (Connection connection = configuration.openSchema(
                configuration.emptySchema);
             PreparedStatement statement = connection.prepareStatement(
                     "insert into sys_user "
                             + "(username, password, nickname, role, status, del_flag) "
                             + "values (?, ?, ?, 0, 1, 0)")) {
            statement.setString(1, "demo13h-owner-" + configuration.runId);
            statement.setString(2, "unusable-test-hash");
            statement.setString(3, "Demo 1.3-H Owner");
            assertEquals(1, statement.executeUpdate());
        }

        Flyway latest = flyway(
                configuration,
                configuration.emptySchema,
                "2.9.1",
                false);
        assertEquals(14, latest.migrate());
        latest.validate();
        assertFlywayLatest(
                configuration,
                configuration.emptySchema,
                "2.9.1");
        applyC0Candidate(
                configuration,
                configuration.emptySchema);
    }

    private static void restoreSnapshot(
            ReplayConfiguration configuration,
            String schema,
            Path dump,
            String expectedBaseline) throws Exception {
        assertOwnership(configuration, schema);
        assertEquals(0L, businessTableCount(configuration, schema));
        restoreDump(configuration, schema, dump);
        assertFlywayLatest(configuration, schema, expectedBaseline);
        assertEquals(43L, businessTableCount(configuration, schema));
    }

    private static void upgradeSnapshot(
            ReplayConfiguration configuration,
            String schema,
            String expectedBaseline,
            int expectedMigrations) throws Exception {
        assertOwnership(configuration, schema);
        assertFlywayLatest(configuration, schema, expectedBaseline);
        assertEquals(43L, businessTableCount(configuration, schema));
        List<TableCount> before = readTableCounts(configuration, schema);
        Flyway latest = flyway(configuration, schema, "2.9.1", false);
        int migrated = latest.migrate();
        assertEquals(expectedMigrations, migrated);
        latest.validate();
        assertFlywayLatest(configuration, schema, "2.9.1");
        assertPreservedCounts(configuration, schema, before);
        applyC0Candidate(configuration, schema);
    }

    private static void applyC0Candidate(
            ReplayConfiguration configuration,
            String schema) throws Exception {
        assertOwnership(configuration, schema);
        assertFlywayLatest(configuration, schema, "2.9.1");
        insertLegalDefaultMembershipOutboxRow(
                configuration,
                schema);
        List<TableCount> before = readTableCounts(configuration, schema);
        Flyway candidate = flyway(
                configuration,
                schema,
                "2.9.2",
                false);
        assertEquals(1, candidate.migrate());
        candidate.validate();
        assertC0Latest(configuration, schema);
        assertPreservedCounts(configuration, schema, before);
        assertTransactionOutboxIdempotencyContract(
                configuration,
                schema);
        applyC01Candidate(configuration, schema);
    }

    private static void applyC01Candidate(
            ReplayConfiguration configuration,
            String schema) throws Exception {
        assertOwnership(configuration, schema);
        assertC0Latest(configuration, schema);
        insertC01UpgradeFixtures(configuration, schema);
        List<TableCount> before = readTableCounts(configuration, schema);
        Flyway candidate = flyway(
                configuration,
                schema,
                "2.9.3",
                false);
        assertEquals(1, candidate.migrate());
        candidate.validate();
        assertCandidateLatest(configuration, schema);
        assertPreservedCounts(configuration, schema, before);
        assertCrossCuttingRecoveryContract(
                configuration,
                schema);
    }

    private static void applyContract(
            ReplayConfiguration configuration,
            String schema) throws Exception {
        assertOwnership(configuration, schema);
        assertCandidateLatest(configuration, schema);
        List<TableCount> before = readTableCounts(configuration, schema);
        Flyway contract = flyway(configuration, schema, "2.10", true);
        assertEquals(1, contract.migrate());
        contract.validate();
        assertContractLatest(configuration, schema);
        assertPreservedCounts(configuration, schema, before);
    }

    private static void verifyReplaySchemas(
            ReplayConfiguration configuration,
            String expectedVersion) throws Exception {
        for (String schema : Arrays.asList(
                configuration.emptySchema,
                configuration.v231Schema,
                configuration.v232Schema)) {
            assertOwnership(configuration, schema);
            assertSchemaState(configuration, schema, expectedVersion);
            assertNoSensitiveAiColumns(configuration, schema);
            assertGeneratedColumnInventory(configuration, schema);
        }

        String emptyFingerprint =
                schemaFingerprint(configuration, configuration.emptySchema);
        assertEquals(
                emptyFingerprint,
                schemaFingerprint(configuration, configuration.v231Schema));
        assertEquals(
                emptyFingerprint,
                schemaFingerprint(configuration, configuration.v232Schema));
    }

    private static void diffReplaySchemas(
            ReplayConfiguration configuration) throws SQLException {
        List<String> empty = schemaSignature(
                configuration,
                configuration.emptySchema);
        printSignatureDifference(
                configuration.emptySchema,
                empty,
                configuration.v231Schema,
                schemaSignature(configuration, configuration.v231Schema));
        printSignatureDifference(
                configuration.emptySchema,
                empty,
                configuration.v232Schema,
                schemaSignature(configuration, configuration.v232Schema));
    }

    private static void verifyGeneratedColumnMappings(
            ReplayConfiguration configuration) throws Exception {
        for (String schema : configuration.replaySchemas()) {
            assertOwnership(configuration, schema);
            assertCandidateLatest(configuration, schema);
            assertGeneratedColumnInventory(configuration, schema);
            List<TableCount> before = readGeneratedColumnTableCounts(
                    configuration,
                    schema);

            UnpooledDataSource dataSource = new UnpooledDataSource(
                    "com.mysql.cj.jdbc.Driver",
                    configuration.urlForSchema(schema),
                    configuration.username,
                    configuration.password);
            MybatisConfiguration mybatis = new MybatisConfiguration();
            mybatis.setMapUnderscoreToCamelCase(true);
            mybatis.setEnvironment(new Environment(
                    "generated-columns-" + schema,
                    new JdbcTransactionFactory(),
                    dataSource));
            registerGeneratedColumnMappers(mybatis);
            SqlSessionFactory factory =
                    new MybatisSqlSessionFactoryBuilder().build(mybatis);

            try (SqlSession session = factory.openSession(false)) {
                exerciseGeneratedColumnMappers(
                        session,
                        configuration.runId);
                session.rollback(true);
            }
            assertPreservedCounts(configuration, schema, before);
        }
    }

    private static void registerGeneratedColumnMappers(
            MybatisConfiguration configuration) {
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(TenantMemberMapper.class);
        configuration.addMapper(TenantSubscriptionMapper.class);
        configuration.addMapper(EntitlementOrderMapper.class);
        configuration.addMapper(MarketplaceOrderMapper.class);
        configuration.addMapper(AnnouncementMapper.class);
        configuration.addMapper(UserCapabilityRestrictionMapper.class);
        configuration.addMapper(AiPolicyVersionMapper.class);
        configuration.addMapper(SupportTicketMessageMapper.class);
        configuration.addMapper(AnnouncementActionRequestMapper.class);
    }

    private static void exerciseGeneratedColumnMappers(
            SqlSession session,
            String runId) {
        long seed = Math.abs((long) runId.hashCode()) + 1_000_000L;
        String suffix = runId.substring(0, Math.min(runId.length(), 16));
        String hash = repeatHex(runId);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        User user = new User();
        user.setUsername("gc-user-" + suffix);
        user.setPassword("unusable-test-hash");
        user.setNickname("Generated Column Test");
        user.setRole(2);
        user.setStatus(1);
        user.setDelFlag(0);
        user.setSuperAdminGuard(91);
        exercise(
                session.getMapper(UserMapper.class),
                user,
                User::getUserId,
                User::getSuperAdminGuard,
                null,
                value -> {
                    value.setSuperAdminGuard(92);
                    value.setNickname("Generated Column Updated");
                });

        TenantMember member = new TenantMember();
        member.setTenantId(seed);
        member.setUserId(seed + 1);
        member.setTenantRole("MEMBER");
        member.setStatus("ACTIVE");
        member.setIsDefault(1);
        member.setActiveDefaultUserGuard(seed + 99);
        member.setContextVersion(1L);
        member.setVersion(0);
        exercise(
                session.getMapper(TenantMemberMapper.class),
                member,
                TenantMember::getMemberId,
                TenantMember::getActiveDefaultUserGuard,
                seed + 1,
                value -> {
                    value.setActiveDefaultUserGuard(seed + 98);
                    value.setTenantRole("TENANT_ADMIN");
                });

        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(seed + 2);
        subscription.setPlanVersionId(seed + 3);
        subscription.setStatus("ACTIVE");
        subscription.setStartTime(now);
        subscription.setEndTime(now.plusDays(1));
        subscription.setSource("DEMO_TEST");
        subscription.setActiveSubscriptionTenantGuard(seed + 99);
        subscription.setVersion(0);
        exercise(
                session.getMapper(TenantSubscriptionMapper.class),
                subscription,
                TenantSubscription::getSubscriptionId,
                TenantSubscription::getActiveSubscriptionTenantGuard,
                seed + 2,
                value -> {
                    value.setActiveSubscriptionTenantGuard(seed + 98);
                    value.setSource("DEMO_TEST_UPDATED");
                });

        EntitlementOrder entitlementOrder = new EntitlementOrder();
        entitlementOrder.setTenantId(seed + 4);
        entitlementOrder.setOrderNo("GC-ENT-" + suffix);
        entitlementOrder.setUserId(seed + 5);
        entitlementOrder.setClientRequestId("gc-ent-" + suffix);
        entitlementOrder.setProductCode("DEMO_5_HOURS");
        entitlementOrder.setOrderType("PURCHASE");
        entitlementOrder.setSourceType("WRONG_SOURCE");
        entitlementOrder.setEntitlementMode("DURATION");
        entitlementOrder.setPricingVersion(1);
        entitlementOrder.setGrantSeconds(18000L);
        entitlementOrder.setAmountCents(500L);
        entitlementOrder.setReferenceAmountCents(500L);
        entitlementOrder.setPaidAmountCents(0L);
        entitlementOrder.setRefundedAmountCents(0L);
        entitlementOrder.setStatus("PENDING_PAYMENT");
        entitlementOrder.setExpireTime(now.plusDays(1));
        entitlementOrder.setVersion(0);
        exercise(
                session.getMapper(EntitlementOrderMapper.class),
                entitlementOrder,
                EntitlementOrder::getOrderId,
                EntitlementOrder::getSourceType,
                "DIRECT_PURCHASE",
                value -> {
                    value.setSourceType("WRONG_UPDATE");
                    value.setRemark("generated-column-update");
                });

        MarketplaceOrder marketOrder = new MarketplaceOrder();
        marketOrder.setOrderNo("GC-MARKET-" + suffix);
        marketOrder.setTenantId(seed + 6);
        marketOrder.setSubjectType("USER");
        marketOrder.setUserId(seed + 7);
        marketOrder.setSubjectKey("WRONG_SUBJECT");
        marketOrder.setActorUserId(seed + 8);
        marketOrder.setClientRequestId("gc-market-" + suffix);
        marketOrder.setRequestFingerprint(hash);
        marketOrder.setTotalAmountCents(100L);
        marketOrder.setPaymentMode("LOCAL_DEMO");
        marketOrder.setPaymentStatus("PENDING");
        marketOrder.setOrderStatus("PENDING_DEMO_PAYMENT");
        marketOrder.setVersion(0);
        exercise(
                session.getMapper(MarketplaceOrderMapper.class),
                marketOrder,
                MarketplaceOrder::getOrderId,
                MarketplaceOrder::getSubjectKey,
                "USER:" + (seed + 7),
                value -> {
                    value.setSubjectKey("WRONG_UPDATE");
                    value.setTotalAmountCents(101L);
                });

        Announcement announcement = new Announcement();
        announcement.setScopeType("PLATFORM");
        announcement.setScopeKey("WRONG_SCOPE");
        announcement.setAuthorUserId(seed + 9);
        announcement.setAuthorDisplayName("Generated Column Test");
        announcement.setClientRequestId("gc-ann-" + suffix);
        announcement.setRequestFingerprint(hash);
        announcement.setCurrentContentVersion(1);
        announcement.setStatus("DRAFT");
        announcement.setPinned(0);
        announcement.setAllowComments(1);
        announcement.setVersion(0);
        announcement.setDelFlag(0);
        exercise(
                session.getMapper(AnnouncementMapper.class),
                announcement,
                Announcement::getAnnouncementId,
                Announcement::getScopeKey,
                "PLATFORM",
                value -> {
                    value.setScopeKey("WRONG_UPDATE");
                    value.setPinned(1);
                });

        UserCapabilityRestriction restriction =
                new UserCapabilityRestriction();
        restriction.setScopeType("GLOBAL");
        restriction.setScopeKey("WRONG_SCOPE");
        restriction.setUserId(seed + 10);
        restriction.setCapability("SUPPORT_SUBMISSION");
        restriction.setStatus("ACTIVE");
        restriction.setReasonCode("DEMO_TEST");
        restriction.setOperatorUserId(seed + 11);
        restriction.setVersion(0);
        exercise(
                session.getMapper(UserCapabilityRestrictionMapper.class),
                restriction,
                UserCapabilityRestriction::getRestrictionId,
                UserCapabilityRestriction::getScopeKey,
                "GLOBAL",
                value -> {
                    value.setScopeKey("WRONG_UPDATE");
                    value.setReasonCode("DEMO_TEST_UPDATED");
                });

        AiPolicyVersion policyVersion = new AiPolicyVersion();
        policyVersion.setPolicyId(seed + 12);
        policyVersion.setVersionNo(1);
        policyVersion.setInstructionReference("test://instruction/" + suffix);
        policyVersion.setInstructionHash(hash);
        policyVersion.setResponseSchemaReference("test://schema/" + suffix);
        policyVersion.setResponseSchemaHash(hash);
        policyVersion.setApproveThresholdBps(9000);
        policyVersion.setManualThresholdBps(5000);
        policyVersion.setStatus("ACTIVE");
        policyVersion.setActivePolicyKey(seed + 99);
        policyVersion.setPublishTime(now);
        policyVersion.setVersion(0);
        exercise(
                session.getMapper(AiPolicyVersionMapper.class),
                policyVersion,
                AiPolicyVersion::getPolicyVersionId,
                AiPolicyVersion::getActivePolicyKey,
                seed + 12,
                value -> {
                    value.setActivePolicyKey(seed + 98);
                    value.setInstructionReference(
                            "test://instruction-updated/" + suffix);
                });

        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicketId(seed + 13);
        message.setTenantId(seed + 14);
        message.setSenderType("SYSTEM");
        message.setSenderKey("WRONG_SENDER");
        message.setClientRequestId("gc-message-" + suffix);
        message.setContentText("generated-column-test");
        message.setUserVisible(1);
        exercise(
                session.getMapper(SupportTicketMessageMapper.class),
                message,
                SupportTicketMessage::getMessageId,
                SupportTicketMessage::getSenderKey,
                "SYSTEM",
                value -> {
                    value.setSenderKey("WRONG_UPDATE");
                    value.setContentText("generated-column-test-updated");
                });

        AnnouncementActionRequest action = new AnnouncementActionRequest();
        action.setScopeType("PLATFORM");
        action.setScopeKey("WRONG_SCOPE");
        action.setAnnouncementId(seed + 15);
        action.setActorUserId(seed + 16);
        action.setActionType("UPDATE");
        action.setClientRequestId("gc-action-" + suffix);
        action.setRequestFingerprint(hash);
        action.setExpectedVersion(0);
        action.setRequestStatus("PENDING");
        action.setVersion(0);
        exercise(
                session.getMapper(AnnouncementActionRequestMapper.class),
                action,
                AnnouncementActionRequest::getActionRequestId,
                AnnouncementActionRequest::getScopeKey,
                "PLATFORM",
                value -> {
                    value.setScopeKey("WRONG_UPDATE");
                    value.setExpectedVersion(1);
                });
    }

    private static <T, K extends Serializable, G> void exercise(
            BaseMapper<T> mapper,
            T entity,
            java.util.function.Function<T, K> id,
            java.util.function.Function<T, G> generated,
            G expectedGenerated,
            java.util.function.Consumer<T> update) {
        assertEquals(1, mapper.insert(entity));
        T inserted = mapper.selectById(id.apply(entity));
        assertEquals(expectedGenerated, generated.apply(inserted));
        update.accept(inserted);
        assertEquals(1, mapper.updateById(inserted));
        T updated = mapper.selectById(id.apply(entity));
        assertEquals(expectedGenerated, generated.apply(updated));
    }

    private static String repeatHex(String value) {
        String normalized = value.replaceAll("[^a-fA-F0-9]", "a");
        if (normalized.isEmpty()) {
            normalized = "a";
        }
        StringBuilder result = new StringBuilder(64);
        while (result.length() < 64) {
            result.append(normalized);
        }
        return result.substring(0, 64).toLowerCase(Locale.ROOT);
    }

    private static List<TableCount> readGeneratedColumnTableCounts(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        List<TableCount> counts = new ArrayList<>();
        try (Connection connection = configuration.openSchema(schema)) {
            for (String table : Arrays.asList(
                    "sys_user",
                    "t_tenant_member",
                    "t_tenant_subscription",
                    "t_entitlement_order",
                    "t_market_order",
                    "t_announcement",
                    "t_user_capability_restriction",
                    "t_ai_policy_version",
                    "t_support_ticket_message",
                    "t_announcement_action_request")) {
                counts.add(new TableCount(
                        table,
                        queryLong(
                                connection,
                                "select count(*) from "
                                        + quoteIdentifier(table))));
            }
        }
        return counts;
    }

    private static void printSignatureDifference(
            String leftName,
            List<String> left,
            String rightName,
            List<String> right) {
        Set<String> onlyLeft = new LinkedHashSet<>(left);
        onlyLeft.removeAll(new LinkedHashSet<>(right));
        Set<String> onlyRight = new LinkedHashSet<>(right);
        onlyRight.removeAll(new LinkedHashSet<>(left));
        System.out.println(
                "REPLAY_DIFF pair=" + leftName + "|" + rightName
                        + " onlyLeft=" + onlyLeft.size()
                        + " onlyRight=" + onlyRight.size());
        printBoundedRows("ONLY_LEFT", onlyLeft);
        printBoundedRows("ONLY_RIGHT", onlyRight);
    }

    private static void printBoundedRows(String label, Set<String> values) {
        int emitted = 0;
        for (String value : values) {
            if (emitted >= 20) {
                break;
            }
            System.out.println(label + " " + value);
            emitted++;
        }
    }

    private static void cleanupReplaySchemas(
            ReplayConfiguration configuration) throws Exception {
        try (Connection server = configuration.openServer()) {
            assertServerIdentity(server, configuration.expectedServerUuid);
            for (String schema : configuration.schemas()) {
                if (schemaCount(server, schema) > 0L) {
                    assertOwnership(server, schema, configuration);
                }
            }
            for (String schema : configuration.replaySchemas()) {
                if (schemaCount(server, schema) == 0L) {
                    continue;
                }
                execute(server, "drop database " + quoteIdentifier(schema));
            }
            if (schemaCount(server, configuration.controlSchema) > 0L) {
                execute(
                        server,
                        "drop database "
                                + quoteIdentifier(configuration.controlSchema));
            }
            for (String schema : configuration.schemas()) {
                assertEquals(0L, schemaCount(server, schema));
            }
        }
    }

    private static Flyway flyway(
            ReplayConfiguration configuration,
            String schema,
            String target,
            boolean p3cCodeReady) {
        org.flywaydb.core.api.configuration.FluentConfiguration fluent =
                Flyway.configure()
                        .dataSource(
                                configuration.urlForSchema(schema),
                                configuration.username,
                                configuration.password)
                        .locations("classpath:db/migration")
                        .encoding("UTF-8")
                        .validateOnMigrate(true)
                        .outOfOrder(false)
                        .cleanDisabled(true)
                        .baselineOnMigrate(false)
                        .placeholders(Collections.singletonMap(
                                "p3cCodeReady",
                                Boolean.toString(p3cCodeReady)));
        if (target != null) {
            fluent.target(target);
        }
        return fluent.load();
    }

    private static void createOwnedSchemas(
            Connection server,
            ReplayConfiguration configuration) throws SQLException {
        execute(
                server,
                "create database "
                        + quoteIdentifier(configuration.controlSchema)
                        + " character set utf8mb4 "
                        + "collate utf8mb4_0900_ai_ci");
        execute(
                server,
                "create table "
                        + quoteIdentifier(configuration.controlSchema)
                        + ".__wifi_test_ownership ("
                        + "schema_name varchar(64) not null,"
                        + "run_id varchar(24) not null,"
                        + "owner_token varchar(80) not null,"
                        + "created_at datetime not null default current_timestamp,"
                        + "primary key (schema_name))");
        registerOwnership(
                server,
                configuration.controlSchema,
                configuration);
        for (String schema : configuration.replaySchemas()) {
            execute(
                    server,
                    "create database " + quoteIdentifier(schema)
                            + " character set utf8mb4 "
                            + "collate utf8mb4_0900_ai_ci");
            registerOwnership(server, schema, configuration);
        }
    }

    private static void registerOwnership(
            Connection server,
            String schema,
            ReplayConfiguration configuration) throws SQLException {
        try (PreparedStatement statement = server.prepareStatement(
                "insert into "
                        + quoteIdentifier(configuration.controlSchema)
                        + ".__wifi_test_ownership "
                        + "(schema_name, run_id, owner_token) "
                        + "values (?, ?, ?)")) {
            statement.setString(1, schema);
            statement.setString(2, configuration.runId);
            statement.setString(3, configuration.ownerToken);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void restoreDump(
            ReplayConfiguration configuration,
            String schema,
            Path dump) throws Exception {
        String sql = new String(Files.readAllBytes(dump), StandardCharsets.UTF_8);
        sql = replaceExactlyOnce(
                sql,
                CREATE_DATABASE_PATTERN,
                "CREATE DATABASE IF NOT EXISTS " + quoteIdentifier(schema)
                        + " CHARACTER SET utf8mb4 "
                        + "COLLATE utf8mb4_0900_ai_ci;");
        sql = replaceExactlyOnce(
                sql,
                USE_DATABASE_PATTERN,
                "USE " + quoteIdentifier(schema) + ";");

        try (Connection connection = configuration.openSchema(schema)) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String replaceExactlyOnce(
            String value,
            Pattern pattern,
            String replacement) {
        Matcher matcher = pattern.matcher(value);
        assertTrue(matcher.find(), "required dump directive is missing");
        int start = matcher.start();
        int end = matcher.end();
        assertFalse(matcher.find(), "dump directive appears more than once");
        return value.substring(0, start) + replacement + value.substring(end);
    }

    private static void assertCandidateLatest(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        assertSchemaState(configuration, schema, "2.9.3");
    }

    private static void assertC0Latest(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        assertSchemaState(configuration, schema, "2.9.2");
    }

    private static void assertContractLatest(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        assertSchemaState(configuration, schema, "2.10");
    }

    private static void assertSchemaState(
            ReplayConfiguration configuration,
            String schema,
            String expectedVersion) throws SQLException {
        assertFlywayLatest(configuration, schema, expectedVersion);
        try (Connection connection = configuration.openSchema(schema)) {
            long expectedTableCount =
                    "2.9.1".equals(expectedVersion) ? 70L : 72L;
            assertEquals(expectedTableCount, queryLong(
                    connection,
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() "
                            + "and table_type = 'BASE TABLE' "
                            + "and table_name not in "
                            + "('__wifi_test_ownership',"
                            + "'flyway_schema_history')"));
        }
    }

    private static void insertLegalDefaultMembershipOutboxRow(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openSchema(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "insert into t_default_tenant_membership_outbox "
                             + "(event_id, idempotency_key, "
                             + "request_fingerprint, user_id, role, status, "
                             + "retry_count, next_retry_time) "
                             + "values (?, ?, ?, ?, 2, 'PENDING', 0, "
                             + "current_timestamp)")) {
            String key = "c0-legal-" + configuration.runId;
            statement.setString(1, key + "-event");
            statement.setString(2, key);
            statement.setString(3, repeatHex(configuration.runId));
            statement.setLong(
                    4,
                    9_000_000_000L
                            + Math.abs((long) schema.hashCode()));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertC01UpgradeFixtures(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        long businessId =
                9_300_000_000L + Math.abs((long) schema.hashCode());
        try (Connection connection = configuration.openSchema(schema)) {
            execute(
                    connection,
                    "insert into t_verify_code "
                            + "(target, target_type, scene, code_hash, "
                            + "verification_provider, provider_out_id, "
                            + "send_status, verify_status, status, "
                            + "expire_time) values "
                            + "('c01-phone-" + configuration.runId
                            + "', 'phone', 'register', 'unusable-test-hash', "
                            + "'aliyun-number-auth', 'c01-provider-"
                            + configuration.runId
                            + "', 1, 0, 0, "
                            + "date_add(current_timestamp, interval 1 hour))");
            execute(
                    connection,
                    "insert into t_ai_review_task "
                            + "(review_request_id, scene, business_type, "
                            + "business_id, content_version, content_hash, "
                            + "policy_version_id, task_status, attempt_count) "
                            + "values ('c01-legacy-ai-"
                            + configuration.runId
                            + "', 'ANNOUNCEMENT_REVIEW', 'ANNOUNCEMENT', "
                            + businessId + ", 1, '"
                            + repeatHex(configuration.runId)
                            + "', 9300000001, 'RUNNING', 1)");
        }
    }

    private static void assertCrossCuttingRecoveryContract(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openSchema(schema)) {
            assertEquals(3L, queryLong(
                    connection,
                    "select count(*) "
                            + "from information_schema.columns "
                            + "where table_schema = database() "
                            + "and table_name = 't_verify_code' "
                            + "and column_name in ("
                            + "'verify_claim_owner',"
                            + "'verify_lease_until',"
                            + "'verify_claimed_time')"));
            assertEquals(7L, queryLong(
                    connection,
                    "select count(*) "
                            + "from information_schema.statistics "
                            + "where table_schema = database() "
                            + "and table_name = 't_verify_code' "
                            + "and index_name = 'idx_verify_code_claim'"));
            assertEquals(4L, queryLong(
                    connection,
                    "select count(*) "
                            + "from information_schema.table_constraints "
                            + "where constraint_schema = database() "
                            + "and constraint_name in ("
                            + "'chk_verify_code_claim_owner',"
                            + "'chk_support_submission_review_task',"
                            + "'chk_support_manual_without_ai_task',"
                            + "'chk_ai_review_task_claim_owner')"));
            assertEquals(0L, queryLong(
                    connection,
                    "select count(*) "
                            + "from information_schema.table_constraints "
                            + "where constraint_schema = database() "
                            + "and table_name = 't_support_submission' "
                            + "and constraint_name = "
                            + "'t_support_submission_chk_12'"));

            assertVerificationCodeClaimContract(
                    connection,
                    configuration.runId);
            assertSupportManualReviewContract(
                    connection,
                    configuration.runId);
            assertLegacyAiClaimRecoveryContract(
                    connection,
                    configuration.runId);
        }
    }

    private static void assertVerificationCodeClaimContract(
            Connection connection,
            String runId) throws SQLException {
        String target = "c01-phone-" + runId;
        assertEquals(1L, queryLong(
                connection,
                "select count(*) from t_verify_code "
                        + "where target = '" + target + "' "
                        + "and verify_claim_owner is null "
                        + "and verify_lease_until is null "
                        + "and verify_claimed_time is null"));

        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "update t_verify_code "
                                + "set verify_claim_owner = 'worker-c01' "
                                + "where target = '" + target + "'"));
        execute(
                connection,
                "update t_verify_code "
                        + "set verify_claim_owner = 'worker-c01', "
                        + "verify_claimed_time = current_timestamp, "
                        + "verify_lease_until = date_add("
                        + "current_timestamp, interval 1 minute) "
                        + "where target = '" + target + "'");
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "update t_verify_code "
                                + "set verify_lease_until = date_sub("
                                + "verify_claimed_time, interval 1 second) "
                                + "where target = '" + target + "'"));
        execute(
                connection,
                "update t_verify_code "
                        + "set verify_claim_owner = null, "
                        + "verify_claimed_time = null, "
                        + "verify_lease_until = null "
                        + "where target = '" + target + "'");
    }

    private static void assertSupportManualReviewContract(
            Connection connection,
            String runId) throws SQLException {
        String fingerprint = repeatHex(runId);
        execute(
                connection,
                "insert into t_support_submission "
                        + "(tenant_id, user_id, client_request_id, "
                        + "request_fingerprint, title, content_hash, "
                        + "accepted_time, quota_date, status, "
                        + "review_request_id, review_decision, "
                        + "outcome_reason_code) values "
                        + "(9400000001, 9400000002, 'c01-manual-" + runId
                        + "', '" + fingerprint
                        + "', 'C0.1 manual review', '" + fingerprint
                        + "', current_timestamp, current_date, "
                        + "'MANUAL_REVIEW', 'c01-manual-review-" + runId
                        + "', 'MANUAL', 'AI_REVIEW_MANUAL_REQUIRED')");
        assertEquals(1L, queryLong(
                connection,
                "select count(*) from t_support_submission "
                        + "where client_request_id = 'c01-manual-" + runId
                        + "' and status = 'MANUAL_REVIEW' "
                        + "and ai_review_task_id is null"));

        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_support_submission "
                                + "(tenant_id, user_id, client_request_id, "
                                + "request_fingerprint, title, content_hash, "
                                + "accepted_time, quota_date, status, "
                                + "review_request_id, review_decision, "
                                + "outcome_reason_code) values "
                                + "(9400000001, 9400000003, "
                                + "'c01-bad-manual-" + runId
                                + "', '" + fingerprint
                                + "', 'Invalid manual review', '"
                                + fingerprint
                                + "', current_timestamp, current_date, "
                                + "'MANUAL_REVIEW', 'c01-bad-review-" + runId
                                + "', 'MANUAL', 'AI_PROVIDER_UNAVAILABLE')"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_support_submission "
                                + "(tenant_id, user_id, client_request_id, "
                                + "request_fingerprint, title, content_hash, "
                                + "accepted_time, quota_date, status, "
                                + "review_request_id, review_decision) values "
                                + "(9400000001, 9400000004, "
                                + "'c01-null-manual-" + runId
                                + "', '" + fingerprint
                                + "', 'Null manual reason', '"
                                + fingerprint
                                + "', current_timestamp, current_date, "
                                + "'MANUAL_REVIEW', 'c01-null-review-" + runId
                                + "', 'MANUAL')"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_support_submission "
                                + "(tenant_id, user_id, client_request_id, "
                                + "request_fingerprint, title, content_hash, "
                                + "accepted_time, quota_date, status, "
                                + "review_request_id, review_decision) values "
                                + "(9400000001, 9400000005, "
                                + "'c01-bad-accepted-" + runId
                                + "', '" + fingerprint
                                + "', 'Invalid accepted review', '"
                                + fingerprint
                                + "', current_timestamp, current_date, "
                                + "'ACCEPTED', 'c01-bad-accepted-review-"
                                + runId + "', 'APPROVE')"));
    }

    private static void assertLegacyAiClaimRecoveryContract(
            Connection connection,
            String runId) throws SQLException {
        String requestId = "c01-legacy-ai-" + runId;
        assertEquals(1L, queryLong(
                connection,
                "select count(*) from t_ai_review_task "
                        + "where review_request_id = '" + requestId + "' "
                        + "and task_status = 'QUEUED' "
                        + "and worker_id is null "
                        + "and lease_until is null "
                        + "and claimed_time is null "
                        + "and next_retry_time is not null "
                        + "and last_error_code = "
                        + "'LEGACY_RUNNING_CLAIM_RECOVERED' "
                        + "and attempt_count = 1"));

        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "update t_ai_review_task "
                                + "set task_status = 'RUNNING' "
                                + "where review_request_id = '"
                                + requestId + "'"));
        execute(
                connection,
                "update t_ai_review_task "
                        + "set task_status = 'RUNNING', "
                        + "worker_id = 'worker-c01', "
                        + "claimed_time = current_timestamp, "
                        + "lease_until = date_add("
                        + "current_timestamp, interval 1 minute) "
                        + "where review_request_id = '" + requestId + "'");
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "update t_ai_review_task "
                                + "set task_status = 'QUEUED' "
                                + "where review_request_id = '"
                                + requestId + "'"));
        execute(
                connection,
                "update t_ai_review_task "
                        + "set task_status = 'QUEUED', "
                        + "worker_id = null, lease_until = null, "
                        + "claimed_time = null "
                        + "where review_request_id = '" + requestId + "'");
    }

    private static void assertTransactionOutboxIdempotencyContract(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openSchema(schema)) {
            String key = "c0-legal-" + configuration.runId;
            assertEquals(1L, queryLong(
                    connection,
                    "select count(*) "
                            + "from t_default_tenant_membership_outbox "
                            + "where idempotency_key = '" + key + "' "
                            + "and status = 'PENDING' "
                            + "and worker_id is null "
                            + "and lease_until is null"));
            assertEquals(2L, queryLong(
                    connection,
                    "select count(*) "
                            + "from information_schema.tables "
                            + "where table_schema = database() "
                            + "and table_name in ("
                            + "'t_user_auth_session_revoke_outbox',"
                            + "'t_entitlement_lease_receipt')"));
            assertEquals(2L, queryLong(
                    connection,
                    "select count(*) "
                            + "from information_schema.table_constraints "
                            + "where table_schema = database() "
                            + "and table_name = "
                            + "'t_default_tenant_membership_outbox' "
                            + "and constraint_name in ("
                            + "'chk_default_membership_outbox_status',"
                            + "'chk_default_membership_claim_owner')"));

            assertDefaultMembershipLeaseContract(connection, key);
            assertAuthRevokeOutboxContract(
                    connection,
                    configuration.runId);
            assertEntitlementLeaseReceiptContract(
                    connection,
                    configuration.runId);
        }
    }

    private static void assertDefaultMembershipLeaseContract(
            Connection connection,
            String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update t_default_tenant_membership_outbox "
                        + "set status = 'PROCESSING', worker_id = 'worker-c0', "
                        + "claimed_time = current_timestamp, "
                        + "lease_until = date_add(current_timestamp, "
                        + "interval 1 minute) "
                        + "where idempotency_key = ?")) {
            statement.setString(1, key);
            assertEquals(1, statement.executeUpdate());
        }

        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "update t_default_tenant_membership_outbox "
                                + "set lease_until = date_sub("
                                + "claimed_time, interval 1 second) "
                                + "where idempotency_key = '" + key + "'"));

        try (PreparedStatement statement = connection.prepareStatement(
                "update t_default_tenant_membership_outbox "
                        + "set status = 'PENDING', worker_id = null, "
                        + "lease_until = null, claimed_time = null "
                        + "where idempotency_key = ?")) {
            statement.setString(1, key);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertAuthRevokeOutboxContract(
            Connection connection,
            String runId) throws SQLException {
        execute(
                connection,
                "insert into t_user_auth_session_revoke_outbox "
                        + "(event_id, user_id, revoke_reason, status) values "
                        + "('auth-pending-" + runId
                        + "', 9100000001, 'PASSWORD_CHANGED', 'PENDING')");
        execute(
                connection,
                "insert into t_user_auth_session_revoke_outbox "
                        + "(event_id, user_id, revoke_reason, status, "
                        + "worker_id, claimed_time, lease_until) values "
                        + "('auth-processing-" + runId
                        + "', 9100000002, 'PASSWORD_CHANGED', 'PROCESSING', "
                        + "'worker-c0', current_timestamp, "
                        + "date_add(current_timestamp, interval 1 minute))");
        execute(
                connection,
                "insert into t_user_auth_session_revoke_outbox "
                        + "(event_id, user_id, revoke_reason, status, "
                        + "next_retry_time, completed_time) values "
                        + "('auth-succeeded-" + runId
                        + "', 9100000003, 'PASSWORD_CHANGED', 'SUCCEEDED', "
                        + "null, current_timestamp)");

        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_user_auth_session_revoke_outbox "
                                + "(event_id, user_id, revoke_reason, status, "
                                + "worker_id, claimed_time, lease_until) "
                                + "values ('auth-bad-lease-" + runId
                                + "', 9100000004, 'PASSWORD_CHANGED', "
                                + "'PROCESSING', 'worker-c0', "
                                + "current_timestamp, "
                                + "date_sub(current_timestamp, "
                                + "interval 1 second))"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_user_auth_session_revoke_outbox "
                                + "(event_id, user_id, revoke_reason, status, "
                                + "next_retry_time) values "
                                + "('auth-bad-terminal-" + runId
                                + "', 9100000005, 'PASSWORD_CHANGED', "
                                + "'DEAD', null)"));
    }

    private static void assertEntitlementLeaseReceiptContract(
            Connection connection,
            String runId) throws SQLException {
        String fingerprint = repeatHex(runId);
        execute(
                connection,
                "insert into t_entitlement_lease_receipt "
                        + "(tenant_id, request_id, request_fingerprint, "
                        + "user_id, session_id, usage_seconds, "
                        + "requested_ttl_seconds, receipt_status, "
                        + "result_allowed, result_charged_seconds, "
                        + "result_reason, completed_time) values "
                        + "(9200000001, 'lease-denied-" + runId + "', '"
                        + fingerprint
                        + "', 9200000002, 9200000003, 0, 20, 'COMPLETED', "
                        + "0, 0, 'USER_UNAVAILABLE', current_timestamp)");
        execute(
                connection,
                "insert into t_entitlement_lease_receipt "
                        + "(tenant_id, request_id, request_fingerprint, "
                        + "entitlement_id, user_id, session_id, "
                        + "usage_seconds, requested_ttl_seconds, "
                        + "receipt_status, result_allowed, "
                        + "result_entitlement_id, result_mode, "
                        + "result_ttl_seconds, result_charged_seconds, "
                        + "result_remaining_seconds, result_reason, "
                        + "completed_time) values "
                        + "(9200000001, 'lease-allowed-" + runId + "', '"
                        + fingerprint
                        + "', 9200000004, 9200000002, 9200000003, 5, 20, "
                        + "'COMPLETED', 1, 9200000004, 'DURATION', "
                        + "15, 5, 95, 'DURATION_AVAILABLE', "
                        + "current_timestamp)");

        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_entitlement_lease_receipt "
                                + "(tenant_id, request_id, "
                                + "request_fingerprint, user_id, session_id, "
                                + "usage_seconds, requested_ttl_seconds, "
                                + "receipt_status, result_allowed, "
                                + "result_charged_seconds, result_reason, "
                                + "completed_time) values "
                                + "(9200000001, 'lease-negative-" + runId
                                + "', '" + fingerprint
                                + "', 9200000002, 9200000003, 0, 20, "
                                + "'COMPLETED', 0, -1, 'DENIED', "
                                + "current_timestamp)"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_entitlement_lease_receipt "
                                + "(tenant_id, request_id, "
                                + "request_fingerprint, user_id, session_id, "
                                + "usage_seconds, requested_ttl_seconds, "
                                + "receipt_status, result_allowed, "
                                + "result_entitlement_id, result_mode, "
                                + "result_charged_seconds, "
                                + "result_remaining_seconds, result_reason, "
                                + "completed_time) values "
                                + "(9200000001, 'lease-no-ttl-" + runId
                                + "', '" + fingerprint
                                + "', 9200000002, 9200000003, 0, 20, "
                                + "'COMPLETED', 1, 9200000004, 'DURATION', "
                                + "0, 100, 'DURATION_AVAILABLE', "
                                + "current_timestamp)"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_entitlement_lease_receipt "
                                + "(tenant_id, request_id, "
                                + "request_fingerprint, user_id, session_id, "
                                + "usage_seconds, requested_ttl_seconds, "
                                + "receipt_status, result_allowed, "
                                + "result_ttl_seconds, "
                                + "result_charged_seconds, result_reason, "
                                + "completed_time) values "
                                + "(9200000001, 'lease-denied-ttl-" + runId
                                + "', '" + fingerprint
                                + "', 9200000002, 9200000003, 0, 20, "
                                + "'COMPLETED', 0, 20, 0, 'DENIED', "
                                + "current_timestamp)"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_entitlement_lease_receipt "
                                + "(tenant_id, request_id, "
                                + "request_fingerprint, user_id, session_id, "
                                + "usage_seconds, requested_ttl_seconds, "
                                + "receipt_status, result_allowed, "
                                + "result_entitlement_id, result_mode, "
                                + "result_ttl_seconds, "
                                + "result_charged_seconds, "
                                + "result_remaining_seconds, result_reason, "
                                + "completed_time) values "
                                + "(9200000001, 'lease-zero-remaining-"
                                + runId + "', '" + fingerprint
                                + "', 9200000002, 9200000003, 0, 20, "
                                + "'COMPLETED', 1, 9200000004, 'DURATION', "
                                + "20, 0, 0, 'DURATION_AVAILABLE', "
                                + "current_timestamp)"));
        assertThrows(
                SQLException.class,
                () -> execute(
                        connection,
                        "insert into t_entitlement_lease_receipt "
                                + "(tenant_id, request_id, "
                                + "request_fingerprint, user_id, session_id, "
                                + "usage_seconds, requested_ttl_seconds) "
                                + "values (9200000001, 'lease-denied-" + runId
                                + "', '" + fingerprint
                                + "', 9200000002, 9200000003, 0, 20)"));
    }

    private static void assertFlywayLatest(
            ReplayConfiguration configuration,
            String schema,
            String expectedVersion) throws SQLException {
        try (Connection connection = configuration.openSchema(schema);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select version, success "
                             + "from flyway_schema_history "
                             + "order by installed_rank desc limit 1")) {
            assertTrue(rows.next());
            assertEquals(expectedVersion, rows.getString("version"));
            assertTrue(rows.getBoolean("success"));
            assertFalse(rows.next());
        }
    }

    private static void assertNoSensitiveAiColumns(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openSchema(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) "
                             + "from information_schema.columns "
                             + "where table_schema = ? "
                             + "and table_name like 't_ai_%' "
                             + "and ("
                             + "column_name like '%prompt%' "
                             + "or column_name like '%credential%' "
                             + "or column_name like '%password%' "
                             + "or column_name like '%secret%' "
                             + "or column_name in "
                             + "('provider_response','full_response','raw_response'))")) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(0L, rows.getLong(1));
            }
        }
    }

    private static void assertGeneratedColumnInventory(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        Set<String> actual = new LinkedHashSet<>();
        try (Connection connection = configuration.openSchema(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "select table_name, column_name "
                             + "from information_schema.columns "
                             + "where table_schema = ? "
                             + "and extra like '%STORED GENERATED%' "
                             + "order by table_name, column_name")) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    actual.add(rows.getString(1) + "." + rows.getString(2));
                }
            }
        }
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                "sys_user.super_admin_guard",
                "t_ai_policy_version.active_policy_key",
                "t_announcement.scope_key",
                "t_announcement_action_request.scope_key",
                "t_entitlement_order.source_type",
                "t_market_order.subject_key",
                "t_support_ticket_message.sender_key",
                "t_tenant_member.active_default_user_guard",
                "t_tenant_subscription.active_subscription_tenant_guard",
                "t_user_capability_restriction.scope_key")), actual);
    }

    private static List<TableCount> readTableCounts(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = configuration.openSchema(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "select table_name from information_schema.tables "
                             + "where table_schema = ? "
                             + "and table_type = 'BASE TABLE' "
                             + "and table_name not in "
                             + "('__wifi_test_ownership',"
                             + "'flyway_schema_history') "
                             + "order by table_name")) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
        }

        List<TableCount> counts = new ArrayList<>();
        try (Connection connection = configuration.openSchema(schema)) {
            for (String table : tables) {
                counts.add(new TableCount(
                        table,
                        queryLong(
                                connection,
                                "select count(*) from "
                                        + quoteIdentifier(table))));
            }
        }
        return counts;
    }

    private static void assertPreservedCounts(
            ReplayConfiguration configuration,
            String schema,
            List<TableCount> before) throws SQLException {
        try (Connection connection = configuration.openSchema(schema)) {
            for (TableCount table : before) {
                assertEquals(
                        table.count,
                        queryLong(
                                connection,
                                "select count(*) from "
                                        + quoteIdentifier(table.name)),
                        table.name + " row count changed during replay");
            }
        }
    }

    private static String schemaFingerprint(
            ReplayConfiguration configuration,
            String schema) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String line : schemaSignature(configuration, schema)) {
            digest.update(line.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return toHex(digest.digest());
    }

    private static List<String> schemaSignature(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        List<String> signature = new ArrayList<>();
        try (Connection connection = configuration.openSchema(schema)) {
            appendSignatureRows(
                    signature,
                    "COLUMN",
                    connection,
                    "select table_name, column_name, column_type, is_nullable, "
                            + "coalesce(column_default, '<NULL>'), extra, "
                            + "coalesce(collation_name, '<NULL>'), "
                            + "coalesce(generation_expression, '<NULL>') "
                            + "from information_schema.columns "
                            + "where table_schema = database() "
                            + "and table_name <> '__wifi_test_ownership' "
                            + "order by table_name, column_name",
                    8);
            appendSignatureRows(
                    signature,
                    "INDEX",
                    connection,
                    "select table_name, index_name, non_unique, seq_in_index, "
                            + "coalesce(column_name, '<NULL>'), "
                            + "coalesce(collation, '<NULL>'), "
                            + "coalesce(sub_part, -1), index_type "
                            + "from information_schema.statistics "
                            + "where table_schema = database() "
                            + "and table_name <> '__wifi_test_ownership' "
                            + "order by table_name, index_name, seq_in_index",
                    8);
            appendSignatureRows(
                    signature,
                    "CONSTRAINT",
                    connection,
                    "select table_name, constraint_name, constraint_type "
                            + "from information_schema.table_constraints "
                            + "where table_schema = database() "
                            + "and table_name <> '__wifi_test_ownership' "
                            + "order by table_name, constraint_name",
                    3);
        }
        return signature;
    }

    private static void appendSignatureRows(
            List<String> signature,
            String type,
            Connection connection,
            String sql,
            int columns) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                StringBuilder line = new StringBuilder(type);
                for (int column = 1; column <= columns; column++) {
                    String value = rows.getString(column);
                    line.append('|').append(
                            value == null ? "<NULL>" : value);
                }
                signature.add(line.toString());
            }
        }
    }

    private static void assertOwnership(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openServer()) {
            assertOwnership(connection, schema, configuration);
        }
    }

    private static void assertOwnership(
            Connection connection,
            String schema,
            ReplayConfiguration configuration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from "
                        + quoteIdentifier(configuration.controlSchema)
                        + ".__wifi_test_ownership "
                        + "where schema_name = ? "
                        + "and run_id = ? and owner_token = ?")) {
            statement.setString(1, schema);
            statement.setString(2, configuration.runId);
            statement.setString(3, configuration.ownerToken);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1L, rows.getLong(1));
            }
        }
    }

    private static void assertServerIdentity(
            Connection connection,
            String expectedServerUuid) throws SQLException {
        DatabaseIdentity identity = readIdentity(connection);
        assertEquals(expectedServerUuid, identity.serverUuid);
        assertEquals("xwh", identity.serverHostname);
        assertEquals(3306, identity.serverPort);
        assertTrue(identity.version.startsWith("8.4."));
    }

    private static DatabaseIdentity readIdentity(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select database(), @@server_uuid, @@version, "
                             + "@@hostname, @@port")) {
            assertTrue(rows.next());
            return new DatabaseIdentity(
                    rows.getString(1),
                    rows.getString(2),
                    rows.getString(3),
                    rows.getString(4),
                    rows.getInt(5));
        }
    }

    private static long schemaCount(Connection connection, String schema)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from information_schema.schemata "
                        + "where schema_name = ?")) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private static long businessTableCount(
            ReplayConfiguration configuration,
            String schema) throws SQLException {
        try (Connection connection = configuration.openSchema(schema);
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from information_schema.tables "
                             + "where table_schema = ? "
                             + "and table_type = 'BASE TABLE' "
                             + "and table_name not in "
                             + "('__wifi_test_ownership',"
                             + "'flyway_schema_history')")) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private static long queryLong(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void verifyDump(Path path, String expectedHash)
            throws Exception {
        assertTrue(Files.isRegularFile(path), "backup dump is missing: " + path);
        assertEquals(expectedHash, sha256(path));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (java.io.InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private static String quoteIdentifier(String value) {
        if (!value.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("unsafe MySQL identifier");
        }
        return "`" + value + "`";
    }

    private static String requireValue(
            Map<String, String> environment,
            String name) {
        TestEnvironmentGuard.requireVariables(environment, name);
        return environment.get(name).trim();
    }

    private static final class ReplayConfiguration {

        private final String serverUrl;
        private final String username;
        private final String password;
        private final String runId;
        private final String ownerToken;
        private final String expectedServerUuid;
        private final String expectedServerHostname;
        private final Path v231Dump;
        private final Path v232Dump;
        private final String controlSchema;
        private final String emptySchema;
        private final String v231Schema;
        private final String v232Schema;

        private ReplayConfiguration(
                String serverUrl,
                String username,
                String password,
                String runId,
                String ownerToken,
                String expectedServerUuid,
                String expectedServerHostname,
                Path v231Dump,
                Path v232Dump) {
            this.serverUrl = serverUrl;
            this.username = username;
            this.password = password;
            this.runId = runId;
            this.ownerToken = ownerToken;
            this.expectedServerUuid = expectedServerUuid;
            this.expectedServerHostname = expectedServerHostname;
            this.v231Dump = v231Dump;
            this.v232Dump = v232Dump;
            this.controlSchema = "wifi_test_13h_control_" + runId;
            this.emptySchema = "wifi_test_13h_empty_" + runId;
            this.v231Schema = "wifi_test_13h_v231_" + runId;
            this.v232Schema = "wifi_test_13h_v232_" + runId;
            for (String schema : schemas()) {
                TestEnvironmentGuard.requireSafeMySqlSchema(
                        urlForSchema(schema));
            }
        }

        private static ReplayConfiguration from(
                Map<String, String> environment) throws IOException {
            TestEnvironmentGuard.requireEnabled(environment, ENABLED);
            TestEnvironmentGuard.requireEnabled(environment, SCHEMA_DDL);
            assertEquals(
                    "dedicated-test-account",
                    requireValue(environment, ACCOUNT_MARKER));

            String serverUrl = requireValue(environment, SERVER_URL);
            URI uri = URI.create(serverUrl.substring("jdbc:".length()));
            assertEquals("mysql", uri.getScheme());
            assertTrue("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()));
            assertEquals(3306, uri.getPort());
            assertTrue(uri.getPath() == null
                    || uri.getPath().isEmpty()
                    || "/".equals(uri.getPath()));
            assertTrue(uri.getUserInfo() == null);

            String runId = requireValue(environment, RUN_ID);
            assertTrue(RUN_ID_PATTERN.matcher(runId).matches());
            String ownerToken = requireValue(environment, OWNER_TOKEN);
            assertTrue(OWNER_TOKEN_PATTERN.matcher(ownerToken).matches());
            String expectedUuid = requireValue(environment, SERVER_UUID);
            assertTrue(expectedUuid.matches("[0-9a-fA-F-]{36}"));
            String expectedHostname = requireValue(
                    environment,
                    SERVER_HOSTNAME);
            assertTrue(expectedHostname.matches("[A-Za-z0-9._-]{1,128}"));

            Path v231Dump = Paths.get(requireValue(environment, V231_DUMP))
                    .toAbsolutePath()
                    .normalize();
            Path v232Dump = Paths.get(requireValue(environment, V232_DUMP))
                    .toAbsolutePath()
                    .normalize();
            assertFalse(v231Dump.equals(v232Dump));

            return new ReplayConfiguration(
                    serverUrl,
                    requireValue(environment, USERNAME),
                    requireValue(environment, PASSWORD),
                    runId,
                    ownerToken,
                    expectedUuid,
                    expectedHostname,
                    v231Dump,
                    v232Dump);
        }

        private List<String> schemas() {
            return Arrays.asList(
                    controlSchema,
                    emptySchema,
                    v231Schema,
                    v232Schema);
        }

        private List<String> replaySchemas() {
            return Arrays.asList(
                    emptySchema,
                    v231Schema,
                    v232Schema);
        }

        private Connection openServer() throws SQLException {
            return DriverManager.getConnection(serverUrl, username, password);
        }

        private Connection openSchema(String schema) throws SQLException {
            return DriverManager.getConnection(
                    urlForSchema(schema),
                    username,
                    password);
        }

        private String urlForSchema(String schema) {
            int queryStart = serverUrl.indexOf('?');
            String beforeQuery = queryStart < 0
                    ? serverUrl
                    : serverUrl.substring(0, queryStart);
            String query = queryStart < 0
                    ? ""
                    : serverUrl.substring(queryStart);
            String normalized = beforeQuery.endsWith("/")
                    ? beforeQuery
                    : beforeQuery + "/";
            return normalized + schema + query;
        }
    }

    private static final class DatabaseIdentity {

        private final String catalog;
        private final String serverUuid;
        private final String version;
        private final String serverHostname;
        private final int serverPort;

        private DatabaseIdentity(
                String catalog,
                String serverUuid,
                String version,
                String serverHostname,
                int serverPort) {
            this.catalog = catalog;
            this.serverUuid = serverUuid;
            this.version = version;
            this.serverHostname = serverHostname;
            this.serverPort = serverPort;
        }
    }

    private static final class TableCount {

        private final String name;
        private final long count;

        private TableCount(String name, long count) {
            this.name = name;
            this.count = count;
        }
    }
}
