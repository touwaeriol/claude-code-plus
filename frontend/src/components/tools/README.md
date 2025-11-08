# 工具显示组件

本目录包含所有 Claude 工具的专业化显示组件。

## 已实现的组件

### 文件操作工具
- **ReadToolDisplay.vue** - 文件读取工具
- **EditToolDisplay.vue** - 文件编辑工具
- **MultiEditToolDisplay.vue** - 批量编辑工具
- **WriteToolDisplay.vue** - 文件写入工具

### 搜索工具
- **GrepToolDisplay.vue** - 内容搜索工具
- **GlobToolDisplay.vue** - 文件模式匹配工具

### 执行工具
- **BashToolDisplay.vue** - Shell 命令执行工具

### 任务管理
- **TodoWriteDisplay.vue** - 待办事项管理工具

### 网络工具 (最新)
- **WebSearchToolDisplay.vue** - 网络搜索工具
- **WebFetchToolDisplay.vue** - 网页抓取工具

## WebSearch 工具

### 功能特性
- 搜索查询显示
- 搜索结果列表(卡片式布局)
- 结果标题、URL、摘要显示
- 点击打开链接(新标签页)
- 允许/禁止域名标签
- 结果位置标记
- 展开/折叠功能

### 数据结构
```typescript
interface WebSearchTool {
  query: string
  allowed_domains?: string[]
  blocked_domains?: string[]
}

interface SearchResult {
  title?: string
  url: string
  snippet?: string
  position?: number
}
```

### 使用示例
```vue
<WebSearchToolDisplay
  :toolUse="toolUseBlock"
  :result="toolResultBlock"
/>
```

### 样式特点
- 主色调: 蓝色 (#0366d6)
- 结果卡片 hover 高亮效果
- 域名标签颜色区分(允许绿色,禁止红色)

## WebFetch 工具

### 功能特性
- 目标 URL 显示(带域名提取)
- HTTP 状态码显示(带颜色标记)
- 内容预览(前 2000 字符)
- 内容格式化(Markdown/JSON/Text)
- 查看完整内容功能
- 复制内容功能
- 内容大小显示
- Prompt 显示

### 数据结构
```typescript
interface WebFetchTool {
  url: string
  prompt: string
}

interface WebFetchResult {
  status_code?: number
  content_type?: string
  content: string
  timestamp?: number
}
```

### 使用示例
```vue
<WebFetchToolDisplay
  :toolUse="toolUseBlock"
  :result="toolResultBlock"
/>
```

### 样式特点
- 主色调: 绿色 (#28a745)
- 状态码颜色标记:
  - 2xx: 绿色(成功)
  - 3xx: 黄色(重定向)
  - 4xx/5xx: 红色(错误)
- Markdown/JSON 内容格式化显示
- 内容截断和懒加载

## 组件注册

在 `MessageDisplay.vue` 中注册:

```typescript
import WebSearchToolDisplay from '@/components/tools/WebSearchToolDisplay.vue'
import WebFetchToolDisplay from '@/components/tools/WebFetchToolDisplay.vue'

function getToolComponent(toolName: string): Component {
  const componentMap: Record<string, Component> = {
    // ... 其他工具
    'WebSearch': WebSearchToolDisplay,
    'WebFetch': WebFetchToolDisplay
  }
  return componentMap[toolName] || ReadToolDisplay
}
```

## 开发规范

### 组件结构
1. **Props 定义**: 统一使用 `toolUse` 和 `result` 两个 props
2. **展开状态**: 使用 `expanded` ref 管理展开/折叠
3. **数据解析**: 在 computed 中解析工具参数和结果
4. **样式规范**:
   - 使用 scoped 样式
   - 统一的颜色变量
   - 主题适配(亮色/暗色)

### 命名规范
- 组件文件: `{ToolName}ToolDisplay.vue`
- 样式类: `.{toolname}-tool`
- 主色调类: `.tool-name`

### 安全注意事项
- 外部链接使用 `noopener,noreferrer`
- URL 验证后再打开
- HTML 内容转义
- JSON 安全解析

## 测试建议

1. **功能测试**
   - 工具参数正确显示
   - 结果内容正确解析
   - 交互功能正常(展开/折叠/复制/打开链接)

2. **边界测试**
   - 空结果
   - 缺少字段
   - 特殊字符
   - 超长内容

3. **性能测试**
   - 大量结果渲染
   - 长内容滚动
   - 内存占用

4. **主题测试**
   - 亮色主题样式
   - 暗色主题样式
   - 颜色对比度

## 后续计划

- [ ] 添加虚拟滚动优化(大量结果时)
- [ ] 添加搜索结果高亮
- [ ] 支持搜索结果过滤
- [ ] 添加内容语法高亮
- [ ] 支持更多内容格式(PDF, 图片等)
- [ ] 添加错误状态显示
- [ ] 添加加载状态动画
