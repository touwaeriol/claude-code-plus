# Claude Code Plus - Complete Feature List

This document provides a comprehensive list of all features in Claude Code Plus, organized by frontend interface and IDEA configuration. This serves as a reference for evaluating OpenAI Codex compatibility.

---

## Table of Contents

1. [Frontend Interface Features](#1-frontend-interface-features)
2. [IDEA Configuration Features](#2-idea-configuration-features)
3. [Backend SDK Features](#3-backend-sdk-features)
4. [Feature Summary](#4-feature-summary)

---

## 1. Frontend Interface Features

### 1.1 Core Chat Functionality

#### Chat Interface (ModernChatView.vue)
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-CHAT-001 | Message Sending | User inputs and sends messages to Claude | `ChatInput.vue`, `ModernChatView.vue` |
| F-CHAT-002 | Streaming Response | Real-time display of AI-generated responses (text, thinking, tool calls) | `MessageList.vue` |
| F-CHAT-003 | Message History | Display all messages in current session with scroll support | `MessageList.vue` |
| F-CHAT-004 | Message Status Indicator | Shows message state (generating, completed, failed) | `MessageList.vue` |
| F-CHAT-005 | Token Usage Statistics | Real-time display of input/output tokens, cache tokens | `ChatInput.vue` |
| F-CHAT-006 | Thinking Content Display | Show Claude's extended thinking process (collapsible) | `MessageList.vue` |
| F-CHAT-007 | Error Dialogs | Display chat error messages | `ModernChatView.vue` |

#### Message Input Area (ChatInput.vue)
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-INPUT-001 | Rich Text Input | Multi-line input with auto-resize | `ChatInput.vue`, `RichTextInput.vue` |
| F-INPUT-002 | Send Message | Click send button or press Enter | `ChatInput.vue` |
| F-INPUT-003 | Force Send | Send message when pending permission requests exist | `ChatInput.vue` |
| F-INPUT-004 | Stop Generation | Click stop button to abort AI generation | `ChatInput.vue` |
| F-INPUT-005 | Context Drag & Drop | Drag files to input area to add context | `ChatInput.vue` |
| F-INPUT-006 | Pending Task Display | Show pending tasks in queue | `PendingMessageQueue.vue` |

### 1.2 Context Management

#### File Context (@Mention)
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-CTX-001 | @Mention Files | Type @ to trigger file search with fuzzy matching | `RichTextInput.vue`, `FileSearchPopup.vue` |
| F-CTX-002 | File Search | Search files by path and name | `FileSearchPopup.vue` |
| F-CTX-003 | Context Tags | Display added file tags, click to remove | `ChatInput.vue` |
| F-CTX-004 | Line Range Selection | Select specific line ranges as context | `FileSearchPopup.vue` |
| F-CTX-005 | Active File Auto-Add | IDE pushes current open file as context | `ChatInput.vue` |
| F-CTX-006 | Image Context | Support adding images as context (base64) | `ContextImagePreview.vue` |
| F-CTX-007 | Auto-Clear Context | Configurable auto-clear after each message | `settingsStore.ts` |

### 1.3 Session Management

#### Multi-Session Support
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-SESS-001 | Create New Session | Create new chat tab/session | `ChatHeader.vue`, `sessionStore.ts` |
| F-SESS-002 | Session Tabs | Display multiple session tabs with switching | `ChatHeader.vue` |
| F-SESS-003 | Rename Session | Rename session title | `sessionStore.ts` |
| F-SESS-004 | Switch Session | Quick switch between sessions | `ModernChatView.vue` |
| F-SESS-005 | Close Session | Close single session tab | `sessionStore.ts` |
| F-SESS-006 | Session History Overlay | Select and load from history list | `SessionListOverlay.vue` |
| F-SESS-007 | Search History | Search past sessions by name | `SessionListOverlay.vue` |
| F-SESS-008 | Delete History | Delete unwanted session records | `aiAgentService.ts` |
| F-SESS-009 | Session Preview | Hover to show session summary | `SessionListOverlay.vue` |

### 1.4 Tool Call Display

#### Tool Display Components
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-TOOL-001 | Read Tool Display | Show file read operations | `ToolUseDisplay.vue` |
| F-TOOL-002 | Write Tool Display | Show file write operations, click to show Diff | `ToolUseDisplay.vue` |
| F-TOOL-003 | Edit Tool Display | Show file edit operations, click to show Diff | `ToolUseDisplay.vue` |
| F-TOOL-004 | MultiEdit Tool Display | Show single-file multi-edit operations | `ToolUseDisplay.vue` |
| F-TOOL-005 | Bash Tool Display | Show terminal command execution | `ToolUseDisplay.vue` |
| F-TOOL-006 | MCP Tool Display | Show MCP service call results | `ToolUseDisplay.vue` |
| F-TOOL-007 | Task Tool Display | Show sub-agent task execution | `ToolUseDisplay.vue` |
| F-TOOL-008 | Tool Collapse Mode | Show key parameters, hide details | `ToolUseDisplay.vue` |
| F-TOOL-009 | Tool Expand Mode | Click to show full details | `ToolUseDisplay.vue` |
| F-TOOL-010 | Tool Status Indicator | Colored dot for status (success/fail/progress) | `ToolUseDisplay.vue` |

#### IDEA Integration
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-IDEA-001 | Click to Open File | Click file path in tool to open in IDEA | `ideaBridge.ts` |
| F-IDEA-002 | Click to Show Diff | Click Write/Edit card to show Diff in IDEA | `ideaBridge.ts` |
| F-IDEA-003 | Navigate to Line | Jump to specific line in file | `ideaBridge.ts` |

### 1.5 Permission Management

#### Permission Dialogs
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-PERM-001 | Permission Request | Request confirmation before file edit/delete | `ToolPermissionInteractive.vue` |
| F-PERM-002 | Parameter Preview | Show key parameters (file path, command) | `ToolPermissionInteractive.vue` |
| F-PERM-003 | Allow Operation | Click "Allow" to approve operation | `ToolPermissionInteractive.vue` |
| F-PERM-004 | Deny Operation | Click "Deny" with reason to reject | `ToolPermissionInteractive.vue` |
| F-PERM-005 | Permission Mode Selection | Choose permission mode during confirmation | `ToolPermissionInteractive.vue` |
| F-PERM-006 | Plan Mode Confirmation | Show plan after Plan mode, confirm execution | `ToolPermissionInteractive.vue` |

#### Permission Modes
| Feature ID | Mode Name | Description |
|------------|-----------|-------------|
| F-PERM-M01 | Default Mode | Confirm each tool operation individually |
| F-PERM-M02 | Accept Edits | Auto-accept file edits, confirm others |
| F-PERM-M03 | Plan Mode | Create plan first, execute after confirmation |
| F-PERM-M04 | Bypass Permissions | Auto-execute all operations (dangerous) |

### 1.6 User Interaction

#### Ask User Question
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-ASK-001 | Claude Questions | Claude asks clarification questions | `AskUserQuestionInteractive.vue` |
| F-ASK-002 | Answer Question | User inputs answer to continue | `AskUserQuestionInteractive.vue` |
| F-ASK-003 | Skip Question | User can skip and continue | `AskUserQuestionInteractive.vue` |
| F-ASK-004 | Multiple Choice | Click to select from options | `AskUserQuestionInteractive.vue` |

#### Keyboard Shortcuts
| Feature ID | Shortcut | Action |
|------------|----------|--------|
| F-KEY-001 | Enter | Send message |
| F-KEY-002 | Shift+Enter | Insert new line |
| F-KEY-003 | ESC | Stop generation / Deny permission |

### 1.7 Theme System

| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-THEME-001 | IDE Theme Sync | Auto-sync with IDE dark/light theme | `themeService.ts` |
| F-THEME-002 | System Theme Detection | Detect system preference (Dark/Light) | `themeService.ts` |
| F-THEME-003 | Manual Theme Switch | Manual switch in browser mode | `App.vue` |
| F-THEME-004 | Dark Theme | IntelliJ dark theme compatible | `themeService.ts` |
| F-THEME-005 | Light Theme | IntelliJ light theme compatible | `themeService.ts` |

### 1.8 Internationalization (i18n)

| Feature ID | Feature Name | Supported Languages |
|------------|-------------|---------------------|
| F-I18N-001 | Simplified Chinese | zh-CN |
| F-I18N-002 | English | en-US |
| F-I18N-003 | Japanese | ja-JP |
| F-I18N-004 | Korean | ko-KR |
| F-I18N-005 | Auto Language Detection | Based on IDE/system |
| F-I18N-006 | Manual Language Switch | User-configurable |

### 1.9 Advanced Features

#### Message Edit & Resend
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-ADV-001 | Edit History Message | Click edit button to modify and resend | `MessageList.vue` |
| F-ADV-002 | Restart from Message | Delete subsequent messages, restart from point | `aiAgentService.ts` |

#### Context Compacting
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-ADV-003 | Auto Compacting | Compress early messages when context too long | `CompactingCard.vue` |
| F-ADV-004 | Compacting Progress | Show compacting status and progress | `CompactingCard.vue` |
| F-ADV-005 | Compacted Summary | Display compressed context summary | `MessageList.vue` |

#### Export
| Feature ID | Feature Name | Description | Related Files |
|------------|-------------|-------------|---------------|
| F-ADV-006 | Export as Markdown | Export conversation to Markdown file | `ExportDialog.vue` |
| F-ADV-007 | Export as JSON | Export conversation to JSON format | `ExportDialog.vue` |
| F-ADV-008 | Export as HTML | Export conversation to viewable HTML | `ExportDialog.vue` |

#### Model Selection
| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| F-MODEL-001 | Opus 4.5 | Select most powerful Claude model |
| F-MODEL-002 | Sonnet 4.5 | Balanced performance and cost |
| F-MODEL-003 | Haiku 4.5 | Fast, lightweight model |
| F-MODEL-004 | Custom Model Support | Add custom Claude models |

#### Extended Thinking
| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| F-THINK-001 | Thinking Enable/Disable | Toggle thinking feature in settings |
| F-THINK-002 | Thinking Token Budget | Configure max thinking tokens (default 8096) |
| F-THINK-003 | Real-time Thinking Display | Show Claude's thinking process live |
| F-THINK-004 | Thinking Collapse | Expand/collapse thinking content |

---

## 2. IDEA Configuration Features

### 2.1 Main Settings Page

| Feature ID | Config Item | Type | Description |
|------------|-------------|------|-------------|
| C-MAIN-001 | API Key | Text | Anthropic API key configuration |
| C-MAIN-002 | Default Model | Dropdown | Select default Claude model |
| C-MAIN-003 | Default Permission Mode | Dropdown | Select default tool permission mode |
| C-MAIN-004 | Auto Accept Permissions | Toggle | Auto-accept file edits |
| C-MAIN-005 | Thinking Enable | Toggle | Enable/disable extended thinking |
| C-MAIN-006 | Thinking Token Budget | Number | Max thinking tokens |
| C-MAIN-007 | System Prompt | TextArea | Custom system prompt |
| C-MAIN-008 | Max Turns | Number | Maximum conversation turns |
| C-MAIN-009 | Max Tokens | Number | Max tokens per generation |
| C-MAIN-010 | Temperature | Slider | Generation randomness (0-1) |
| C-MAIN-011 | Verbose Logging | Toggle | Enable detailed debug logs |
| C-MAIN-012 | Skip Permission Confirmation | Toggle | Auto-execute all operations |

### 2.2 MCP Settings Page

| Feature ID | Config Item | Type | Description |
|------------|-------------|------|-------------|
| C-MCP-001 | MCP Server List | List | Configure and manage MCP servers |
| C-MCP-002 | Add MCP Server | Button | Add new MCP server |
| C-MCP-003 | Delete MCP Server | Button | Remove MCP server |
| C-MCP-004 | MCP Command | Text | MCP service startup command |
| C-MCP-005 | MCP Environment Variables | TextArea | MCP service env vars |
| C-MCP-006 | Terminal MCP Config | Dialog | Configure Terminal MCP shell type |

### 2.3 Claude Code Settings Page

| Feature ID | Config Item | Type | Description |
|------------|-------------|------|-------------|
| C-CLI-001 | Node.js Path | FilePath | Node.js executable path |
| C-CLI-002 | Claude CLI Path | FilePath | Claude CLI executable path |
| C-CLI-003 | Auto Detect CLI | Button | Auto-detect Claude CLI location |

### 2.4 Git Generate Settings Page

| Feature ID | Config Item | Type | Description |
|------------|-------------|------|-------------|
| C-GIT-001 | Enable Git Generate | Toggle | Enable/disable commit message generation |
| C-GIT-002 | Custom System Prompt | TextArea | Git generate system prompt |
| C-GIT-003 | Auto Commit After Generate | Toggle | Auto-commit after message generation |

### 2.5 Environment Detection

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| C-ENV-001 | Node.js Auto-Detection | Auto-detect Node.js installation |
| C-ENV-002 | Node.js Version Validation | Check Node.js version requirements |
| C-ENV-003 | Claude CLI Auto-Detection | Auto-detect Claude CLI installation |
| C-ENV-004 | CLI Version Check | Check CLI version compatibility |
| C-ENV-005 | Initialization Prompt | Prompt configuration on first use |

### 2.6 IDE Integration APIs

#### File Operations
| Feature ID | HTTP API | Description |
|------------|----------|-------------|
| C-API-001 | `ide.openFile` | Open specified file in IDE |
| C-API-002 | `ide.showDiff` | Show file edit differences |
| C-API-003 | `ide.getFileContent` | Read file content |
| C-API-004 | `ide.searchFiles` | Search files in project |
| C-API-005 | `ide.getProjectPath` | Get project root directory |

#### IDE Information
| Feature ID | HTTP API | Description |
|------------|----------|-------------|
| C-API-006 | `ide.getTheme` | Get current IDE theme |
| C-API-007 | `ide.getLocale` | Get IDE language setting |
| C-API-008 | `ide.setLocale` | Change IDE language |
| C-API-009 | `ide.openUrl` | Open URL in system browser |

### 2.7 Git Integration

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| C-GIT-004 | Git Status Detection | Check if project is in Git repo |
| C-GIT-005 | Get Current Branch | Get current Git branch name |
| C-GIT-006 | Generate Commit Message | Use Claude to generate commit messages |
| C-GIT-007 | VCS Check Handler | Check file changes before commit |

### 2.8 Code Analysis (Java)

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| C-JAVA-001 | Java Code Analysis | Analyze Java code structure and context |
| C-JAVA-002 | Class/Method Query | Query classes, methods, fields |
| C-JAVA-003 | Inheritance Check | Check class inheritance relationships |
| C-JAVA-004 | Reference Search | Search all symbol references |

### 2.9 Tool Window

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| C-WIN-001 | Open/Close Tool Window | Toggle Claude Code Plus tool window |
| C-WIN-002 | Tool Window Position | Default on IDE right side |
| C-WIN-003 | Tool Window Icon | Display custom Claude icon |

### 2.10 Terminal MCP Support

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| C-TERM-001 | Create Terminal Session | Create new IDE terminal tab for MCP |
| C-TERM-002 | Custom Shell | Configure default shell type |
| C-TERM-003 | Execute Terminal Command | Execute commands in configured shell |
| C-TERM-004 | Auto-Detect git-bash | Auto-detect git-bash on Windows |

### 2.11 Background Services

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| C-SVC-001 | Start HTTP Server | Auto-start backend on project open |
| C-SVC-002 | Random Port Allocation | Auto-select available port |
| C-SVC-003 | Frontend Resource Service | Serve Vue app static resources |
| C-SVC-004 | WebSocket Support | WebSocket for streaming responses |
| C-SVC-005 | Session State Save | Save chat session state at project level |
| C-SVC-006 | Session Auto-Restore | Restore last session when reopening project |

### 2.12 Multi-Version Compatibility

| Version | Build | Terminal API | Diff API |
|---------|-------|-------------|----------|
| 2024.2 | 242 | `createLocalShellWidget` | `DiffRequestProcessorEditor` |
| 2024.3 | 243 | `createLocalShellWidget` | `DiffEditorViewerFileEditor` |
| 2025.1 | 251 | `createNewSession` | `DiffEditorViewerFileEditor` |
| 2025.2 | 252 | `createNewSession` | `DiffEditorViewerFileEditor` |
| 2025.3 | 253 | `createNewSession` | `DiffEditorViewerFileEditor` |

---

## 3. Backend SDK Features

### 3.1 Session Management

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-SESS-001 | Create Session | Create new Claude conversation session |
| B-SESS-002 | Session State | Track connection state and message history |
| B-SESS-003 | Session Interrupt | Abort ongoing conversation generation |
| B-SESS-004 | Parallel Sessions | Support multiple independent parallel sessions |

### 3.2 Message Processing

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-MSG-001 | Streaming Messages | Real-time push generated content |
| B-MSG-002 | Partial Messages | Stream intermediate results for token counting |
| B-MSG-003 | Message Encoding | Protobuf encoding for efficient transfer |
| B-MSG-004 | Message History | Load and manage historical messages |

### 3.3 Supported Tools

| Feature ID | Tool Name | Description | Permission Required |
|------------|-----------|-------------|---------------------|
| B-TOOL-001 | Read | Read file content | No |
| B-TOOL-002 | Write | Create or overwrite file | Yes |
| B-TOOL-003 | Edit | Edit file content | Yes |
| B-TOOL-004 | MultiEdit | Multiple edits in one file | Yes |
| B-TOOL-005 | Bash | Execute terminal command | Yes |
| B-TOOL-006 | Task | Create and execute sub-agent task | Yes |

### 3.4 MCP Tool Support

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-MCP-001 | MCP Server Registration | Register and manage MCP servers |
| B-MCP-002 | MCP Tool List | Dynamically load available tools |
| B-MCP-003 | MCP Tool Call | Call MCP server tools |
| B-MCP-004 | Tool Parameter Display | Show MCP tool parameter list |

### 3.5 Streaming Response Processing

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-STREAM-001 | WebSocket Stream | Real-time push via WebSocket |
| B-STREAM-002 | Content Blocks | Send text, thinking, tool calls in chunks |
| B-STREAM-003 | Stream Completion | Mark stream completion and final status |

### 3.6 Response Types

| Feature ID | Response Type | Description |
|------------|---------------|-------------|
| B-TYPE-001 | TextContent | Plain text response |
| B-TYPE-002 | ThinkingContent | Claude's extended thinking process |
| B-TYPE-003 | ToolUseContent | Claude's tool call request |
| B-TYPE-004 | ToolResultContent | Tool execution result |
| B-TYPE-005 | ImageContent | Base64 image support |
| B-TYPE-006 | ErrorContent | Error and exception info |

### 3.7 Token and Cost Management

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-TOKEN-001 | Input Tokens | Count user input tokens |
| B-TOKEN-002 | Output Tokens | Count AI generated tokens |
| B-TOKEN-003 | Cache Creation Tokens | Prompt caching created tokens |
| B-TOKEN-004 | Cache Read Tokens | Prompt caching read tokens |
| B-TOKEN-005 | Real-time Display | Show token usage during streaming |
| B-TOKEN-006 | Thinking Token Budget | Configure max thinking tokens |

### 3.8 History Management

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-HIST-001 | JSONL Storage | Store history messages in JSONL format |
| B-HIST-002 | Session Level | Each session corresponds to a history file |
| B-HIST-003 | Project Level | History files organized by project path |
| B-HIST-004 | Load History | Load past messages from history file |
| B-HIST-005 | Get Session List | Get all session metadata |
| B-HIST-006 | Delete History | Delete history session file |
| B-HIST-007 | Truncate History | Truncate history from specified message |

### 3.9 Advanced SDK Features

#### Extended Thinking
| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-ADV-001 | Thinking Enable | Enable Claude's extended thinking |
| B-ADV-002 | Thinking Token Budget | Max thinking tokens (default 8096) |
| B-ADV-003 | Thinking Display | Show thinking process in UI |

#### Prompt Caching
| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-ADV-004 | Cache Creation | Create reusable prompt cache |
| B-ADV-005 | Cache Read | Use cache to reduce token consumption |
| B-ADV-006 | Cache Cost Calculation | Reflect cache savings in token stats |

#### Sub-agents (Task)
| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-ADV-007 | Task Tool | Create sub-agent tasks |
| B-ADV-008 | Sub-agent Session | Maintain independent session per sub-agent |
| B-ADV-009 | Sub-agent History | Load sub-agent history messages |
| B-ADV-010 | Sub-agent Results | Display sub-agent execution results |

### 3.10 Session Configuration

| Feature ID | Config Item | Default | Description |
|------------|-------------|---------|-------------|
| B-CFG-001 | Default System Prompt | `claude_code` | Default prompt |
| B-CFG-002 | Custom System Prompt | - | User-provided prompt |
| B-CFG-003 | maxTokens | null | Max tokens per generation |
| B-CFG-004 | temperature | null | Generation randomness (0-1) |
| B-CFG-005 | maxTurns | 10 | Max conversation turns |
| B-CFG-006 | continueConversation | false | Continue historical conversation |

### 3.11 RSocket Communication

| Feature ID | Feature Name | Description |
|------------|-------------|-------------|
| B-RS-001 | Bidirectional Communication | Server can push proactively |
| B-RS-002 | Request-Response | RPC-style request-response |
| B-RS-003 | Streaming Support | Stream large data |
| B-RS-004 | Protobuf Encoding | Efficient binary transfer |

---

## 4. Feature Summary

### Feature Count by Category

| Category | Feature Count | Main Capabilities |
|----------|---------------|-------------------|
| Frontend Interface | 90+ | Chat, sessions, tool display, permissions, theme, i18n |
| IDEA Configuration | 50+ | Settings pages, IDE integration, Git, multi-version compat |
| Backend SDK | 60+ | Claude sessions, tools, tokens, history, RSocket |
| Native Tools | 6 | Read/Write/Edit/MultiEdit/Bash/Task |
| MCP Tools | N (dynamic) | Dynamically loaded MCP tools |

### Key Differentiators

1. **Deep IDE Integration** - IntelliJ platform integration with multi-version support
2. **Advanced Conversation Management** - Message editing, history, multi-session
3. **Flexible Permission System** - Multiple permission modes, suggestions
4. **MCP Protocol Support** - Dynamic tool loading and extension
5. **Extended Thinking** - Built-in thinking support with token budget
6. **Sub-agent System** - Task tool for independent sub-agents
7. **Multi-language UI** - 4 languages out-of-box
8. **Complete Theme System** - Auto-sync with IDE theme

### Tech Stack

- **Frontend**: Vue 3 + TypeScript + Pinia + Element Plus
- **Backend**: Kotlin + Ktor + RSocket + Protobuf
- **IDE Integration**: IntelliJ Platform SDK (2024.2-2025.3)
- **Communication**: WebSocket + RSocket + HTTP
- **AI SDK**: Anthropic Claude Agent SDK (Python)
