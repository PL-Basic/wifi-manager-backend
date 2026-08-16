package com.plagod.service;

import com.plagod.vo.user.UserOperationRequestPageResult;
import com.plagod.dto.user.UserOperationReviewDTO;

public interface UserOperationRequestService {
    UserOperationRequestPageResult pageRequests(
            Integer current,
            Integer size,
            Integer status);

    Long requestPurge(Long targetUserId, Long requesterId, String requesterName, String reason);

    void review(Long id, Long approverId, String approverName, Integer approverRole, UserOperationReviewDTO dto);
}
