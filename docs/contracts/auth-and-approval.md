# 登录与审批接口合同（UC02）

状态：**已确认**，以仓库中本文件为准。修改需要提 PR，并由受影响模块的负责人确认。

| 相关方 | 负责内容 |
| --- | --- |
| Zhang Hanming | UC02 审批（`approval` 模块、`frontend/src/features/approval/`）、测试基础设施 |
| Shi Wenqi | UC01 预约（`reservation` 模块，提供第 3 节的公开 Service） |
| Zhou Fanhao | 数据库表结构与迁移（`approval_decision`、`reservation` 等） |
| Wang Yuanmeng | 后端平台（登录、ProblemDetail、OpenAPI）与前端脚手架 |

---

## 1. 开发顺序

| 顺序 | PR | 负责人 | 依赖 |
| --- | --- | --- | --- |
| 1 | PostgreSQL 18 + Flyway：表结构迁移并进 `app/`，删除 MySQL / H2；同时完成第 6 节中数据库相关的修改 | Zhou Fanhao | — |
| 2 | 测试基础设施：Testcontainers 基类、测试数据构造工具、ArchUnit 规则 | Zhang Hanming | 1 |
| 3 | 后端平台：登录接口（第 2 节）、Principal 映射、全局 ProblemDetail（第 5 节）、springdoc OpenAPI；删除 Thymeleaf | Wang Yuanmeng | 1 |
| 4 | 前端脚手架：Vite + React + TS、路由、`AuthProvider`、API 客户端、OpenAPI 类型生成 | Wang Yuanmeng | 3 |
| 5 | CI / 部署：backend、frontend 两个 CI 任务；compose 改为 `web` + `app` + `db` | Shi Wenqi | 1；前端任务在 4 之后 |
| 6 | UC01 预约，包含第 3 节的 `ReservationApprovalService` | Shi Wenqi | 1、3 |
| 7 | UC02 审批（接口层、集成测试、前端页面） | Zhang Hanming | 2、3、4、6 |

- UC02 分支从 main 创建，命名为 `feature/zhang-hanming-uc02-approval`；提 PR 前先合入最新 main。
- 不依赖基础 PR 的部分可以先做：审批领域逻辑、责任链（Chain of Responsibility）及其单元测试。
- 第 7 个 PR 在第 6 个合并前，用 mock 的 `ReservationApprovalService` 编写测试。

---

## 2. 登录（Identity）

### 2.1 CSRF 与 Session

- 后端使用 `CookieCsrfTokenRepository`：Cookie 名 `XSRF-TOKEN`（前端可读），请求头名 `X-XSRF-TOKEN`。
- 前端启动时先调用 `GET /api/v1/auth/csrf`（返回 204），拿到 CSRF Cookie。
- **所有** `POST` / `PUT` / `PATCH` / `DELETE` 请求都必须带 `X-XSRF-TOKEN` 请求头，包括 login 和 logout。
- Session Cookie 为 `JSESSIONID`，属性 `HttpOnly`、`Secure`、`SameSite=Lax`；登录成功后更换 Session ID（防止会话固定）。

### 2.2 接口

| 接口 | 请求体 | 成功 | 失败 |
| --- | --- | --- | --- |
| `GET /api/v1/auth/csrf` | — | 204，下发 `XSRF-TOKEN` Cookie | — |
| `POST /api/v1/auth/login` | `{ "email": string, "password": string }` | 200，返回 `CurrentUser` | 400 请求体格式错误；401 账号不存在、密码错误或账号停用（统一提示，不区分原因）；403 CSRF 校验失败 |
| `POST /api/v1/auth/logout` | — | 204，Session 作废 | 403 CSRF 校验失败 |
| `GET /api/v1/auth/me` | — | 200，返回 `CurrentUser` | 401 未登录 |

`CurrentUser`：

```json
{
  "userId": "7f1c…-uuid",
  "email": "someone@u.nus.edu",
  "displayName": "Someone",
  "roles": ["BORROWER", "APPROVER"]
}
```

### 2.3 Principal 与 User UUID 的映射

