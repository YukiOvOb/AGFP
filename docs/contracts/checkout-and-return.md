# UC03 领用归还接口合同

负责人 Zhou Fanhao。实现位于 `app/.../loan` 和 `frontend/src/features/loan`。所有业务接口需要登录；写操作必须带 CSRF Header。CUSTODIAN 执行现场交接；BORROWER 查询自己的记录；没有隐式 ADMIN 继承。

| 方法与路径 | 作用 | 权限 |
| --- | --- | --- |
| GET `/api/v1/loans/checkouts?page=0&size=20` | 未结束的 CONFIRMED 预约，按开始时间排序 | CUSTODIAN |
| POST `/api/v1/loans` | 实际领用，201 和 Location | CUSTODIAN |
| POST `/api/v1/loans/{loanId}/return` | 实际接收归还，200 | CUSTODIAN |
| GET `/api/v1/loans?activeOnly=true&page=0&size=20` | 库管查看全部，借用人查看本人；最新领用在前 | CUSTODIAN / BORROWER |
| GET `/api/v1/loans/{loanId}` | 借用详情；他人记录对普通借用人返回 404 | CUSTODIAN / BORROWER |

分页返回数组，`size` 为 1–100、`page` 为 0–100000。无需总数；不足一页则停止向后翻页。队列包括未来预约，列表展示不代表满足领用条件，服务端会在锁内重新验证。

领用请求：

```json
{"reservationId":"uuid","borrowerId":"uuid","expectedVersion":0,"identityConfirmed":true}
```

`borrowerId` 是现场核对对象，必须与保存的 requesterId 相等；不是操作人。expectedVersion 来自队列记录。identityConfirmed 表示库管已现场核对，系统不能替代物理身份核验。

归还请求：

```json
{"expectedVersion":0,"condition":"DAMAGED","note":"Lens cracked"}
```

condition 只允许 GOOD / DAMAGED；损坏时 note 必填且非空白，最长 2000 字符。expectedVersion 来自 Loan 记录。时间、归还操作人和逾期标志由后端生成，禁止由客户端决定。

LoanView 包含 loanId、reservationId、equipmentId、equipmentName、borrowerId、borrowerName、checkoutBy、returnBy、checkedOutAt、dueAt、returnedAt、status、returnCondition、returnNote、version、overdue。ACTIVE 的 overdue 表示当前时间大于 dueAt；RETURNED 的 overdue 表示 returnedAt 大于 dueAt。dueAt 等于原预约 endAt，时间为 UTC Instant，微秒精度。后端版本为 int64；页面目前使用与其他模块一致的 JSON number。

| 状态码 | 场景 |
| --- | --- |
| 400 | 缺字段、非法版本或条件、未确认身份、缺损坏说明 |
| 401 | 没有登录 |
| 403 | CSRF、角色或数据库账号权限不满足 |
| 404 | 不存在或不能查看的借用 |
| 409 | 状态、时间窗、版本、借用人或设备事实冲突；type 为 `/problems/loan-conflict` |
| 503 | 数据库暂不可用；不回传 SQL 或凭据 |

## 事务与跨模块事件

锁顺序为 Equipment → Reservation → Loan → MaintenanceCase。领用归还和维修单写入、状态更新、成功审计及同步事件在同一 Spring 事务。版本以 `WHERE version=?` 检查，不由客户端或服务自行递增，数据库触发器递增。失败会回滚，不做自动无限重试；连接错误导致提交结果未知时，刷新记录后确认，不盲目重发。

`LoanEvent` 包含 action（CHECKED_OUT / RETURNED）、loanId、reservationId、equipmentId、borrowerId、actorId、dueAt、occurredAt、late、returnCondition、maintenanceCaseId。事件在提交前同步发布，数据库消费者应加入当前事务；Email 或其他外部效果需 after-commit/outbox。UUID Loan 是业务主键，V003 的 bigint reminderId 是通知接口桥接，生产者应使用现有 `LoanNotificationRequestFactory`，不能把 UUID 截断成整数。

本模块不负责 Wang Yuanmeng 的逾期定时扫描和提醒周期决策。归还状态立即保存为 RETURNED；通知发送前的 `PostgresLoanReminderGuard` 会在同一锁顺序下重新检查借用是否仍 ACTIVE。
