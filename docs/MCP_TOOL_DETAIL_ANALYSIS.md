# MCP Tool Detail Analysis

This document analyzes how Claude Code CLI retrieves and displays MCP tool details, including the data structures, data flow, and frontend implementation guide.

## Overview

When viewing MCP tools in Claude Code CLI (`/mcp` command), the tool details include:
- **Tool name**: Original MCP tool name (e.g., "FileIndex")
- **Full name**: Fully qualified name (e.g., "mcp__jetbrains__FileIndex")
- **Description**: Tool description text
- **Parameters**: Input schema with parameter definitions

## Data Structure Analysis

### 1. MCP Protocol Level (`mcp-types`)

**Source**: `external/openai-codex/codex-rs/mcp-types/src/lib.rs`

```rust
/// Definition for a tool the client can call.
pub struct Tool {
    /// Tool description
    pub description: Option<String>,

    /// Input parameter schema
    #[serde(rename = "inputSchema")]
    pub input_schema: ToolInputSchema,

    /// Tool name
    pub name: String,

    /// Display title
    pub title: Option<String>,

    /// Additional annotations
    pub annotations: Option<ToolAnnotations>,
}

/// A JSON Schema object defining the expected parameters for the tool.
pub struct ToolInputSchema {
    /// Parameter definitions (JSON Schema properties)
    pub properties: Option<serde_json::Value>,

    /// List of required parameter names
    pub required: Option<Vec<String>>,

    /// Schema type (default: "object")
    pub r#type: String,
}
```

### 2. CLI Protocol Level

**Source**: `external/openai-codex/codex-rs/protocol/src/protocol.rs`

```rust
// McpTool is imported from mcp_types::Tool
use mcp_types::Tool as McpTool;

/// Response from /mcp command - list tools
pub struct McpListToolsResponseEvent {
    /// Fully qualified tool name -> tool definition
    /// Key format: "mcp__{server_name}__{tool_name}"
    pub tools: HashMap<String, McpTool>,

    /// Known resources grouped by server name
    pub resources: HashMap<String, Vec<McpResource>>,

    /// Known resource templates grouped by server name
    pub resource_templates: HashMap<String, Vec<McpResourceTemplate>>,

    /// Authentication status for each configured MCP server
    pub auth_statuses: HashMap<String, McpAuthStatus>,
}
```

### 3. Backend SDK Level (Kotlin)

**Source**: `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/Options.kt`

```kotlin
/**
 * MCP tool information.
 */
@Serializable
data class McpToolInfo(
    /** Tool name (original MCP tool name, e.g., "FileIndex") */
    val name: String,

    /** Tool description */
    val description: String,

    /** Input JSON Schema - contains properties, required, type */
    val inputSchema: JsonElement? = null
)

/**
 * Response from mcp_tools request.
 */
@Serializable
data class McpToolsResponse(
    /** Server name filter (null if all servers) */
    val serverName: String?,

    /** List of tools */
    val tools: List<McpToolInfo>,

    /** Total count of tools */
    val count: Int
)
```

### 4. Frontend Level (TypeScript)

**Current Implementation** (`frontend/src/components/toolbar/McpStatusPopup.vue`):

```typescript
interface McpToolInfo {
  name: string
  description: string
  inputSchema?: string  // ❌ Wrong type - should be object
}
```

**Corrected Type**:

```typescript
interface McpToolInputSchema {
  type: string                           // Usually "object"
  properties?: Record<string, {
    type: string
    description?: string
    default?: any
    enum?: string[]
    minimum?: number
    maximum?: number
    // ... other JSON Schema properties
  }>
  required?: string[]
  additionalProperties?: boolean
}

interface McpToolInfo {
  name: string
  description: string
  inputSchema?: McpToolInputSchema
}
```

## Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           MCP Server                                     │
│  (e.g., jetbrains-plugin, playwright-extension)                         │
│                                                                         │
│  Provides Tool via MCP Protocol:                                        │
│  {                                                                      │
│    name: "FileIndex",                                                   │
│    description: "Search files...",                                      │
│    inputSchema: {                                                       │
│      type: "object",                                                    │
│      properties: {                                                      │
│        query: { type: "string", description: "Search keywords" },       │
│        maxResults: { type: "integer", default: 20 }                     │
│      },                                                                 │
│      required: ["query"]                                                │
│    }                                                                    │
│  }                                                                      │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ MCP Protocol (JSON-RPC)
                                 │ tools/list request
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Claude Code CLI                                  │
│                                                                         │
│  Stores in AppState:                                                    │
│  getAppState().mcp.tools = {                                            │
│    "mcp__jetbrains__FileIndex": Tool { ... }                           │
│  }                                                                      │
│                                                                         │
│  Control Endpoint: mcp_tools                                            │
│  - Reads from state.mcp.tools                                           │
│  - Filters by server_name if provided                                   │
│  - Returns tool list with full inputSchema                              │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ WebSocket (Control Protocol)
                                 │ { subtype: "mcp_tools", server_name: "xxx" }
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Backend (ControlProtocol.kt)                          │
│                                                                         │
│  getMcpTools(serverName):                                               │
│    1. Send control request to CLI                                       │
│    2. Parse response:                                                   │
│       - name: toolObj["name"]                                           │
│       - description: toolObj["description"]                             │
│       - inputSchema: toolObj["inputSchema"]  // Full JSON object        │
│    3. Return McpToolsResponse                                           │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ WebSocket RPC
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Frontend (Vue)                                   │
│                                                                         │
│  session.getMcpTools(serverName):                                       │
│    Returns { tools: McpToolInfo[], count: number }                      │
│                                                                         │
│  Current: Only displays description                                      │
│  Needed: Parse and display inputSchema parameters                       │
└─────────────────────────────────────────────────────────────────────────┘
```

## CLI Display Implementation

### CLI `/mcp` Command Flow

1. User enters `/mcp` command
2. CLI calls `Op::ListMcpTools`
3. Core returns `McpListToolsResponseEvent` with full tool data
4. TUI displays interactive server list
5. User selects server → shows tool list
6. User selects tool → shows tool details

**Tool Detail Display** (from CLI screenshots):

```
Tool name: javascript_tool
Full name: mcp__claude-in-chrome__javascript_tool

