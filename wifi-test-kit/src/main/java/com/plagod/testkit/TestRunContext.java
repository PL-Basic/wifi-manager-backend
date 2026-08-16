package com.plagod.testkit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class TestRunContext implements AutoCloseable {

    @FunctionalInterface
    public interface CleanupAction {
        void cleanup() throws Exception;
    }

    @FunctionalInterface
    public interface ResidueProbe {
        boolean exists() throws Exception;
    }

    private final String runId;
    private final Deque<ResourceRegistration> resources = new ArrayDeque<>();

    public TestRunContext(String runId) {
        this.runId = requireText(runId, "runId");
    }

    public String getRunId() {
        return runId;
    }

    public void register(
            String type,
            String tenantId,
            String primaryKey,
            String businessKey,
            CleanupAction cleanup,
            ResidueProbe residueProbe) {

        String safeType = requireText(type, "type");
        if (!hasText(primaryKey) && !hasText(businessKey)) {
            throw new IllegalArgumentException("primaryKey or businessKey is required");
        }
        resources.push(new ResourceRegistration(
                safeType,
                tenantId,
                primaryKey,
                businessKey,
                Objects.requireNonNull(cleanup, "cleanup"),
                Objects.requireNonNull(residueProbe, "residueProbe")));
    }

    public int registeredResourceCount() {
        return resources.size();
    }

    public void cleanupAll() {
        List<String> failures = new ArrayList<>();
        while (!resources.isEmpty()) {
            ResourceRegistration resource = resources.pop();
            try {
                resource.cleanup.cleanup();
            } catch (Exception exception) {
                failures.add(resource.label() + ":cleanup");
            }

            try {
                if (resource.residueProbe.exists()) {
                    failures.add(resource.label() + ":residue");
                }
            } catch (Exception exception) {
                failures.add(resource.label() + ":residue-check");
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "test cleanup did not reach zero residue for " + String.join(",", failures));
        }
    }

    @Override
    public void close() {
        cleanupAll();
    }

    private static String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class ResourceRegistration {

        private final String type;
        private final String tenantId;
        private final String primaryKey;
        private final String businessKey;
        private final CleanupAction cleanup;
        private final ResidueProbe residueProbe;

        private ResourceRegistration(
                String type,
                String tenantId,
                String primaryKey,
                String businessKey,
                CleanupAction cleanup,
                ResidueProbe residueProbe) {
            this.type = type;
            this.tenantId = tenantId;
            this.primaryKey = primaryKey;
            this.businessKey = businessKey;
            this.cleanup = cleanup;
            this.residueProbe = residueProbe;
        }

        private String label() {
            return type
                    + "[tenant=" + safe(tenantId)
                    + ",primary=" + safe(primaryKey)
                    + ",business=" + safe(businessKey)
                    + "]";
        }

        private String safe(String value) {
            return hasText(value) ? value : "-";
        }
    }
}
