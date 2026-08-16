package com.plagod.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonContractAssertionsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixtureLoaderAndRecursiveAssertionAcceptStringLongIds() {
        JsonFixtureLoader loader = new JsonFixtureLoader(objectMapper);
        JsonNode fixture = loader.load("fixtures/string-long-ids.json", JsonNode.class);

        JsonContractAssertions.assertLongIdsAreStrings(fixture);

        assertEquals("9223372036854775807", fixture.path("data").path("userId").asText());
    }

    @Test
    void recursiveAssertionRejectsNumericLongIdBeforeFrontendParsing() throws Exception {
        JsonNode payload = objectMapper.readTree(
                "{\"data\":{\"tenantId\":9223372036854775807}}");

        assertThrows(
                AssertionError.class,
                () -> JsonContractAssertions.assertLongIdsAreStrings(payload));
    }
}
