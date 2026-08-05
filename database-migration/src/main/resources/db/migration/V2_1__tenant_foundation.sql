alter table sys_user
    add column super_admin_guard tinyint
        generated always as (case when role = 0 and del_flag = 0 then 1 else null end) stored
        comment '确保未删除超级管理员全局唯一',
    add unique key uk_sys_user_super_admin_guard (super_admin_guard);

create table t_tenant (
    tenant_id bigint not null auto_increment comment '租户ID',
    tenant_code varchar(64) not null comment '不可变租户编码',
    name varchar(128) not null comment '租户名称',
    status varchar(16) not null default 'ACTIVE' comment 'ACTIVE或DISABLED',
    timezone varchar(64) not null default 'Asia/Shanghai' comment 'IANA时区',
    owner_user_id bigint not null comment '租户所有者用户ID',
    context_version bigint not null default 1 comment '租户上下文版本',
    version int not null default 0 comment '乐观锁版本',
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    del_flag tinyint not null default 0,
    primary key (tenant_id),
    unique key uk_tenant_code (tenant_code),
    key idx_tenant_status (status, del_flag),
    check (status in ('ACTIVE', 'DISABLED'))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='租户';

create table t_tenant_member (
    member_id bigint not null auto_increment comment '成员关系ID',
    tenant_id bigint not null comment '租户ID',
    user_id bigint not null comment '全局用户ID',
    tenant_role varchar(24) not null comment 'TENANT_OWNER/TENANT_ADMIN/MEMBER',
    status varchar(16) not null default 'ACTIVE' comment 'ACTIVE或REMOVED',
    is_default tinyint not null default 0 comment '是否为用户默认租户',
    active_default_user_guard bigint
        generated always as (
            case when is_default = 1 and status = 'ACTIVE' then user_id else null end
        ) stored comment '每个用户最多一个有效默认租户',
    context_version bigint not null default 1 comment '成员上下文版本',
    join_time datetime not null default current_timestamp,
    version int not null default 0 comment '乐观锁版本',
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (member_id),
    unique key uk_tenant_member (tenant_id, user_id),
    unique key uk_tenant_member_active_default (active_default_user_guard),
    key idx_tenant_member_user (user_id, status),
    key idx_tenant_member_tenant (tenant_id, status),
    check (tenant_role in ('TENANT_OWNER', 'TENANT_ADMIN', 'MEMBER')),
    check (status in ('ACTIVE', 'REMOVED')),
    check (is_default in (0, 1))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='租户成员';

create table t_platform_staff (
    staff_id bigint not null auto_increment,
    user_id bigint not null,
    authority varchar(64) not null comment '平台员工能力',
    status varchar(16) not null default 'ACTIVE',
    granted_by bigint not null,
    reason varchar(255) default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (staff_id),
    unique key uk_platform_staff_authority (user_id, authority),
    key idx_platform_staff_status (status),
    check (status in ('ACTIVE', 'DISABLED'))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='平台员工授权';

create table t_saas_plan (
    plan_id bigint not null auto_increment,
    plan_code varchar(64) not null,
    name varchar(128) not null,
    status varchar(16) not null default 'DRAFT',
    current_published_version_id bigint default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (plan_id),
    unique key uk_saas_plan_code (plan_code),
    check (status in ('DRAFT', 'ACTIVE', 'DISABLED'))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='SaaS套餐身份';

create table t_saas_plan_version (
    plan_version_id bigint not null auto_increment,
    plan_id bigint not null,
    version_no int not null,
    member_limit bigint not null,
    device_limit bigint not null,
    concurrent_session_limit bigint not null,
    daily_minutes_limit bigint not null,
    status varchar(16) not null default 'DRAFT',
    publish_time datetime default null,
    create_time datetime not null default current_timestamp,
    primary key (plan_version_id),
    unique key uk_saas_plan_version (plan_id, version_no),
    key idx_saas_plan_version_status (plan_id, status),
    check (member_limit >= 0 and device_limit >= 0 and concurrent_session_limit >= 0 and daily_minutes_limit >= 0),
    check (status in ('DRAFT', 'PUBLISHED', 'RETIRED'))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='不可变SaaS套餐版本';

create table t_tenant_subscription (
    subscription_id bigint not null auto_increment,
    tenant_id bigint not null,
    plan_version_id bigint not null,
    status varchar(16) not null,
    start_time datetime not null,
    end_time datetime not null,
    source varchar(24) not null,
    active_subscription_tenant_guard bigint
        generated always as (
            case when status in ('TRIAL', 'ACTIVE') then tenant_id else null end
        ) stored comment '每个租户最多一个有效订阅',
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (subscription_id),
    unique key uk_tenant_active_subscription (active_subscription_tenant_guard),
    key idx_tenant_subscription_history (tenant_id, create_time),
    check (status in ('TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    check (end_time > start_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='租户订阅';

create table t_tenant_quota (
    tenant_quota_id bigint not null auto_increment,
    tenant_id bigint not null,
    quota_type varchar(32) not null,
    limit_value bigint not null,
    used_value bigint not null default 0,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (tenant_quota_id),
    unique key uk_tenant_quota (tenant_id, quota_type),
    check (limit_value >= 0 and used_value >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='租户配额';

create table t_tenant_usage_daily (
    usage_id bigint not null auto_increment,
    tenant_id bigint not null,
    usage_date date not null,
    usage_type varchar(32) not null,
    used_value bigint not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (usage_id),
    unique key uk_tenant_usage_daily (tenant_id, usage_date, usage_type),
    check (used_value >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='租户每日用量';

create table t_default_tenant_membership_outbox (
    outbox_id bigint not null auto_increment,
    event_id varchar(64) not null,
    user_id bigint not null,
    role int not null,
    status varchar(16) not null default 'PENDING',
    retry_count int not null default 0,
    next_retry_time datetime not null default current_timestamp,
    last_error varchar(500) default null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    primary key (outbox_id),
    unique key uk_default_membership_event (event_id),
    unique key uk_default_membership_user (user_id),
    key idx_default_membership_due (status, next_retry_time),
    check (role in (0, 1, 2)),
    check (status in ('PENDING', 'RETRY', 'SUCCEEDED'))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='默认租户成员Outbox';

insert into t_tenant (
    tenant_code, name, status, timezone, owner_user_id, context_version, version, del_flag
) values (
    'default-tenant',
    '默认兼容租户',
    'ACTIVE',
    'Asia/Shanghai',
    (select min(user_id) from sys_user where role = 0 and del_flag = 0),
    1,
    0,
    0
);

insert into t_tenant_member (
    tenant_id, user_id, tenant_role, status, is_default, context_version, join_time, version
)
select tenant.tenant_id,
       user.user_id,
       case user.role
           when 0 then 'TENANT_OWNER'
           when 1 then 'TENANT_ADMIN'
           else 'MEMBER'
       end,
       'ACTIVE',
       1,
       1,
       current_timestamp,
       0
from sys_user user
join t_tenant tenant on tenant.tenant_code = 'default-tenant'
where user.del_flag = 0
  and user.status = 1;
