---
name: serms-task
description: 在已有 SERMS 分工、技术栈和合同范围内实现一项约定任务并完成相应 Test Bench。
---

# 实现任务

来源：[规则与授权](../../../.github/CONTRIBUTING.md#角色和授权)、[任务与依赖](../../../.github/CONTRIBUTING.md#任务和依赖)、[技术栈](../../../docs/tech-stack.md)、[合同索引](../../../docs/contracts/README.md)。

1. 读当前任务或会话约定、相关合同、允许路径和依赖版本，记录规则基线、HEAD/base 与已授权范围。确认自己是唯一 writer。
2. 在独立 worktree 实现已约定的行为。保留单后端、单前端以及已有模块分层，复用平台和公共测试设施。
3. 涉及既有规则冲突时，给出具体方案询问用户；继续不依赖答案的工作。不把旧代码现状当成修改合同的许可。
4. 同步编写该功能完整 Test Bench。后端接口变化重新生成前端类型；关注真实权限、事务、并发和异常流程。
5. 按[验证](../serms-verify/SKILL.md)记录实际结果，按[PR](../serms-pr/SKILL.md)整理交付。只有用户授权委派时才按[agent 工作流](../../../docs/collaboration/agent-workflow.md)安排子任务。

交付：实际变更、分支/HEAD/base、测试证据、剩余验收与待决定问题。不要把局部实现或本地通过标成团队 Done。
