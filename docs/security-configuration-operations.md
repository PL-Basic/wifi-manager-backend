# Wifi Manager 安全配置与密钥操作手册

## 1. 配置来源总则

安全配置必须按所属边界放置，禁止为了“方便”在多个位置重复配置同一个值。

- 用户 JWT：只配置在 Nacos 的 `wifi-jwt.yml`，只允许 Auth 和 Gateway 加载。
- Gateway/Internal Token、数据库、Redis、MQTT、OAuth、验证码 Provider 等部署参数：配置在仓库外的受控 `backend.env`，模板为 `deploy/backend.env.example`。
- 浏览器公网入口、TLS、反向代理头清理：配置在 Nginx，模板位于前端仓库 `deploy/nginx.conf.example`。
- `application.yml` 和 `bootstrap.yml` 只保留配置结构、非敏感默认行为以及 Nacos 连接方式，不写真实密钥。
- 不在 Git 仓库、聊天记录、截图或日志中保存真实密钥。

`JWT_SECRET` 已停止使用。部署加载脚本会拒绝仍包含该变量的环境文件。

### 1.1 IntelliJ IDEA 启动时加载外部环境文件

Spring Boot 2.3 不会自动读取环境文件。PowerShell 和 Shell 加载器现在都要求显式传入仓库外路径，不再回退到仓库 `.env`。仅修改文件不会改变 IDEA 已启动服务的配置，必须让每个 Spring Boot Run Configuration 在下一次启动时重新注入这些环境变量。

真实环境文件必须放在 Git 仓库外，例如 `<安全配置目录>\backend.env`。生产或多人共用主机应将该目录放在支持访问控制的文件系统中，并只向部署账号和系统管理员授予读取权限；单人开发机可以沿用当前用户私有目录，不要求为了本项目额外修改整块磁盘或仓库权限。不要在仓库根目录重新创建含真实密钥的 `.env`。

首选方式是不依赖插件，完全退出所有 IDEA 窗口后使用仓库脚本启动一个新 IDEA 进程：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\start-idea-with-env.ps1 `
  -EnvPath "<安全配置目录>\backend.env" `
  -IdeaPath "<IDEA_EXECUTABLE>"
```

脚本会先调用 `load-env.ps1` 完成缺键、重复键、占位值、Token 和 Origin 检查，再调用 `verify-nacos-auth.ps1` 验证 Nacos 鉴权，全部通过后才把变量注入新 IDEA 进程。检测到任何 `idea64.exe` 仍在运行时脚本会拒绝继续，防止 JetBrains 单实例机制复用旧进程而继续使用旧变量。修改外部 `backend.env` 后，停止服务、完全退出 IDEA，再执行同一命令。

EnvFile 是可选方式。插件描述中的 “Requires Java 21” 指运行插件的 IDEA Runtime，不是项目 SDK。是否兼容应查看 `Help -> About` 中的 IDEA Runtime；当前 Auth、Gateway、User、Device、Monitor、Admin 等 Java 8 模块的 SDK 和 Run Configuration 仍选择 JDK 8，后续独立 AI 模块按其 POM 使用 JDK 17。

使用 EnvFile 时：

1. 在 `Settings -> Plugins` 安装并启用 `EnvFile`，随后按插件提示重启 IDEA。
2. 打开 `Run -> Edit Configurations`，逐个选择 Auth、User、Device、Monitor、Admin 和 Gateway 的 Spring Boot 启动配置。
3. 在 `EnvFile` 页签启用该功能，添加 `<安全配置目录>\backend.env`。
4. 确认文件中包含 `SPRING_PROFILES_ACTIVE=prod`；不要同时在 Active profiles、VM options 或 Program arguments 中配置另一个冲突的 profile。
5. 修改文件后停止并重新启动受影响服务。EnvFile 会在每次新启动时重新读取文件，不需要仅为文件修改而重启 IDEA。

EnvFile 只负责注入变量，不会执行 `deploy/load-env.ps1`。应用会在 `prod` 启动阶段再次校验必需配置和服务 Token，但加载器可以在启动 JVM 前一次性报告全部环境文件问题，因此使用插件时仍应先校验外部文件：

```powershell
.\deploy\load-env.ps1 -Path "<安全配置目录>\backend.env"
```

如果 Windows PowerShell 因本机执行策略拒绝运行脚本，可使用仅对本次子进程生效的方式校验；该命令不会修改系统执行策略，但子进程退出后变量也不会保留：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\load-env.ps1 -Path "<安全配置目录>\backend.env"
```

