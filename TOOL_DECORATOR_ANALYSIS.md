# Claude Agent SDK 中的 `tool()` 装饰器实现分析

## 📍 源代码位置
- **文件**: `external/claude-agent-sdk-python/src/claude_agent_sdk/__init__.py`
- **行号**: 71-131

## 🏗️ 核心架构

### 1. 数据结构：`SdkMcpTool`

```python
@dataclass
class SdkMcpTool(Generic[T]):
    """Definition for an SDK MCP tool."""
    
    name: str                                      # 工具唯一标识
    description: str                               # 工具描述
    input_schema: type[T] | dict[str, Any]        # 输入参数模式
    handler: Callable[[T], Awaitable[dict[str, Any]]]  # 异步处理函数
```

**设计特点**：
- ✅ **泛型支持** (`Generic[T]`): 为输入参数提供类型安全
- ✅ **灵活的 Schema**: 支持类型字典、TypedDict 或完整的 JSON Schema
- ✅ **异步处理**: 所有工具处理函数必须是 async

---

### 2. 装饰器函数：`tool()`

```python
def tool(
    name: str, 
    description: str, 
    input_schema: type | dict[str, Any]
) -> Callable[[Callable[[Any], Awaitable[dict[str, Any]]]], SdkMcpTool[Any]]:
    """Decorator for defining MCP tools with type safety."""
    
    def decorator(
        handler: Callable[[Any], Awaitable[dict[str, Any]]],
    ) -> SdkMcpTool[Any]:
        return SdkMcpTool(
            name=name,
            description=description,
            input_schema=input_schema,
            handler=handler,
        )
    
    return decorator
```

**实现原理**：
1. **高阶函数**: `tool()` 返回一个装饰器函数
2. **参数捕获**: 装饰器捕获 `name`、`description`、`input_schema`
3. **函数包装**: 装饰器将处理函数包装成 `SdkMcpTool` 实例
4. **类型安全**: 通过类型提示确保编译时类型检查

---

## 💡 使用示例

### 基础用法

```python
@tool("greet", "Greet a user", {"name": str})
async def greet(args):
    return {"content": [{"type": "text", "text": f"Hello, {args['name']}!"}]}
```

### 多参数工具

```python
@tool("add", "Add two numbers", {"a": float, "b": float})
async def add_numbers(args):
    result = args["a"] + args["b"]
    return {"content": [{"type": "text", "text": f"Result: {result}"}]}
```

### 错误处理

```python
@tool("divide", "Divide two numbers", {"a": float, "b": float})
async def divide(args):
    if args["b"] == 0:
        return {
            "content": [{"type": "text", "text": "Error: Division by zero"}],
            "is_error": True
        }
    return {"content": [{"type": "text", "text": f"Result: {args['a'] / args['b']}"}]}
```

---

## 🔄 Schema 转换流程

在 `create_sdk_mcp_server()` 中，简单的类型映射被转换为标准的 JSON Schema：

```python
# 输入: {"a": float, "b": float}
# 输出:
{
    "type": "object",
    "properties": {
        "a": {"type": "number"},
        "b": {"type": "number"}
    },
    "required": ["a", "b"]
}
```

**支持的类型映射**：
| Python 类型 | JSON Schema 类型 |
|-----------|-----------------|
| `str` | `"string"` |
| `int` | `"integer"` |
| `float` | `"number"` |
| `bool` | `"boolean"` |
| 其他 | `"string"` (默认) |

---

## 🎯 工具注册流程

### 1. 定义工具
```python
@tool("add", "Add numbers", {"a": float, "b": float})
async def add(args):
    return {"content": [{"type": "text", "text": f"Sum: {args['a'] + args['b']}"}]}
```

### 2. 创建 MCP 服务器
```python
calculator = create_sdk_mcp_server(
    name="calculator",
    version="2.0.0",
    tools=[add]  # 传入工具列表
)
```

