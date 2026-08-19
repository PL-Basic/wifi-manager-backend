package com.plagod.testkit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreUseCaseIndexContractTest {

    private static final String FIXTURE_DIRECTORY =
            "/fixtures/core-use-cases-v1/";
    private static final String MANIFEST = "manifest.json";
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private static final List<String> COMMON_OPERATION_FIELDS =
            Arrays.asList(
                    "operationId",
                    "kind",
                    "owner",
                    "actor",
                    "context",
                    "stateModel",
                    "stateMachineRefs",
                    "input",
                    "result",
                    "errorKeys",
                    "implementationStatus",
                    "conflicts",
                    "evidence",
                    "targetLayer");
    private static final List<String> COMMAND_FIELDS =
            Arrays.asList(
                    "idempotencyKey",
                    "fingerprint",
                    "transactionBoundary",
                    "externalEffects",
                    "auditPolicy",
                    "legalStateTransitions");
    private static final List<String> QUERY_FIELDS =
            Arrays.asList("scope", "resultLimit", "readOnly");

    private static JsonNode root;
    private static JsonNode manifest;
    private static JsonNode crossDomainEvents;
    private static JsonNode auditClosures;
    private static final Map<String, JsonNode> domainFixtures =
            new LinkedHashMap<>();
    private static Path repositoryRoot;

    @BeforeAll
    static void loadFixture() throws IOException {
        manifest = readFixture(MANIFEST);
        ObjectNode aggregate = manifest.deepCopy();
        ArrayNode stateMachines = OBJECT_MAPPER.createArrayNode();
        ArrayNode groups = OBJECT_MAPPER.createArrayNode();

        for (JsonNode descriptor : manifest.path("domainFixtures")) {
            String file = requiredText(descriptor, "file");
            JsonNode domain = readFixture(file);
            assertFalse(
                    domainFixtures.containsKey(file),
                    "duplicate domain fixture: " + file);
            domainFixtures.put(file, domain);
            domain.path("stateMachines").forEach(stateMachines::add);
            domain.path("groups").forEach(groups::add);
        }

        crossDomainEvents = readFixture(
                requiredText(manifest, "crossDomainEventsFile"));
        auditClosures = readFixture(
                requiredText(manifest, "auditClosuresFile"));
        aggregate.set("stateMachines", stateMachines);
        aggregate.set("groups", groups);
        aggregate.set(
                "events",
                crossDomainEvents.path("events").deepCopy());
        aggregate.set(
                "auditClosures",
                auditClosures.path("auditClosures").deepCopy());
        root = aggregate;
        repositoryRoot = findRepositoryRoot();
    }

    @Test
    void manifestListsOnlyTheFixedDomainFixtures() throws Exception {
        Set<String> expectedFiles = setOf(
                "manifest.json",
                "auth.json",
                "user-entitlement.json",
                "tenant-saas.json",
                "marketplace.json",
                "support-announcement.json",
                "ai.json",
                "device.json",
                "monitor-governance.json",
                "cross-domain-events.json",
                "audit-closures.json");
        Set<String> expectedLoadedFiles = new HashSet<>(expectedFiles);
        expectedLoadedFiles.remove(MANIFEST);

        assertEquals(expectedFiles, fixtureDirectoryFileNames());
        assertEquals(
                expectedLoadedFiles,
                textSet(manifest.path("fixtureFiles")));
        assertEquals(8, manifest.path("domainFixtures").size());
        assertEquals(8, domainFixtures.size());

        Map<String, Set<String>> expectedGroups = expectedDomainGroups();
        Map<String, Set<String>> expectedStates = expectedDomainStates();
        Map<String, Set<String>> expectedPackages = expectedDomainPackages();
        Map<String, String> expectedDomainIds = expectedDomainIds();
        Set<String> descriptorFiles = new HashSet<>();

        for (JsonNode descriptor : manifest.path("domainFixtures")) {
            String file = requiredText(descriptor, "file");
            String domainId = requiredText(descriptor, "domainId");
            assertTrue(descriptorFiles.add(file), "duplicate descriptor " + file);
            assertTrue(domainFixtures.containsKey(file), "not loaded " + file);

            JsonNode domain = domainFixtures.get(file);
            assertEquals("core-use-cases-v1", requiredText(domain, "version"));
            assertEquals(expectedDomainIds.get(file), domainId, file);
            assertEquals(domainId, requiredText(domain, "domainId"));
            assertEquals(
                    setOf("version", "domainId", "workPackages",
                            "stateMachines", "groups"),
                    fieldNames(domain),
                    file);

            Set<String> groupIds = ids(domain.path("groups"), "groupId");
            Set<String> stateIds = ids(
                    domain.path("stateMachines"),
                    "stateMachineId");
            Set<String> packages = textSet(domain.path("workPackages"));
            assertEquals(expectedGroups.get(file), groupIds, file);
            assertEquals(expectedStates.get(file), stateIds, file);
            assertEquals(expectedPackages.get(file), packages, file);
            assertEquals(groupIds, textSet(descriptor.path("groupIds")), file);
            assertEquals(
                    stateIds,
                    textSet(descriptor.path("stateMachineIds")),
                    file);
            assertEquals(
                    packages,
                    textSet(descriptor.path("workPackages")),
                    file);

            for (JsonNode group : domain.path("groups")) {
                for (JsonNode packageId : group.path("workPackages")) {
                    assertTrue(
                            packages.contains(packageId.asText()),
                            file + " disallows " + packageId.asText());
                }
            }
            assertFalse(domain.has("operationDefaults"), file);
            assertFalse(domain.has("events"), file);
            assertFalse(domain.has("auditClosures"), file);
        }
        assertEquals(expectedGroups.keySet(), descriptorFiles);
    }

    @Test
    void frozenInputsGroupsAndOperationCountsAreExact() {
        assertEquals("core-use-cases-v1", root.path("version").asText());
        assertEquals("2.1-S0", root.path("stage").asText());
        assertEquals(
                "5d5222f23cb0b3f589c4d35e34785554def81479",
                root.path("sourceCommits").path("backend").asText());
        assertEquals(
                "0b68b097c0e1e184449a074204def67c4240986e",
                root.path("sourceCommits").path("frontend").asText());
        assertEquals(
                "4f42cc16de5274c807c9ad11a18ce537cc468e6a",
                root.path("sourceCommits").path("firmware").asText());

        Set<String> expected = new HashSet<>(Arrays.asList(
                "AUTH-01", "AUTH-02", "AUTH-03", "AUTH-04", "AUTH-05",
                "ACCOUNT-01", "ACCOUNT-02", "ACCOUNT-03",
                "TENANT-01", "TENANT-02", "TENANT-03",
                "SAAS-01", "SAAS-02", "QUOTA-01", "QUOTA-02",
                "ENT-01", "ENT-02", "ENT-03", "ENT-04", "ENT-05",
                "ENT-06",
                "MARKET-01", "MARKET-02", "MARKET-03", "MARKET-04",
                "MARKET-05", "MARKET-06",
                "ANN-01", "ANN-02", "ANN-03", "ANN-04", "ANN-05",
                "ANN-06",
                "AI-01", "AI-02", "AI-03",
                "SUPPORT-01", "SUPPORT-02", "SUPPORT-03", "SUPPORT-04",
                "SUPPORT-05", "SUPPORT-06",
                "DEVICE-01", "DEVICE-02", "DEVICE-03", "DEVICE-04",
                "DEVICE-05",
                "SESSION-01", "SESSION-02", "TELEMETRY-01",
                "RULE-01", "RULE-02", "ALERT-01", "AUDIT-01",
                "LOCATION-01", "GEOFENCE-01", "ANALYTICS-01", "GOV-01"));

        Set<String> actual = new HashSet<>();
        int operations = 0;
        int commands = 0;
        int queries = 0;
        for (JsonNode group : root.path("groups")) {
            String groupId = requiredText(group, "groupId");
            assertTrue(actual.add(groupId), "duplicate groupId: " + groupId);
            for (JsonNode operation : group.path("operations")) {
                operations++;
                if ("COMMAND".equals(operation.path("kind").asText())) {
                    commands++;
                } else if ("QUERY".equals(operation.path("kind").asText())) {
                    queries++;
                }
            }
        }

        assertEquals(58, root.path("groupCount").asInt());
        assertEquals(58, root.path("groups").size());
        assertEquals(expected, actual);
        assertEquals(183, root.path("operationCount").asInt());
        assertEquals(135, root.path("commandCount").asInt());
        assertEquals(48, root.path("queryCount").asInt());
        assertEquals(42, root.path("stateMachineCount").asInt());
        assertEquals(183, operations);
        assertEquals(135, commands);
        assertEquals(48, queries);
    }

    @Test
    void everyOperationIsPhysicalSelfDescribingAndUniquelyOwned() {
        assertFalse(
                root.has("operationDefaults"),
                "operationDefaults must not satisfy operation contracts");
        assertFalse(
                root.path("operationContract").path("inheritanceAllowed")
                        .asBoolean(true),
                "operation inheritance must be disabled");

        Pattern operationPattern =
                Pattern.compile(requiredText(root, "operationIdPattern"));
        Set<String> operationIds = new HashSet<>();
        Set<String> knownActors =
                textSet(root.path("actorContexts").path("actors"));
        Set<String> knownContexts =
                textSet(root.path("actorContexts").path("contexts"));
        Map<String, JsonNode> stateMachines =
                nodesBy(root.path("stateMachines"), "stateMachineId");
        Set<String> statuses =
                textSet(root.path("implementationStatuses"));
        Set<String> targetLayers = textSet(root.path("targetLayers"));

        for (JsonNode group : root.path("groups")) {
            String groupId = requiredText(group, "groupId");
            assertFalse(requiredText(group, "title").isEmpty());
            assertFalse(group.path("workPackages").isEmpty(), groupId);
            assertFalse(group.path("operations").isEmpty(), groupId);
            for (String inherited : Arrays.asList(
                    "owners",
                    "actors",
                    "contexts",
                    "stateMachines",
                    "input",
                    "result",
                    "errorKeys",
                    "implementationStatus",
                    "conflicts",
                    "evidence",
                    "targetLayer")) {
                assertFalse(
                        group.has(inherited),
                        groupId + " must not provide inherited " + inherited);
            }

            for (JsonNode operation : group.path("operations")) {
                assertOperationContract(
                        operation,
                        groupId,
                        operationPattern,
                        operationIds,
                        knownActors,
                        knownContexts,
                        stateMachines,
                        statuses,
                        targetLayers);
            }
        }
        assertEquals(183, operationIds.size());
    }

    @Test
    void missingOperationFieldCannotFallBackToGroupOrDefaults() {
        JsonNode first = root.path("groups").get(0)
                .path("operations").get(0);
        ObjectNode missingOwner = first.deepCopy();
        missingOwner.remove("owner");

        assertThrows(
                AssertionError.class,
                () -> assertRequiredOwnField(missingOwner, "owner"));
        assertFalse(root.has("operationDefaults"));
        assertFalse(root.path("groups").get(0).has("owner"));
        assertFalse(root.path("groups").get(0).has("owners"));
    }

    @Test
    void objectStateCatalogIsExactTypedAndSourceBacked() {
        Set<String> expected = new HashSet<>(Arrays.asList(
                "AUTH_REFRESH_SESSION",
                "AUTH_REFRESH_TOKEN",
                "USER_ACCOUNT",
                "USER_OPERATION_REQUEST",
                "ENTITLEMENT_ORDER",
                "ENTITLEMENT_PAYMENT",
                "ENTITLEMENT_REFUND",
                "ENTITLEMENT_PURCHASE",
                "ENTITLEMENT_LEASE_RECEIPT",
                "TENANT_ACCOUNT",
                "TENANT_MEMBER",
                "PLATFORM_STAFF_STATUS",
                "SAAS_PLAN",
                "SAAS_PLAN_VERSION",
                "TENANT_SUBSCRIPTION",
                "TENANT_PLAN_ASSIGNMENT",
                "TENANT_QUOTA_RESERVATION",
                "MARKETPLACE_PRODUCT",
                "MARKETPLACE_SKU",
                "MARKETPLACE_PAYMENT",
                "MARKETPLACE_ORDER",
                "MARKETPLACE_TARGET_FULFILLMENT",
                "MARKETPLACE_TRACK_ONLY_FULFILLMENT",
                "ANNOUNCEMENT",
                "ANNOUNCEMENT_CONTENT_VERSION",
                "COMMENT",
                "CAPABILITY_RESTRICTION",
                "ANNOUNCEMENT_REVIEW_OUTBOX",
                "AI_REVIEW_TASK",
                "SUPPORT_SUBMISSION",
                "SUPPORT_TICKET",
                "SUPPORT_REVIEW_OUTBOX",
                "DEVICE_NODE",
                "DEVICE_COMMAND",
                "DEVICE_WIFI_CONFIG",
                "PORTAL_SESSION",
                "ACCESS_RULE",
                "ALERT_EVENT",
                "LOCATION_AUTHORIZATION",
                "GEOFENCE",
                "GEOFENCE_PRESENCE",
                "GEOFENCE_EVENT"));
        Set<String> forbidden = new HashSet<>(Arrays.asList(
                "AUTH_REFRESH",
                "ACCOUNT_ENTITLEMENT",
                "TENANT_MEMBER_STAFF",
                "SAAS_QUOTA",
                "MARKETPLACE",
                "ANNOUNCEMENT_COMMENT",
                "AI_REVIEW",
                "SUPPORT",
                "DEVICE",
                "MONITOR"));

        Set<String> actual = new HashSet<>();
        for (JsonNode machine : root.path("stateMachines")) {
            String id = requiredText(machine, "stateMachineId");
            assertTrue(actual.add(id), "duplicate state machine: " + id);
            assertFalse(requiredText(machine, "businessObject").isEmpty());
            assertFalse(requiredText(machine, "owner").isEmpty());
            assertFalse(requiredText(machine, "field").isEmpty());
            String machineType = requiredText(machine, "machineType");
            assertTrue(
                    "INTEGER".equals(machineType)
                            || "STRING".equals(machineType),
                    id + " type=" + machineType);
            assertFalse(machine.path("source").isEmpty(), id);
            for (JsonNode source : machine.path("source")) {
                Path sourcePath = repositoryRoot.resolve(source.asText());
                assertTrue(
                        Files.isRegularFile(sourcePath),
                        id + " missing source: " + source.asText());
            }
            assertFalse(machine.path("machineValues").isEmpty(), id);
            Set<String> semanticNames = new HashSet<>();
            Set<String> machineValues = new HashSet<>();
            for (JsonNode value : machine.path("machineValues")) {
                JsonNode machineValue = value.path("machineValue");
                if ("INTEGER".equals(machineType)) {
                    assertTrue(
                            machineValue.isIntegralNumber(),
                            id + " machineValue must be numeric: " + value);
                } else {
                    assertTrue(
                            machineValue.isTextual(),
                            id + " machineValue must be text: " + value);
                }
                assertTrue(
                        machineValues.add(machineValue.toString()),
                        id + " duplicate machineValue: " + machineValue);
                assertTrue(
                        semanticNames.add(requiredText(value, "semanticName")),
                        id + " duplicate semanticName: " + value);
                assertTrue(value.has("terminal"), id + " missing terminal");
            }

            JsonNode lifecycle = machine.path("lifecycle");
            String lifecycleStatus = requiredText(lifecycle, "status");
            assertTrue(
                    Arrays.asList("FROZEN", "PARTIAL", "GAP")
                            .contains(lifecycleStatus),
                    id + " lifecycle=" + lifecycleStatus);
            assertTrue(lifecycle.has("terminalStates"), id);
            assertTrue(lifecycle.has("legalTransitions"), id);
            if ("GAP".equals(lifecycleStatus)
                    || "PARTIAL".equals(lifecycleStatus)) {
                JsonNode gap = lifecycle.path("gap");
                assertTrue(gap.isObject(), id + " missing lifecycle gap");
                assertTrue(
                        requiredText(gap, "targetLayer")
                                .matches("^2\\.[2-5]$"),
                        id);
                assertFalse(requiredText(gap, "currentTruth").isEmpty());
                assertFalse(requiredText(gap, "evidence").isEmpty());
            } else {
                assertFalse(
                        lifecycle.path("legalTransitions").isEmpty(),
                        id + " frozen lifecycle needs transitions");
            }
        }
        assertEquals(expected, actual);
        for (String oldId : forbidden) {
            assertFalse(actual.contains(oldId), "forbidden aggregate: " + oldId);
        }
    }

    @Test
    void frozenIntegerMappingsAndCommandTypeAreExact() {
        assertIntegerMapping(
                "USER_ACCOUNT",
                mapOf(0, "DISABLED", 1, "ENABLED"));
        assertIntegerMapping(
                "USER_OPERATION_REQUEST",
                mapOf(0, "PENDING", 1, "APPROVED", 2, "REJECTED"));
        assertIntegerMapping(
                "DEVICE_NODE",
                mapOf(0, "OFFLINE", 1, "ONLINE"));
        assertIntegerMapping(
                "DEVICE_COMMAND",
                mapOf(
                        0, "PENDING",
                        1, "PUBLISHED",
                        2, "SUCCEEDED",
                        3, "EXECUTION_FAILED",
                        4, "PUBLISH_FAILED",
                        5, "TIMED_OUT"));
        assertIntegerMapping(
                "DEVICE_WIFI_CONFIG",
                mapOf(
                        0, "DISPATCHING",
                        1, "STAGED",
                        2, "ACTIVE",
                        3, "FAILED",
                        4, "UNKNOWN",
                        5, "SUPERSEDED"));
        assertIntegerMapping(
                "PORTAL_SESSION",
                mapOf(
                        0, "CLOSED",
                        1, "ACTIVE",
                        2, "PENDING",
                        3, "WAITING_REPLACEMENT"));
        assertIntegerMapping(
                "ALERT_EVENT",
                mapOf(0, "UNHANDLED", 1, "HANDLED"));

        Set<String> commandTypes =
                textSet(root.path("deviceCommandTypes"));
        assertTrue(commandTypes.contains("STAGE_WIFI_CONFIG"));
        assertFalse(commandTypes.contains("WIFI_CONFIG"));
    }

    @Test
    void domainAliasesAreScopedAndKnownGapsRemainExplicit() {
        Set<String> marketplaceBanned = new HashSet<>(Arrays.asList(
                "PENDING_PAYMENT",
                "COMPLETED",
                "ATTENTION_REQUIRED",
                "RETRY_WAIT",
                "MANUAL_REQUIRED"));
        for (String id : Arrays.asList(
                "MARKETPLACE_PRODUCT",
                "MARKETPLACE_SKU",
                "MARKETPLACE_PAYMENT",
                "MARKETPLACE_ORDER",
                "MARKETPLACE_TARGET_FULFILLMENT",
                "MARKETPLACE_TRACK_ONLY_FULFILLMENT")) {
            Set<String> values = semanticValues(id);
            for (String old : marketplaceBanned) {
                assertFalse(values.contains(old), id + " old alias " + old);
            }
        }
        assertTrue(
                semanticValues("MARKETPLACE_TARGET_FULFILLMENT")
                        .contains("PROCESSING"));
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "VISIBLE", "USER_DELETED", "ADMIN_HIDDEN")),
                semanticValues("COMMENT"));
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "QUEUED",
                        "RUNNING",
                        "SUCCEEDED",
                        "FAILED",
                        "MANUAL_REQUIRED",
                        "STALE")),
                semanticValues("AI_REVIEW_TASK"));

        JsonNode assignment = stateMachine("TENANT_PLAN_ASSIGNMENT");
        assertEquals(
                new HashSet<>(Arrays.asList("PENDING")),
                semanticValues("TENANT_PLAN_ASSIGNMENT"));
        assertEquals(
                "GAP",
                assignment.path("lifecycle").path("status").asText());

        JsonNode authorityGap = root.path("vocabularyGaps").get(0);
        assertEquals(
                "PLATFORM_STAFF_AUTHORITY",
                authorityGap.path("vocabularyId").asText());
        assertEquals("GAP", authorityGap.path("status").asText());
        assertEquals("2.2", authorityGap.path("targetLayer").asText());
        assertTrue(
                authorityGap.path("currentTruth").asText()
                        .contains("no production constant"));
    }

    @Test
    void supportAlertAndGovFactsAreNotOverstated() {
        JsonNode accepted =
                machineValue("SUPPORT_SUBMISSION", "ACCEPTED");
        assertTrue(accepted.path("compatibilityOnly").asBoolean());
        assertFalse(accepted.path("terminal").asBoolean());
        assertTrue(
                textSet(stateMachine("SUPPORT_TICKET")
                        .path("lifecycle")
                        .path("legalTransitions"))
                        .contains("NONE->OPEN"));

        JsonNode supportAccept =
                operation("SUPPORT-02.COMMAND.APPLY_REVIEW_RESULT");
        Set<String> supportTransitions =
                textSet(supportAccept.path("legalStateTransitions"));
        assertTrue(supportTransitions.contains(
                "REVIEW_PENDING|MANUAL_REVIEW->ACCEPTED->CONVERTED_TO_TICKET"));
        assertTrue(supportTransitions.contains("NONE->OPEN"));

        JsonNode alertHandle = operation("ALERT-01.COMMAND.HANDLE");
        assertEquals(
                "PARTIAL",
                alertHandle.path("implementationStatus").asText());
        assertEquals("2.4", alertHandle.path("targetLayer").asText());
        assertTrue(
                textSet(alertHandle.path("legalStateTransitions"))
                        .contains("0(UNHANDLED)->1(HANDLED)"));

        for (JsonNode operation : root.path("groups").get(57)
                .path("operations")) {
            String action = action(operation.path("operationId").asText());
            if (Arrays.asList(
                    "LIST_ACCOUNTS",
                    "CHANGE_ACCOUNT_STATUS",
                    "REVIEW_ACCOUNT_DELETION").contains(action)) {
                assertEquals("USER", operation.path("owner").asText());
            } else {
                assertEquals("TENANT", operation.path("owner").asText());
            }
        }
    }

    @Test
    void conflictsGapsEventsAndAuditClosuresAreStructured() {
        assertEquals(
                setOf("version", "compatibilityConstraints", "events"),
                fieldNames(crossDomainEvents));
        assertNonEmptyTextArray(
                crossDomainEvents,
                "compatibilityConstraints");
        assertEquals(
                setOf("version", "auditClosures"),
                fieldNames(auditClosures));

        for (JsonNode conflict : root.path("conflicts")) {
            for (String field : Arrays.asList(
                    "id",
                    "source",
                    "currentTruth",
                    "resolution",
                    "targetLayer",
                    "evidence")) {
                assertRequiredOwnField(conflict, field);
            }
            assertTrue(Files.isRegularFile(repositoryRoot.resolve(
                    requiredText(conflict, "evidence"))));
        }
        for (JsonNode gap : root.path("vocabularyGaps")) {
            assertEquals("GAP", requiredText(gap, "status"));
            assertTrue(
                    requiredText(gap, "targetLayer").matches("^2\\.[2-5]$"));
            assertFalse(requiredText(gap, "evidence").isEmpty());
            for (JsonNode source : gap.path("source")) {
                assertTrue(Files.isRegularFile(
                        repositoryRoot.resolve(source.asText())));
            }
        }

        int high = 0;
        int medium = 0;
        for (JsonNode closure : root.path("auditClosures")) {
            assertFalse(requiredText(closure, "findingId").isEmpty());
            assertFalse(requiredText(closure, "before").isEmpty());
            assertFalse(requiredText(closure, "after").isEmpty());
            assertEquals(
                    "CLOSED_IN_S0_CANDIDATE",
                    requiredText(closure, "status"));
            if ("HIGH".equals(requiredText(closure, "severity"))) {
                high++;
            } else {
                medium++;
            }
        }
        assertEquals(11, high);
        assertEquals(4, medium);

        Set<String> eventIds = new HashSet<>();
        for (JsonNode event : root.path("events")) {
            assertTrue(eventIds.add(requiredText(event, "id")));
            assertFalse(requiredText(event, "sourceOwner").isEmpty());
            assertFalse(event.path("targetOwners").isEmpty());
            assertFalse(event.path("referenceFields").isEmpty());
        }
        assertTrue(eventIds.contains("ENTITLEMENT_FULFILLMENT_COMPLETED"));
        assertTrue(eventIds.contains("SAAS_FULFILLMENT_COMPLETED"));
        assertFalse(eventIds.contains("MARKETPLACE_FULFILLMENT_COMPLETED"));

        for (JsonNode operation : allOperations()) {
            assertFalse(
                    operation.has("auditClosures"),
                    operation.path("operationId").asText());
            for (JsonNode conflict : operation.path("conflicts")) {
                assertFalse(requiredText(conflict, "source").isEmpty());
                assertFalse(requiredText(conflict, "currentTruth").isEmpty());
                assertFalse(requiredText(conflict, "resolution").isEmpty());
                assertTrue(
                        requiredText(conflict, "targetLayer")
                                .matches("^2\\.[2-5]$"));
                assertTrue(
                        Files.isRegularFile(repositoryRoot.resolve(
                                requiredText(conflict, "evidence"))));
            }
            for (JsonNode evidence : operation.path("evidence")) {
                assertFalse(
                        evidence.asText().contains("audit-closures.json"),
                        operation.path("operationId").asText());
            }
        }
        for (JsonNode machine : root.path("stateMachines")) {
            for (JsonNode source : machine.path("source")) {
                assertFalse(
                        source.asText().contains("audit-closures.json"),
                        machine.path("stateMachineId").asText());
            }
        }
    }

    @Test
    void workPackageOwnershipAndCoordinatorWriteSetRemainExact() {
        Set<String> expectedPackages = new HashSet<>(Arrays.asList(
                "P21-AUTH",
                "P21-USER",
                "P21-TENANT",
                "P21-MARKET",
                "P21-SUPPORT",
                "P21-AI",
                "P21-DEVICE",
                "P21-MONITOR"));
        Map<String, Set<String>> groupsByPackage = new HashMap<>();
        List<OwnedPath> ownedPaths = new ArrayList<>();

        for (JsonNode workPackage : root.path("workPackages")) {
            String packageId = requiredText(workPackage, "id");
            assertTrue(
                    expectedPackages.remove(packageId),
                    "unexpected or duplicate package: " + packageId);
            assertEquals(1, workPackage.path("ownedPaths").size(), packageId);
            assertFalse(workPackage.path("readOnlyPaths").isEmpty(), packageId);
            groupsByPackage.put(
                    packageId,
                    textSet(workPackage.path("inputGroups")));
            for (JsonNode ownedPath : workPackage.path("ownedPaths")) {
                ownedPaths.add(new OwnedPath(
                        packageId,
                        ownershipPrefix(ownedPath.asText())));
            }
        }
        assertTrue(expectedPackages.isEmpty(), expectedPackages.toString());

        for (int left = 0; left < ownedPaths.size(); left++) {
            for (int right = left + 1; right < ownedPaths.size(); right++) {
                OwnedPath first = ownedPaths.get(left);
                OwnedPath second = ownedPaths.get(right);
                assertFalse(
                        overlaps(first.pathPrefix, second.pathPrefix),
                        first.ownerId + ":" + first.pathPrefix
                                + " overlaps "
                                + second.ownerId + ":" + second.pathPrefix);
            }
        }

        for (JsonNode group : root.path("groups")) {
            String groupId = group.path("groupId").asText();
            for (JsonNode packageId : group.path("workPackages")) {
                assertTrue(
                        groupsByPackage.get(packageId.asText())
                                .contains(groupId),
                        packageId.asText() + " misses " + groupId);
            }
        }

        Set<String> coordinatorPaths =
                textSet(root.path("coordinatorOwnedPaths"));
        Set<String> expectedCoordinatorPaths = setOf(
                "docs/core-use-case-index.md",
                "docs/backend-api-index.md",
                "wifi-test-kit/src/test/java/com/plagod/testkit/"
                        + "CoreUseCaseIndexContractTest.java",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/manifest.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/auth.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/user-entitlement.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/tenant-saas.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/marketplace.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/support-announcement.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/ai.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/device.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/monitor-governance.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/cross-domain-events.json",
                "wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1/audit-closures.json");
        assertEquals(expectedCoordinatorPaths, coordinatorPaths);
    }

    @Test
    void markdownIndexAdvertisesTheSamePhysicalContract() throws IOException {
        String markdown = new String(
                Files.readAllBytes(
                        repositoryRoot.resolve("docs/core-use-case-index.md")),
                "UTF-8");
        assertTrue(markdown.contains("CORE_STATE_MACHINE_COUNT: 42"));
        assertTrue(markdown.contains("CORE_OPERATION_COUNT: 183"));
        assertTrue(markdown.contains("operation 自描述"));
        assertTrue(markdown.contains("不得依赖全局默认或组级继承"));
        assertTrue(markdown.contains("中央 manifest + 分域 fixture"));
        assertTrue(markdown.contains("core-use-cases-v1/manifest.json"));
        assertFalse(markdown.contains(
                "机器事实源："
                        + "`wifi-test-kit/src/test/resources/fixtures/"
                        + "core-use-cases-v1.json`"));
        for (JsonNode machine : root.path("stateMachines")) {
            assertTrue(
                    markdown.contains(
                            "`" + machine.path("stateMachineId").asText()
                                    + "`"),
                    "markdown misses "
                            + machine.path("stateMachineId").asText());
        }
    }

    @Test
    void markdownCrossDomainEventTableMatchesFixture() throws IOException {
        String markdown = new String(
                Files.readAllBytes(
                        repositoryRoot.resolve("docs/core-use-case-index.md")),
                "UTF-8");
        Set<String> fixtureEventIds = new HashSet<>();
        for (JsonNode event : crossDomainEvents.path("events")) {
            String eventId = requiredText(event, "id");
            assertTrue(fixtureEventIds.add(eventId), "duplicate " + eventId);
            assertTrue(
                    markdown.contains("| `" + eventId + "` |"),
                    "markdown misses event row " + eventId);
        }

        Set<String> markdownEventIds = new HashSet<>();
        boolean inEventSection = false;
        Pattern eventRow =
                Pattern.compile("^\\| `([A-Z][A-Z0-9_]*)` \\|");
        for (String line : markdown.split("\\r?\\n")) {
            if ("## 5. 跨域事件骨架".equals(line)) {
                inEventSection = true;
            } else if (inEventSection && line.startsWith("## ")) {
                break;
            } else if (inEventSection) {
                java.util.regex.Matcher matcher = eventRow.matcher(line);
                if (matcher.find()) {
                    assertTrue(
                            markdownEventIds.add(matcher.group(1)),
                            "duplicate Markdown event " + matcher.group(1));
                }
            }
        }

        assertEquals(fixtureEventIds, markdownEventIds);
        assertTrue(markdownEventIds.contains(
                "ENTITLEMENT_FULFILLMENT_COMPLETED"));
        assertTrue(markdownEventIds.contains(
                "SAAS_FULFILLMENT_COMPLETED"));
        assertFalse(markdown.contains(
                "MARKETPLACE_FULFILLMENT_COMPLETED"));
    }

    private static JsonNode readFixture(String file) throws IOException {
        String resource = FIXTURE_DIRECTORY + file;
        try (InputStream input =
                     CoreUseCaseIndexContractTest.class
                             .getResourceAsStream(resource)) {
            assertNotNull(input, "missing fixture: " + resource);
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static Set<String> fixtureDirectoryFileNames()
            throws IOException, URISyntaxException {
        java.net.URL resource =
                CoreUseCaseIndexContractTest.class
                        .getResource(FIXTURE_DIRECTORY);
        assertNotNull(resource, "missing fixture directory");
        assertEquals("file", resource.getProtocol());
        Set<String> files = new HashSet<>();
        try (Stream<Path> paths = Files.list(Paths.get(resource.toURI()))) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .forEach(name -> assertTrue(
                            files.add(name),
                            "duplicate fixture file: " + name));
        }
        return files;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> ids(JsonNode values, String field) {
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) {
            String id = requiredText(value, field);
            assertTrue(result.add(id), "duplicate " + field + ": " + id);
        }
        return result;
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static Map<String, String> expectedDomainIds() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("auth.json", "AUTH");
        result.put("user-entitlement.json", "USER_ENTITLEMENT");
        result.put("tenant-saas.json", "TENANT_SAAS");
        result.put("marketplace.json", "MARKETPLACE");
        result.put("support-announcement.json", "SUPPORT_ANNOUNCEMENT");
        result.put("ai.json", "AI");
        result.put("device.json", "DEVICE");
        result.put("monitor-governance.json", "MONITOR_GOVERNANCE");
        return result;
    }

    private static Map<String, Set<String>> expectedDomainGroups() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("auth.json", setOf(
                "AUTH-01", "AUTH-02", "AUTH-03", "AUTH-04", "AUTH-05"));
        result.put("user-entitlement.json", setOf(
                "ACCOUNT-01", "ACCOUNT-02", "ACCOUNT-03",
                "ENT-01", "ENT-02", "ENT-03", "ENT-04", "ENT-05",
                "ENT-06"));
        result.put("tenant-saas.json", setOf(
                "TENANT-01", "TENANT-02", "TENANT-03",
                "SAAS-01", "SAAS-02", "QUOTA-01", "QUOTA-02"));
        result.put("marketplace.json", setOf(
                "MARKET-01", "MARKET-02", "MARKET-03", "MARKET-04",
                "MARKET-05", "MARKET-06"));
        result.put("support-announcement.json", setOf(
                "ANN-01", "ANN-02", "ANN-03", "ANN-04", "ANN-05",
                "ANN-06",
                "SUPPORT-01", "SUPPORT-02", "SUPPORT-03", "SUPPORT-04",
                "SUPPORT-05", "SUPPORT-06"));
        result.put("ai.json", setOf("AI-01", "AI-02", "AI-03"));
        result.put("device.json", setOf(
                "DEVICE-01", "DEVICE-02", "DEVICE-03", "DEVICE-04",
                "DEVICE-05", "SESSION-01", "SESSION-02", "TELEMETRY-01"));
        result.put("monitor-governance.json", setOf(
                "RULE-01", "RULE-02", "ALERT-01", "AUDIT-01",
                "LOCATION-01", "GEOFENCE-01", "ANALYTICS-01", "GOV-01"));
        return result;
    }

    private static Map<String, Set<String>> expectedDomainStates() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("auth.json", setOf(
                "AUTH_REFRESH_SESSION", "AUTH_REFRESH_TOKEN"));
        result.put("user-entitlement.json", setOf(
                "USER_ACCOUNT", "USER_OPERATION_REQUEST",
                "ENTITLEMENT_ORDER", "ENTITLEMENT_PAYMENT",
                "ENTITLEMENT_REFUND", "ENTITLEMENT_PURCHASE",
                "ENTITLEMENT_LEASE_RECEIPT"));
        result.put("tenant-saas.json", setOf(
                "TENANT_ACCOUNT", "TENANT_MEMBER", "PLATFORM_STAFF_STATUS",
                "SAAS_PLAN", "SAAS_PLAN_VERSION", "TENANT_SUBSCRIPTION",
                "TENANT_PLAN_ASSIGNMENT", "TENANT_QUOTA_RESERVATION"));
        result.put("marketplace.json", setOf(
                "MARKETPLACE_PRODUCT", "MARKETPLACE_SKU",
                "MARKETPLACE_PAYMENT", "MARKETPLACE_ORDER",
                "MARKETPLACE_TARGET_FULFILLMENT",
                "MARKETPLACE_TRACK_ONLY_FULFILLMENT"));
        result.put("support-announcement.json", setOf(
                "ANNOUNCEMENT", "ANNOUNCEMENT_CONTENT_VERSION", "COMMENT",
                "CAPABILITY_RESTRICTION", "ANNOUNCEMENT_REVIEW_OUTBOX",
                "SUPPORT_SUBMISSION", "SUPPORT_TICKET",
                "SUPPORT_REVIEW_OUTBOX"));
        result.put("ai.json", setOf("AI_REVIEW_TASK"));
        result.put("device.json", setOf(
                "DEVICE_NODE", "DEVICE_COMMAND", "DEVICE_WIFI_CONFIG",
                "PORTAL_SESSION"));
        result.put("monitor-governance.json", setOf(
                "ACCESS_RULE", "ALERT_EVENT", "LOCATION_AUTHORIZATION",
                "GEOFENCE", "GEOFENCE_PRESENCE", "GEOFENCE_EVENT"));
        return result;
    }

    private static Map<String, Set<String>> expectedDomainPackages() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("auth.json", setOf("P21-AUTH"));
        result.put("user-entitlement.json", setOf("P21-USER"));
        result.put("tenant-saas.json", setOf("P21-TENANT"));
        result.put("marketplace.json", setOf("P21-MARKET"));
        result.put("support-announcement.json", setOf("P21-SUPPORT"));
        result.put("ai.json", setOf("P21-AI"));
        result.put("device.json", setOf("P21-DEVICE"));
        result.put(
                "monitor-governance.json",
                setOf("P21-MONITOR", "P21-USER", "P21-TENANT"));
        return result;
    }

    private static void assertOperationContract(
            JsonNode operation,
            String groupId,
            Pattern operationPattern,
            Set<String> operationIds,
            Set<String> knownActors,
            Set<String> knownContexts,
            Map<String, JsonNode> stateMachines,
            Set<String> statuses,
            Set<String> targetLayers) {
        for (String field : COMMON_OPERATION_FIELDS) {
            assertRequiredOwnField(operation, field);
        }

        String operationId = requiredText(operation, "operationId");
        String kind = requiredText(operation, "kind");
        assertTrue(operationPattern.matcher(operationId).matches(), operationId);
        assertTrue(operationId.startsWith(groupId + "."), operationId);
        assertTrue(operationId.contains("." + kind + "."), operationId);
        assertTrue(operationIds.add(operationId), "duplicate " + operationId);
        assertTrue(
                "COMMAND".equals(kind) || "QUERY".equals(kind),
                operationId);
        assertFalse(requiredText(operation, "owner").isEmpty());
        assertNonEmptyTextArray(operation, "actor");
        assertNonEmptyTextArray(operation, "context");
        assertArraySubset(operation, "actor", knownActors, operationId);
        assertArraySubset(operation, "context", knownContexts, operationId);
        assertNonEmptyTextArray(operation, "errorKeys");
        assertNonEmptyTextArray(operation, "evidence");

        String stateModel = requiredText(operation, "stateModel");
        assertTrue(operation.path("stateMachineRefs").isArray(), operationId);
        if ("STATELESS".equals(stateModel)) {
            assertTrue(
                    operation.path("stateMachineRefs").isEmpty(),
                    operationId);
        } else {
            assertEquals("STATEFUL", stateModel, operationId);
            assertFalse(
                    operation.path("stateMachineRefs").isEmpty(),
                    operationId);
            for (JsonNode ref : operation.path("stateMachineRefs")) {
                JsonNode machine = stateMachines.get(ref.asText());
                assertNotNull(machine, operationId + " unknown " + ref);
                assertEquals(
                        operation.path("owner").asText(),
                        machine.path("owner").asText(),
                        operationId + " cross-owner state " + ref.asText());
            }
        }

        String status = requiredText(operation, "implementationStatus");
        String targetLayer = requiredText(operation, "targetLayer");
        assertTrue(statuses.contains(status), operationId);
        assertTrue(targetLayers.contains(targetLayer), operationId);
        if ("REUSE".equals(status)) {
            assertEquals("NONE", targetLayer, operationId);
        } else {
            assertTrue(targetLayer.matches("^2\\.[2-5]$"), operationId);
        }

        boolean hasActualEvidence = false;
        boolean hasGapEvidence = false;
        for (JsonNode evidence : operation.path("evidence")) {
            String value = evidence.asText();
            if (value.startsWith("GAP:")) {
                hasGapEvidence = true;
            } else {
                hasActualEvidence = true;
                assertTrue(
                        Files.isRegularFile(repositoryRoot.resolve(value)),
                        operationId + " missing evidence " + value);
            }
        }
        if ("REUSE".equals(status)) {
            assertTrue(hasActualEvidence, operationId);
        } else {
            assertTrue(hasGapEvidence, operationId + " missing GAP evidence");
        }

        for (JsonNode conflict : operation.path("conflicts")) {
            for (String field : Arrays.asList(
                    "source",
                    "currentTruth",
                    "resolution",
                    "targetLayer",
                    "evidence")) {
                assertRequiredOwnField(conflict, field);
            }
        }

        if ("COMMAND".equals(kind)) {
            for (String field : COMMAND_FIELDS) {
                assertRequiredOwnField(operation, field);
            }
            assertNonEmptyTextArray(operation, "externalEffects");
            assertNonEmptyTextArray(operation, "legalStateTransitions");
            for (String field : QUERY_FIELDS) {
                assertFalse(operation.has(field), operationId + " has " + field);
            }
        } else {
            for (String field : QUERY_FIELDS) {
                assertRequiredOwnField(operation, field);
            }
            assertTrue(operation.path("readOnly").asBoolean(), operationId);
            assertTrue(operation.path("resultLimit").asInt() > 0, operationId);
            for (String field : COMMAND_FIELDS) {
                assertFalse(operation.has(field), operationId + " has " + field);
            }
        }
    }

    private static void assertRequiredOwnField(JsonNode node, String field) {
        assertTrue(node.has(field), "missing own field " + field + " in " + node);
        JsonNode value = node.get(field);
        assertFalse(value == null || value.isNull(), "null " + field);
        if (value.isTextual()) {
            assertFalse(value.asText().trim().isEmpty(), "blank " + field);
        }
    }

    private static void assertNonEmptyTextArray(
            JsonNode node,
            String field) {
        JsonNode values = node.path(field);
        assertTrue(values.isArray(), "not array " + field + " in " + node);
        assertFalse(values.isEmpty(), "empty " + field + " in " + node);
        for (JsonNode value : values) {
            assertTrue(value.isTextual(), field + " value=" + value);
            assertFalse(value.asText().trim().isEmpty(), field + " blank");
        }
    }

    private static void assertArraySubset(
            JsonNode node,
            String field,
            Set<String> allowed,
            String owner) {
        for (JsonNode value : node.path(field)) {
            assertTrue(
                    allowed.contains(value.asText()),
                    owner + " unknown " + field + ": " + value.asText());
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        assertFalse(value.isEmpty(), "missing " + field + " in " + node);
        return value;
    }

    private static Map<String, JsonNode> nodesBy(
            JsonNode values,
            String idField) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode value : values) {
            String id = requiredText(value, idField);
            assertFalse(result.containsKey(id), "duplicate " + idField + ": " + id);
            result.put(id, value);
        }
        return result;
    }

    private static Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) {
            assertTrue(
                    result.add(value.asText()),
                    "duplicate value: " + value.asText());
        }
        return result;
    }

    private static List<JsonNode> allOperations() {
        List<JsonNode> operations = new ArrayList<>();
        for (JsonNode group : root.path("groups")) {
            for (JsonNode operation : group.path("operations")) {
                operations.add(operation);
            }
        }
        return operations;
    }

    private static JsonNode operation(String operationId) {
        for (JsonNode operation : allOperations()) {
            if (operationId.equals(operation.path("operationId").asText())) {
                return operation;
            }
        }
        throw new AssertionError("missing operation " + operationId);
    }

    private static JsonNode stateMachine(String id) {
        for (JsonNode machine : root.path("stateMachines")) {
            if (id.equals(machine.path("stateMachineId").asText())) {
                return machine;
            }
        }
        throw new AssertionError("missing state machine " + id);
    }

    private static Set<String> semanticValues(String id) {
        Set<String> result = new HashSet<>();
        for (JsonNode value : stateMachine(id).path("machineValues")) {
            result.add(requiredText(value, "semanticName"));
        }
        return result;
    }

    private static JsonNode machineValue(String id, String semanticName) {
        for (JsonNode value : stateMachine(id).path("machineValues")) {
            if (semanticName.equals(value.path("semanticName").asText())) {
                return value;
            }
        }
        throw new AssertionError(id + " missing " + semanticName);
    }

    private static void assertIntegerMapping(
            String id,
            Map<Integer, String> expected) {
        JsonNode machine = stateMachine(id);
        assertEquals("INTEGER", machine.path("machineType").asText());
        Map<Integer, String> actual = new HashMap<>();
        for (JsonNode value : machine.path("machineValues")) {
            actual.put(
                    value.path("machineValue").asInt(),
                    value.path("semanticName").asText());
        }
        assertEquals(expected, actual, id);
    }

    private static Map<Integer, String> mapOf(Object... values) {
        Map<Integer, String> result = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((Integer) values[index], (String) values[index + 1]);
        }
        return result;
    }

    private static String action(String operationId) {
        return operationId.substring(operationId.lastIndexOf('.') + 1);
    }

    private static Path findRepositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("wifi-test-kit"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("cannot locate repository root");
    }

    private static String ownershipPrefix(String path) {
        String normalized = path.replace('\\', '/');
        int wildcard = normalized.indexOf('*');
        if (wildcard >= 0) {
            normalized = normalized.substring(0, wildcard);
        }
        while (normalized.endsWith("/")) {
            normalized =
                    normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean overlaps(String first, String second) {
        return first.equals(second)
                || first.startsWith(second + "/")
                || second.startsWith(first + "/");
    }

    private static final class OwnedPath {
        private final String ownerId;
        private final String pathPrefix;

        private OwnedPath(String ownerId, String pathPrefix) {
            this.ownerId = ownerId;
            this.pathPrefix = pathPrefix;
        }
    }
}
