package com.plagod.service;

import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;

public interface UserAccountPersistenceService {

    UserAccountCreateResultVO createAccount(UserAccountCreateRequest request);

    UserAccountSnapshotVO findById(Long userId);

    UserAuthenticationSnapshotVO findAuthenticationById(Long userId);

    UserAuthenticationSnapshotVO findByLogin(
            String loginType,
            String account);

    UserPasswordReplaceResultVO replacePassword(UserPasswordReplaceRequest request);

    void dispatchDefaultMembership(Long userId);
}
