# Wifi Manager 后端部署说明

安全配置后续如何修改、修改后影响哪些服务，以及各凭据保护的边界，统一参见 [安全配置与密钥操作手册](security-configuration-operations.md)。

## 1. 运行环境

- JDK 8
- MySQL 8
- Redis
- Nacos 2.x
- MQTT Broker
- Maven Wrapper

对外只开放 Gateway 端口 `8080`。`8381` 至 `8386` 以及 MySQL、Redis、Nacos、MQTT 应限制在可信网络内。

## 2. 准备配置

`deploy/backend.env.example` 是完整变量模板。Spring Boot 2.3 不会自动读取环境文件，必须在启动 JAR 前显式加载，或者由系统服务使用 `EnvironmentFile` 注入。加载器不再默认读取仓库 `.env`。本地 IDEA 的首选无插件入口是 `deploy/start-idea-with-env.ps1 -EnvPath <仓库外环境文件>`；EnvFile 插件只读取文件，不执行仓库校验脚本。

复制模板到仓库外的私密目录，例如：

```text
<安全配置目录>\backend.env
```

替换所有 `CHANGE_ME`，不要把真实配置写回示例文件。

用户 JWT 只由 Auth 签发、Gateway 验证。两者必须位于相同 Nacos namespace/group，并加载同一个 Data ID `wifi-jwt.yml`。不要再设置 `JWT_SECRET` 环境变量，也不要把 JWT 密钥配置到其他微服务。生产 Nacos 必须先开启鉴权并修改默认管理员密码；鉴权关闭时 `NACOS_USERNAME/PASSWORD` 不会保护配置，匿名客户端可以读取 JWT 密钥。

在 Nacos 创建或更新 `wifi-jwt.yml`，结构必须与 [wifi-jwt.example.yml](../deploy/nacos/wifi-jwt.example.yml) 一致：

```yaml
wifi:
  jwt:
    secret: 替换为至少32字节的高熵随机密钥
    expiration-millis: 86400000
```

JWT 密钥不支持运行期热刷新。轮换时必须协调重启 Auth 和 Gateway，已有 JWT 会失效。缺少配置、长度不足、示例值或首尾空白都会令服务启动失败，避免静默回退到不同密钥。

Gateway 验证浏览器 JWT 后会剥离原始凭证，只向下游注入可信身份；monitor-service 不加载或解析 JWT。`WIFI_GATEWAY_TOKEN` 必须在 Gateway 与下游服务中保持一致，且不能与 `WIFI_INTERNAL_TOKEN` 使用相同值；两者均不少于 16 字节。

Gateway/Auth 继续使用 `WIFI_ALLOWED_ORIGIN` 与 `WIFI_ALLOWED_ORIGIN_ALT`；monitor-service 的告警 WebSocket 使用逗号分隔的 `WIFI_ALLOWED_ORIGINS`。开发 LAN 必须显式加入手机实际访问的 Origin，生产只填写主站和 Portal 的正式 HTTPS Origin，禁止使用 `*`。告警 WebSocket 必须经 Gateway `8080` 转发，直连 monitor `8384` 会因缺少 Gateway 可信身份而被拒绝。