### 3. 在 Claude Agent 中使用
```python
options = ClaudeAgentOptions(
    mcp_servers={"calc": calculator},
    allowed_tools=["add"]
)
```

---

## [object Object]CP 服务器集成

在 `create_sdk_mcp_server()` 中：

### 工具列表处理 (`list_tools`)
```python
@server.list_tools()
async def list_tools() -> list[Tool]:
    tool_list = []
    for tool_def in tools:
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

### 工具调用处理 (`call_tool`)
```python
@server.call_tool()
async def call_tool(name: str, arguments: dict[str, Any]) -> Any:
    if name not in tool_map:
        raise ValueError(f"Tool '{name}' not found")
    
    tool_def = tool_map[name]
    # 调用处理函数
    result = await tool_def.handler(arguments)
    
    # 转换结果为 MCP 格式
    content = []
    for item in result["content"]:
        if item.get("type") == "text":
            content.append(TextContent(type="text", text=item["text"]))
    
    return content
```

---

## ✨ 关键特性

### 1. 类型安全
- ✅ 泛型支持 (`Generic[T]`)
- ✅ 类型提示完整
- ✅ 编译时类型检查

### 2. 灵活的 Schema 定义
- ✅ 简单类型映射: `{"name": str}`
- ✅ TypedDict: 复杂结构
- ✅ 完整 JSON Schema: 高级验证

### 3. 异步优先
- ✅ 所有处理函数都是 async
- ✅ 支持并发执行
- ✅ 无阻塞 I/O

### 4. 错误处理
- ✅ 通过 `is_error` 标志表示错误
- ✅ 返回错误消息
- ✅ 不中断工作流

### 5. 应用状态访问
- ✅ 工具可直接访问应用变量
- ✅ 无需 IPC 开销
- ✅ 同进程执行

---

## 📊 与外部 MCP 服务器的对比

| 特性 | SDK MCP | 外部 MCP |
|-----|--------|---------|
| **执行位置** | 同进程 | 独立进程 |
| **性能** | 高 (无 IPC) | 较低 (IPC 开销) |
| **部署** | 简单 (单进程) | 复杂 (多进程) |
| **调试** | 容易 | 困难 |
| **状态访问** | 直接 | 受限 |
| **隔离性** | 低 | 高 |

---

## 🚀 最佳实践

### 1. 命名规范
```python
@tool("list_files", "List files in directory", {"path": str})
async def list_files(args):
    # 使用清晰的名称和描述
    pass
```

### 2. 错误处理
```python
@tool("read_file", "Read file content", {"path": str})
async def read_file(args):
    try:
        # 实现逻辑
        return {"content": [{"type": "text", "text": content}]}
    except Exception as e:
        return {
            "content": [{"type": "text", "text": f"Error: {str(e)}"}],
            "is_error": True
        }
```

### 3. 返回格式
```python
# 标准返回格式
{
    "content": [
        {"type": "text", "text": "result"},
        {"type": "image", "data": "...", "mimeType": "image/png"}
    ],
    "is_error": False  # 可选
}
```

### 4. Schema 定义
```python
# 简单参数
@tool("greet", "Greet", {"name": str})

# 多个参数
@tool("calc", "Calculate", {"a": float, "b": float, "op": str})

# 复杂 Schema
@tool("query", "Query", {
    "type": "object",
    "properties": {
        "query": {"type": "string"},
        "limit": {"type": "integer", "minimum": 1}
    },
    "required": ["query"]
})
```

---

## 📝 总结

Claude Agent SDK 的 `tool()` 装饰器是一个**简洁而强大**的设计：

1. **简洁**: 只需 3 个参数 + 1 个异步函数
2. **类型安全**: 完整的类型提示和泛型支持
3. **灵活**: 支持多种 Schema 定义方式
4. **高效**: 同进程执行，无 IPC 开销
5. **易用**: 直观的 API，易于学习和使用

这个设计为开发者提供了一个**低学习成本、高生产力**的工具定义方式。

