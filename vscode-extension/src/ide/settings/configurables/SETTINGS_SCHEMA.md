# VS Code Settings Schema

This document lists all settings that need to be added to `package.json` in the `contributes.configuration` section.

## Overview

Settings are organized into the following categories:
1. **Claude Code Plus** - Main settings (default backend)
2. **Claude Code** - Claude-specific settings (model, thinking, permissions, agents)
3. **Codex** - Codex-specific settings (model, reasoning, sandbox)
4. **Git Generate** - AI-powered commit message generation
5. **MCP** - Model Context Protocol server configuration

---

## 1. Claude Code Plus (Main Settings)

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.defaultBackendType` | `enum` | `"claude"` | Default backend type (`claude` or `codex`) |
| `claudeCodePlus.defaultBypassPermissions` | `boolean` | `false` | Skip all permission confirmations by default |
| `claudeCodePlus.includePartialMessages` | `boolean` | `true` | Include partial messages in stream |

---

## 2. Claude Code Settings

### 2.1 Runtime Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.claude.nodePath` | `string` | `""` | Path to Node.js executable (empty = auto-detect) |
| `claudeCodePlus.claude.defaultModelId` | `string` | `"claude-opus-4-6"` | Default Claude model ID |
| `claudeCodePlus.claude.customModels` | `array` | `[]` | Custom model definitions |

### 2.2 Thinking Configuration

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.claude.defaultThinkingLevel` | `enum` | `"ultra"` | Default thinking level (`off`, `think`, `ultra`) |
| `claudeCodePlus.claude.thinkTokens` | `number` | `2048` | Token budget for "think" level (1-128000) |
| `claudeCodePlus.claude.ultraTokens` | `number` | `8096` | Token budget for "ultra" level (1-128000) |

### 2.3 Permissions

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.claude.permissionMode` | `enum` | `"default"` | Permission mode (`default`, `acceptEdits`, `plan`, `bypassPermissions`) |
| `claudeCodePlus.claude.defaultAutoCleanupContexts` | `boolean` | `false` | Auto-clear enabled contexts after send |

### 2.4 Agents Configuration

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.claude.agents.exploreWithVscode.enabled` | `boolean` | `true` | Enable ExploreWithVscode agent |
| `claudeCodePlus.claude.agents.exploreWithVscode.model` | `string` | `""` | Agent model (empty = inherit) |
| `claudeCodePlus.claude.agents.exploreWithVscode.description` | `string` | `""` | Agent description |
| `claudeCodePlus.claude.agents.exploreWithVscode.prompt` | `string` | `""` | Agent system prompt |
| `claudeCodePlus.claude.agents.exploreWithVscode.selectionHint` | `string` | `""` | Appended system prompt |
| `claudeCodePlus.claude.agents.exploreWithVscode.tools` | `array` | `[]` | Allowed tools list |

---

## 3. Codex Settings

### 3.1 Runtime Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.codex.path` | `string` | `""` | Path to Codex executable (empty = auto-detect) |
| `claudeCodePlus.codex.webSearchEnabled` | `boolean` | `false` | Enable web search feature |
| `claudeCodePlus.codex.defaultModelId` | `string` | `"gpt-5.2-codex"` | Default Codex model ID |
| `claudeCodePlus.codex.customModels` | `array` | `[]` | Custom model definitions |

### 3.2 Session Defaults

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.codex.reasoningEffort` | `enum` | `"medium"` | Reasoning effort (`minimal`, `low`, `medium`, `high`, `xhigh`) |
| `claudeCodePlus.codex.reasoningSummary` | `enum` | `"auto"` | Reasoning summary (`auto`, `concise`, `detailed`, `none`) |
| `claudeCodePlus.codex.sandboxMode` | `enum` | `"workspace-write"` | Sandbox mode (`read-only`, `workspace-write`, `danger-full-access`) |
| `claudeCodePlus.codex.defaultAutoCleanupContexts` | `boolean` | `false` | Auto-clear enabled contexts after send |

---

## 4. Git Generate Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.gitGenerate.enabled` | `boolean` | `false` | Enable Git Generate feature |
| `claudeCodePlus.gitGenerate.backend` | `enum` | `"claude"` | Backend for generation (`claude`, `codex`) |
| `claudeCodePlus.gitGenerate.model` | `string` | `""` | Model for generation (empty = default) |
| `claudeCodePlus.gitGenerate.saveSession` | `boolean` | `false` | Save generation session to history |
| `claudeCodePlus.gitGenerate.claudeThinkingLevel` | `string` | `"ultra"` | Claude thinking level for generation |
| `claudeCodePlus.gitGenerate.codexReasoningEffort` | `string` | `"xhigh"` | Codex reasoning effort for generation |
| `claudeCodePlus.gitGenerate.systemPrompt` | `string` | `""` | Custom system prompt (empty = default) |
| `claudeCodePlus.gitGenerate.userPrompt` | `string` | `""` | Custom user prompt (empty = default) |

