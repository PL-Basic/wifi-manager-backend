# Wifi Manager Demo 1.3-A 数据库四方审计

> 2026-08-11 边界修正：本文保留 A 批当时的只读事实。文中的 `V2_4_2` 是从未执行的原候选名，现已整体后移为 `V2_10`；当前五层充分性门禁和阶段边界以 1.3 计划及三域迁移回放记录为准。

## 状态与边界

- 审计日期：2026-08-10。
- 审计范围：本文主体记录 Demo 1.3-A 执行时快照；当时未执行 Flyway migrate、repair、clean 或 `V2_3_2`，未执行 DDL、UPDATE、DELETE、回填或约束收紧。后续 1.3-B 状态以 [Demo 1.3-B P-3B contract 实施记录](demo-1.3-b-p3b-contract.md) 为准。
- 数据库目标：`xwh:3306/wifi`，MySQL `8.4.8`，server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- 连接证明：JDBC 连接的 `database()=wifi`、`@@transaction_read_only=1`；审计程序只执行固定 SELECT，并在结束时 rollback。
- 仓库目标：后端 `demo/stage-01-foundation`，HEAD `66647c0f3`。
- 保护边界：P-3B 的 82 个 tracked dirty 文件和 6 个 untracked 文件保持原归属；两份未跟踪迁移只读，不修改、不暂存。

## 数据库与仓库总数

- 仓库迁移文件：13 个，`V1_1` 至 `V2_3_2`。
- 实时 BASE TABLE：44 张，其中业务表 43 张，另有 `flyway_schema_history`。
- 仓库迁移创建的业务表集合：43 张，与实时业务表集合完全一致；两边独有对象均为 0。
- 实时 VIEW、TRIGGER、EVENT、ROUTINE、FOREIGN KEY：均为 0。
- `@TableName` Entity：38 个，全部能映射到实时表，无悬空 Entity。
- Java Mapper：42 个，其中 BaseMapper 39 个、原生 Mapper 3 个。
- Mapper 覆盖：40 张业务表；`sys_user` 同时由 Auth/User 两个 Mapper 使用，`MonitorHealthMapper` 是不对应业务表的只读健康查询。
- Mapper XML：17 个，namespace 缺失或找不到 Java Mapper 的数量为 0。
- 无 Entity 的 5 张表：`t_session_user_guard`、`t_client_access_guard`、`t_saas_plan_version`、`t_tenant_quota`、`t_tenant_usage_daily`。
- 前两张 guard 表由原生锁行 Mapper 有意维护，不是缺口；后三张 SaaS 表缺 Entity 和 Mapper，归 1.3-D，不在 1.3-A 提前实现。

## Flyway 历史与 checksum

1.3-A 执行时的实时历史：

- installed rank 1：baseline `1.6`，`success=1`，无 SQL checksum。
- installed rank 2：`V1_7__reward_order_metadata.sql`，checksum `-1448715625`，`success=1`。
- installed rank 3：`V2_1__tenant_foundation.sql`，checksum `-1139133663`，`success=1`。
- installed rank 4：`V2_2__auth_session_and_tenant_context.sql`，checksum `894503329`，`success=1`。
- installed rank 5：`V2_2_1__tenant_user_entitlement_expand.sql`，checksum `1696118597`，`success=1`。
- installed rank 6：`V2_2_2__tenant_user_entitlement_contract.sql`，checksum `1969643131`，`success=1`。
- installed rank 7：`V2_3_1__tenant_device_expand.sql`，checksum `-565722391`，`success=1`。

使用项目实际 Flyway `6.4.4` 的 `ChecksumCalculator` 对仓库文件重算，以上 6 个 SQL 历史 checksum 全部一致。

`V1_1` 至 `V1_6` 在该库中由 baseline `1.6` 接管，没有逐文件历史行，不能写成“本库逐个执行成功”；但它们定义空库初始化和 baseline 实体结构，继续按已发布历史迁移冻结，任何修正必须使用更高版本。

`V2_3_2__tenant_device_contract.sql` 的仓库 checksum 为 `1407250874`，在 1.3-A 执行时不在实时 Flyway 历史中，当时确认尚未执行。

