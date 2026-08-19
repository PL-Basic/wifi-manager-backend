# Wifi Manager Demo 核心用例索引 v1

## 1. 基线与用途

本文档是 Demo 2.1 批次 A（S0）的中央业务索引。它冻结第一阶段实际产物，
为后续八个 `P21-*` 证据包提供唯一输入，不代表这些工作包已经启动或完成。

- 后端冻结提交：`5d5222f23cb0b3f589c4d35e34785554def81479`
- 前端冻结提交：`0b68b097c0e1e184449a074204def67c4240986e`
- 固件冻结提交：`4f42cc16de5274c807c9ad11a18ce537cc468e6a`
- 机器事实源：
  `wifi-test-kit/src/test/resources/fixtures/core-use-cases-v1/manifest.json`
- HTTP/WS 入口事实源：[backend-api-index.md](backend-api-index.md)

机器索引固定采用“中央 manifest + 分域 fixture”。manifest 明列 8 个业务
分域文件、`cross-domain-events.json` 和 `audit-closures.json`；永久测试只读取
这份固定清单，在内存中组合 58 个组、183 个 operation 和 42 个状态模型，
不得递归扫描目录，也不得重新落盘单体聚合 JSON。分域归属为
AUTH、USER/ENTITLEMENT、TENANT/SAAS、MARKETPLACE、
SUPPORT/ANNOUNCEMENT、AI、DEVICE 和 MONITOR/GOVERNANCE。每个
`groupId`、`operationId`、`stateMachineId` 只归一个分域文件；跨 owner 事件
只存在于 `cross-domain-events.json`，审计关闭矩阵不参与业务事实判定。

58 个编号是业务用例组，不是 HTTP endpoint 数量。每个具体查询或命令使用
`<GROUP_ID>.<QUERY|COMMAND>.<UPPER_SNAKE_ACTION>` 格式的全局唯一
`operationId`。Controller、BFF、Feign 或 worker 只是入口，不获得第二个业务
owner。

S0 的 operation 自描述契约不允许继承。每个 operation 必须在自身对象中物理
声明唯一 `owner`、`actor/context`、`stateMachineRefs` 或显式 `STATELESS`、
输入输出、错误、实现事实、冲突、证据和唯一 `targetLayer`；COMMAND 还必须
声明幂等、fingerprint、事务边界、外部效果、审计和合法状态边，QUERY 还
必须声明 scope、分页或明确上限及 `readOnly=true`。不得依赖全局默认或组级继承
补齐任何 operation 字段。

`implementationStatus` 只使用：

- `REUSE`：第一阶段实现已经满足本组当前冻结语义，`targetLayer=NONE`。
- `PARTIAL`：存在可复用生产链，但仍有一个明确主缺口。
- `GAP`：只有持久化载体、局部骨架或尚无业务入口。

`PARTIAL/GAP` 的 `targetLayer` 只允许 `2.2/2.3/2.4/2.5` 中一个。历史计划与
当前机器事实的差异保存在 `conflicts[]`，冲突本身不是第四种
`implementationStatus`。

## 2. 可信 actor 与 context

- `ANONYMOUS_USER`：仅用于注册和登录入口，不能提供可信 tenant 或 user。
- `ACCOUNT_USER`：已验证的全局账号本人。
- `TENANT_MEMBER`：当前 ACTIVE membership 的租户成员。
- `TENANT_ADMIN`：当前租户的 `TENANT_OWNER/TENANT_ADMIN`。
- `PLATFORM_STAFF`：ACTIVE `PlatformStaff` 且显式持有
  `PLATFORM_SUPER_ADMIN`。
- `INTERNAL_SERVICE`、`SCHEDULED_SERVICE`、`DEVICE_EVENT`：独立机器身份，
  不能借用浏览器 actor。

可信 context 固定为 `NONE/PLATFORM/TENANT/PLATFORM_TENANT/INTERNAL/
SCHEDULED/DEVICE`。`PLATFORM_TENANT` 必须使用
`TENANT_SUPPORT/SECURITY_INVESTIGATION/BILLING_RECONCILIATION/DATA_REPAIR`
之一作为 `reasonCode`。平台 actor 与 tenant owner 是两个独立主体。

## 3. 真实状态来源

<!-- CORE_STATE_MACHINE_COUNT: 42 -->
<!-- CORE_OPERATION_COUNT: 183 -->

状态目录按真实业务对象建模，共 42 个对象级状态模型。数值状态中的
`machineValue` 是持久化/API 整数，`semanticName` 只是明确映射；字符串
状态按原值冻结。`PARTIAL/GAP` 表示值域存在但生产转换仍不完整，不能把
Schema、Entity、Mapper 或 Outbox 载体冒充业务闭环。

