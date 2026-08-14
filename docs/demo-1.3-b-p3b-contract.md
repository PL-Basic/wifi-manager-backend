# Demo 1.3-B P-3B Contract 实施记录

## 范围与边界

- 执行日期：2026-08-10。
- 目标：只关闭 Demo 1.3-B 的 P-3B 数据库 contract，不进入 1.3-C。
- 目标数据库：`localhost:3306/wifi`；MySQL 主机名 `xwh`；MySQL `8.4.8`；server UUID `13ce8e61-3cc1-11f1-88ae-00e04c370235`。
- 迁移文件：`V2_3_2__tenant_device_contract.sql`，仓库 Flyway `6.4.4` checksum `1407250874`。
- 未修改 P-3B 业务代码或迁移文件；未执行 Flyway repair/clean、baseline、手工 DDL、UPDATE、DELETE 或回填。

## 恢复点确认

- 文件：`F:\MyProject\summary\backups\wifi-demo-1.3b-pre-v2_3_2-20260810-223858.sql`。
- 大小：`641426` 字节。
- SHA-256：`0fb32b70b385d10ec34e3d87f7f16a8e02a31b926c097384902209328b6023fe`。
- 文件级检查：44 个 `CREATE TABLE`、28 个数据 INSERT 语句、1 个 dump 完成标记。
- 用户已明确确认该文件作为本次恢复点，并确认旧版本服务写入已经停止。
- 该备份尚未做隔离库恢复演练；这是当前保留的恢复验证缺口，不影响本次已明确授权的 Demo 迁移事实。

## 执行前实时门禁

- JDBC 目标、主机名、server UUID、MySQL 版本与 1.3-A 一致；审计事务为只读。
- 业务表数为 43。
- Flyway 历史为 baseline `1.6` 加成功的 `V1_7`、`V2_1`、`V2_2`、`V2_2_1`、`V2_2_2`、`V2_3_1`；`V2_3_2` 不在历史中。
- 六个已执行 SQL checksum 与仓库使用 Flyway `6.4.4` 重算的结果一致；`V2_3_2` checksum 为 `1407250874`。
- 九张 P-3B 表的 `tenant_id` 均仍可空；目标 contract 唯一键尚未生效。

迁移前和迁移后使用相同只读 SQL 得到以下结果：

| 门禁 | 迁移前 | 迁移后 |
| --- | ---: | ---: |
| tenant NULL | 0 | 0 |
| tenant 引用孤儿 | 0 | 0 |
| Session/Node tenant 不一致 | 0 | 0 |
| Traffic 关系缺失或 tenant 不一致 | 0 | 0 |
| Signal 关系缺失或 tenant 不一致 | 0 | 0 |
| WiFi 配置关系缺失或 tenant 不一致 | 0 | 0 |
| Command 关系缺失或 tenant 不一致 | 0 | 0 |
| 黑名单 tenant/mac 重复 | 0 | 0 |
| Traffic tenant/device/event 重复 | 0 | 0 |
| Session guard tenant/user 重复 | 0 | 0 |
| Client guard tenant/mac 重复 | 0 | 0 |
| 全局 deviceCode 重复 | 0 | 0 |
| 全局 requestId 重复 | 0 | 0 |

## Flyway 执行结果

- 使用 `database-migration` 唯一迁移入口，显式锁定 JDBC URL 为 `localhost:3306/wifi`，`baseline-on-migrate=false`。
- Flyway `6.4.4` 成功验证 13 个仓库迁移。
- 执行前 schema 版本为 `2.3.1`。
- 只应用 1 个迁移：`V2_3_2__tenant_device_contract.sql`。
- 执行后 installed rank 为 `8`，checksum 为 `1407250874`，`success=1`，execution time 为 `703 ms`。
- Flyway 报告 MySQL `8.4` 高于该版本已测试到的 MySQL `8.0`，但 validate、migrate 和迁移后结构复核均成功；该兼容性警告保留为后续基础设施升级事项。

首次在受限网络中启动 Maven 时，项目模型解析因无法下载 Spring Boot parent 而停止；应用和 Flyway 均未启动，数据库未执行 SQL。按 `ERR-20260807-024` 使用允许联网的同一本机 Maven 路径后，一次执行成功，没有重复迁移。

## Contract 结构复核

- 九张表的 `tenant_id` 均为 `NOT NULL`：
  - `t_esp32_node`
  - `t_session`
  - `t_mac_blacklist`
  - `t_traffic_log`
  - `t_client_signal`
  - `t_session_user_guard`
  - `t_client_access_guard`
  - `t_device_command`
  - `t_device_wifi_config`
- `t_mac_blacklist.uk_blacklist_tenant_mac`：`tenant_id,mac`，唯一。
- `t_traffic_log.uk_traffic_tenant_device_event`：`tenant_id,device_code,event_id`，唯一。
- `t_session_user_guard.PRIMARY`：`tenant_id,user_id`。
- `t_client_access_guard.PRIMARY`：`tenant_id,mac`。
- 六个被 contract 替代的旧索引计数为 0。
- 业务表数仍为 43，数据库身份和 server UUID 未变化。

## 永久测试

执行四个 P-3B 已登记测试类：

- `AuthSessionWebClientTest`：2 个通过。
- `DeviceIdempotencyRegressionTest`：7 个通过。
- `AdminUserControllerAuthorizationTest`：3 个通过。
- `GlobalExceptionHandlerTest`：3 个通过。

Reactor 九个模块全部成功；合计 15 个测试通过，失败、错误、跳过均为 0。

## 结论与恢复点

- `V2_3_2` 已在备份确认、旧版本写入停止和全部实时门禁通过后执行并复核。
- P-3B 数据 contract 已关闭；迁移文件从此视为已执行历史，禁止修改、重命名或复用版本号。
- Demo 1.3-B 已完成实施和自审，当前等待用户验收。
- 验收前不暂存、不提交、不进入 1.3-C。