## 冻结迁移

以下文件禁止修改、重命名或复用版本号：

- `V1_1__user_schema.sql`
- `V1_2__auth_schema.sql`
- `V1_3__entitlement_schema.sql`
- `V1_4__user_operation_schema.sql`
- `V1_5__device_schema.sql`
- `V1_6__monitor_schema.sql`
- `V1_7__reward_order_metadata.sql`
- `V2_1__tenant_foundation.sql`
- `V2_2__auth_session_and_tenant_context.sql`
- `V2_2_1__tenant_user_entitlement_expand.sql`
- `V2_2_2__tenant_user_entitlement_contract.sql`
- `V2_3_1__tenant_device_expand.sql`

`V2_3_2__tenant_device_contract.sql` 在 1.3-A 执行时是已存在的未执行候选文件；A 批只冻结其当前内容/checksum，不执行、不修改。后续即使发现设计缺口，也必须由更高版本修正，不能改写已经冻结的 P-3B 文件。

## 候选版本顺序

当前仓库没有高于 `V2_3_2` 的迁移，候选顺序保持：

1. `V2_3_2__tenant_device_contract.sql`
2. `V2_4_1__tenant_monitor_expand.sql`
3. `V2_4_2__tenant_monitor_contract.sql`
4. `V2_5__saas_quota_and_assignment_schema.sql`
5. `V2_5_1__marketplace_demo_schema.sql`
6. `V2_6__announcement_schema.sql`
7. `V2_7__ai_review_schema.sql`
8. `V2_8__support_ticket_schema.sql`

该顺序只冻结版本占位，不创建任何 1.3-B 或后续迁移文件。

## 迁移到表到 Entity 到 Mapper/XML

### V1.1 用户

- `sys_user` -> `User` -> Auth `UserMapper` + XML；User `UserMapper`。
- `t_social_identity` -> `SocialIdentity` -> User `SocialIdentityMapper`。

### V1.2 认证

- `t_verify_code` -> `VerifyCode` -> Auth `VerifyCodeMapper` + XML。
- `t_login_fail_record` -> `LoginFailRecord` -> Auth `LoginFailRecordMapper` + XML。
- `t_oauth_state` -> `OAuthStateRecord` -> Auth `OAuthStateMapper`。

### V1.3 个人权益

- `t_duration_purchase` -> `DurationPurchase` -> User `DurationPurchaseMapper`。
- `t_network_entitlement` -> `NetworkEntitlement` -> User `NetworkEntitlementMapper` + XML。
- `t_entitlement_usage_log` -> `EntitlementUsageLog` -> User `EntitlementUsageLogMapper`。
- `t_entitlement_order` -> `EntitlementOrder` -> User `EntitlementOrderMapper` + XML。
- `t_payment_record` -> `PaymentRecord` -> User `PaymentRecordMapper` + XML。
- `t_refund_record` -> `RefundRecord` -> User `RefundRecordMapper` + XML。
- `t_trade_status_log` -> `TradeStatusLog` -> User `TradeStatusLogMapper` + XML。

### V1.4 账号操作

- `t_user_operation_request` -> `UserOperationRequest` -> User `UserOperationRequestMapper`。

### V1.5 设备

- `t_esp32_node` -> `Esp32Node` -> Device `Esp32NodeMapper` + XML。
- `t_session` -> `SessionRecord` -> Device `SessionRecordMapper`。
- `t_mac_blacklist` -> `MacBlacklist` -> Device `MacBlacklistMapper`。
- `t_traffic_log` -> `TrafficLog` -> Device `TrafficLogMapper` + XML。
- `t_client_signal` -> `ClientSignalRecord` -> Device `ClientSignalMapper` + XML。
- `t_session_user_guard` -> 无 Entity（有意）-> Device `SessionUserGuardMapper` 原生锁行 SQL。
- `t_client_access_guard` -> 无 Entity（有意）-> Device `ClientAccessGuardMapper` 原生锁行 SQL。
- `t_device_command` -> `DeviceCommandRecord` -> Device `DeviceCommandRecordMapper`。
- `t_device_wifi_config` -> `DeviceWifiConfigRecord` -> Device `DeviceWifiConfigRecordMapper`。

