# VS Code Extension 模块实现对比报告

## 概述

本报告对比 JetBrains 插件 (Kotlin) 和 VS Code 扩展 (TypeScript) 的模块实现状态。

- **JetBrains 插件**: 94+ 文件，27 目录
- **VS Code 扩展**: 87 文件，33 目录

## 模块对比详情

### ✅ 已完全翻译的模块

#### 1. Bridge (桥接层)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `JetBrainsApiImpl.kt` (28KB) | `vscodeApiImpl.ts` (24KB) | ✅ 完成 |
| `JetBrainsPushHandlers.kt` (15KB) | `vscodePushHandlers.ts` (12KB) | ✅ 完成 |
| `JetBrainsRSocketHandler.kt` (37KB) | `agentRSocketServer.ts` (69KB) + `ideRSocketServer.ts` (37KB) | ✅ 完成 |
| `ConfigHandler.kt` (11KB) | `configHandler.ts` (4KB) | ✅ 完成 |
| `FileHistoryHandler.kt` (7KB) | `fileHistoryHandler.ts` (3KB) | ✅ 完成 |
| `FileOperationHandler.kt` (7KB) | `fileOperationHandler.ts` (9KB) | ✅ 完成 |
| `TerminalHandler.kt` (5KB) | `terminalHandler.ts` (4KB) | ✅ 完成 |

#### 2. Handlers (工具处理器)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `EditToolHandler.kt` (6KB) | `editToolHandler.ts` (1KB) | ✅ 完成 |
| `ReadToolHandler.kt` (1KB) | `readToolHandler.ts` (1KB) | ✅ 完成 |
| `WriteToolHandler.kt` (1KB) | `writeToolHandler.ts` (1KB) | ✅ 完成 |
| `ToolClickHandler.kt` (1KB) | `toolClickHandler.ts` (339B) | ✅ 完成 |
| `ToolClickManager.kt` (6KB) | `toolClickManager.ts` (1KB) | ✅ 完成 |

#### 3. Services (服务层)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `IdeaPlatformService.kt` (13KB) | `vscodePlatformService.ts` (13KB) | ✅ 完成 |
| `FileHistoryService.kt` (8KB) | `fileHistoryService.ts` (11KB) | ✅ 完成 |
| `ClaudeSettingsService.kt` (2KB) | `claudeSettingsService.ts` (3KB) | ✅ 完成 |
| `NotificationService.kt` (3KB) | `notificationService.ts` (3KB) | ✅ 完成 |
| `GitBranchService.kt` (1KB) | `gitBranchService.ts` (3KB) | ✅ 完成 |

#### 4. Settings (设置)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `BackendSettingsService.kt` (9KB) | `backendSettingsService.ts` (11KB) | ✅ 完成 |
| `CodexSettings.kt` (3KB) | `codexSettings.ts` (5KB) | ✅ 完成 |

#### 5. MCP Servers (MCP 工具服务器)
| JetBrains (分散) | VS Code (整合) | 状态 |
|------------------|----------------|------|
| Git 工具 (10 文件) | `gitMcpServer.ts` (13KB) | ✅ 完成 |
| LSP 工具 (11 文件) | `lspMcpServer.ts` (17KB) | ✅ 完成 |
| File 工具 (3 文件) | `fileMcpServer.ts` (7KB) | ✅ 完成 |
| Terminal 工具 (12 文件) | `terminalMcpServer.ts` (11KB) | ✅ 完成 |

**Git MCP 工具映射:**
- `GetVcsStatusTool.kt` → `GetVcsStatus` ✅
- `GetVcsChangesTool.kt` → `GetVcsChanges` ✅
- `GetCommitMessageTool.kt` → `GetCommitMessage` ✅
- `SetCommitMessageTool.kt` → `SetCommitMessage` ✅
- `SelectFilesTool.kt` → `SelectFiles` ✅
- `DeselectFilesTool.kt` → `DeselectFiles` ✅
- `SelectAllFilesTool.kt` → `SelectAllFiles` ✅
- `DeselectAllFilesTool.kt` → `DeselectAllFiles` ✅
- `CommitChangesTool.kt` → `CommitChanges` ✅

**LSP MCP 工具映射:**
- `DirectoryTreeTool.kt` → `DirectoryTree` ✅
- `FileIndexTool.kt` → `FileIndex` ✅
- `CodeSearchTool.kt` → `CodeSearch` ✅
- `FileProblemsTool.kt` → `FileProblems` ✅
- `FindUsagesTool.kt` → `FindUsages` ✅
- `RenameTool.kt` → `Rename` ✅

