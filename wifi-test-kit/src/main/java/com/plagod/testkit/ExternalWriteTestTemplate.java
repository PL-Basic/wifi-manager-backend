package com.plagod.testkit;

import java.util.Map;

public final class ExternalWriteTestTemplate {

    private ExternalWriteTestTemplate() {
    }

    public static TestRunContext start(
            Map<String, String> environment,
            String stage,
            String caseName) {

        TestEnvironmentGuard.requireDedicatedWriteAccess(environment);
        return new TestRunContext(TestRunId.create(stage, caseName));
    }
}
