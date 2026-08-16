# Demo 1.3-D SaaS 配额与套餐分配实施记录

> 2026-08-11 边界修正：本文保留 D 批当时因原 V2.4.2 阻断而未运行后续迁移的历史事实。原候选现为 `V2_10`；当前 1.3 只保留 Entity 与基础 Mapper，锁行、额度更新和并发访问归 2.3。

## 范围与边界

- 执行日期：2026-08-11。
- 目标：只补齐 V2.1 SaaS 持久化缺口，新增幂等套餐分配和配额预留底座，不进入 1.3-E。
- 目标数据库：`localhost:3306/wifi`；MySQL 主机名 `xwh`；MySQL `8.4.8`；server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- `V2_5__saas_quota_and_assignment_schema.sql` 仅作为候选未执行迁移新增。
- `V2_4_2` 仍受 P-3C 代码就绪门禁约束，因此未在真实库或隔离库越过它执行 `V2_5`。
- 未执行 Flyway migrate、repair、clean、手工 DDL、DML、回填、约束收紧或数据库测试写入。

## 实时只读门禁

- Flyway 最新记录仍为 installed rank `9`、`V2_4_1`、checksum `848930306`、`success=1`。
- `V2_4_2` 与 `V2_5` 的 Flyway 历史总计数为 0。
- V2.1 的 `t_saas_plan`、`t_saas_plan_version`、`t_tenant_subscription`、`t_tenant_quota`、`t_tenant_usage_daily` 五张表全部存在。
- 五张表的实时行数依次为 `0/0/0/0/0`；`t_tenant_plan_assignment` 和 `t_tenant_quota_reservation` 均不存在。
- 套餐版本孤儿、非法限额、已发布版本缺发布时间、当前发布版本引用异常均为 0。
- 订阅孤儿、非法期间、有效订阅引用未发布版本均为 0。
- 配额孤儿、负数、已用量超限和每日用量孤儿、负数均为 0。
- 当前数据与 V2.1 约束一致，没有触发 1.3-D 停止条件。

## 迁移与映射

- `V2_5` 不重建 V2.1 五张 SaaS 表；迁移先验证五表存在、四组历史数据门禁和新表 partial DDL 状态。
- 新增 `t_tenant_plan_assignment`，以 `(tenant_id, source_type, client_request_id)` 保证命令幂等，并保存请求 fingerprint、不可变套餐版本、期间、套餐快照 hash、来源、actor、结果订阅、状态和 version。
- 新增 `t_tenant_quota_reservation`，以 `(tenant_id, quota_type, business_type, business_key)` 保证业务预留幂等，并保存正数 amount、四态状态、过期时间和 version。
- 两张新表不建立跨域外键，不包含 Marketplace、公告、AI 或 Support 结构。
- 补齐 `SaasPlanVersion`、`TenantQuota`、`TenantUsageDaily`、`TenantPlanAssignment`、`TenantQuotaReservation` 五个 Entity。
- `SaasPlanVersionMapper` 不继承 `BaseMapper`，只暴露插入和读取，避免已发布套餐快照获得通用更新入口。
- `TenantSubscriptionMapper` 增加有效订阅锁行读取；V2.1 的生成列唯一键继续保证每租户最多一条 TRIAL/ACTIVE 订阅。
- `TenantQuotaMapper` 使用 tenant、quota type、expectedVersion 条件更新；预留要求正数且不超限，释放要求已用量充足，限额更新不得低于已用量。
- `TenantUsageDailyMapper` 只提供正数原子累计；Assignment 和 Reservation Mapper 均提供幂等键读取、锁行读取和受状态/version 约束的转换。

## 永久测试

- `SaasQuotaMigrationScriptTest`：4 个测试通过，覆盖 V2.1 复用、数据/partial DDL 门禁、Assignment/Reservation 唯一键和单有效订阅生成列唯一约束。
- `SaasQuotaPersistenceContractTest`：3 个测试通过，覆盖 Entity 字段、不可变套餐版本 Mapper、锁行入口、配额 version/超限条件、Reservation 单次释放和每日用量只增入口。
- 定向命令：

```powershell
.\mvnw.cmd -pl database-migration,tenant-service -am `
  '-Dtest=SaasQuotaMigrationScriptTest,SaasQuotaPersistenceContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- Reactor 八个模块全部成功，共 7 个定向测试通过，失败、错误、跳过均为 0。
- `SaasQuotaMapperMySqlIT` 在同一次 `testCompile` 中编译成功；其默认关闭，只接受 `wifi_test_*`、显式写许可和专用测试账号标记。
- MySQL IT 设计为事务内写入并回滚，再以只读连接断言零残留；覆盖单有效订阅、重复 Assignment、重复 Reservation、超限、陈旧 version 冲突、只释放一次和每日用量只增。
- 因合法 V2.5 白名单 Schema 不能越过未执行的 `V2_4_2` 创建，本批未运行 MySQL IT，不把未执行入口记录为通过证据。

## 自审与恢复点

- 已完整读取 V2.5、五个 Entity、六个 Mapper 和三个永久测试文件，并核对 V2.1 原始 DDL。
- 修正 `SaasQuotaMapperMySqlIT` 中 lambda 捕获重复赋值 ID 的编译错误，已登记 `ERR-20260811-195`；修正后定向验证通过。
- 前端 9 个既有 tracked dirty 和固件 10 个既有 dirty 保持只读；后端 P-3B 文件未被修改、重命名、暂存或提交。
- 暂存区保持为空；未执行 commit 或 push。
- 1.3-D 已完成可执行范围内的实现和自审，当前等待用户验收；验收前不暂存、不提交、不进入 1.3-E。
