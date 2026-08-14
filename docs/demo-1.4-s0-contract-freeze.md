# Demo 1.4 S0 公共能力与兼容契约冻结

## 范围与基线

本文件是 1.4 批次 A（S0）的只读输入索引，不代表批次 B 或 `P14-*`
已经实现。S0 不修改生产业务代码，只冻结兼容目标、分类、协议样本和文件
所有权。

- 后端基线：`demo/stage-01-foundation`，
  `731cfa11e270338732ef6c914a8356a014c96f66`，审计时工作区和暂存区为空。
- 前端基线：`demo/stage-01-foundation`，
  `cd1cc9e27b7c1a5a164bd3d8dce1bff48481af20`，保留 9 个 tracked dirty；
  `P14-FE` 输入文件本次只读。
- 固件基线：`demo/stage-01-foundation`，
  `07ba4966d68d90f58d9cf1a5340dd30bd7e7d7d7`，保留 10 个 dirty；
  `P14-FW` 必须以所有权 fixture 中的受保护文件 hash 为输入，不得从干净
  HEAD 猜测当前协议实现。
- GitHub 远端实时查询因本会话无法连接 `github.com:443` 未完成；后端本地
  HEAD 与本地远端跟踪引用均为上述 SHA。

冻结资产：

- HTTP envelope 与基础错误键：`http-envelope-v1`
- MQTT 协议样本：`mqtt-protocol-v1`
- AI Provider 固定输出：`ai-moderation-result-v1`
- P14 输入与所有权：`p14-work-packages-v1`

fixture 位于：

```text
wifi-common-api/src/test/resources/contracts/demo-1.4-s0/
```

## 能力分类

### REUSE

- `ApiResponse` 当前 `code/message/data` 三字段结构和 `code=200` 成功语义继续
  作为旧调用方基线。
- `ApiStatusException` 已把 HTTP status、兼容 numeric code 和
  `Retry-After` 秒数留在 Web 框架无关的 common-api。
- 六个 Servlet handler 已覆盖 Bean Validation、格式错误、非法参数和
  `ApiStatusException` 的基础映射；正确的模块本地语义不在 S0 重写。
- 分页查询普遍已有 `current<=0 -> 1`、`size<=0 -> 10`、`size<=100`，
  PageResult 保持 `total/current/size/records`。
- 金额字段已使用 `Cents`，业务时长使用 `Seconds`，流量使用 bytes，
  经纬度使用 decimal 并有范围校验。
- `MqttTopics`、六类生产命令 payload、五类事件 DTO 和固件命令结构可作为
  协议真相输入。
- 固件受保护工作区已有 requestId 终态缓存、`configVersion` 双槽语义和
  KICK 独立 reason 缓冲区；本批只冻结样本，不重写执行逻辑。
- AI 已有 Provider/Policy/PolicyVersion/ReviewTask/ManualReview 持久化骨架，
  内容 hash、provider 元数据、`confidenceBps` 和人工结果载体可复用。
- 前端已按 HTTP/legacy `body.code` 处理错误，并支持 Retry-After、超时、
  离线和安全用户文案。

### GAP

- 生产 `ApiResponse` 尚无可空 `errorKey/requestId`；基础错误键也没有唯一
  注册入口。
- Gateway 尚未创建、校验、透传或回写 `X-Request-Id`，Servlet/Feign/MDC
  也没有完整生命周期。
- handler 的 Retry-After、未知异常安全输出和 null message 行为不完全一致；
  当前部分 500 会直接返回异常 message。
- 通用分页归一化入口尚不存在；若以后开放客户端排序，必须先增加领域白名单。
  现有仅按 `create_time` 排序的查询仍缺稳定次级 ID。
- `Asia/Shanghai` 历史 datetime 解释尚无统一配置/契约测试。
- 结构化脱敏、生产配置 fail-fast、liveness/readiness 和低基数指标尚未形成
  公共入口。
- Java/固件尚无共同版本化 MQTT fixture。固件文档声明 traffic topic，但
  当前固件树没有 traffic event 生产实现；该事实不能在 1.4 伪装为已实现。
- 固件 status 发送 `rssi`，当前 Java `DeviceStatusEvent` 未消费该字段。
- 固件除 `STAGE_WIFI_CONFIG` 和 KICK reason 外仍使用局部字符串扫描读取
  JSON；KICK 当前未校验 payload `deviceCode`。这些兼容缺口只登记给
  `P14-FW`，S0 不修改解析器。
- AI 尚无 Provider interface、Registry、严格 Schema 校验、隐私门禁、有界
  调用或 test-only Adapter。
- 前端尚未优先消费 `errorKey`，也未暴露 `requestId` 给错误展示/诊断。

### MERGE

- 六个 Servlet handler 的公共映射在 S1 合并到唯一 Web 支持入口；服务只保留
  领域异常和安全的本地扩展，不能同时注册冲突 Advice。
- 分散的分页默认值在模块接入时委托统一归一化值对象；已有正确 PageResult 和
  固定排序不批量替换。
- HTTP status、legacy numeric code、errorKey、Retry-After 和 requestId 在
  前端合并成一个标准错误对象；中文 message 不再参与机器分支。
- Java DTO、固件结构和文档协议在 `mqtt-protocol-v1` 合并为一份样本索引，
  但实现真相仍由各仓源码拥有。