| stateMachineId | businessObject | owner | field | type | machine values | source | lifecycle fact |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `AUTH_REFRESH_SESSION` | RefreshSession | AUTH | `t_auth_refresh_session.status` | STRING | ACTIVE/REVOKED/EXPIRED | `database-migration/src/main/resources/db/migration/V2_2__auth_session_and_tenant_context.sql`<br>`auth-service/src/main/java/com/plagod/entity/auth/AuthRefreshSession.java` | PARTIAL; EXPIRED is schema-valid, but the current expiry path persists REVOKED with an expiry reason. |
| `AUTH_REFRESH_TOKEN` | RefreshToken | AUTH | `t_auth_refresh_token.status` | STRING | ACTIVE/ROTATED/REPLAYED/REVOKED/EXPIRED | `database-migration/src/main/resources/db/migration/V2_2__auth_session_and_tenant_context.sql`<br>`auth-service/src/main/java/com/plagod/entity/auth/AuthRefreshToken.java` | PARTIAL; EXPIRED is schema-valid, but no production path currently writes it. |
| `USER_ACCOUNT` | User | USER | `sys_user.status` | INTEGER | 0=DISABLED/1=ENABLED | `database-migration/src/main/resources/db/migration/V1_1__user_schema.sql`<br>`user-service/src/main/java/com/plagod/entity/user/User.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/user/UserVO.java` | FROZEN; NONE->1(ENABLED); 1(ENABLED)->0(DISABLED); 0(DISABLED)->1(ENABLED) |
| `USER_OPERATION_REQUEST` | UserOperationRequest | USER | `t_user_operation_request.status` | INTEGER | 0=PENDING/1=APPROVED/2=REJECTED | `database-migration/src/main/resources/db/migration/V1_4__user_operation_schema.sql`<br>`user-service/src/main/java/com/plagod/service/impl/UserOperationRequestServiceImpl.java`<br>`user-service/src/main/java/com/plagod/entity/user/UserOperationRequest.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/user/UserOperationRequestVO.java` | FROZEN; NONE->0(PENDING); 0(PENDING)->1(APPROVED); 0(PENDING)->2(REJECTED) |
| `ENTITLEMENT_ORDER` | Entitlement Order | USER | `t_entitlement_order.status` | STRING | PENDING_PAYMENT/PAID/FULFILLED/CANCELLED/CLOSED/REFUNDING/PARTIALLY_REFUNDED/REFUNDED | `wifi-common-api/src/main/java/com/plagod/constant/EntitlementTradeConstants.java`<br>`user-service/src/main/java/com/plagod/entity/entitlement/EntitlementOrder.java`<br>`user-service/src/main/java/com/plagod/service/impl/EntitlementOrderServiceImpl.java` | FROZEN; NONE->PENDING_PAYMENT; PENDING_PAYMENT->PAID; PENDING_PAYMENT->CANCELLED; PENDING_PAYMENT->CLOSED; PAID->FULFILLED; FULFILLED->REFUNDING; REFUNDING->PARTIALLY_REFUNDED; REFUNDING->REFUNDED; PARTIALLY_REFUNDED->REFUNDED |
| `ENTITLEMENT_PAYMENT` | Entitlement Payment | USER | `t_payment_record.status` | STRING | CREATED/SUCCEEDED/FAILED/CLOSED/PARTIALLY_REFUNDED/REFUNDED | `wifi-common-api/src/main/java/com/plagod/constant/EntitlementTradeConstants.java`<br>`user-service/src/main/java/com/plagod/entity/entitlement/PaymentRecord.java`<br>`user-service/src/main/java/com/plagod/service/impl/PaymentServiceImpl.java` | PARTIAL; FAILED is a real constant, but no production write path was found. |
| `ENTITLEMENT_REFUND` | Entitlement Refund | USER | `t_refund_record.status` | STRING | REQUESTED/REJECTED/PROCESSING/SUCCEEDED/FAILED | `wifi-common-api/src/main/java/com/plagod/constant/EntitlementTradeConstants.java`<br>`user-service/src/main/java/com/plagod/entity/entitlement/RefundRecord.java`<br>`user-service/src/main/java/com/plagod/service/impl/RefundServiceImpl.java` | FROZEN; NONE->REQUESTED; REQUESTED->REJECTED; REQUESTED->PROCESSING; PROCESSING->SUCCEEDED; PROCESSING->FAILED |
| `ENTITLEMENT_PURCHASE` | Entitlement Purchase | USER | `t_duration_purchase.status` | INTEGER | 1=USABLE/2=EXHAUSTED/3=REFUNDED/4=REFUND_RESERVED | `wifi-common-api/src/main/java/com/plagod/constant/EntitlementTradeConstants.java`<br>`user-service/src/main/java/com/plagod/entity/entitlement/DurationPurchase.java`<br>`user-service/src/main/java/com/plagod/service/impl/RefundServiceImpl.java` | FROZEN; NONE->1(USABLE); 1(USABLE)->2(EXHAUSTED); 1(USABLE)->4(REFUND_RESERVED); 4(REFUND_RESERVED)->1(USABLE); 4(REFUND_RESERVED)->3(REFUNDED) |
| `ENTITLEMENT_LEASE_RECEIPT` | Entitlement LeaseReceipt | USER | `t_entitlement_lease_receipt.receipt_status` | STRING | PENDING/COMPLETED | `database-migration/src/main/resources/db/migration/V2_9_2__transaction_outbox_idempotency_contract.sql`<br>`user-service/src/main/java/com/plagod/entity/entitlement/EntitlementLeaseReceipt.java`<br>`user-service/src/main/java/com/plagod/service/impl/EntitlementLeaseServiceImpl.java` | FROZEN; NONE->PENDING; PENDING->COMPLETED |
| `TENANT_ACCOUNT` | Tenant | TENANT | `t_tenant.status` | STRING | ACTIVE/DISABLED | `database-migration/src/main/resources/db/migration/V2_1__tenant_foundation.sql`<br>`tenant-service/src/main/java/com/plagod/entity/Tenant.java` | FROZEN; NONE->ACTIVE; ACTIVE->DISABLED; DISABLED->ACTIVE |
| `TENANT_MEMBER` | Member | TENANT | `t_tenant_member.status` | STRING | ACTIVE/REMOVED | `database-migration/src/main/resources/db/migration/V2_1__tenant_foundation.sql`<br>`tenant-service/src/main/java/com/plagod/entity/TenantMember.java` | GAP; ACTIVE and REMOVED are schema values, but the production removal transition is missing. |
| `PLATFORM_STAFF_STATUS` | PlatformStaff | TENANT | `t_platform_staff.status` | STRING | ACTIVE/DISABLED | `database-migration/src/main/resources/db/migration/V2_1__tenant_foundation.sql`<br>`tenant-service/src/main/java/com/plagod/entity/PlatformStaff.java` | GAP; The status vocabulary exists, but no production PlatformStaff lifecycle service freezes legal transitions. |
| `SAAS_PLAN` | Plan | TENANT | `t_saas_plan.status` | STRING | DRAFT/ACTIVE/DISABLED | `database-migration/src/main/resources/db/migration/V2_1__tenant_foundation.sql`<br>`tenant-service/src/main/java/com/plagod/entity/SaasPlan.java` | GAP; Schema and Entity freeze values only; production Plan transitions are absent. |
| `SAAS_PLAN_VERSION` | PlanVersion | TENANT | `t_saas_plan_version.status` | STRING | DRAFT/PUBLISHED/RETIRED | `database-migration/src/main/resources/db/migration/V2_1__tenant_foundation.sql`<br>`tenant-service/src/main/java/com/plagod/entity/SaasPlanVersion.java` | GAP; Schema and Entity freeze values only; publish/retire transitions are absent. |
| `TENANT_SUBSCRIPTION` | Subscription | TENANT | `t_tenant_subscription.status` | STRING | TRIAL/ACTIVE/EXPIRED/CANCELLED | `database-migration/src/main/resources/db/migration/V2_1__tenant_foundation.sql`<br>`tenant-service/src/main/java/com/plagod/entity/TenantSubscription.java` | GAP; Schema and Entity freeze values only; the complete Subscription transition service is absent. |
| `TENANT_PLAN_ASSIGNMENT` | Assignment | TENANT | `t_tenant_plan_assignment.status` | STRING | PENDING | `database-migration/src/main/resources/db/migration/V2_5__saas_quota_and_assignment_schema.sql`<br>`tenant-service/src/main/java/com/plagod/entity/TenantPlanAssignment.java` | GAP; Only default PENDING is source-backed; no CHECK, constant, or complete transition vocabulary exists. |
| `TENANT_QUOTA_RESERVATION` | Reservation | TENANT | `t_tenant_quota_reservation.status` | STRING | RESERVED/CONFIRMED/RELEASED/EXPIRED | `database-migration/src/main/resources/db/migration/V2_5__saas_quota_and_assignment_schema.sql`<br>`tenant-service/src/main/java/com/plagod/entity/TenantQuotaReservation.java` | GAP; Schema freezes Reservation values, but the production transition service is absent. |
| `MARKETPLACE_PRODUCT` | Marketplace Product | MARKETPLACE | `t_market_product.status` | STRING | DRAFT/PUBLISHED/UNPUBLISHED | `database-migration/src/main/resources/db/migration/V2_5_1__marketplace_demo_schema.sql`<br>`marketplace-service/src/main/java/com/plagod/entity/MarketplaceProduct.java` | GAP; Schema freezes Product values, but no production catalog transition service exists. |
| `MARKETPLACE_SKU` | Marketplace SKU | MARKETPLACE | `t_market_sku.status` | STRING | ACTIVE/INACTIVE | `database-migration/src/main/resources/db/migration/V2_5_1__marketplace_demo_schema.sql`<br>`marketplace-service/src/main/java/com/plagod/entity/MarketplaceSku.java` | GAP; Schema freezes SKU values, but no production SKU transition service exists. |
| `MARKETPLACE_PAYMENT` | Marketplace Payment | MARKETPLACE | `t_market_order.payment_status` | STRING | PENDING/SUCCEEDED/FAILED/CLOSED | `database-migration/src/main/resources/db/migration/V2_5_1__marketplace_demo_schema.sql`<br>`marketplace-service/src/main/java/com/plagod/entity/MarketplaceOrder.java` | GAP; Schema freezes Marketplace Payment values, but no controlled production transition service exists. |
| `MARKETPLACE_ORDER` | Marketplace Order | MARKETPLACE | `t_market_order.order_status` | STRING | PENDING_DEMO_PAYMENT/PAID/FULFILLING/FULFILLED/CANCELLED/CLOSED/FULFILLMENT_FAILED | `database-migration/src/main/resources/db/migration/V2_5_1__marketplace_demo_schema.sql`<br>`marketplace-service/src/main/java/com/plagod/entity/MarketplaceOrder.java` | GAP; Schema freezes Marketplace Order values and invariants, but the complete production advancement loop is absent. |
| `MARKETPLACE_TARGET_FULFILLMENT` | TARGET_DOMAIN Fulfillment | MARKETPLACE | `t_market_fulfillment.status where fulfillment_mode=TARGET_DOMAIN` | STRING | PENDING/PROCESSING/SUCCEEDED/FAILED | `database-migration/src/main/resources/db/migration/V2_5_1__marketplace_demo_schema.sql`<br>`marketplace-service/src/main/java/com/plagod/service/MarketplaceFulfillmentWorker.java`<br>`marketplace-service/src/main/java/com/plagod/entity/MarketplaceFulfillment.java` | FROZEN; NONE->PENDING; PENDING->PROCESSING; PROCESSING->PROCESSING; PROCESSING->SUCCEEDED; PROCESSING->PENDING; PROCESSING->FAILED |
| `MARKETPLACE_TRACK_ONLY_FULFILLMENT` | TRACK_ONLY Fulfillment | MARKETPLACE | `t_market_fulfillment.status where fulfillment_mode=TRACK_ONLY` | STRING | PENDING/DEMO_DELIVERY_PENDING/DEMO_DELIVERED/CANCELLED | `database-migration/src/main/resources/db/migration/V2_5_1__marketplace_demo_schema.sql`<br>`marketplace-service/src/main/java/com/plagod/entity/MarketplaceFulfillment.java` | GAP; TRACK_ONLY values are schema-frozen, but production transitions are absent. |
| `ANNOUNCEMENT` | Announcement | SUPPORT | `t_announcement.status` | STRING | DRAFT/REVIEW_PENDING/REVIEW_APPROVED/REVIEW_REJECTED/MANUAL_REVIEW/PUBLISHED/WITHDRAWN | `database-migration/src/main/resources/db/migration/V2_6__announcement_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/Announcement.java` | GAP; Schema freezes values and review invariants, but the production Announcement transition service is absent. |
| `ANNOUNCEMENT_CONTENT_VERSION` | Announcement ContentVersion | SUPPORT | `t_announcement_content_version.status` | STRING | DRAFT/REVIEW_PENDING/REVIEW_APPROVED/REVIEW_REJECTED/MANUAL_REVIEW/PUBLISHED/WITHDRAWN | `database-migration/src/main/resources/db/migration/V2_6__announcement_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/AnnouncementContentVersion.java` | GAP; Schema freezes values and content-version invariants, but the production transition service is absent. |
| `COMMENT` | Comment | SUPPORT | `t_announcement_comment.status` | STRING | VISIBLE/USER_DELETED/ADMIN_HIDDEN | `database-migration/src/main/resources/db/migration/V2_6__announcement_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/AnnouncementComment.java` | GAP; Schema freezes Comment values, but production delete/hide transitions are absent. |
| `CAPABILITY_RESTRICTION` | CapabilityRestriction | SUPPORT | `t_user_capability_restriction.status` | STRING | ACTIVE/REVOKED | `database-migration/src/main/resources/db/migration/V2_6__announcement_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/UserCapabilityRestriction.java` | GAP; Schema freezes restriction values, but the production restriction transition service is absent. |
| `ANNOUNCEMENT_REVIEW_OUTBOX` | Announcement Review Outbox | SUPPORT | `t_support_content_review_outbox.status where scene=ANNOUNCEMENT_REVIEW` | STRING | PENDING/PROCESSING/SENT/FAILED/CANCELLED | `database-migration/src/main/resources/db/migration/V2_6__announcement_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/SupportContentReviewOutbox.java` | PARTIAL; Claim/send/retry are implemented, but no production path writes CANCELLED. |
| `AI_REVIEW_TASK` | AI Review Task | AI | `t_ai_review_task.task_status` | STRING | QUEUED/RUNNING/SUCCEEDED/FAILED/MANUAL_REQUIRED/STALE | `database-migration/src/main/resources/db/migration/V2_7__ai_review_schema.sql`<br>`ai-service/src/main/java/com/plagod/entity/AiReviewTask.java`<br>`ai-service/src/main/java/com/plagod/ai/task/AiReviewTaskWorker.java` | PARTIAL; FAILED and STALE are schema-valid, but no production path currently writes them. |
| `SUPPORT_SUBMISSION` | Support Submission | SUPPORT | `t_support_submission.status` | STRING | REVIEW_PENDING/MANUAL_REVIEW/ACCEPTED/SPAM_REJECTED/CONVERTED_TO_TICKET/SYSTEM_ABANDONED | `database-migration/src/main/resources/db/migration/V2_8__support_ticket_schema.sql`<br>`database-migration/src/main/resources/db/migration/V2_9_3__cross_cutting_recovery_contract.sql`<br>`support-service/src/main/java/com/plagod/entity/SupportSubmission.java`<br>`support-service/src/main/java/com/plagod/support/SupportReviewWorker.java` | GAP; Accept/reject/abandon and atomic Ticket conversion are schema-backed targets without a complete production service. |
| `SUPPORT_TICKET` | Ticket | SUPPORT | `t_support_ticket.status` | STRING | OPEN/IN_PROGRESS/WAITING_USER/RESOLVED/CLOSED | `database-migration/src/main/resources/db/migration/V2_8__support_ticket_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/SupportTicket.java` | GAP; The Schema freezes legal edges, but no production service atomically creates Ticket, first Message and transition. |
| `SUPPORT_REVIEW_OUTBOX` | Support Review Outbox | SUPPORT | `t_support_content_review_outbox.status where scene=SUPPORT_SUBMISSION_REVIEW` | STRING | PENDING/PROCESSING/SENT/FAILED/CANCELLED | `database-migration/src/main/resources/db/migration/V2_6__announcement_schema.sql`<br>`support-service/src/main/java/com/plagod/entity/SupportContentReviewOutbox.java` | PARTIAL; Claim/send/retry are implemented, but no production path writes CANCELLED. |
| `DEVICE_NODE` | DeviceNode | DEVICE | `t_esp32_node.status` | INTEGER | 0=OFFLINE/1=ONLINE | `database-migration/src/main/resources/db/migration/V1_5__device_schema.sql`<br>`device-service/src/main/java/com/plagod/service/impl/DeviceHeartbeatServiceImpl.java`<br>`device-service/src/main/java/com/plagod/entity/device/Esp32Node.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/device/DeviceNodeVO.java` | FROZEN; NONE->0(OFFLINE); 0(OFFLINE)->1(ONLINE); 1(ONLINE)->0(OFFLINE) |
| `DEVICE_COMMAND` | DeviceCommand | DEVICE | `t_device_command.status` | INTEGER | 0=PENDING/1=PUBLISHED/2=SUCCEEDED/3=EXECUTION_FAILED/4=PUBLISH_FAILED/5=TIMED_OUT | `database-migration/src/main/resources/db/migration/V1_5__device_schema.sql`<br>`device-service/src/main/java/com/plagod/constant/DeviceCommandStatus.java`<br>`device-service/src/main/java/com/plagod/entity/device/DeviceCommandRecord.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/device/DeviceCommandVO.java` | FROZEN; NONE->0(PENDING); 0(PENDING)->1(PUBLISHED); 0(PENDING)->4(PUBLISH_FAILED); 1(PUBLISHED)->2(SUCCEEDED); 1(PUBLISHED)->3(EXECUTION_FAILED); 1(PUBLISHED)->5(TIMED_OUT) |
| `DEVICE_WIFI_CONFIG` | DeviceWifiConfig | DEVICE | `t_device_wifi_config.status` | INTEGER | 0=DISPATCHING/1=STAGED/2=ACTIVE/3=FAILED/4=UNKNOWN/5=SUPERSEDED | `database-migration/src/main/resources/db/migration/V1_5__device_schema.sql`<br>`device-service/src/main/java/com/plagod/constant/DeviceWifiConfigStatus.java`<br>`device-service/src/main/java/com/plagod/entity/device/DeviceWifiConfigRecord.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/device/WifiConfigTaskVO.java` | FROZEN; NONE->0(DISPATCHING); 0(DISPATCHING)->1(STAGED); 0(DISPATCHING)->3(FAILED); 1(STAGED)->2(ACTIVE); 1(STAGED)->3(FAILED); 1(STAGED)->4(UNKNOWN); 4(UNKNOWN)->1(STAGED); 4(UNKNOWN)->2(ACTIVE); 0(DISPATCHING)->5(SUPERSEDED); 1(STAGED)->5(SUPERSEDED); 4(UNKNOWN)->5(SUPERSEDED) |
| `PORTAL_SESSION` | PortalSession | DEVICE | `t_session.status` | INTEGER | 0=CLOSED/1=ACTIVE/2=PENDING/3=WAITING_REPLACEMENT | `database-migration/src/main/resources/db/migration/V1_5__device_schema.sql`<br>`device-service/src/main/java/com/plagod/constant/SessionStatus.java`<br>`device-service/src/main/java/com/plagod/entity/device/SessionRecord.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/device/SessionRecordVO.java` | FROZEN; NONE->2(PENDING); 2(PENDING)->1(ACTIVE); 2(PENDING)->3(WAITING_REPLACEMENT); 3(WAITING_REPLACEMENT)->1(ACTIVE); 1(ACTIVE)->0(CLOSED); 2(PENDING)->0(CLOSED); 3(WAITING_REPLACEMENT)->0(CLOSED) |
| `ACCESS_RULE` | AccessRule | MONITOR | `t_access_rule.enabled` | INTEGER | 0=DISABLED/1=ENABLED | `database-migration/src/main/resources/db/migration/V1_6__monitor_schema.sql`<br>`monitor-service/src/main/java/com/plagod/entity/monitor/AccessRule.java` | FROZEN; NONE->1(ENABLED); 1(ENABLED)->0(DISABLED); 0(DISABLED)->1(ENABLED) |
| `ALERT_EVENT` | AlertEvent | MONITOR | `t_alert_event.status` | INTEGER | 0=UNHANDLED/1=HANDLED | `database-migration/src/main/resources/db/migration/V1_6__monitor_schema.sql`<br>`monitor-service/src/main/java/com/plagod/entity/monitor/AlertEvent.java`<br>`wifi-common-api/src/main/java/com/plagod/vo/monitor/AlertEventVO.java`<br>`monitor-service/src/main/java/com/plagod/service/impl/AlertEventServiceImpl.java`<br>`monitor-service/src/main/resources/com/plagod/mapper/xml/AlertEventMapper.xml` | FROZEN; NONE->0(UNHANDLED); 0(UNHANDLED)->1(HANDLED) |
| `LOCATION_AUTHORIZATION` | LocationAuthorization | MONITOR | `t_location_authorization.enabled` | INTEGER | 0=NOT_AUTHORIZED/1=AUTHORIZED | `database-migration/src/main/resources/db/migration/V1_6__monitor_schema.sql`<br>`monitor-service/src/main/java/com/plagod/entity/monitor/LocationAuthorization.java` | FROZEN; NONE->0(NOT_AUTHORIZED); 0(NOT_AUTHORIZED)->1(AUTHORIZED); 1(AUTHORIZED)->0(NOT_AUTHORIZED) |
| `GEOFENCE` | Geofence | MONITOR | `t_geofence.enabled` | INTEGER | 0=DISABLED/1=ENABLED | `database-migration/src/main/resources/db/migration/V1_6__monitor_schema.sql`<br>`monitor-service/src/main/java/com/plagod/entity/monitor/Geofence.java` | FROZEN; NONE->1(ENABLED); 1(ENABLED)->0(DISABLED); 0(DISABLED)->1(ENABLED) |
| `GEOFENCE_PRESENCE` | Geofence Presence | MONITOR | `t_geofence_state.inside_state` | INTEGER | 0=OUTSIDE/1=INSIDE | `database-migration/src/main/resources/db/migration/V1_6__monitor_schema.sql`<br>`monitor-service/src/main/java/com/plagod/entity/monitor/GeofenceState.java` | FROZEN; NONE->0(OUTSIDE); 0(OUTSIDE)->1(INSIDE); 1(INSIDE)->0(OUTSIDE) |
| `GEOFENCE_EVENT` | Geofence Event | MONITOR | `t_geofence_event.event_type` | STRING | ENTER/EXIT | `database-migration/src/main/resources/db/migration/V1_6__monitor_schema.sql`<br>`monitor-service/src/main/java/com/plagod/entity/monitor/GeofenceEvent.java` | FROZEN; OUTSIDE->ENTER; INSIDE->EXIT |

