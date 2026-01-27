# VS Code 迁移 - MCP 命名统一 & Proto 重生成计划

## 📋 概述

本计划整合以下变更：
1. **MCP 服务器命名统一** - 从平台特定命名改为通用 `ide-*` 前缀
2. **Proto 定义重生成** - 更新所有语言绑定代码
3. **相关代码同步更新** - 系统提示词、前端、后端

---

## 🎯 目标

| 目标 | 说明 |
|------|------|
| 跨平台统一 | Claude 系统提示词无需区分 IDE 平台 |
| 前端简化 | 不需要根据平台判断调用哪个 MCP |
| 维护性提升 | 单一命名规范，减少混淆 |

---

## 📝 Phase 1: MCP 服务器命名统一

### 1.1 命名变更对照表

| 类别 | 旧命名 (JetBrains) | 旧命名 (VS Code) | **新命名** |
|------|-------------------|------------------|-----------|
| 文件操作 | `jetbrains-file` | `vscode-file` | `ide-file` |
| 终端 | `jetbrains-terminal` | `vscode-terminal` | `ide-terminal` |
| LSP/索引 | `jetbrains-lsp` | `vscode-lsp` | `ide-lsp` |
| Git | `jetbrains_git` | `vscode_git` | `ide-git` |
| 用户交互 | `user_interaction` | `user_interaction` | `user-interaction` (统一连字符) |

### 1.2 影响的工具名称

```
# 旧格式
mcp__jetbrains-file__ReadFile
mcp__jetbrains-file__WriteFile
mcp__jetbrains-file__EditFile
mcp__jetbrains-terminal__Terminal
mcp__jetbrains-terminal__TerminalRead
mcp__jetbrains-terminal__TerminalList
mcp__jetbrains-terminal__TerminalKill
mcp__jetbrains-terminal__TerminalTypes
mcp__jetbrains-terminal__TerminalRename
mcp__jetbrains-terminal__TerminalInterrupt
mcp__jetbrains-lsp__DirectoryTree
mcp__jetbrains-lsp__FileIndex
mcp__jetbrains-lsp__CodeSearch
mcp__jetbrains-lsp__FileProblems
mcp__jetbrains-lsp__FindUsages
mcp__jetbrains-lsp__Rename
mcp__jetbrains_git__GetVcsStatus
mcp__jetbrains_git__GetVcsChanges
mcp__jetbrains_git__GetCommitMessage
mcp__jetbrains_git__SetCommitMessage
mcp__jetbrains_git__SelectFiles
mcp__jetbrains_git__DeselectFiles
mcp__jetbrains_git__SelectAllFiles
mcp__jetbrains_git__DeselectAllFiles
mcp__jetbrains_git__CommitChanges
mcp__user_interaction__AskUserQuestion

# 新格式
mcp__ide-file__ReadFile
mcp__ide-file__WriteFile
mcp__ide-file__EditFile
mcp__ide-terminal__Terminal
mcp__ide-terminal__TerminalRead
mcp__ide-terminal__TerminalList
mcp__ide-terminal__TerminalKill
mcp__ide-terminal__TerminalTypes
mcp__ide-terminal__TerminalRename
mcp__ide-terminal__TerminalInterrupt
mcp__ide-lsp__DirectoryTree
mcp__ide-lsp__FileIndex
mcp__ide-lsp__CodeSearch
mcp__ide-lsp__FileProblems
mcp__ide-lsp__FindUsages
mcp__ide-lsp__Rename
mcp__ide-git__GetVcsStatus
mcp__ide-git__GetVcsChanges
mcp__ide-git__GetCommitMessage
mcp__ide-git__SetCommitMessage
mcp__ide-git__SelectFiles
mcp__ide-git__DeselectFiles
mcp__ide-git__SelectAllFiles
mcp__ide-git__DeselectAllFiles
mcp__ide-git__CommitChanges
mcp__user-interaction__AskUserQuestion
```

### 1.3 需修改的文件

#### JetBrains 插件 (Kotlin)

| 文件 | 修改内容 |
|------|---------|
| `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/FileMcpServerImpl.kt` | `serverName = "ide-file"` |
| `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/TerminalMcpServerImpl.kt` | `serverName = "ide-terminal"` |
| `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/LspMcpServerImpl.kt` | `serverName = "ide-lsp"` |
| `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/GitMcpServerImpl.kt` | `serverName = "ide-git"` |
| `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/UserInteractionMcpServerImpl.kt` | `serverName = "user-interaction"` |

#### VS Code 扩展 (TypeScript)

| 文件 | 修改内容 |
|------|---------|
| `vscode-extension/src/mcp/fileMcpServer.ts` | `serverName = "ide-file"` |
| `vscode-extension/src/mcp/terminalMcpServer.ts` | `serverName = "ide-terminal"` |
| `vscode-extension/src/mcp/lspMcpServer.ts` | `serverName = "ide-lsp"` |
| `vscode-extension/src/mcp/gitMcpServer.ts` | `serverName = "ide-git"` |
| `vscode-extension/src/mcp/userInteractionMcpServer.ts` | `serverName = "user-interaction"` |

