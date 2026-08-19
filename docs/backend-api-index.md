# Wifi Manager 后端接口清单

## 0. Demo 2.1 S0 核心用例交叉引用

本文件继续只记录真实 HTTP、WebSocket 和内部传输入口。传输入口数量不等于
业务用例数量，也不能从 Controller、BFF、Feign 或 worker 反推第二套业务
owner。

Demo 2.1 S0 已在
[核心用例索引](core-use-case-index.md) 中冻结 58 个业务用例组和 183 个全局
唯一 `QUERY/COMMAND operationId`；对应机器事实源为
`wifi-test-kit/src/test/resources/fixtures/core-use-cases-v1/manifest.json`。
机器索引采用“中央 manifest + 分域 fixture”：8 个业务分域文件分别拥有唯一
group、operation 和 stateMachine，跨 owner 事件与审计关闭矩阵使用独立文件。
测试只按 manifest 固定清单在内存组合，不递归加载，也不生成单体聚合 JSON。
fixture 使用 42 个对象级状态模型，不保留
`AUTH_REFRESH/ACCOUNT_ENTITLEMENT/SAAS_QUOTA/MARKETPLACE/SUPPORT/DEVICE/`
`MONITOR` 等聚合状态机。每个 operation 都是物理自描述对象，只允许一个业务
owner，并在自身登记 actor/context、状态引用或 `STATELESS`、输入输出、错误、
证据、`REUSE/PARTIAL/GAP`、完整冲突和唯一 `targetLayer`；不得依赖 manifest
默认值、loader 默认值、全局 defaults 或组级继承。

八个后续证据包的输入固定为：

- `P21-AUTH`：`AUTH-01..05`；
- `P21-USER`：`ACCOUNT-01..03`、`ENT-01..06`、`GOV-01` 的 USER 边界；
- `P21-TENANT`：`TENANT-01..03`、`SAAS-01..02`、`QUOTA-01..02`、
  `GOV-01` 的 TENANT 边界；
- `P21-MARKET`：`MARKET-01..06`；
- `P21-SUPPORT`：`ANN-01..06`、`SUPPORT-01..06`；
- `P21-AI`：`AI-01..03`；
- `P21-DEVICE`：`DEVICE-01..05`、`SESSION-01..02`、`TELEMETRY-01`；
- `P21-MONITOR`：`RULE-01..02`、`ALERT-01`、`AUDIT-01`、
  `LOCATION-01`、`GEOFENCE-01`、`ANALYTICS-01`。

本节不声明上述 P21 包已经启动。后续包只能用真实入口核对中央 operation，
不能新增 operationId、改变状态词、修改中央索引或把 `/internal/**` 暴露给
浏览器/设备。发现入口与中央索引冲突时必须停止并退回 2.1 协调门。

## 1. 访问入口

客户端、前端和第三方回调统一通过 Gateway：

```text
http://{gateway-host}:8080
```

除 WebSocket 和头像文件外，当前生产 HTTP 接口统一返回三字段兼容 envelope：

```json
{"code":200,"message":"操作成功","data":{}}
```

Demo 1.4 S1 已在共享入口实现 `http-envelope-v1`：`ApiResponse` 保留
`code/message/data`，并以仅在非空时序列化的方式增加 `errorKey/requestId`。
`http-support-v1` 固定 Servlet 的九类状态、`X-Request-Id`、MDC 清理和安全
500；业务服务 handler 与 Gateway 已通过对应 `P14-*` 完成接入，S2 共享
收敛见下文。冻结结论、基础错误键、MQTT/AI 样本和 P14 文件所有权见
[Demo 1.4 S0 公共能力与兼容契约冻结](demo-1.4-s0-contract-freeze.md)。

Demo 1.4 C0 在不改变业务接口的前提下补充两组共享入口：
`wifi-common-api` 提供 `PageBounds`、稳定单位/时区常量、结构化 allowlist
脱敏和不回显配置值的纯 Java 校验；`wifi-web-support-spring-boot-starter`
提供无异常 message 的可定位安全堆栈、默认仅暴露 `health/info` 的 Actuator
基线，以及只约束 `wifi.*` 自定义指标的低基数 `MeterFilter`。服务可显式扩展
readiness 必要依赖，但不得把外部依赖加入 liveness。