无生命周期的查询、日志、消息、遥测和黑名单操作在 fixture 中显式使用
`STATELESS` 且 `stateMachineRefs=[]`，不得挂靠其他 owner 或无关状态机。
`PLATFORM_STAFF_AUTHORITY` 不是已冻结状态机：生产字段仍是无 CHECK/常量的
`varchar(64)`，因此作为 `GAP/2.2` 独立登记。Assignment 当前也只能证明
`PENDING`，完整词汇及转换保持 `GAP/2.4`。

`ConditionalStateTransition` 的
`APPLIED/REPLAYED/RESOURCE_NOT_FOUND/ILLEGAL_STATE/VERSION_CONFLICT` 是共享
状态应用结果，不是任何领域状态机的新状态。
## 4. 58 个核心用例组

下表中的操作名称省略共同的 `<GROUP_ID>.` 前缀。`Q` 表示 `QUERY`，`C`
表示 `COMMAND`。

| 组 | owner / actor / context | operationId 后缀 | 状态与唯一后续层 | P21 输入 |
| --- | --- | --- | --- | --- |
| AUTH-01 | AUTH / ANONYMOUS_USER / NONE | C `REGISTER`,`LOGIN_PASSWORD`,`LOGIN_VERIFICATION_CODE`,`LOGIN_OAUTH` | REUSE / NONE | P21-AUTH |
| AUTH-02 | AUTH / ACCOUNT_USER / PLATFORM,TENANT,PLATFORM_TENANT | C `ROTATE_REFRESH`,`COMPLETE_STEP_UP` | REUSE / NONE | P21-AUTH |
| AUTH-03 | AUTH / ACCOUNT_USER / PLATFORM,TENANT,PLATFORM_TENANT | C `LOGOUT`,`SWITCH_ACCOUNT` | REUSE / NONE | P21-AUTH |
| AUTH-04 | AUTH / ACCOUNT_USER,PLATFORM_STAFF / PLATFORM,TENANT,PLATFORM_TENANT | C `SWITCH_TENANT_CONTEXT`,`SWITCH_PLATFORM_CONTEXT`,`SWITCH_PLATFORM_TENANT_CONTEXT` | REUSE / NONE | P21-AUTH |
| AUTH-05 | AUTH / ACCOUNT_USER / PLATFORM,TENANT,PLATFORM_TENANT | C `ISSUE_OPERATION_TOKEN`,`CONSUME_OPERATION_TOKEN` | REUSE / NONE | P21-AUTH |
| ACCOUNT-01 | USER / ACCOUNT_USER / PLATFORM,TENANT | Q `GET_PROFILE`,`GET_AVATAR`; C `UPDATE_PROFILE`,`UPLOAD_AVATAR` | REUSE / NONE | P21-USER |
| ACCOUNT-02 | USER / ACCOUNT_USER / PLATFORM,TENANT | Q `LIST_SOCIAL_IDENTITIES`; C `UNBIND_SOCIAL_IDENTITY` | REUSE / NONE | P21-USER |
| ACCOUNT-03 | USER / ACCOUNT_USER / PLATFORM,TENANT | Q `GET_DELETION_REQUEST`; C `REQUEST_DELETION` | REUSE / NONE | P21-USER |
| TENANT-01 | TENANT / PLATFORM_STAFF / PLATFORM | C `CREATE` | PARTIAL / 2.4 | P21-TENANT |
| TENANT-02 | TENANT / PLATFORM_STAFF / PLATFORM,PLATFORM_TENANT | Q `GET`; C `UPDATE_PROFILE`,`CHANGE_STATUS` | PARTIAL / 2.4 | P21-TENANT |
| TENANT-03 | TENANT / TENANT_ADMIN / TENANT | Q `LIST_MEMBERS`; C `ADD_MEMBER`,`CHANGE_MEMBER_ROLE`,`REMOVE_MEMBER`,`TRANSFER_OWNER` | GAP / 2.2 | P21-TENANT |
| SAAS-01 | TENANT / PLATFORM_STAFF / PLATFORM | Q `LIST_PLANS`; C `CREATE_PLAN`,`UPDATE_PLAN`,`PUBLISH_PLAN_VERSION`,`CHANGE_PLAN_STATUS` | GAP / 2.4 | P21-TENANT |
| SAAS-02 | TENANT / PLATFORM_STAFF,INTERNAL_SERVICE / PLATFORM,INTERNAL | Q `GET_SUBSCRIPTION`; C `ASSIGN_PLAN`,`APPLY_ASSIGNMENT`,`EXPIRE_SUBSCRIPTION` | PARTIAL / 2.4 | P21-TENANT |
| QUOTA-01 | TENANT / TENANT_ADMIN,INTERNAL_SERVICE / TENANT,INTERNAL | Q `GET`; C `RESERVE`,`CONFIRM` | GAP / 2.4 | P21-TENANT |
| QUOTA-02 | TENANT / INTERNAL_SERVICE,SCHEDULED_SERVICE / INTERNAL,SCHEDULED | C `RELEASE`,`EXPIRE_RESERVATION` | GAP / 2.4 | P21-TENANT |
| ENT-01 | USER / TENANT_MEMBER / TENANT | Q `LIST_PRODUCTS`,`GET_ENTITLEMENT` | REUSE / NONE | P21-USER |
| ENT-02 | USER / TENANT_MEMBER,INTERNAL_SERVICE / TENANT,INTERNAL | C `CREATE_DIRECT_ORDER`,`PAY_ORDER`,`FULFILL_ORDER`,`CANCEL_ORDER`,`CLOSE_ORDER` | REUSE / NONE | P21-USER |
| ENT-03 | USER / TENANT_MEMBER,INTERNAL_SERVICE / TENANT,INTERNAL | Q `LIST_USAGE_LEDGER`; C `ACQUIRE_LEASE`,`RENEW_LEASE`,`CONSUME` | REUSE / NONE | P21-USER |
| ENT-04 | USER / TENANT_MEMBER,PLATFORM_STAFF / TENANT,PLATFORM_TENANT | C `REQUEST_REFUND`,`REVIEW_REFUND`,`EXECUTE_REFUND` | REUSE / NONE | P21-USER |
| ENT-05 | USER / PLATFORM_STAFF,INTERNAL_SERVICE / PLATFORM_TENANT,INTERNAL | C `GRANT_SUPER_ADMIN`,`ADJUST_DURATION`,`GRANT_REWARD` | REUSE / NONE | P21-USER |
| ENT-06 | USER / INTERNAL_SERVICE / INTERNAL | C `FULFILL_MARKETPLACE` | GAP / 2.2 | P21-USER |
| MARKET-01 | MARKETPLACE / PLATFORM_STAFF / PLATFORM | Q `LIST_PRODUCTS`; C `CREATE_PRODUCT`,`UPDATE_PRODUCT`,`PUBLISH_PRODUCT`,`UNPUBLISH_PRODUCT`,`CHANGE_SKU_STATUS` | GAP / 2.4 | P21-MARKET |
| MARKET-02 | MARKETPLACE / TENANT_MEMBER / TENANT | Q `LIST_CATALOG`,`GET_CATALOG_ITEM` | GAP / 2.5 | P21-MARKET |
| MARKET-03 | MARKETPLACE / TENANT_MEMBER / TENANT | C `CREATE_ORDER` | PARTIAL / 2.4 | P21-MARKET |
| MARKET-04 | MARKETPLACE / TENANT_MEMBER,SCHEDULED_SERVICE / TENANT,SCHEDULED | C `PAY_ORDER`,`CANCEL_ORDER`,`EXPIRE_ORDER` | PARTIAL / 2.4 | P21-MARKET |
| MARKET-05 | MARKETPLACE / INTERNAL_SERVICE,SCHEDULED_SERVICE / INTERNAL,SCHEDULED | C `DISPATCH_FULFILLMENT`,`APPLY_FULFILLMENT_RESULT`,`RECOVER_FULFILLMENT` | PARTIAL / 2.4 | P21-MARKET |
| MARKET-06 | MARKETPLACE / TENANT_MEMBER,PLATFORM_STAFF / TENANT,PLATFORM | Q `LIST_MY_ORDERS`,`LIST_PLATFORM_ORDERS` | GAP / 2.5 | P21-MARKET |
| ANN-01 | SUPPORT / TENANT_ADMIN,PLATFORM_STAFF / TENANT,PLATFORM | C `CREATE`,`EDIT` | GAP / 2.4 | P21-SUPPORT |
| ANN-02 | SUPPORT / TENANT_ADMIN,PLATFORM_STAFF / TENANT,PLATFORM | C `SUBMIT_REVIEW` | PARTIAL / 2.4 | P21-SUPPORT |
| ANN-03 | SUPPORT / INTERNAL_SERVICE,PLATFORM_STAFF / INTERNAL,PLATFORM | C `APPLY_AI_REVIEW`,`APPLY_MANUAL_REVIEW` | PARTIAL / 2.4 | P21-SUPPORT |
| ANN-04 | SUPPORT / TENANT_ADMIN,PLATFORM_STAFF,TENANT_MEMBER / TENANT,PLATFORM | Q `LIST_PUBLISHED`; C `PUBLISH`,`WITHDRAW` | GAP / 2.4 | P21-SUPPORT |
| ANN-05 | SUPPORT / TENANT_MEMBER,TENANT_ADMIN / TENANT | Q `LIST_COMMENTS`; C `CREATE_COMMENT`,`REPLY_COMMENT`,`DELETE_OWN_COMMENT`,`HIDE_COMMENT` | GAP / 2.4 | P21-SUPPORT |
| ANN-06 | SUPPORT / TENANT_ADMIN,PLATFORM_STAFF / TENANT,PLATFORM | Q `GET_RESTRICTIONS`; C `SET_RESTRICTION` | GAP / 2.4 | P21-SUPPORT |
| AI-01 | AI / INTERNAL_SERVICE / INTERNAL | C `CREATE_REVIEW_TASK` | PARTIAL / 2.4 | P21-AI |
| AI-02 | AI / SCHEDULED_SERVICE / SCHEDULED | C `CLAIM_TASK`,`EXECUTE_PROVIDER`,`FINALIZE_PROVIDER_RESULT` | PARTIAL / 2.4 | P21-AI |
| AI-03 | AI / PLATFORM_STAFF / PLATFORM | Q `LIST_MANUAL_QUEUE`; C `SUBMIT_MANUAL_REVIEW` | GAP / 2.4 | P21-AI |
| SUPPORT-01 | SUPPORT / TENANT_MEMBER / TENANT | C `SUBMIT` | PARTIAL / 2.4 | P21-SUPPORT |
| SUPPORT-02 | SUPPORT / INTERNAL_SERVICE,SCHEDULED_SERVICE / INTERNAL,SCHEDULED | C `APPLY_REVIEW_RESULT`,`COMPENSATE_DAILY_QUOTA` | PARTIAL / 2.4 | P21-SUPPORT |
| SUPPORT-03 | SUPPORT / TENANT_ADMIN / TENANT | Q `LIST_ADMIN_QUEUE`; C `CLAIM_TICKET` | GAP / 2.4 | P21-SUPPORT |
| SUPPORT-04 | SUPPORT / TENANT_MEMBER,TENANT_ADMIN / TENANT | Q `LIST_MESSAGES`; C `ADD_MESSAGE` | GAP / 2.4 | P21-SUPPORT |
| SUPPORT-05 | SUPPORT / TENANT_MEMBER,TENANT_ADMIN / TENANT | C `MARK_WAITING_USER`,`RESOLVE`,`REOPEN`,`CLOSE` | GAP / 2.4 | P21-SUPPORT |
| SUPPORT-06 | SUPPORT / TENANT_MEMBER,TENANT_ADMIN,PLATFORM_STAFF / TENANT,PLATFORM | Q `LIST_MY_SUPPORT`,`LIST_TENANT_SUPPORT`,`GET_PLATFORM_REVIEW_OVERVIEW` | GAP / 2.5 | P21-SUPPORT |
| DEVICE-01 | DEVICE / TENANT_ADMIN / TENANT | Q `LIST`,`GET`; C `CREATE`,`UPDATE`,`DELETE`,`RESTORE` | PARTIAL / 2.4 | P21-DEVICE |
| DEVICE-02 | DEVICE / DEVICE_EVENT,TENANT_ADMIN / DEVICE,TENANT | Q `GET_STATUS`; C `RECORD_HEARTBEAT` | REUSE / NONE | P21-DEVICE |
| DEVICE-03 | DEVICE / TENANT_ADMIN,DEVICE_EVENT,SCHEDULED_SERVICE / TENANT,DEVICE,SCHEDULED | Q `GET_COMMAND`; C `CREATE_COMMAND`,`MARK_PUBLISHED`,`APPLY_RESULT`,`MARK_TIMED_OUT` | REUSE / NONE | P21-DEVICE |
| DEVICE-04 | DEVICE / TENANT_ADMIN,DEVICE_EVENT / TENANT,DEVICE | Q `GET_WIFI_CONFIG`; C `DISPATCH_WIFI_CONFIG`,`APPLY_WIFI_COMMAND_RESULT`,`CONFIRM_WIFI_CONFIG` | REUSE / NONE | P21-DEVICE |
| DEVICE-05 | DEVICE / TENANT_ADMIN / TENANT | Q `LIST_BLACKLIST`; C `ADD_BLACKLIST`,`REMOVE_BLACKLIST`,`DISCONNECT_MAC`,`BLOCK_TRAFFIC`,`KICK` | REUSE / NONE | P21-DEVICE |
| SESSION-01 | DEVICE / TENANT_MEMBER,INTERNAL_SERVICE,DEVICE_EVENT / TENANT,INTERNAL,DEVICE | C `AUTHORIZE_PORTAL`,`APPLY_ALLOW_RESULT` | PARTIAL / 2.4 | P21-DEVICE |
| SESSION-02 | DEVICE / TENANT_MEMBER,TENANT_ADMIN,INTERNAL_SERVICE / TENANT,INTERNAL | Q `LIST`; C `RENEW_LEASE`,`REVOKE`,`CLOSE` | REUSE / NONE | P21-DEVICE |
| TELEMETRY-01 | DEVICE / DEVICE_EVENT,TENANT_ADMIN / DEVICE,TENANT | Q `QUERY`; C `INGEST_TRAFFIC`,`INGEST_SIGNAL`,`INGEST_DISCONNECT` | REUSE / NONE | P21-DEVICE |
| RULE-01 | MONITOR / TENANT_ADMIN / TENANT | Q `LIST`,`GET`; C `CREATE`,`UPDATE`,`CHANGE_ENABLED`,`DELETE` | REUSE / NONE | P21-MONITOR |
| RULE-02 | MONITOR / INTERNAL_SERVICE / INTERNAL | C `EVALUATE_TRAFFIC`,`APPLY_ACTION_RESULT` | REUSE / NONE | P21-MONITOR |
| ALERT-01 | MONITOR / TENANT_ADMIN,INTERNAL_SERVICE / TENANT,INTERNAL | Q `LIST`,`SUBSCRIBE`; C `CREATE`,`HANDLE` | PARTIAL / 2.4（重复 HANDLE 当前冲突） | P21-MONITOR |
| AUDIT-01 | MONITOR / TENANT_ADMIN,PLATFORM_STAFF,INTERNAL_SERVICE / TENANT,PLATFORM,INTERNAL | Q `LIST`; C `APPEND_OUTCOME` | REUSE / NONE | P21-MONITOR |
| LOCATION-01 | MONITOR / TENANT_MEMBER / TENANT | Q `LIST_HISTORY`; C `AUTHORIZE`,`REVOKE`,`REPORT`,`DELETE_HISTORY` | REUSE / NONE | P21-MONITOR |
| GEOFENCE-01 | MONITOR / TENANT_ADMIN,INTERNAL_SERVICE / TENANT,INTERNAL | Q `LIST`,`LIST_EVENTS`; C `CREATE`,`UPDATE`,`CHANGE_ENABLED`,`DELETE`,`EVALUATE` | REUSE / NONE | P21-MONITOR |
| ANALYTICS-01 | MONITOR / TENANT_ADMIN / TENANT | Q `QUERY_TRAFFIC`,`QUERY_SIGNAL`,`QUERY_RULE_ALERT`,`QUERY_GIS` | REUSE / NONE | P21-MONITOR |
| GOV-01 | USER 或 TENANT（逐 operation 唯一） / PLATFORM_STAFF / PLATFORM | Q `LIST_ACCOUNTS`,`LIST_PLATFORM_STAFF`; C `CHANGE_ACCOUNT_STATUS`,`REVIEW_ACCOUNT_DELETION`,`GRANT_PLATFORM_AUTHORITY`,`DISABLE_PLATFORM_AUTHORITY` | GAP / 2.2 | P21-USER,P21-TENANT |

