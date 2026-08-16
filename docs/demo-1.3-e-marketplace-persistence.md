# Demo 1.3-E Demo 商城持久化实施记录

> 2026-08-11 边界修正：本文保留 E 批当时的历史事实。原 `V2_4_2` 候选现为 `V2_10`；Marketplace 专用业务 Mapper 和 MySQL IT 已从 1.3 撤回，当前模块只保留 Entity、纯 BaseMapper 和映射测试。

## 范围与边界

- 执行日期：2026-08-11。
- 目标：只建立 Demo 商城目录、订单、订单项和履约持久化底座，不进入 1.3-F。
- 目标数据库：`localhost:3306/wifi`；MySQL 主机名 `xwh`；MySQL `8.4.8`；server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- `V2_5_1__marketplace_demo_schema.sql` 仅作为候选未执行迁移新增。
- 因 `V2_4_2`、`V2_5` 均未执行，本批未在真实库或隔离库越过顺序门禁执行 `V2_5_1`。
- 未执行 Flyway migrate、repair、clean、手工 DDL、DML、回填、约束收紧或数据库测试写入。

## 实时只读门禁

- Flyway 最新记录仍为 installed rank `9`、`V2_4_1`、checksum `848930306`、`success=1`。
- `V2_4_2`、`V2_5`、`V2_5_1` 的 Flyway 历史计数均为 0。
- 五张 Marketplace 表、`t_entitlement_order.source_type/source_reference`、来源唯一索引和 Marketplace CHECK 的实时计数均为 0。
- `t_entitlement_order` 仍为 PURCHASE 3 行、REWARD 2 行；`t_payment_record` 仍为 0 行。
- 五张 V2.1 SaaS 表仍均为 0 行，数据库状态没有触发 1.3-E 停止条件。

## 迁移与映射

- 新增最小 `marketplace-service`，根 Reactor 已登记该模块；模块没有启动类、Controller、Service 编排或公共 API。
- `t_market_product` 只保存三类商品目录与发布状态；`t_market_sku` 保存可比较 JSON、金额分和三选一稳定目标引用。
- `t_market_order` 以生成 subject key 保证 USER/TENANT 主体幂等键不受 NULL 唯一语义影响，并保存请求 fingerprint、Demo 付款真相、状态和 version。
- `t_market_order_item` 保存下单时商品、规格、单价、数量、小计和目标引用快照，不复制目标域当前状态。
- `t_market_fulfillment` 作为 Marketplace 自有履约 Outbox，保存 event key、目标业务键、重试、lease、worker、固定错误键和 version。
- TARGET_DOMAIN 履约领取要求有效新 lease，完成/失败要求当前 worker、未过期 lease 和 expectedVersion；硬件只允许 TRACK_ONLY 状态集合。
- 五个 Marketplace Mapper 均不继承 `BaseMapper`，只暴露受控插入、归属读取、锁行读取和状态/version 更新入口。
- 个人权益收据复用 `t_entitlement_order`：生成 `source_type` 区分 MARKETPLACE、ADMIN_REWARD 和 DIRECT_PURCHASE，`source_reference` 保存 Marketplace event key，并建立租户内来源唯一键。
- Marketplace 收据使用专用 insert；既有 PURCHASE/REWARD insert 未引用新列，且收据路径不创建 `t_payment_record`。
- 未新增库存、硬件资产、租赁、合同、物流或真实支付结构。

## 永久测试

- `MarketplaceMigrationScriptTest`：5 个测试通过，覆盖五表边界、三类稳定目标、金额快照、订单幂等、履约 lease、硬件 TRACK_ONLY、权益来源和无第二支付表。
- `MarketplacePersistenceContractTest`：4 个测试通过，覆盖 Entity 字段、受控 Mapper、可售 SKU、不可变订单项快照、订单状态/version 和履约 lease/version 条件。
- `MarketplaceEntitlementReceiptContractTest`：3 个测试通过，覆盖来源字段/常量、租户来源锁行、专用收据 insert、原 insert 不变和无支付记录。
- 定向命令：

```powershell
.\mvnw.cmd -pl database-migration,user-service,marketplace-service -am `
  '-Dtest=MarketplaceMigrationScriptTest,MarketplaceEntitlementReceiptContractTest,MarketplacePersistenceContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- Reactor 九个模块全部成功，共 12 个定向测试通过，失败、错误、跳过均为 0。
- `MarketplaceMapperMySqlIT` 在同一次 testCompile 中编译成功；默认关闭，只接受 `wifi_test_*`、显式写许可和专用测试账号标记。
- MySQL IT 设计为事务内写入并回滚，再用只读连接断言零残留；覆盖三类 SKU、价格快照、重复订单、lease/version 和硬件 TRACK_ONLY。
- 合法顺序 Schema 尚不存在，因此本批未运行 MySQL IT，不把未执行入口记录为通过证据。

## 自审与恢复点

- 已完整读取候选迁移、五个 Entity、五个 Mapper、三个离线契约测试和永久 MySQL IT，并逐项核对 DDL/Entity/Mapper 字段与状态条件。
- 定向 tracked diff check、E 文件尾随空白扫描和敏感值扫描均通过。
- 已知 Windows 显式 wildcard 路径错误继续归入 `ERR-20260811-185`；更新记录后改用目录加 `--glob`，未原样重试。
- 前端 9 个既有 tracked dirty 和固件 10 个既有 dirty 保持只读；后端既有 P-3B 文件未被修改、重命名、暂存或提交。
- 暂存区保持为空；未执行 commit 或 push。
- 1.3-E 已完成可执行范围内的实现和自审，当前等待用户验收；验收前不暂存、不提交、不进入 1.3-F。