`V2_3_1` 已为以上九表增加可空 `tenant_id` 和兼容索引；当前 7 个 Entity 与 2 个原生 Mapper 均已存在 tenant-aware 候选实现。`V2_3_2` 仅负责 contract，不创建新表。

### V1.6 监控

- `t_access_rule` -> `AccessRule` -> Monitor `AccessRuleMapper`。
- `t_client_location` -> `ClientLocation` -> Monitor `ClientLocationMapper` + XML。
- `t_location_authorization` -> `LocationAuthorization` -> Monitor `LocationAuthorizationMapper`。
- `t_audit_log` -> `AuditLog` -> Common MyBatis `AuditLogMapper`。
- `t_alert_event` -> `AlertEvent` -> Monitor `AlertEventMapper` + XML。
- `t_rule_hit` -> `RuleHitRecord` -> Monitor `RuleHitRecordMapper` + XML。
- `t_geofence` -> `Geofence` -> Monitor `GeofenceMapper`。
- `t_geofence_state` -> `GeofenceState` -> Monitor `GeofenceStateMapper`。
- `t_geofence_event` -> `GeofenceEvent` -> Monitor `GeofenceEventMapper` + XML。

实时九表均没有 `tenant_id`，确认 P-3C expand 尚未发生。当前行数依次为：

- `t_access_rule=1`
- `t_client_location=0`
- `t_location_authorization=2`
- `t_audit_log=145`
- `t_alert_event=0`
- `t_rule_hit=0`
- `t_geofence=0`
- `t_geofence_state=0`
- `t_geofence_event=0`

### V2.1 租户与 SaaS

- `t_tenant` -> `Tenant` -> Tenant `TenantMapper`。
- `t_tenant_member` -> `TenantMember` -> Tenant `TenantMemberMapper`。
- `t_platform_staff` -> `PlatformStaff` -> Tenant `PlatformStaffMapper`。
- `t_saas_plan` -> `SaasPlan` -> Tenant `SaasPlanMapper`。
- `t_saas_plan_version` -> 缺 Entity -> 缺 Mapper。
- `t_tenant_subscription` -> `TenantSubscription` -> Tenant `TenantSubscriptionMapper`。
- `t_tenant_quota` -> 缺 Entity -> 缺 Mapper。
- `t_tenant_usage_daily` -> 缺 Entity -> 缺 Mapper。
- `t_default_tenant_membership_outbox` -> `DefaultTenantMembershipOutbox` -> Common MyBatis `DefaultTenantMembershipOutboxMapper`。

V2.1 另修改 `sys_user`，不创建第二个 User Entity。

### V2.2 认证会话

- `t_auth_refresh_session` -> `AuthRefreshSession` -> Auth `AuthRefreshSessionMapper` + XML。
- `t_auth_refresh_token` -> `AuthRefreshToken` -> Auth `AuthRefreshTokenMapper` + XML。
- `t_auth_refresh_risk_event` -> `AuthRefreshRiskEvent` -> Auth `AuthRefreshRiskEventMapper`。

### 后续 alter

- `V1_7` 只扩展 `t_entitlement_order`。
- `V2_2_1` 与 `V2_2_2` 依次 expand/contract 七张个人权益表，不创建新表。
- `V2_3_1` 与未执行的 `V2_3_2` 依次 expand/contract 九张设备表，不创建新表。

## P-3B 实时门禁结果

九张设备表的 `tenant_id` 均存在且当前仍为 nullable，证明 `V2_3_1` 已生效、`V2_3_2` 未生效。以下只读计数全部为 0：

- 九表 tenant NULL。
- 九表 tenant 引用孤儿。
- Session 与节点 tenant 不一致。
- Traffic 与节点/Session tenant 不一致或引用缺失。
- Signal 与节点/有效 Session tenant 不一致或引用缺失。
- WiFi 配置与节点 tenant 不一致或引用缺失。
- Command 与可解析节点/Session tenant 不一致或引用缺失；保留迁移中对终态 `RUNTIME_TEST` 记录的既有例外。
- `(tenant_id, mac)` 黑名单重复。
- `(tenant_id, device_code, event_id)` Traffic 重复。
- `(tenant_id, user_id)` Session guard 重复。
- `(tenant_id, mac)` Client guard 重复。
- 全局 `device_code` 重复。
- 全局命令 `request_id` 重复。