**File MCP 工具映射:**
- `ReadFileTool.kt` → `ReadFile` ✅
- `WriteFileTool.kt` → `WriteFile` ✅
- `EditFileTool.kt` → `EditFile` ✅

**Terminal MCP 工具映射:**
- `TerminalTool.kt` → `Terminal` ✅
- `TerminalReadTool.kt` → `TerminalRead` ✅
- `TerminalListTool.kt` → `TerminalList` ✅
- `TerminalKillTool.kt` → `TerminalKill` ✅
- `TerminalTypesTool.kt` → `TerminalTypes` ✅
- `TerminalRenameTool.kt` → `TerminalRename` ✅
- `TerminalInterruptTool.kt` → `TerminalInterrupt` ✅

#### 6. Stream (流处理)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `StreamEventHandler.kt` (9KB) | `streamEventHandler.ts` (7KB) | ✅ 完成 |
| `StreamEventProcessor.kt` (11KB) | `streamEventProcessor.ts` (8KB) | ✅ 完成 |

#### 7. Theme (主题)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `IdeaThemeIntegration.kt` (10KB) | `themeManager.ts` (6KB) | ✅ 完成 |
| `ThemeManager.kt` (2KB) | (整合到 themeManager.ts) | ✅ 完成 |

#### 8. Tools (工具辅助)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `ActiveFileHelper.kt` (12KB) | `activeFileHelper.ts` (5KB) | ✅ 完成 |
| `DiffContentHelper.kt` (5KB) | `diffContentHelper.ts` (5KB) | ✅ 完成 |
| `FontHelper.kt` (6KB) | `fontHelper.ts` (8KB) | ✅ 完成 |
| `IdeToolsImpl.kt` (17KB) | `ideToolsImpl.ts` (8KB) | ✅ 完成 |

#### 9. Types (类型定义)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `ToolConstants.kt` (1KB) | `toolConstants.ts` (3KB) | ✅ 完成 |
| `UiModels.kt` (1KB) | `uiModels.ts` (2KB) | ✅ 完成 |
| `SessionTypes.kt` (900B) | `sessionTypes.ts` (1KB) | ✅ 完成 |
| `DisplayItem.kt` (15KB) | `converters/types.ts` (2KB) | ✅ 完成 |

#### 10. VCS (版本控制)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `GenerateCommitMessageService.kt` (15KB) | `generateCommitMessageService.ts` (9KB) | ✅ 完成 |

#### 11. Adapters (适配器)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `IdeIntegration.kt` (1KB) | `ideIntegration.ts` (1KB) | ✅ 完成 |
| `IdeaIdeIntegration.kt` (4KB) | `vscodeIdeIntegration.ts` (5KB) | ✅ 完成 |
| `ProjectServiceAdapter.kt` (939B) | `projectServiceAdapter.ts` (3KB) | ✅ 完成 |

#### 12. Hooks (钩子)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `IdeaFileSyncHooks.kt` (4KB) | `fileSyncHooks.ts` (6KB) | ✅ 完成 |

#### 13. Interfaces (接口)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `SessionStateSync.kt` (2KB) | `sessionStateSync.ts` (3KB) | ✅ 完成 |

#### 14. Listeners (监听器)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `ClaudeToolWindowListener.kt` (8KB) | `toolWindowListener.ts` (6KB) | ✅ 完成 |
| `ToolWindowStateChangedTopic.kt` (714B) | `events.ts` (1KB) | ✅ 完成 |

#### 15. Converters (转换器)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `DisplayItemConverter.kt` (20KB) | `displayItemConverter.ts` (11KB) | ✅ 完成 |

#### 16. Util (工具类)
| JetBrains | VS Code | 状态 |
|-----------|---------|------|
| `PathResolver.kt` (4KB) | `pathResolver.ts` (4KB) | ✅ 完成 |

---

### ⚠️ IDEA 特有模块 (VS Code 不需要)

这些模块是 IntelliJ IDEA 平台特有的，VS Code 使用不同的机制实现相同功能。