## 5. 跨域事件骨架

| eventType | source owner | target owner | 稳定引用 | 规则 |
| --- | --- | --- | --- | --- |
| `TENANT_CREATED` | TENANT | USER | `eventId,tenantId,ownerUserId` | 平台 actor 与 owner 分离 |
| `TENANT_ACCESS_CHANGED` | TENANT | DEVICE,AUTH | `eventId,tenantId,contextVersion` | Outbox 后异步收敛 |
| `ACCOUNT_SESSION_REVOKE_REQUESTED` | USER | AUTH | `eventId,userId,reasonCode` | User Outbox，Auth 自然幂等 |
| `ASSIGNMENT_APPLIED` | TENANT | MARKETPLACE | `eventId,assignmentId,sourceReference` | 目标结果只保留一个 reference |
| `QUOTA_RESERVATION_CHANGED` | TENANT | USER,DEVICE | `eventId,reservationId,businessKey,state` | confirm/release 均幂等 |
| `MARKETPLACE_FULFILLMENT_REQUESTED` | MARKETPLACE | USER,TENANT | `eventId,orderNo,sourceReference,targetType` | 目标域按 eventId 幂等 |
| `ENTITLEMENT_FULFILLMENT_COMPLETED` | USER | MARKETPLACE | `eventId,resultReference,resultCode` | 不创建第二 Payment |
| `SAAS_FULFILLMENT_COMPLETED` | TENANT | MARKETPLACE | `eventId,resultReference,resultCode` | 不创建第二 Payment |
| `CONTENT_REVIEW_REQUESTED` | SUPPORT | AI | `reviewRequestId,businessType,businessId,contentVersion` | AI 不拥有业务正文状态 |
| `CONTENT_REVIEW_COMPLETED` | AI | SUPPORT | `reviewRequestId,decision,resultReference` | 旧 contentVersion 按 STALE 忽略 |
| `DEVICE_COMMAND_RESULT` | DEVICE | DEVICE,MONITOR | `requestId,commandType,resultCode,occurredAt` | PUBLISHED 不等于成功 |
| `SESSION_STATE_CHANGED` | DEVICE | USER,TENANT | `eventId,sessionId,state,reservationId` | ALLOW SUCCEEDED 才可 ACTIVE |
| `TRAFFIC_INGESTED` | DEVICE | MONITOR | `eventId,tenantId,nodeId,sessionId` | tenant 来自绑定设备 |
| `RULE_ACTION_REQUESTED` | MONITOR | DEVICE | `eventId,ruleId,commandType,targetReference` | Monitor 事务内不发布 MQTT |
| `AUTHORITY_HANDOFF_COMPLETED` | TENANT | USER | `eventId,userId,receiptId` | receipt 完成后才可停用账号 |