这些零计数在 1.3-A 完成时只是 1.3-B 的数据前置证据，不是执行许可。当时尚无已确认的 post-`V2_3_1` 恢复点，且 1.3-A 不验证旧版本服务已经停止写入，因此在 A 批结束时禁止执行 `V2_3_2`。后续门禁确认和执行结果见 [Demo 1.3-B P-3B contract 实施记录](demo-1.3-b-p3b-contract.md)。

### P-3B 只读 SQL

```sql
select 'tenant_null' check_name, sum(violation_count) violation_count
from (
    select count(*) violation_count from t_esp32_node where tenant_id is null
    union all select count(*) from t_session where tenant_id is null
    union all select count(*) from t_mac_blacklist where tenant_id is null
    union all select count(*) from t_traffic_log where tenant_id is null
    union all select count(*) from t_client_signal where tenant_id is null
    union all select count(*) from t_session_user_guard where tenant_id is null
    union all select count(*) from t_client_access_guard where tenant_id is null
    union all select count(*) from t_device_command where tenant_id is null
    union all select count(*) from t_device_wifi_config where tenant_id is null
) checks;

select 'tenant_orphan' check_name, sum(violation_count) violation_count
from (
    select count(*) violation_count from t_esp32_node r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_session r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_mac_blacklist r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_traffic_log r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_client_signal r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_session_user_guard r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_client_access_guard r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_device_command r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
    union all select count(*) from t_device_wifi_config r left join t_tenant t on t.tenant_id=r.tenant_id where t.tenant_id is null
) checks;
```

关系一致性与重复键 SQL 以冻结的 `V2_3_2__tenant_device_contract.sql` 中 8 个命名门禁为唯一来源；1.3-A 的 JDBC 审计逐项执行了等价 SELECT，未复制或改写迁移文件。

## P-3C 只读门禁

P-3C 当前处于 pre-expand，以下 SQL 可立即只读执行：

```sql
select table_name, count(*) tenant_column_count
from information_schema.columns
where table_schema = database()
  and column_name = 'tenant_id'
  and table_name in (
      't_access_rule', 't_client_location', 't_location_authorization',
      't_audit_log', 't_alert_event', 't_rule_hit',
      't_geofence', 't_geofence_state', 't_geofence_event'
  )
group by table_name;

select action, count(*) action_count
from t_audit_log
group by action
order by action;
```

当前第一条返回 0 个表；第二条返回 26 个 action、145 行。当前 action 只读分类候选：

- PLATFORM：`auth.platform_context`、`auth.platform_tenant_context`、`auth.register`、`auth.reset_password`、`user.purge`、`user.update`。
- TENANT：`blacklist.add`、`blacklist.remove`、`device.allow`、`device.allow-client`、`device.create`、`device.delete`、`device.kick`、`device.manual-block-traffic`、`device.wifi.stage`、`entitlement.reward-order.create`、`entitlement.unlimited.adjust`、`location.consent.grant`、`location.consent.revoke`、`refund.apply`、`refund.channel.result`、`refund.review`、`rule.delete`、`session.admin-revoke`、`session.logout`、`session.portal-authorize`。

该分类只是 1.3-A 的门禁输入，不回填数据；`V2_4_1` 实施前必须按真实写入语义复核。任何未列入集合的 action 都计为 `UNKNOWN_AUDIT_ACTION` 并阻断 contract。

`V2_4_1` 执行后、`V2_4_2` 执行前必须只读得到以下全部为 0：

- 八张纯租户表 tenant NULL。
- 九表非空 tenant 引用孤儿。
- location 与 Session/节点 tenant 不一致。
- alert 与 rule tenant 不一致。
- rule hit 与 rule、alert、节点、Session tenant 不一致。
- geofence state/event 与 fence、location、节点、Session tenant 不一致。
- audit `scope_type` 不在 `PLATFORM/TENANT`，或 PLATFORM 带 tenant、TENANT 缺 tenant。
- rule code、位置授权、rule hit、围栏状态、围栏事件的目标租户组合键重复。
- `UNKNOWN_AUDIT_ACTION`。