Demo 1.4 S2 将安全堆栈的唯一实现固定为
`com.plagod.support.SafeExceptionLogFormatter`，位于框架无关的
`wifi-common-api`。Servlet Starter 中原
`com.plagod.web.SafeExceptionLogFormatter` 仅作为兼容委托保留；
Gateway 未分类 500 使用同一实现记录有界的异常类型和定位帧，不记录异常
message、suppressed 内容或 Throwable 参数。

Demo 1.5 S0 将身份/Header 共享输入冻结为 `trusted-context-v1`：

- `wifi-common-api` 的 `TrustedRequestHeaders` 是 Gateway、Servlet Starter
  与 Feign 后续唯一可信 Header 常量来源；旧 `TrustedHeaderNames` 仅保留
  兼容委托。`X-Request-Id` 已进入可信传播清单，但不作为可伪造的身份字段。
- `TrustedRequestContext` 是不可变快照，分别保存 `trustedSource`、用户与
  session/jti、`PLATFORM/TENANT/PLATFORM_TENANT`、租户双版本、平台权限和
  requestId。`GATEWAY_USER`、`INTERNAL_SERVICE`、`SCHEDULED_SERVICE`、
  `DEVICE_EVENT` 是独立来源；内部/后台/设备身份不能伪造浏览器 actor。
- Security Starter 的 `TrustedRequestContextResolver` 只接受
  `TrustedRequestFilter` 已标记的 Gateway/Internal 请求。现有租户写校验
  与 Feign 出站按需装配并复用该 Context；未新增覆盖所有 Servlet 读路径的
  全局认证 Filter。
- Feign 始终删除 Authorization、Cookie、Gateway Token、调用方 Internal
  Token、手工身份/租户 Header 和手工 requestId，再注入当前服务自己的
  Internal Token。只有成功装配的用户 Context 才传播用户工作区字段；纯
  Internal Service 只传播关联 ID，不获得用户、平台或默认租户权限。

`P15-GW` 必须将 Gateway 内现有 Header 字面量切换为
`TrustedRequestHeaders` 并保持“先删外部同名 Header、再写验证结果”；
`P15-AUTH` 负责补 session/jti 失效的服务内永久测试；`P15-TENANT` 负责保证
TENANT 不签发平台权限、PLATFORM_TENANT 不签发 tenantRole/memberVersion；
其余 `P15-*` 业务包只消费 Resolver/Context，不复制 Header 解析器。以上是
批次 B 的冻结输入，不表示对应业务目录已经完成接入或真实环境联调。

Demo 1.5 B0 为两个内部跨服务对象增加显式租户载体：
`TrafficEvaluationRequest.tenantId` 必须由 Device 根据已持久化的
TrafficLog/Session 关系确定；`LocationSessionContextVO.tenantId` 必须来自
Device 的 tenant-scoped Session 查询。Monitor 必须拒绝字段缺失、非法或与
当前可信 Context 不一致的请求，不能从浏览器 Header、请求体用户标识或默认
租户补全。该变更不修改 entitlement lease/snapshot 契约；现有
`entitlementId + userId` 的租户重新解析路径继续复用。

Demo 1.5 C0 冻结事务、Outbox 与 HTTP 幂等共享载体：

- `t_default_tenant_membership_outbox` 继续由 User 独占，复用既有
  `idempotency_key/request_fingerprint`、claim 字段和
  `idx_default_membership_claim`。状态固定为
  `PENDING/PROCESSING/RETRY/SUCCEEDED/DEAD`；只有 `PROCESSING` 可以持有
  worker/lease，且 lease 必须晚于 claim 时间。
- User 私有 `t_user_auth_session_revoke_outbox` 保存账号业务状态同事务产生
  的撤销事件。Worker 提交 claim 后，在事务外继续调用现有
  `POST /internal/auth/sessions/users/{userId}/revoke?reason=...`，再以独立
  事务 finalize。Auth endpoint 不增加 `eventId`，Auth 不新增消费 Receipt；
  重复调用继续依赖只撤销 ACTIVE Session/Token 的自然幂等性。