- 自定义 `SermsUserPrincipal implements UserDetails`，包含 `userId`（UUID）、`email`、`roles`。
- `Authentication.getName()` 返回 **`userId` 的字符串形式**。这与通知模块的收件人标识（`notification_principal` 默认值 `user_id::text`）一致。
- Controller 通过 `@AuthenticationPrincipal SermsUserPrincipal me` 获取当前用户，传给 service 的是 `UUID actorId`；service 层不依赖 Spring Security 类型。

### 2.4 角色

- 五种角色：`BORROWER`、`APPROVER`、`CUSTODIAN`（库管）、`MAINTAINER`（技术员）、`ADMIN`，映射为 Spring Security 权限 `ROLE_<角色>`。
- 审批相关接口要求 `ROLE_APPROVER`。
- **`ADMIN` 不自动拥有审批权**；需要审批时必须另外分配 `APPROVER`。

### 2.5 401 与 403

| 情况 | 状态码 |
| --- | --- |
| 未登录，或 Session 已过期 | 401（API 不重定向到登录页，直接返回 ProblemDetail） |
| 已登录但缺少所需角色 | 403 |
| CSRF 请求头缺失或无效 | 403 |

---

## 3. 预约模块提供的公开 Service

审批模块**不得**访问 `reservation` 模块的 repository，只能调用下面的公开 Service。

```java
package sg.edu.nus.serms.reservation.service;

public interface ReservationApprovalService {
    /** 待审批列表，只读、不加锁；包含已过期的待审批预约。 */
    Page<PendingReservation> findPendingApproval(Pageable pageable);

    /** 单个预约，只读、不加锁；不存在时抛 ReservationNotFoundException。 */
    PendingReservation get(UUID reservationId);

    /** 批准：PENDING_APPROVAL → CONFIRMED。必须在调用方事务中执行。 */
    PendingReservation confirm(UUID reservationId, long expectedVersion);

    /** 拒绝：PENDING_APPROVAL → REJECTED，并释放时段。必须在调用方事务中执行。 */
    PendingReservation reject(UUID reservationId, long expectedVersion);
}
```

`PendingReservation`（record）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `reservationId` | `UUID` | |
| `requesterId` | `UUID` | |
| `requesterName` | `String` | 显示用 |
| `equipment` | `EquipmentSummary` | `equipmentId`（UUID）、`assetTag`、`name`、`category` |
| `startAt` / `endAt` | `Instant` | |
| `purpose` | `String` | 可为空 |
| `status` | `ReservationStatus` | `PENDING_APPROVAL`、`CONFIRMED`、`REJECTED`、`CANCELLED`、`FULFILLED` |
| `version` | `long` | 乐观锁版本号 |
| `createdAt` | `Instant` | |
| `expired` | `boolean` | `now >= startAt` 且状态为 `PENDING_APPROVAL` 时为 `true` |

---

## 4. confirm / reject 的职责划分

### 4.1 预约模块负责（在 `confirm` / `reject` 内部完成）

两个方法都标注 `@Transactional(propagation = Propagation.MANDATORY)`，必须在审批模块的事务里调用，保证预约状态变更与审批记录写入同时成功或同时回滚。

1. **加锁**：`SELECT … FOR UPDATE`，顺序为先设备、后预约，与领用 / 归还一致，避免死锁。
2. **版本检查**：`version != expectedVersion` 时抛 `ReservationVersionConflictException`。
3. **状态检查**：状态不是 `PENDING_APPROVAL` 时抛 `ReservationStateConflictException`（异常中带当前状态和版本）。
4. **仅 `confirm`**：
   - 已过期（`now >= startAt`）时抛 `ReservationExpiredException`。数据库触发器只拦截 “结束时间已过” 的确认，所以 “开始时间已过” 必须由 service 检查。
   - 设备在等待期间转入维修或报废时抛 `ReservationStateConflictException`；数据库触发器也会兜底拦截。
5. **仅 `reject`**：**已过期的待审批预约仍然可以拒绝**，用于释放时段和留下审批记录。
6. **释放时段**：排他约束只对 `PENDING_APPROVAL`、`CONFIRMED`、`FULFILLED` 生效，状态变为 `REJECTED` 后时段自动释放，无需额外代码。