post-expand 候选只读 SQL：

```sql
select 'TENANT_NULL' check_name, (
    (select count(*) from t_access_rule where tenant_id is null)
    + (select count(*) from t_client_location where tenant_id is null)
    + (select count(*) from t_location_authorization where tenant_id is null)
    + (select count(*) from t_alert_event where tenant_id is null)
    + (select count(*) from t_rule_hit where tenant_id is null)
    + (select count(*) from t_geofence where tenant_id is null)
    + (select count(*) from t_geofence_state where tenant_id is null)
    + (select count(*) from t_geofence_event where tenant_id is null)
) violation_count
union all
select 'TENANT_REFERENCE', (
    select count(*)
    from (
        select tenant_id from t_access_rule
        union all select tenant_id from t_client_location
        union all select tenant_id from t_location_authorization
        union all select tenant_id from t_audit_log where tenant_id is not null
        union all select tenant_id from t_alert_event
        union all select tenant_id from t_rule_hit
        union all select tenant_id from t_geofence
        union all select tenant_id from t_geofence_state
        union all select tenant_id from t_geofence_event
    ) scoped
    left join t_tenant tenant_record on tenant_record.tenant_id = scoped.tenant_id
    where tenant_record.tenant_id is null
)
union all
select 'LOCATION_RELATION_TENANT', (
    select count(*)
    from t_client_location location_record
    left join t_session session_record on session_record.session_id = location_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = location_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = location_record.device_code
    where (location_record.session_id is not null and (
              session_record.session_id is null
              or session_record.tenant_id <> location_record.tenant_id
          ))
       or (location_record.node_id is not null and (
              node_by_id.node_id is null
              or node_by_id.tenant_id <> location_record.tenant_id
          ))
       or (location_record.device_code is not null and (
              node_by_code.node_id is null
              or node_by_code.tenant_id <> location_record.tenant_id
          ))
)
union all
select 'ALERT_RULE_TENANT', (
    select count(*)
    from t_alert_event alert_record
    left join t_access_rule rule_record
      on rule_record.tenant_id = alert_record.tenant_id
     and rule_record.rule_code = alert_record.rule_code
    where rule_record.id is null
)
union all
select 'RULE_HIT_RELATION_TENANT', (
    select count(*)
    from t_rule_hit hit_record
    left join t_access_rule rule_record on rule_record.id = hit_record.rule_id
    left join t_alert_event alert_record on alert_record.id = hit_record.alert_id
    left join t_session session_record on session_record.session_id = hit_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = hit_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = hit_record.device_code
    where rule_record.id is null
       or rule_record.tenant_id <> hit_record.tenant_id
       or rule_record.rule_code <> hit_record.rule_code
       or session_record.session_id is null
       or session_record.tenant_id <> hit_record.tenant_id
       or node_by_id.node_id is null
       or node_by_id.tenant_id <> hit_record.tenant_id
       or node_by_code.node_id is null
       or node_by_code.tenant_id <> hit_record.tenant_id
       or (hit_record.alert_id is not null and (
              alert_record.id is null
              or alert_record.tenant_id <> hit_record.tenant_id
          ))
)
union all
select 'GEOFENCE_STATE_RELATION_TENANT', (
    select count(*)
    from t_geofence_state state_record
    left join t_geofence fence_record on fence_record.fence_id = state_record.fence_id
    left join t_client_location location_record on location_record.id = state_record.last_location_id
    left join t_session session_record on session_record.session_id = state_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = state_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = state_record.device_code
    where fence_record.fence_id is null
       or fence_record.tenant_id <> state_record.tenant_id
       or location_record.id is null
       or location_record.tenant_id <> state_record.tenant_id
       or session_record.session_id is null
       or session_record.tenant_id <> state_record.tenant_id
       or node_by_id.node_id is null
       or node_by_id.tenant_id <> state_record.tenant_id
       or node_by_code.node_id is null
       or node_by_code.tenant_id <> state_record.tenant_id
)
union all
select 'GEOFENCE_EVENT_RELATION_TENANT', (
    select count(*)
    from t_geofence_event event_record
    left join t_geofence fence_record on fence_record.fence_id = event_record.fence_id
    left join t_client_location location_record on location_record.id = event_record.location_id
    left join t_session session_record on session_record.session_id = event_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = event_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = event_record.device_code
    where fence_record.fence_id is null
       or fence_record.tenant_id <> event_record.tenant_id
       or location_record.id is null
       or location_record.tenant_id <> event_record.tenant_id
       or session_record.session_id is null
       or session_record.tenant_id <> event_record.tenant_id
       or node_by_id.node_id is null
       or node_by_id.tenant_id <> event_record.tenant_id
       or node_by_code.node_id is null
       or node_by_code.tenant_id <> event_record.tenant_id
)
union all
select 'AUDIT_SCOPE_TENANT', (
    select count(*)
    from t_audit_log
    where scope_type not in ('PLATFORM', 'TENANT')
       or (scope_type = 'PLATFORM' and tenant_id is not null)
       or (scope_type = 'TENANT' and tenant_id is null)
)
union all
select 'DUPLICATE_TENANT_KEY', (
    (select count(*) from (
        select tenant_id, rule_code
        from t_access_rule
        group by tenant_id, rule_code
        having count(*) > 1
    ) duplicate_rule)
    + (select count(*) from (
        select tenant_id, user_id
        from t_location_authorization
        group by tenant_id, user_id
        having count(*) > 1
    ) duplicate_location_authorization)
    + (select count(*) from (
        select tenant_id, device_code, event_id, rule_code
        from t_rule_hit
        group by tenant_id, device_code, event_id, rule_code
        having count(*) > 1
    ) duplicate_rule_hit)
    + (select count(*) from (
        select tenant_id, fence_id, session_id
        from t_geofence_state
        group by tenant_id, fence_id, session_id
        having count(*) > 1
    ) duplicate_geofence_state)
    + (select count(*) from (
        select tenant_id, fence_id, location_id, event_type
        from t_geofence_event
        group by tenant_id, fence_id, location_id, event_type
        having count(*) > 1
    ) duplicate_geofence_event)
)
union all
select 'UNKNOWN_AUDIT_ACTION', count(*)
from t_audit_log
where action not in (
    'auth.platform_context',
    'auth.platform_tenant_context',
    'auth.register',
    'auth.reset_password',
    'user.purge',
    'user.update',
    'blacklist.add',
    'blacklist.remove',
    'device.allow',
    'device.allow-client',
    'device.create',
    'device.delete',
    'device.kick',
    'device.manual-block-traffic',
    'device.wifi.stage',
    'entitlement.reward-order.create',
    'entitlement.unlimited.adjust',
    'location.consent.grant',
    'location.consent.revoke',
    'refund.apply',
    'refund.channel.result',
    'refund.review',
    'rule.delete',
    'session.admin-revoke',
    'session.logout',
    'session.portal-authorize'
);
```

