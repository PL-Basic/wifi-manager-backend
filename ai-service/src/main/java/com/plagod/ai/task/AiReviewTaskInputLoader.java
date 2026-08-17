package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationScene;
import com.plagod.mapper.AiReviewTaskInputMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AiReviewTaskInputLoader {

    private static final String ANNOUNCEMENT = "ANNOUNCEMENT";
    private static final String SUPPORT_SUBMISSION = "SUPPORT_SUBMISSION";
    private static final String DEFAULT_LANGUAGE = "zh-CN";

    private final AiReviewTaskInputMapper inputMapper;

    public AiReviewTaskInputLoader(
            AiReviewTaskInputMapper inputMapper) {
        this.inputMapper = Objects.requireNonNull(
                inputMapper,
                "inputMapper 不能为空");
    }

    public AiModerationRequest load(AiReviewTaskClaim claim) {
        Objects.requireNonNull(claim, "AI 任务 claim 不能为空");
        AiReviewTaskInputSnapshot snapshot = loadSnapshot(claim);
        if (snapshot == null
                || !claim.getContentHash().equals(snapshot.getContentHash())) {
            throw new AiReviewTaskInputException();
        }

        try {
            AiModerationRequest request = new AiModerationRequest(
                    AiModerationScene.valueOf(claim.getScene()),
                    claim.getReviewRequestId(),
                    "policy-version:" + claim.getPolicyVersionId(),
                    snapshot.getTitle(),
                    snapshot.getBody(),
                    snapshot.getContentHash(),
                    DEFAULT_LANGUAGE);
            if (!claim.matches(request)) {
                throw new AiReviewTaskInputException();
            }
            return request;
        } catch (IllegalArgumentException exception) {
            throw new AiReviewTaskInputException();
        }
    }

    private AiReviewTaskInputSnapshot loadSnapshot(
            AiReviewTaskClaim claim) {
        if (AiModerationScene.ANNOUNCEMENT_REVIEW.name()
                .equals(claim.getScene())
                && ANNOUNCEMENT.equals(claim.getBusinessType())) {
            return inputMapper.selectAnnouncementVersion(
                    claim.getBusinessId(),
                    claim.getTenantId(),
                    claim.getContentVersion(),
                    claim.getReviewRequestId(),
                    claim.getContentHash());
        }
        if (AiModerationScene.SUPPORT_SUBMISSION_REVIEW.name()
                .equals(claim.getScene())
                && SUPPORT_SUBMISSION.equals(claim.getBusinessType())
                && claim.getTenantId() != null) {
            return inputMapper.selectSupportSubmission(
                    claim.getBusinessId(),
                    claim.getTenantId(),
                    claim.getContentVersion(),
                    claim.getReviewRequestId(),
                    claim.getContentHash());
        }
        throw new AiReviewTaskInputException();
    }
}
