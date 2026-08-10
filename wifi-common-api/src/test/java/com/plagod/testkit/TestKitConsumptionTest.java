package com.plagod.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestKitConsumptionTest {

    @Test
    void commonApiConsumesTestKitOnlyFromItsTestClasspath() throws Exception {
        String runId = TestRunId.create("1-2-b", "common-api");
        JsonContractAssertions.assertLongIdsAreStrings(
                new ObjectMapper().readTree("{\"userId\":\"9223372036854775807\"}"));

        assertTrue(runId.startsWith("wm-1-2-b-common-api-"));
    }
}