Windows PowerShell 加载变量：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\deploy\load-env.ps1 -Path "<安全配置目录>\backend.env"
.\deploy\verify-nacos-auth.ps1
```

第一条命令加载并校验变量，第二条命令证明 `wifi-jwt.yml` 拒绝匿名读取且当前账号能够读取正确配置。生产环境两条命令都必须成功。变量只对当前 PowerShell 及其启动的 JAR 生效；每个新终端都要先执行加载器，再启动对应服务。IDEA/EnvFile 的具体设置、JBR 21 与项目 JDK 8 的区别见[安全配置与密钥操作手册](security-configuration-operations.md#11-intellij-idea-启动时加载-env)。

Linux Shell 加载变量：

```sh
. ./deploy/load-env.sh /etc/wifi-manager/backend.env
```

必须使用 `.` 或 `source`，直接执行脚本无法把环境变量留在当前 Shell。两个加载器都会拒绝 `JWT_SECRET`、`CHANGE_ME`、示例域名、过短密钥以及相同的 Gateway/Internal Token。

`NACOS_DISCOVERY_IP` 是服务向 Nacos 注册并供其他服务调用的地址，不是 Nacos Server 地址。单机开发中七个服务和调用方都在同一台电脑时填 `127.0.0.1`；多节点生产中，每个节点必须使用当前节点可被其他节点访问的固定私网 IP，并为该节点上的所有服务注入同一个值。禁止留空依赖自动网卡选择，多网卡、热点或虚拟网卡切换会使服务注册到不可达地址。Nacos 显示 `healthy=true` 只代表实例仍在续约，不证明其他节点能够连接该 IP 和端口。

修改 `NACOS_DISCOVERY_IP` 后必须重启该节点的全部 Wifi Manager 服务，并在 Nacos 服务列表逐一核对 `gateway-service`、`auth-service`、`user-service`、`device-service`、`monitor-service`、`admin-service` 和 `tenant-service` 的实例 IP、端口。随后至少通过 Gateway 执行一次真实的跨服务调用；仅检查进程启动或 Nacos healthy 状态不足以完成验收。

使用 systemd 时不需要运行加载器，可以在每个服务单元中直接配置：

```ini
[Service]
EnvironmentFile=/etc/wifi-manager/backend.env
ExecStart=/usr/bin/java -jar /opt/wifi-manager/gateway-service-0.0.1-SNAPSHOT.jar
```

每个微服务使用独立单元和独立 `ExecStart`，但可以共享同一份受限权限的 `backend.env`。

## 3. 构建

在后端仓库根目录执行：

```powershell
.\mvnw.cmd clean package
```

## 4. 数据库迁移

先创建空数据库，但不要手工执行旧 `sql` 初始化脚本：

```sql
CREATE DATABASE wifi
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

### 新空库

保持：

```text
DB_MIGRATION_BASELINE_ON_MIGRATE=false
```

执行：

```powershell
java -jar database-migration\target\database-migration-0.0.1-SNAPSHOT.jar
```

程序会依次执行 `V1.1` 至当前最新版本，成功后自动退出。当前奖励订单功能要求数据库至少达到 `V1.7`；再次运行应显示数据库已是最新版本。

### 接管已有数据库

接管前必须备份并确认现有表结构与当前迁移基线一致。

仅第一次执行时设置：

```powershell
$env:DB_MIGRATION_BASELINE_ON_MIGRATE="true"
java -jar database-migration\target\database-migration-0.0.1-SNAPSHOT.jar
```

接管完成后立即恢复：

```powershell
$env:DB_MIGRATION_BASELINE_ON_MIGRATE="false"
```

禁止长期使用 `true`，否则错误的已有库可能被误登记为完整基线。

## 5. 启动顺序

基础设施启动顺序：

```text
MySQL -> Redis -> Nacos -> MQTT
```

数据库迁移成功后，将下列每个 JAR 注册为独立系统服务，或分别在独立终端中启动。不要在同一个前台 PowerShell 中顺序粘贴执行，否则第一条常驻进程会阻止后续命令运行：

```powershell
java -jar auth-service\target\auth-service-0.0.1-SNAPSHOT.jar
java -jar user-service\target\user-service-0.0.1-SNAPSHOT.jar
java -jar device-service\target\device-service-0.0.1-SNAPSHOT.jar
java -jar monitor-service\target\monitor-service-0.0.1-SNAPSHOT.jar
java -jar admin-service\target\admin-service-0.0.1-SNAPSHOT.jar
java -jar tenant-service\target\tenant-service-0.0.1-SNAPSHOT.jar
java -jar gateway-service\target\gateway-service-0.0.1-SNAPSHOT.jar
```

