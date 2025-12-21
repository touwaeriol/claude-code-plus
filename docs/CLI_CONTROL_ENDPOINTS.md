# Claude CLI Control Endpoints Analysis

This document analyzes all control request endpoints supported by Claude CLI (v2.0.73) and their implementation status in our SDK.

## Overview

Control requests use the following format:

```json
{
  "type": "control_request",
  "request_id": "unique-id",
  "request": {
    "subtype": "endpoint_name",
    // ... endpoint-specific parameters
  }
}
```

Response format:

```json
{
  "type": "control_response",
  "response": {
    "subtype": "success" | "error",
    "request_id": "unique-id",
    "response": { /* endpoint-specific response */ },
    "error": "error message if subtype is error"
  }
}
```

---

## Endpoint Implementation Status

| Endpoint | CLI Status | SDK Status | Notes |
|----------|------------|------------|-------|
| `interrupt` | Built-in | Implemented | ControlProtocol.interrupt() |
| `initialize` | Built-in | Implemented | ControlProtocol.initialize() |
| `set_permission_mode` | Built-in | Implemented | ControlProtocol.setPermissionMode() |
| `set_model` | Built-in | Implemented | ControlProtocol.setModel() |
| `set_max_thinking_tokens` | Built-in | Implemented | ControlProtocol.setMaxThinkingTokens() |
| `mcp_status` | Built-in | Implemented | ControlProtocol.getMcpStatus() |
| `mcp_message` | Built-in | Implemented | handleMcpMessage() for SDK MCP servers |
| `mcp_set_servers` | Built-in | Implemented | ControlProtocol.setMcpServers() |
| `rewind_files` | Built-in | Not Implemented | File checkpoint/rewind feature |
| `agent_run_to_background` | Patched | Implemented | ControlProtocol.agentRunToBackground() |
| `get_chrome_status` | Patched | Implemented | ControlProtocol.getChromeStatus() |
| `mcp_reconnect` | Patched | Implemented | ControlProtocol.reconnectMcp() |
| `mcp_tools` | Patched | Implemented | ControlProtocol.getMcpTools() |
| `mcp_disable` | Patched | Implemented | ControlProtocol.disableMcp() |
| `mcp_enable` | Patched | Implemented | ControlProtocol.enableMcp() |

---

## Detailed Endpoint Specifications

### 1. interrupt

Interrupts the current execution.

**Request:**
```json
{
  "subtype": "interrupt"
}
```

**Response:**
```json
{
  "subtype": "success"
}
```

**Implementation:** Calls `D.abort()` (AbortController) to cancel ongoing operations.

---

### 2. initialize

Initializes the session with optional configuration.

**Request:**
```json
{
  "subtype": "initialize",
  "systemPrompt": "optional custom system prompt",
  "appendSystemPrompt": "optional append to system prompt",
  "agents": [],
  "hooks": {},
  "jsonSchema": {},
  "sdkMcpServers": ["server1", "server2"]
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "commands": [
      { "name": "help", "description": "Show help" }
    ],
    "output_style": "default",
    "available_output_styles": ["default", "json"],
    "models": [
      { "value": "claude-3-5-sonnet", "displayName": "Sonnet 3.5" }
    ],
    "account": {
      "email": "user@example.com",
      "subscriptionType": "pro"
    }
  }
}
```

---

### 3. set_permission_mode

Changes the permission mode for tool execution.

**Request:**
```json
{
  "subtype": "set_permission_mode",
  "mode": "bypassPermissions" | "askAlways" | "autoApprove"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "mode": "bypassPermissions"
  }
}
```

**Error Response (if mode is disabled):**
```json
{
  "subtype": "error",
  "error": "Cannot set permission mode to bypassPermissions because it is disabled"
}
```

---

### 4. set_model

Switches the AI model.

**Request:**
```json
{
  "subtype": "set_model",
  "model": "claude-3-5-sonnet-20241022" | "default"
}
```

**Response:**
```json
{
  "subtype": "success"
}
```

---

### 5. set_max_thinking_tokens

Sets the maximum thinking tokens limit.

