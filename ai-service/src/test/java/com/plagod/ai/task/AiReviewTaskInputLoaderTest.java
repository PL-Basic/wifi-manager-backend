package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.support.AiContentSecurity;
import com.plagod.entity.AiReviewTask;
import com.plagod.mapper.AiReviewTaskInputMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewTaskInputLoaderTest {

    private AiReviewTaskInputMapper inputMapper;
    private AiReviewTaskInputLoader loader;

    @BeforeEach
    void setUp() {
        inputMapper = mock(AiReviewTaskInputMapper.class);
        loader = new AiReviewTaskInputLoader(inputMapper);
    }

    @Test
    void rebuildsAnnouncementInputFromOwnedFrozenVersion() {
        AiReviewTaskClaim claim = claim(
                "ANNOUNCEMENT_REVIEW",
                "ANNOUNCEMENT",
                501L,
                null,
                3,
                "review-loader-001",
                "title",
                "body");
        AiReviewTaskInputSnapshot snapshot =
                snapshot("title", "body", claim.getContentHash());
        when(inputMapper.selectAnnouncementVersion(
                501L,
                null,
                3,
                "review-loader-001",
                claim.getContentHash()))
                .thenReturn(snapshot);

        AiModerationRequest request = loader.load(claim);

        assertEquals("title", request.getTitle());
        assertEquals("body", request.getBody());
        assertEquals("policy-version:7", request.getPolicyVersion());
        verify(inputMapper).selectAnnouncementVersion(
                501L,
                null,
                3,
                "review-loader-001",
                claim.getContentHash());
    }

    @Test
    void rebuildsSupportInputOnlyWithinTaskTenant() {
        AiReviewTaskClaim claim = claim(
                "SUPPORT_SUBMISSION_REVIEW",
                "SUPPORT_SUBMISSION",
                901L,
                22L,
                1,
                "review-loader-002",
                "support",
                "details");
        AiReviewTaskInputSnapshot snapshot =
                snapshot("support", "details", claim.getContentHash());
        when(inputMapper.selectSupportSubmission(
                901L,
                22L,
                1,
                "review-loader-002",
                claim.getContentHash()))
                .thenReturn(snapshot);

        AiModerationRequest request = loader.load(claim);

        assertEquals(
                "SUPPORT_SUBMISSION_REVIEW",
                request.getScene().name());
        verify(inputMapper).selectSupportSubmission(
                901L,
                22L,
                1,
                "review-loader-002",
                claim.getContentHash());
    }

    @Test
    void rejectsMissingOrChangedFrozenContent() {
        AiReviewTaskClaim claim = claim(
                "ANNOUNCEMENT_REVIEW",
                "ANNOUNCEMENT",
                501L,
                null,
                3,
                "review-loader-003",
                "title",
                "body");

        assertThrows(
                AiReviewTaskInputException.class,
                () -> loader.load(claim));
    }

    private AiReviewTaskClaim claim(
            String scene,
            String businessType,
            Long businessId,
            Long tenantId,
            int contentVersion,
            String reviewRequestId,
            String title,
            String body) {
        AiReviewTask task = new AiReviewTask();
        task.setReviewTaskId(41L);
        task.setReviewRequestId(reviewRequestId);
        task.setScene(scene);
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setTenantId(tenantId);
        task.setContentVersion(contentVersion);
        task.setContentHash(
                new AiContentSecurity().contentHash(title, body));
        task.setPolicyVersionId(7L);
        task.setTaskStatus("RUNNING");
        task.setWorkerId("worker-ai");
        task.setAttemptCount(1);
        task.setVersion(8);
        return AiReviewTaskClaim.from(task, "worker-ai");
    }

    private AiReviewTaskInputSnapshot snapshot(
            String title,
            String body,
            String contentHash) {
        AiReviewTaskInputSnapshot snapshot =
                new AiReviewTaskInputSnapshot();
        snapshot.setTitle(title);
        snapshot.setBody(body);
        snapshot.setContentHash(contentHash);
        return snapshot;
    }
}
