# Wifi Manager 后端部署说明

## 1. 运行环境

- JDK 8
- MySQL 8
- Redis
- Nacos 2.x
- MQTT Broker
- Maven Wrapper

对外只开放 Gateway 端口 `8080`。`8381` 至 `8385` 以及 MySQL、Redis、Nacos、MQTT 应限制在可信网络内。

## 2. 准备配置

`deploy/backend.env.example` 只是变量清单，Spring Boot 不会自动读取该文件。

复制模板到仓库外的私密目录，例如：

```text
D:\wifi-manager-config\backend.env
```

替换所有 `CHANGE_ME`，不要把真实配置写回示例文件。

`JWT_SECRET` 必须在 Gateway、Auth、Monitor 中保持一致。`WIFI_GATEWAY_TOKEN` 与 `WIFI_INTERNAL_TOKEN` 必须使用不同值，且均不少于 16 字节。

PowerShell 加载变量：

```powershell
$envFile = "D:\wifi-manager-config\backend.env"

Get-Content -LiteralPath $envFile |
    Where-Object { $_ -and -not $_.TrimStart().StartsWith("#") } |
    ForEach-Object {
        $pair = $_.Split("=", 2)
        [Environment]::SetEnvironmentVariable($pair[0], $pair[1], "Process")
    }
```

这些变量只对当前 PowerShell 及其启动的进程生效。

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

程序会依次执行 `V1.1` 至 `V1.6`，成功后自动退出。再次运行应显示数据库已是最新版本。

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
```

## 6. 运行边界

- 当前 Spring Boot 版本锁定 Flyway 6.4.4；在 MySQL 8.4 上已验证 V1.1 至 V1.6 迁移和重复运行成功，但会出现数据库版本尚未正式验证的警告。该警告不阻塞 Demo，正式生产升级前应重新验证兼容性或统一升级 Flyway 与 Spring Boot。
- `SPRING_PROFILES_ACTIVE=prod` 会关闭 MyBatis SQL 参数输出。
- Nacos 中的 `wifi-jwt.yml` 不得与环境变量中的 JWT 密钥冲突。
- 多个 Device 实例必须使用不同的 `MQTT_CLIENT_ID`。
- `WIFI_COMMAND_SECRET_KEY` 必须是 Base64 编码的 32 字节密钥；缺失时只禁用敏感 WiFi 配置命令，不阻止 Device 启动。
- Redis 故障时限流会降级到单实例本地窗口，此时不再具备严格的全局限流能力。
- `local-*-change-me` 和 `CHANGE_ME` 值只能用于本地占位，禁止用于正式环境。
- 信任反向代理前必须确认代理会清除外部传入的转发头，再启用 `WIFI_TRUST_PROXY_HEADERS=true`。
- 头像目录必须持久化，并授予 User 服务写权限。

## 7. 最小部署检查

```powershell
Test-NetConnection 127.0.0.1 -Port 8080
Test-NetConnection 127.0.0.1 -Port 8381
Test-NetConnection 127.0.0.1 -Port 8382
Test-NetConnection 127.0.0.1 -Port 8383
Test-NetConnection 127.0.0.1 -Port 8384
Test-NetConnection 127.0.0.1 -Port 8385
```

随后通过 Gateway 验证登录、本人资源查询、管理员概览、Session 查询和一次受限接口拒绝。不要绕过 Gateway 直接把内部接口暴露给客户端。