**Request:**
```json
{
  "subtype": "set_max_thinking_tokens",
  "max_thinking_tokens": 8000 | null
}
```

**Response:**
```json
{
  "subtype": "success"
}
```

---

### 6. mcp_status

Queries the status of all MCP servers.

**Request:**
```json
{
  "subtype": "mcp_status"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "mcpServers": [
      {
        "name": "jetbrains",
        "status": "connected",
        "serverInfo": {
          "name": "JetBrains MCP",
          "version": "1.0.0"
        }
      }
    ]
  }
}
```

**Status values:** `connected`, `pending`, `failed`, `disabled`, `sdk`

---

### 7. mcp_message

Sends a message to an SDK MCP server.

**Request:**
```json
{
  "subtype": "mcp_message",
  "server_name": "my-sdk-server",
  "message": {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": { "name": "myTool", "arguments": {} }
  }
}
```

**Response:** Forwarded to SDK for handling.

---

### 8. mcp_set_servers

Dynamically configures MCP servers (FULL REPLACEMENT mode).

**Request:**
```json
{
  "subtype": "mcp_set_servers",
  "servers": {
    "server1": {
      "command": "node",
      "args": ["server.js"],
      "env": { "API_KEY": "xxx" }
    }
  }
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "added": ["server1"],
    "removed": ["old-server"],
    "errors": {}
  }
}
```

**Important:** This is a FULL REPLACEMENT. Servers not in the request will be REMOVED.

---

### 9. rewind_files (Not Implemented)

Rewinds files to a specific message checkpoint.

**Request:**
```json
{
  "subtype": "rewind_files",
  "user_message_id": "uuid-of-message"
}
```

**Response:**
```json
{
  "subtype": "success"
}
```

**Error Response:**
```json
{
  "subtype": "error",
  "error": "No file checkpoint found for message xxx"
}
```

**Note:** This endpoint is used for the file "time travel" feature, allowing users to revert file changes to a previous state.

---

## Patched Endpoints

These endpoints are added via AST patches to the CLI.

### 10. agent_run_to_background (Patch 001)

Moves a Task tool (subagent) to background execution.

**Request:**
```json
{
  "subtype": "agent_run_to_background",
  "target_id": "optional-agent-id"
}
```

**Response:**
```json
{
  "subtype": "success"
}
```

**Patch File:** `cli-patches/patches/001-run-in-background.js`

---

### 11. get_chrome_status (Patch 002)

Queries Chrome extension status.

**Request:**
```json
{
  "subtype": "get_chrome_status"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "installed": true,
    "enabled": true,
    "connected": true,
    "mcpServerStatus": "connected",
    "extensionVersion": "1.0.36"
  }
}
```

**Patch File:** `cli-patches/patches/002-chrome-status.js`

---

### 12. mcp_reconnect (Patch 004)

Reconnects a specific MCP server.

**Request:**
```json
{
  "subtype": "mcp_reconnect",
  "server_name": "jetbrains"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "success": true,
    "server_name": "jetbrains",
    "status": "connected",
    "tools_count": 15,
    "error": null
  }
}
```

**Patch File:** `cli-patches/patches/004-mcp-reconnect.js`

---

## Implementation Priority

### High Priority (Core Features)
- All currently implemented - no gaps

### Medium Priority (Complete Support)
- `rewind_files` - File management/undo feature

### Low Priority (Specific Scenarios)
- None currently identified

---

## Patch System

The CLI patch system uses Babel AST transformation to add new control endpoints without modifying the original CLI source.

**Patch Structure:**
```javascript
module.exports = {
  id: 'patch-name',
  description: 'Description',
  priority: 100,  // Lower = earlier execution
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    // AST transformation logic
    return { success: true, details: [] };
  }
};
```

**Patch Files Location:** `claude-agent-sdk/cli-patches/patches/`

**Patch Registry:** `claude-agent-sdk/cli-patches/patches/index.js`

---

## /mcp Command Analysis

The `/mcp` command in Claude CLI provides a complete MCP server management interface. This section documents how each feature is implemented.

