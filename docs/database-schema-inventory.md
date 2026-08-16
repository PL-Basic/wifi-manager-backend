# Wifi Manager 数据库对象清单

> 第 1-6 节保留 2026-08-02 历史快照。Demo 1.3 的 2026-08-11 实时数据库、Flyway checksum、V2.9/V2.10 候选清单和只读门禁以第 7 节、[demo-1.3-a-database-audit.md](demo-1.3-a-database-audit.md)及[demo-1.3-h-migration-replay.md](demo-1.3-h-migration-replay.md)为准；P-3B contract 当前状态以 [demo-1.3-b-p3b-contract.md](demo-1.3-b-p3b-contract.md) 为准。不得再把本页的旧表数或 V2.2 待执行状态当作当前事实。

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

## 7. Demo 1.3 当前候选清单

2026-08-14 R7 最终只读复核确认真实 `wifi` 为 MySQL 主机 `xwh`、rank 9 /
V2.4.1 / checksum `848930306`。V2.5 至 V2.10 均未执行；2026-08-12
新增的 V2.9.1 同样未执行。本节只描述仓库候选，不代表真实库现有对象。

仓库当前有 22 个迁移文件、68 个生产 `@TableName` Entity、71 个 Java
Mapper 和 15 个 Mapper XML。V2.9.1 新增一张业务表；Run
`20260814r7c` 已证明空库、V2.3.1 快照和 V2.3.2 快照三链在默认
V2.9.1 及显式 V2.10 后均为 70 张业务表，columns/index/constraint
指纹一致。

### 所有权矩阵

以下每项顺序为：表集合；Entity；Mapper/XML；唯一所有者；允许写入方；
允许只读方；实际引用模块。

- Auth：`t_verify_code`、`t_login_fail_record`、`t_oauth_state`、
  `t_auth_refresh_session`、`t_auth_refresh_token`、
  `t_auth_refresh_risk_event`；六个 Auth 私有 Entity；六个 Auth 私有
  Mapper，相关 XML 由 auth-service 资源目录打包；auth-service；仅 Auth；
  仅 Auth；auth-service。
- User 与个人权益：`sys_user`、`t_social_identity`、`t_duration_purchase`、
  `t_network_entitlement`、`t_entitlement_usage_log`、
  `t_entitlement_order`、`t_payment_record`、`t_refund_record`、
  `t_trade_status_log`、`t_user_operation_request`、
  `t_default_tenant_membership_outbox`、
  `t_user_account_command_receipt`；12 个 User 私有 Entity；12 个 User
  私有 Mapper，权益 resultMap XML 由 user-service 打包；user-service；
  仅 User；Auth 只能通过内部账号契约读取账号快照或发起受控命令；
  user-service，Auth 只引用 common-api 请求/响应契约和 Feign 端口。
- Device：`t_esp32_node`、`t_session`、`t_mac_blacklist`、
  `t_traffic_log`、`t_client_signal`、`t_device_wifi_config`、
  `t_device_command`、`t_session_user_guard`、`t_client_access_guard`；
  前七张表有 Device 私有 Entity，两张 guard 表按冻结设计无 Entity；
  九个 Device 私有 Mapper，已有 resultMap XML 由 device-service 打包；
  device-service；仅 Device；其他服务经内部契约读取；device-service。
- Monitor：`t_access_rule`、`t_client_location`、
  `t_location_authorization`、`t_audit_log`、`t_alert_event`、
  `t_rule_hit`、`t_geofence`、`t_geofence_state`、`t_geofence_event`；
  九个 Monitor 私有 Entity；九个表绑定 Mapper 加一个无业务表的
  `MonitorHealthMapper`，已有 resultMap XML 由 monitor-service 打包；
  monitor-service；Monitor 业务写入，审计追加写入仅经 Audit Writer；
  Monitor；monitor-service 和
  wifi-audit-spring-boot-starter 的受控 JDBC 写端口。