- User 私有 `t_entitlement_lease_receipt` 以
  `tenant_id + request_id` 唯一，fingerprint 覆盖
  `entitlementId/userId/sessionId/usageSeconds/requestedTtlSeconds`。
  Receipt、权益扣减和 usage log 必须同事务；同 key 同 fingerprint 重放首次
  结果并返回 `duplicate=true`，不同 fingerprint 返回 409
  `IDEMPOTENCY_KEY_CONFLICT`。Receipt 同时保存允许与拒绝结果，拒绝结果的
  entitlement、mode、TTL、remaining 和 subscriptionEndTime 可为空。
- `TenantCreateRequest.clientRequestId` 与
  `PortalAuthorizeDTO.clientRequestId` 均为必填，最多 64 字符，格式为
  `^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$`。调用方必须生成并在同一业务输入的
  网络/人工重试中复用；服务端不得从 `X-Request-Id`、随机值或默认业务值
  回退。该字段是 additive JSON 字段，旧 payload 仍可反序列化，但因缺少
  必填字段明确返回 400。

Demo 1.5 S1 冻结 `audit-v1` 与条件状态转换共享模板：

- 审计 actor/context 只来自 `TrustedRequestContext`；后台服务和设备事件
  必须显式传入 `AuditActorContext`。匿名 Auth 请求仅在 Security Starter
  已标记可信 Gateway source 后记录为 `ANONYMOUS`，不解释调用方提交的
  user/tenant Header。
- `@Audited` 不再默认序列化 args/result。动态目标只通过
  `@AuditTargetId`，detail 只接受 `@AuditDetail("固定键")` 标记的安全
  标量；未标记的密码、Token、Cookie、正文和任意返回对象不会进入审计。
- SUCCESS 在当前业务事务 `afterCommit` 回调内使用独立
  `REQUIRES_NEW` 事务追加；无本地事务时在业务成功返回后也使用独立事务。
  DENIED/FAILED 使用固定 `errorKey` 和独立 `REQUIRES_NEW` writer。
  writer 失败只产生固定 warning、
  `wifi.audit.write.failures` 低基数指标和本地失败计数，不改变业务提交或
  原异常。
- `Future`/`CompletionStage` 当前不支持异步完成态审计。切面必须返回原
  异步对象，不得提前记录 SUCCESS，也不得注册无上下文传播保证的完成回调；
  事件按固定原因 `unsupported_async_result` 丢弃，并写入
  `wifi.audit.events.dropped` 指标和本地丢弃计数，不影响业务。
- 沿用 `t_audit_log` 现有列。`requestId/eventId`、actor/context、
  `platformManaged`、target、outcome、errorKey 和 sourceService 以固定
  安全 envelope 写入 JSON detail；业务 detail 位于显式 allowlist
  `fields` 下。
- `ConditionalStateTransition` 先校验条件更新影响行数：`1` 直接返回
  `APPLIED`，`0` 才按 duplicate event、资源、状态、版本分类，其他值拒绝。
  模板只冻结 `fromStates/toState`、`expectedVersion/eventKey` 校验和
  `APPLIED/REPLAYED/RESOURCE_NOT_FOUND/ILLEGAL_STATE/VERSION_CONFLICT`
  分类。领域服务仍负责合法边、tenant-scoped 条件更新以及主状态与
  transition log 的同事务写入。
- 后续调用方仅分 AUTH、TENANT、USER、DEVICE、MONITOR 五组，并只在以下
  冻结入口补 target/detail 元数据、可信 context、DENIED/FAILED 开关及
  本域 transition 接入；不得复制 Starter、修改共享路径或扩大入口集合。

`AUTH` 冻结 4 个入口：

- `auth-service/src/main/java/com/plagod/controller/TenantContextController.java`：
  `TenantContextController#returnPlatform`（`auth.platform_context`）、
  `TenantContextController#enterPlatformTenant`
  （`auth.platform_tenant_context`）。
- `auth-service/src/main/java/com/plagod/service/impl/UserServiceImpl.java`：
  `UserServiceImpl#register`（`auth.register`）、
  `UserServiceImpl#resetPassword`（`auth.reset_password`）。

`TENANT` 冻结 3 个入口：

- `tenant-service/src/main/java/com/plagod/service/impl/TenantServiceImpl.java`：
  `TenantServiceImpl#createTenant`（`tenant.create`）、
  `TenantServiceImpl#updateTenant`（`tenant.update`）、
  `TenantServiceImpl#updateStatus`（`tenant.status`）。

`USER` 冻结 10 个入口：