所有跨域业务事务只写领域状态和领域 Outbox。`audit-v1` 的 SUCCESS 在提交后
使用独立 `REQUIRES_NEW` writer；DENIED/FAILED 使用独立 writer。事务内禁止
Feign、Quota、MQTT 或 Provider 网络调用。

## 6. P21 包输入、只读边界与所有权

S0 只冻结下列包定义，不创建任务分支或子智能体。每个包未来只能写自己唯一的
`src/test/**`；生产源码、中央索引和共享 fixture 对所有 P21 包均只读。

| 包 | 精确输入组 | 唯一 owned path | 只读核对 |
| --- | --- | --- | --- |
| P21-AUTH | AUTH-01..05 | `auth-service/src/test/**` | Auth Controller/Service、session/token、Gateway/context |
| P21-USER | ACCOUNT-01..03、ENT-01..06、GOV-01(USER) | `user-service/src/test/**` | User Controller/Service/Mapper/XML、权益 worker/outbox |
| P21-TENANT | TENANT-01..03、SAAS-01..02、QUOTA-01..02、GOV-01(TENANT) | `tenant-service/src/test/**` | Tenant Controller/Service/Mapper/XML、Schema |
| P21-MARKET | MARKET-01..06 | `marketplace-service/src/test/**` | Marketplace Entity/Mapper/worker、目标域契约 |
| P21-SUPPORT | ANN-01..06、SUPPORT-01..06 | `support-service/src/test/**` | Announcement/Support Entity/Mapper/worker |
| P21-AI | AI-01..03 | `ai-service/src/test/**` | AI Entity/Mapper/Provider worker |
| P21-DEVICE | DEVICE-01..05、SESSION-01..02、TELEMETRY-01 | `device-service/src/test/**` | Device Controller/Service/Mapper/XML、MQTT/固件 fixture |
| P21-MONITOR | RULE-01..02、ALERT-01、AUDIT-01、LOCATION-01、GEOFENCE-01、ANALYTICS-01 | `monitor-service/src/test/**` | Monitor Controller/Service/Mapper/XML、Device internal contract |

