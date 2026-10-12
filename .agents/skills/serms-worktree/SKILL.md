---
name: serms-worktree
description: 为 SERMS 任务建立或恢复独立写入空间，或准备 writer 交接和 worktree 清理。
---

# 隔离与交接

来源：[分支和 worktree](../../../.github/CONTRIBUTING.md#分支和-worktree)、[交接和清理](../../../.github/CONTRIBUTING.md#交接和清理)、[交接记录](../../../docs/collaboration/agent-workflow.md#交接记录)。

先查看：

```text
git worktree list --porcelain
git status --short --branch
git log -1 --oneline
```

- 建立：确认任务、base 和 writer。优先复用本任务适合的空闲 worktree；独立写入任务另建 worktree，不切换他人的活跃 checkout。由任务协调者处理共享 refs；Codex 管理的 worktree 使用 app 对应工具。
- 恢复：核对原 writer 已交出、分支/HEAD 和未提交文件、最新任务/规则/合同变化，然后继续。
- 交接：停止自己启动的服务与写入活动，按来源中的记录格式交付证据和待决定事项；接手者确认后再写。
- 清理：先检查 untracked/ignored、未推送提交及 stash，保留有价值内容，确认完成/取消范围。用创建它的工具归档；不要仅凭 clean status 删除。

交付：worktree、分支、writer、HEAD/base、交接确认、保留内容及未完成验收。