- Tenant 与 SaaS：`t_tenant`、`t_tenant_member`、`t_platform_staff`、
  `t_saas_plan`、`t_saas_plan_version`、`t_tenant_subscription`、
  `t_tenant_quota`、`t_tenant_usage_daily`、
  `t_tenant_plan_assignment`、`t_tenant_quota_reservation`、
  `t_tenant_creation_receipt`、`t_tenant_domain_outbox`；12 个 Tenant 私有
  Entity 和 Mapper；tenant-service；仅 Tenant；其他服务经内部契约读取；
  tenant-service。
- Marketplace：`t_market_product`、`t_market_sku`、`t_market_order`、
  `t_market_order_item`、`t_market_fulfillment`；五个 Marketplace 私有
  Entity 和纯 BaseMapper；marketplace-service；仅 Marketplace；后续服务
  契约；marketplace-service。
- Support：`t_announcement`、`t_announcement_content_version`、
  `t_announcement_comment`、`t_user_capability_restriction`、
  `t_support_content_review_outbox`、`t_support_user_guard`、
  `t_support_daily_guard`、`t_support_submission`、`t_support_ticket`、
  `t_support_ticket_message`、`t_support_ticket_transition`、
  `t_announcement_action_request`；12 个 Support 私有 Entity 和纯
  BaseMapper；support-service；仅 Support；后续服务契约；
  support-service。
- AI：`t_ai_provider`、`t_ai_policy`、`t_ai_policy_version`、
  `t_ai_review_task`、`t_ai_manual_review`；五个 AI 私有 Entity 和纯
  BaseMapper；ai-service；仅 AI；后续服务契约；ai-service。

### 三个跨模块例外

- `sys_user`：UserMapper 只存在于 user-service。Auth 不持有 User Entity、
  Mapper 或 XML，通过 `/internal/user-accounts/**` 完成账号创建、快照读取、
  密码条件替换和默认成员事件触发。
- `t_default_tenant_membership_outbox`：Entity、Mapper、事件创建和状态推进
  全部在 user-service。Auth 只能触发 User 的受控投递入口。
- `t_audit_log`：AuditLog Entity 和查询 Mapper 只在 monitor-service。
  audit starter 只公开 `AuditWriter`，使用参数化 JDBC 追加写入，不公开
  BaseMapper、任意查询、更新或删除能力。

`wifi-common-mybatis` 当前保留 `WifiMybatisAutoConfiguration`、分页插件、
逻辑删除配置和 `spring.factories` 自动装配入口；User、Device、Monitor、
Tenant 四个模块直接消费该基础配置。其业务 Entity、Mapper 和 Mapper XML
均为 0，因此没有需要保留在公共 MyBatis 的业务持久化类型。Marketplace、
Support 和 AI 继续保持私有持久化模块，不含启动类。

### 数量闭合

- Auth：6 表 / 6 Entity / 6 Mapper / 2 XML。
- User：12 表 / 12 Entity / 12 Mapper / 5 XML。
- Device：9 表 / 7 Entity / 9 Mapper / 3 XML；两张 guard 表按设计无
  Entity。
- Monitor：9 表 / 9 Entity / 10 Mapper / 5 XML；额外 Mapper 为无业务表的
  `MonitorHealthMapper`。
- Tenant：12 表 / 12 Entity / 12 Mapper / 0 XML。
- Marketplace：5 表 / 5 Entity / 5 Mapper / 0 XML。
- Support：12 表 / 12 Entity / 12 Mapper / 0 XML。
- AI：5 表 / 5 Entity / 5 Mapper / 0 XML。

合计 70 张业务表、68 个生产 Entity、71 个 Java Mapper 和 15 个 Mapper
XML。六个真实持久化服务 Context 已分别冻结允许注册的 Mapper 集合；
Admin Context 已证明不存在 DataSource、MyBatis 类、Mapper/Entity Bean 或
Mapper XML。

1.3 不实现 claim、锁行、expectedVersion/expectedStatus 条件更新、业务状态
转换或分页聚合；这些具名数据访问归 1.5/2.3。

后续真实迁移仍必须重新执行数据库身份、Flyway 历史/checksum、专用测试
账号和备份门禁。V2.10 还要求 P-3C tenant-aware 代码切换与只读零计数门禁；
隔离库成功也不能授权执行真实 V2.5-V2.10。