在 Run Configuration 的 `Environment variables` 输入框中手工填写变量也可以，但它与外部文件没有关联，文件后续修改不会自动同步。无论使用哪种加载方式，都不要把真实变量写进可提交的 `.idea` 配置。

### 1.2 `application.yml` 与 `application-prod.yml`

`application.yml` 是所有环境的基础配置。只有激活 `prod` profile 时，Spring Boot 才会额外加载同一服务的 `application-prod.yml`，并用其中同名配置覆盖基础值；未在生产文件中重复的配置继续继承基础文件。因此生产文件不应复制整份 `application.yml`。

当前七个常驻服务都具有生产覆盖。Auth、User、Device、Monitor、Tenant 在 `prod` 下要求显式数据库配置；Auth 和 Gateway 要求显式 Redis 配置；Device 要求显式 MQTT 配置；Gateway、Auth 和 Monitor 要求显式 Origin。Gateway 要求 Gateway Token；六个 Servlet 服务同时要求 Gateway Token 和出站 Internal Token。所有服务的 `bootstrap-prod.yml` 都要求显式 Nacos 连接配置，数据库迁移程序也有独立的生产覆盖。

七个服务的 `bootstrap-prod.yml` 还会把非空 `NACOS_DISCOVERY_IP` 绑定到 `spring.cloud.nacos.discovery.ip`。该变量决定其他微服务实际连接哪个网卡地址，不影响 `NACOS_SERVER_ADDR`。单机开发填写 `127.0.0.1`；多节点生产按节点填写可路由的固定私网 IP。禁止留空让框架自动选择网卡。

生产覆盖仍然只写与基础配置不同或必须显式提供的键，不复制端口、路由、业务阈值等稳定基础配置。JWT 继续只由 Nacos `wifi-jwt.yml` 提供，不写入任何 `application-prod.yml`。

### 1.3 生产防误配门禁

生产启动采用三层门禁：

1. `deploy/load-env.ps1` 和 `deploy/load-env.sh` 在 JVM 启动前检查环境文件。
2. `application-prod.yml` 与 `bootstrap-prod.yml` 使用没有本地默认值的占位符；缺少关键变量时 Spring 启动失败。
3. 应用启动校验拒绝本地示例 Token、首尾空白、相同的 Gateway/Internal Token、关闭可信请求认证，以及 Gateway 关闭 Redis 全局限流。

Servlet 服务区分两种 Internal Token 用途：`wifi.internal.token` 用于出站 Feign；只有 User、Device、Monitor 拥有 `/internal/**` 入站接口并配置 `wifi.security.internal-token`。Auth 和 Admin 不接受 Internal Token 作为入站凭据，避免扩大信任边界。

加载器要求 `DB_MIGRATION_BASELINE_ON_MIGRATE=false` 和 `REDIS_RATE_LIMIT_ENABLED=true`。Redis、MQTT 的用户名或密码允许显式留空，但对应键必须存在；这表示操作者已经明确选择无认证本地设施，而不是因为漏写配置而静默回退。OAuth、短信和邮件等可选 Provider 凭据可以缺省或留空，对应能力会保持不可用。

修改 `NACOS_DISCOVERY_IP` 会影响当前节点上所有服务对外公布的实例地址。修改后停止并重新启动该节点的七个服务，检查 Nacos 实例列表中的 IP 和端口，再通过 Gateway 验证至少一条真实跨服务调用。`healthy=true` 只证明客户端仍向 Nacos 续约；当注册地址指向热点、虚拟网卡或已经失效的私网时，调用仍会超时，因此不能用 healthy 状态代替可达性检查。

本地手机 Portal 联调建议使用：