- `user-service/src/main/java/com/plagod/service/impl/UserManageServiceImpl.java`：
  `UserManageServiceImpl#updateUser`（`user.update`）、
  `UserManageServiceImpl#updateStatus`（`user.status`）、
  `UserManageServiceImpl#deleteUser`（`user.delete`）、
  `UserManageServiceImpl#purgeUser`（`user.purge`）。
- `user-service/src/main/java/com/plagod/service/impl/RefundServiceImpl.java`：
  `RefundServiceImpl#apply`（`refund.apply`）、
  `RefundServiceImpl#review`（`refund.review`）、
  `RefundServiceImpl#handleChannelResult`（`refund.channel.result`）。
- `user-service/src/main/java/com/plagod/service/impl/EntitlementRewardOrderServiceImpl.java`：
  `EntitlementRewardOrderServiceImpl#create`
  （`entitlement.reward-order.create`）。
- `user-service/src/main/java/com/plagod/service/impl/EntitlementAdjustmentServiceImpl.java`：
  `EntitlementAdjustmentServiceImpl#adjust`（`entitlement.adjust`）、
  `EntitlementAdjustmentServiceImpl#adjustUnlimited`
  （`entitlement.unlimited.adjust`）。

`DEVICE` 冻结 17 个入口：

- `device-service/src/main/java/com/plagod/service/RuleActionExecutor.java`：
  `RuleActionExecutor#disconnectMac`（`monitor.auto.disconnect-mac`）、
  `RuleActionExecutor#blockTraffic`（`monitor.auto.block-traffic`）。
- `device-service/src/main/java/com/plagod/service/impl/SessionRevokeServiceImpl.java`：
  `SessionRevokeServiceImpl#logout`（`session.logout`）、
  `SessionRevokeServiceImpl#adminRevoke`（`session.admin-revoke`）。
- `device-service/src/main/java/com/plagod/service/impl/PortalSessionServiceImpl.java`：
  `PortalSessionServiceImpl#authorize`（`session.portal-authorize`）。
- `device-service/src/main/java/com/plagod/service/impl/MacBlacklistServiceImpl.java`：
  `MacBlacklistServiceImpl#addBlacklist`（`blacklist.add`）。
- `device-service/src/main/java/com/plagod/service/impl/DeviceCommandServiceImpl.java`：
  `DeviceCommandServiceImpl#restoreDevice`（`device.restore`）、
  `DeviceCommandServiceImpl#createDevice`（`device.create`）、
  `DeviceCommandServiceImpl#updateDevice`（`device.update`）、
  `DeviceCommandServiceImpl#deleteDevice`（`device.delete`）、
  `DeviceCommandServiceImpl#allowDevice`（`device.allow`）、
  `DeviceCommandServiceImpl#kickDevice`（`device.kick`）、
  `DeviceCommandServiceImpl#allowClient`（`device.allow-client`）、
  `DeviceCommandServiceImpl#removeBlacklist`（`blacklist.remove`）。
- `device-service/src/main/java/com/plagod/service/impl/DeviceWifiConfigServiceImpl.java`：
  `DeviceWifiConfigServiceImpl#stageCandidate`（`device.wifi.stage`）。
- `device-service/src/main/java/com/plagod/service/impl/ManualDeviceControlServiceImpl.java`：
  `ManualDeviceControlServiceImpl#disconnectMac`
  （`device.manual-disconnect-mac`）、
  `ManualDeviceControlServiceImpl#blockTraffic`
  （`device.manual-block-traffic`）。

`MONITOR` 冻结 13 个入口：

- `monitor-service/src/main/java/com/plagod/service/impl/ClientLocationServiceImpl.java`：
  `ClientLocationServiceImpl#report`（`location.report`）、
  `ClientLocationServiceImpl#grantAuthorization`
  （`location.consent.grant`）、
  `ClientLocationServiceImpl#revokeAuthorization`
  （`location.consent.revoke`）、
  `ClientLocationServiceImpl#clearOwnedHistory`
  （`location.history.clear`）。
- `monitor-service/src/main/java/com/plagod/service/impl/AlertEventServiceImpl.java`：
  `AlertEventServiceImpl#handle`（`alert.handle`）。