#### 系统提示词/配置

| 文件 | 修改内容 |
|------|---------|
| Claude Agent SDK 系统提示词模板 | 更新工具引用 |
| MCP 配置文件 | 更新服务器名称 |

---

## 📝 Phase 2: Proto 定义重生成

### 2.1 Proto 文件位置

```
ai-agent-proto/src/main/proto/
├── ai_agent_rpc.proto      # 入口文件
├── common.proto            # Provider / SessionStatus / PermissionMode
├── content.proto           # ContentBlock / TextBlock / ToolUseBlock
├── stream.proto            # StreamEvent / Delta / Usage
├── message.proto           # RpcMessage / UserMessage / AssistantMessage
├── session.proto           # ConnectOptions / ConnectResult
├── history.proto           # History / HistorySession
├── mcp.proto               # McpServerStatus / McpToolInfo
├── background.proto        # BashBackgroundResult / TerminalTaskUpdate
├── permission.proto        # PermissionUpdate / RequestPermission
└── ide.proto               # IdeSettings / ThemeChangedNotify
```

### 2.2 生成命令

```bash
# 1. Kotlin 绑定 (Gradle 自动处理)
./gradlew :ai-agent-proto:generateProto

# 2. TypeScript 绑定 (buf generate)
cd ai-agent-proto && npx buf generate

# 或从 frontend 目录
cd frontend && npx buf generate
```

### 2.3 生成产物位置

| 语言 | 输出位置 |
|------|---------|
| Kotlin | `ai-agent-proto/build/generated/source/proto/` |
| TypeScript | `frontend/src/proto/*_pb.ts` |

### 2.4 验证步骤

```bash
# 编译检查
./gradlew :ai-agent-proto:build
./gradlew :ai-agent-server:build

# 前端类型检查
cd frontend && npm run type-check
```

---

## 📝 Phase 3: 相关代码同步

### 3.1 前端工具拦截器

文件: `frontend/src/services/toolShowInterceptor.ts`

检查是否有基于旧工具名的判断逻辑，需更新为新命名。

### 3.2 系统提示词引用

搜索并更新所有包含 `jetbrains-` 或 `vscode-` 的系统提示词模板。

### 3.3 文档更新

| 文档 | 更新内容 |
|------|---------|
| `CLAUDE.md` | 更新 MCP 服务器命名说明 |
| `docs/plans/vscode-api-compat.md` | 更新 MCP 工具列表 |
| `docs/plans/vscode-module-comparison.md` | 更新模块命名 |

---

## ✅ 执行清单

### Phase 1: MCP 命名统一

- [ ] 1.1 修改 JetBrains MCP 服务器 serverName
  - [ ] FileMcpServerImpl.kt
  - [ ] TerminalMcpServerImpl.kt
  - [ ] LspMcpServerImpl.kt
  - [ ] GitMcpServerImpl.kt
  - [ ] UserInteractionMcpServerImpl.kt
- [ ] 1.2 修改 VS Code MCP 服务器 serverName
  - [ ] fileMcpServer.ts
  - [ ] terminalMcpServer.ts
  - [ ] lspMcpServer.ts
  - [ ] gitMcpServer.ts
  - [ ] userInteractionMcpServer.ts
- [ ] 1.3 更新系统提示词模板

### Phase 2: Proto 重生成

- [ ] 2.1 执行 Kotlin 绑定生成
- [ ] 2.2 执行 TypeScript 绑定生成
- [ ] 2.3 验证编译通过

### Phase 3: 同步更新

- [ ] 3.1 更新前端工具拦截器（如需要）
- [ ] 3.2 更新文档

### Phase 4: 验证

- [ ] 4.1 JetBrains 插件功能测试
- [ ] 4.2 VS Code 扩展功能测试（如已实现）
- [ ] 4.3 前端 MCP 工具调用测试

---

## ⚠️ 注意事项

1. **向后兼容**: 此变更不向后兼容，需同时更新所有组件
2. **系统提示词缓存**: Claude CLI 可能缓存系统提示词，需确保更新生效
3. **MCP 配置**: 用户自定义 MCP 配置需手动更新（如有）

---

## 📅 预估工作量

| Phase | 预估 |
|-------|------|
| Phase 1: MCP 命名 | 1-2 小时 |
| Phase 2: Proto 重生成 | 30 分钟 |
| Phase 3: 同步更新 | 1-2 小时 |
| Phase 4: 验证 | 1-2 小时 |
| **总计** | **4-6 小时** |

---

## 📎 相关文档

- [VS Code 迁移总体计划](vscode-migration-plan.md)
- [API 契约文档](vscode-api-compat.md)
- [模块对比](vscode-module-comparison.md)