### 4.2 审批模块负责

1. 权限检查：`@PreAuthorize("hasRole('APPROVER')")`。
2. **禁止自审**：`requesterId == actorId` 时拒绝，即使该用户同时是 `ADMIN`（数据库触发器 `check_approval` 兜底）。
3. 拒绝必须填写理由（数据库 `CHECK` 约束兜底）。
4. 写入 `approval_decision`：`reservation_id` 唯一，保证**每个预约只能审批一次**；并发下第二次写入会触发唯一约束冲突，转为 409。
5. 发布 `ReservationDecided` 事件，供通知模块通知申请人、审计模块记录操作。

### 4.3 一次审批的事务顺序

```
@Transactional  ApprovalService.decide(actorId, reservationId, decision, comment, expectedVersion)
  1. reservation = reservationApprovalService.get(reservationId)        // 404
  2. 自审检查                                                            // 403
  3. APPROVED → reservationApprovalService.confirm(id, expectedVersion)  // 409
     REJECTED → reservationApprovalService.reject(id, expectedVersion)   // 409
  4. INSERT approval_decision                                            // 唯一约束冲突 → 409
  5. publish ReservationDecided
```

---

## 5. 审批 REST API

### 5.1 接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/approvals/pending?page=0&size=20&sort=startAt,asc` | 分页返回 `PendingReservation` 列表，默认按 `startAt` 升序；包含已过期项（`expired: true`） |
| `GET /api/v1/approvals/{reservationId}` | 路径参数是**预约 ID**。返回 `PendingReservation`；已审批过的附带 `decision`（`ApprovalDecision`） |
| `POST /api/v1/approvals/{reservationId}/decision` | 提交审批结果，成功返回 200 |

请求体：

```json
{ "decision": "APPROVED", "comment": "可选", "expectedVersion": 3 }
```

| 字段 | 规则 |
| --- | --- |
| `decision` | 必填，`APPROVED` 或 `REJECTED` |
| `comment` | `REJECTED` 时必填且不能为空白；`APPROVED` 时可选；最长 1000 字符 |
| `expectedVersion` | 必填，整数（int64） |

成功响应：

```json
{
  "reservation": { "…": "更新后的 PendingReservation" },
  "decision": {
    "approvalId": "uuid",
    "reservationId": "uuid",
    "approverId": "uuid",
    "decision": "REJECTED",
    "comment": "该时段设备需要校准",
    "decidedAt": "2026-10-01T02:30:00Z"
  }
}
```

### 5.2 错误：统一返回 ProblemDetail（RFC 9457）

| 状态码 | 场景 | `type` |
| --- | --- | --- |
| 400 | 请求体校验失败，包括拒绝时未填理由；附带 `errors: [{ "field", "message" }]` | `/problems/validation` |
| 401 | 未登录 | `/problems/unauthenticated` |
| 403 | 没有 `APPROVER` 角色 | `/problems/forbidden` |
| 403 | 审批自己的预约 | `/problems/self-approval` |
| 403 | CSRF 校验失败 | `/problems/csrf` |
| 404 | 预约不存在 | `/problems/not-found` |
| 409 | 状态不是 `PENDING_APPROVAL`（包括已被审批过）、设备不可用 | `/problems/state-conflict` |
| 409 | 版本不一致 | `/problems/version-conflict` |
| 409 | 批准已过期的预约 | `/problems/reservation-expired` |

- 所有 409 响应额外带 `currentStatus` 和 `currentVersion`，前端据此提示 “已被他人处理，请刷新”。
- **权限检查先于查找**：没有 `APPROVER` 角色的用户访问任何 ID 都先得到 403，不能通过 404 探测预约是否存在。
- ProblemDetail 中不包含堆栈、SQL 或内部类名。

---

## 6. 统一的数据约定

