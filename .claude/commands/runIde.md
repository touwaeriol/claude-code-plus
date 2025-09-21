---
allowed-tools: Bash(git:*), Read
argument-hint: <target-branch>
description: 起的 ide 测试插件功能
---

后台执行执行./gradlew jetbrains-plugin:runIde 命令
如果后台已经启动，你也执行，以命令会重启 ide，而不是什么都不做
不要执行其他任何操作，也不要等待命令结束，后续可以查询日志

**复杂多步骤任务** - 需要3个/r或更多步骤时