```env
WIFI_ALLOWED_ORIGIN=http://localhost:5173
WIFI_ALLOWED_ORIGIN_ALT=http://portal.test:5173
WIFI_ALLOWED_ORIGINS=http://portal.test:5173,http://localhost:5173,http://127.0.0.1:5173
WIFI_ALERT_HEARTBEAT_INTERVAL_MILLIS=25000
REDIS_RATE_LIMIT_ENABLED=true
```

前两个变量供 Gateway CORS 使用，复数变量供 Monitor WebSocket 使用。部署到正式域名时必须替换为真实 HTTPS Origin，不能保留本地域名或使用 `*`。

## 2. 请求信任链

浏览器登录后持有 JWT。受保护请求按下面的顺序处理：

```text
浏览器 JWT
-> Gateway 使用 wifi-jwt.yml 验证签名、有效期、用户和角色
-> Gateway 执行路径权限、本人资源约束和限流
-> Gateway 删除浏览器传入的 Authorization、X-User-* 和服务 Token
-> Gateway 注入可信 X-User-*、X-Gateway-Token 和 X-Client-IP
-> 业务服务验证 X-Gateway-Token 后使用可信身份
```

告警 WebSocket 的 JWT 通过 `Sec-WebSocket-Protocol` 到达 Gateway。Gateway 验证后只保留协商协议名 `access_token`，不会把 JWT 继续交给 Monitor。Monitor 再验证 Origin、`X-Gateway-Token`、可信用户身份和管理员角色。

服务间调用使用另一条链路：

```text
调用服务
-> X-Internal-Token
-> 接收服务的 /internal/**
-> TrustedRequestFilter 验证内部 Token
```

Gateway 不路由 `/internal/**`。`X-Gateway-Token` 不能调用内部路径，`X-Internal-Token` 也不能暴露给浏览器。

## 3. JWT 配置与修改

### 3.1 Nacos 鉴权是生产前置条件

`NACOS_USERNAME` 和 `NACOS_PASSWORD` 只是客户端登录凭据。当 `nacos.core.auth.enabled=false` 时，Nacos 不校验它们，它们不能保护配置。关闭鉴权也不会禁止创建或发布配置；如果控制台此时无法发布，应检查控制台请求和 Nacos 服务错误，不能把“开启鉴权”误当成修复编辑功能的手段。

无论使用 standalone 还是集群，只要 Nacos 保存真实 JWT 密钥，匿名客户端就不能读取配置。`nacos.core.auth.enabled=false` 只适用于不保存真实密钥且与其他网络隔离的临时开发实例，不能作为生产部署状态。

生产集群必须在发布真实 JWT 密钥前执行：

1. 备份 Nacos 外部数据库和每个节点的 `conf/application.properties`，选择维护窗口。可在仓库外生成一份所有节点共用的鉴权片段：

```powershell
New-Item -ItemType Directory -Path "<安全配置目录>" -Force
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\generate-nacos-auth-properties.ps1 `
  -OutputPath "<安全配置目录>\nacos-auth.properties"
```

生成器拒绝写入 Git 仓库，也不会把密钥打印到控制台。限制该文件的读取权限，不要提交、截图或发送；轮换时只有明确传入 `-Force` 才允许覆盖。

2. 在所有节点设置生成文件中的 `nacos.core.auth.system.type=nacos`、`nacos.core.auth.enabled=true`、`nacos.core.auth.enable.userAgentAuthWhite=false`、server identity key/value 和 token secret。identity key 必须是合法 HTTP Header 名，identity value 是独立高熵值；token secret 使用 Base64，解码后不少于 32 字节。
3. 所有节点必须使用相同 identity key/value 和 token secret。不要让集群长期处于部分节点开启、部分节点关闭或密钥不同的混合状态。
4. 协调重启所有 Nacos 节点，确认节点和客户端恢复；`8848`、`9848`、`9849` 只允许可信网络访问。
5. 使用管理员账号登录控制台，立即修改默认 `nacos/nacos` 密码，再把新凭据更新到仓库外 `backend.env` 的 `NACOS_USERNAME`、`NACOS_PASSWORD`。
6. 先加载环境文件，再执行只读鉴权门禁：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\deploy\load-env.ps1 -Path "<安全配置目录>\backend.env"
.\deploy\verify-nacos-auth.ps1
```