---

## 5. MCP Settings

### 5.1 Built-in MCP Servers

#### User Interaction MCP
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.userInteraction.enabled` | `boolean` | `true` | Enable User Interaction MCP |
| `claudeCodePlus.mcp.userInteraction.backends` | `array` | `["all"]` | Enabled backends |
| `claudeCodePlus.mcp.userInteraction.instructions` | `string` | `""` | Custom instructions |
| `claudeCodePlus.mcp.userInteraction.timeout` | `number` | `3600` | Tool timeout in seconds |

#### VS Code LSP MCP
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.vscodeLsp.enabled` | `boolean` | `true` | Enable VS Code LSP MCP |
| `claudeCodePlus.mcp.vscodeLsp.backends` | `array` | `["all"]` | Enabled backends |
| `claudeCodePlus.mcp.vscodeLsp.instructions` | `string` | `""` | Custom instructions |
| `claudeCodePlus.mcp.vscodeLsp.timeout` | `number` | `60` | Tool timeout in seconds |

#### VS Code File MCP
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.vscodeFile.enabled` | `boolean` | `true` | Enable VS Code File MCP |
| `claudeCodePlus.mcp.vscodeFile.backends` | `array` | `["all"]` | Enabled backends |
| `claudeCodePlus.mcp.vscodeFile.instructions` | `string` | `""` | Custom instructions |
| `claudeCodePlus.mcp.vscodeFile.timeout` | `number` | `60` | Tool timeout in seconds |
| `claudeCodePlus.mcp.vscodeFile.disableBuiltinTools` | `boolean` | `false` | Disable built-in file tools |
| `claudeCodePlus.mcp.vscodeFile.allowExternal` | `boolean` | `true` | Allow external file access |
| `claudeCodePlus.mcp.vscodeFile.externalRules` | `string` | `"[]"` | External file access rules (JSON) |

#### Context7 MCP
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.context7.enabled` | `boolean` | `false` | Enable Context7 MCP |
| `claudeCodePlus.mcp.context7.backends` | `array` | `["all"]` | Enabled backends |
| `claudeCodePlus.mcp.context7.instructions` | `string` | `""` | Custom instructions |
| `claudeCodePlus.mcp.context7.timeout` | `number` | `60` | Tool timeout in seconds |
| `claudeCodePlus.mcp.context7.apiKey` | `string` | `""` | Context7 API key |

#### Terminal MCP
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.terminal.enabled` | `boolean` | `false` | Enable Terminal MCP |
| `claudeCodePlus.mcp.terminal.backends` | `array` | `["all"]` | Enabled backends |
| `claudeCodePlus.mcp.terminal.instructions` | `string` | `""` | Custom instructions |
| `claudeCodePlus.mcp.terminal.timeout` | `number` | `60` | Tool timeout in seconds |
| `claudeCodePlus.mcp.terminal.maxOutputLines` | `number` | `500` | Max output lines |
| `claudeCodePlus.mcp.terminal.maxOutputChars` | `number` | `50000` | Max output characters |
| `claudeCodePlus.mcp.terminal.readTimeout` | `number` | `30` | Read timeout in seconds |
| `claudeCodePlus.mcp.terminal.disableBuiltinBash` | `boolean` | `false` | Disable built-in Bash tool |

#### Git MCP
| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.git.enabled` | `boolean` | `false` | Enable Git MCP |
| `claudeCodePlus.mcp.git.backends` | `array` | `["all"]` | Enabled backends |
| `claudeCodePlus.mcp.git.instructions` | `string` | `""` | Custom instructions |
| `claudeCodePlus.mcp.git.timeout` | `number` | `60` | Tool timeout in seconds |
| `claudeCodePlus.mcp.git.commitLanguage` | `string` | `"en"` | Commit message language |

