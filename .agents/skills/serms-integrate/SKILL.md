---
name: serms-integrate
description: 核对 SERMS PR 的合并条件与依赖更新，并分别记录合并、部署和团队 Done 的证据。
---

# 合并核对与收尾

来源：[README 合并制度](../../../README.md#协作与发布)、[PR 和团队验收](../../../.github/CONTRIBUTING.md#pr-和团队验收)、[Sprint DoD](../../../docs/agile/sprint-plan.md#2-完成标准definition-of-done)、[testing](../../../docs/testing.md)。

1. 读取 PR 的最新 HEAD、目标分支及 CI 结果，核对任务范围、依赖、合同变更确认与实际权限。保留 Write 协作者自行合并的制度，不指定唯一 integrator。
2. 说明尚缺的检查和验收。任务基于依赖分支时核对合并顺序、retarget 和更新后的组合；旧 HEAD/base 的通过不覆盖新组合。
3. 合并操作按现有用户授权和 GitHub 实际保护执行；本 skill 本身不授权 agent 合并，不能借规则提案批准自身或绕过失败检查。
4. 操作后读取实际 PR 状态和 merge SHA；记录部署与部署后检查，交回依赖任务重新验证。
5. 尚缺组员检查、真实整合或其他 DoD 证据时保留 Testing/未完成验收，不自动关闭任务。全部验收完成后才更新 Done；清理按 worktree 指南保留成果。

交付：实际合并/部署状态、merge SHA（若有）、任务状态、后续负责人和保留的分支/worktree。