这些 SQL 依赖尚不存在的 `tenant_id/scope_type`，当前禁止执行。实际 `V2_4_1/2` 文件必须在 1.3-C 中根据最终 expand DDL 和历史分类决定复核字段名、索引目标与关系例外，再把每个项目固化为门禁；不能在 1.3-A 提前创建迁移文件。

## 备份门禁

仓库外当前只发现：

- `wifi-p3a-pre-recovery-20260807-121458.sql`，SHA-256 `a614932a35816280ce11805b8f64fe7abef793516a85b419e9e7eadfc16884ea`。
- `wifi-p3b-pre-v2_3_1-20260807-203831.sql`，SHA-256 `0744c3d4090cb1b915b24351abaee9aec9d4524ded75d172614aa58e495cfc07`。
- 1.3-B 新生成 `wifi-demo-1.3b-pre-v2_3_2-20260810-223858.sql`，大小 `641426` 字节，SHA-256 `0fb32b70b385d10ec34e3d87f7f16a8e02a31b926c097384902209328b6023fe`；文件级检查包含 44 个 `CREATE TABLE`、28 个 INSERT 语句和 1 个 dump 完成标记。

最新文件是 post-`V2_3_1`、pre-`V2_3_2` 备份，但尚未做隔离库恢复演练。1.3-B 执行前要求：

