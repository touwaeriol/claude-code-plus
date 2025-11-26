# Claude Code SDK Kotlin - 正确实现指南

## 重要发现和修正

基于对Claude CLI实际行为的深入测试，我们发现之前的实现有一些关键的误解。本文档记录了**正确的工作机制**。

## ❌ 之前的错误理解

### 1. 主动初始化请求
```kotlin
// ❌ 错误：我们错误地认为需要主动发送InitializeRequest
val initRequest = InitializeRequest(hooks = hooksConfig)
val response = sendControlRequest(initRequest)
```

### 2. 命令行参数传递权限配置
```kotlin
// ❌ 错误：使用了不存在的参数
command.addAll(listOf("--permission-prompt-tool", "stdio"))
```

### 3. Hook和工具预注册
我们错误地认为需要在启动时将所有hooks和自定义工具注册给Claude CLI。

## ✅ 正确的工作机制

### 1. Claude CLI 主导的初始化流程

**实际流程**：
```
1. 启动Claude CLI (--input-format stream-json --output-format stream-json)
2. Claude CLI 自动发送系统初始化消息
3. SDK 接收并解析 system init 消息
4. 开始正常的消息交换
```

**真实的初始化消息**：
```json
{
  "type": "system",
  "subtype": "init",
  "cwd": "/Users/erio/codes/idea/claude-code-plus",
  "session_id": "714183bb-9bf5-49ed-82b1-f6fc56670026",
  "tools": ["Task", "Bash", "Read", "Write", ...],
  "mcp_servers": [
    {"name": "gradle-class-finder-mcp", "status": "connected"},
    ...
  ],
  "model": "claude-opus-4-1-20250805",
  "permissionMode": "default",
  "apiKeySource": "none"
}
```

### 2. 动态权限和Hook处理

**权限请求流程**：
```
1. Claude需要工具权限时
2. Claude CLI 发送 control_request (permission_request)
3. SDK 调用 canUseTool 回调
4. SDK 发送 control_response 
```

**Hook回调流程**：
```
1. Claude执行工具时触发hook事件
2. Claude CLI 发送 control_request (hook_callback)  
3. SDK 调用相应的hook函数
4. SDK 发送 control_response with hook结果
```

### 3. 正确的CLI参数

**存在的权限相关参数**：
```bash
--permission-mode <mode>           # acceptEdits, bypassPermissions, default, plan
--dangerously-skip-permissions     # 跳过所有权限检查
```

**不存在的参数**：
```bash
--permission-prompt-tool stdio     # ❌ 这个参数不存在！
```

## 📋 正确的实现步骤

### 1. 启动和连接

```kotlin
suspend fun connect() {
    // 启动Claude CLI进程
    actualTransport!!.connect()
    
    // 启动消息处理协程
    controlProtocol!!.startMessageProcessing(clientScope!!)
    
    // 等待接收system init消息（而不是主动发送请求）
    serverInfo = waitForSystemInit()
}
```

### 2. 消息路由机制

```kotlin
private suspend fun routeMessage(jsonElement: JsonElement) {
    val jsonObject = jsonElement.jsonObject
    val type = jsonObject["type"]?.jsonPrimitive?.content
    
    when (type) {
        "system" -> {
            val subtype = jsonObject["subtype"]?.jsonPrimitive?.content
            if (subtype == "init") {
                handleSystemInit(jsonElement)
            }
        }
        "control_request" -> {
            handleControlRequest(jsonElement)
        }
        "assistant", "user", "result" -> {
            // 常规SDK消息，转发给用户
            val message = messageParser.parseMessage(jsonElement)
            _sdkMessages.send(message)
        }
    }
}
```

### 3. Hook和权限的实时处理

```kotlin
private suspend fun handleControlRequest(jsonElement: JsonElement) {
    val (requestId, request) = messageParser.parseControlRequest(jsonElement)
    
    val response = when (request) {
        is PermissionRequest -> {
            // 实时调用权限回调
            val result = options.canUseTool?.invoke(
                request.toolName, 
                request.input, 
                ToolPermissionContext()
            )
            convertPermissionResult(result)
        }
        is HookCallbackRequest -> {
            // 实时调用hook回调
            val callback = registeredHooks[request.callbackId]
            val result = callback?.invoke(
                request.input,
                request.toolUseId,
                HookContext()
            )
            Json.encodeToJsonElement(result)
        }
    }
    
    sendControlResponse(requestId, "success", response)
}
```

## 🔄 完整的消息流程图

```
Claude CLI 进程                    Kotlin SDK
     |                                |
     |---> {"type":"system","subtype":"init"}
     |                                |---> 解析并保存服务器信息
     |                                |
     |<--- {"type":"user","message":...}
     |                                |---> 处理用户消息
     |                                |
     |---> {"type":"assistant","message":...}
     |                                |---> 转发给用户应用
     |                                |
     |---> {"type":"control_request","request":{"subtype":"permission_request",...}}
     |                                |---> 调用canUseTool回调
     |<--- {"type":"control_response","response":{"subtype":"success",...}}
     |                                |
     |---> {"type":"control_request","request":{"subtype":"hook_callback",...}}
     |                                |---> 调用hook函数
     |<--- {"type":"control_response","response":{"subtype":"success",...}}
     |                                |
     |---> {"type":"result","subtype":"success"}
     |                                |---> 转发结果给用户
```

## 📚 与官方Python SDK的对比

### Python SDK实现要点
1. **等待初始化**：Python SDK启动后等待Claude CLI发送init消息
2. **动态回调**：权限和hook都是在收到control_request时才被调用
3. **无预注册**：不需要在启动时告诉Claude CLI有哪些hook或权限回调

### Kotlin SDK对应实现
1. **相同的等待机制**：我们也需要等待system init消息
2. **相同的动态响应**：control_request → 调用回调 → control_response
3. **相同的简洁启动**：只启动CLI，不传递hook/权限配置

## 🛠️ 修复计划

### 1. 移除错误的初始化
- 删除主动的`sendControlRequest(InitializeRequest)`
- 实现`waitForSystemInit()`来接收Claude CLI的初始化消息

### 2. 修复CLI参数
- 移除`--permission-prompt-tool stdio`
- 确保使用正确的stream-json参数

### 3. 完善控制协议
- 确保control_request能正确路由到对应的回调
- 实现正确的control_response格式

### 4. 测试验证
- 测试权限回调的实际工作
- 测试hook回调的实际工作
- 验证与官方Python SDK的行为一致性

## 🔍 关键发现总结

1. **Claude CLI是主导者**：它决定何时发送什么消息，SDK是响应者
2. **协议是动态的**：权限和hook都是按需处理，不是预配置的
3. **参数要准确**：必须使用实际存在的CLI参数
4. **流程要正确**：等待初始化消息，而不是主动发送

这些发现彻底改变了我们对Claude Code SDK工作机制的理解，并且解释了为什么之前的测试会超时失败。