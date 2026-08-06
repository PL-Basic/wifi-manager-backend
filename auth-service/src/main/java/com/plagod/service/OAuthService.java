package com.plagod.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.client.UserSocialIdentityClient;
import com.plagod.constant.OAuthProvider;
import com.plagod.constant.OAuthPurpose;
import com.plagod.constant.SocialIdentityResolveStatus;
import com.plagod.dto.*;
import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.dto.user.SocialIdentityResolveDTO;
import com.plagod.service.oauth.OAuthProviderAdapter;
import com.plagod.service.oauth.OAuthProviderRegistry;
import com.plagod.vo.AuthSessionIssue;
import com.plagod.vo.OAuthAuthorizationVO;
import com.plagod.vo.OAuthCallbackIssue;
import com.plagod.vo.OAuthCallbackResultVO;
import com.plagod.vo.user.SocialIdentityResolveResultVO;
import com.plagod.vo.user.SocialLoginPrincipalVO;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OAuthService {

    @Autowired
    private DefaultTenantMembershipOutboxService defaultTenantMembershipOutboxService;

    @Autowired
    private OAuthProviderRegistry providerRegistry;

    @Autowired
    private OAuthStateTransactionService stateService;

    @Autowired
    private UserSocialIdentityClient userClient;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${wifi.internal.token:local-internal-token-change-me}")
    private String internalToken;

    public OAuthAuthorizationVO startLogin(String provider, String returnUri) {

        OAuthProviderAdapter adapter = providerRegistry.require(provider);

        return stateService.issue(adapter, OAuthPurpose.LOGIN, null, returnUri);
    }

    public OAuthAuthorizationVO startBind(String provider, Long userId, String returnUri) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("绑定操作缺少可信用户身份");
        }

        OAuthProviderAdapter adapter = providerRegistry.require(provider);

        return stateService.issue(adapter, OAuthPurpose.BIND, userId, returnUri);
    }

    public OAuthCallbackIssue callback(String providerValue,
                                       String rawState,
                                       String authorizationCode,
                                       String clientInstanceId,
                                       String userAgent,
                                       String clientIp) {

        OAuthProvider provider = OAuthProvider.parse(providerValue);

        OAuthStateContext context;
        try {
            context = stateService.claim(provider.value(), rawState, authorizationCode);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该 OAuth 授权码已经被使用");
        }

        if (context.isReplayed()) {
            return new OAuthCallbackIssue(buildReplayResult(context), null);
        }

        AuthSessionIssue sessionIssue = null;
        try {
            OAuthProviderAdapter adapter = providerRegistry.require(provider.value());

            OAuthProfile profile = adapter.exchange(authorizationCode);

            SocialIdentityResolveDTO resolveDTO = buildResolveDTO(profile, context);

            ApiResponse<SocialIdentityResolveResultVO> response = userClient.resolve(internalToken, resolveDTO);

            SocialIdentityResolveResultVO resolved = requireResolveResult(response);

            OAuthCallbackResultVO result = buildCallbackResult(resolved, context);
            sessionIssue = openLoginSession(
                    result,
                    clientInstanceId,
                    userAgent,
                    clientIp);

            Long resultUserId = resolved.getPrincipal() == null ? null : resolved.getPrincipal().getUserId();

            String resultMessage = StringUtils.hasText(resolved.getMessage()) ? resolved.getMessage() : result.getMessage();

            stateService.complete(context, resolved.getStatus().name(), resultUserId, resultMessage);

            return new OAuthCallbackIssue(result, sessionIssue);
        } catch (FeignException exception) {
            String message = resolveFeignFailureMessage(exception);

            failQuietly(context, message);

            // user-service 的 400 表示可以安全返回的业务拒绝。
            if (exception.status() == 400) {
                throw new IllegalArgumentException(message);
            }

            // 内部认证失败、服务异常等不能伪装成用户参数错误。
            throw new IllegalStateException(message);
        } catch (RuntimeException exception) {
            revokeIssuedSessionQuietly(sessionIssue);
            String message = safeFailureMessage(exception);
            failQuietly(context, message);
            throw exception;
        }
    }

    private AuthSessionIssue openLoginSession(OAuthCallbackResultVO result,
                                              String clientInstanceId,
                                              String userAgent,
                                              String clientIp) {
        if (!"LOGIN_READY".equals(result.getStatus())
                || !"ACTIVE".equals(result.getAccountState())
                || result.getUserId() == null) {
            return null;
        }
        AuthResultDTO identity = new AuthResultDTO();
        identity.setUserId(String.valueOf(result.getUserId()));
        identity.setUsername(result.getUsername());
        identity.setRole(result.getRole());
        identity.setNickname(result.getNickname());
        identity.setAvatar(result.getAvatar());
        AuthSessionIssue issue = authSessionService.open(
                identity,
                clientInstanceId,
                userAgent,
                clientIp);
        result.setToken(issue.getAuthResult().getToken());
        result.setContext(issue.getAuthResult().getContext());
        return issue;
    }

    private void revokeIssuedSessionQuietly(AuthSessionIssue sessionIssue) {
        if (sessionIssue == null || !StringUtils.hasText(sessionIssue.getSessionId())) {
            return;
        }
        try {
            authSessionService.revokeSession(
                    sessionIssue.getSessionId(),
                    "OAUTH_CALLBACK_FAILED");
        } catch (RuntimeException exception) {
            log.warn(
                    "OAuth 回调失败后的会话撤销未完成，sessionId={}",
                    sessionIssue.getSessionId());
        }
    }

    public void deny(String providerValue, String rawState) {

        OAuthProvider provider = OAuthProvider.parse(providerValue);

        stateService.deny(provider.value(), rawState);
    }

    private SocialIdentityResolveDTO buildResolveDTO(OAuthProfile profile, OAuthStateContext context) {

        SocialIdentityResolveDTO dto = new SocialIdentityResolveDTO();

        dto.setProvider(profile.getProvider());
        dto.setPurpose(context.getPurpose());
        dto.setProviderSubject(profile.getProviderSubject());
        dto.setProviderUnionId(profile.getProviderUnionId());
        dto.setProviderUsername(profile.getProviderUsername());
        dto.setDisplayName(profile.getDisplayName());
        dto.setAvatarUrl(profile.getAvatarUrl());
        dto.setVerifiedEmail(profile.getVerifiedEmail());
        dto.setEmailVerified(Boolean.TRUE.equals(profile.getEmailVerified()));
        dto.setBindUserId(context.getBindUserId());
        return dto;
    }

    private SocialIdentityResolveResultVO requireResolveResult(ApiResponse<SocialIdentityResolveResultVO> response) {

        if (response == null ||
                response.getCode() != 200 ||
                response.getData() == null ||
                response.getData().getStatus() == null) {
            throw new IllegalStateException("社交身份服务返回无效结果");
        }
        return response.getData();
    }

    private OAuthCallbackResultVO buildCallbackResult(SocialIdentityResolveResultVO resolved, OAuthStateContext context) {

        OAuthCallbackResultVO result = new OAuthCallbackResultVO();

        result.setStatus(resolved.getStatus().name());
        result.setMessage(resolveSuccessMessage(resolved.getStatus(), resolved.getMessage()));
        result.setIdentity(resolved.getIdentity());
        result.setReturnUri(context.getReturnUri());
        result.setReplayed(false);

        SocialLoginPrincipalVO principal = resolved.getPrincipal();

        if (principal != null) {
            result.setUserId(principal.getUserId());
            result.setUsername(principal.getUsername());
            result.setRole(principal.getRole());
            result.setNickname(principal.getNickname());
            result.setAvatar(principal.getAvatar());
        }

        if (resolved.getStatus() == SocialIdentityResolveStatus.LOGIN_READY) {

            if (principal == null) {
                throw new IllegalStateException("社交登录结果缺少用户身份");
            }

            if (!Integer.valueOf(0).equals(principal.getRole())) {
                defaultTenantMembershipOutboxService.dispatchForUser(principal.getUserId());
            }
            if (!Integer.valueOf(0).equals(principal.getRole())
                    && !defaultTenantMembershipOutboxService.isMembershipReady(principal.getUserId())) {
                result.setAccountState("TENANT_MEMBERSHIP_PENDING");
                result.setMessage("默认租户成员关系正在恢复");
            } else {
                result.setAccountState("ACTIVE");
            }
        }

        return result;
    }

    private OAuthCallbackResultVO buildReplayResult(OAuthStateContext context) {

        OAuthCallbackResultVO result = new OAuthCallbackResultVO();

        result.setStatus(context.getResultStatus());
        result.setMessage(StringUtils.hasText(context.getResultMessage())? context.getResultMessage() : "OAuth 回调已经处理");
        result.setUserId(context.getResultUserId());
        result.setReturnUri(context.getReturnUri());
        result.setReplayed(true);

        result.setToken(null);
        return result;
    }

    private String resolveSuccessMessage(SocialIdentityResolveStatus status, String providerMessage) {

        if (StringUtils.hasText(providerMessage)) {
            return providerMessage;
        }
        if (status == SocialIdentityResolveStatus.LOGIN_READY) {
            return "社交登录成功";
        }
        if (status == SocialIdentityResolveStatus.BIND_READY) {
            return "社交身份绑定成功";
        }
        return "该社交身份需要绑定已有账号";
    }

    private String safeFailureMessage(
            RuntimeException exception) {

        if ((exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) && StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }

        return "OAuth 回调处理失败";
    }

    private String resolveFeignFailureMessage(FeignException exception) {

        if (exception.status() == 400) {
            try {
                JsonNode body = objectMapper.readTree(exception.contentUTF8());
                JsonNode messageNode = body.get("message");

                if (messageNode != null && messageNode.isTextual() && StringUtils.hasText(messageNode.asText())) {
                    return messageNode.asText();
                }
            } catch (Exception ignored) {
                // 下游响应无法解析时使用统一文案，不能继续覆盖原异常流程。
            }
            return "社交身份信息不符合绑定要求";
        }

        if (exception.status() == 401 || exception.status() == 403) {
            return "社交身份服务内部认证失败";
        }
        return "社交身份服务暂时不可用";
    }

    private void failQuietly(OAuthStateContext context, String message) {

        try {
            stateService.fail(context, message);
        } catch (RuntimeException failureException) {
            log.warn("OAuth 失败状态写入未完成");
        }
    }
}
