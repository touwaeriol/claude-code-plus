# Claude CLI --system-prompt 参数分析报告

## 基于 AST 语法树分析 (CLI 2.1.19)

---

## 📋 概述

`--system-prompt` 及相关参数用于控制 Claude CLI 会话的系统提示词（System Prompt）。系统提示词是发送给模型的指令，定义了 Claude 在该会话中的行为和能力。

---

## 🔧 命令行参数定义 (行 6135)

| 参数 | 描述 | 作用 |
|------|------|------|
| `--system-prompt <prompt>` | System prompt to use for the session | **完全替换**默认系统提示词 |
| `--system-prompt-file <file>` | Read system prompt from a file | 从文件读取，效果同上 |
| `--append-system-prompt <prompt>` | Append a system prompt to the default system prompt | **追加到**默认系统提示词之后 |
| `--append-system-prompt-file <file>` | Read system prompt from a file and append to the default system prompt | 从文件读取追加内容 |

---

## 🏗️ 核心数据流

### 1. 参数解析阶段

```
命令行参数 (--system-prompt / --append-system-prompt)
    ↓
Commander.js 解析 (行 6135)
    ↓
systemPrompt / appendSystemPrompt 变量
    ↓
传递给 X9A 函数 (REPL 入口)
```

### 2. 系统提示词组装函数 `fX1` (行 2059)

这是**核心组装函数**，参数签名：

```javascript
function fX1({
    mainThreadAgentDefinition,  // Agent 定义
    toolUseContext,              // 工具使用上下文
    customSystemPrompt,          // --system-prompt 传入的值
    defaultSystemPrompt,         // 默认系统提示词
    appendSystemPrompt,          // --append-system-prompt 传入的值
    overrideSystemPrompt         // SDK 内部覆盖（优先级最高）
}) {
    // 如果有 overrideSystemPrompt，直接返回它
    if (overrideSystemPrompt) return [overrideSystemPrompt];
    
    // 获取 Agent 的系统提示词（如果有）
    let agentPrompt = mainThreadAgentDefinition?.getSystemPrompt();
    
    // 组装最终提示词数组
    return [
        // 优先使用 Agent 提示词，其次是 customSystemPrompt，最后是 defaultSystemPrompt
        ...agentPrompt ? [agentPrompt] : customSystemPrompt ? [customSystemPrompt] : defaultSystemPrompt,
        // 如果有 appendSystemPrompt，追加到末尾
        ...appendSystemPrompt ? [appendSystemPrompt] : []
    ];
}
```

### 3. 默认系统提示词

从 AST 分析发现两个默认系统提示词：

| 变量名 | 内容 | 使用场景 |
|--------|------|----------|
| `D66` | "You are Claude Code, Anthropic's official CLI for Claude." | **普通模式** |
| `D24` | "You are Claude Code, Anthropic's official CLI for Claude, running within the Claude Agent SDK." | **SDK 模式** |

---

## 🔄 优先级规则

系统提示词的优先级从高到低：

```
1. overrideSystemPrompt (SDK 内部覆盖)
   ↓
2. mainThreadAgentDefinition.getSystemPrompt() (Agent 定义的提示词)
   ↓
3. customSystemPrompt (--system-prompt 参数)
   ↓
4. defaultSystemPrompt (默认提示词 D66/D24)
   ↓
5. + appendSystemPrompt (--append-system-prompt 追加内容)
```

**关键规则：**
- `--system-prompt` 会**完全替换**默认系统提示词
- `--append-system-prompt` 会**追加到**任何提示词之后
- 如果使用 Agent，Agent 的提示词优先于 `--system-prompt`

---

## 📡 API 调用 (行 2846)

系统提示词最终传递给 Anthropic API：

```javascript
$.beta.messages.create({
    system: j,  // 组装后的系统提示词
    // ... 其他参数
})
```

---

## 🔧 相关函数调用链

```
命令行入口 (行 6135)
    ↓
fn2 验证参数 (行 6200)
    ↓
X9A REPL 入口 (行 5951)
    ↓ 传递 systemPrompt, appendSystemPrompt
Oa7 查询函数 (行 3039)
    ↓ 传递 customSystemPrompt, appendSystemPrompt
fX1 组装函数 (行 2059)
    ↓ 返回组装后的提示词数组
tT 发送查询 (行 3083)
    ↓ 使用 systemPrompt
API 调用 (行 2846)
```

---

## 💡 使用场景示例

### 1. 完全自定义系统提示词

```bash
claude --print --system-prompt "你是一个专业的代码审查助手"
```
效果：完全替换默认提示词，Claude 不再知道自己是 "Claude Code"。

### 2. 追加额外指令

```bash
claude --print --append-system-prompt "请始终使用中文回复"
```
效果：保留默认提示词，在末尾追加中文回复指令。

### 3. SDK 模式

在 SDK 模式下，系统提示词会自动包含 "running within the Claude Agent SDK"，并可以通过 API 控制。

---

## 📊 相关变量映射 (CLI 2.1.19)

| 用途 | 变量名 | 行号 |
|------|--------|------|
| 普通模式默认提示词 | `D66` | 403 |
| SDK 模式默认提示词 | `D24` | 403 |
| 系统提示词组装函数 | `fX1` | 2059 |
| 查询主函数 | `Oa7` | 3039 |
| REPL 入口 | `X9A` | 5951 |
| 参数验证 | `fn2` | 6200 |

---

## ⚠️ 注意事项

1. **--system-prompt 和 --system-prompt-file 互斥**
   ```
   Error: Cannot use both --system-prompt and --system-prompt-file
   ```

2. **--append-system-prompt 和 --append-system-prompt-file 互斥**
   ```
   Error: Cannot use both --append-system-prompt and --append-system-prompt-file
   ```

3. **Agent 提示词优先**
   - 如果使用 `--agent` 参数指定了自定义 Agent，Agent 的 `getSystemPrompt()` 会覆盖 `--system-prompt`

4. **SDK 模式特殊处理**
   - 当 `CLAUDE_CODE_ENTRYPOINT` 为 `sdk-ts`/`sdk-py`/`sdk-cli` 时，使用 SDK 模式默认提示词

---

## 🔍 AST 分析工具

使用以下脚本进行分析：

```bash
cd claude-agent-sdk/cli-patches
node analyze-system-prompt.mjs claude-cli-2.1.19.js
node analyze-system-prompt-flow.mjs claude-cli-2.1.19.js
```

---

## 📝 版本信息

- CLI 版本: 2.1.19
- 分析时间: 2026-01-31
- 分析方法: Babel AST 解析