| 项目 | 约定 | 需要的改动 |
| --- | --- | --- |
| 审批结果枚举 | API、Java、数据库统一为 **`APPROVED` / `REJECTED`**，不再使用 `APPROVE` / `REJECT` | 周凡浩 V002 目前是 `CHECK (decision IN ('APPROVE','REJECT'))`，以及 `decision <> 'REJECT'` 的理由约束，需改为 `APPROVED` / `REJECTED`。V002 尚未合并进 main，直接在其分支修改即可，不需要新增迁移 |
| 版本号 | Java 统一 `long`，数据库统一 **`BIGINT`**，JSON 为整数 | V002 中 `equipment`、`reservation`、`loan`、`maintenance_case` 的 `version` 目前是 `integer`，需改为 `bigint` |
| 时间 | 数据库 `timestamptz`，Java `Instant`，JSON 为 ISO-8601 UTC 字符串；前端按 `Asia/Singapore` 显示 | — |
| ID | UUID，JSON 为字符串 | — |

---

## 7. 前端

- 模块目录：`frontend/src/features/approval/`。
- 路由：`/approvals`（待审批列表）、`/approvals/:reservationId`（详情与审批）。
- **由前端脚手架 PR（第 1 节第 4 个）提供**，审批模块直接使用，不自行实现：
  - OpenAPI 类型生成：`pnpm gen:api` 从后端 `/api/v1/openapi.json` 生成 `frontend/src/api/schema.d.ts`，CI 检查生成结果是否最新。
  - 全局登录状态：`AuthProvider`、`useAuth()`（当前用户和角色）、`<RequireRole role="APPROVER">` 路由守卫。
  - API 请求封装：自动带 `X-XSRF-TOKEN` 请求头；遇到 401 跳转登录页；统一解析 ProblemDetail。
- 页面行为：已过期的待审批项显示 “已过期” 标记，隐藏 “批准” 按钮，保留 “拒绝”；选择拒绝时理由输入框必填。

---

## 8. 业务规则汇总

1. UC01 创建预约时，按设备 `requires_approval` 决定初始状态：无需审批 → `CONFIRMED`；需要审批 → `PENDING_APPROVAL`。
2. 只有 `APPROVER` 能审批；禁止审批自己的预约（`ADMIN` 也不例外）。
3. 每个预约只能审批一次，审批记录写入后不可修改（数据库触发器 `approval_immutable` 保证）。
4. 批准：`PENDING_APPROVAL` → `CONFIRMED`。
5. 拒绝：`PENDING_APPROVAL` → `REJECTED`，释放时段；必须填写理由。
6. 已过期（`now >= startAt`）的待审批预约：列表中继续显示并标记过期；**不能批准**（409）；**可以拒绝**，用于释放时段并记录结果。
7. 记录审批人、结果、意见、时间（Proposal 5.4）。
8. 后续任务（不在本次范围）：定时任务把过期的待审批预约自动转为 `CANCELLED`。

---

## 9. 待确认事项

| 事项 | 现状 | 由谁确认 |
| --- | --- | --- |
| **设备状态是四种还是五种** | Proposal 5.2 列出五种：Available、Reserved、Issued、Under Maintenance、Retired，技术栈文档中的 “五种状态” 即来源于此。周凡浩 V002 依据他本地的 `SERMS_Equipment_and_Loan_States.md`（未提交到仓库）实现了四种：`AVAILABLE`、`ON_LOAN`、`UNDER_MAINTENANCE`、`RETIRED`。差异在于 Proposal 的 **Reserved**：V002 没有这个状态，“是否被预约” 改为按时间段由 `reservation` 表判断。两者哪个是团队最终模型，需要对照原始状态图确认，**不在本合同中自行决定** | Zhou Fanhao（对照原始状态文档），全组确认后同步修改 Proposal 对应说明或 V002 |
| 维修工单状态是五种还是六种 | Proposal 5.6 列出六种：Reported、Assigned、In Progress、Resolved、Not Repairable、Closed。V002 为五种：`OPEN`、`ASSIGNED`、`IN_PROGRESS`、`RESOLVED`、`UNREPAIRABLE`，没有 Closed | Liu Tongyao（UC04）与 Zhou Fanhao |

以上两项确认之前，`docs/tech-stack.md` 中相应描述标注为 “待确认”。审批模块不依赖设备状态的具体取值，只通过 `ReservationApprovalService` 的异常得知 “设备不可用”，因此不阻塞 UC02 开发。