### 5.2 Custom MCP Servers

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `claudeCodePlus.mcp.customServers` | `array` | `[]` | Custom MCP server configurations |

Custom server object schema:
```json
{
  "name": "string",
  "enabled": "boolean",
  "backends": ["claude", "codex", "all"],
  "config": {
    "command": "string",
    "args": ["string"],
    "env": {}
  },
  "instructions": "string",
  "timeout": "number"
}
```

---

## package.json Configuration Section

Add the following to `contributes.configuration.properties` in `package.json`:

```json
{
  "claudeCodePlus.defaultBackendType": {
    "type": "string",
    "enum": ["claude", "codex"],
    "default": "claude",
    "description": "Default backend type for new sessions"
  },
  "claudeCodePlus.defaultBypassPermissions": {
    "type": "boolean",
    "default": false,
    "description": "Skip all permission confirmations by default (use with caution)"
  },
  "claudeCodePlus.includePartialMessages": {
    "type": "boolean",
    "default": true,
    "description": "Include partial messages in UI stream"
  },
  "claudeCodePlus.claude.nodePath": {
    "type": "string",
    "default": "",
    "description": "Path to Node.js executable. Leave empty to auto-detect from system PATH."
  },
  "claudeCodePlus.claude.defaultModelId": {
    "type": "string",
    "default": "claude-opus-4-6",
    "description": "Default Claude model ID"
  },
  "claudeCodePlus.claude.customModels": {
    "type": "array",
    "default": [],
    "items": {
      "type": "object",
      "properties": {
        "displayName": { "type": "string" },
        "modelId": { "type": "string" }
      },
      "required": ["displayName", "modelId"]
    },
    "description": "Custom Claude model definitions"
  },
  "claudeCodePlus.claude.defaultThinkingLevel": {
    "type": "string",
    "enum": ["off", "think", "ultra"],
    "default": "ultra",
    "description": "Default thinking level for Claude"
  },
  "claudeCodePlus.claude.thinkTokens": {
    "type": "number",
    "default": 2048,
    "minimum": 1,
    "maximum": 128000,
    "description": "Token budget for 'think' thinking level"
  },
  "claudeCodePlus.claude.ultraTokens": {
    "type": "number",
    "default": 8096,
    "minimum": 1,
    "maximum": 128000,
    "description": "Token budget for 'ultra' thinking level"
  },
  "claudeCodePlus.claude.permissionMode": {
    "type": "string",
    "enum": ["default", "acceptEdits", "plan", "bypassPermissions"],
    "default": "default",
    "description": "Permission mode for Claude sessions"
  },
  "claudeCodePlus.claude.defaultAutoCleanupContexts": {
    "type": "boolean",
    "default": false,
    "description": "Auto-clear enabled contexts after sending message"
  },
  "claudeCodePlus.codex.path": {
    "type": "string",
    "default": "",
    "description": "Path to Codex executable. Leave empty to auto-detect from system PATH."
  },
  "claudeCodePlus.codex.webSearchEnabled": {
    "type": "boolean",
    "default": false,
    "description": "Enable web search feature for Codex"
  },
  "claudeCodePlus.codex.defaultModelId": {
    "type": "string",
    "default": "gpt-5.2-codex",
    "description": "Default Codex model ID"
  },
  "claudeCodePlus.codex.customModels": {
    "type": "array",
    "default": [],
    "items": {
      "type": "object",
      "properties": {
        "displayName": { "type": "string" },
        "modelId": { "type": "string" }
      },
      "required": ["displayName", "modelId"]
    },
    "description": "Custom Codex model definitions"
  },
  "claudeCodePlus.codex.reasoningEffort": {
    "type": "string",
    "enum": ["minimal", "low", "medium", "high", "xhigh"],
    "default": "medium",
    "description": "Reasoning effort level for Codex"
  },
  "claudeCodePlus.codex.reasoningSummary": {
    "type": "string",
    "enum": ["auto", "concise", "detailed", "none"],
    "default": "auto",
    "description": "Reasoning summary mode for Codex"
  },
  "claudeCodePlus.codex.sandboxMode": {
    "type": "string",
    "enum": ["read-only", "workspace-write", "danger-full-access"],
    "default": "workspace-write",
    "description": "Sandbox mode for Codex sessions"
  },
  "claudeCodePlus.codex.defaultAutoCleanupContexts": {
    "type": "boolean",
    "default": false,
    "description": "Auto-clear enabled contexts after sending message"
  },
  "claudeCodePlus.gitGenerate.enabled": {
    "type": "boolean",
    "default": false,
    "description": "Enable AI-powered Git commit message generation"
  },
  "claudeCodePlus.gitGenerate.backend": {
    "type": "string",
    "enum": ["claude", "codex"],
    "default": "claude",
    "description": "Backend for commit message generation"
  },
  "claudeCodePlus.gitGenerate.model": {
    "type": "string",
    "default": "",
    "description": "Model for generation (empty = use default)"
  },
  "claudeCodePlus.gitGenerate.saveSession": {
    "type": "boolean",
    "default": false,
    "description": "Save generation session to history"
  },
  "claudeCodePlus.gitGenerate.claudeThinkingLevel": {
    "type": "string",
    "default": "ultra",
    "description": "Claude thinking level for generation"
  },
  "claudeCodePlus.gitGenerate.codexReasoningEffort": {
    "type": "string",
    "default": "xhigh",
    "description": "Codex reasoning effort for generation"
  },
  "claudeCodePlus.gitGenerate.systemPrompt": {
    "type": "string",
    "default": "",
    "description": "Custom system prompt for generation (empty = use default)"
  },
  "claudeCodePlus.gitGenerate.userPrompt": {
    "type": "string",
    "default": "",
    "description": "Custom user prompt for generation (empty = use default)"
  },
  "claudeCodePlus.mcp.userInteraction.enabled": {
    "type": "boolean",
    "default": true,
    "description": "Enable User Interaction MCP server"
  },
  "claudeCodePlus.mcp.userInteraction.backends": {
    "type": "array",
    "items": { "type": "string" },
    "default": ["all"],
    "description": "Enabled backends for User Interaction MCP"
  },
  "claudeCodePlus.mcp.userInteraction.instructions": {
    "type": "string",
    "default": "",
    "description": "Custom instructions for User Interaction MCP"
  },
  "claudeCodePlus.mcp.userInteraction.timeout": {
    "type": "number",
    "default": 3600,
    "description": "Tool timeout in seconds"
  },
  "claudeCodePlus.mcp.vscodeLsp.enabled": {
    "type": "boolean",
    "default": true,
    "description": "Enable VS Code LSP MCP server"
  },
  "claudeCodePlus.mcp.vscodeLsp.backends": {
    "type": "array",
    "items": { "type": "string" },
    "default": ["all"],
    "description": "Enabled backends for VS Code LSP MCP"
  },
  "claudeCodePlus.mcp.vscodeLsp.instructions": {
    "type": "string",
    "default": "",
    "description": "Custom instructions for VS Code LSP MCP"
  },
  "claudeCodePlus.mcp.vscodeLsp.timeout": {
    "type": "number",
    "default": 60,
    "description": "Tool timeout in seconds"
  },
  "claudeCodePlus.mcp.vscodeFile.enabled": {
    "type": "boolean",
    "default": true,
    "description": "Enable VS Code File MCP server"
  },
  "claudeCodePlus.mcp.vscodeFile.backends": {
    "type": "array",
    "items": { "type": "string" },
    "default": ["all"],
    "description": "Enabled backends for VS Code File MCP"
  },
  "claudeCodePlus.mcp.vscodeFile.instructions": {
    "type": "string",
    "default": "",
    "description": "Custom instructions for VS Code File MCP"
  },
  "claudeCodePlus.mcp.vscodeFile.timeout": {
    "type": "number",
    "default": 60,
    "description": "Tool timeout in seconds"
  },
  "claudeCodePlus.mcp.vscodeFile.disableBuiltinTools": {
    "type": "boolean",
    "default": false,
    "description": "Disable built-in file tools (Read, Write, Edit)"
  },
  "claudeCodePlus.mcp.vscodeFile.allowExternal": {
    "type": "boolean",
    "default": true,
    "description": "Allow access to files outside workspace"
  },
  "claudeCodePlus.mcp.vscodeFile.externalRules": {
    "type": "string",
    "default": "[]",
    "description": "External file access rules (JSON array)"
  },
  "claudeCodePlus.mcp.context7.enabled": {
    "type": "boolean",
    "default": false,
    "description": "Enable Context7 MCP server"
  },
  "claudeCodePlus.mcp.context7.backends": {
    "type": "array",
    "items": { "type": "string" },
    "default": ["all"],
    "description": "Enabled backends for Context7 MCP"
  },
  "claudeCodePlus.mcp.context7.instructions": {
    "type": "string",
    "default": "",
    "description": "Custom instructions for Context7 MCP"
  },
  "claudeCodePlus.mcp.context7.timeout": {
    "type": "number",
    "default": 60,
    "description": "Tool timeout in seconds"
  },
  "claudeCodePlus.mcp.context7.apiKey": {
    "type": "string",
    "default": "",
    "description": "Context7 API key"
  },
  "claudeCodePlus.mcp.terminal.enabled": {
    "type": "boolean",
    "default": false,
    "description": "Enable Terminal MCP server"
  },
  "claudeCodePlus.mcp.terminal.backends": {
    "type": "array",
    "items": { "type": "string" },
    "default": ["all"],
    "description": "Enabled backends for Terminal MCP"
  },
  "claudeCodePlus.mcp.terminal.instructions": {
    "type": "string",
    "default": "",
    "description": "Custom instructions for Terminal MCP"
  },
  "claudeCodePlus.mcp.terminal.timeout": {
    "type": "number",
    "default": 60,
    "description": "Tool timeout in seconds"
  },
  "claudeCodePlus.mcp.terminal.maxOutputLines": {
    "type": "number",
    "default": 500,
    "description": "Maximum output lines to capture"
  },
  "claudeCodePlus.mcp.terminal.maxOutputChars": {
    "type": "number",
    "default": 50000,
    "description": "Maximum output characters to capture"
  },
  "claudeCodePlus.mcp.terminal.readTimeout": {
    "type": "number",
    "default": 30,
    "description": "Read timeout in seconds"
  },
  "claudeCodePlus.mcp.terminal.disableBuiltinBash": {
    "type": "boolean",
    "default": false,
    "description": "Disable built-in Bash tool"
  },
  "claudeCodePlus.mcp.git.enabled": {
    "type": "boolean",
    "default": false,
    "description": "Enable Git MCP server"
  },
  "claudeCodePlus.mcp.git.backends": {
    "type": "array",
    "items": { "type": "string" },
    "default": ["all"],
    "description": "Enabled backends for Git MCP"
  },
  "claudeCodePlus.mcp.git.instructions": {
    "type": "string",
    "default": "",
    "description": "Custom instructions for Git MCP"
  },
  "claudeCodePlus.mcp.git.timeout": {
    "type": "number",
    "default": 60,
    "description": "Tool timeout in seconds"
  },
  "claudeCodePlus.mcp.git.commitLanguage": {
    "type": "string",
    "default": "en",
    "description": "Language for commit messages"
  },
  "claudeCodePlus.mcp.customServers": {
    "type": "array",
    "default": [],
    "items": {
      "type": "object",
      "properties": {
        "name": { "type": "string" },
        "enabled": { "type": "boolean", "default": true },
        "backends": {
          "type": "array",
          "items": { "type": "string" },
          "default": ["all"]
        },
        "config": {
          "type": "object",
          "properties": {
            "command": { "type": "string" },
            "args": { "type": "array", "items": { "type": "string" } },
            "env": { "type": "object" }
          }
        },
        "instructions": { "type": "string" },
        "timeout": { "type": "number", "default": 60 }
      },
      "required": ["name", "config"]
    },
    "description": "Custom MCP server configurations"
  }
}
```