### /mcp Command Features Overview

| Feature | Implementation | Control Endpoint | SDK Support |
|---------|---------------|------------------|-------------|
| **List servers** | Built-in | `mcp_status` | ✅ getMcpStatus() |
| **View tools** | Patched | `mcp_tools` | ✅ getMcpTools() |
| **Reconnect** | Patched | `mcp_reconnect` | ✅ reconnectMcp() |
| **Disable** | Patched | `mcp_disable` | ✅ disableMcp() |
| **Enable** | Patched | `mcp_enable` | ✅ enableMcp() |

### Feature Details

#### 1. View Tools

Shows all tools provided by an MCP server.

**Implementation:** `mcp_tools` control endpoint (Patch 005)

**Request:**
```json
{
  "subtype": "mcp_tools",
  "server_name": "jetbrains"  // Optional, null returns all tools
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "server_name": "jetbrains",
    "tools": [
      {
        "name": "FileIndex",
        "description": "Search files, classes, and symbols...",
        "inputSchema": { "type": "object", "properties": {...} }
      }
    ],
    "count": 15
  }
}
```

**SDK Usage:**
```kotlin
val result = controlProtocol.getMcpTools("jetbrains")
// result.tools - list of tool info
// result.count - total tool count
```

**Frontend Usage:**
```typescript
const result = await session.getMcpTools("jetbrains")
// result.tools.forEach(tool => console.log(tool.name, tool.description))
```

**How it works internally:**
1. Patch reads from CLI's internal tool registry (`y.mcp.tools`)
2. Filters tools by `serverName` if provided
3. Returns tool name (original MCP name), description, and inputSchema

**Note:** The CLI populates `y.mcp.tools` when MCP servers connect via real-time MCP protocol calls. This endpoint reads from that registry.

---

#### 2. Reconnect

Reconnects a specific MCP server without full restart.

**Implementation:** `mcp_reconnect` control endpoint (Patch 004)

**Internal Flow:**
```
User selects "Reconnect"
    ↓
CLI sends control_request { subtype: "mcp_reconnect", server_name: "xxx" }
    ↓
Patch calls internal function x2A(serverName, config)
    ↓
x2A executes: gm() (disconnect) → hm() (connect)
    ↓
Returns: { success, status, tools_count, error }
    ↓
CLI updates y.clients[] with new state
```

**SDK Usage:**
```kotlin
val result = controlProtocol.reconnectMcp("jetbrains")
// result.success, result.status, result.toolsCount
```

---

#### 3. Disable

Temporarily disables an MCP server.

**Implementation:** `mcp_disable` control endpoint (Patch 006)

**Request:**
```json
{
  "subtype": "mcp_disable",
  "server_name": "jetbrains"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "success": true,
    "server_name": "jetbrains",
    "status": "disabled",
    "tools_count": 0,
    "error": null
  }
}
```

**Internal Flow:**
```
User requests disable
    ↓
SDK sends control_request { subtype: "mcp_disable", server_name: "xxx" }
    ↓
Patch calls internal functions:
    - CY0(serverName, false) → Add to disabledMcpServers list
    - gm(serverName, config) → Disconnect if currently connected
    ↓
Returns: { success, status: "disabled", tools_count: 0, error }
```

**SDK Usage:**
```kotlin
val result = controlProtocol.disableMcp("jetbrains")
// result.success, result.status == "disabled"
```

---

#### 4. Enable

Re-enables a previously disabled MCP server.

**Implementation:** `mcp_enable` control endpoint (Patch 006)

**Request:**
```json
{
  "subtype": "mcp_enable",
  "server_name": "jetbrains"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "success": true,
    "server_name": "jetbrains",
    "status": "connected",
    "tools_count": 15,
    "error": null
  }
}
```

**Internal Flow:**
```
User requests enable
    ↓
SDK sends control_request { subtype: "mcp_enable", server_name: "xxx" }
    ↓
Patch calls internal functions:
    - CY0(serverName, true) → Remove from disabledMcpServers list
    - x2A(serverName, config) → Reconnect the server
    ↓
Returns: { success, status: "connected", tools_count, error }
```