门禁会逐个检查 `NACOS_SERVER_ADDR` 中逗号分隔的节点：先匿名读取 `wifi-jwt.yml`，只有 401/403 才继续；随后登录并验证指定 Namespace、Group 和 Data ID 可读，同时要求所有节点返回相同 SHA-256。脚本只输出节点、状态、长度和哈希，不输出配置正文、密码或 access token。任一节点匿名返回 200 时脚本失败，生产 Auth/Gateway 不得启动。

若启用鉴权后需要回滚，必须在维护窗口内对所有节点一致回滚并恢复备份配置。关闭鉴权会重新暴露 Nacos 中全部敏感配置，只能作为限制在可信网络内的短时故障恢复措施。

重建或恢复 standalone 实例时，不能只翻转鉴权开关，必须按以下顺序处理：

1. 停止六个后端服务，执行 `<NACOS_HOME>\bin\shutdown.cmd` 停止 Nacos。
2. 备份 `<NACOS_HOME>\conf\application.properties` 和 `nacos_config` 数据库。
3. 生成仓库外鉴权片段，把其中六项逐项更新到 `application.properties`，不要保留空值。
4. 执行 `<NACOS_HOME>\bin\startup.cmd -m standalone`，确认日志出现 standalone 启动成功且无 auth 初始化异常。
5. 使用 `nacos/nacos` 首次登录，在“权限控制 -> 用户列表”立即修改密码；随后同步更新仓库外 `backend.env` 的 `NACOS_PASSWORD`。
6. 在新 PowerShell 中按上文加载外部文件并执行 `verify-nacos-auth.ps1`。通过后再启动 Auth、Gateway 和其余服务。

验收必须同时满足：默认管理员密码已轮换，匿名读取 `wifi-jwt.yml` 返回 401/403，新凭据认证读取成功且配置哈希符合预期。生产或共享主机还应检查环境文件和 Nacos 配置文件仅对部署账号、SYSTEM 与 Administrators 可读；这项 ACL 检查不要求单人开发机改变仓库权限。

### 3.2 应该在哪里修改

只在 Nacos 修改，不在本地 `application.yml`、`.env` 或系统环境变量中配置。

Nacos 配置必须满足：

```text
Data ID: wifi-jwt.yml
Group: 与 NACOS_GROUP 相同，默认 DEFAULT_GROUP
Namespace: 与 Auth/Gateway 的 NACOS_NAMESPACE 相同
```

内容结构：

```yaml
wifi:
  jwt:
    secret: 至少32字节的高熵随机密钥
    expiration-millis: 86400000
```

仓库模板是 `deploy/nacos/wifi-jwt.example.yml`。模板中的 `CHANGE_ME` 不能直接投入运行。

当前 Nacos 如果仍使用旧结构：

```yaml
jwt:
  secret: 原密钥
```

首次迁移时保留原密钥值不变，只把键名迁移到 `wifi.jwt.secret`，并补齐 `expiration-millis`。这样已签发且未过期的 JWT 仍然有效。

### 3.3 Nacos 控制台操作

1. 确认 `verify-nacos-auth.ps1` 已证明匿名读取被拒绝，再打开 Nacos 控制台，例如 `http://127.0.0.1:8848/nacos`。
2. 进入“配置管理 -> 配置列表”。
3. 选择 Auth 和 Gateway 实际使用的 Namespace。
4. 找到 Data ID `wifi-jwt.yml`，确认 Group 与 `NACOS_GROUP` 一致。
5. 修改并发布。发布前检查 YAML 缩进、密钥长度和过期时间。
6. 不要把真实密钥复制到项目文件中。

JWT 配置设置了 `refresh: false`。发布 Nacos 配置不会立即改变正在运行的服务，必须重启 Auth 和 Gateway。这样可以避免一个实例热更新而另一个实例仍使用旧密钥。

### 3.4 只迁移配置键但不轮换密钥

1. 在 Nacos 把旧 `jwt.secret` 迁移为 `wifi.jwt.secret`，值保持不变。
2. 从仓库外的 `backend.env` 删除 `JWT_SECRET=...`。
3. 清除当前终端遗留变量：

```powershell
Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
```