1. 确认上述新文件作为 server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`、`localhost:3306/wifi` 的本次恢复点。用户已于 2026-08-10 确认。
2. 确认旧版本服务写入已经停止，不会继续产生缺 tenant 数据。用户已于 2026-08-10 确认。
3. 在两项确认前不要执行 Flyway、`V2_3_2`、repair、clean、DDL 或数据修正。该边界已遵守。

## 1.3-A 结论

- 数据库身份、目标 schema、Flyway 历史、成功迁移 checksum、表集合、Entity 集合、Mapper/XML 数量和未跟踪迁移状态均已实时核对。
- P-3B 当前数据门禁为零，但 contract 未执行；P-3C 仍为 pre-expand。
- 四方清单的真实缺口为 3 张 SaaS 表缺 Entity/Mapper，另有后续 Demo 新域表尚未创建；这些都按 1.3-D 及后续批次处理。
- 1.3-A 已于 2026-08-10 通过用户验收。1.3-B 随后在两项用户确认和执行前实时门禁全部通过后完成 `V2_3_2`；当前结果及唯一恢复点见 [Demo 1.3-B P-3B contract 实施记录](demo-1.3-b-p3b-contract.md)。

## 1.3-A-R 充分性复核结论

2026-08-11 在原 H 回放后重新从 A 执行“用例/标识 -> 列/索引 -> Entity -> Mapper/条件更新”审计。原 A 的数据库身份、历史 checksum 和集合清单仍有效，但“集合存在”不能证明字段足以承载 2.2/2.3 契约。

### 计划缺口

- 原 A-H 批次与验收矩阵只要求迁移、表、Entity、Mapper/XML 集合对齐，没有设置逐个 `clientRequestId`、`eventId`、`expectedVersion`、quota 引用和 Worker lease 的字段充分性门禁。
- 原批次责任没有为 Tenant 创建收据、Tenant/Assignment 共用领域 Outbox、公告动作收据和跨域 Worker lease 分配独立迁移批次；H 也没有把未解释字段列为停止条件。
- 因此原 H 可以在 20 个迁移、66 张表集合对齐时结束，却没有证明第二阶段契约可由数据库稳定承载。A-R 已在唯一阶段计划中补入五层清单、独立验收项、批次 I 和 H-R 停止条件。

### 实现缺口

- 缺三张真实业务表：Tenant 创建收据、Tenant 领域 Outbox、公告更新/发布/撤回动作收据。
- `t_user_operation_request` 缺 client request、fingerprint、version 和 expectedVersion 条件审批入口。
- 默认成员 Outbox 和 AI review task 缺可恢复 Worker claim/lease；Quota reservation 缺 eventId。
- Node、Portal Session 和 Device command 缺客户端幂等、请求 fingerprint、quota reservation 引用或 version；Device command 还缺 dispatch claim/lease 和有界 due 索引。
- 这些实现缺口已由未执行的 `V2_9__cross_domain_persistence_carriers.sql`、对应 Entity/Mapper/XML 和永久测试补齐，没有修改任何已执行迁移，也没有提前实现 Service/API。

### 修正后事实

- 新 Run `20260811hr2016` 的空库、V2.3.1 和 V2.3.2 三链均到达 V2.9 和 69 张业务表，Flyway validate、结构语义指纹、AI 敏感列和 expectedVersion 并发探针通过。
- 九类所有者 Mapper MySQL IT 全部执行并回滚为零残留；仓库最终为 21 个迁移、69 张业务表、67 个 Entity、71 个 Java Mapper 和 17 个 Mapper XML。
- 真实 `wifi` 始终保持 rank 9 / V2.4.1 / checksum `848930306`，V2.9 未执行；四个隔离回放 Schema 已经 ownership 校验后精确清理为零。
- A-R/I/H-R 已完成实施和自审，等待 1.3 用户验收；验收前不暂存、不提交、不进入 1.4。