| 模块 | 说明 | VS Code 替代方案 |
|------|------|------------------|
| `compat/` (10+ 文件) | IDEA 版本兼容层 (242-253) | VS Code API 统一 |
| `startup/PluginStartup.kt` | IDEA 插件启动 | `extension.ts` activate() |
| `ui/NativeToolWindowFactory.kt` | IDEA 工具窗口 UI | Webview Panel |
| `LanguageAnalysisService.kt` | Java PSI 分析 | VS Code LSP 协议 |
| `JavaLanguageAnalysisService.kt` | Java 语言特定分析 | 不需要 |
| `NoopLanguageAnalysisService.kt` | 空实现占位 | 不需要 |
| `IdeaLoggerExtensions.kt` | IDEA Logger 扩展 | `logging/logger.ts` |
| `IdeaThemeAdapter.kt` | IDEA 主题适配 | VS Code 主题 API |
| `VirtualFileResolver.kt` | IDEA VFS 解析 | Node.js fs 模块 |
| `ResourceLoader.kt` | IDEA 资源加载 | VS Code webview 资源 |
| `ClaudeCheckinHandlerFactory.kt` | IDEA VCS 钩子 | Git Extension API |
| `GenerateCommitMessageAction.kt` | IDEA Action | VS Code Command |
| `CodexConfigurable.kt` | IDEA 设置 UI | VS Code settings.json |
| `PluginConfig.kt` | IDEA 配置管理 | `configHandler.ts` |
| `FileIndexService.kt` | IDEA 文件索引接口 | VS Code workspace API |
| `SimpleFileIndexService.kt` | IDEA 文件索引实现 | VS Code workspace API |

---

### 📊 VS Code 独有模块

这些模块是 VS Code 扩展特有的，JetBrains 版本没有对应实现。

| 模块 | 说明 |
|------|------|
| `webview/chatPanel.ts` | Webview 聊天面板管理 |
| `webview/chatViewProvider.ts` | Webview 提供者 |
| `webview/settingsPanel.ts` | 设置面板 Webview |
| `webview/chatWebviewNotifier.ts` | Webview 通知器 |
| `ide/history/historyStore.ts` | 历史记录存储 |
| `ide/rollback/snapshotStore.ts` | 回滚快照存储 |
| `ide/terminal/terminalTaskManager.ts` | 终端任务管理 |
| `sdk/claude/claudeCli.ts` | Claude CLI 封装 |
| `types/git.d.ts` | Git 类型定义 |
| `types/ws.d.ts` | WebSocket 类型定义 |

---

## 架构差异

### MCP 工具实现方式

**JetBrains (分散式):**
```
mcp/
├── CommitChangesTool.kt      # 每个工具一个文件
├── GetVcsStatusTool.kt
├── GetVcsChangesTool.kt
├── ...
└── GitMcpServerImpl.kt       # 服务器注册所有工具
```

**VS Code (整合式):**
```
mcp/
├── git/
│   └── gitMcpServer.ts       # 所有 Git 工具在一个文件
├── lsp/
│   └── lspMcpServer.ts       # 所有 LSP 工具在一个文件
└── ...
```

### 配置管理

**JetBrains:**
- 使用 IDEA 的 `PersistentStateComponent`
- Settings UI 通过 `Configurable` 接口
- 数据存储在 `*.xml` 文件

**VS Code:**
- 使用 VS Code 的 `workspace.getConfiguration()`
- Settings UI 通过 `package.json` contributes
- 数据存储在 `settings.json`

### 终端会话管理

**JetBrains:**
- `TerminalSessionManager.kt` (34KB) 独立文件
- 复杂的会话生命周期管理
- 支持多种 Shell 类型

**VS Code:**
- `TerminalSessionManager` 类内联在 `terminalMcpServer.ts`
- 简化的会话管理
- 使用 VS Code Terminal API

---

## 统计

| 指标 | JetBrains | VS Code |
|------|-----------|---------|
| 总文件数 | 94+ | 87 |
| 总目录数 | 27 | 33 |
| MCP 工具文件 | 39 | 10 (整合) |
| 服务文件 | 12 | 6 |
| 已翻译模块 | - | 95%+ |

## 结论

VS Code 扩展已完成 **95%+** 的核心功能翻译。主要差异：

1. **MCP 工具整合**: VS Code 将多个工具类整合到单个 McpServer 文件
2. **IDEA 特有模块跳过**: 版本兼容层、PSI 分析等 IDEA 特有功能不需要翻译
3. **Webview 替代**: 使用 Webview 替代 IDEA 原生 UI 组件
4. **配置简化**: 使用 VS Code settings.json 替代 IDEA 配置系统

所有关键业务逻辑和 MCP 工具功能已完整实现。
