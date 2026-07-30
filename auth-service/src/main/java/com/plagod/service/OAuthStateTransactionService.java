package com.plagod.service;

import com.plagod.configuration.OAuthProperties;
import com.plagod.constant.OAuthPurpose;
import com.plagod.vo.OAuthAuthorizationVO;
import com.plagod.dto.OAuthStateContext;
import com.plagod.entity.auth.OAuthStateRecord;
import com.plagod.mapper.OAuthStateMapper;
import com.plagod.service.oauth.OAuthProviderAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Service
public class OAuthStateTransactionService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private OAuthStateMapper oauthStateMapper;

    @Autowired
    private OAuthProperties properties;

    @Transactional
    public OAuthAuthorizationVO issue(OAuthProviderAdapter adapter, OAuthPurpose purpose, Long bindUserId, String returnUri) {

        String normalizedReturnUri = normalizeReturnUri(returnUri);

        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);

        String rawState = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(properties.getStateExpireMinutes());

        OAuthStateRecord record = new OAuthStateRecord();
        record.setStateHash(sha256(rawState));
        record.setProvider(adapter.provider().value());
        record.setPurpose(purpose.value());
        record.setBindUserId(bindUserId);
        record.setReturnUri(normalizedReturnUri);
        record.setStatus(STATUS_PENDING);
        record.setExpireTime(expireTime);

        oauthStateMapper.insert(record);

        OAuthAuthorizationVO result = new OAuthAuthorizationVO();

        result.setProvider(adapter.provider().value());
        result.setPurpose(purpose.value());
        result.setAuthorizationUrl(adapter.buildAuthorizationUrl(rawState));
        result.setExpireTime(expireTime);
        return result;
    }

    @Transactional
    public OAuthStateContext claim(String provider, String rawState, String authorizationCode) {

        requireLength(rawState, "state", 256);
        requireLength(authorizationCode, "authorizationCode", 2048);

        String stateHash = sha256(rawState);
        String codeHash = sha256(authorizationCode);

        OAuthStateRecord record = oauthStateMapper.selectByHashForUpdate(stateHash);

        if (record == null) {
            throw new IllegalArgumentException("OAuth state 无效");
        }
        if (!record.getProvider().equals(provider)) {
            throw new IllegalArgumentException("OAuth state 与 Provider 不匹配");
        }

        if (Integer.valueOf(STATUS_COMPLETED).equals(record.getStatus())) {

            if (!codeHash.equals(record.getAuthorizationCodeHash())) {
                throw new IllegalArgumentException("OAuth 回调授权码与已完成记录不一致");
            }
            return toReplayContext(record, codeHash);
        }

        if (Integer.valueOf(STATUS_FAILED).equals(record.getStatus())) {
            throw new IllegalArgumentException(StringUtils.hasText(record.getResultMessage()) ? record.getResultMessage() : "OAuth 回调已经失败");
        }

        if (Integer.valueOf(STATUS_PROCESSING).equals(record.getStatus())) {
            throw new IllegalArgumentException("OAuth 回调正在处理中，请勿重复提交");
        }

        if (record.getExpireTime() == null || !record.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("OAuth state 已过期");
        }

        record.setAuthorizationCodeHash(codeHash);
        record.setStatus(STATUS_PROCESSING);
        record.setConsumeTime(LocalDateTime.now());
        oauthStateMapper.updateById(record);

        return toContext(record, codeHash, false);
    }

    @Transactional
    public void complete(OAuthStateContext context, String resultStatus, Long resultUserId, String message) {

        // 锁定记录，保证完成、失败和拒绝操作按顺序修改同一个 state。
        OAuthStateRecord record = oauthStateMapper.selectByIdForUpdate(context.getStateId());

        requireProcessingRecord(record, context);

        record.setStatus(STATUS_COMPLETED);
        record.setResultStatus(resultStatus);
        record.setResultUserId(resultUserId);
        record.setResultMessage(limit(message, 255));
        oauthStateMapper.updateById(record);
    }

    @Transactional
    public void fail(OAuthStateContext context, String message) {

        // 与 complete 使用相同的行锁，避免失败状态覆盖已经完成的结果。
        OAuthStateRecord record = oauthStateMapper.selectByIdForUpdate(context.getStateId());
        if (record == null || !Integer.valueOf(STATUS_PROCESSING).equals(record.getStatus()) || !context.getCodeHash().equals(record.getAuthorizationCodeHash())) {
            return;
        }

        record.setStatus(STATUS_FAILED);
        record.setResultMessage(limit(message, 255));
        oauthStateMapper.updateById(record);
    }

    @Transactional
    public void deny(String provider, String rawState) {

        requireLength(rawState, "state", 256);

        OAuthStateRecord record = oauthStateMapper.selectByHashForUpdate(sha256(rawState));

        if (record == null || !record.getProvider().equals(provider)) {
            throw new IllegalArgumentException("OAuth state 无效");
        }

        // Provider 拒绝只能终止尚未被领取的 state。
        // PROCESSING 说明另一个正常回调已经开始，不能覆盖其执行结果。
        if (!Integer.valueOf(STATUS_PENDING).equals(record.getStatus())) {
            return;
        }

        record.setStatus(STATUS_FAILED);
        record.setResultMessage("用户取消或 Provider 拒绝授权");
        record.setConsumeTime(LocalDateTime.now());
        oauthStateMapper.updateById(record);
    }
    private void requireProcessingRecord(OAuthStateRecord record, OAuthStateContext context) {

        if (record == null ||
                !Integer.valueOf(STATUS_PROCESSING).equals(record.getStatus()) ||
                !context.getCodeHash().equals(record.getAuthorizationCodeHash())) {
            throw new IllegalStateException("OAuth 回调状态已经发生变化");
        }
    }

    private OAuthStateContext toReplayContext(OAuthStateRecord record, String codeHash) {

        OAuthStateContext context = toContext(record, codeHash, true);

        context.setResultStatus(record.getResultStatus());
        context.setResultUserId(record.getResultUserId());
        context.setResultMessage(record.getResultMessage());
        return context;
    }

    private OAuthStateContext toContext(OAuthStateRecord record, String codeHash, boolean replayed) {

        OAuthStateContext context = new OAuthStateContext();
        context.setStateId(record.getStateId());
        context.setProvider(record.getProvider());
        context.setPurpose(record.getPurpose());
        context.setBindUserId(record.getBindUserId());
        context.setReturnUri(record.getReturnUri());
        context.setCodeHash(codeHash);
        context.setReplayed(replayed);
        return context;
    }

    private String normalizeReturnUri(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() > 512 ||
                trimmed.contains("\\") ||
                trimmed.contains("\r") ||
                trimmed.contains("\n")) {
            throw new IllegalArgumentException("returnUri 无效");
        }

        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return trimmed;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("returnUri 格式无效");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("returnUri 不在允许范围内");
        }

        String origin = buildOrigin(uri);
        for (String allowed : properties.getAllowedReturnOrigins()) {
            if (origin.equalsIgnoreCase(normalizeAllowedOrigin(allowed))) {
                return trimmed;
            }
        }

        throw new IllegalArgumentException("returnUri 不在允许范围内");
    }

    private String normalizeAllowedOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        try {
            return buildOrigin(URI.create(value.trim()));
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String buildOrigin(URI uri) {
        String origin = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);

        if (uri.getPort() >= 0) {
            origin += ":" + uri.getPort();
        }
        return origin;
    }

    private void requireLength(String value, String field, int maximumLength) {

        if (!StringUtils.hasText(value) || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder(64);
            for (byte item : hashed) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 OAuth 安全摘要");
        }
    }

    private String limit(String value, int maximumLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}