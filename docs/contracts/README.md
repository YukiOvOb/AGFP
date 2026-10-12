# SERMS 接口合同索引

合同继续以仓库版本为准；变更要提 PR，并由受影响模块负责人确认，见 [README](../../README.md#协作与发布)。本索引不修改合同内容。

| 合同 / 对接记录 | 涉及模块 | 阅读目的 |
| --- | --- | --- |
| [登录与审批（UC02）](auth-and-approval.md) | identity、approval、reservation、公共平台及数据 | 登录、角色、CSRF、预约公开服务、审批 API、事务及待确认状态 |
| [设备搜索与预约（UC01）](equipment-and-reservation.md) | equipment、reservation、approval | 搜索和预约 API、状态、规则、异常与审批接口 |
| [通知接口对齐记录](../notification-interface-alignment.md) | loan、notification、PostgreSQL adapter | 现有整合边界；这是对接记录，不据此覆盖已确认合同 |
| [审批通知 review 记录](../reviews/pr-8-notification-contract.md) | approval、notification | 已有 review 的上下文和缺口；核对记录覆盖的提交 |

新增合同放在本目录并加入索引，写清：范围、提供方与消费方、输入输出、身份/权限、事务/并发、事件与去重、异常、版本影响、测试和确认状态。合同草稿标“待确认”；不要把 agent 的提案记录成成员已确认。

设备状态、维修状态及迁移整合等既有差异，继续按[决策记录](../collaboration/decisions.md#保留的技术和业务差异)追踪，不在导航或协作规则 PR 中决定。