Description:
Execute JavaScript code in the context of the current page...

Parameters:
• action (required): string - Must be set to 'javascript_exec'
• text (required): string - The JavaScript code to execute...
• tabId (required): number - Tab ID to execute the code in...
```

### Key Observation

The CLI displays tool parameters by parsing `inputSchema.properties` and `inputSchema.required`:

```rust
// Pseudocode for parameter display
for (name, schema) in tool.input_schema.properties {
    let required = tool.input_schema.required.contains(name);
    let type_str = schema.type;
    let desc = schema.description.unwrap_or("");

    println!("• {} ({}): {} - {}", name,
        if required { "required" } else { "optional" },
        type_str, desc);
}
```

## Frontend Implementation Guide

### Step 1: Update Type Definitions

```typescript
// types/mcp.ts
export interface McpToolParameter {
  type: string
  description?: string
  default?: any
  enum?: string[]
  minimum?: number
  maximum?: number
  minLength?: number
  maxLength?: number
  format?: string
}

export interface McpToolInputSchema {
  type: string  // Usually "object"
  properties?: Record<string, McpToolParameter>
  required?: string[]
  additionalProperties?: boolean
}

export interface McpToolInfo {
  name: string
  description: string
  inputSchema?: McpToolInputSchema
}
```

### Step 2: Create Parameter Display Component

```vue
<!-- McpToolParameters.vue -->
<template>
  <div v-if="parameters.length > 0" class="tool-parameters">
    <div class="params-header">Parameters:</div>
    <div v-for="param in parameters" :key="param.name" class="param-item">
      <span class="param-name">{{ param.name }}</span>
      <span class="param-required" :class="{ required: param.required }">
        ({{ param.required ? 'required' : 'optional' }})
      </span>
      <span class="param-type">: {{ param.type }}</span>
      <span v-if="param.description" class="param-desc">
        - {{ param.description }}
      </span>
      <span v-if="param.default !== undefined" class="param-default">
        [default: {{ JSON.stringify(param.default) }}]
      </span>
    </div>
  </div>
  <div v-else class="no-params">No parameters</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { McpToolInputSchema } from '@/types/mcp'

const props = defineProps<{
  inputSchema?: McpToolInputSchema
}>()

interface ParsedParam {
  name: string
  type: string
  required: boolean
  description?: string
  default?: any
}

const parameters = computed<ParsedParam[]>(() => {
  if (!props.inputSchema?.properties) return []

  const required = new Set(props.inputSchema.required || [])

  return Object.entries(props.inputSchema.properties).map(([name, schema]) => ({
    name,
    type: schema.type || 'any',
    required: required.has(name),
    description: schema.description,
    default: schema.default
  })).sort((a, b) => {
    // Required parameters first
    if (a.required !== b.required) return a.required ? -1 : 1
    return a.name.localeCompare(b.name)
  })
})
</script>
```

### Step 3: Update McpStatusPopup.vue

Update the tool details section to include parameters:

```vue
<div v-if="expandedTool === tool.name" class="tool-details">
  <div class="tool-full-name">
    <span class="label">Full name:</span>
    <code>mcp__{{ selectedServer }}__{{ tool.name }}</code>
  </div>

  <div class="tool-description">
    <span class="label">Description:</span>
    <p>{{ tool.description || 'No description' }}</p>
  </div>

  <McpToolParameters :input-schema="tool.inputSchema" />
</div>
```

## Summary

| Layer | File | Key Structure |
|-------|------|---------------|
| MCP Protocol | `mcp-types/src/lib.rs` | `Tool`, `ToolInputSchema` |
| CLI Protocol | `protocol/src/protocol.rs` | `McpListToolsResponseEvent` |
| Backend SDK | `types/Options.kt` | `McpToolInfo`, `McpToolsResponse` |
| Frontend | `McpStatusPopup.vue` | `McpToolInfo` interface |

**Data Already Available**:
- ✅ Backend returns `inputSchema` as `JsonElement`
- ✅ Data includes `properties`, `required`, `type`

**Frontend Needs**:
- ❌ Fix `inputSchema` type from `string` to `object`
- ❌ Add parameter display component
- ❌ Update tool details UI to show parameters
