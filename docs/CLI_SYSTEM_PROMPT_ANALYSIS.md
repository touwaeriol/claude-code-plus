# Claude CLI System Prompt 参数分析报告

## 📋 概述

本文档分析 Claude CLI 2.1.19 版本中 `--system-prompt` 和 `--append-system-prompt` 相关参数的功能及内置提示词。

---

## 1️⃣ 命令行参数定义

### 参数列表

| 参数 | 描述 | 互斥关系 |
|------|------|----------|
| `--system-prompt <prompt>` | 为会话设置系统提示词（替换默认） | 与 `--system-prompt-file` 互斥 |
| `--system-prompt-file <file>` | 从文件读取系统提示词 | 与 `--system-prompt` 互斥 |
| `--append-system-prompt <prompt>` | 追加到默认系统提示词 | 与 `--append-system-prompt-file` 互斥 |
| `--append-system-prompt-file <file>` | 从文件读取并追加到默认系统提示词 | 与 `--append-system-prompt` 互斥 |

### 参数定义代码 (行 6135)

```javascript
.addOption(new V3("--system-prompt <prompt>", "System prompt to use for the session").argParser(String))
.addOption(new V3("--system-prompt-file <file>", "Read system prompt from a file").argParser(String).hideHelp())
.addOption(new V3("--append-system-prompt <prompt>", "Append a system prompt to the default system prompt").argParser(String))
.addOption(new V3("--append-system-prompt-file <file>", "Read system prompt from a file and append to the default system prompt").argParser(String).hideHelp())
```

---

## 2️⃣ 参数处理逻辑 (行 6145-6151)

### 处理流程

```javascript
// 1. 处理 systemPrompt
let TA = O.systemPrompt;  // 从命令行获取
if (O.systemPromptFile) {
    if (O.systemPrompt) {
        // 错误：不能同时使用两个参数
        process.stderr.write(J1.red(`Error: Cannot use both --system-prompt and --system-prompt-file`));
        process.exit(1);
    }
    // 从文件读取
    TA = YbK(v6, "utf8");  // readFileSync
}

// 2. 处理 appendSystemPrompt
let OA = O.appendSystemPrompt;
if (O.appendSystemPromptFile) {
    if (O.appendSystemPrompt) {
        // 错误：不能同时使用两个参数
        process.stderr.write(J1.red(`Error: Cannot use both --append-system-prompt and --append-system-prompt-file`));
        process.exit(1);
    }
    // 从文件读取
    OA = YbK(v6, "utf8");
}

// 3. 特殊处理：Teammate 模式追加提示词
if (i8() && LA?.agentId && LA?.agentName && LA?.teamName) {
    let v6 = qn2().TEAMMATE_SYSTEM_PROMPT_ADDENDUM;
    OA = OA ? `${OA}\n\n${v6}` : v6;
}
```

### 功能说明

| 参数 | 作用 |
|------|------|
| `--system-prompt` | **替换**默认系统提示词，完全覆盖内置提示词 |
| `--append-system-prompt` | **追加**到默认系统提示词末尾，保留内置提示词 |

---

## 3️⃣ 系统提示词组装函数

### 核心函数: `fX1` (行 2059)

```javascript
function fX1({
    mainThreadAgentDefinition,  // 主线程 Agent 定义
    toolUseContext,             // 工具使用上下文
    customSystemPrompt,         // 用户自定义系统提示词 (--system-prompt)
    defaultSystemPrompt,        // 默认系统提示词
    appendSystemPrompt,         // 追加系统提示词 (--append-system-prompt)
    overrideSystemPrompt        // 覆盖系统提示词
}) {
    // 如果有 overrideSystemPrompt，直接使用
    if (overrideSystemPrompt) return [overrideSystemPrompt];
    
    // 获取 Agent 的 getSystemPrompt()
    let agentPrompt = mainThreadAgentDefinition?.getSystemPrompt({
        toolUseContext: { options: toolUseContext.options }
    });
    
    // 组装提示词数组
    return [
        ...(agentPrompt ? [agentPrompt] : customSystemPrompt ? [customSystemPrompt] : defaultSystemPrompt),
        ...(appendSystemPrompt ? [appendSystemPrompt] : [])
    ];
}
```

### 优先级规则

1. `overrideSystemPrompt` > 一切（最高优先级）
2. `Agent.getSystemPrompt()` > `customSystemPrompt` > `defaultSystemPrompt`
3. `appendSystemPrompt` 始终追加到末尾

---

## 4️⃣ 内置系统提示词

### 4.1 身份声明变量

| 变量名 | 内容 | 用途 |
|--------|------|------|
| `D66` | `You are Claude Code, Anthropic's official CLI for Claude.` | 标准身份声明 |
| `D24` | `You are Claude Code, Anthropic's official CLI for Claude, running within the Claude Agent SDK.` | SDK 模式身份声明 |

