package com.plagod.service;

import com.plagod.client.UserAccountClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.exception.ApiStatusException;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UserAccountGateway {

    private final UserAccountClient client;
    private final String internalToken;

    public UserAccountGateway(
            UserAccountClient client,
            @Value("${wifi.internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    public UserAccountCreateResultVO create(UserAccountCreateRequest request) {
        return requireData(client.create(internalToken, request), "账号创建");
    }

    public UserAccountSnapshotVO findById(Long userId) {
        return optionalData(client.findById(internalToken, userId), "账号读取");
    }

    public UserAuthenticationSnapshotVO findAuthenticationById(Long userId) {
        return optionalData(
                client.findAuthenticationById(internalToken, userId),
                "账号认证信息读取");
    }

    public UserAuthenticationSnapshotVO findByLogin(
            String loginType,
            String account) {
        return optionalData(
                client.findByLogin(internalToken, loginType, account),
                "账号读取");
    }

    public UserPasswordReplaceResultVO replacePassword(
            UserPasswordReplaceRequest request) {
        return requireData(
                client.replacePassword(internalToken, request),
                "密码修改");
    }

    public void dispatchDefaultMembership(Long userId) {
        requireSuccess(
                client.dispatchDefaultMembership(internalToken, userId),
                "默认租户成员事件投递");
    }

    private <T> T requireData(ApiResponse<T> response, String action) {
        T data = optionalData(response, action);
        if (data == null) {
            throw ApiStatusException.serviceUnavailable(action + "服务返回空结果");
        }
        return data;
    }

    private <T> T optionalData(ApiResponse<T> response, String action) {
        requireSuccess(response, action);
        return response.getData();
    }

    private void requireSuccess(ApiResponse<?> response, String action) {
        if (response == null || response.getCode() != 200) {
            throw ApiStatusException.serviceUnavailable(action + "服务返回无效结果");
        }
    }

    public RuntimeException mapFailure(String action, RuntimeException exception) {
        if (exception instanceof ApiStatusException) {
            return exception;
        }
        if (exception instanceof FeignException) {
            FeignException feignException = (FeignException) exception;
            if (feignException.status() == 400) {
                return new IllegalArgumentException(action + "请求无效");
            }
            if (feignException.status() == 401 || feignException.status() == 403) {
                return ApiStatusException.forbidden(action + "内部认证失败");
            }
        }
        return ApiStatusException.serviceUnavailable(action + "服务暂时不可用");
    }
}
