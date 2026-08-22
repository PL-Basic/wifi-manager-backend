package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.AuditDetail;
import com.plagod.audit.AuditTargetId;
import com.plagod.audit.Audited;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SocialIdentityMapper;
import com.plagod.vo.user.UserConnectionPolicyVO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.vo.user.UserVO;
import com.plagod.vo.user.UserRoleSnapshotVO;
import com.plagod.entity.user.User;
import com.plagod.mapper.UserMapper;
import com.plagod.service.UserAuthSessionRevokeOutboxAppender;
import com.plagod.service.UserManageService;
import com.plagod.support.PageBounds;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserManageServiceImpl implements UserManageService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SocialIdentityMapper socialIdentityMapper;

    @Autowired
    private UserAuthSessionRevokeOutboxAppender revokeOutboxAppender;

    @Override
    public UserPageResult pageUsers(
            Integer current,
            Integer size,
            String keyword) {
        PageBounds pageBounds = PageBounds.of(current, size);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like("username", keyword)
                    .or().like("nickname", keyword)
                    .or().like("email", keyword)
                    .or().like("phone", keyword));
        }
        queryWrapper.orderByDesc("create_time");
        queryWrapper.orderByDesc("user_id");

        Page<User> page = userMapper.selectPage(
                new Page<>(
                        pageBounds.getCurrent(),
                        pageBounds.getSize()),
                queryWrapper);
        List<UserVO> records = new ArrayList<>();
        for (User user : page.getRecords()) {
            records.add(toVO(user));
        }

        UserPageResult result = new UserPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    public UserVO getUser(Long userId) {
        User user = getExistingUser(userId);
        return toVO(user);
    }

    @Override
    public List<UserRoleSnapshotVO> getRoleSnapshots(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty() || userIds.size() > 100) {
            throw new IllegalArgumentException("用户ID列表数量必须在1到100之间");
        }
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("用户ID必须大于0");
            }
        }
        List<UserRoleSnapshotVO> result = new ArrayList<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            UserRoleSnapshotVO snapshot = new UserRoleSnapshotVO();
            snapshot.setUserId(String.valueOf(user.getUserId()));
            snapshot.setUsername(user.getUsername());
            snapshot.setNickname(user.getNickname());
            snapshot.setRole(user.getRole());
            snapshot.setStatus(user.getStatus());
            result.add(snapshot);
        }
        return result;
    }

    @Override
    @Transactional
    @Audited(
            action = "user.update",
            targetType = "USER",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    public UserVO updateUser(
            @AuditTargetId Long userId,
            UserUpdateDTO updateDTO,
            @AuditDetail("operatorRole") Integer operatorRole) {
        User user = getExistingUser(userId);
        boolean roleChanged = false;

        String rawEmail = updateDTO.getEmail();
        String rawNickname = updateDTO.getNickname();
        String rawPhone = updateDTO.getPhone();
        String rawAvatar = updateDTO.getAvatar();

        updateDTO.setNickname(cleanText(updateDTO.getNickname()));
        updateDTO.setEmail(cleanText(updateDTO.getEmail()));
        updateDTO.setPhone(cleanText(updateDTO.getPhone()));
        updateDTO.setAvatar(cleanText(updateDTO.getAvatar()));

        if (!Integer.valueOf(0).equals(operatorRole)) {
            updateDTO.setRole(null);
        }
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("user_id", userId);
        if (rawEmail != null) {
            updateWrapper.set("email", updateDTO.getEmail());
        }
        if (rawPhone != null) {
            updateWrapper.set("phone", updateDTO.getPhone());
        }
        if (rawAvatar != null) {
            updateWrapper.set("avatar", updateDTO.getAvatar());
        }
        if (rawNickname != null) {
            updateWrapper.set("nickname", updateDTO.getNickname());
        }
        if (updateDTO.getRole() != null) {
            if (!updateDTO.getRole().equals(user.getRole())) {
                roleChanged = true;
                appendAuthSessionRevoke(userId, "ROLE_CHANGED");
            }
            updateWrapper.set("role", updateDTO.getRole());
        }
        if (updateDTO.getMaxConnections() != null) {
            updateWrapper.set("max_connections", updateDTO.getMaxConnections());
        }
        if (updateDTO.getDailyQuotaMinutes() != null) {
            updateWrapper.set("daily_quota_minutes", updateDTO.getDailyQuotaMinutes());
        }
        if (updateDTO.getExpireTime() != null) {
            updateWrapper.set("expire_time", updateDTO.getExpireTime());
        }

        int updated = userMapper.update(null, updateWrapper);
        if (roleChanged && updated != 1) {
            throw new IllegalStateException("用户角色更新失败");
        }
        return toVO(userMapper.selectById(userId));
    }

    @Override
    @Transactional
    @Audited(
            action = "user.status",
            targetType = "USER",
            scope = Audited.Scope.PLATFORM,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    public void updateStatus(
            @AuditTargetId Long userId,
            UserStatusDTO statusDTO) {
        User user = getExistingUser(userId);
        if (Integer.valueOf(0).equals(statusDTO.getStatus())
                && !Integer.valueOf(0).equals(user.getStatus())) {
            appendAuthSessionRevoke(userId, "ACCOUNT_DISABLED");
        }
        user.setStatus(statusDTO.getStatus());
        if (userMapper.updateById(user) != 1) {
            throw new IllegalStateException("用户状态更新失败");
        }
    }

    @Override
    @Transactional
    @Audited(
            action = "user.delete",
            targetType = "USER",
            scope = Audited.Scope.PLATFORM,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    public void deleteUser(@AuditTargetId Long userId) {
        requireDeletableUser(getExistingUser(userId));
        appendAuthSessionRevoke(userId, "ACCOUNT_DELETED");
        if (userMapper.deleteById(userId) != 1) {
            throw new IllegalStateException("用户删除失败");
        }
    }

    @Override
    @Transactional
    @Audited(
            action = "user.purge",
            targetType = "USER",
            scope = Audited.Scope.PLATFORM,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    public void purgeUser(@AuditTargetId Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 无效");
        }

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        requireDeletableUser(user);
        requireNoEntitlementHistory(userId);

        appendAuthSessionRevoke(userId, "ACCOUNT_PURGED");
        socialIdentityMapper.physicalDeleteByUserId(userId);
        if (jdbcTemplate.update(
                "DELETE FROM sys_user WHERE user_id = ?",
                userId) != 1) {
            throw new IllegalStateException("用户物理删除失败");
        }
    }
    @Override
    public UserConnectionPolicyVO getConnectionPolicy(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 无效");
        }
        User user = getExistingUser(userId);
        // 被禁用的用户不能继续创建新的 Portal Session。
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户当前不可用");
        }

        Integer configuredLimit = user.getMaxConnections();

        // 历史用户没有配置时，按照设计约定使用默认值 1。
        int effectiveLimit = configuredLimit == null || configuredLimit < 1 ? 1 : configuredLimit;

        UserConnectionPolicyVO policy = new UserConnectionPolicyVO();
        policy.setUserId(user.getUserId());
        policy.setMaxConnections(effectiveLimit);
        return policy;
    }

    @Override
    public UserStatsVO getUserStats() {
        long totalUsers = userMapper.selectCount(new QueryWrapper<User>());

        QueryWrapper<User> enabledWrapper = new QueryWrapper<>();
        enabledWrapper.eq("status", 1);
        long enabledUsers = userMapper.selectCount(enabledWrapper);

        QueryWrapper<User> disabledWrapper = new QueryWrapper<>();
        disabledWrapper.eq("status", 0);
        long disabledUsers = userMapper.selectCount(disabledWrapper);

        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("role", 1);
        long adminUsers = userMapper.selectCount(adminWrapper);

        UserStatsVO statsVO = new UserStatsVO();
        statsVO.setTotalUsers(totalUsers);
        statsVO.setEnabledUsers(enabledUsers);
        statsVO.setDisabledUsers(disabledUsers);
        statsVO.setAdminUsers(adminUsers);
        return statsVO;
    }

    private User getExistingUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private void appendAuthSessionRevoke(Long userId, String reason) {
        revokeOutboxAppender.append(userId, reason);
    }

    private void requireDeletableUser(User user) {
        if (user.getRole() == null || Integer.valueOf(0).equals(user.getRole())) {
            throw new IllegalArgumentException("超级管理员账号不能通过产品功能删除");
        }
    }

    private void requireNoEntitlementHistory(Long userId) {
        Integer historyExists = jdbcTemplate.queryForObject(
                "SELECT CASE WHEN "
                        + "EXISTS (SELECT 1 FROM t_duration_purchase WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM t_network_entitlement WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM t_entitlement_usage_log WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM t_entitlement_order WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM t_payment_record WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM t_refund_record WHERE user_id = ?) "
                        + "THEN 1 ELSE 0 END",
                Integer.class,
                userId,
                userId,
                userId,
                userId,
                userId,
                userId
        );
        if (Integer.valueOf(1).equals(historyExists)) {
            throw ApiStatusException.conflict(
                    "该用户存在权益或交易记录，只能停用或逻辑删除，不能永久删除"
            );
        }
    }

    private UserVO toVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    private String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }


}
