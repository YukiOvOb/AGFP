---
name: serms-verify
description: 验证 SERMS 改动并把本地、CI 和验收证据绑定到真实 HEAD、base 与输入版本。
---

# 验证改动

来源：[testing](../../../docs/testing.md)、[原有测试台要求](../../../docs/tech-stack.md#5-测试台test-bench每人负责自己的)。

1. 记录 OS、工具版本、HEAD、base 及获取时间、未提交输入。核对测试覆盖的是交付内容与当前依赖组合。
2. 运行与改动最相关的行为检查，再完成任务和既有 PR 的验证范围。命令和实际 workflow 见 testing；不要套用 Gateflow 的 Go/Python 测试。
3. 测试资源隔离；不复用他人的容器、库、端口或未注明版本的报告。
4. 每项结果用 `passed / failed / not run / blocked`，后面写证据或原因。没有真实 PostgreSQL 18、远端 CI 或部署证据时保持缺项。
5. HEAD/base 变化后重跑受影响检查。规则和模板改动也演练一个实际任务，核对它能保留既有授权、依赖和人类确认边界。

交付：逐项命令、版本、结果、证据位置和剩余验收。本地检查不会代替 CI、组员检查、合同确认或 Done。