端口对应关系：

```text
Gateway 8080
Auth 8381
User 8382
Device 8383
Monitor 8384
Admin 8385
Tenant 8386
```

## 6. 运行边界

- 当前 Spring Boot 版本锁定 Flyway 6.4.4；在 MySQL 8.4 上已验证 V1.1 至 V1.6 迁移和重复运行成功，但会出现数据库版本尚未正式验证的警告。该警告不阻塞 Demo，正式生产升级前应重新验证兼容性或统一升级 Flyway 与 Spring Boot。
- `SPRING_PROFILES_ACTIVE=prod` 会关闭 MyBatis SQL 参数输出，并启用各服务 `application-prod.yml`、`bootstrap-prod.yml` 的必需环境变量覆盖和启动级安全校验。
- 加载器会拒绝缺少关键键、空的必需值（包括 `NACOS_DISCOVERY_IP`）、重复键、`CHANGE_ME`、通配 Origin、`DB_MIGRATION_BASELINE_ON_MIGRATE` 非 `false` 和 `REDIS_RATE_LIMIT_ENABLED` 非 `true`。
- Auth 和 Gateway 只加载相同 namespace/group 下的 Nacos `wifi-jwt.yml`；JWT 配置不再接受本地 YAML 默认值或 `JWT_SECRET` 环境变量。
- 生产 Nacos 必须启用鉴权并拒绝匿名配置读取；所有集群节点的 server identity 与 token secret 必须一致，Nacos 管理端口不得暴露到公网。
- 多个 Device 实例必须使用不同的 `MQTT_CLIENT_ID`。
- `WIFI_COMMAND_SECRET_KEY` 必须是 Base64 编码的 32 字节密钥；缺失时只禁用敏感 WiFi 配置命令，不阻止 Device 启动。
- `GITHUB_OAUTH_REDIRECT_URI`、`QQ_OAUTH_REDIRECT_URI` 和 `WECHAT_OAUTH_REDIRECT_URI` 必须指向前端 `/oauth-complete/{provider}` 路由，并与 Provider 控制台登记值完全一致。
- `OAUTH_ALLOWED_RETURN_ORIGIN` 和 `OAUTH_ALLOWED_RETURN_ORIGIN_ALT` 限制 OAuth 完成后的前端返回来源，通常与 `WIFI_ALLOWED_ORIGIN` 保持一致。
- Redis 故障时限流会降级到单实例本地窗口，此时不再具备严格的全局限流能力。
- `local-*-change-me` 和 `CHANGE_ME` 值只能用于本地占位，禁止用于正式环境。
- 信任反向代理前必须确认代理会清除外部传入的转发头，再启用 `WIFI_TRUST_PROXY_HEADERS=true`。
- 使用前端仓库 `deploy/nginx.conf.example` 时，保持 `FORWARD_HEADERS_STRATEGY=none`，由 Gateway 读取 Nginx 覆盖后的 `X-Forwarded-For`；不要同时让 Spring 重写远端地址。
- Nginx 必须覆盖 `X-Forwarded-For`/`X-Real-IP`、清除外部 `X-Gateway-Token`/`X-Internal-Token`/`X-User-*`，并拒绝 `/api/internal/**`。
- 头像目录必须持久化，并授予 User 服务写权限。

## 7. 最小部署检查

```powershell
Test-NetConnection 127.0.0.1 -Port 8080
Test-NetConnection 127.0.0.1 -Port 8381
Test-NetConnection 127.0.0.1 -Port 8382
Test-NetConnection 127.0.0.1 -Port 8383
Test-NetConnection 127.0.0.1 -Port 8384
Test-NetConnection 127.0.0.1 -Port 8385
Test-NetConnection 127.0.0.1 -Port 8386
```

随后通过 Gateway 验证登录、本人资源查询、管理员概览、Session 查询和一次受限接口拒绝。不要绕过 Gateway 直接把内部接口暴露给客户端。
