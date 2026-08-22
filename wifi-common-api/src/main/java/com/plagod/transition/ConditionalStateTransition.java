package com.plagod.transition;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * 条件状态转换的共享输入与失败分类模板。
 *
 * 领域服务仍负责定义合法边、执行持久化并在同一事务写 transition log。
 */
public final class ConditionalStateTransition<S> {

    private static final Pattern EVENT_KEY = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    private final Set<S> fromStates;
    private final S toState;
    private final long expectedVersion;
    private final String eventKey;

    private ConditionalStateTransition(
            Collection<S> fromStates,
            S toState,
            long expectedVersion,
            String eventKey) {
        if (fromStates == null || fromStates.isEmpty()) {
            throw new IllegalArgumentException("fromStates 不能为空");
        }
        LinkedHashSet<S> normalized = new LinkedHashSet<>();
        for (S state : fromStates) {
            if (state == null) {
                throw new IllegalArgumentException(
                        "fromStates 不能包含 null");
            }
            normalized.add(state);
        }
        if (toState == null) {
            throw new IllegalArgumentException("toState 不能为空");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion 不能小于 0");
        }
        if (eventKey == null
                || !EVENT_KEY.matcher(eventKey).matches()) {
            throw new IllegalArgumentException("eventKey 格式错误");
        }
        this.fromStates = Collections.unmodifiableSet(normalized);
        this.toState = toState;
        this.expectedVersion = expectedVersion;
        this.eventKey = eventKey;
    }

    public static <S> ConditionalStateTransition<S> of(
            Collection<S> fromStates,
            S toState,
            long expectedVersion,
            String eventKey) {
        return new ConditionalStateTransition<>(
                fromStates,
                toState,
                expectedVersion,
                eventKey);
    }

    public void requireLegalEdge(BiPredicate<S, S> legalEdge) {
        if (legalEdge == null) {
            throw new IllegalArgumentException("legalEdge 不能为空");
        }
        for (S fromState : fromStates) {
            if (!legalEdge.test(fromState, toState)) {
                throw new IllegalArgumentException(
                        "包含未定义的状态转换");
            }
        }
    }

    /**
     * 先确认条件更新结果；只有影响 0 行时才根据同事务内重新读取的事实分类。
     */
    public Outcome classify(
            int affectedRows,
            boolean duplicateEvent,
            boolean resourceExists,
            S currentState,
            long actualVersion) {
        if (affectedRows == 1) {
            return Outcome.APPLIED;
        }
        if (affectedRows != 0) {
            throw new IllegalArgumentException(
                    "条件更新影响行数只能是 0 或 1");
        }
        if (duplicateEvent) {
            return Outcome.REPLAYED;
        }
        if (!resourceExists) {
            return Outcome.RESOURCE_NOT_FOUND;
        }
        if (!fromStates.contains(currentState)) {
            return Outcome.ILLEGAL_STATE;
        }
        if (actualVersion != expectedVersion) {
            return Outcome.VERSION_CONFLICT;
        }
        // 状态和版本看似一致时仍可能已被并发事务抢先提交。
        return Outcome.VERSION_CONFLICT;
    }

    public Set<S> getFromStates() {
        return fromStates;
    }

    public S getToState() {
        return toState;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public String getEventKey() {
        return eventKey;
    }

    public enum Outcome {
        APPLIED,
        REPLAYED,
        RESOURCE_NOT_FOUND,
        ILLEGAL_STATE,
        VERSION_CONFLICT
    }
}
