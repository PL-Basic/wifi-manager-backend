package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.client.UserRoleClient;
import com.plagod.audit.Audited;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.DefaultTenantMembershipRequest;
import com.plagod.dto.tenant.TenantCreateRequest;
import com.plagod.dto.tenant.TenantStatusRequest;
import com.plagod.dto.tenant.TenantUpdateRequest;
import com.plagod.dto.user.UserRoleBatchRequest;
import com.plagod.entity.SaasPlan;
import com.plagod.entity.Tenant;
import com.plagod.entity.TenantMember;
import com.plagod.entity.TenantSubscription;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SaasPlanMapper;
import com.plagod.mapper.TenantMapper;
import com.plagod.mapper.TenantMemberMapper;
import com.plagod.mapper.TenantSubscriptionMapper;
import com.plagod.service.TenantService;
import com.plagod.vo.tenant.SaasPlanVO;
import com.plagod.vo.tenant.MyTenantVO;
import com.plagod.vo.tenant.TenantMemberPageResult;
import com.plagod.vo.tenant.TenantMemberVO;
import com.plagod.vo.tenant.TenantPageResult;
import com.plagod.vo.tenant.TenantVO;
import com.plagod.vo.user.UserRoleSnapshotVO;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TenantServiceImpl implements TenantService {

    private static final String DEFAULT_TENANT_CODE = "default-tenant";
    private static final String ACTIVE = "ACTIVE";

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final SaasPlanMapper saasPlanMapper;
    private final TenantSubscriptionMapper tenantSubscriptionMapper;
    private final UserRoleClient userRoleClient;

    public TenantServiceImpl(TenantMapper tenantMapper,
                             TenantMemberMapper tenantMemberMapper,
                             SaasPlanMapper saasPlanMapper,
                             TenantSubscriptionMapper tenantSubscriptionMapper,
                             UserRoleClient userRoleClient) {
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.saasPlanMapper = saasPlanMapper;
        this.tenantSubscriptionMapper = tenantSubscriptionMapper;
        this.userRoleClient = userRoleClient;
    }

    @Override
    public TenantPageResult pageTenants(long current, long size, String keyword) {
        Page<Tenant> page = new Page<>(positivePage(current), pageSize(size));
        QueryWrapper<Tenant> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            wrapper.and(query -> query.like("tenant_code", normalized).or().like("name", normalized));
        }
        wrapper.orderByAsc("tenant_id");
        Page<Tenant> result = tenantMapper.selectPage(page, wrapper);

        TenantPageResult pageResult = new TenantPageResult();
        pageResult.setTotal(result.getTotal());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setRecords(result.getRecords().stream().map(this::toTenantVO).collect(Collectors.toList()));
        return pageResult;
    }

    @Override
    public TenantVO getTenant(String tenantId) {
        return toTenantVO(requiredTenant(parseId(tenantId, "租户ID")));
    }

    @Override
    public TenantMemberPageResult pageMembers(String tenantId, long current, long size) {
        Tenant tenant = requiredTenant(parseId(tenantId, "租户ID"));
        Page<TenantMember> page = new Page<>(positivePage(current), pageSize(size));
        QueryWrapper<TenantMember> wrapper = new QueryWrapper<TenantMember>()
                .eq("tenant_id", tenant.getTenantId())
                .orderByAsc("member_id");
        Page<TenantMember> result = tenantMemberMapper.selectPage(page, wrapper);

        List<Long> userIds = result.getRecords().stream()
                .map(TenantMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserRoleSnapshotVO> users = roleSnapshots(userIds);

        List<TenantMemberVO> records = result.getRecords().stream()
                .map(member -> toMemberVO(member, users.get(member.getUserId())))
                .collect(Collectors.toList());

        TenantMemberPageResult pageResult = new TenantMemberPageResult();
        pageResult.setTotal(result.getTotal());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setRecords(records);
        return pageResult;
    }

    @Override
    public List<SaasPlanVO> listPlans() {
        return saasPlanMapper.selectList(new QueryWrapper<SaasPlan>().orderByAsc("plan_id"))
                .stream()
                .map(this::toPlanVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<MyTenantVO> listMyTenants(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0");
        }
        List<TenantMember> memberships = tenantMemberMapper.selectList(
                new QueryWrapper<TenantMember>()
                        .eq("user_id", userId)
                        .eq("status", ACTIVE)
                        .orderByDesc("is_default")
                        .orderByAsc("member_id"));
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tenantIds = memberships.stream().map(TenantMember::getTenantId).collect(Collectors.toList());
        Map<Long, Tenant> tenants = tenantMapper.selectBatchIds(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getTenantId, tenant -> tenant));
        List<MyTenantVO> result = new ArrayList<>();
        for (TenantMember membership : memberships) {
            Tenant tenant = tenants.get(membership.getTenantId());
            if (tenant != null) {
                MyTenantVO vo = new MyTenantVO();
                vo.setTenantId(String.valueOf(tenant.getTenantId()));
                vo.setTenantCode(tenant.getTenantCode());
                vo.setTenantName(tenant.getName());
                vo.setTenantStatus(tenant.getStatus());
                vo.setTimezone(tenant.getTimezone());
                vo.setTenantRole(membership.getTenantRole());
                vo.setDefaultTenant(Integer.valueOf(1).equals(membership.getIsDefault()));
                vo.setTenantContextVersion(tenant.getContextVersion());
                vo.setMemberContextVersion(membership.getContextVersion());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional
    @Audited(action = "tenant.create")
    public TenantVO createTenant(TenantCreateRequest request, Long operatorId, Integer operatorRole) {
        requireSuperAdmin(operatorRole);
        String code = request.getTenantCode().trim();
        if (DEFAULT_TENANT_CODE.equals(code)) {
            throw ApiStatusException.conflict("系统默认租户编码不可占用");
        }
        String timezone = validateTimezone(request.getTimezone());
        if (operatorId == null || operatorId <= 0) {
            throw new IllegalArgumentException("可信操作者用户ID无效");
        }
        Long ownerUserId = operatorId;
        UserRoleSnapshotVO owner = requiredUser(ownerUserId);
        if (!Integer.valueOf(0).equals(owner.getRole()) || !Integer.valueOf(1).equals(owner.getStatus())) {
            throw ApiStatusException.conflict("租户所有者必须是有效的超级管理员");
        }

        Tenant tenant = new Tenant();
        tenant.setTenantCode(code);
        tenant.setName(request.getName().trim());
        tenant.setStatus(ACTIVE);
        tenant.setTimezone(timezone);
        tenant.setOwnerUserId(ownerUserId);
        tenant.setContextVersion(1L);
        tenant.setVersion(0);
        tenant.setDelFlag(0);

        try {
            tenantMapper.insert(tenant);
            tenantMemberMapper.insert(newMember(tenant.getTenantId(), ownerUserId, "TENANT_OWNER", false));
        } catch (DuplicateKeyException exception) {
            throw ApiStatusException.conflict("租户编码或成员关系已存在");
        }
        return toTenantVO(tenant);
    }

    @Override
    @Transactional
    @Audited(action = "tenant.update")
    public TenantVO updateTenant(String tenantId, TenantUpdateRequest request, Integer operatorRole) {
        requireSuperAdmin(operatorRole);
        Tenant tenant = requiredTenant(parseId(tenantId, "租户ID"));
        String timezone = validateTimezone(request.getTimezone());
        int updated = tenantMapper.update(null, new UpdateWrapper<Tenant>()
                .eq("tenant_id", tenant.getTenantId())
                .eq("version", tenant.getVersion())
                .set("name", request.getName().trim())
                .set("timezone", timezone)
                .setSql("context_version = context_version + 1")
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw ApiStatusException.conflict("租户信息已变化，请刷新后重试");
        }
        return toTenantVO(requiredTenant(tenant.getTenantId()));
    }

    @Override
    @Transactional
    @Audited(action = "tenant.status")
    public TenantVO updateStatus(String tenantId, TenantStatusRequest request, Integer operatorRole) {
        requireSuperAdmin(operatorRole);
        Tenant tenant = requiredTenant(parseId(tenantId, "租户ID"));
        String targetStatus = request.getStatus();
        if (DEFAULT_TENANT_CODE.equals(tenant.getTenantCode()) && !ACTIVE.equals(targetStatus)) {
            throw ApiStatusException.conflict("默认兼容租户在首版迁移期间不能停用");
        }
        if (Objects.equals(tenant.getStatus(), targetStatus)) {
            return toTenantVO(tenant);
        }
        int updated = tenantMapper.update(null, new UpdateWrapper<Tenant>()
                .eq("tenant_id", tenant.getTenantId())
                .eq("version", tenant.getVersion())
                .set("status", targetStatus)
                .setSql("context_version = context_version + 1")
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw ApiStatusException.conflict("租户状态已变化，请刷新后重试");
        }
        return toTenantVO(requiredTenant(tenant.getTenantId()));
    }

    @Override
    @Transactional
    public void ensureDefaultMembership(DefaultTenantMembershipRequest request) {
        UserRoleSnapshotVO user = requiredUser(request.getUserId());
        if (!Objects.equals(user.getRole(), request.getRole())) {
            throw ApiStatusException.conflict("注册事件角色与当前用户角色不一致");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw ApiStatusException.conflict("停用用户不能创建默认租户成员关系");
        }
        Tenant tenant = tenantMapper.selectOne(new QueryWrapper<Tenant>().eq("tenant_code", DEFAULT_TENANT_CODE));
        if (tenant == null || !ACTIVE.equals(tenant.getStatus())) {
            throw ApiStatusException.serviceUnavailable("默认租户当前不可用");
        }

        TenantMember existing = tenantMemberMapper.selectOne(new QueryWrapper<TenantMember>()
                .eq("tenant_id", tenant.getTenantId())
                .eq("user_id", request.getUserId()));
        String tenantRole = tenantRoleForGlobalRole(user.getRole());
        if (existing != null) {
            if (ACTIVE.equals(existing.getStatus())
                    && Integer.valueOf(1).equals(existing.getIsDefault())
                    && tenantRole.equals(existing.getTenantRole())) {
                return;
            }
            int updated = tenantMemberMapper.update(null, new UpdateWrapper<TenantMember>()
                    .eq("member_id", existing.getMemberId())
                    .eq("version", existing.getVersion())
                    .set("tenant_role", tenantRole)
                    .set("status", ACTIVE)
                    .set("is_default", 1)
                    .setSql("context_version = context_version + 1")
                    .setSql("version = version + 1"));
            if (updated != 1) {
                throw ApiStatusException.conflict("默认成员关系已变化，请重试");
            }
            return;
        }

        try {
            tenantMemberMapper.insert(newMember(tenant.getTenantId(), request.getUserId(), tenantRole, true));
        } catch (DuplicateKeyException exception) {
            TenantMember raced = tenantMemberMapper.selectOne(new QueryWrapper<TenantMember>()
                    .eq("tenant_id", tenant.getTenantId())
                    .eq("user_id", request.getUserId()));
            if (raced == null
                    || !ACTIVE.equals(raced.getStatus())
                    || !Integer.valueOf(1).equals(raced.getIsDefault())
                    || !tenantRole.equals(raced.getTenantRole())) {
                throw ApiStatusException.conflict("用户已存在其他有效默认租户或成员关系冲突");
            }
        }
    }

    private TenantMember newMember(Long tenantId, Long userId, String role, boolean defaultTenant) {
        TenantMember member = new TenantMember();
        member.setTenantId(tenantId);
        member.setUserId(userId);
        member.setTenantRole(role);
        member.setStatus(ACTIVE);
        member.setIsDefault(defaultTenant ? 1 : 0);
        member.setContextVersion(1L);
        member.setJoinTime(LocalDateTime.now());
        member.setVersion(0);
        return member;
    }

    private Tenant requiredTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw ApiStatusException.notFound("租户不存在");
        }
        return tenant;
    }

    private UserRoleSnapshotVO requiredUser(Long userId) {
        UserRoleSnapshotVO user = roleSnapshots(Collections.singletonList(userId)).get(userId);
        if (user == null) {
            throw ApiStatusException.notFound("用户不存在");
        }
        return user;
    }

    private Map<Long, UserRoleSnapshotVO> roleSnapshots(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        UserRoleBatchRequest request = new UserRoleBatchRequest();
        request.setUserIds(userIds);
        ApiResponse<List<UserRoleSnapshotVO>> response;
        try {
            response = userRoleClient.getRoleSnapshots(request);
        } catch (RuntimeException exception) {
            throw ApiStatusException.serviceUnavailable("用户角色服务暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw ApiStatusException.serviceUnavailable("用户角色服务返回无效结果");
        }
        Map<Long, UserRoleSnapshotVO> result = new LinkedHashMap<>();
        for (UserRoleSnapshotVO snapshot : response.getData()) {
            if (snapshot != null && StringUtils.hasText(snapshot.getUserId())) {
                result.put(parseId(snapshot.getUserId(), "用户ID"), snapshot);
            }
        }
        return result;
    }

    private TenantVO toTenantVO(Tenant tenant) {
        TenantVO vo = new TenantVO();
        BeanUtils.copyProperties(tenant, vo, "tenantId", "ownerUserId");
        vo.setTenantId(String.valueOf(tenant.getTenantId()));
        vo.setOwnerUserId(String.valueOf(tenant.getOwnerUserId()));
        vo.setMemberCount(tenantMemberMapper.selectCount(new QueryWrapper<TenantMember>()
                .eq("tenant_id", tenant.getTenantId())
                .eq("status", ACTIVE)));
        TenantSubscription subscription = tenantSubscriptionMapper.selectOne(
                new QueryWrapper<TenantSubscription>()
                        .eq("tenant_id", tenant.getTenantId())
                        .in("status", Arrays.asList("TRIAL", ACTIVE))
                        .last("limit 1"));
        vo.setSubscriptionStatus(subscription == null ? "NO_ACTIVE_SUBSCRIPTION" : subscription.getStatus());
        return vo;
    }

    private TenantMemberVO toMemberVO(TenantMember member, UserRoleSnapshotVO user) {
        TenantMemberVO vo = new TenantMemberVO();
        vo.setMemberId(String.valueOf(member.getMemberId()));
        vo.setTenantId(String.valueOf(member.getTenantId()));
        vo.setUserId(String.valueOf(member.getUserId()));
        vo.setTenantRole(member.getTenantRole());
        vo.setStatus(member.getStatus());
        vo.setDefaultTenant(Integer.valueOf(1).equals(member.getIsDefault()));
        vo.setContextVersion(member.getContextVersion());
        vo.setJoinTime(member.getJoinTime());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setGlobalRole(user.getRole());
            vo.setGlobalStatus(user.getStatus());
        }
        return vo;
    }

    private SaasPlanVO toPlanVO(SaasPlan plan) {
        SaasPlanVO vo = new SaasPlanVO();
        BeanUtils.copyProperties(plan, vo, "planId", "currentPublishedVersionId");
        vo.setPlanId(String.valueOf(plan.getPlanId()));
        vo.setCurrentPublishedVersionId(plan.getCurrentPublishedVersionId() == null
                ? null : String.valueOf(plan.getCurrentPublishedVersionId()));
        return vo;
    }

    private String tenantRoleForGlobalRole(Integer role) {
        if (Integer.valueOf(0).equals(role)) {
            return "TENANT_OWNER";
        }
        if (Integer.valueOf(1).equals(role)) {
            return "TENANT_ADMIN";
        }
        if (Integer.valueOf(2).equals(role)) {
            return "MEMBER";
        }
        throw ApiStatusException.conflict("用户全局角色无效");
    }

    private void requireSuperAdmin(Integer operatorRole) {
        if (!Integer.valueOf(0).equals(operatorRole)) {
            throw ApiStatusException.forbidden("仅超级管理员可以执行平台租户操作");
        }
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

    private String validateTimezone(String timezone) {
        String value = timezone == null ? "" : timezone.trim();
        try {
            ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("时区必须是有效的IANA时区");
        }
        return value;
    }

    private long positivePage(long current) {
        if (current < 1) {
            throw new IllegalArgumentException("页码必须大于0");
        }
        return current;
    }

    private long pageSize(long size) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("每页数量必须在1到100之间");
        }
        return size;
    }
}
