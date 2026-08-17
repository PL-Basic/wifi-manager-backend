package com.plagod.ai.task;

import com.plagod.entity.AiReviewTask;

import java.util.Objects;

public final class AiReviewTaskEntryResult {

    private final AiReviewTask task;
    private final boolean duplicate;

    public AiReviewTaskEntryResult(
            AiReviewTask task,
            boolean duplicate) {
        this.task = Objects.requireNonNull(task, "task 不能为空");
        this.duplicate = duplicate;
    }

    public AiReviewTask getTask() {
        return task;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
