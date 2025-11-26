# Context7 使用规范

## 概述

**通过 Context7 获取文档是规范的正确使用方式。**

Context7 MCP 是一个为 LLM 提供最新、版本特定的文档和代码示例的服务。它直接从源代码获取文档，防止过时信息或幻觉响应。

### 为什么使用 Context7 是规范做法？

1. **准确性保证**: 直接从源代码获取，确保文档的准确性和时效性
2. **版本控制**: 支持特定版本的文档查询，避免版本不匹配问题
3. **标准化接口**: 提供统一的 MCP 标准接口，符合行业规范
4. **防止幻觉**: 避免 LLM 基于过时或错误信息生成响应
5. **可追溯性**: 文档来源清晰，便于验证和追溯
6. **性能优化**: 结构化的文档检索，比网络搜索更高效

**重要提示**: 在需要获取库文档时，应优先使用 Context7 MCP 工具，而不是依赖网络搜索或 LLM 的训练数据。

## 核心功能

Context7 MCP 提供两个主要工具：

1. **resolve-library-id**: 根据库名搜索并解析为 Context7 兼容的库 ID
2. **get-library-docs**: 根据库 ID 获取文档内容，支持主题过滤和令牌限制

## 规范使用说明

### ⚠️ 重要：这是唯一规范的使用方式

**通过 Context7 MCP 获取文档是规范、正确的使用方式。**

所有需要获取库文档的场景，都应该：
1. ✅ 使用 `resolve-library-id` 工具解析库名
2. ✅ 使用 `get-library-docs` 工具获取文档
3. ❌ **不要**使用网络搜索
4. ❌ **不要**依赖 LLM 训练数据中的信息
5. ❌ **不要**直接猜测库 ID

### 规范流程示例

```typescript
// ✅ 规范方式：通过 Context7 获取
// 步骤 1: 解析库 ID
const libraryInfo = await resolveLibraryId('next.js');
// 返回: { id: '/vercel/next.js', ... }

// 步骤 2: 获取文档
const docs = await getLibraryDocs({
  context7CompatibleLibraryID: libraryInfo.id,
  topic: 'routing'
});

// ❌ 错误方式：直接使用网络搜索或猜测
// 不要这样做！
```

### 为什么这是规范？

| 特性 | Context7 MCP | 其他方式 |
|------|-------------|---------|
| 准确性 | ✅ 直接从源代码获取 | ❌ 可能过时或错误 |
| 版本控制 | ✅ 支持特定版本 | ❌ 版本不明确 |
| 标准化 | ✅ MCP 标准接口 | ❌ 非标准化 |
| 可追溯 | ✅ 来源清晰 | ❌ 来源不明 |
| 性能 | ✅ 结构化检索 | ❌ 效率较低 |

## 配置方式

### 1. VS Code 配置

#### 本地服务器（推荐）
```json
{
  "mcp": {
    "servers": {
      "context7": {
        "type": "stdio",
        "command": "npx",
        "args": ["-y", "@upstash/context7-mcp", "--api-key", "YOUR_API_KEY"]
      }
    }
  }
}
```

#### 使用环境变量
```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp"],
      "env": {
        "CONTEXT7_API_KEY": "YOUR_API_KEY"
      }
    }
  }
}
```

### 2. Zed 配置

```json
{
  "context_servers": {
    "Context7": {
      "source": "custom",
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp", "--api-key", "YOUR_API_KEY"]
    }
  }
}
```

### 3. Amp 配置

#### 基础配置（无需 API Key）
```sh
amp mcp add context7 https://mcp.context7.com/mcp
```

#### 带 API Key 配置
```sh
amp mcp add context7 --header "CONTEXT7_API_KEY=YOUR_API_KEY" https://mcp.context7.com/mcp
```

### 4. Augment Code 配置

```json
{
  "augment.advanced": {
    "mcpServers": [
      {
        "name": "context7",
        "command": "npx",
        "args": ["-y", "@upstash/context7-mcp", "--api-key", "YOUR_API_KEY"]
      }
    ]
  }
}
```

### 5. Gemini CLI 配置（远程服务器）

```json
{
  "mcpServers": {
    "context7": {
      "httpUrl": "https://mcp.context7.com/mcp",
      "headers": {
        "CONTEXT7_API_KEY": "YOUR_API_KEY",
        "Accept": "application/json, text/event-stream"
      }
    }
  }
}
```

### 6. Windows CMD 配置（解决超时问题）

```toml
[mcp_servers.context7]
command = "cmd"
args = [
    "/c",
    "npx",
    "-y",
    "@upstash/context7-mcp",
    "--api-key",
    "YOUR_API_KEY"
]
env = { SystemRoot="C:\\Windows" }
startup_timeout_ms = 20000
```

## API 使用示例

### 规范使用流程

