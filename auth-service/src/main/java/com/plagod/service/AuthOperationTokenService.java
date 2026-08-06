package com.plagod.service;

import com.plagod.dto.auth.OperationTokenConsumeRequest;
import com.plagod.dto.auth.OperationTokenIssueRequest;
import com.plagod.vo.auth.OperationTokenConsumptionVO;
import com.plagod.vo.auth.OperationTokenVO;

public interface AuthOperationTokenService {

    OperationTokenVO issue(Long userId,
                           String sessionId,
                           OperationTokenIssueRequest request,
                           String clientIp);

    OperationTokenConsumptionVO consume(OperationTokenConsumeRequest request);
}