- `monitor-service/src/main/java/com/plagod/service/impl/GeofenceAdminServiceImpl.java`：
  `GeofenceAdminServiceImpl#create`（`geofence.create`）、
  `GeofenceAdminServiceImpl#update`（`geofence.update`）、
  `GeofenceAdminServiceImpl#toggle`（`geofence.toggle`）、
  `GeofenceAdminServiceImpl#delete`（`geofence.delete`）。
- `monitor-service/src/main/java/com/plagod/service/impl/AccessRuleServiceImpl.java`：
  `AccessRuleServiceImpl#create`（`rule.create`）、
  `AccessRuleServiceImpl#update`（`rule.update`）、
  `AccessRuleServiceImpl#delete`（`rule.delete`）、
  `AccessRuleServiceImpl#toggleEnabled`（`rule.toggle`）。

`mqtt-protocol-v1` 的最终规范化 SHA-256 为
`26ABC67B1DCA9A99173D079359B87C57366F74D9D243728EDFB4AE52A5E8AE87`。
后端与固件本地副本按 UTF-8、LF 换行规范化后必须得到该值；原始文件换行符
不同不构成协议内容差异。

受保护接口使用：

```http
Authorization: Bearer {JWT}
```

P-2 起 Access JWT 固定约 15 分钟，包含 `jti`、`sid`、`sessionSecurityVersion`
和当前租户上下文；同一个有效 Access JWT 可以重复调用普通 API，不是每请求一次性
Token。7 天免登录由服务端 Refresh Session 承担，浏览器只通过 HttpOnly/SameSite
Cookie 持有高熵 Refresh Token，服务端只保存哈希。普通 refresh 不递增
`sessionSecurityVersion`；风险 step-up 成功后递增，使风险事件前签发的 Access 和
operation token 永久失效。

角色 `0`、`1` 可访问管理员接口，角色 `2` 只能访问本人资源。HTTP `429` 表示限流拒绝，并返回 `Retry-After`。

## 2. 匿名认证接口

```text
POST /auth/register                         注册
POST /auth/login                            密码登录
POST /auth/code-login                       验证码登录
POST /auth/refresh                          通过 HttpOnly Cookie 旋转 Refresh Token 并续发 Access JWT
POST /auth/refresh/step-up                  环境风险命中后使用当前账号验证码复核并旋转会话
POST /auth/logout                           撤销当前 Refresh family 并清除 Cookie
POST /auth/codes                            发送验证码
POST /auth/reset-password                   重置密码（新密码不能与当前密码相同）
GET  /auth/oauth/providers                  查询 OAuth Provider 可用性

GET  /auth/oauth/{provider}/authorize       发起社交登录
GET  /auth/oauth/{provider}/callback        OAuth Provider 回调
POST /payment/callbacks/local-demo          本地 Demo 支付回调
GET  /users/avatars/{filename}              读取公开头像文件
```

`provider` 当前支持 `github`、`qq`、`wechat`。支付回调虽然允许匿名进入 Gateway，但仍由签名和幂等规则保护。

Refresh 风险信号组合命中时返回 HTTP 403，错误标识
`REFRESH_STEP_UP_REQUIRED`，服务端保留 HttpOnly Refresh Cookie。客户端先通过
`POST /auth/codes`、`scene=step_up` 向当前账号联系方式发送验证码，再调用
`POST /auth/refresh/step-up`；只有复核成功才清除风险态并旋转 Refresh Token。
401 类 Refresh 拒绝才清除 Cookie。

## 3. 登录用户与社交身份

```text
POST   /auth/account-switch                                  验证历史账号并撤销旧 family 后建立新会话
POST   /auth/account-switch/codes                            按历史 userId 和 phone/email 渠道发送验证码
POST   /auth/operation-tokens                                签发短时、限定 purpose 的高风险一次性凭证
GET    /users/{userId}                                      查询本人资料
PUT    /users/{userId}                                      修改本人资料
POST   /users/{userId}/avatar                               上传本人头像
POST   /users/{userId}/purge-requests                       非超管用户申请彻底删除本人账号
GET    /users/{userId}/social-identities                    查询已绑定社交身份
DELETE /users/{userId}/social-identities/{identityId}       解绑社交身份
GET    /auth/oauth/{provider}/bind                           发起社交身份绑定
```

路径中的 `userId` 必须与 JWT 中的用户一致，不能通过修改路径访问他人资源。

