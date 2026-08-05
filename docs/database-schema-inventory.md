# Wifi Manager 数据库对象清单

## 1. 清单基线

本清单对应 2026-08-02 的 `wifi` schema 快照和当前源码。数据库共有 32 张 BASE TABLE，其中 31 张业务表、1 张 `flyway_schema_history`；VIEW、TRIGGER、EVENT、PROCEDURE、FUNCTION 和 FOREIGN KEY 均为 0。

Flyway 历史为 baseline 1.6 与成功执行的 V1.7。数据库记录的 V1.7 checksum 为 `-1448715625`，当前 `V1_7__reward_order_metadata.sql` 必须在纳入版本控制前由用户再次执行 `flyway validate`。本清单只记录集合关系，不能替代真实库查询或 validate。

## 2. Flyway、Entity、Mapper 与所有者

以下每项依次记录：真实表；Flyway 来源；Entity；Mapper/XML；所属服务；关键唯一约束。

- `sys_user`；V1.1；`User`；auth/user 两个 `UserMapper`；auth-service 与 user-service；`username` 唯一。
- `t_social_identity`；V1.1；`SocialIdentity`；`SocialIdentityMapper`；user-service；provider subject、provider union id、user provider 唯一。
- `t_verify_code`；V1.2；`VerifyCode`；`VerifyCodeMapper`；auth-service；Provider 外部编号唯一。
- `t_login_fail_record`；V1.2；`LoginFailRecord`；`LoginFailRecordMapper` + XML；auth-service；account、login type、request IP 唯一。
- `t_oauth_state`；V1.2；`OAuthStateRecord`；`OAuthStateMapper`；auth-service；state hash、Provider authorization code hash 唯一。
- `t_duration_purchase`；V1.3；`DurationPurchase`；`DurationPurchaseMapper`；user-service；order no 唯一。
- `t_network_entitlement`；V1.3；`NetworkEntitlement`；`NetworkEntitlementMapper` + XML；user-service；user id 唯一。
- `t_entitlement_usage_log`；V1.3；`EntitlementUsageLog`；`EntitlementUsageLogMapper`；user-service；request id + line no 唯一。
- `t_entitlement_order`；V1.3，V1.7 增加奖励订单元数据；`EntitlementOrder`；`EntitlementOrderMapper` + XML；user-service；order no、user id + client request id 唯一。
- `t_payment_record`；V1.3；`PaymentRecord`；`PaymentRecordMapper` + XML；user-service；payment no、用户请求、业务键、渠道交易/回调及 order no 唯一。
- `t_refund_record`；V1.3；`RefundRecord`；`RefundRecordMapper` + XML；user-service；refund no、用户请求、渠道退款/事件唯一。
- `t_trade_status_log`；V1.3；`TradeStatusLog`；`TradeStatusLogMapper` + XML；user-service；business type + business no + event key + target status 唯一。
- `t_user_operation_request`；V1.4；`UserOperationRequest`；`UserOperationRequestMapper`；user-service；主键与业务状态约束。
- `t_esp32_node`；V1.5；`Esp32Node`；`Esp32NodeMapper` + XML；device-service；device code 全局唯一。
- `t_session`；V1.5；`SessionRecord`；`SessionRecordMapper`；device-service；主键及 user/mac/node/status 查询索引。
- `t_mac_blacklist`；V1.5；`MacBlacklist`；`MacBlacklistMapper`；device-service；mac 唯一。
- `t_traffic_log`；V1.5；`TrafficLog`；`TrafficLogMapper` + XML；device-service；device code + event id 唯一。
- `t_client_signal`；V1.5；`ClientSignalRecord`；`ClientSignalMapper` + XML；device-service；主键及设备/Session/MAC 时间索引。
- `t_session_user_guard`；V1.5；允许无 Entity；`SessionUserGuardMapper` 原生锁行 SQL；device-service；user id 主键。
- `t_client_access_guard`；V1.5；允许无 Entity；`ClientAccessGuardMapper` 原生锁行 SQL；device-service；mac 主键。
- `t_device_command`；V1.5；`DeviceCommandRecord`；`DeviceCommandRecordMapper`；device-service；request id 全局唯一。
- `t_device_wifi_config`；V1.5；`DeviceWifiConfigRecord`；`DeviceWifiConfigRecordMapper`；device-service；request id、node id + config version 唯一。
- `t_access_rule`；V1.6；`AccessRule`；`AccessRuleMapper`；monitor-service；rule code 唯一。
- `t_client_location`；V1.6；`ClientLocation`；`ClientLocationMapper` + XML；monitor-service；主键及用户/Session/节点时间索引。
- `t_location_authorization`；V1.6；`LocationAuthorization`；`LocationAuthorizationMapper`；monitor-service；user id 主键。
- `t_audit_log`；V1.6；`AuditLog`；公共 `AuditLogMapper`；audit starter 写入、monitor-service 查询；主键及 action/operator/time 索引。
- `t_alert_event`；V1.6；`AlertEvent`；`AlertEventMapper` + XML；monitor-service；主键及状态/规则/设备时间索引。
- `t_rule_hit`；V1.6；`RuleHitRecord`；`RuleHitRecordMapper` + XML；monitor-service；device code + event id + rule code 唯一。
- `t_geofence`；V1.6；`Geofence`；`GeofenceMapper`；monitor-service；fence id 主键。
- `t_geofence_state`；V1.6；`GeofenceState`；`GeofenceStateMapper`；monitor-service；fence id + session id 唯一。
- `t_geofence_event`；V1.6；`GeofenceEvent`；`GeofenceEventMapper` + XML；monitor-service；fence id + location id + event type 唯一。
- `flyway_schema_history`；Flyway 元数据；无业务 Entity；由 database-migration/Flyway 管理；不属于业务表集合。

