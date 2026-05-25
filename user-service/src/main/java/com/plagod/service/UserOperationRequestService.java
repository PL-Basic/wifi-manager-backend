package com.plagod.service;

import com.plagod.dto.UserOperationRequestPageResult;
import com.plagod.dto.UserOperationReviewDTO;
import com.plagod.entity.UserOperationRequest;

public interface UserOperationRequestService {
    UserOperationRequestPageResult pageRequests(long current, long size, Integer status);

    Long requestPurge(Long targetUserId, Long requesterId, String requesterName, String reason);

    void review(Long id, Long approverId, String approverName, UserOperationReviewDTO dto);
}
