# tool() 装饰器与 MCP 服务器的关系

## 核心答案：是的！

**`tool()` 装饰器本质上就是通过创建 MCP 服务器来实现的。**

---

## 🔍 完整实现链路

### 1️⃣ tool() 装饰器只是"数据容器"

```python
@tool("add", "Add numbers", {"a": float, "b": float})
async def add(args):
    return {"content": [{"type": "text", "text": f"Sum: {args['a'] + args['b']}"}]}
```

**这一步做了什么？**
- ✅ 创建了一个 `SdkMcpTool` 实例
- ✅ 保存了工具的元数据（name, description, schema）
- ✅ 保存了处理函数的引用（handler）
- ❌ **并没有创建任何服务器或注册任何东西**

---

### 2️⃣ create_sdk_mcp_server() 才是真正的"实现者"

```python
calculator = create_sdk_mcp_server(
    name="calculator",
    tools=[add, multiply]  # 传入 tool() 装饰的函数
)
```

**这一步才是关键！**

#### 步骤 A: 创建 MCP Server 实例

```python
from mcp.server import Server

server = Server(name, version=version)
```

这里使用的是 **官方 MCP Python SDK** 的 `Server` 类。

---

#### 步骤 B: 注册 MCP 协议处理器

##### B1. 注册 `list_tools` 处理器

```python
@server.list_tools()
async def list_tools() -> list[Tool]:
    """当 Claude 请求工具列表时被调用"""
    tool_list = []
    for tool_def in tools:  # 遍历所有 @tool 装饰的函数
        # 转换 input_schema 为 JSON Schema
        schema = convert_to_json_schema(tool_def.input_schema)
        
        tool_list.append(
            Tool(
                name=tool_def.name,
                description=tool_def.description,
                inputSchema=schema,
            )
        )
    return tool_list
```

**作用**: 响应 MCP 协议的 `tools/list` 请求

---

##### B2. 注册 `call_tool` 处理器

```python
@server.call_tool()
async def call_tool(name: str, arguments: dict[str, Any]) -> Any:
    """当 Claude 调用工具时被调用"""
    # 1. 从 tool_map 中查找工具
    tool_def = tool_map[name]
    
    # 2. 调用用户定义的 handler
    result = await tool_def.handler(arguments)
    
    # 3. 转换返回值为 MCP 格式
    content = []
    for item in result["content"]:
        if item.get("type") == "text":
            content.append(TextContent(type="text", text=item["text"]))
    
    return content
```

**作用**: 响应 MCP 协议的 `tools/call` 请求

---

#### 步骤 C: 返回服务器配置

```python
return McpSdkServerConfig(
    type="sdk",           # 标记为 SDK 内置服务器
    name=name,            # 服务器名称
    instance=server       # MCP Server 实例
)
```

---

### 3️⃣ 集成到 Claude Agent

```python
options = ClaudeAgentOptions(
    mcp_servers={"calc": calculator},  # 注册 MCP 服务器
    allowed_tools=["add", "multiply"]  # 允许使用的工具
)

result = await query("What is 1 + 2?", options=options)
```

---

## 📊 对比：SDK MCP 服务器 vs 外部 MCP 服务器

| 特性 | SDK MCP (tool() 方式) | 外部 MCP 服务器 |
|-----|---------------------|----------------|
| **实现方式** | `create_sdk_mcp_server()` | 独立进程 + stdio/SSE 通信 |
| **MCP 协议** | ✅ 完整实现 | ✅ 完整实现 |
| **运行位置** | 同进程 | 独立进程 |
| **通信方式** | 函数调用 | IPC (stdio/HTTP) |
| **性能** | 高 (无序列化) | 较低 (需序列化) |
| **部署** | 单进程 | 多进程 |
| **调试** | 容易 | 困难 |
| **隔离性** | 低 | 高 |

---

## 🎯 关键理解

### tool() 装饰器的本质

```
tool() 装饰器
    ↓
SdkMcpTool 实例 (数据容器)
    ↓
create_sdk_mcp_server() (创建 MCP Server)
    ↓
注册 @server.list_tools() 和 @server.call_tool()
    ↓
返回 McpSdkServerConfig
    ↓
集成到 ClaudeAgentOptions.mcp_servers
    ↓
Claude 通过 MCP 协议调用工具
```

---

## 💡 为什么要这样设计？

### 1. 统一接口

无论是 SDK 内置工具还是外部 MCP 服务器，Claude 都通过 **相同的 MCP 协议** 调用：

```python
# SDK 内置工具
sdk_server = create_sdk_mcp_server("calc", tools=[add])

# 外部 MCP 服务器
external_server = {
    "command": "python",
    "args": ["-m", "my_mcp_server"]
}

# 两者使用方式完全相同
options = ClaudeAgentOptions(
    mcp_servers={
        "calc": sdk_server,      # SDK MCP
        "external": external_server  # 外部 MCP
    }
)
```

---

### 2. 性能优化

SDK MCP 服务器运行在同一进程中，避免了：
- ❌ 进程间通信 (IPC) 开销
- ❌ JSON 序列化/反序列化开销
- ❌ 进程启动时间

---

### 3. 简化开发

开发者只需：
1. 用 `@tool` 装饰函数
2. 调用 `create_sdk_mcp_server()`
3. 传入 `ClaudeAgentOptions`

**不需要**：
- ❌ 编写独立的 MCP 服务器进程
- ❌ 处理 stdio/SSE 通信
- ❌ 管理进程生命周期

---

## 🔬 源码证据

### 关键导入

```python
from mcp.server import Server
from mcp.types import ImageContent, TextContent, Tool
```

这些都是 **官方 MCP Python SDK** 的类型和类。

### 关键代码

```python
# 第 210 行: 创建 MCP Server
server = Server(name, version=version)

# 第 218 行: 注册 list_tools 处理器
@server.list_tools()
async def list_tools() -> list[Tool]:
    ...

# 第 264 行: 注册 call_tool 处理器
@server.call_tool()
async def call_tool(name: str, arguments: dict[str, Any]) -> Any:
    ...
```

---

## 📝 总结

**tool() 装饰器 = 语法糖 + MCP 服务器包装器**

1. `@tool` 装饰器：创建工具定义（SdkMcpTool）
2. `create_sdk_mcp_server()`：
   - 创建真正的 MCP Server 实例
   - 注册 MCP 协议处理器（list_tools, call_tool）
   - 将工具定义转换为 MCP 工具
3. Claude Agent：通过标准 MCP 协议调用工具

**所以答案是：是的，tool() 最终是通过创建 MCP 服务器来实现的！**