历史账号切换接口不接收或返回完整手机号/邮箱。前端只保存 `userId`、脱敏账号标识和可用渠道；后端按 `userId + channel` 定位真实联系方式并执行现有验证码频率限制。
账号切换同时校验 Bearer Access JWT 的 `sid` 与 HttpOnly Cookie 所属 family，
不一致时返回 409，不能撤销另一个标签页或旧身份的会话。

## 4. 权益、订单、支付与退款

```text
GET  /entitlements/products                         查询可购买产品
GET  /entitlements/me                               查询当前权益
GET  /entitlements/purchases                        查询购买记录
GET  /entitlements/usage-logs                       查询权益使用流水

POST /entitlements/orders                           创建订单
GET  /entitlements/orders                           查询本人订单
GET  /entitlements/orders/{orderNo}                 查询订单详情
POST /entitlements/orders/{orderNo}/cancel          取消订单
POST /entitlements/orders/{orderNo}/payments        创建支付单

GET  /entitlements/payments/{paymentNo}             查询支付单
POST /entitlements/payments/{paymentNo}/demo-complete
                                                     完成本地 Demo 支付

POST /entitlements/refunds                          申请退款
GET  /entitlements/refunds                          查询本人退款
GET  /entitlements/refunds/{refundNo}               查询退款详情
```

以上本人订单、支付、退款、购买记录和使用流水均以 Gateway 注入的可信
`X-Tenant-Id` 与当前用户联合过滤；其他租户的业务编号按不存在处理。
固定时长商品为 5/24/100/300 小时，订阅商品为 1/3/6/12 个自然月。
订单金额、`pricingVersion`、`grantMonths`、参照直购金额、订阅比例和周期折扣
均由服务端计算并保存快照，客户端金额不参与订单定价。未配置可用
`PaymentChannelAdapter` 时创建支付返回 503 和 `PAYMENT_CHANNEL_UNAVAILABLE`，
不会创建支付记录或履约；渠道下线后，已完成的历史支付记录仍可查询。

## 5. Portal Session 与流量

```text
POST /sessions/portal-authorize                     申请 Portal 授权
GET  /sessions/{sessionId}/portal-status            查询本人授权执行状态
GET  /sessions                                      查询本人 Session
POST /sessions/{sessionId}/logout                   主动注销本人 Session

GET  /traffic                                       查询本人流量记录
GET  /client-signals                                查询本人客户端信号记录
```

Portal 状态包含 Session 状态、当前设备命令状态和真实 `requestId`，不能仅根据命令是否入队判断固件已执行。

## 6. 定位与授权

```text
POST   /locations/sessions/{sessionId}/report       上报 ACTIVE Session 位置
GET    /locations                                   查询本人位置历史
GET    /locations/consent                           查询定位授权
POST   /locations/consent                           授予或更新定位授权
DELETE /locations/consent                           撤销定位授权
DELETE /locations/history                           删除本人位置历史
```

位置上报必须同时满足本人 Session、ACTIVE 状态、节点、MAC 和定位授权约束。

### 本人租户与上下文

```text
GET  /tenants/me
GET  /tenants/current
POST /auth/tenant-context/switch
POST /auth/platform-context
POST /auth/platform-context/tenants/{tenantId}
```

新注册或 OAuth 首次创建用户的默认成员 Outbox 尚未成功时，密码登录、验证码登录和 OAuth 回调返回 `accountState=TENANT_MEMBERSHIP_PENDING` 且 `token=null`；前端只能进入受限账户页。补偿成功后重新登录才签发 JWT。role=0 平台登录不受该状态阻塞。

role=0 默认登录进入 `PLATFORM`，通过专用接口显式进入 `PLATFORM_TENANT` 并提供审计原因；普通用户只能切换到自己的 ACTIVE 成员关系。租户切换会签发新的 Access JWT 并更新服务端 session 当前上下文，旧上下文 Access JWT 不再被 Gateway 接受。

## 7. 管理员接口

以下接口仅允许角色 `0`、`1` 访问。

平台租户运营接口只允许 role=0，role=1 即使直接构造请求也返回 403：

```text
GET  /admin/platform/tenants
POST /admin/platform/tenants
GET  /admin/platform/tenants/{tenantId}
PUT  /admin/platform/tenants/{tenantId}
PUT  /admin/platform/tenants/{tenantId}/status
GET  /admin/platform/tenants/{tenantId}/members
GET  /admin/platform/saas-plans
```

