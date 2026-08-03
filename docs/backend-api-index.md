# Wifi Manager 后端接口清单

## 1. 访问入口

客户端、前端和第三方回调统一通过 Gateway：

```text
http://{gateway-host}:8080
```

除 WebSocket 和头像文件外，HTTP 接口统一返回：

```json
{"code":200,"message":"操作成功","data":{}}
```

受保护接口使用：

```http
Authorization: Bearer {JWT}
```

角色 `0`、`1` 可访问管理员接口，角色 `2` 只能访问本人资源。HTTP `429` 表示限流拒绝，并返回 `Retry-After`。

## 2. 匿名认证接口

```text
POST /auth/register                         注册
POST /auth/login                            密码登录
POST /auth/code-login                       验证码登录
POST /auth/codes                            发送验证码
POST /auth/reset-password                   重置密码（新密码不能与当前密码相同）
GET  /auth/oauth/providers                  查询 OAuth Provider 可用性

GET  /auth/oauth/{provider}/authorize       发起社交登录
GET  /auth/oauth/{provider}/callback        OAuth Provider 回调
POST /payment/callbacks/local-demo          本地 Demo 支付回调
GET  /users/avatars/{filename}              读取公开头像文件
```

`provider` 当前支持 `github`、`qq`、`wechat`。支付回调虽然允许匿名进入 Gateway，但仍由签名和幂等规则保护。

## 3. 登录用户与社交身份

```text
GET    /users/{userId}                                      查询本人资料
PUT    /users/{userId}                                      修改本人资料
POST   /users/{userId}/avatar                               上传本人头像
POST   /users/{userId}/purge-requests                       非超管用户申请彻底删除本人账号
GET    /users/{userId}/social-identities                    查询已绑定社交身份
DELETE /users/{userId}/social-identities/{identityId}       解绑社交身份
GET    /auth/oauth/{provider}/bind                           发起社交身份绑定
```

路径中的 `userId` 必须与 JWT 中的用户一致，不能通过修改路径访问他人资源。

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

## 7. 管理员接口

以下接口仅允许角色 `0`、`1` 访问。

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
POST /admin/users/{userId}/entitlement/reward-orders
```

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
/internal/entitlements/**
/internal/social-identities/**
/internal/location-sessions/**
/internal/analytics/**
/internal/monitor/**
/internal/admin/**
```

这些接口依赖 `WIFI_INTERNAL_TOKEN` 或可信 Gateway 请求机制。禁止在 Gateway 增加 `/internal/**` 路由，也禁止客户端自行构造 `X-User-*`、`X-Gateway-Token` 或内部 Token。
