package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.client.UserRoleClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.dto.user.UserRoleBatchRequest;
import com.plagod.entity.PlatformStaff;
import com.plagod.entity.Tenant;
import com.plagod.entity.TenantMember;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.PlatformStaffMapper;
import com.plagod.mapper.TenantMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.service.TenantContextService;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.tenant.TenantContextValidationVO;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class TenantContextServiceImpl implements TenantContextService {

    private static final String CONTEXT_TENANT = "TENANT";
    private static final String CONTEXT_PLATFORM = "PLATFORM";
    private static final String CONTEXT_PLATFORM_TENANT = "PLATFORM_TENANT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final PlatformStaffMapper platformStaffMapper;
    private final UserRoleClient userRoleClient;

    public TenantContextServiceImpl(TenantMapper tenantMapper,
                                    TenantMemberMapper tenantMemberMapper,
                                    PlatformStaffMapper platformStaffMapper,
                                    UserRoleClient userRoleClient) {
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.platformStaffMapper = platformStaffMapper;
        this.userRoleClient = userRoleClient;
    }

    @Override
    public TenantContextVO resolve(TenantContextResolveRequest request) {
        try {
            return doResolve(request);
        } catch (DataAccessException exception) {
            throw ApiStatusException.serviceUnavailable("租户上下文存储暂时不可用");
        }
    }

    @Override
    public TenantContextValidationVO validate(TenantContextValidationRequest request) {
        try {
            TenantContextVO context = doValidate(request);
            TenantContextValidationVO result = new TenantContextValidationVO();
            result.setAllowed(true);
            result.setMessage("租户上下文有效");
            result.setContext(context);
            return result;
        } catch (DataAccessException exception) {
            throw ApiStatusException.serviceUnavailable("租户上下文存储暂时不可用");
        }
    }

    private TenantContextVO doResolve(TenantContextResolveRequest request) {
        UserRoleSnapshotVO user = requiredCurrentUser(request.getUserId(), request.getGlobalRole());
        Long userId = parseId(user.getUserId(), "用户ID");
        List<String> authorities = activeAuthorities(userId);
        String requestedType = normalize(request.getContextType());

        if (Integer.valueOf(0).equals(user.getRole())) {
            if (requestedType == null || CONTEXT_PLATFORM.equals(requestedType)) {
                if (StringUtils.hasText(request.getTenantId())) {
                    throw new IllegalArgumentException("平台上下文不能携带租户ID");
                }
                return platformContext(authorities);
            }
            if (CONTEXT_PLATFORM_TENANT.equals(requestedType)) {
                return platformTenantContext(requiredTenantId(request.getTenantId()), authorities);
            }
            throw ApiStatusException.forbidden("超级管理员必须通过平台代管上下文进入租户");
        }

        if (CONTEXT_PLATFORM.equals(requestedType) || CONTEXT_PLATFORM_TENANT.equals(requestedType)) {
            throw ApiStatusException.forbidden("当前用户无权进入平台上下文");
        }
        if (requestedType != null && !CONTEXT_TENANT.equals(requestedType)) {
            throw new IllegalArgumentException("上下文类型不受支持");
        }
        return memberTenantContext(userId, optionalTenantId(request.getTenantId()), authorities);
    }

    private TenantContextVO doValidate(TenantContextValidationRequest request) {
        UserRoleSnapshotVO user = requiredCurrentUser(request.getUserId(), request.getGlobalRole());
        Long userId = parseId(user.getUserId(), "用户ID");
        List<String> authorities = activeAuthorities(userId);
        TenantContextVO current;

        if (CONTEXT_PLATFORM.equals(request.getContextType())) {
            requireSuperAdmin(user);
            current = platformContext(authorities);
        } else if (CONTEXT_PLATFORM_TENANT.equals(request.getContextType())) {
            requireSuperAdmin(user);
            current = platformTenantContext(requiredTenantId(request.getTenantId()), authorities);
        } else if (CONTEXT_TENANT.equals(request.getContextType())) {
            if (Integer.valueOf(0).equals(user.getRole())) {
                throw ApiStatusException.forbidden("超级管理员租户访问必须使用平台代管上下文");
            }
            current = memberTenantContext(userId, requiredTenantId(request.getTenantId()), authorities);
        } else {
            throw new IllegalArgumentException("上下文类型不受支持");
        }

        if (Boolean.TRUE.equals(request.getWriteRequest())) {
            validateWriteSnapshot(request, current);
        }
        return current;
    }

    private TenantContextVO platformContext(List<String> authorities) {
        TenantContextVO context = new TenantContextVO();
        context.setContextType(CONTEXT_PLATFORM);
        context.setWritable(true);
        context.setAuthorities(new ArrayList<>(authorities));
        return context;
    }

    private TenantContextVO platformTenantContext(Long tenantId, List<String> authorities) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw ApiStatusException.notFound("租户不存在");
        }
        ensureTenantVersion(tenant);
        ensureTenantStatus(tenant);
        TenantContextVO context = tenantContext(tenant, authorities);
        context.setContextType(CONTEXT_PLATFORM_TENANT);
        return context;
    }

    private TenantContextVO memberTenantContext(Long userId,
                                                Long requestedTenantId,
                                                List<String> authorities) {
        QueryWrapper<TenantMember> memberQuery = new QueryWrapper<TenantMember>()
                .eq("user_id", userId)
                .eq("status", STATUS_ACTIVE);
        if (requestedTenantId == null) {
            memberQuery.eq("is_default", 1).last("limit 1");
        } else {
            memberQuery.eq("tenant_id", requestedTenantId).last("limit 1");
        }
        TenantMember member = tenantMemberMapper.selectOne(memberQuery);
        if (member == null) {
            throw ApiStatusException.forbidden(requestedTenantId == null
                    ? "用户没有有效的默认租户成员关系"
                    : "用户不是目标租户的有效成员");
        }
        if (!StringUtils.hasText(member.getTenantRole()) || member.getContextVersion() == null) {
            throw ApiStatusException.serviceUnavailable("租户成员上下文数据不完整");
        }

        Tenant tenant = tenantMapper.selectById(member.getTenantId());
        if (tenant == null) {
            throw ApiStatusException.serviceUnavailable("租户成员关系指向不存在的租户");
        }
        ensureTenantVersion(tenant);
        ensureTenantStatus(tenant);

        TenantContextVO context = tenantContext(tenant, authorities);
        context.setContextType(CONTEXT_TENANT);
        context.setTenantRole(member.getTenantRole());
        context.setMemberContextVersion(member.getContextVersion());
        return context;
    }

    private TenantContextVO tenantContext(Tenant tenant, List<String> authorities) {
        TenantContextVO context = new TenantContextVO();
        context.setTenantId(String.valueOf(tenant.getTenantId()));
        context.setTenantCode(tenant.getTenantCode());
        context.setTenantName(tenant.getName());
        context.setContextVersion(tenant.getContextVersion());
        context.setTenantStatus(tenant.getStatus());
        context.setWritable(STATUS_ACTIVE.equals(tenant.getStatus()));
        context.setAuthorities(new ArrayList<>(authorities));
        return context;
    }

    private void validateWriteSnapshot(TenantContextValidationRequest request, TenantContextVO current) {
        if (!Boolean.TRUE.equals(current.getWritable())) {
            throw ApiStatusException.forbidden("当前租户状态不允许写操作");
        }
        if (!Objects.equals(normalize(request.getTenantId()), current.getTenantId())
                || !Objects.equals(normalize(request.getTenantCode()), current.getTenantCode())
                || !Objects.equals(normalize(request.getTenantRole()), current.getTenantRole())
                || !Objects.equals(request.getContextVersion(), current.getContextVersion())
                || !Objects.equals(request.getMemberContextVersion(), current.getMemberContextVersion())
                || !normalizedAuthorities(request.getAuthorities())
                .equals(normalizedAuthorities(current.getAuthorities()))) {
            throw staleContext();
        }
    }

    private UserRoleSnapshotVO requiredCurrentUser(String requestedUserId, Integer requestedGlobalRole) {
        Long userId = parseId(requestedUserId, "用户ID");
        UserRoleBatchRequest roleRequest = new UserRoleBatchRequest();
        roleRequest.setUserIds(Collections.singletonList(userId));

        ApiResponse<List<UserRoleSnapshotVO>> response;
        try {
            response = userRoleClient.getRoleSnapshots(roleRequest);
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable("用户角色服务暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw ApiStatusException.serviceUnavailable("用户角色服务返回无效结果");
        }

        UserRoleSnapshotVO user = response.getData().stream()
                .filter(Objects::nonNull)
                .filter(snapshot -> requestedUserId.equals(snapshot.getUserId()))
                .findFirst()
                .orElseThrow(() -> ApiStatusException.notFound("用户不存在"));
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw ApiStatusException.forbidden("用户当前不可用");
        }
        if (!Objects.equals(requestedGlobalRole, user.getRole())) {
            throw staleContext();
        }
        return user;
    }

    private List<String> activeAuthorities(Long userId) {
        List<PlatformStaff> grants = platformStaffMapper.selectList(
                new QueryWrapper<PlatformStaff>()
                        .eq("user_id", userId)
                        .eq("status", STATUS_ACTIVE)
                        .orderByAsc("authority"));
        if (grants == null || grants.isEmpty()) {
            return Collections.emptyList();
        }
        return grants.stream()
                .filter(Objects::nonNull)
                .filter(grant -> STATUS_ACTIVE.equals(grant.getStatus()))
                .map(PlatformStaff::getAuthority)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private void ensureTenantVersion(Tenant tenant) {
        if (tenant.getContextVersion() == null || !StringUtils.hasText(tenant.getTenantCode())) {
            throw ApiStatusException.serviceUnavailable("租户上下文数据不完整");
        }
    }

    private void ensureTenantStatus(Tenant tenant) {
        if (!STATUS_ACTIVE.equals(tenant.getStatus())
                && !STATUS_DISABLED.equals(tenant.getStatus())) {
            throw ApiStatusException.serviceUnavailable("租户状态不受支持");
        }
    }

    private void requireSuperAdmin(UserRoleSnapshotVO user) {
        if (!Integer.valueOf(0).equals(user.getRole())) {
            throw ApiStatusException.forbidden("仅超级管理员可以使用平台上下文");
        }
    }

    private Long requiredTenantId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        return parseId(value, "租户ID");
    }

    private Long optionalTenantId(String value) {
        return StringUtils.hasText(value) ? parseId(value, "租户ID") : null;
    }

    private Long parseId(String value, String label) {
        if (!StringUtils.hasText(value) || !value.matches("^[1-9]\\d*$")) {
            throw new IllegalArgumentException(label + "必须是大于0的整数");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "超出64位整数范围");
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Set<String> normalizedAuthorities(List<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptySet();
        }
        return authorities.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private ApiStatusException staleContext() {
        return new ApiStatusException(401, 401, "上下文已变化，请刷新会话后重试");
    }
}
