# Demo 1.3-G 问题提交与基础工单持久化实施记录

> 2026-08-11 命名与边界修正：本文保留 G 批当时的历史事实。文中的 `V2_4_2` 是原候选名，现为 `V2_10`；guard 锁行、状态条件更新和工单事务 Mapper 归 2.3/2.4。

## 范围与数据库事实

- 执行日期：2026-08-11；只实现问题提交、额度 guard 与基础工单持久化，不进入 1.3-H。
- 真实目标仍为 `xwh:3306/wifi`、MySQL `8.4.8`、server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- Flyway 最新仍为 rank `9` / `V2_4_1` / checksum `848930306` / `success=1`。
- `V2_4_2` 至 `V2_8` 均无历史记录；十张 F 表和六张 G 表实时计数均为 0。
- 未执行 `V2_8`、Flyway migrate/repair/clean、DDL、DML、回填或数据库测试写入。

## Schema 与持久化边界

- 未执行的 `V2_8__support_ticket_schema.sql` 要求 F 的十张 Support/AI 表全部存在，并只接受 G 表处于 `0/6` 状态。
- 新增 `t_support_user_guard` 与 `t_support_daily_guard`，分别承载 pending/unresolved 计数和按 Asia/Shanghai 日期派生的每日额度占用。
- 新增 `t_support_submission`，保存 tenant/user 创建幂等键、fingerprint、暂存原文、内容版本/hash、审核引用和结果。
- F 已有 `t_support_content_review_outbox` 被双场景复用；G 未创建第二张审核 Outbox。
- AI/策略拒绝、垃圾拒绝和成功转工单不返还每日额度。只有 AI 任务形成前的系统放弃可进入 `SYSTEM_ABANDONED`，并以唯一补偿 event key 标记一次返还。
- 新增 ticket、message、transition 三表；sourceSubmissionId 唯一保证一个提交一张工单，生成 sender key 保证消息幂等，transition CHECK 只允许冻结生命周期。
- ticket 只能从同租户 `ACCEPTED` submission 创建；首条消息直接复制该 submission 的原文；转换后 submission 原文清除，历史消息不再受其变化影响。
- 关闭工单不能追加消息；状态推进使用 tenant、expectedStatus 和 expectedVersion，OPEN 不能直接 RESOLVED/CLOSED。
- 未增加开发问题库、附件、通知中心、客服组织、跨层升级或公共 API。
- `V2_8` 当前 SHA-256：`2C0BAD9A0D73B815E624999277B55346048D377B6170C330644E99CD4C1C634B`；这是未执行候选文件指纹，不冒充 Flyway checksum。

## Entity、Mapper 与测试

- 新增 6 个私有 Entity 和 6 个受控 Mapper；均不继承 `BaseMapper`。
- guard 预留、释放和补偿均带 tenant、非负条件与 expectedVersion。
- submission 审核结果同时比较 tenant、ID、contentVersion、contentHash、expectedStatus 和 expectedVersion。
- ticket、首消息和 transition 的 Mapper 均验证同租户来源及合法当前状态，可由第二阶段在一个本地事务内编排。
- `SupportTicketMigrationScriptTest` 5 项、`SupportTicketPersistenceContractTest` 4 项和 F 的 `SupportPersistenceContractTest` 4 项全部通过，共 13 项，失败、错误、跳过均为 0。
- `SupportTicketMapperMySqlIT` 在同次 testCompile 中编译成功但未运行；它默认关闭，只接受 `wifi_test_*`、显式写许可和专用测试账号，写事务回滚后使用只读连接断言零残留。

```powershell
.\mvnw.cmd -pl database-migration,support-service -am `
  '-Dtest=SupportTicketMigrationScriptTest,SupportTicketPersistenceContractTest,SupportPersistenceContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 恢复点

- 1.3-F 已于 2026-08-11 通过用户验收。
- 1.3-G 已完成实现、定向离线验证、只读数据库复核和真实 diff 自审，等待用户验收。
- 前端、固件和既有 P-3B 文件保持受保护；暂存区为空，未 commit、未 push、未进入 1.3-H。