八个包共同禁止修改：

- `docs/core-use-case-index.md`、`docs/backend-api-index.md` 和
  `wifi-test-kit/**`，这些文件只有 2.1 协调者可写；
- 任意 `src/main/**`、生产 migration、根 `pom.xml`、`wifi-common-api/**`、
  所有共享 Starter、Gateway、Admin BFF、前端和固件；
- 后端主工作区受保护的
  `auth-service/src/main/java/com/plagod/controller/AuthController.java`。

## 7. S0 REUSE、GAP 与 CONFLICT 结论

直接复用：`trusted-context-v1`、`audit-v1`、`ConditionalStateTransition`、
HTTP envelope、稳定 requestId、Outbox claim/lease/finalize、Auth
session/token、User 权益、Device/Portal、AccessRule/Location/Geofence 与 MQTT command result；ALERT-01 重复 handle 仍为 PARTIAL/2.4。

主要缺口：PlatformStaff authority 完整词汇、authority handoff 契约、Tenant owner
分离和成员生命周期、Assignment 完整机器词汇、SaaS/Quota 业务闭环、Marketplace 公开业务、Announcement
业务 Service、AI 人工复核、Support Ticket/Message/查询、Device 与配额/权益
的完整编排。数据库 Schema 载体存在不代表公开业务已经完成。

已解决冲突：

- Marketplace Order 不使用旧 `PENDING_PAYMENT/COMPLETED/ATTENTION_REQUIRED/RETRY_WAIT/MANUAL_REQUIRED` 别名；TARGET_DOMAIN Fulfillment 保留真实合法的 `PROCESSING`，TRACK_ONLY 使用独立交付词汇。
- Announcement 不使用 `READY_TO_PUBLISH/REJECTED`；Comment 不使用
  `DELETED_BY_AUTHOR/HIDDEN_BY_ADMIN`。
- AI 不新增 `PENDING/PROCESSING/RETRY_WAIT/CANCELLED`；Provider 失败不得自动
  APPROVE。
- Support `ACCEPT` 必须原子形成 Ticket 和首消息；`REJECT` 不返还 daily
  limit。

`docs/database-schema-inventory.md` 的历史数量不能作为 S0 机器闭合依据：
当前 User 还包含 session revoke Outbox 和 entitlement lease receipt，AI 还
包含 task input Mapper。S0 直接引用真实迁移、Mapper 和 worker。
