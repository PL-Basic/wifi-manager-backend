package com.plagod.support;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StableSupportContractTest {

    @Test
    void pageBoundsNormalizesInputsAndCalculatesLongOffset() {
        PageBounds defaults = PageBounds.of(null, null);
        assertEquals(1, defaults.getCurrent());
        assertEquals(10, defaults.getSize());
        assertEquals(0L, defaults.getOffset());

        PageBounds negative = PageBounds.of(-7, -3);
        assertEquals(1, negative.getCurrent());
        assertEquals(10, negative.getSize());

        PageBounds bounded = PageBounds.of(Integer.MAX_VALUE, 101);
        assertEquals(Integer.MAX_VALUE, bounded.getCurrent());
        assertEquals(100, bounded.getSize());
        assertEquals(214_748_364_600L, bounded.getOffset());
    }

    @Test
    void stableUnitsExposeOnlyFixedUnitAndTimeZoneValues() {
        assertEquals(100, StableUnits.CENTS_PER_MAJOR_UNIT);
        assertEquals(60, StableUnits.SECONDS_PER_MINUTE);
        assertEquals(1_000, StableUnits.MILLISECONDS_PER_SECOND);
        assertEquals(1_024, StableUnits.BYTES_PER_KIBIBYTE);
        assertEquals("Asia/Shanghai", StableUnits.ASIA_SHANGHAI_ZONE_ID);
        assertEquals(
                ZoneId.of("Asia/Shanghai"),
                StableUnits.ASIA_SHANGHAI);
    }

    @Test
    void structuredRedactorFailsClosedWithoutRetainingUnsafeReferences() {
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("nestedSecret", "map-secret");
        List<String> nestedList = new ArrayList<>(
                Collections.singletonList("list-secret"));
        String[] array = {"array-secret"};
        ToStringCanary canary = new ToStringCanary();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requestId", "request-1");
        input.put("secret", "do-not-copy");
        input.put("attempt", 3);
        input.put("optional", null);
        input.put("nestedMap", nestedMap);
        input.put("nestedList", nestedList);
        input.put("array", array);
        input.put("custom", canary);
        input.put("ignored", "internal");
        Map<String, Object> original = new LinkedHashMap<>(input);

        Map<String, Object> output = StructuredRedactor.redact(
                input,
                new java.util.LinkedHashSet<>(Arrays.asList(
                        "requestId",
                        "secret",
                        "attempt",
                        "optional",
                        "nestedMap",
                        "nestedList",
                        "array",
                        "custom")),
                Collections.singleton("secret"));

        assertEquals(
                Arrays.asList(
                        "requestId",
                        "secret",
                        "attempt",
                        "optional",
                        "nestedMap",
                        "nestedList",
                        "array",
                        "custom"),
                Arrays.asList(output.keySet().toArray()));
        assertEquals("request-1", output.get("requestId"));
        assertEquals(3, output.get("attempt"));
        assertEquals(null, output.get("optional"));
        assertEquals(
                StructuredRedactor.REDACTED_VALUE,
                output.get("secret"));
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("nestedMap"));
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("nestedList"));
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("array"));
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("custom"));
        assertNotSame(nestedMap, output.get("nestedMap"));
        assertNotSame(nestedList, output.get("nestedList"));
        assertNotSame(array, output.get("array"));
        assertNotSame(canary, output.get("custom"));
        assertFalse(canary.toStringCalled);
        assertFalse(output.containsKey("ignored"));
        assertEquals(original, input);

        nestedMap.put("laterSecret", "mutated-map-secret");
        nestedList.add("mutated-list-secret");
        array[0] = "mutated-array-secret";
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("nestedMap"));
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("nestedList"));
        assertSame(
                StructuredRedactor.REDACTED_VALUE,
                output.get("array"));
        assertFalse(output.containsValue("mutated-map-secret"));
        assertFalse(output.containsValue("mutated-list-secret"));
        assertFalse(output.containsValue("mutated-array-secret"));
        assertFalse(canary.toStringCalled);
        assertThrows(
                UnsupportedOperationException.class,
                () -> output.put("extra", "value"));
    }

    @Test
    void configurationValidationReturnsValuesAndNeverLeaksThem() {
        String text = "  enabled  ";
        assertSame(
                text,
                SafeConfigurationValue.requireText(
                        "feature.mode",
                        text));

        String secret = "accepted-secret-value";
        assertSame(
                secret,
                SafeConfigurationValue.requireSecret(
                        "auth.secret",
                        secret,
                        16,
                        Collections.singleton("forbidden-secret-value")));

        assertDoesNotLeak(
                "auth.secret",
                "short-secret",
                assertThrows(
                        IllegalStateException.class,
                        () -> SafeConfigurationValue.requireSecret(
                                "auth.secret",
                                "short-secret",
                                32,
                                Collections.emptySet())));
        assertDoesNotLeak(
                "auth.secret",
                "forbidden-secret-value",
                assertThrows(
                        IllegalStateException.class,
                        () -> SafeConfigurationValue.requireSecret(
                                "auth.secret",
                                "forbidden-secret-value",
                                16,
                                Collections.singleton(
                                        "forbidden-secret-value"))));
        assertDoesNotLeak(
                "feature.mode",
                "   ",
                assertThrows(
                        IllegalStateException.class,
                        () -> SafeConfigurationValue.requireText(
                                "feature.mode",
                                "   ")));
    }

    private void assertDoesNotLeak(
            String propertyName,
            String secret,
            IllegalStateException failure) {
        assertEquals(true, failure.getMessage().contains(propertyName));
        assertFalse(failure.getMessage().contains(secret));
    }

    private static final class ToStringCanary {
        private boolean toStringCalled;

        @Override
        public String toString() {
            toStringCalled = true;
            return "custom-to-string-secret";
        }
    }
}