4. 重启 Auth，再重启 Gateway。
5. 使用原账号重新登录或继续使用未过期 JWT，验证普通 API 和管理员告警连接。

### 3.5 正式轮换 JWT 密钥

HS256 的签发和验证使用同一个密钥。轮换期间 Auth 与 Gateway 不能分别使用新旧值，否则 Auth 新签发的 JWT 会被 Gateway 拒绝。

1. 选择维护窗口，通知现有会话将失效。
2. 安全备份 Nacos 中的旧配置，不把备份写入仓库。
3. 停止 Gateway，阻止维护窗口内继续进入受保护请求。
4. 停止 Auth。
5. 在 Nacos 更新 `wifi.jwt.secret`。
6. 启动 Auth，确认没有 JWT 配置缺失或弱密钥错误。
7. 启动 Gateway。
8. 所有用户重新登录，获取新 JWT。

轮换影响：旧 JWT 全部失效；前端会收到 401 并返回登录页；Portal 和业务 Session 是否仍在线由各自状态机决定，不能把 JWT 轮换当成网络 Session 批量注销。

回滚时必须再次同时停止 Auth/Gateway、恢复旧 Nacos 配置并重启。轮换后新签发的 JWT 在回滚到旧密钥后会失效。

### 3.6 修改过期时间

修改 `wifi.jwt.expiration-millis` 后仍按 Auth/Gateway 协调重启。该值只影响之后新签发 JWT 的 `exp`，不会延长或缩短已经签发的 JWT。

## 4. 各安全措施保护什么

### 4.1 用户 JWT

保护对象：浏览器用户身份、登录有效期、角色声明，以及 Gateway 上的本人资源和管理员权限判断。

配置位置：Nacos `wifi-jwt.yml`。

使用模块：Auth 签发，Gateway 验证。其他业务服务不获取 HS256 密钥。

失配现象：登录接口可能成功返回 Token，但所有受保护 Gateway 请求返回 401；告警 WebSocket 握手返回 401。

### 4.2 `WIFI_GATEWAY_TOKEN`

保护对象：证明请求确实经过受信任 Gateway，防止客户端绕过 Gateway 后伪造 `X-User-Id`、`X-User-Role` 等身份头。

配置位置：仓库外 `backend.env`。

使用模块：Gateway 写入；Auth、User、Device、Monitor、Admin 验证。所有相关服务必须使用同一个值。

修改方式：同时更新所有服务使用的环境文件，协调重启 Gateway 和五个业务服务。不能与 `WIFI_INTERNAL_TOKEN` 相同。

失配现象：下游返回 401 和“服务请求来源认证失败”；WebSocket 日志出现 `GATEWAY_TOKEN_MISMATCH`。

### 4.3 `WIFI_INTERNAL_TOKEN`

保护对象：服务间 Feign 调用和 `/internal/**`，例如用户策略、权益租约、Monitor 评估和位置 Session 上下文。

配置位置：仓库外 `backend.env`。

使用模块：Auth、User、Device、Monitor、Admin 可以把它用于出站内部调用；只有 User、Device、Monitor 接收 `/internal/**` 入站调用。Gateway 不使用它作为用户认证。

修改方式：同时更新所有调用方和接收方，协调重启业务服务。失配窗口内内部调用会失败，因此不能逐台长期混用新旧值。

失配现象：外部 Gateway 接口可能仍可达，但聚合、权益、设备策略或 Monitor 调用返回下游失败；服务日志出现 `INTERNAL_TOKEN_MISMATCH`。

### 4.4 Origin、CORS 与 WebSocket Origin

保护对象：限制哪些浏览器站点可以发起带凭证请求或建立告警 WebSocket，不能替代 JWT。

配置位置：`backend.env` 中的 `WIFI_ALLOWED_ORIGIN`、`WIFI_ALLOWED_ORIGIN_ALT`、`WIFI_ALLOWED_ORIGINS` 和 OAuth Return Origin。

修改方式：填写完整 Origin，即协议、主机和端口，例如 `http://192.168.1.20:5173`。修改后重启 Gateway、Monitor；OAuth Return Origin 变化时同时重启 Auth。

失配现象：HTTP 浏览器请求出现 CORS 错误；WebSocket 返回 403 并记录 `ORIGIN_NOT_ALLOWED`。

