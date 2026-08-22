package com.plagod.transition;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionalStateTransitionTest {

    @Test
    void validatesCommandAndDomainEdges() {
        ConditionalStateTransition<String> transition =
                ConditionalStateTransition.of(
                        Arrays.asList("PENDING", "RETRY"),
                        "PROCESSING",
                        3,
                        "worker:claim:42");

        transition.requireLegalEdge((from, to) ->
                "PROCESSING".equals(to)
                        && ("PENDING".equals(from)
                        || "RETRY".equals(from)));

        assertEquals(2, transition.getFromStates().size());
        assertEquals("PROCESSING", transition.getToState());
        assertEquals(3, transition.getExpectedVersion());
        assertEquals("worker:claim:42", transition.getEventKey());
    }

    @Test
    void rejectsUndefinedEdgeAndInvalidEventKey() {
        ConditionalStateTransition<String> transition =
                ConditionalStateTransition.of(
                        Arrays.asList("PENDING"),
                        "SUCCEEDED",
                        0,
                        "event-1");

        assertThrows(
                IllegalArgumentException.class,
                () -> transition.requireLegalEdge(
                        (from, to) -> false));
        assertThrows(
                IllegalArgumentException.class,
                () -> ConditionalStateTransition.of(
                        Arrays.asList("PENDING"),
                        "SUCCEEDED",
                        0,
                        "contains secret"));
    }

    @Test
    void classifiesAppliedReplayAndConditionalFailures() {
        ConditionalStateTransition<String> transition =
                ConditionalStateTransition.of(
                        Arrays.asList("PENDING"),
                        "SUCCEEDED",
                        4,
                        "event-2");

        assertEquals(
                ConditionalStateTransition.Outcome.APPLIED,
                transition.classify(
                        1, false, true, "PENDING", 4));
        assertEquals(
                ConditionalStateTransition.Outcome.APPLIED,
                transition.classify(
                        1, true, true, "SUCCEEDED", 5));
        assertEquals(
                ConditionalStateTransition.Outcome.REPLAYED,
                transition.classify(
                        0, true, true, "SUCCEEDED", 5));
        assertEquals(
                ConditionalStateTransition.Outcome.RESOURCE_NOT_FOUND,
                transition.classify(
                        0, false, false, null, 0));
        assertEquals(
                ConditionalStateTransition.Outcome.ILLEGAL_STATE,
                transition.classify(
                        0, false, true, "FAILED", 4));
        assertEquals(
                ConditionalStateTransition.Outcome.VERSION_CONFLICT,
                transition.classify(
                        0, false, true, "PENDING", 5));
        assertThrows(
                IllegalArgumentException.class,
                () -> transition.classify(
                        -1, true, true, "SUCCEEDED", 5));
        assertThrows(
                IllegalArgumentException.class,
                () -> transition.classify(
                        2, false, true, "PENDING", 4));
    }
}
