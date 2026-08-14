package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.plagod.dto.user.UserAccountCreateRequest;
import com.plagod.dto.user.UserPasswordReplaceRequest;
import com.plagod.entity.user.User;
import com.plagod.entity.user.UserAccountCommandReceipt;
import com.plagod.mapper.UserAccountCommandReceiptMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.DefaultTenantMembershipOutboxAppender;
import com.plagod.service.DefaultTenantMembershipOutboxService;
import com.plagod.service.UserAccountPersistenceService;
import com.plagod.vo.user.UserAccountCreateResultVO;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;
import com.plagod.vo.user.UserPasswordReplaceResultVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class UserAccountPersistenceServiceImpl
        implements UserAccountPersistenceService {

    private final UserMapper userMapper;
    private final UserAccountCommandReceiptMapper receiptMapper;
    private final DefaultTenantMembershipOutboxAppender outboxAppender;
    private final DefaultTenantMembershipOutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    public UserAccountPersistenceServiceImpl(
            UserMapper userMapper,
            UserAccountCommandReceiptMapper receiptMapper,
            DefaultTenantMembershipOutboxAppender outboxAppender,
            DefaultTenantMembershipOutboxService outboxService,
            PlatformTransactionManager transactionManager) {
        this.userMapper = userMapper;
        this.receiptMapper = receiptMapper;
        this.outboxAppender = outboxAppender;
        this.outboxService = outboxService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public UserAccountCreateResultVO createAccount(UserAccountCreateRequest request) {
        UserAccountCommandReceipt receipt =
                findReceipt("REGISTER", request.getIdempotencyKey());
        if (receipt != null) {
            return resolveCreateReceipt(receipt, request);
        }

        try {
            return transactionTemplate.execute(
                    status -> createAccountInTransaction(request));
        } catch (DuplicateKeyException exception) {
            UserAccountCommandReceipt concurrentReceipt =
                    findReceipt("REGISTER", request.getIdempotencyKey());
            if (concurrentReceipt != null) {
                return resolveCreateReceipt(concurrentReceipt, request);
            }
            return UserAccountCreateResultVO.conflict(findConflicts(request));
        }
    }

    private UserAccountCreateResultVO createAccountInTransaction(
            UserAccountCreateRequest request) {
        UserAccountCommandReceipt receipt =
                findReceipt("REGISTER", request.getIdempotencyKey());
        if (receipt != null) {
            return resolveCreateReceipt(receipt, request);
        }
        Set<String> conflicts = findConflicts(request);
        if (!conflicts.isEmpty()) {
            return UserAccountCreateResultVO.conflict(conflicts);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPasswordHash());
        user.setNickname(request.getNickname());
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setPhone(normalizeNullable(request.getPhone()));
        user.setRole(2);
        user.setStatus(1);
        user.setDelFlag(0);

        userMapper.insert(user);

        outboxAppender.append(
                user.getUserId(),
                user.getRole(),
                request.getIdempotencyKey(),
                request.getRequestFingerprint());

        insertReceipt(
                "REGISTER",
                request.getIdempotencyKey(),
                request.getRequestFingerprint(),
                user.getUserId());
        return UserAccountCreateResultVO.success(toSnapshot(user), false);
    }

    @Override
    public UserAccountSnapshotVO findById(Long userId) {
        return toSnapshot(userMapper.selectById(userId));
    }

    @Override
    public UserAuthenticationSnapshotVO findAuthenticationById(Long userId) {
        return toAuthenticationSnapshot(userMapper.selectById(userId));
    }

    @Override
    public UserAuthenticationSnapshotVO findByLogin(
            String loginType,
            String account) {
        if (!StringUtils.hasText(loginType) || !StringUtils.hasText(account)) {
            return null;
        }
        QueryWrapper<User> query = new QueryWrapper<User>().eq("del_flag", 0);
        if ("username".equals(loginType)) {
            query.eq("username", account.trim());
        } else if ("contact".equals(loginType)) {
            query.and(wrapper -> wrapper.eq("email", account.trim())
                    .or().eq("phone", account.trim()));
        } else {
            throw new IllegalArgumentException("登录类型错误");
        }
        return toAuthenticationSnapshot(userMapper.selectOne(query));
    }

    @Override
    public UserPasswordReplaceResultVO replacePassword(UserPasswordReplaceRequest request) {
        UserAccountCommandReceipt receipt =
                findReceipt("PASSWORD_REPLACE", request.getIdempotencyKey());
        if (receipt != null) {
            return resolvePasswordReceipt(receipt, request);
        }

        UserPasswordReplaceResultVO result;
        try {
            result = transactionTemplate.execute(
                    status -> replacePasswordInTransaction(request));
        } catch (DuplicateKeyException exception) {
            result = UserPasswordReplaceResultVO.conflict();
        }
        if (!"CONFLICT".equals(result.getStatus())) {
            return result;
        }

        UserAccountCommandReceipt concurrentReceipt =
                findReceipt("PASSWORD_REPLACE", request.getIdempotencyKey());
        return concurrentReceipt == null
                ? result
                : resolvePasswordReceipt(concurrentReceipt, request);
    }

    private UserPasswordReplaceResultVO replacePasswordInTransaction(
            UserPasswordReplaceRequest request) {
        UserAccountCommandReceipt receipt =
                findReceipt("PASSWORD_REPLACE", request.getIdempotencyKey());
        if (receipt != null) {
            return resolvePasswordReceipt(receipt, request);
        }
        User current = userMapper.selectById(request.getUserId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) {
            return UserPasswordReplaceResultVO.conflict();
        }
        int updated = userMapper.update(null, new UpdateWrapper<User>()
                .eq("user_id", request.getUserId())
                .eq("status", 1)
                .eq("password", request.getExpectedPasswordHash())
                .set("password", request.getNewPasswordHash()));
        if (updated != 1) {
            return UserPasswordReplaceResultVO.conflict();
        }
        insertReceipt(
                "PASSWORD_REPLACE",
                request.getIdempotencyKey(),
                request.getRequestFingerprint(),
                request.getUserId());
        return UserPasswordReplaceResultVO.replaced(false);
    }

    @Override
    public void dispatchDefaultMembership(Long userId) {
        outboxService.dispatchForUser(userId);
    }

    private Set<String> findConflicts(UserAccountCreateRequest request) {
        QueryWrapper<User> query = new QueryWrapper<User>()
                .eq("del_flag", 0)
                .and(wrapper -> {
                    wrapper.eq("username", request.getUsername());
                    if (StringUtils.hasText(request.getEmail())) {
                        wrapper.or().eq("email", request.getEmail().trim());
                    }
                    if (StringUtils.hasText(request.getPhone())) {
                        wrapper.or().eq("phone", request.getPhone().trim());
                    }
                });
        List<User> users = userMapper.selectList(query);
        Set<String> conflicts = new LinkedHashSet<>();
        for (User user : users) {
            if (Objects.equals(user.getUsername(), request.getUsername())) {
                conflicts.add("USERNAME");
            }
            if (StringUtils.hasText(request.getEmail())
                    && request.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
                conflicts.add("EMAIL");
            }
            if (StringUtils.hasText(request.getPhone())
                    && request.getPhone().trim().equals(user.getPhone())) {
                conflicts.add("PHONE");
            }
        }
        return conflicts;
    }

    private UserAccountCommandReceipt findReceipt(
            String commandType,
            String idempotencyKey) {
        return receiptMapper.selectOne(
                new QueryWrapper<UserAccountCommandReceipt>()
                        .eq("command_type", commandType)
                        .eq("idempotency_key", idempotencyKey));
    }

    private UserAccountCreateResultVO resolveCreateReceipt(
            UserAccountCommandReceipt receipt,
            UserAccountCreateRequest request) {
        if (!Objects.equals(
                receipt.getRequestFingerprint(), request.getRequestFingerprint())) {
            return UserAccountCreateResultVO.fingerprintConflict();
        }
        return UserAccountCreateResultVO.success(
                toSnapshot(userMapper.selectById(receipt.getUserId())),
                true);
    }

    private UserPasswordReplaceResultVO resolvePasswordReceipt(
            UserAccountCommandReceipt receipt,
            UserPasswordReplaceRequest request) {
        return Objects.equals(
                receipt.getRequestFingerprint(), request.getRequestFingerprint())
                ? UserPasswordReplaceResultVO.replaced(true)
                : UserPasswordReplaceResultVO.fingerprintConflict();
    }

    private UserAccountSnapshotVO toSnapshot(User user) {
        if (user == null) {
            return null;
        }
        UserAccountSnapshotVO snapshot = new UserAccountSnapshotVO();
        snapshot.setUserId(user.getUserId());
        snapshot.setUsername(user.getUsername());
        snapshot.setNickname(user.getNickname());
        snapshot.setEmail(user.getEmail());
        snapshot.setPhone(user.getPhone());
        snapshot.setAvatar(user.getAvatar());
        snapshot.setRole(user.getRole());
        snapshot.setStatus(user.getStatus());
        snapshot.setMembershipReady(outboxService.isMembershipReady(user.getUserId()));
        return snapshot;
    }

    private UserAuthenticationSnapshotVO toAuthenticationSnapshot(User user) {
        UserAccountSnapshotVO account = toSnapshot(user);
        if (account == null) {
            return null;
        }
        UserAuthenticationSnapshotVO authentication =
                new UserAuthenticationSnapshotVO();
        authentication.setUserId(account.getUserId());
        authentication.setUsername(account.getUsername());
        authentication.setNickname(account.getNickname());
        authentication.setEmail(account.getEmail());
        authentication.setPhone(account.getPhone());
        authentication.setAvatar(account.getAvatar());
        authentication.setRole(account.getRole());
        authentication.setStatus(account.getStatus());
        authentication.setMembershipReady(account.getMembershipReady());
        authentication.setPasswordHash(user.getPassword());
        return authentication;
    }

    private void insertReceipt(
            String commandType,
            String idempotencyKey,
            String requestFingerprint,
            Long userId) {
        UserAccountCommandReceipt receipt = new UserAccountCommandReceipt();
        receipt.setCommandType(commandType);
        receipt.setIdempotencyKey(idempotencyKey);
        receipt.setRequestFingerprint(requestFingerprint);
        receipt.setUserId(userId);
        receipt.setResultStatus("SUCCEEDED");
        receiptMapper.insert(receipt);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
