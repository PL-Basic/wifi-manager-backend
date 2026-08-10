# Wifi Manager Demo 测试资产索引

## 文档状态

- 快照日期：2026-08-10。
- 当前范围：Demo 1.2-A 测试资产基线、1.2-B 至 1.2-F 已建立的永久测试入口，以及 1.2-G 三仓最终自审。
- 本文登记现有资产、证明边界、环境依赖、缺口、后续测试归属和永久执行入口，不把低层测试通过写成更高层成功。
- 原始统计口径：后端 `25` 个 tracked Java 测试文件加 `3` 个受保护的 untracked Java 测试文件，共 `28` 个文件、`97` 个 `@Test`；1.2 新增底座测试单独记录，不混入原始资产计数。
- 当前自动证明面包括后端离线测试 Kit、前端 Vitest 与 Playwright 匿名 smoke，以及固件 PlatformIO 测试发现入口。MySQL、真实 HTTP/MQTT 和固件测试仍没有运行通过证据。
- 运行证据：后端定向 Maven Reactor 共 `52` 个测试通过，前端 Vitest 已有 `5` 个文件、`14` 个测试通过，Playwright 匿名 smoke 通过且真实服务项目按门禁跳过；固件 `--list-tests` 已发现 `3` 个 suite，但未编译、上传、打开串口或执行测试。

## 证明层级

- `L0` 静态与构建：只能证明源码、配置或构建入口的基础完整性，不能证明业务流程成功。
- `L1` 单元与组件：在进程内使用 mock、fake 或 Spring Test 请求对象证明局部规则，不访问真实数据库、Redis、网络、MQTT 或串口。
- `L2` 持久化与迁移：需要白名单 MySQL 测试 Schema；1.2-E 已提供默认关闭的连接校验入口，但未连接数据库或执行迁移。
- `L3` 服务契约与运行时集成：需要 Gateway、下游服务、Redis、Nacos、MQTT 等真实依赖；1.2-E 已提供默认关闭的 HTTP/MQTT 夹具，但未连接真实服务或 Broker。
- `L4` 前端单元、组件与浏览器：Vitest 已有 `5` 个文件、`14` 个测试通过；Playwright 匿名 smoke 通过，真实服务项目因缺少显式变量按门禁 skip。
- `L5` MQTT 与真实 ESP32：需要明确测试设备、串口、Broker 和硬件环境；当前已有组件测试发现入口和人工指南，但没有硬件运行通过证据。

任何较低层级的通过都不能替代更高层级证明。`npm run build` 不能证明路由和交互，Mockito 测试不能证明真实数据库或多服务链路，固件源码审计不能证明真实重投、断电和网络控制。

## 后端现有测试

### 共同运行特征

- 全部 `28` 个文件都使用 JUnit 5，均未使用 `@SpringBootTest`。
- 相关模块通过 `spring-boot-starter-test` 提供 JUnit、Mockito 和 Spring Test 工具。
- `19` 个文件使用 Mockito；Mapper、Redis、Feign/WebClient 或其他外部端口均以 mock/fake 形式参与。
- 当前测试属于 `L1`。没有 MockMvc/WebTestClient 统一接口套件、Testcontainers、真实 MySQL 迁移测试或全服务上下文入口。
- 多个测试使用 `ReflectionTestUtils` 注入依赖；这是当前事实，不在 1.2-A 重写。
- `GatewaySecurityRegressionTest` 有一个固定 `Thread.sleep(1200)` 的本地限流过期用例；只有出现真实不稳定证据后才点对点替换。
- 根 Maven 已增加 `backend-core-acceptance` profile，统一区分 unit/architecture/contract 与 integration 测试入口；默认关闭外部写入和 MySQL。
- Windows `mvnw.cmd` 已修复非链接 `.m2` 目录的空 `Target` 判断；后端定向 Reactor 已通过该 wrapper 执行成功。

### Admin，3 个文件、7 个测试

- `admin-service/src/test/java/com/plagod/controller/AdminPlatformTenantControllerTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：普通管理员在调用 Tenant Service 前被平台租户管理接口拒绝。
  - 层级/依赖：L1；Tenant client 为 mock，无外部环境。
- `admin-service/src/test/java/com/plagod/configuration/GlobalExceptionHandlerTest.java`
  - 状态：untracked，属于受保护 P-3B 工作树；`3` 个测试。
  - 证明：下游 404/409 状态保留，内部认证失败和未知下游失败映射为 503 且不泄漏内部认证细节。
  - 层级/依赖：L1；直接调用异常处理器，无外部环境。
- `admin-service/src/test/java/com/plagod/controller/AdminUserControllerAuthorizationTest.java`
  - 状态：untracked，属于受保护 P-3B 工作树；`3` 个测试。
  - 证明：平台超级管理员允许进入全局用户管理，租户管理员和缺失角色被拒绝。
  - 层级/依赖：L1；直接调用 Controller 授权边界，无外部环境。

### Auth，5 个文件、14 个测试

- `auth-service/src/test/java/com/plagod/listener/DefaultTenantMembershipOutboxListenerTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：Outbox 使用专用有界执行器，队列饱和时保留数据库重试语义，不退化为调用线程执行。
  - 层级/依赖：L1；进程内线程池，无数据库或消息系统。
