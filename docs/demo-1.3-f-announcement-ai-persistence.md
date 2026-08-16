# Demo 1.3-F 公告与 AI 持久化实施记录

> 2026-08-11 边界修正：本文保留 F 批当时的历史事实。Support/AI 在 1.3 仅为持久化模块；claim、锁行、条件更新和业务 MySQL IT 归 1.5/2.3。

## 范围与边界

- 执行日期：2026-08-11。
- 本批只建立公告、评论、能力限制和双场景 AI 审核的持久化底座，不进入 1.3-G。
- 新模块只包含 POM、Entity、受控 Mapper 和持久化测试；未创建启动类、Controller、Service 编排、Gateway 路由或公共 API。
- `V2_6__announcement_schema.sql` 与 `V2_7__ai_review_schema.sql` 均为未执行候选迁移。
- 未执行 Flyway migrate、repair、clean，未执行 DDL、DML、回填、约束收紧或数据库测试写入。

## 实时数据库事实

- 目标数据库：`localhost:3306/wifi`；MySQL 主机名 `xwh`，版本 `8.4.8`，server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- Flyway 最新记录仍为 installed rank `9`、`V2_4_1`、checksum `848930306`、`success=1`。
- `V2_4_2`、`V2_5`、`V2_5_1`、`V2_6`、`V2_7` 的历史记录均为 0。
- 五张 Support 表和五张 AI 表的实时 `information_schema` 总计数为 0。
- 本批最后一次数据库复核只执行 `SELECT`；MySQL 命令行仅报告密码参数安全提示，没有 SQL 错误。

## Support 持久化

- `t_announcement` 保存 PLATFORM/TENANT 作用域、创建幂等键和 fingerprint、当前/已发布内容版本、发布状态及 version。
- `t_announcement_content_version` 保存不可变标题、摘要、纯文本正文、内容 hash 和审核快照；发布正文不会被新草稿覆盖。
- `t_announcement_comment` 始终保存 tenant，包括平台公告评论，并以 tenant、作者和 clientRequestId 保证创建幂等。
- `t_user_capability_restriction` 独立区分公告发布、公告评论和问题提交；生成 scope key 保证 GLOBAL/TENANT 唯一语义。
- `t_support_content_review_outbox` 只保存场景、业务引用、内容版本/hash、重试游标和 lease，不保存公告或问题原文。
- 公告状态更新均带 scope/tenant、expectedStatus 和 expectedVersion；审核状态接口不能写 `PUBLISHED`，已发布公告必须先撤回才能创建下一内容版本。
- 五个 Support Mapper 均不继承 `BaseMapper`，不暴露任意 CRUD。

## AI 持久化与隐私

- `t_ai_provider` 只保存非秘密 Provider 元数据、配置引用、能力声明和超时，不保存密钥、Token、Cookie 或完整 endpoint 凭据。
- `t_ai_policy` 保存稳定场景身份；`t_ai_policy_version` 保存 instruction/schema 引用及 hash，不保存完整 prompt。
- 生成列 `active_policy_key` 与唯一索引保证一个 policy 最多一个 ACTIVE 版本。
- `t_ai_review_task` 只保存业务引用、content hash、策略/Provider 引用、执行状态、决定和最小结果元数据，不保存原文或完整 Provider 响应。
- `SUCCEEDED` 只允许 `APPROVE/REJECT`；`MANUAL` 必须进入 `MANUAL_REQUIRED`，Provider 异常或低置信度不能形成 APPROVE。
- `t_ai_manual_review` 以唯一 event key 保存幂等人工动作；AI Mapper 不引用 Support 公告表，AI 决定不能直接写公告发布状态。
- 五个 AI Mapper 均不继承 `BaseMapper`。

## 迁移门禁与文件指纹

- `V2_6` 要求五张 Marketplace 前序表全部存在，并只接受目标 Support 表处于 `0/5` 状态。
- `V2_7` 要求五张 Support 前序表全部存在，并只接受目标 AI 表处于 `0/5` 状态。
- `V2_6` 当前 SHA-256：`847B14EC6020A2335BDB9F9A523D154D69F2A8A12F5077B7CCEE92FA3B313BFF`。
- `V2_7` 当前 SHA-256：`F1437491F79F3CC71D7325C3D176E21AE04D6BE2246E967C451DF4CA864D1F98`。
- 以上是未执行候选文件指纹，不冒充 `flyway_schema_history` checksum；用户验收前仍不暂存、不提交。

## 永久测试

- `AnnouncementAiMigrationScriptTest`：5 个测试通过，覆盖批次表边界、scope/tenant CHECK、内容版本唯一、单 ACTIVE 策略、任务幂等和敏感字段禁入。
- `SupportPersistenceContractTest`：4 个测试通过，覆盖受控 Mapper、创建幂等、发布/审核状态、租户归属、乐观锁和 Outbox lease。
- `AiPersistencePrivacyContractTest`：4 个测试通过，覆盖 Entity 隐私边界、不可变策略引用、执行状态/决定分离和人工 event key。
- 定向 Reactor 五个模块全部成功，共 13 个测试通过，失败、错误、跳过均为 0。
- `SupportMapperMySqlIT` 与 `AiMapperMySqlIT` 在同一次 testCompile 中编译成功，但本批未运行。
- 两个 MySQL IT 默认关闭，只接受 `wifi_test_*`、显式写许可和专用测试账号；测试写入位于回滚事务，回滚后再使用只读连接断言零残留。
- 因合法顺序 Schema 尚不存在，本批没有创建或执行 V2.6/V2.7 测试 Schema，不把未运行 IT 记录为通过证据。

定向命令：

```powershell
.\mvnw.cmd -pl database-migration,support-service,ai-service -am `
  '-Dtest=AnnouncementAiMigrationScriptTest,SupportPersistenceContractTest,AiPersistencePrivacyContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 错误门禁与自审

- 首次 Maven 运行命中既有 `ERR-20260807-024`：受限网络无法解析本地缺失的 Spring Boot 父 POM；更新原记录后，以外部网络权限重跑。
- 收紧 AI 成功决定后，迁移静态测试保留旧断言，登记 `ERR-20260811-198`；只同步为更强约束断言后，同一测试集通过。
- 未对同一失败进行无修改重试，没有扩大测试范围，也没有运行 MySQL IT。
- 30 个 F 源码/迁移/测试文件和两个后端证据文件已逐项审计；Entity 数、Mapper 数和 DDL 表数均为 10。
- tracked `git diff --check` 无空白错误；F 未跟踪文件尾随空白扫描和敏感值扫描通过。
- 前端和固件保持只读；既有 P-3B 文件未被修改、重命名、暂存或提交。
- 暂存区为空；未执行 commit 或 push。

## 恢复点

- 1.3-E 已于 2026-08-11 通过用户验收。
- 1.3-F 已完成可执行范围内的实施、离线验证、只读数据库复核和真实 diff 自审，当前等待用户验收。
- 验收前不暂存、不提交、不进入 1.3-G。