### 4.2 主系统提示词 (行 4843-4972)

主系统提示词是一个动态模板，包含以下核心部分：

#### 开头身份声明
```
You are an interactive CLI tool that helps users [根据 Output Style 动态调整] 
Use the instructions below and the tools available to you to assist the user.
```

#### URL 安全警告
```
IMPORTANT: You must NEVER generate or guess URLs for the user unless you are 
confident that the URLs are for helping the user with programming. You may use 
URLs provided by the user in their messages or local files.
```

#### 帮助信息
```
If the user asks for help or wants to give feedback inform them of the following:
- /help: Get help with using Claude Code
- To give feedback, users should report the issue at https://github.com/anthropics/claude-code/issues
```

#### 语气和风格指南 (无 Output Style 时)
```markdown
# Tone and style
- Only use emojis if the user explicitly requests it
- Your output will be displayed on a command line interface
- Output text to communicate with the user; all text you output outside of tool use is displayed to the user
- NEVER create files unless they're absolutely necessary for achieving your goal
- Do not use a colon before tool calls

# Professional objectivity
Prioritize technical accuracy and truthfulness over validating the user's beliefs. 
Focus on facts and problem-solving, providing direct, objective technical info without 
any unnecessary superlatives, praise, or emotional validation.

# No time estimates
Never give time estimates or predictions for how long tasks will take...
```

#### 任务管理 (当有 TodoWrite 工具时)
```markdown
# Task Management
You have access to the TodoWrite tools to help you manage and plan tasks. 
Use these tools VERY frequently to ensure that you are tracking your tasks...

It is critical that you mark todos as completed as soon as you are done with a task.
```

#### 编码任务指南
```markdown
# Doing tasks
- NEVER propose changes to code you haven't read
- Be careful not to introduce security vulnerabilities
- Avoid over-engineering. Only make changes that are directly requested or clearly necessary
  - Don't add features, refactor code, or make "improvements" beyond what was asked
  - Don't add error handling, fallbacks, or validation for scenarios that can't happen
  - Don't create helpers, utilities, or abstractions for one-time operations
- Avoid backwards-compatibility hacks
```

#### 工具使用策略
```markdown
# Tool usage policy
- When doing file search, prefer to use the Task tool to reduce context usage
- You should proactively use the Task tool with specialized agents
- When exploring the codebase, use Task tool with subagent_type=Explore
- You can call multiple tools in a single response (parallel tool calls)
- Use specialized tools instead of bash commands when possible
```

#### 代码引用格式
```markdown
# Code References
When referencing specific functions or pieces of code include the pattern 
`file_path:line_number` to allow the user to easily navigate to the source code location.
```

#### 语言设置
```markdown
# Language
Always respond in ${语言}. Use ${语言} for all explanations, comments, and communications.
Technical terms and code identifiers should remain in their original form.
```

### 4.3 Agent 特定提示词

#### Bash Agent (行 1768)
```
You are a command execution specialist for Claude Code. Your role is to execute
bash commands efficiently and safely.

Guidelines:
- Execute commands precisely as instructed
- For git operations, follow git safety protocols
- Report command output clearly and concisely
- If a command fails, explain the error and suggest solutions
- Use command chaining (&&) for dependent operations
- Quote paths with spaces properly
- For clear communication, avoid using emojis

Complete the requested operations efficiently.
```

#### General-Purpose Agent (行 1779)
```
You are an agent for Claude Code, Anthropic's official CLI for Claude. Given the
user's message, you should use the tools available to complete the task. Do what
has been asked; nothing more, nothing less. When you complete the task simply 
respond with a detailed writeup.

Your strengths:
- Searching and understanding code across large codebases
- Reading and analyzing multiple files to understand context
- Explaining code patterns and architecture
- Finding bugs and security issues
- Answering questions about how code works
...
```

#### Explore Agent
```
You are an agent for Claude Code, Anthropic's official CLI for Claude. Given the
user's message, you should use the tools available to complete the task...

Your strengths:
- Fast file pattern matching and search
- Code keyword search
- Codebase structure analysis
```

#### Status Line Setup Agent (行 1794)
```
You are a status line setup agent for Claude Code. Your job is to create or update
the statusLine command in the user's Claude Code settings.

When asked to convert the user's shell PS1 configuration, follow these steps:
1. Read the user's shell configuration files
2. Extract the PS1 value
3. Convert PS1 escape sequences to shell commands
4. Update ~/.claude/settings.json
```

#### Security Review Skill (行 4358)
```
You are a senior security engineer conducting a focused security review...

OBJECTIVE:
Perform a security-focused code review to identify HIGH-CONFIDENCE security 
vulnerabilities that could have real exploitation potential.

CRITICAL INSTRUCTIONS:
1. MINIMIZE FALSE POSITIVES: Only flag issues where you're >80% confident
2. AVOID NOISE: Skip theoretical issues, style concerns, or low-impact findings
3. FOCUS ON IMPACT: Prioritize vulnerabilities that could lead to unauthorized access
```

