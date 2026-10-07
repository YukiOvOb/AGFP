# 设备搜索与预约接口合同（UC01）

负责人：Member 1（搜索和预约设备）。本合同以仓库版本为准，修改需提 PR 并由受影响模块负责人确认（README “协作与发布”）。

- 数据模型：`database/` 中已合并的 V001 + V002（`serms` schema）。本模块**不新增迁移**。
- 实现：后端 `app/src/main/java/sg/edu/nus/serms/{equipment,reservation}`，前端 `frontend/src/features/{equipment,reservation}`。
- 审批相关的预约侧接口见 [登录与审批接口合同](auth-and-approval.md) 第 3–4 节，本模块已按其实现（见第 5 节）。

---

## 1. 通用约定

| 项目 | 约定 |
| --- | --- |
| 前缀 | `/api/v1`，JSON |
| 登录 | 所有接口都要求已登录（Session Cookie）；未登录返回 401 `/problems/unauthenticated` |
| CSRF | 所有 `POST` 需要 `X-XSRF-TOKEN` 请求头，缺失返回 403 `/problems/csrf` |
| 身份 | 预约人**只从登录身份取**（`SermsUserPrincipal.userId`），请求体里没有、也不接受 `requesterId` |
| 时间 | 请求和响应都是 ISO-8601 UTC（`2030-01-10T01:00:00Z`）；前端按 `Asia/Singapore` 显示和输入 |
| 时间段 | 半开区间 `[startAt, endAt)`，与数据库 `tstzrange(start_at, end_at, '[)')` 一致：09:00–11:00 与 11:00–12:00 **不冲突** |
| 分页 | `page`（从 0 开始）、`size`（1–50，默认 20）；响应带 `content`、`page`、`size`、`totalElements`、`totalPages` |
| 错误 | RFC 9457 Problem Details：`type`、`title`、`status`、`detail`；本模块额外带 `violations` 或 `errors` 数组（见第 4 节） |

---

## 2. 设备搜索（任何已登录角色）

### 2.1 `GET /api/v1/equipment` — 搜索

| 参数 | 说明 |
| --- | --- |
| `q` | 名称或资产编号的部分匹配，不区分大小写；`%`、`_` 按普通字符处理；最长 100 |
| `category`、`location` | 精确匹配（取值来自 2.2） |
| `from`、`to` | 时间窗口，必须同时提供，`from < to`，跨度 ≤ 90 天 |
| `availableOnly` | `true` 时只返回该窗口可预约的设备；必须同时给出时间窗口 |
| `page`、`size` | 分页 |

- 不返回 `RETIRED`（报废）设备；结果按名称、资产编号排序。
- 每条结果的 `availableInWindow`：没有时间窗口时为 `null`；有时间窗口时，`true` 表示**此刻**提交该时段的预约会被接受。判断条件与数据库插入触发器完全相同：`serms.equipment_can_reserve(设备, from)`（未报废、未在维修、没有未关闭的故障单、借出中的设备在 `from` 前到期）**且**没有 `PENDING_APPROVAL` / `CONFIRMED` / `FULFILLED` 预约与窗口重叠。
- 参数不合法返回 400 `/problems/validation`，`detail` 直接说明原因（例如 “Choose a time window to show only available equipment”）。