## 3. 已解释差集

- 已删除遗留 `DeviceNode -> wifi_device` 和 `MacWhitelist -> wifi_mac_whitelist` 映射。全仓没有对应真实表、Flyway DDL、Mapper、Service、反射注册或代码生成配置引用。
- `t_session_user_guard` 和 `t_client_access_guard` 故意不创建 Entity，由专用 Mapper 的原生 SQL承担并发锁行；它们不是遗漏。
- `t_audit_log` 的 Mapper 位于公共 MyBatis 模块，写入由 audit starter 负责，查询由 monitor-service 负责。

## 4. V2.1 已执行租户基础对象

P-1 已由用户执行 V2.1 并完成验收。以下对象加入源码、Entity/Mapper 和真实库核对范围：

- `t_tenant`；V2.1；`Tenant`；`TenantMapper`；tenant-service；tenant code 全局唯一。
- `t_tenant_member`；V2.1；`TenantMember`；`TenantMemberMapper`；tenant-service；tenant + user 唯一、每个用户最多一个有效默认成员。
- `t_platform_staff`；V2.1；`PlatformStaff`；`PlatformStaffMapper`；tenant-service；user + authority 唯一。
- `t_saas_plan`；V2.1；`SaasPlan`；`SaasPlanMapper`；tenant-service；plan code 唯一。
- `t_saas_plan_version`；V2.1；当前 P-1 仅建表；tenant-service；plan + version no 唯一。
- `t_tenant_subscription`；V2.1；`TenantSubscription`；`TenantSubscriptionMapper`；tenant-service；每个租户最多一个有效订阅。
- `t_tenant_quota`；V2.1；当前 P-1 仅建表；tenant-service；tenant + quota type 唯一。
- `t_tenant_usage_daily`；V2.1；当前 P-1 仅建表；tenant-service；tenant + date + usage type 唯一。
- `t_default_tenant_membership_outbox`；V2.1；`DefaultTenantMembershipOutbox`；auth-service `DefaultTenantMembershipOutboxMapper`；user id 与 event id 唯一。

## 5. V2.2 待执行认证会话对象

以下对象已经进入 P-2 源码，但在用户执行 V2.2 和真实 schema 核对前不得写成“数据库已存在”：

- `t_auth_refresh_session`；V2.2；`AuthRefreshSession`；`AuthRefreshSessionMapper` + XML；auth-service；session id 主键，保存 family 状态、7 天绝对期限、当前可信租户上下文和只在安全事件后递增的 security_version。
- `t_auth_refresh_token`；V2.2；`AuthRefreshToken`；`AuthRefreshTokenMapper` + XML；auth-service；只保存 refresh token SHA-256 哈希，哈希唯一并保留旋转/重放状态。
- `t_auth_refresh_risk_event`；V2.2；`AuthRefreshRiskEvent`；`AuthRefreshRiskEventMapper`；auth-service；记录 IP 网段、User-Agent、client instance 和 STEP_UP_REQUIRED/STEP_UP_COMPLETED 的弱风险信号哈希历史。

V2.2 不创建 Access JWT “每请求已使用”表。Access `jti` 只用于唯一标识、审计和显式撤销；普通请求不会消费 `jti`。session family 的持久撤销以 `sid` 和上述会话表为准，短时单个 `jti` 撤销使用 Redis TTL。

## 6. 后续迁移门禁

执行任何后续 V2.x 前必须依次完成：数据库备份/恢复点、已执行迁移文件与历史 checksum 核对、用户执行 Flyway validate、重新查询真实对象集合并与本清单逐项比对。禁止用 Entity 与业务表数量相等替代集合核对，也禁止对当前业务库执行 Flyway clean。