### 4.4 Teammate 系统提示词追加 (行 3090)

```
# Teammate Communication

IMPORTANT: You are running as a teammate in a swarm. Your plain text output is NOT 
visible to the user or the team lead. To communicate with anyone on your team:
- Use the Teammate tool with the `write` operation to send messages
- Use the Teammate tool with the `broadcast` operation sparingly for team-wide announcements
- Just typing a response in text is not visible to others - you must use the tool

The user interacts only with the team lead. Your work is coordinated through the
task system and teammate messaging.
```

### 4.5 Hooks 配置说明 (行 5568)

```markdown
## Hooks Configuration

Hooks run commands at specific points in Claude Code's lifecycle.

### Hook Events
| Event | Matcher | Purpose |
|-------|---------|---------|
| PermissionRequest | Tool name | Run before permission prompt |
| PreToolUse | Tool name | Run before tool, can block |
| PostToolUse | Tool name | Run after successful tool |
| PostToolUseFailure | Tool name | Run after tool fails |
| Notification | Notification type | Run on notifications |
| Stop | - | Run when Claude stops |
| PreCompact | "manual"/"auto" | Before compaction |
| UserPromptSubmit | - | When user submits |
| SessionStart | - | When session starts |
```

---

## 5️⃣ 提示词组装数据流

```
用户输入
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  命令行参数解析                                              │
│  --system-prompt / --system-prompt-file                     │
│  --append-system-prompt / --append-system-prompt-file       │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  参数验证                                                    │
│  • 检查互斥参数                                              │
│  • 从文件读取内容                                            │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  特殊追加                                                    │
│  • Teammate 模式 → 追加 TEAMMATE_SYSTEM_PROMPT_ADDENDUM      │
│  • Chrome 模式 → 追加 Chrome 集成提示词                      │
│  • Custom Agent → 追加 Custom Agent Instructions             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  fX1() 组装函数                                              │
│                                                              │
│  优先级:                                                     │
│  1. overrideSystemPrompt (最高)                              │
│  2. Agent.getSystemPrompt()                                  │
│  3. customSystemPrompt (--system-prompt)                     │
│  4. defaultSystemPrompt (内置)                               │
│                                                              │
│  + appendSystemPrompt (始终追加)                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  gC() 主提示词生成函数                                       │
│                                                              │
│  动态拼接:                                                   │
│  • 身份声明                                                  │
│  • URL 安全警告                                              │
│  • 帮助信息                                                  │
│  • 语气和风格 (如无 Output Style)                            │
│  • 任务管理 (如有 TodoWrite)                                 │
│  • 问答工具 (如有 AskUserQuestion)                           │
│  • Hooks 说明                                                │
│  • 编码任务指南                                              │
│  • 工具使用策略                                              │
│  • 代码引用格式                                              │
│  • 语言设置                                                  │
│  • Output Style (如有)                                       │
│  • MCP 说明 (如有)                                           │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
                    最终系统提示词
```

---

## 6️⃣ SDK 使用方式

### Kotlin SDK (SubprocessTransport.kt)

```kotlin
// 系统提示词 - 使用临时文件避免命令行长度限制
options.systemPrompt?.let { prompt ->
    val tempFile = getOrCreateSystemPromptFile(prompt)
    command.add("--system-prompt-file")
    command.add(tempFile.toAbsolutePath().toString())
}

// 追加系统提示词 - 用于 MCP 场景
options.appendSystemPromptFile?.let { appendContent ->
    val tempFile = getOrCreateSystemPromptFile(appendContent)
    command.add("--append-system-prompt-file")
    command.add(tempFile.toAbsolutePath().toString())
}

// 临时文件缓存路径: {tempDir}/claude-agent-sdk/system-prompts/
```

---

## 7️⃣ 使用建议

### 何时使用 `--system-prompt`

- 需要**完全自定义**系统提示词
- 构建专用的 AI 应用，不需要 Claude Code 的标准功能
- 需要精确控制 AI 的行为和能力

### 何时使用 `--append-system-prompt`

- 需要**保留** Claude Code 的标准功能
- 只需添加额外的指令或上下文
- MCP 集成场景（添加 MCP 工具说明）
- 项目特定的编码规范

### 最佳实践

1. **使用文件而非命令行参数**：避免命令行长度限制和转义问题
2. **追加优于替换**：除非有特殊需求，否则使用 `--append-system-prompt`
3. **结构化提示词**：使用 Markdown 格式组织提示词内容
4. **测试验证**：修改系统提示词后充分测试 AI 行为