- `auth-service/src/test/java/com/plagod/service/OAuthServiceSessionOrderingTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：Refresh Session 创建失败时不提前完成 OAuth state。
  - 层级/依赖：L1；Provider、state、user 和 session 端口均为 mock。
- `auth-service/src/test/java/com/plagod/service/impl/AuthOperationTokenServiceImplTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：operation token 首次消费绑定 purpose/business key，重复 JTI 被拒绝，旧 security version 不能消费。
  - 层级/依赖：L1；Redis 使用 mock。
- `auth-service/src/test/java/com/plagod/service/impl/AuthSessionServiceImplTest.java`
  - 状态：tracked；`5` 个测试。
  - 证明：只持久化 Refresh Token 哈希；旋转 Token 重放撤销整个 family；风险 step-up、旋转和账号切换 sid/cookie 绑定。
  - 层级/依赖：L1；Mapper、Redis、租户上下文和其他端口均为 mock；部分断言使用进程当前时间。
- `auth-service/src/test/java/com/plagod/service/impl/DefaultTenantMembershipOutboxServiceImplTest.java`
  - 状态：tracked；`2` 个测试。
  - 证明：待处理默认成员 Outbox 让新用户保持受限，成功或已迁移用户进入 ready。
  - 层级/依赖：L1；Mapper/客户端为 mock。

### Device，1 个文件、7 个测试

- `device-service/src/test/java/com/plagod/service/impl/DeviceIdempotencyRegressionTest.java`
  - 状态：tracked 且当前文件本身有受保护未暂存差异；`7` 个测试。
  - 证明：等待替换授权幂等；重复 Traffic event 不重复累计；同 eventId 不同 payload 冲突；跨租户设备和 Portal Session 按不存在隐藏；全局 deviceCode 冲突；KICK 长 UTF-8 原因不再耦合 MAC 缓冲区。
  - 层级/依赖：L1；Mapper、MQTT/业务端口和 ObjectMapper 在进程内使用，未连接真实 Broker、数据库或固件。

### Gateway，4 个文件、18 个测试

- `gateway-service/src/test/java/com/plagod/client/AuthSessionWebClientTest.java`
  - 状态：untracked，属于受保护 P-3B 工作树；`2` 个测试。
  - 证明：幂等验证只对一次传输超时重试，下游 HTTP 失败不重试。
  - 层级/依赖：L1；WebClient 使用自定义 ExchangeFunction，不发真实网络请求。
- `gateway-service/src/test/java/com/plagod/configuration/GatewayProductionConfigurationValidatorTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：生产环境拒绝本地占位 Token 和禁用 Redis 限流，接受满足要求的安全配置。
  - 层级/依赖：L1；纯配置对象，无外部环境。
- `gateway-service/src/test/java/com/plagod/filter/GatewaySecurityRegressionTest.java`
  - 状态：tracked；`10` 个测试。
  - 证明：清除伪造身份头；本人资源使用 JWT claims；WebSocket JWT 在 Gateway 终止；越权拒绝；限流和 `Retry-After`；上下文依赖失败 fail closed；同一 Access JWT 可重复调用普通接口。
  - 层级/依赖：L1；Spring mock exchange、mock 身份服务和内存回退限流；一个用例固定等待 1200 ms，不连接真实 Redis 或 Gateway。
- `gateway-service/src/test/java/com/plagod/service/GatewayIdentityValidationServiceTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：上下文切换后旧 Access JWT 被拒绝，step-up 前 security version 被拒绝，写校验依赖失败保持 503。
  - 层级/依赖：L1；Auth/Tenant WebClient 为 mock。

### Monitor，2 个文件、5 个测试

- `monitor-service/src/test/java/com/plagod/service/impl/ClientLocationRegressionTest.java`
  - 状态：tracked；`2` 个测试。
  - 证明：只有已授权 ACTIVE Session 使用可信会话数据写入位置；缺失或未授权 Session 不写位置。
  - 层级/依赖：L1；Mapper、Device client 和围栏服务为 mock。
- `monitor-service/src/test/java/com/plagod/ws/AlertWebSocketHandshakeInterceptorTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：Gateway 已验证管理员身份可握手，普通用户和缺少 Gateway 凭证的请求被拒绝，后端不接收原始 JWT。
  - 层级/依赖：L1；Spring mock servlet/WebSocket 对象，无真实网络。

### Tenant，2 个文件、12 个测试

- `tenant-service/src/test/java/com/plagod/service/impl/TenantContextServiceImplTest.java`
  - 状态：tracked；`9` 个测试。
  - 证明：role 0 默认 PLATFORM；普通用户进入有效 TENANT；PLATFORM_TENANT 不伪造成员；上下文类型互斥；旧写版本、禁用租户、成员移除和依赖失败的拒绝语义。
  - 层级/依赖：L1；Mapper/User client 为 mock。
- `tenant-service/src/test/java/com/plagod/service/impl/TenantServiceImplTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：普通管理员不能创建租户，default-tenant 不能停用，默认成员事件重复消费幂等。
  - 层级/依赖：L1；Mapper/Outbox 端口为 mock。

### User 与个人权益，6 个文件、8 个测试

- `user-service/src/test/java/com/plagod/service/impl/EntitlementAdjustmentServiceImplTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：非超级管理员不能授予无限权益；grant 幂等且保留时长余额；revoke 恢复保留的时长权益。
  - 层级/依赖：L1；Mapper 为 mock。
- `user-service/src/test/java/com/plagod/service/impl/EntitlementLeaseServiceImplTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：后台续租从已持久化 entitlement 反查 tenant，且该场景不扣减。
  - 层级/依赖：L1；Mapper 为 mock。