租户编码创建后不可修改。`default-tenant` 在首版迁移期间不可停用；P-1 新建租户没有有效订阅时返回 `NO_ACTIVE_SUBSCRIPTION`，不能伪装为可用套餐。

### 概览与用户

```text
GET /admin/overview; GET /admin/dashboard
GET /admin/users; GET /admin/users/stats; GET /admin/users/{userId}
PUT /admin/users/{userId}; PUT /admin/users/{userId}/status
DELETE /admin/users/{userId}; DELETE /admin/users/{userId}/purge
POST /admin/users/{userId}/purge-requests
GET /admin/users/operation-requests
PUT /admin/users/operation-requests/{id}/review
GET /admin/users/{userId}/entitlement
GET /admin/users/{userId}/entitlement/purchases
GET /admin/users/{userId}/entitlement/usage-logs
POST /admin/users/{userId}/entitlement/adjustments
POST /admin/users/{userId}/entitlement/unlimited-adjustments
POST /admin/users/{userId}/entitlement/reward-orders
```

`/admin/users/**` 当前是全局账号管理接口，仅允许 `role=0`。租户工作区不能把
全局 `sys_user` 列表当作当前组织成员列表；组织成员身份以
`t_tenant_member` 为准，租户成员管理必须使用独立的 tenant-service 契约。
在该契约完成前，前端不展示旧的全局用户管理和敏感操作审批入口。

`unlimited-adjustments` 仅允许 role=0，请求体为
`requestId`、`action=GRANT|REVOKE`、`reason`。相同租户内稳定 `requestId`
重复调用不重复改变权益；role=1/2 返回 403。无限权益只跳过用户时长扣减，
设备侧仍使用最多 20 秒的滚动租约，撤销、停用或注销不会获得永久 TTL。
奖励订阅使用 `grantMonths=1|3|6|12`，不再使用 `grantSeconds` 表示自然月。

账号删除规则：role=0 超级管理员不能通过逻辑删除、直接物理删除、删除申请或审批路径删除；role=1 普通管理员允许被 role=0 删除，也可为自己的账号提交删除申请，但不能删除 role=0/1 管理员；role=2 普通用户按现有管理员权限删除。删除申请审批仅允许 role=0，批准时会再次核验目标当前角色。

### 设备、黑名单与命令

```text
POST /admin/devices; GET /admin/devices; GET /admin/devices/stats
GET /admin/devices/{nodeId}; PUT /admin/devices/{nodeId}
DELETE /admin/devices/{nodeId}; POST /admin/devices/{nodeId}/restore
POST /admin/devices/{deviceCode}/allow
POST /admin/devices/{deviceCode}/kick
POST /admin/devices/{deviceCode}/disconnect-mac
POST /admin/devices/{deviceCode}/block-traffic
POST /admin/devices/{deviceCode}/wifi-config/candidate
GET  /admin/devices/{deviceCode}/wifi-config/latest
GET  /admin/devices/{deviceCode}/wifi-config/{requestId}
GET /admin/devices/blacklist; POST /admin/devices/blacklist
DELETE /admin/devices/blacklist/{mac}
GET /admin/device-commands
```

六类生产 MQTT 命令都携带全局唯一 `requestId`。固件保存最近 16 条命令终态；
QoS 1 重投、MQTT 重连或 KICK 重启后收到相同 `requestId` 时不重复执行，
只重新发布原 `command-result`。KICK `reason` 为可选字段，最多 255 个 UTF-8
字节，不再受 MAC 长度限制。

### Session、流量、规则、告警和审计

```text
GET /admin/sessions; POST /admin/sessions/{sessionId}/revoke
GET /admin/traffic; GET /admin/client-signals
GET /admin/rules; GET /admin/rules/{id}; POST /admin/rules
PUT /admin/rules/{id}; DELETE /admin/rules/{id}
PATCH /admin/rules/{id}/enabled
GET /admin/alerts; GET /admin/alerts/{id}
PATCH /admin/alerts/{id}/handle
GET /admin/audits; GET /admin/audits/{id}
GET /admin/locations
```

### 权益、分析、GIS 与围栏