**SDK Usage:**
```kotlin
val result = controlProtocol.enableMcp("jetbrains")
// result.success, result.status, result.toolsCount
```

**UI Flow:**
```
Status: ( ) disabled  →  (select "Enable")  →  Status: ✓ connected
```

---

### MCP State Data Structures

```javascript
// Server configurations
y.configs = {
  "jetbrains": {
    type: "stdio",
    command: "node",
    args: ["server.js"],
    env: { "KEY": "value" },
    disabled: false  // Enable/disable flag
  }
}

// Connected clients
y.clients = [
  {
    name: "jetbrains",
    type: "connected",  // "connected" | "failed" | "pending" | "disabled"
    serverInfo: {
      name: "JetBrains MCP",
      version: "1.0.0"
    }
  }
]

// Tools registry
y.mcp.tools = [
  {
    name: "toolName",
    serverName: "jetbrains",
    description: "Tool description",
    inputSchema: { type: "object", properties: {...} }
  }
]
```

---

## /chrome Command Analysis

The `/chrome` command in Claude CLI manages the Chrome extension (claude-in-chrome) MCP server.

### /chrome Command Features

| Feature | Implementation | Control Endpoint | SDK Support |
|---------|---------------|------------------|-------------|
| **Check status** | Patched | `get_chrome_status` | ✅ getChromeStatus() |
| **Reconnect extension** | Patched | `mcp_reconnect` (server_name: "claude-in-chrome") | ✅ reconnectMcp("claude-in-chrome") |

### Feature Details

#### 1. Check Status

Queries the Chrome extension installation and connection status.

**Implementation:** `get_chrome_status` control endpoint (Patch 002)

**Request:**
```json
{
  "subtype": "get_chrome_status"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "installed": true,
    "enabled": true,
    "connected": true,
    "mcpServerStatus": "connected",
    "extensionVersion": "1.0.36"
  }
}
```

**SDK Usage:**
```kotlin
val status = controlProtocol.getChromeStatus()
// status.installed, status.connected, status.extensionVersion
```

---

#### 2. Reconnect Extension

Reconnects the Chrome extension MCP server. This is a special case of `mcp_reconnect` where `server_name` is always `"claude-in-chrome"`.

**Implementation:** Reuses `mcp_reconnect` control endpoint (Patch 004)

**Request:**
```json
{
  "subtype": "mcp_reconnect",
  "server_name": "claude-in-chrome"
}
```

**Response:**
```json
{
  "subtype": "success",
  "response": {
    "success": true,
    "server_name": "claude-in-chrome",
    "status": "connected",
    "tools_count": 17,
    "error": null
  }
}
```

**SDK Usage:**
```kotlin
val result = controlProtocol.reconnectMcp("claude-in-chrome")
// result.success, result.toolsCount
```

**Note:** The Chrome extension is just another MCP server named "claude-in-chrome", so all MCP management operations apply to it.

---

## Frontend Implementation Notes

### Real-time Data Fetching

**IMPORTANT:** The frontend MUST NOT cache MCP/Chrome status data. Each time a status popup is opened, it should make a fresh API call to the backend.

**Current Implementation (ChatHeader.vue):**
```typescript
// MCP Status: fetch on every popup open
watch(showMcpStatus, async (visible) => {
  if (visible && isConnected) {
    fetchedMcpServers.value = []  // Clear old data immediately
    const result = await session.getMcpStatus()
    fetchedMcpServers.value = result.servers
  }
})

// Chrome Status: fetch on every popup open
watch(showChromeStatus, async (visible) => {
  if (visible && isConnected) {
    await queryChromeStatus()
  }
})
```

This ensures:
1. Data is always fresh and reflects the actual current state
2. No stale cache issues
3. Immediate feedback after reconnect operations

---

## Version History

- **v2.0.73** - Current analyzed version
- Patches applied: 001-run-in-background, 002-chrome-status, 003-parent-uuid, 004-mcp-reconnect, 005-mcp-tools, 006-mcp-disable-enable
