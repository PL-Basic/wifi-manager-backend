# Demo 1.3-C P-3C Monitor Expand 实施记录

> 2026-08-11 命名与边界修正：本文保留 C 批执行 V2.4.1 时的历史事实。文中的 `V2_4_2` 是从未执行的原候选名，现为 `V2_10`；具名 tenant 查询和条件更新归 2.3。

## 范围与边界

- 执行日期：2026-08-11。
- 目标：只完成 P-3C monitor expand、历史数据回填和持久化映射，不进入 1.3-D。
- 目标数据库：`localhost:3306/wifi`；MySQL 主机名 `xwh`；MySQL `8.4.8`；server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- 本批只执行 `V2_4_1__tenant_monitor_expand.sql`；`V2_4_2__tenant_monitor_contract.sql` 保持未执行。
- 未修改 P-3B 业务代码或迁移；未执行 Flyway repair、clean、baseline、手工 DDL 或手工数据回填。

## 恢复点

- 文件：`F:\MyProject\summary\backups\wifi-demo-1.3c-pre-v2_4_1-20260811-123417.sql`。
- 大小：`641241` 字节。
- SHA-256：`288cedf9a455cf0c1d6b570e03ab674b0d07409b7a812d2fb1279123536337d1`。
- 文件级检查：44 个 `CREATE TABLE`、28 个数据 `INSERT`、1 个 dump 完成标记。
- 用户已明确接受该文件作为 pre-`V2_4_1` 恢复点，并授权真实 `wifi` 仅执行 `V2_4_1`。

## 迁移与映射

- `V2_4_1` 为九张 Monitor 表增加可空 `tenant_id`，为 `t_audit_log` 增加 `scope_type`。
- `t_access_rule`、`t_alert_event`、`t_location_authorization`、`t_geofence` 增加 `version`。
- 历史数据按 Session、Node、Rule、Geofence 关系和唯一 `default-tenant` 回填。
- 47 类当前源码 audit action 已冻结：11 类 PLATFORM，36 类 TENANT；未知 action 阻断 contract。
- expand 保留旧唯一键和旧写入口，并增加 tenant-aware 兼容索引。
- 九个 Monitor Entity、共享 `AuditLogMapper`、八个 Monitor Mapper 和四份 Mapper XML 已同步 tenant/scope/version 字段及 tenant-aware 入口。
- 四份 XML 均使用显式 Entity `resultMap`；旧 Mapper 方法未删除。
- `V2_4_2` 包含显式 `p3cCodeReady` 门禁、十项数据门禁、最终 `NOT NULL`、audit CHECK 和组合唯一键切换，但本批没有执行。

## 隔离验证

- 空链 Schema `wifi_test_13c_empty_20260811_1226` 在冻结的 V2.1 因缺少 role=0 owner 首次停止；未修改历史迁移，也未原样重试。
- 重建该隔离 Schema 后先迁移到 V1.7，插入唯一不可登录测试 owner，再成功迁移到 `V2_4_1`。
- 升级 Schema `wifi_test_13c_upgrade_20260811_1226` 从真实 `wifi` 的临时只读快照恢复，初始为 43 张业务表、Flyway `2.3.2`，随后只执行 `V2_4_1`。
- 两条隔离链均成功到达 `V2_4_1`；升级链 installed rank 为 `9`，checksum 为 `848930306`，`success=1`。
- 隔离升级库的九个 tenant 字段、四个 version 字段和一个 scope 字段存在，十项 contract 前置计数均为 0。
- `V2_4_2` 未出现在任何隔离 Flyway 历史中。

## 真实数据库结果

- 迁移前身份、server UUID、43 张业务表和 Flyway rank 8 / `V2_3_2` / checksum `1407250874` 均与冻结事实一致。
- Flyway `6.4.4` 成功验证 15 个迁移，只应用一个迁移：`V2_4_1`。
- 迁移后 installed rank 为 `9`，checksum 为 `848930306`，`success=1`，历史表 execution time 为 `531 ms`。
- 九个 `tenant_id` 均存在且仍为 expand 可空态；四个 `version` 列和一个 `scope_type` 列存在。
- Audit 回填结果：PLATFORM / tenant NULL 为 42 条，TENANT / `default-tenant` 为 103 条。
- tenant NULL、tenant 引用孤儿、Location 关系、Alert/Rule 关系、RuleHit 关系、GeofenceState 关系、GeofenceEvent 关系、audit scope、未知 audit action 和目标组合键重复十项计数全部为 0。
- `V2_4_2` Flyway 历史计数为 0，contract 未被提前执行。

## 永久测试

- `TenantMonitorMigrationScriptTest`：3 个测试通过。
- `MonitorTenantPersistenceContractTest`：3 个测试通过。
- `MonitorTenantMapperMySqlIT`：在白名单隔离升级 Schema 上执行 9 条 tenant-aware 只读查询，1 个测试通过，失败、错误、跳过均为 0。
- MyBatis IT 不执行 `SELECT ... FOR UPDATE`；锁定读入口由静态 Mapper 契约测试覆盖。
- 相关定向 Maven Reactor 模块全部成功。

## 清理与恢复点

- 两个本批拥有的 `wifi_test_13c_*` Schema 已精确删除，残留计数为 0。
- 临时升级快照 `wifi-test-13c-upgrade-source-20260811-1226.sql` 已精确删除，残留计数为 0。
- 仓库外正式恢复点保持不变。
- 1.3-C 已完成实施和自审，当前等待用户验收；验收前不暂存、不提交、不进入 1.3-D。
