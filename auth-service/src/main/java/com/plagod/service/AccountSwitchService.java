package com.plagod.service;

import com.plagod.dto.AccountSwitchCodeRequest;
import com.plagod.dto.AccountSwitchRequest;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.vo.AccountSwitchCodeVO;

public interface AccountSwitchService {

    AccountSwitchCodeVO sendCode(AccountSwitchCodeRequest request, String clientIp);

    AuthResultDTO verify(AccountSwitchRequest request, String clientIp);
}
