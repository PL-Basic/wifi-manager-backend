# Wifi Manager Backend

Wifi Manager Backend 是面向家庭和小型边缘网络场景的 WiFi 管理后端 Demo。

当前版本定位为 Backend Demo 1.0：Java 微服务、Gateway、MySQL、Redis、Nacos、MQTT 与 ESP32 边缘节点组成的单实例或小规模本地系统。核心模块已完成，当前处于最终审计和可复现部署收尾阶段。

## 系统结构

```text
Vue 管理端 -> gateway-service :8080
                 |-> auth-service    :8381
                 |-> user-service    :8382
                 |-> device-service  :8383
                 |-> monitor-service :8384
                 |-> admin-service   :8385
                         |-> MySQL / Redis / Nacos / MQTT Broker
                                                   |-> ESP32 边缘节点
```

Gateway 是外部客户端唯一入口。业务服务之间通过 Feign 和受保护的内部请求通信。

## Maven 模块

```text
wifi-common-api                       共享 DTO、VO 和响应结构
wifi-common-mybatis                   共享实体、Mapper 基础能力
wifi-audit-spring-boot-starter        可选审计能力
wifi-service-security-spring-boot-starter  内部请求安全
wifi-gen                              MyBatis-Plus 代码生成器
gateway-service                      路由、JWT、来源限制、WebSocket、限流
auth-service                         注册、登录、验证码、密码重置、OAuth
user-service                         用户、头像、权益、订单、支付、退款
device-service                       ESP32、Session、MQTT、命令、流量
monitor-service                      规则、告警、审计、GPS/GIS、分析
admin-service                        管理端 BFF
database-migration                   Flyway 数据库迁移入口
```

`admin-service` 只聚合下游服务，不直接拥有业务数据。

## 已完成能力

### 认证、OAuth 和验证码

- 用户名密码、手机或邮箱验证码登录，注册和密码重置。
- 验证码场景、过期、使用状态、发送记录、登录失败和临时锁定。
- Redis 多实例原子限流。
- GitHub、QQ、微信 OAuth，身份绑定、重复回调幂等和已有账号绑定。
- JWT 签发和 Gateway 统一校验。

### 用户、权益和支付

- 用户资料、头像、状态和管理员操作。
- 本人资料 PUT 只允许修改昵称；邮箱和手机号必须经过验证码绑定；头像使用独立上传接口。
- 权益快照、时长订单、本地 Demo 支付回调、退款审核、权益流水和余额扣减。
- 支付回调幂等，重复回调不会重复增加权益。

### ESP32、Session 和 MQTT

- ESP32 节点、Portal Session、名额限制、续租、心跳、断线和关闭。
- `ALLOW`、`REVOKE_ACCESS`、`DISCONNECT_MAC`、`BLOCK_TRAFFIC`、`KICK` 命令。
- MQTT 设备身份校验、命令 Outbox、发布状态、重试、超时和执行结果回传。
- `requestId` 幂等、旧命令并发保护和设备执行失败状态。

### 流量、规则、告警和 GIS

- 流量事件接收、`eventId` 幂等、设备/Session/MAC 归属校验。
- SNI、目标 IP、端口、协议、上下行流量和访问规则匹配。
- 告警生成、抑制、审计和 WebSocket 推送基础能力。
- GPS 基础上报，和真实用户、ACTIVE Session、节点、MAC 关联。
- 轨迹、停留点、热力图和围栏基础查询与分析接口。

## 访问入口和接口语义

Gateway 默认地址：`http://localhost:8080`。

```text
/auth/**          认证、注册、验证码、OAuth
/users/**         本人资料、头像和个人操作
/entitlements/**  权益、订单、支付、退款
/sessions/**      Portal Session 和授权状态
/admin/**         管理端 BFF
/internal/**      仅供服务间调用
```

状态码约定：`400` 参数或业务前置条件错误；`401` 未认证；`403` 无权访问；`404` 资源不存在；`409` 状态冲突或设备离线；`429` 限流；`502` 下游或 MQTT 调用失败；`503` 外部 Provider 不可用；`500` 未预期内部错误。

完整接口见 [docs/backend-api-index.md](docs/backend-api-index.md)。

## 环境变量

生产环境至少配置：

```text
MYSQL_URL MYSQL_USERNAME MYSQL_PASSWORD
JWT_SECRET WIFI_GATEWAY_TOKEN WIFI_INTERNAL_TOKEN
NACOS_SERVER_ADDR NACOS_USERNAME NACOS_PASSWORD NACOS_NAMESPACE NACOS_GROUP
REDIS_HOST REDIS_PORT REDIS_PASSWORD REDIS_DATABASE REDIS_SSL
MQTT_BROKER_URL MQTT_CLIENT_ID MQTT_USERNAME MQTT_PASSWORD
WIFI_COMMAND_SECRET_KEY WIFI_ALLOWED_ORIGIN WIFI_TRUST_PROXY_HEADERS WIFI_AVATAR_DIR
```

OAuth、短信、邮件和本地 Demo 支付配置见 [deploy/backend.env.example](deploy/backend.env.example)。真实环境必须更换 JWT、内部请求、Redis、MQTT 和 WiFi 命令密钥。

## 数据库迁移

新环境只创建空数据库：

```sql
CREATE DATABASE wifi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 Flyway：

```powershell
D:\java\maven\apache-maven-3.9.13\bin\mvn.cmd -pl database-migration -am package
java -jar database-migration\target\database-migration-0.0.1-SNAPSHOT.jar
```

新库执行全部迁移版本。已有开发库首次接管时按 [docs/backend-deployment.md](docs/backend-deployment.md) 登记基线，不能用历史 SQL 覆盖现有数据。`sql` 目录仅用于基线核对。

## 启动顺序

先启动：`MySQL`、`Redis`、`Nacos`、`MQTT Broker`；再启动：`auth-service`、`user-service`、`device-service`、`monitor-service`、`admin-service`、`gateway-service`。

## 编译与测试

```powershell
D:\java\maven\apache-maven-3.9.13\bin\mvn.cmd compile
D:\java\maven\apache-maven-3.9.13\bin\mvn.cmd -pl gateway-service,device-service,user-service -am test
```

部署说明、接口清单和配置模板：

```text
docs/backend-deployment.md
docs/backend-api-index.md
deploy/backend.env.example
```

## Demo 边界

当前 Demo 已完成单实例主流程和本地可复现验证。多地域容灾、OAuth Provider 熔断、短信 PENDING 长期恢复、大规模归档、极端并发最终一致性、MQTT TLS/ACL/密钥轮换、JWT 在线轮换、真实支付渠道和 SaaS 多租户属于后续生产化边界，不影响当前 Demo 主流程。

当前范围不包含 OTA。

## 代码与数据边界

- `auth-service` 负责认证、验证码和 OAuth。
- `user-service` 负责用户和权益数据。
- `device-service` 负责设备、Session、黑名单、命令和流量接入。
- `monitor-service` 负责规则、告警、审计和 GIS。
- `admin-service` 只作为 BFF 聚合接口。
- 用户身份必须来自 JWT，不能通过伪造 `X-User-*` 请求头获得。
- 设备事件必须使用 `deviceCode`、`sessionId`、`mac`、`eventId` 或 `requestId` 进行可信关联。

## 当前状态

Backend Demo 1.0 的核心模块已经完成。当前收尾内容是最终代码审计、最小回归、数据库迁移、部署文档和 README 核对；完成后后端开发结束，进入前端联调和项目文档整理。