### 4.5 Nginx 外部边界

保护对象：TLS、同源 `/api`、WebSocket Upgrade、客户端 IP，以及清除浏览器伪造的服务身份头。

配置位置：前端仓库 `deploy/nginx.conf.example` 对应的服务器 Nginx 配置。

修改方式：配置正式域名和证书，保留对 `X-Gateway-Token`、`X-Internal-Token`、`X-User-*` 的清空规则，保留对 `/api/internal/**` 的拒绝。修改后先执行 `nginx -t`，再 reload。

错误影响：删除头清理会形成身份伪造风险；错误的 `/api` 或 `/ws` 代理会造成服务不可达或 WebSocket 断连。

### 4.6 `WIFI_COMMAND_SECRET_KEY`

保护对象：Device 生成的敏感候选 WiFi 配置命令载荷，使用 32 字节 AES 密钥。

配置位置：Device 的 `backend.env`，值必须是 Base64 编码的 32 字节数据。

修改方式：只有在命令接收端协议也使用同一密钥时才能启用或轮换。轮换时先停止发送敏感命令，更新两端，再恢复命令。普通告警、流量和 Session 查询不依赖该密钥。

失配现象：候选 WiFi 配置命令无法解密；缺失时 Device 会明确禁用该敏感功能，而不是伪装成功。

### 4.7 数据库、Redis、MQTT 与第三方 Provider 凭据

数据库凭据保护业务数据访问；迁移账号与业务账号应分离。修改数据库密码时同时修改数据库用户和各服务环境配置，再重启受影响服务。

Redis 凭据当前影响 Auth 验证码/状态和 Gateway 全局限流。不可用时部分限流会降级，但不能把降级当作正常生产状态。

MQTT 凭据保护 Device 与 Broker 的消息通道。修改时需要同步 Broker、Device 和实际设备端；失配会中断命令与设备事件。

OAuth Client Secret、邮件密码、阿里云号码认证密钥只由 Auth 使用。修改 Provider 控制台配置和 `backend.env` 后重启 Auth。它们不替代用户 JWT。

本地 Demo 支付 Secret 只保护 Demo 回调签名，不代表真实支付已接入，也不能产生真实支付成功状态。

## 5. 修改后的最小验收

JWT 或安全 Token 修改后至少验证：

1. Auth、Gateway 和受影响业务服务均无配置错误并完成 Nacos 注册。
2. 新登录成功，返回 JWT。
3. 使用 JWT 查询一个本人资源成功。
4. 普通用户访问管理员接口返回 403。
5. 伪造 `X-User-Role: 0` 不会覆盖 JWT 中的真实角色。
6. 超管告警 WebSocket 保持绿色，不出现 `JWT_INVALID`、`GATEWAY_TOKEN_MISMATCH` 或持续重连。
7. 直连 Monitor `8384` 的 WebSocket 因缺少 Gateway 凭据而被拒绝。
8. 一个内部聚合接口成功，证明 `WIFI_INTERNAL_TOKEN` 一致。

## 6. 手机显示“服务不可达”的判断

前端只有在 Axios 没收到任何 HTTP 响应时才显示“服务不可达”。这发生在 Gateway/JWT 返回 401 之前，通常不是 JWT 错误。

本地手机验收必须同时满足：

- 前端 `.env` 使用 `WIFI_DEV_HOST=0.0.0.0`。
- `WIFI_DEV_ALLOWED_HOSTS` 包含手机实际访问的主机名或电脑 LAN IP。
- Windows 防火墙允许手机访问 Vite 端口 `5173`。
- 手机与电脑之间存在可达路由；ESP SoftAP 认证前后不能把电脑地址路由丢失。
- Gateway/Monitor Origin 白名单包含手机页面的精确 Origin。

在手机浏览器分别于认证前后访问：

```text
http://电脑LAN地址:5173/api/health/gateway
```

预期获得 Gateway JSON 响应。若页面本身可打开但该地址没有任何 HTTP 响应，应检查 Vite Host、Allowed Hosts、防火墙和 ESP 路由；若收到 401/403/5xx，则再根据响应和服务日志检查认证或下游服务。
