# Wifi Manager Backend

Wifi Manager Backend 是一个面向家庭、小型商户和边缘设备场景的 WiFi 管控后端项目。项目目标是构建一套云边协同的 WiFi 管理系统：云端负责认证、用户、设备、规则、告警、审计等业务能力，边缘设备负责接入真实网络环境并执行控制指令。

当前版本主要作为开源基点项目，重点完成认证体系、验证码体系、登录安全保护、用户与设备管理基础能力，并为后续 ESP32 网关、Portal 认证和 SaaS 化管理能力打基础。

## 项目定位

项目设想中，系统由三部分组成：

- 云端服务：负责统一认证、用户管理、设备管理、规则管理、告警审计和指令下发。
- 边缘设备：例如 ESP32 网关或其他边缘节点，接收云端指令并控制实际网络访问。
- 管理前端：供管理员或客户查看用户、设备、规则、会话、流量、告警等信息。

后续目标是扩展为“客户可购买使用的云边协同 WiFi 管控 SaaS”，让非专业用户也能通过简单配置完成自家网络或设备系统的管理。

## 技术栈

- Java 8
- Spring Boot
- Spring Cloud
- Spring Cloud Gateway
- Spring Cloud Alibaba Nacos
- MyBatis-Plus
- MySQL
- JWT
- Spring Security
- MQTT
- Maven

## 模块说明

```text
Wifi_Manager
├── gateway-service                  网关服务，负责路由与 JWT 鉴权
├── auth-service                     认证服务，负责登录、注册、验证码、重置密码
├── user-service                     用户服务，负责用户资料、头像、状态管理等
├── device-service                   设备服务，负责 ESP32 节点、会话、黑名单、流量、MQTT 指令
├── monitor-service                  监控服务，负责规则、告警、审计、定位、WebSocket 推送
├── admin-service                    管理后台 BFF，聚合用户、设备、监控等服务
├── wifi-common-api                  通用 API、DTO、工具类
├── wifi-common-mybatis              通用 MyBatis 实体与配置
├── wifi-audit-spring-boot-starter   审计 starter
└── sql                              数据库初始化脚本
```

## 已完成功能

### 认证与账号

- 用户名密码登录
- 手机号/邮箱登录
- 邮箱验证码发送
- 验证码登录
- 注册账号
- 注册时必须至少绑定手机号或邮箱
- 忘记密码 / 重置密码
- JWT token 签发与校验
- 网关接口放行与鉴权

### 验证码体系

- 验证码记录表 `t_verify_code`
- 按手机号/邮箱区分接收方
- 按业务场景区分验证码用途
- 验证码过期时间
- 验证码状态：未使用、已使用、已过期
- 发送 IP 与验证 IP 记录
- 发送状态记录：待发送、发送成功、发送失败
- 发送失败原因记录
- 发送频率限制
- 定时清理验证码记录

### 登录失败保护

- 登录失败记录表 `t_login_fail_record`
- 以“账号 + 登录类型 + IP”为保护维度
- 连续密码错误次数统计
- 失败统计时间窗口
- 临时锁定
- 锁定过期后自动重置
- 正确登录后清理失败记录
- 登录失败记录定时清理
- 最大失败次数、锁定时间、统计窗口、记录保留天数配置化

### 设备与监控基础能力

- ESP32 节点管理
- 黑名单管理
- 会话管理
- 流量日志
- MQTT 指令主题规划
- 访问规则
- 告警事件
- 审计记录
- WebSocket 告警推送
- GPS/定位数据接口基础能力

## 环境要求

- JDK 8
- Maven 3.x
- MySQL 8.x
- Nacos 2.x
- MQTT Broker，例如 Mosquitto

## 环境变量

为了避免敏感信息提交到仓库，项目使用环境变量覆盖本地配置。

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_USERNAME` | MySQL 用户名 | `root` |
| `MYSQL_PASSWORD` | MySQL 密码 | 空 |
| `JWT_SECRET` | JWT 签名密钥，auth-service 和 gateway-service 必须一致 | 本地开发默认值 |
| `MAIL_HOST` | 邮箱 SMTP 地址 | `smtp.qq.com` |
| `MAIL_PORT` | SMTP 端口 | `587` |
| `MAIL_USERNAME` | 发件邮箱账号 | 空 |
| `MAIL_PASSWORD` | 邮箱 SMTP 授权码 | 空 |
| `MQTT_BROKER_URL` | MQTT Broker 地址 | `tcp://localhost:1883` |
| `MQTT_CLIENT_ID` | MQTT 客户端 ID | `device-service` |
| `WIFI_AVATAR_DIR` | 头像上传目录 | `uploads/avatars` |

PowerShell 示例：

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="your_mysql_password"
$env:JWT_SECRET="your-jwt-secret-at-least-32-bytes-long"
$env:MAIL_USERNAME="your_email@qq.com"
$env:MAIL_PASSWORD="your_smtp_auth_code"
```

## 数据库初始化

先创建数据库：

```sql
create database if not exists wifi default charset utf8mb4 collate utf8mb4_unicode_ci;
```

然后按需执行 `sql` 目录下的脚本：

```text
sql/init-database.sql
sql/auth-service.sql
sql/user-service.sql
sql/device-service.sql
sql/monitor-service.sql
sql/user-operation-request.sql
```

## 启动顺序

建议先启动基础设施：

```text
MySQL
Nacos
MQTT Broker
```

再启动后端服务：

```text
auth-service
user-service
device-service
monitor-service
admin-service
gateway-service
```

网关默认端口：

```text
http://localhost:8080
```

## 编译

编译主要服务：

```powershell
.\mvnw.cmd -pl auth-service,user-service,monitor-service -am compile
```

完整编译：

```powershell
.\mvnw.cmd compile
```

## 前端项目

前端仓库位于：

```text
F:\MyProject\vue\wifi
```

前端基于 Vue 3 + Vite，负责登录、注册、个人中心、用户管理、设备管理、规则、告警、审计、定位展示等页面。

## 后续规划

- 完善 ESP32 Portal 认证流程
- 完善云端到边缘设备的指令确认机制
- 扩展客户/租户管理能力
- 增加设备绑定、客户开通、套餐或授权控制
- 完善 README、接口文档和部署文档
- 增加自动化测试
- 完善前端管理台交互体验

## 说明

该项目仍处于持续开发阶段，当前版本更偏向学习、实践和开源基点建设。项目重点不只是完成单个功能，而是逐步形成一个包含认证、网关、数据库设计、前端联调、设备接入规划和安全保护策略的完整系统。
