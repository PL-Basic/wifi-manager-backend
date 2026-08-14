# Demo 1.3 三域迁移回放与恢复点记录

## 目标与边界

- 目标服务器连接端点为 `localhost:3306`；MySQL 主机为 `xwh`，版本为 `8.4.8`，server UUID 为 `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- 真实业务 Schema 固定为 `wifi`，只执行数据库身份、Flyway 历史和对象存在性读取。
- 回放只使用 `wifi_test_13h_*` 白名单 Schema，不在真实 `wifi` 执行 Flyway。
- 不执行 `repair`、`clean`、baseline、UPDATE、DELETE、业务回填或约束手工修改。

## 迁移顺序修正

原未执行候选 `V2_4_2__tenant_monitor_contract.sql` 会在 `outOfOrder=false` 下阻断 V2.5-V2.9，导致正常 expand/contract 部署链不可执行。

修正后：

- 默认 Flyway target 为 V2.9.1。
- `p3cCodeReady` 默认 `false`。
- V2.5、V2.5.1、V2.6、V2.7、V2.8、V2.9、V2.9.1 顺序执行。
- Monitor contract 后移为 `V2_10__tenant_monitor_contract.sql`。
- 只有显式 target V2.10 且 `p3cCodeReady=true` 时才执行 contract。

原 V2.4.2 从未进入真实 Flyway 历史；本次没有修改任何已执行迁移。

## 真实数据库只读事实

回放前、cleanup 前和 cleanup 后均通过 `identity`：

- `database()=wifi`
- `@@hostname=xwh`
- 端口 `3306`
- MySQL `8.4.8`
- Flyway installed rank `9`
- 最新 version `2.4.1`
- 最新 checksum `848930306`
- 最新记录 `success=1`
- V2.5 至 V2.10 历史记录数为 0
- V2.9 新增对象在真实 `wifi` 中不存在
- V2.9.1 账号命令收据表和扩展对象在真实 `wifi` 中不存在

## 冻结快照

V2.3.1 快照：

- 文件：`F:\MyProject\summary\backups\wifi-demo-1.3b-pre-v2_3_2-20260810-223858.sql`
- SHA-256：`0FB32B70B385D10EC34E3D87F7F16A8E02A31B926C097384902209328B6023FE`
- 最新 Flyway version：`2.3.1`
- 业务表：43

V2.3.2 快照：

- 文件：`F:\MyProject\summary\backups\wifi-demo-1.3c-pre-v2_4_1-20260811-123417.sql`
- SHA-256：`288CEDF9A455CF0C1D6B570E03AB674B0D07409B7A812D2FB1279123536337D1`
- 最新 Flyway version：`2.3.2`
- 业务表：43

快照路径和 hash 是回放输入，不是执行真实迁移的授权。

## 永久回放入口

`MigrationReplayMySqlIT` 默认关闭，只有以下条件同时有效时才运行：

- `WIFI_TEST_13H_ENABLED=true`
- `WIFI_TEST_13H_ALLOW_SCHEMA_DDL=true`
- 专用测试账号标记
- 本机 server JDBC URL
- 目标 server UUID/hostname
- Run ID 和 owner token
- 两份固定 hash 快照

Schema 名固定派生为：

- `wifi_test_13h_control_<runId>`
- `wifi_test_13h_empty_<runId>`
- `wifi_test_13h_v231_<runId>`
- `wifi_test_13h_v232_<runId>`

`create` 在 control Schema 的 ownership manifest 中登记四个精确名称、Run ID 和 owner token。后续动作先核对归属；`cleanup` 仅在归属全部匹配时删除。

固定 Flyway 参数：

- `validateOnMigrate=true`
- `outOfOrder=false`
- `cleanDisabled=true`
- `baselineOnMigrate=false`

## 动作顺序

1. `identity`
2. `create`
3. `empty`
4. `restore-v231`
5. `upgrade-v231`
6. `restore-v232`
7. `upgrade-v232`
8. `verify-expand`
9. `generated-columns`
10. `contract-empty`
11. `contract-v231`
12. `contract-v232`
13. `verify`
14. `diff`
15. `cleanup`

每个动作独立断言输入状态和输出状态；不得在局部失败 Run 上继续后续动作。

## 最终成功 Run

Run ID：`20260814r7c`

### V2.9.1 默认部署链

- 空库链、V2.3.1 快照链和 V2.3.2 快照链均按默认 target 到达
  V2.9.1。
- `verify-expand` 证明三链均为 70 张业务表。
- 三链 columns/index/constraint 语义指纹一致。
- 两条快照链的 43 张基线表原有行数逐表保持。
- AI 表敏感列检查通过。

这一步单独证明正常 Flyway 部署链可以到达 V2.9.1，不依赖 P-3C contract。

### generated always 列

- `generated-columns` 在三条隔离链中真实注册 10 个 BaseMapper。
- insert/select/update 均由 MyBatis-Plus 真实执行；数据库生成列没有进入
  insert/update 写 SQL，select 结果可以回填。
- R7 动作复核 Marketplace 的 `PURCHASE -> DIRECT_PURCHASE`；其余合法
  映射和非法类型拒绝由已验收的独立
  `MarketplaceGeneratedSourceTypeMySqlIT` 证明，不计入本 Run。
- 每条链均在事务回滚后断言测试表行数保持。

### V2.10 contract 链

- `contract-empty`、`contract-v231`、`contract-v232` 分别从已验证
  V2.9.1 起点执行。
- 三个 contract 动作均显式设置 target V2.10 和 `p3cCodeReady=true`。
- 每条链只执行 1 个新迁移并 validate 成功。
- 最终 `verify` 证明三链均为 V2.10、70 张业务表且结构语义一致。
- `diff` 再次比较三链 columns/index/constraint 指纹，结果一致。

这一步只证明 contract 文件和门禁在代码就绪条件下可执行，不授权在真实 `wifi` 提前执行 V2.10。

## R7 失败 Run 的处理

Run `20260814r7a` 和 `20260814r7b` 在隔离 Schema 中暴露 MySQL 8.4 对单值
CHECK 的元数据规范化差异：`IN ('SUCCEEDED')` 可能返回等号形式，且
`check_clause` 保留反斜杠转义。V2.9.1 前置和后置门禁已统一兼容两种等价
签名并移除转义字符后比较。

- 两个失败 Run 均在首次失败后停止，没有原命令盲目重试。
- 真实 `wifi` 未写。
- 两个 Run 的四个 Schema 均先核对 ownership manifest，再精确 cleanup
  为 0。
- 修正后使用全新 Run ID `20260814r7c` 从 identity/create 重新开始。

## 代码与离线测试

- R6 独立验收证据为 Auth 21 项、User 9 项、所有权架构 7 项，共 37 项；
  最新并发修正测试 7/7 通过。
- R7 六个真实服务 Application Context 测试各 1 项，共 6 项，失败、错误、
  跳过均为 0；定向 Reactor 16 个模块 `BUILD SUCCESS`。
- Auth、User、Device、Tenant、Monitor 分别只注册 6/12/9/12/10 个允许
  Mapper；Admin 无 DataSource、MyBatis 类、Mapper/Entity Bean 或 Mapper
  XML。
- Auth、User、Device、Monitor 的 Mapper XML 在真实服务 Context 中装配；
  Tenant 明确为 0 XML。
- `UserAccountPersistenceMigrationScriptTest` 6/6 通过。
- Run `20260814r7c` 的 15 次独立 MySQL 动作均为 1/1，失败、错误、跳过
  均为 0。

## 仓库清点

- 迁移文件：22
- 候选业务表：70
- 生产 `@TableName` Entity：68
- Java Mapper：71
- Mapper XML：15

差集说明：

- `t_session_user_guard` 和 `t_client_access_guard` 有专用原生 Mapper，按既有设计无 Entity。
- `sys_user` 只有 User 的 Mapper 直接访问；Auth 经受控账号契约读取或发起
  命令。
- `MonitorHealthMapper` 不绑定业务表。
- `wifi-common-mybatis` 有 User、Device、Monitor、Tenant 四个消费者，只
  提供自动配置，不含业务 Entity、Mapper 或 XML。

## 零残留

- Run `20260814r7a`、`20260814r7b` 和 `20260814r7c` 均按 manifest 精确
  cleanup。
- 最终 `wifi_test_13h_* = 0`、`wifi_test_13r3_* = 0`。
- cleanup 后再次执行真实 `wifi` identity，仍为 rank 9 / V2.4.1 / checksum `848930306`。

## 结论

- V2.5-V2.9.1 默认部署链可行。
- V2.10 contract 与默认链解耦，必须由第二阶段代码就绪门禁显式开启。
- 六个真实服务 Context、Mapper 注册边界、XML 装配、generated 列和三链
  可恢复迁移均已形成永久验证入口。
- 1.3 仅交付 Schema、Entity、基础映射、持久化所有权和迁移验证。
- Worker 机制归 1.5，具名业务 Mapper 和并发数据访问归 2.3。
- 当前等待用户重新验收 1.3；验收前不暂存、不提交、不进入 1.4。