```text
GET /admin/entitlements/refunds
GET /admin/entitlements/refunds/{refundNo}
PUT /admin/entitlements/refunds/{refundNo}/review
POST /admin/entitlements/refunds/{refundNo}/demo-result

GET /admin/analytics/signals
GET /admin/analytics/traffic
GET /admin/analytics/alerts-rules

GET /admin/gis/trajectory
GET /admin/gis/stay-points
GET /admin/gis/heatmap
GET /admin/gis/node-coverage

POST /admin/geofences; GET /admin/geofences
GET /admin/geofences/{fenceId}; PUT /admin/geofences/{fenceId}
PATCH /admin/geofences/{fenceId}/enabled
DELETE /admin/geofences/{fenceId}
GET /admin/geofences/events
```

## 8. WebSocket 告警

```text
WS /ws/alerts
```

只允许管理员连接。浏览器必须经 Gateway `8080` 连接，不能直连 monitor-service `8384`；浏览器通过子协议向 Gateway 携带 JWT：

```text
Sec-WebSocket-Protocol: access_token, {JWT}
```

Gateway 校验 JWT 和管理员角色后删除原始 JWT，只向 monitor-service 转发 `access_token` 子协议标记、可信身份头和 Gateway Token。monitor-service 先验证服务凭据，再使用 Gateway 注入的身份建立连接。

退款申请的 `purchaseId` 是字符串业务标识，对应购买记录的唯一 `order_no`。请求体字段为 `requestId`、`purchaseId`、`reason`；数据库 `purchase_id` 仅是内部关联主键，不作为用户输入或公开购买 ID。

服务端使用应用层 `PING`，客户端回复 `PONG`；长期无响应的连接会被关闭并由客户端退避重连。

## 9. Gateway 健康检查

```text
GET /health/gateway
```

该端点只检查 Gateway 自身是否可响应，不依赖 user-service 或其他业务服务。

## 10. 内部接口边界

所有 `/internal/**` 接口只供微服务之间调用，不属于前端或设备公开 API。

```text
/internal/users/**
/internal/user-accounts/**
/internal/entitlements/**
/internal/social-identities/**
/internal/location-sessions/**
/internal/analytics/**
/internal/monitor/**
/internal/admin/**
/internal/auth/sessions/**
/internal/auth/operation-tokens/consume
/internal/tenants/context/resolve
/internal/tenants/context/validate
```

`/internal/user-accounts/**` 是 Auth 调用 User 权威账号持久化能力的受控契约：

```text
POST /internal/user-accounts
GET  /internal/user-accounts/{userId}
GET  /internal/user-accounts/{userId}/authentication
GET  /internal/user-accounts/login
POST /internal/user-accounts/password
POST /internal/user-accounts/{userId}/default-membership/dispatch
```

User 是 `sys_user`、账号命令收据和默认成员 Outbox 的唯一直接写入方。Auth
只通过上述契约创建账号、读取认证快照、条件替换密码和触发默认成员投递；
请求携带幂等键及 fingerprint，跨服务调用不包含在 Auth 本地事务中。

`POST /internal/entitlements/lease` 的首次租约只接受可信
`X-Tenant-Id` 上下文；无 Servlet 请求上下文的后台续租必须携带已持久化的
`entitlementId`，由 user-service 校验权益与用户并反查租户。请求体不能自行指定租户。

`POST /internal/entitlements/lease` 的请求体 `requestId` 是该租约业务的
HTTP 幂等键，不等于链路 `X-Request-Id`。首次与重放均由 User 私有 Receipt
返回稳定业务结果；重放仅把 `duplicate` 置为 `true`，不得再次扣减权益或
写入 usage log。

这些接口依赖 `WIFI_INTERNAL_TOKEN` 或可信 Gateway 请求机制。禁止在 Gateway 增加 `/internal/**` 路由，也禁止客户端自行构造 `X-User-*`、`X-Gateway-Token` 或内部 Token。

Gateway 会先删除外部请求伪造的 `X-Session-Id`、`X-Token-Id`、`X-Context-Type`、`X-Tenant-*` 和 `X-Platform-Authorities`，完成 session、`jti` 与 tenant/member 双版本校验后再注入可信值。业务写请求在下游服务再次实时调用 tenant-service 校验；校验依赖不可用时 fail closed 返回 503。