**标准流程（必须遵循）**:
1. 使用 `resolve-library-id` 解析库名 → 获取 Context7 兼容的库 ID
2. 使用 `get-library-docs` 获取文档 → 基于正确的库 ID

### 1. 解析库 ID

**这是规范的第一步，必须执行。**

在使用 `get-library-docs` 之前，**必须**先使用 `resolve-library-id` 来获取正确的库 ID。不要直接猜测或使用不完整的库 ID。

**示例：搜索 "next.js"**
```typescript
// 返回结果示例
{
  results: [
    {
      id: '/vercel/next.js',
      title: 'Next.js',
      description: 'The React Framework for Production',
      branch: 'canary',
      lastUpdateDate: '2025-01-20T15:30:00Z',
      state: 'finalized',
      totalTokens: 150000,
      totalSnippets: 1250,
      totalPages: 180,
      trustScore: 10,
      versions: ['v14.3.0-canary.87', 'v14.2.0', 'v13.5.0']
    }
  ]
}
```

### 2. 获取库文档

**这是规范的第二步，基于第一步获取的正确库 ID。**

**基本用法（规范方式）**
```typescript
// 获取 Next.js 的文档
const docs = await getLibraryDocs({
  context7CompatibleLibraryID: '/vercel/next.js'
});
```

**带主题过滤**
```typescript
// 只获取路由相关的文档
const docs = await getLibraryDocs({
  context7CompatibleLibraryID: '/vercel/next.js',
  topic: 'routing',
  tokens: 5000
});
```

**指定版本**
```typescript
// 获取特定版本的文档
const docs = await getLibraryDocs({
  context7CompatibleLibraryID: '/vercel/next.js/v14.3.0-canary.87',
  topic: 'app router'
});
```

## API 参数说明

### resolve-library-id

- **libraryName** (string, 必需): 要搜索的库名称

**返回字段说明：**
- `id`: Context7 兼容的库 ID（格式：`/org/project` 或 `/org/project/version`）
- `title`: 库的标题
- `description`: 库的描述
- `totalSnippets`: 可用代码片段数量
- `trustScore`: 信任评分（0-10）
- `versions`: 可用版本列表

### get-library-docs

- **context7CompatibleLibraryID** (string, 必需): Context7 兼容的库 ID
- **topic** (string, 可选): 主题过滤（如 'routing', 'hooks'）
- **page** (integer, 可选): 页码（1-10，默认 1）

**返回内容：**
- 格式化的文档文本
- 代码示例
- API 参考

## 错误处理

### 常见错误

1. **404 Not Found**: 库不存在或未完成文档化
   ```
   "The library you are trying to access does not exist. Please try with a different library ID."
   ```

2. **429 Too Many Requests**: 请求过于频繁
   ```
   "Rate limited due to too many requests. Please try again later."
   ```

3. **401 Unauthorized**: API Key 无效
   ```
   "Unauthorized. Please check your API key. API keys should start with 'ctx7sk'"
   ```

## API Key 获取

API Key 格式：`ctx7sk_...`

使用 API Key 的好处：
- 更高的速率限制
- 访问私有仓库
- 更好的性能

## 最佳实践

### 规范使用流程

**标准流程（必须遵循）**:
1. **优先使用 Context7**: 当需要获取任何库的文档时，必须通过 Context7 MCP 工具获取
2. **先解析后获取**: 总是先使用 `resolve-library-id` 获取正确的库 ID，再使用 `get-library-docs`
3. **使用主题过滤**: 当只需要特定主题的文档时，使用 `topic` 参数提高效率
4. **指定版本**: 如果需要特定版本的文档，在库 ID 中包含版本号
5. **错误处理**: 始终处理可能的错误响应（404、429、401）
6. **缓存结果**: 对于频繁查询的库，考虑缓存解析后的库 ID

### 使用原则

✅ **正确做法**:
- 通过 `resolve-library-id` 搜索库
- 通过 `get-library-docs` 获取文档
- 使用 API Key 提高速率限制
- 指定版本号获取特定版本文档

❌ **错误做法**:
- 直接使用网络搜索获取文档
- 依赖 LLM 训练数据中的过时信息
- 跳过 `resolve-library-id` 直接猜测库 ID
- 忽略错误处理和版本管理

## 库 ID 格式

- 基本格式: `/org/project`
- 带版本: `/org/project/version`
- 示例:
  - `/vercel/next.js`
  - `/vercel/next.js/v14.3.0-canary.87`
  - `/mongodb/docs`
  - `/supabase/supabase`

## 支持的库类型

Context7 支持多种类型的库和文档：
- 前端框架（React, Next.js, Vue 等）
- 后端框架（Express, FastAPI 等）
- 数据库（MongoDB, Supabase 等）
- 工具库（各种 npm、pip 包等）
- 官方文档站点

## 更多信息

- Context7 官网: https://context7.com
- MCP 服务器包: `@upstash/context7-mcp`
- 远程服务器: `https://mcp.context7.com/mcp`

