# Claude Code SDK Kotlin

A Kotlin implementation of the Claude Code SDK, providing a type-safe client for interacting with Claude CLI.



## Overview

This SDK provides a `ClaudeCodeSdkClient` for bidirectional, interactive conversations with Claude Code. It supports all the features of the Python SDK including:

- **Streaming communication** with Claude CLI
- **Hook callbacks** for tool usage monitoring
- **Permission management** for tool access control
- **Control protocol** for interrupts and session management
- **Type-safe API** using Kotlin's type system

## Architecture

The SDK is organized into several layers:

```
ClaudeCodeSdkClient (Public API)
    ↓
ControlProtocol (Message routing & dynamic callbacks)
    ↓
Transport (SubprocessTransport - bidirectional JSON)
    ↓
Claude CLI Process (主导者)
```

### 重要：消息流程机制

**Claude CLI 主导的初始化流程**：
1. SDK 启动 Claude CLI 进程 (`--input-format stream-json --output-format stream-json`)
2. Claude CLI 自动发送 `{"type": "system", "subtype": "init"}` 消息
3. SDK 接收初始化信息（session_id, tools, mcp_servers等）
4. 开始双向消息交换

**动态权限和Hook处理**：
- Hook和权限回调不是预注册的，而是当Claude CLI需要时发送`control_request`
- SDK收到`control_request`时实时调用对应的Kotlin回调函数
- SDK通过`control_response`返回结果

这种设计确保了与官方Python SDK完全一致的行为。详细的技术说明请参考 [`docs/IMPLEMENTATION_GUIDE.md`](docs/IMPLEMENTATION_GUIDE.md)。

## Key Components

### Data Models (`types/`)
- **Messages**: UserMessage, AssistantMessage, SystemMessage, ResultMessage
- **ContentBlocks**: TextBlock, ThinkingBlock, ToolUseBlock, ToolResultBlock
- **Permissions**: Permission callbacks and update mechanisms
- **Hooks**: Hook events and callback management
- **Options**: Configuration for CLI interaction

### Transport Layer (`transport/`)
- **Transport**: Abstract interface for CLI communication
- **SubprocessTransport**: Implementation using ProcessBuilder

### Protocol Layer (`protocol/`)
- **ControlProtocol**: Manages bidirectional control messages
- **MessageParser**: Converts JSON to typed objects

## Usage

### Basic Usage

```kotlin
import com.claudecodeplus.sdk.*
import com.claudecodeplus.sdk.types.*

val options = ClaudeCodeOptions(
    model = "claude-3-5-sonnet",
    allowedTools = listOf("Read", "Write", "Bash")
)

val client = ClaudeCodeSdkClient(options)

client.use {
    query("Hello Claude!")
    
    receiveResponse().collect { message ->
        when (message) {
            is AssistantMessage -> {
                message.content.filterIsInstance<TextBlock>().forEach {
                    println("Claude: ${it.text}")
                }
            }
            is ResultMessage -> println("Done!")
        }
    }
}
```

### Hook Configuration

```kotlin
val options = ClaudeCodeOptions(
    hooks = mapOf(
        HookEvent.PRE_TOOL_USE to listOf(
            HookMatcher(
                matcher = "Bash",
                hooks = listOf { input, toolUseId, context ->
                    val command = input["command"] as? String
                    if (command?.contains("rm -rf") == true) {
                        HookJSONOutput(decision = "block")
                    } else {
                        HookJSONOutput()
                    }
                }
            )
        )
    )
)
```

### Permission Callbacks

```kotlin
val options = ClaudeCodeOptions(
    canUseTool = { toolName, input, context ->
        when (toolName) {
            "Write" -> {
                val path = input["file_path"] as? String
                if (path?.contains("/etc/") == true) {
                    PermissionResultDeny("Cannot write to system directories")
                } else {
                    PermissionResultAllow()
                }
            }
            else -> PermissionResultAllow()
        }
    }
)
```

### Convenience Functions

```kotlin
// Simple one-shot query
val messages = claudeQuery("What is 2 + 2?")

// Builder pattern
val client = claudeCodeSdkClient {
    model = "claude-3-5-sonnet"
    allowedTools = listOf("Read", "Write")
    permissionMode = PermissionMode.ACCEPT_EDITS
}
```

## Integration with IntelliJ Plugin

This SDK is designed to be used within the Claude Code Plus IntelliJ plugin. To add it as a dependency:

```kotlin
// In your module's build.gradle.kts
dependencies {
    implementation(project(":claude-code-sdk"))
}
```

## Requirements

- Kotlin 1.9+
- Claude CLI installed and accessible in PATH
- JDK 17+

## Design Principles

1. **Type Safety**: Leverage Kotlin's type system for compile-time safety
2. **Coroutines**: Use Kotlin coroutines for natural async programming
3. **Flow**: Use Flow for reactive stream processing
4. **Immutability**: Prefer immutable data structures
5. **Resource Management**: Automatic cleanup with `use` functions

## Implementation Notes

- **JSON Parsing**: Uses kotlinx.serialization for type-safe JSON handling
- **Process Management**: Handles Claude CLI subprocess lifecycle
- **Error Handling**: Comprehensive exception hierarchy
- **Memory Management**: Efficient message streaming without buffering
- **Thread Safety**: Safe for concurrent use

This implementation maintains compatibility with the Python SDK's API while providing idiomatic Kotlin interfaces and leveraging Kotlin's unique features for a better developer experience.