- `user-service/src/test/java/com/plagod/service/impl/EntitlementRewardOrderServiceImplTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：奖励订阅从现有到期时间按自然月延长。
  - 层级/依赖：L1；Mapper 为 mock，使用进程内日期时间。
- `user-service/src/test/java/com/plagod/service/impl/PaymentCallbackRegressionTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：成功支付回调重复到达时返回原结果且不重复发放权益。
  - 层级/依赖：L1；全部 Mapper/履约端口为 mock。
- `user-service/src/test/java/com/plagod/service/impl/PaymentServiceImplTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：支付渠道不可用时，已完成的历史支付仍可读取。
  - 层级/依赖：L1；Mapper 为 mock，渠道列表为空。
- `user-service/src/test/java/com/plagod/service/impl/UserManageServiceImplTest.java`
  - 状态：tracked；`1` 个测试。
  - 证明：存在权益历史时，purge 在任何外部副作用前被拒绝。
  - 层级/依赖：L1；JdbcTemplate、Mapper 和 Auth client 为 mock。

### Common API，1 个文件、4 个测试

- `wifi-common-api/src/test/java/com/plagod/utils/JwtUtilsTest.java`
  - 状态：tracked；`4` 个测试。
  - 证明：配置密钥签发/解析、不同密钥拒绝、Access JWT 的 sid/tenant claims、弱密钥和占位配置 fail closed。
  - 层级/依赖：L1；纯进程内加密和 JSON，无外部环境。

### Security Starter，4 个文件、22 个测试

- `wifi-service-security-spring-boot-starter/src/test/java/com/plagod/security/TenantContextWriteValidationFilterTest.java`
  - 状态：tracked；`8` 个测试。
  - 证明：租户写请求二次校验；平台代管上下文；安全内部路径跳过规则；BFF 传播上下文；自调用避免；依赖失败、无效响应和畸形上下文 fail closed。
  - 层级/依赖：L1；Spring mock servlet 和 mock validator。
- `wifi-service-security-spring-boot-starter/src/test/java/com/plagod/security/TrustedFeignRequestInterceptorTest.java`
  - 状态：tracked；`4` 个测试。
  - 证明：内部 Token 长度门禁；固定租户 Header；只传播可信上下文；无可信请求时移除调用方伪造上下文。
  - 层级/依赖：L1；MockHttpServletRequest/Feign template，无网络。
- `wifi-service-security-spring-boot-starter/src/test/java/com/plagod/security/TrustedRequestFilterTest.java`
  - 状态：tracked；`3` 个测试。
  - 证明：Gateway 和内部请求分别建立可信标记，Gateway Token 不能进入内部路径。
  - 层级/依赖：L1；Spring mock servlet，无网络。
- `wifi-service-security-spring-boot-starter/src/test/java/com/plagod/security/TrustedRequestPropertiesTest.java`
  - 状态：tracked；`7` 个测试。
  - 证明：生产环境拒绝占位、缺失、相同 Token 和禁用认证；区分仅出站内部 Token 的服务；接受满足要求的不同 Token。
  - 层级/依赖：L1；纯配置对象，无外部环境。

### 28 文件完整性断言

- 文件分布：Admin `3`、Auth `5`、Device `1`、Gateway `4`、Monitor `2`、Tenant `2`、User `6`、Common API `1`、Security Starter `4`，合计 `28`。
- 测试方法分布：Admin `7`、Auth `14`、Device `7`、Gateway `18`、Monitor `5`、Tenant `12`、User `8`、Common API `4`、Security Starter `22`，合计 `97`。
- tracked/untracked 分布：tracked `25`，untracked `3`，合计 `28`。
- `@SpringBootTest` 分布：`0`。
- 本索引逐文件列出全部 `28` 个文件，没有把 untracked P-3B 测试误登记为 1.2 新增资产。

## 前端测试资产与缺口

### 已有资产

- `package.json` 已增加 `test:unit`、`test:unit:watch` 和 `test:e2e`；Vitest、Vue Test Utils、happy-dom 与 Playwright 均为 dev dependency。
- `vitest.config.js` 只发现 `tests/unit/**/*.test.js`；5 个测试文件、14 个测试覆盖 session、tenant、access、API 错误和 `StateBlock` 代表性边界。
- `playwright.config.js` 使用单 worker、有界超时和本机已有浏览器；匿名 smoke 已通过，真实服务 smoke 仅在显式只读门禁满足时运行，当前按门禁跳过。
- E2E 网络守卫在匿名场景本地响应会话/OAuth 读取，并阻断其他非 GET API；真实服务场景只允许 GET，不默认写业务数据。
- `.env.example` 只登记变量名和非秘密默认值；`coverage`、`playwright-report` 与 `test-results` 已忽略，失败截图和 trace 不进入源码。

### 自动测试缺口

- 当前 14 个 Vitest 测试是代表性底座，不覆盖全部页面、路由、确认框、旧响应隔离、WebSocket、地图和业务工作流。
- 真实服务 Playwright 项目尚无运行通过证据，也没有登录账号、跨服务成功路径、视觉回归或可访问性证据。
- 前端没有业务写测试入口；后续若增加写路径，必须复用后端 Run ID、专用账号、精确清理和零残留规则。
- 本地 `.env` 被 Git 忽略且含已配置环境秘密；测试只能读取变量，禁止复制值到源码、fixture、日志或本文。

### 后续归属

- 1.2-C 已建立 Vitest 和代表性 session/tenant/access/API/组件测试。
- 1.2-D 已建立 Playwright、匿名 smoke、真实服务门禁和失败证据目录。
- 4.x 按真实页面与 API 增量测试；6.x 执行视觉、可访问性和完整浏览器验收。

## 固件测试资产与缺口

### 已有资产

- `platformio.ini` 保留生产 `esp32dev` 和 `esp32-s3-devkitc-1`，并新增独立 `esp32dev-test` 环境；两个生产环境均以 `test_ignore = *` 拒绝误跑测试。
- `README.md` 有设备状态、Portal、ALLOW/TTL、DISCONNECT_MAC、KICK 和上游 WiFi 不可达恢复的人工指南。
- `docs/firmware-contract-audit.md` 记录六类生产命令、Topic/payload/result、NVS 最近 16 条终态和 Portal 配置边界。
- `app_command` 与 `tls_sni_parser` 包含可做确定性组件测试的解析逻辑；`app_storage` 依赖 NVS；Portal、WiFi、MQTT 和 lwIP 控制需要设备级证明。
- `test_app_command`、`test_tls_sni_parser` 和 `test_nvs_boundary` 共登记 `9` 个 Unity 测试方法；`partitions_test.csv` 提供独立 `nvs_test` 分区。

### 自动测试缺口

- `platformio test --list-tests -e esp32dev-test` 已发现 `3` 个 suite；当前只有配置解析、源码审计和测试发现证据，没有固件编译或执行通过证据。
- 已覆盖代表性命令字段、TTL 拒绝、未知 Topic、TLS SNI 分片/归一化/拒绝和测试 NVS 读写边界；六类命令完整字段矩阵、重复 requestId、NVS 恢复、冲突 payload和终态首次结果保持仍未覆盖。
- `nvs_test` 分区与 `wifi_test` namespace 已隔离默认 `nvs`；本阶段不调用硬编码默认分区的 `app_storage` 写 API。
- 已登记单 suite、显式端口和 JUnit 输出命令，但尚无 JUnit XML、串口确认或专用测试设备运行证据。
- README 人工步骤没有自动证明 QoS 1 重投、MQTT 重连、断电恢复、命令副作用恰好一次或真实后端关联。

### 后续归属

- 1.2-F 已建立独立测试环境、组件测试入口和安全 NVS 边界。
- 3.1-3.5 按锁定协议增量加入命令、事件、恢复和数据面用例。
- 3.6 使用专用设备做真实 MQTT、断电、Portal、WiFi 和网络控制验收。
- 任意固件测试失败都保留 `.pio`、build、日志和 JUnit 结果；禁止 clean、删除、覆盖或更换构建目录规避失败。

## 数据库与运行时缺口

- 当前没有 Testcontainers、Flyway 新库/升级测试或 Mapper/XML 集成测试；已有 MySQL 测试 Schema 门禁，但没有真实连接或迁移通过证据。
- 当前已有默认禁用的 HTTP/MQTT 客户端夹具，但没有 Gateway 到下游服务、Redis、MQTT、WebSocket 和多服务状态链的实际 L3 套件。
- 当前已有统一 Run ID、专用账号门禁、资源登记、精确清理栈和零残留断言模板，但没有对真实外部资源执行通过的证据。
- 1.2-E 只建立默认禁用外部写入的 L2/L3 入口；1.2 不执行 `V2_3_2` 或任何新业务迁移。
- 真实迁移和 Mapper 用例归 1.3；业务服务契约、跨服务状态和最终 E2E 在对应 2.x 至 6.x 阶段增量。

## 1.1 不变量覆盖映射

### 身份、所有权与租户作用域

- 全局 User/GlobalRole 与 TenantMember/租户角色分离：
  - 当前证明：`AdminUserControllerAuthorizationTest`、`AdminPlatformTenantControllerTest`、`TenantContextServiceImplTest`。
  - 边界：只证明局部角色和上下文拒绝；没有真实 Gateway 到 Tenant/User 服务链。
  - 后续：1.2-B 归入稳定分类，1.5/2.x 增量，6.2 做跨租户总验收。
- PLATFORM、PLATFORM_TENANT、TENANT 互斥且由 Auth/Tenant 权威解析：
  - 当前证明：`TenantContextServiceImplTest`、`GatewayIdentityValidationServiceTest`、`TenantContextWriteValidationFilterTest`。
  - 缺口：没有真实 Session、Gateway、Tenant 服务和数据库联合证明。
  - 后续：1.5 增量 L1/L3，6.2 总验收。
- Gateway 只认证/传递可信上下文，Admin BFF 不拥有业务状态：
  - 当前证明：Gateway 三组安全测试、Security Starter 四组测试和 Admin 授权测试。
  - 缺口：没有架构规则阻止 Controller 直接依赖 Mapper、Admin 引入 persistence 或跨服务 implementation。
  - 后续：1.2-B 建立架构规则；后续模块持续增量。
- 个人联网权益属于 `tenant + user`，Tenant SaaS 属于 `tenant`，二者不能互相替代：
  - 当前证明：权益调整、续租、奖励、支付回调和 Tenant Service 局部测试。
  - 缺口：SaaS 版本、订阅和配额写闭环尚不存在，也没有跨域架构/集成证明。
  - 后续：1.3、2.1-2.5 增量，6.2/6.4 总验收。
- Marketplace 只拥有目录、下单、历史快照和履约引用：
  - 当前证明：无 Marketplace 自动测试；个人权益测试不能冒充 Marketplace。
  - 后续：1.3 建表后从 L2 开始，2.2-2.5 增量契约/数据访问/Service/API 测试。
- Support 拥有公告、评论、问题和工单；AI 只拥有审核任务和决定：
  - 当前证明：无。
  - 后续：1.3、1.4、1.5 和 2.x 按层增加，禁止 AI 测试直接推进 Support 发布/工单终态。
- Device 拥有节点、Portal Session、命令、WiFi 配置和原始遥测；Monitor 拥有规则、告警、审计、位置和围栏：
  - 当前证明：`DeviceIdempotencyRegressionTest`、`ClientLocationRegressionTest`、`AlertWebSocketHandshakeInterceptorTest`。
  - 缺口：无真实 MySQL tenant 条件、MQTT 入站和跨服务联合证明。
  - 后续：1.3、2.x、3.x 和 6.2/6.4。

### 标识与跨域引用

- 全部 HTTP/JSON Long ID 输出十进制字符串，前端只按不透明字符串处理：
  - 当前证明：无递归 JSON 契约测试，属于 E-01。
  - 后续：1.2-B 建立可复用 JSON 契约断言和失败清单；2.2 点对点修正；1.2-C/4.x 验证前端不做数值转换。
- Auth `sid` 与 Long 型 Portal Session ID 永久分离：
  - 当前证明：Auth Session/JWT/Gateway 测试覆盖 sid；Device 测试覆盖 Portal Session 局部归属。
  - 缺口：没有跨服务契约断言证明字段名、JSON 类型和查询入口不混用。
  - 后续：1.2-B 契约分类，2.2/2.5 和 4.x 增量。
- `marketplaceOrderNo` 与 `entitlementOrderNo` 分离：
  - 当前证明：只有个人权益订单/支付局部测试，Marketplace 不存在。
  - 后续：Marketplace 落地时从 DTO、唯一键、Service 幂等和 API 契约四层证明。
- Device command requestId、支付/退款 requestId、support clientRequestId 和 eventId 使用各自作用域：
  - 当前证明：Device event 冲突、支付回调幂等和 operation token JTI 的局部测试。
  - 缺口：没有跨模块命名/唯一索引架构断言，Support/Marketplace 尚不存在，固件没有自动测试。
  - 后续：1.2-B/F 建底座；1.3、2.x、3.x 在所属域增量。
- 跨域只保存稳定 ID、业务代码、不可变版本和历史快照，不复制当前余额/配额/在线状态/原文：
  - 当前证明：OAuth Session 排序、权益续租反查、支付回调和位置可信 Session 的局部行为。
  - 缺口：没有通用架构规则或新业务持久化测试。
  - 后续：1.2-B 架构入口；1.3/2.x 由各域持久化和 Service 测试负责。

### 五条高风险生命周期

- Auth Refresh Session：
  - 当前证明：哈希持久化、旋转重放、family 撤销、step-up、sid/cookie 绑定、旧 security/context version 拒绝。
  - 缺口：迁移 `EXPIRED` 与当前 `REVOKED + reason` 不一致未关闭；无真实 Redis/数据库并发和 Gateway 链。
  - 后续：1.5 主修正并回归，L2/L3 在 1.2-E 入口上增量。
- Marketplace 订单与目标领域履约：
  - 当前证明：无 Marketplace；个人支付回调幂等只能作为目标域参考。
  - 后续：1.3 持久化，2.1-2.5 分层测试，5.2 补对账/补偿，6.4 做完整履约。
- 公告、AI 审核与人工复核：
  - 当前证明：无。
  - 后续：1.3 持久化、1.4 Provider Schema/fail-closed、1.5 Outbox/幂等，2.x 状态机与权限。
- 用户问题提交、审核与基础工单：
  - 当前证明：无。
  - 后续：1.3 唯一键/额度/状态持久化，1.4/1.5 审核与投递机制，2.x 转工单和参与者权限。
- 设备命令发布、执行、结果与超时：
  - 当前证明：后端局部幂等、event 冲突、跨租户隐藏和 KICK payload；固件只有源码与人工协议指南。
  - 缺口：没有真实 Outbox/MQTT/固件链、严格 Topic、六类 requestId、NVS 断电恢复、迟到结果和副作用恰好一次自动证明，属于 E-02 及 3.x 协议缺口。
  - 后续：1.2-F 建组件入口；3.1-3.5 增量；3.6 真实硬件验收。

### 通用失败与并发规则

- 相同幂等键返回首次保存结果，不同业务快照冲突：
  - 当前证明：默认成员事件、权益调整、支付回调、Traffic event 和等待替换授权局部覆盖。
  - 缺口：Marketplace/Support/AI、真实并发和数据库唯一约束未覆盖。
- 终态不被迟到或冲突结果改写，超时不解释为成功：
  - 当前证明：Gateway WebClient 传输超时重试边界；设备中心终态和新业务状态机没有完整永久测试。
  - 后续：所属 2.x/3.x Service 与 L2/L3 测试负责。
- 跨租户对象先按可信 `tenantId + businessId` 查询并按不存在隐藏：
  - 当前证明：Device 跨租户设备/Portal Session 与 Tenant 上下文局部覆盖。
  - 缺口：Monitor、权益、Marketplace、Support、AI 和真实数据库查询未全覆盖。
- 依赖不可用时 fail closed：
  - 当前证明：Gateway、Tenant、Security Starter、JWT 配置、Admin 下游异常和支付渠道局部覆盖。
  - 缺口：真实 Nacos/Redis/MQTT/Provider/多服务不可用链未覆盖。
- 内容结果必须匹配对象 ID、tenantId、contentVersion 和 hash，旧结果进入 STALE：
  - 当前证明：无 Support/AI 测试。
  - 后续：1.3 唯一键/版本，1.4 Schema，1.5 Outbox，2.4 状态应用测试。

### E-01 至 E-24 测试归属

- E-01：1.2-B 的递归 JSON Long ID 契约断言；2.2 只修具体失败契约。
- E-02：1.2-F 的固件组件/协议入口；3.1-3.5 增量，3.6 硬件验收。
- E-03：1.3 的 Flyway、零违规门禁和设备 contract L2 测试。
- E-04：1.3 的 SaaS 版本/订阅/配额持久化测试，2.x 分层业务测试。
- E-05：1.3 的 Monitor tenant 字段、Mapper 和 contract L2 测试，6.2 跨租户验收。
- E-06：1.3 的 Marketplace/Support/AI Schema、唯一键和状态持久化测试。
- E-07：1.4 的 Provider 固定 Schema、脱敏、超时和人工降级单元/契约测试。
- E-08：1.5 的 Auth Refresh Session 终态一致性与回归测试。
- E-09：1.5 的前端 context epoch/旧请求隔离单元测试，4.x 浏览器增量。
- E-10：1.5 的幂等、Outbox、租约、重试和失败恢复代表性测试，5.2 对账补偿。
- E-11：2.1 用例/状态模型的静态一致性和状态转换用例。
- E-12：2.2 DTO/VO、Long ID、幂等键、版本和错误契约测试。
- E-13：2.3 tenant-scoped 查询、锁行和条件更新的 L2 测试。
- E-14：2.4 权威 Service 原子状态转换和跨域命令测试。
- E-15：2.5 Controller/内部接口权限、分页、幂等、状态码和 API 索引测试。
- E-16：3.1 协议样本、Topic、eventId 和能力矩阵静态/组件契约测试。
- E-17：3.3 精确 Topic、严格 cJSON、deviceCode 和六类 requestId 组件测试。
- E-18：3.3 NVS 失败顺序和重复执行窗口组件/硬件测试，3.6 断电重投验收。
- E-19：3.5 稳定 eventId、有界队列、重试和后端幂等消费测试。
- E-20：4.1 角色/工作区/页面/API 矩阵静态一致性测试。
- E-21：4.2 路由、导航、工作区上下文和越权重定向单元/浏览器测试。
- E-22：4.3 API wrapper、加载/空/错误/分页和取消状态单元测试。
- E-23：4.5 查询、详情和可视化页面组件/浏览器测试。
- E-24：4.6 写操作、expectedVersion、幂等、反馈和权限浏览器测试。

## 环境与数据门禁

- L1 默认离线，不读取开发环境凭据，不隐式连接 localhost。
- L2 必须显式提供白名单测试 JDBC 配置；禁止 `wifi`、空 Schema、系统 Schema 和归属不明数据库。
- L3/L4 真实写测试必须同时具备专用账号、显式写许可、唯一 Run ID、精确资源登记和 finally 清理。
- Run ID 采用 `wm-<stage>-<case>-<time6>-<rand6>`，总长不超过 48；清理依赖登记的主键/业务键，禁止按前缀宽删除。
- L5 必须先只读列出设备并由用户确认串口和专用测试设备；一次只运行一条精确 build/upload/test。
- 任何真实环境变量值、Token、Cookie、密码、地图凭据、内部服务凭据和 WiFi 密码都不得写入测试源码、fixture、文档、截图或日志；测试假值必须明确标识且不能用于真实环境。

### 1.2-E 永久集成入口

- 唯一定向命令：`.\mvnw.cmd -pl wifi-test-kit,wifi-common-api,wifi-service-security-spring-boot-starter -am test`。2026-08-10 该命令一次运行成功，Reactor 四模块全部 `SUCCESS`，共 `52` 个测试通过，其中 `wifi-test-kit` 为 `25` 个，失败、错误、跳过均为 `0`。
- `MySqlTestDatabase.open(...)` 只有在 `WIFI_TEST_MYSQL_ENABLED=true` 且 JDBC URL、用户名、密码齐全时才允许连接；URL 必须指向 `wifi_test_*` Schema，连接后再次核对 MySQL 产品名与 catalog，不执行 Flyway 或任何 SQL。
- `HttpTestClient.create(...)` 只有在 `WIFI_TEST_HTTP_ENABLED=true` 且提供 `WIFI_TEST_HTTP_BASE_URL` 时可创建；禁用自动重试和重定向，单次超时最多 30 秒，响应最多 1 MiB，READ_ONLY 模式只允许 GET/HEAD/OPTIONS。
- `MqttTestClient.create(...)` 只有在 `WIFI_TEST_MQTT_ENABLED=true` 且提供 `WIFI_TEST_MQTT_BROKER_URI` 时可创建；使用 clean session、禁用自动重连，READ_ONLY 模式禁止 publish。
- 任意 MySQL、HTTP 或 MQTT WRITE 还必须同时满足 `WIFI_TEST_ALLOW_WRITES=true` 与 `WIFI_TEST_ACCOUNT_MARKER=dedicated-test-account`。
- 外部写测试统一用 `ExternalWriteTestTemplate.start(...)` 创建唯一 Run ID，并在 `try-with-resources` 中逐资源登记主键/业务键、逆序精确清理和零残留探针；禁止前缀删除、全表删除或归属不明资源清理。
- 1.2-E 的自测只验证缺环境 fail closed、危险 Schema/URI 被拒绝、只读客户端拒绝写入和清理模板语义；不读取本机凭据，不访问 localhost，不连接 MySQL、HTTP 服务或 MQTT Broker。

### 1.2-G 最终入口摘要

- 后端：`.\mvnw.cmd -pl wifi-test-kit,wifi-common-api,wifi-service-security-spring-boot-starter -am test`；已通过 `52` 个测试，其中 Kit `25` 个，失败、错误、跳过均为 `0`。
- 前端单元：`npm.cmd run test:unit`；已通过 `5` 个文件、`14` 个测试。
- 前端浏览器：`npm.cmd run test:e2e`；匿名 smoke `1 passed`，真实服务 smoke `1 skipped`，缺少显式只读环境时不连接真实服务。
- 前端构建：`npm.cmd run build`；已成功转换 `2515` 个模块，仅有既有 chunk 和插件耗时警告。
- 固件发现：`& 'C:\Users\45333\.platformio\penv\Scripts\platformio.exe' test --list-tests -e esp32dev-test`；已发现 `3` 个 suite、共 `9` 个 Unity 测试方法。
- 固件运行仍要求用户先确认专用测试设备和串口，再执行一个带 `-f <suite>`、显式 upload/test port 和 JUnit 输出路径的命令；当前没有编译、上传、串口或硬件运行通过证据。
- 1.2-G 只复核既有通过证据与静态边界，没有重跑构建或外部连接；验收后已按仓库形成原子本地提交，未 push。

## 受保护工作树

以下路径在 1.2-A 开始前已经 dirty，均不属于本批次。不得移动、格式化、重写、暂存或通过切换分支消除。

### 前端

```text
src/components/app/AccountMenu.vue
src/components/app/AppHeader.vue
src/components/network/DeviceCommandPanel.vue
src/components/network/SessionDetailDrawer.vue
src/composables/useActionDialog.js
src/config/navigation.js
src/layouts/AppShell.vue
src/router/index.js
src/views/ProfileView.vue
```

### 后端

```text
admin-service/src/main/java/com/plagod/configuration/GlobalExceptionHandler.java
admin-service/src/main/java/com/plagod/controller/AdminUserController.java
auth-service/src/main/java/com/plagod/controller/TenantContextController.java
auth-service/src/main/java/com/plagod/service/impl/AuthSessionServiceImpl.java
device-service/src/main/java/com/plagod/client/MonitorServiceClient.java
device-service/src/main/java/com/plagod/client/UserEntitlementClient.java
device-service/src/main/java/com/plagod/controller/ClientSignalController.java
device-service/src/main/java/com/plagod/controller/DeviceCommandController.java
device-service/src/main/java/com/plagod/controller/DeviceController.java
device-service/src/main/java/com/plagod/controller/InternalAdminClientSignalController.java
device-service/src/main/java/com/plagod/controller/InternalAdminSessionController.java
device-service/src/main/java/com/plagod/controller/InternalAdminTrafficController.java
device-service/src/main/java/com/plagod/controller/InternalDeviceAnalyticsController.java
device-service/src/main/java/com/plagod/controller/InternalLocationSessionController.java
device-service/src/main/java/com/plagod/controller/MacBlacklistController.java
device-service/src/main/java/com/plagod/controller/SessionController.java
device-service/src/main/java/com/plagod/controller/TrafficController.java
device-service/src/main/java/com/plagod/dto/TrafficAnalyticsQueryCriteria.java
device-service/src/main/java/com/plagod/mapper/ClientAccessGuardMapper.java
device-service/src/main/java/com/plagod/mapper/ClientSignalMapper.java
device-service/src/main/java/com/plagod/mapper/DeviceCommandRecordMapper.java
device-service/src/main/java/com/plagod/mapper/DeviceWifiConfigRecordMapper.java
device-service/src/main/java/com/plagod/mapper/Esp32NodeMapper.java
device-service/src/main/java/com/plagod/mapper/SessionRecordMapper.java
device-service/src/main/java/com/plagod/mapper/SessionUserGuardMapper.java
device-service/src/main/java/com/plagod/mapper/TrafficLogMapper.java
device-service/src/main/java/com/plagod/mapper/xml/ClientSignalMapper.xml
device-service/src/main/java/com/plagod/mapper/xml/Esp32NodeMapper.xml
device-service/src/main/java/com/plagod/mapper/xml/TrafficLogMapper.xml
device-service/src/main/java/com/plagod/service/ClientSignalQueryService.java
device-service/src/main/java/com/plagod/service/DeviceCommandQueryService.java
device-service/src/main/java/com/plagod/service/DeviceCommandService.java
device-service/src/main/java/com/plagod/service/DeviceSignalAnalyticsQueryService.java
device-service/src/main/java/com/plagod/service/DeviceTrafficAnalyticsQueryService.java
device-service/src/main/java/com/plagod/service/DeviceWifiConfigQueryService.java
device-service/src/main/java/com/plagod/service/DeviceWifiConfigService.java
device-service/src/main/java/com/plagod/service/MacBlacklistService.java
device-service/src/main/java/com/plagod/service/ManagedDeviceCommandService.java
device-service/src/main/java/com/plagod/service/ManualDeviceControlService.java
device-service/src/main/java/com/plagod/service/PortalSessionService.java
device-service/src/main/java/com/plagod/service/PortalSessionStatusQueryService.java
device-service/src/main/java/com/plagod/service/RuleActionExecutor.java
device-service/src/main/java/com/plagod/service/SessionQueryService.java
device-service/src/main/java/com/plagod/service/SessionRevokeService.java
device-service/src/main/java/com/plagod/service/TrafficQueryService.java
device-service/src/main/java/com/plagod/service/TrafficRuleEvaluator.java
device-service/src/main/java/com/plagod/service/impl/ClientDisconnectEventServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/ClientSignalEventServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/ClientSignalQueryServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/CommandResultEventServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceCommandDispatchServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceCommandOutboxServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceCommandQueryServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceCommandServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceEventServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceWifiConfigLifecycleServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceWifiConfigQueryServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/DeviceWifiConfigServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/MacBlacklistServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/ManagedDeviceCommandServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/ManualDeviceControlServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/PortalSessionServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/PortalSessionStatusQueryServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/SessionCommandLifecycleServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/SessionLeaseServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/SessionQueryServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/SessionRevokeServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/TrafficEventServiceImpl.java
device-service/src/main/java/com/plagod/service/impl/TrafficQueryServiceImpl.java
device-service/src/test/java/com/plagod/service/impl/DeviceIdempotencyRegressionTest.java
docs/backend-api-index.md
gateway-service/src/main/java/com/plagod/client/AuthSessionWebClient.java
gateway-service/src/main/java/com/plagod/client/TenantContextWebClient.java
gateway-service/src/main/java/com/plagod/configuration/InternalWebClientConfiguration.java
gateway-service/src/main/resources/application.yml
wifi-common-mybatis/src/main/java/com/plagod/entity/device/ClientSignalRecord.java
wifi-common-mybatis/src/main/java/com/plagod/entity/device/DeviceCommandRecord.java
wifi-common-mybatis/src/main/java/com/plagod/entity/device/DeviceWifiConfigRecord.java
wifi-common-mybatis/src/main/java/com/plagod/entity/device/Esp32Node.java
wifi-common-mybatis/src/main/java/com/plagod/entity/device/MacBlacklist.java
wifi-common-mybatis/src/main/java/com/plagod/entity/device/SessionRecord.java
wifi-common-mybatis/src/main/java/com/plagod/entity/device/TrafficLog.java
admin-service/src/test/java/com/plagod/configuration/GlobalExceptionHandlerTest.java
admin-service/src/test/java/com/plagod/controller/AdminUserControllerAuthorizationTest.java
database-migration/src/main/resources/db/migration/V2_3_1__tenant_device_expand.sql
database-migration/src/main/resources/db/migration/V2_3_2__tenant_device_contract.sql
device-service/src/main/java/com/plagod/utils/TenantScopeUtils.java
gateway-service/src/test/java/com/plagod/client/AuthSessionWebClientTest.java
```

最后六项在 1.2-A 开始前为 untracked；前三个测试仍按现有资产登记，但所有权属于 P-3B，不属于 1.2。

### 固件

```text
README.md
components/app_command/CMakeLists.txt
components/app_command/app_command.c
components/app_command/include/app_command.h
components/app_storage/CMakeLists.txt
components/app_storage/app_storage.c
components/app_storage/include/app_storage.h
docs/firmware-contract-audit.md
src/CMakeLists.txt
src/main.c
```

其中 `components/app_command/CMakeLists.txt` 和 `src/CMakeLists.txt` 当前只有状态脏、无内容 diff，仍必须保护。`.pio/`、`build/`、`release/` 以及所有成功、失败或中断构建结果永久保护。

## 当前结论

- 28 个后端测试文件和 97 个测试方法已完整分类，无遗漏。
- 后端现有证明集中在认证、可信上下文、Gateway 安全、权益幂等、设备局部幂等、位置和 WebSocket 握手，均属于离线 L1。
- E-01 已由 1.2-B 的递归 Long ID 契约断言提供永久入口；E-02 已有固件组件测试发现入口，完整协议与硬件证明仍归 3.x。
- 前端已有 Vitest 与 Playwright 永久入口；固件已有独立 PlatformIO/Unity 环境、3 个 suite 和安全测试 NVS 分区。
- 1.1 所有权、标识、五条生命周期和通用失败规则均已指定现有证据或唯一后续测试归属，没有通过修改业务语义掩盖缺口。
- 1.2-G 及 1.2 整体已于 2026-08-10 通过用户验收并完成三仓本地提交闭合；后端以包含本文的当前 1.2 提交为准，后续会话直接从 1.3-A 开始。
