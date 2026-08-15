package com.plagod.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiModerationResultParserTest {

    private AiModerationResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiModerationResultParser(new ObjectMapper(), 4096);
    }

    @Test
    void acceptsOnlyTheFrozenResultSchema() {
        AiModerationResult result = parser.parse(
                validJson("APPROVE", "0.92", ""),
                AiModerationScene.ANNOUNCEMENT_REVIEW);

        assertEquals(AiModerationDecision.APPROVE, result.getDecision());
        assertEquals(0.92d, result.getConfidence(), 0.000001d);
        assertEquals("POLICY_CLEAR", result.getReasonCode());
        assertEquals(1, result.getRiskLabels().size());
    }

    @Test
    void rejectsMalformedJsonUnknownDecisionAndUnknownField() {
        assertInvalid("{");
        assertInvalid(validJson("ALLOW", "0.92", ""));
        assertInvalid(validJson("APPROVE", "0.92", ",\"debug\":\"raw\""));
        assertThrows(
                AiProviderException.class,
                () -> parser.parse(
                        validJson("APPROVE", "0.92", ""),
                        null));
    }

    @Test
    void rejectsDuplicateFieldsAndTrailingDocuments() {
        assertInvalid(validJson(
                "APPROVE",
                "0.92",
                ",\"decision\":\"REJECT\""));
        assertInvalid(validJson("APPROVE", "0.92", "") + "{}");
    }

    @Test
    void rejectsOutOfRangeConfidenceAndDuplicateLabels() {
        assertInvalid(validJson("APPROVE", "1.01", ""));
        assertInvalid(validJson("APPROVE", "0.92", "")
                .replace("[\"SAFE\"]", "[\"SAFE\",\"SAFE\"]"));
    }

    @Test
    void rejectsSupportOnlyFieldsForAnnouncementScene() {
        assertInvalid(validJson(
                "MANUAL",
                "0.40",
                ",\"category\":\"CONNECTIVITY\",\"priority\":\"HIGH\""));

        AiModerationResult result = parser.parse(
                validJson(
                        "MANUAL",
                        "0.40",
                        ",\"category\":\"CONNECTIVITY\",\"priority\":\"HIGH\""),
                AiModerationScene.SUPPORT_SUBMISSION_REVIEW);
        assertEquals("CONNECTIVITY", result.getCategory());
    }

    @Test
    void rejectsOversizeResponsesBeforeSchemaParsing() {
        AiModerationResultParser smallParser =
                new AiModerationResultParser(new ObjectMapper(), 1024);
        String oversized = validJson(
                "APPROVE",
                "0.92",
                ",\"model\":\"" + repeat('x', 1200) + "\"");

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> smallParser.parse(
                        oversized,
                        AiModerationScene.ANNOUNCEMENT_REVIEW));
        assertEquals(AiProviderFailure.RESPONSE_TOO_LARGE, exception.getFailure());
    }

    private void assertInvalid(String raw) {
        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> parser.parse(raw, AiModerationScene.ANNOUNCEMENT_REVIEW));
        assertEquals(AiProviderFailure.RESPONSE_INVALID, exception.getFailure());
    }

    private String validJson(String decision, String confidence, String suffix) {
        return "{"
                + "\"schemaVersion\":\"ai-moderation-result-v1\","
                + "\"decision\":\"" + decision + "\","
                + "\"confidence\":" + confidence + ","
                + "\"reasonCode\":\"POLICY_CLEAR\","
                + "\"riskLabels\":[\"SAFE\"]"
                + suffix
                + "}";
    }

    private String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