- AI 数据库存储的 `confidenceBps` 与 Provider Schema 的 `confidence 0..1`
  在 adapter 边界显式换算，不能混用同一单位。

### LOCAL_EXTENSION

- `admin-service` 保留 Feign 400/404/409/429 的安全映射，并把下游 401/403
  隐藏为 503。
- `auth-service` 保留验证码 Provider 和 Refresh Session 特例；现有
  `RefreshSessionException` 把机器码放在 `data` 的兼容行为必须定向迁移，
  S0 不直接删除。
- Gateway 继续使用独立 WebFlux writer，不进入 Servlet starter。
- 固件继续兼容除 `STAGE_WIFI_CONFIG` 外缺少 requestId 的旧 payload，但后端
  生产者必须始终发送非空 requestId；旧 payload 不具备重复执行保护。
- 前端技术术语替换属于展示层本地扩展，不成为 errorKey 注册表。

## 冻结兼容契约

### HTTP

- 旧响应保持 `code/message/data`，成功 `code=200`。
- 新字段 `errorKey/requestId` 只能作为可空、向后兼容字段增加。
- `errorKey` 使用稳定 `UPPER_SNAKE_CASE`；message 只面向用户。
- Header 固定 `X-Request-Id`；合法值只允许 ASCII 字母、数字、`-`、`_`，
  长度 16 至 64。
- HTTP status 优先，旧的 `body.code` 仅作兼容 fallback。
- 基础状态固定为 400/401/403/404/409/429/502/503/500；429 使用秒单位的
  Retry-After。

完整样本和 15 个基础 errorKey 见 `http-envelope-v1.json`。

### 分页、排序与单位

- `current = max(1, current or 1)`
- `size = min(100, max(1, size or 10))`
- PageResult 固定 `total/current/size/records`。
- 客户端排序只允许领域枚举映射；每个查询保留稳定次级 ID 排序。
- 历史 datetime 按 `Asia/Shanghai` 解释；新公共时间优先带 `+08:00`。
- 金额为整数 cents，业务时长为 seconds，内部/provider 耗时为 millis，
  流量为 bytes，经纬度为 decimal。
- Provider `confidence` 为 `0..1`；数据库 `confidenceBps` 为 `0..10000`。

### MQTT

- Topic、字段、长度、枚举、正反样本版本固定为 `mqtt-protocol-v1`。
- 后端六类生产命令都必须发送非空 requestId，最大 63 个可见字节。
- command-result 终态类型固定为六类生产命令，结果包含
  `deviceCode/requestId/type/success/message`。
- `STAGE_WIFI_CONFIG` 的 configVersion 为 `1..4294967295`；旧版本或重复
  requestId 不得重复执行。
- Topic 中的 deviceCode 是后端可信身份；payload 冲突时拒绝。
- Java 与固件后续各复制本仓 fixture，并以版本和 hash 对照；固件构建不得
  依赖后端路径。

### AI

- Schema 固定为 `ai-moderation-result-v1`，拒绝未知字段。
- decision 只允许 `APPROVE/REJECT/MANUAL`。
- confidence 为 `0..1`，riskLabels 最多 16 项且去重。
- reasonCode 使用稳定机器键；category/priority 仅作为受控可空字段。
- Provider metadata 只允许 providerRequestId、model、latencyMillis。
- timeout、unavailable、oversize、非法 JSON、未知决定或 Schema 错误全部
  fail closed 到 `MANUAL`，不得返回 APPROVE。
- Provider 未证明 no-training/retention 时不得发送真实正文。

## P14 只读输入与文件所有权

机器可检验清单见 `p14-work-packages-v1.json`。共享路径只由串行协调者拥有：

```text
pom.xml
docs/backend-api-index.md
docs/demo-1.4-s0-contract-freeze.md
wifi-common-api/**
wifi-common-mybatis/**
wifi-audit-spring-boot-starter/**
wifi-service-security-spring-boot-starter/**
wifi-web-support-spring-boot-starter/**
database-migration/**
```

模块包固定为：

- `P14-GW` -> 后端 `gateway-service/**`
- `P14-AUTH` -> 后端 `auth-service/**`
- `P14-USER` -> 后端 `user-service/**`
- `P14-TENANT` -> 后端 `tenant-service/**`
- `P14-DEVICE` -> 后端 `device-service/**`
- `P14-MONITOR` -> 后端 `monitor-service/**`
- `P14-BFF` -> 后端 `admin-service/**`
- `P14-AI` -> 后端 `ai-service/**`
- `P14-FE` -> 前端 HTTP/error 入口及其 fixture/test
- `P14-FW` -> 固件协议文档、app_command/app_storage、协议测试和必要 main 接入

同一 repository 内任意两个不同 owner 的路径前缀不得相等或互为前缀。
`StableSupportContractFixtureTest` 对此执行永久断言。固件包在创建 worktree 前
还必须先解决“干净 HEAD 不包含当前受保护 dirty 协议实现”的输入门禁；S0 不
复制、暂存或覆盖这些 dirty 文件。

## S0 停止边界

- S0 不增加 Web starter，不修改根 POM，不实现 errorKey/requestId。
- S0 不修改任何 Service、Controller、Worker、Gateway filter、前端生产文件或
  固件生产文件。
- S0 不运行数据库、服务、全量构建或固件编译。
- 本文件和 fixture 只供后续批次读取；未经 S0 原子提交验收，不启动批次 B 或
  `P14-*`。
