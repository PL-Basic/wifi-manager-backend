package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalWriteTestTemplateTest {

    @Test
    void missingWriteAuthorizationFailsBeforeCreatingRun() {
        assertThrows(
                IllegalStateException.class,
                () -> ExternalWriteTestTemplate.start(
                        new HashMap<>(),
                        "stage-e",
                        "missing-gate"));
    }

    @Test
    void authorizedRunUsesPreciseCleanupAndZeroResidueProbe() {
        List<String> operations = new ArrayList<>();

        try (TestRunContext context = ExternalWriteTestTemplate.start(
                writeEnvironment(),
                "stage-e",
                "cleanup-template")) {
            assertTrue(context.getRunId().startsWith("wm-stage-e-cleanup-template-"));
            context.register(
                    "test-resource",
                    "tenant-test",
                    "resource-1",
                    null,
                    () -> operations.add("cleanup-resource-1"),
                    () -> false);
        }

        assertEquals(1, operations.size());
        assertEquals("cleanup-resource-1", operations.get(0));
    }

    private static Map<String, String> writeEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("WIFI_TEST_ALLOW_WRITES", "true");
        environment.put("WIFI_TEST_ACCOUNT_MARKER", "dedicated-test-account");
        return environment;
    }
}
