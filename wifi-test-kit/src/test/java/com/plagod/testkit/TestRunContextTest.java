package com.plagod.testkit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestRunContextTest {

    @Test
    void cleansRegisteredResourcesInReverseOrderAndChecksResidue() {
        List<String> operations = new ArrayList<>();
        TestRunContext context = new TestRunContext("wm-stage-case-123456-abc123");
        context.register("parent", "1", "10", null,
                () -> operations.add("cleanup-parent"),
                () -> false);
        context.register("child", "1", "11", null,
                () -> operations.add("cleanup-child"),
                () -> false);

        context.cleanupAll();

        assertEquals(
                Arrays.asList("cleanup-child", "cleanup-parent"),
                operations);
        assertEquals(0, context.registeredResourceCount());
    }

    @Test
    void nonZeroResidueFailsWithoutBroadCleanupFallback() {
        TestRunContext context = new TestRunContext("wm-stage-case-123456-abc123");
        context.register("order", "1", "10", "order-1", () -> { }, () -> true);

        assertThrows(IllegalStateException.class, context::cleanupAll);
    }
}