```json
{
  "content": [
    {
      "equipmentId": "…", "assetTag": "CAM-001", "name": "Canon EOS R6",
      "category": "Camera", "location": "COM1 Lab", "status": "AVAILABLE",
      "requiresApproval": false, "availableInWindow": true
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

### 2.2 `GET /api/v1/equipment/filters` — 搜索表单的下拉选项

返回 `{ "categories": [...], "locations": [...] }`（去重、排序、不含报废设备）。

### 2.3 `GET /api/v1/equipment/{id}/availability?from&to` — 单台设备在窗口内的占用情况

返回设备信息、`available`（同 2.1 的判断）、`openMaintenance`（是否有未关闭故障单）和 `busySlots`。

- `busySlots[].reason`：`PENDING_APPROVAL`（待审批也占用时段）、`RESERVED`（已确认）、`ON_LOAN`（已领用）。
- **不返回占用人**，避免泄露其他借用人的信息。
- 报废或不存在的设备返回 404。

---

## 3. 预约（只允许 `BORROWER`）

服务层用 `@PreAuthorize("hasRole('BORROWER') and #requesterId.toString() == authentication.name")` 做真正的权限检查：没有 BORROWER 角色返回 403，任何人都不能替别人操作。

### 3.1 `POST /api/v1/reservations` — 提交预约 → 201

```json
{ "equipmentId": "…", "startAt": "2030-01-10T01:00:00Z", "endAt": "2030-01-10T03:00:00Z", "purpose": "Project shoot" }
```

- `purpose` 可选，最长 500。成功返回 201、`Location: /api/v1/reservations/{id}` 和 `ReservationView`。
- 初始状态由设备决定：`requires_approval = false` → `CONFIRMED`；`true` → `PENDING_APPROVAL`（与 UC02 合同第 8 节一致）。

处理顺序（一个事务内）：

1. `SELECT … FOR UPDATE` 锁设备（报废 / 不存在 → 404；维修中或有未关闭故障单 → 409 `equipment-unavailable`）。
2. 锁预约人（`app_user`），账号被停用 → 403 `not-eligible`。锁顺序 “先设备、后用户” 与数据库触发器一致，避免死锁；锁用户保证同一人并发提交时配额不会被同时突破。
3. 检查预约规则（第 6 节），**一次返回所有违反项** → 422 `reservation-rule`。
4. 时段已被占用 → 409 `reservation-conflict`；设备在开始时间仍借出未还 → 409 `equipment-unavailable`。
5. 插入。即使并发请求绕过了第 4 步，数据库排他约束 `reservation_no_overlap` 也会拒绝（SQLState `23P01`），同样转为 409 `reservation-conflict`；触发器拒绝（`23514`）转为 409 `equipment-unavailable`。
6. 同一事务内发布 `ReservationSubmitted` 事件（第 7 节）。

### 3.2 `GET /api/v1/reservations/mine?status&page&size` — 我的预约

只返回当前用户的预约，按开始时间倒序；`status` 可选（`PENDING_APPROVAL`、`CONFIRMED`、`REJECTED`、`CANCELLED`、`FULFILLED`），非法值 400。

### 3.3 `GET /api/v1/reservations/{id}` — 我的一条预约

别人的预约和不存在的预约**一样返回 404**，不暴露是否存在。

### 3.4 `POST /api/v1/reservations/{id}/cancel` — 取消 → 200

- 只能取消 `PENDING_APPROVAL` 或 `CONFIRMED`，且还没有领用记录（Proposal 规则 4）。已领用 → 409 `not-cancellable`（提示 “归还而不是取消”）；已取消 / 已拒绝 → 409 `not-cancellable`。
- 带版本号的条件更新；并发修改导致失败 → 409 `stale-reservation`。
- 取消后时段立即释放（排他约束只对占用状态生效）；发布 `ReservationCancelled` 事件。

### 3.5 `ReservationView`

| 字段 | 说明 |
| --- | --- |
| `reservationId`、`equipmentId` | UUID |
| `assetTag`、`equipmentName`、`location` | 显示用 |
| `startAt`、`endAt`、`createdAt` | UTC |
| `status` | 见 3.2 |
| `purpose` | 可为 `null` |
| `version` | 乐观锁版本号 |
| `cancellable` | 后端计算，前端只用它决定是否显示 “取消” 按钮 |

---

## 4. 错误一览

| HTTP | `type` | 何时 | 额外字段 |
| --- | --- | --- | --- |
| 400 | `/problems/validation` | 请求体字段缺失 / 格式错误；搜索参数不合法 | `errors: [{field, message}]` |
| 401 | `/problems/unauthenticated` | 未登录 | |
| 403 | `/problems/csrf` | 缺少 CSRF 头 | |
| 403 | `/problems/forbidden` | 没有 BORROWER 角色 | |
| 403 | `/problems/not-eligible` | 账号已停用 | `violations: []` |
| 404 | `/problems/not-found` | 设备报废 / 不存在；别人的或不存在的预约 | |
| 409 | `/problems/reservation-conflict` | 时段与已有预约重叠（含并发） | `violations: []` |
| 409 | `/problems/equipment-unavailable` | 维修中、有故障单、开始时仍借出 | `violations: []` |
| 409 | `/problems/not-cancellable` | 已领用 / 已结束的预约 | `violations: []` |
| 409 | `/problems/stale-reservation` | 版本号已变化 | `violations: []` |
| 422 | `/problems/reservation-rule` | 违反预约规则 | `violations: [{code, message}]`，全部列出 |

`violations[].code` 是稳定值，前端或测试可以依赖：`invalid-interval`、`start-in-past`、`too-short`、`too-long`、`too-far-ahead`、`quota-exceeded`。

---

## 5. 提供给审批模块（UC02）的公开 Service

按 [auth-and-approval.md](auth-and-approval.md) 第 3–4 节实现，类在 `sg.edu.nus.serms.reservation.service`：

| 合同要求 | 实现 |
| --- | --- |
| `ReservationApprovalService`：`findPendingApproval(Pageable)`、`get`、`confirm`、`reject` | `ReservationApprovalService` 接口 + `DefaultReservationApprovalService` |
| `PendingReservation`（含 `requesterName`、`equipment`、`version`、`expired`） | `reservation.domain.PendingReservation` |
| `confirm` / `reject` 为 `Propagation.MANDATORY` | 已标注；不在事务中调用抛 `IllegalTransactionStateException` |
| 先锁设备、后锁预约 | 已实现 |
| 版本不符 → `ReservationVersionConflictException` | 带当前版本号 |
| 不是待审批 → `ReservationStateConflictException` | 带当前状态和版本号 |
| `confirm` 时已过期（`now >= startAt`）→ `ReservationExpiredException`；过期的仍可 `reject` | 已实现 |
| 等待期间设备进入维修 / 报废 / 有故障单 → 不可批准 | `ReservationStateConflictException`，触发器兜底同样转换 |
| 不存在 → `ReservationNotFoundException` | 继承 `RecordNotFoundException`，全局处理为 404 |

UC02（PR #8）中的 `ApprovalService` 依赖 `ReservationApprovalPort`。PR #8 合并后，由预约模块新增 `ReservationApprovalAdapter` 实现该端口并**委托给上面的 Service**，审批模块因此不需要访问预约表。适配器、冲突异常补充 `currentStatus`/`currentVersion` 以及审批通知的接入，在 PR #8 合并后的单独 PR 中完成。

---

## 6. 预约规则（策略模式，可配置）

每条规则是 `ReservationPolicy` 的一个实现，`ReservationRules` 依次执行并收集所有违反项。默认值在 `serms.reservation.*` 中配置（`ReservationLimits`）：

| 规则 | 类 | 默认值 | code |
| --- | --- | --- | --- |
| 结束晚于开始；开始时间不能早于当前 | `StartInFuturePolicy` | — | `invalid-interval` / `start-in-past` |
| 时长下限和上限（Proposal 规则 10） | `DurationPolicy` | 30 分钟 – 7 天 | `too-short` / `too-long` |
| 最多提前多久预约 | `BookingHorizonPolicy` | 90 天 | `too-far-ahead` |
| 同时持有的未结束预约数（Proposal 规则 11） | `OpenReservationQuotaPolicy` | 3 个（`PENDING_APPROVAL` + `CONFIRMED` 且未结束） | `quota-exceeded` |

> 默认值是团队暂定值，Sprint 2 评审时确认；修改只需改配置，不需要改代码。前端 `features/reservation/rules.ts` 为即时提示镜像了时长和开始时间规则，最终以后端为准。

---

## 7. 领域事件

在发起方事务内发布（Spring `ApplicationEventPublisher`），事务回滚则事件监听器里写入的通知 / 审计也一起回滚（tech-stack §2.5）。

| 事件 | 字段 | 订阅方（计划） |
| --- | --- | --- |
| `reservation.service.event.ReservationSubmitted` | `eventId`、`reservationId`、`requesterId`、`equipmentId`、`startAt`、`endAt`、`status` | 通知（`PENDING_APPROVAL` 时提醒审批人）、审计 |
| `reservation.service.event.ReservationCancelled` | `eventId`、`reservationId`、`requesterId`、`equipmentId` | 通知、审计；UC05 可取消该预约未发送的提醒 |

---

## 8. 待确认事项

| 事项 | 现状 | 由谁确认 |
| --- | --- | --- |
| 第 6 节规则的默认数值 | 暂定 30 分钟 / 7 天 / 90 天 / 3 个；Proposal 规则 10、11 只规定 “有上限” | 全组 Sprint 2 评审 |
| 按设备类别或借用人类型使用不同规则 | 目前全局一套；策略已拆分，增加 “按类别取值” 只需新增配置来源 | 全组，需要时再做 |
| 过期的待审批预约自动转 `CANCELLED` | UC02 合同第 8 节列为后续任务 | Member 1 + Member 2 |
| `version` 列 `integer` → `bigint` | UC02 合同第 6 节要求；Java 已统一用 `long` 读取，改列类型不影响本模块 | Member 3（数据库） |
