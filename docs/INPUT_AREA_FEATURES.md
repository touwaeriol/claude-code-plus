# 输入区域增强功能文档

## 概述

输入区域 (InputArea) 是用户与 Claude 交互的主要界面。Phase 2 完成后，输入区域支持以下高级功能:

## 核心功能

### 1. 上下文引用系统 (Context References)

用户可以在消息中引用文件、文件夹、URL 或代码片段，为 Claude 提供额外上下文。

#### 支持的引用类型

```typescript
export interface ContextReference {
  type: 'file' | 'folder' | 'url' | 'code'
  name: string        // 显示名称
  path: string        // 完整路径
  content?: string    // 文件内容（可选）
  lineStart?: number  // 起始行号（可选）
  lineEnd?: number    // 结束行号（可选）
}
```

#### UI 展示

引用显示为"芯片"（chips）样式:
- **文件引用**: 📄 filename.ext
- **带行号**: 📄 filename.ext:42 或 📄 filename.ext:10-50
- **文件夹引用**: 📁 folder-name
- **URL 引用**: 🔗 url
- **代码片段**: 💻 snippet

#### 使用方式

1. **@ 提及**:
   - 输入 `@` 触发文件搜索建议
   - 上下键导航，Enter 选择
   - 自动添加到引用列表

2. **拖放文件**:
   - 从 IDE 项目树拖放文件到输入区
   - 自动读取文件内容
   - 添加到引用列表

3. **点击添加**:
   - 点击"添加文件"按钮
   - 通过浏览器文件选择器选择文件

4. **移除引用**:
   - 点击引用芯片右侧的 × 按钮

### 2. @ 提及建议系统

#### 触发机制

输入 `@` 字符后，自动触发文件搜索:

```vue
<template>
  <div class="mention-suggestions">
    <div v-for="suggestion in filteredSuggestions"
         class="suggestion-item">
      <span class="suggestion-icon">{{ getIcon(type) }}</span>
      <div class="suggestion-content">
        <div class="suggestion-name">{{ name }}</div>
        <div class="suggestion-path">{{ path }}</div>
      </div>
    </div>
  </div>
</template>
```

#### 交互

- **键盘导航**:
  - ↓ / ↑: 选择建议
  - Enter: 确认选择
  - Esc: 关闭建议

- **鼠标操作**:
  - 点击选择建议

#### 后端 API

```typescript
// 前端调用
const response = await ideService.searchFiles(query, maxResults)

// 后端实现 (Kotlin)
private fun handleSearchFiles(request: FrontendRequest): FrontendResponse {
  val query = request.data["query"]
  val maxResults = request.data["maxResults"] ?: 20

  // 递归搜索项目文件
  val files = searchFilesRecursive(project.baseDir, query, maxResults)

  return FrontendResponse(
    success = true,
    data = mapOf("files" to files)
  )
}
```

### 3. 文件拖放支持

#### 功能特性

1. **拖放区域提示**:
   ```vue
   <div v-if="isDragging" class="drop-zone-overlay">
     <div class="drop-zone-content">
       <span class="drop-icon">📁</span>
       <span class="drop-text">释放文件以添加到上下文</span>
     </div>
   </div>
   ```

2. **支持多文件**:
   - 同时拖放多个文件
   - 批量添加到引用列表

3. **自动内容读取**:
   ```typescript
   async function addFileReference(file: File) {
     const content = await readFileContent(file)
     addReference({
       type: 'file',
       name: file.name,
       path: file.name,
       content: content
     })
   }
   ```

### 4. 多行编辑增强

#### 键盘快捷键

- **Ctrl+Enter**: 发送消息
- **Shift+Enter**: 插入换行（不发送）
- **↑/↓**: @ 提及导航
- **Esc**: 关闭建议

#### 自适应高度

```css
.input-textarea {
  min-height: 100px;
  max-height: 300px;
  resize: vertical;
}
```

### 5. 消息构建

#### 引用内容注入

发送消息时，引用内容会自动注入到消息文本中:

```typescript
async function handleSendMessage(message: string, references: ContextReference[]) {
  const userMessage: Message = {
    role: 'user',
    content: [{
      type: 'text',
      text: message
    }]
  }

  // 添加引用上下文
  if (references.length > 0) {
    const refContext = references.map(ref => {
      if (ref.content) {
        return `\n\n@${ref.name}:\n\`\`\`\n${ref.content}\n\`\`\``
      } else {
        return `\n@${ref.name}: ${ref.path}`
      }
    }).join('\n')

    userMessage.content[0].text = message + refContext
  }

  await claudeService.query(userMessage.content[0].text)
}
```

#### 示例消息

用户输入:
```
请帮我审查这个文件的代码

@App.vue
@InputArea.vue
```

实际发送给 Claude:
```markdown
请帮我审查这个文件的代码

@App.vue:
\`\`\`vue
<template>
  ...
</template>
...
\`\`\`

@InputArea.vue:
\`\`\`vue
<template>
  ...
</template>
...
\`\`\`
```

## API 接口

### 前端 API

#### ideService.searchFiles()

```typescript
/**
 * 搜索项目文件
 * @param query - 搜索关键词
 * @param maxResults - 最大结果数（默认 20）
 * @returns 文件列表
 */
async searchFiles(query: string, maxResults?: number): Promise<FrontendResponse>

// 响应格式
{
  success: true,
  data: {
    files: [
      { name: 'App.vue', path: 'src/App.vue', isDirectory: false },
      { name: 'InputArea.vue', path: 'src/components/input/InputArea.vue', isDirectory: false }
    ]
  }
}
```

#### ideService.getFileContent()

```typescript
/**
 * 获取文件内容
 * @param filePath - 文件路径
 * @param lineStart - 起始行号（可选）
 * @param lineEnd - 结束行号（可选）
 * @returns 文件内容
 */
async getFileContent(
  filePath: string,
  lineStart?: number,
  lineEnd?: number
): Promise<FrontendResponse>

// 响应格式
{
  success: true,
  data: {
    content: "文件内容..."
  }
}
```

### 后端 API (Kotlin)

#### handleSearchFiles

```kotlin
private fun handleSearchFiles(request: FrontendRequest): FrontendResponse {
  val query = request.data["query"]?.toString() ?: return error("Missing query")
  val maxResults = request.data["maxResults"]?.toString()?.toIntOrNull() ?: 20

  val files = mutableListOf<Map<String, JsonElement>>()
  ApplicationManager.getApplication().runReadAction {
    val baseDir = project.baseDir ?: return@runReadAction
    searchFilesRecursive(baseDir, query, files, maxResults)
  }

  return FrontendResponse(
    success = true,
    data = mapOf("files" to JsonArray(files.map { JsonObject(it) }))
  )
}
```

#### searchFilesRecursive

```kotlin
private fun searchFilesRecursive(
  dir: VirtualFile,
  query: String,
  results: MutableList<Map<String, JsonElement>>,
  maxResults: Int
) {
  if (results.size >= maxResults) return

  dir.children?.forEach { file ->
    val name = file.name

    // 匹配文件名
    if (name.contains(query, ignoreCase = true)) {
      results.add(mapOf(
        "name" to JsonPrimitive(name),
        "path" to JsonPrimitive(file.path),
        "isDirectory" to JsonPrimitive(file.isDirectory)
      ))
    }

    // 递归搜索子目录（排除 .git, node_modules 等）
    if (file.isDirectory && !name.startsWith(".") && name != "node_modules") {
      searchFilesRecursive(file, query, results, maxResults)
    }
  }
}
```

#### handleGetFileContent

```kotlin
private fun handleGetFileContent(request: FrontendRequest): FrontendResponse {
  val filePath = request.data["filePath"]?.toString() ?: return error("Missing filePath")
  val lineStart = request.data["lineStart"]?.toString()?.toIntOrNull()
  val lineEnd = request.data["lineEnd"]?.toString()?.toIntOrNull()

  var content: String? = null

  ApplicationManager.getApplication().runReadAction {
    val file = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
      ?: LocalFileSystem.getInstance().findFileByPath(filePath)

    if (file != null && !file.isDirectory) {
      val fullContent = String(file.contentsToByteArray(), Charsets.UTF_8)

      content = if (lineStart != null) {
        val lines = fullContent.lines()
        val start = (lineStart - 1).coerceAtLeast(0)
        val end = (lineEnd ?: lineStart).coerceAtMost(lines.size)
        lines.subList(start, end).joinToString("\n")
      } else {
        fullContent
      }
    }
  }

  return if (content != null) {
    FrontendResponse(success = true, data = mapOf("content" to JsonPrimitive(content)))
  } else {
    FrontendResponse(false, error = "File not found: $filePath")
  }
}
```

## 主题适配

### 亮色主题

```css
.context-references {
  background: #ffffff;
  border: 1px solid #e1e4e8;
}

.reference-chip {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  color: #24292e;
}

.reference-file {
  border-color: #0366d6;
  background: #f1f8ff;
}

.drop-zone-overlay {
  background: rgba(3, 102, 214, 0.1);
  border: 2px dashed #0366d6;
}
```

### 暗色主题

```css
.theme-dark .context-references {
  background: #1e1e1e;
  border-color: #444d56;
}

.theme-dark .reference-chip {
  background: #2d333b;
  border-color: #444d56;
  color: #e1e4e8;
}

.theme-dark .drop-zone-overlay {
  background: rgba(88, 166, 255, 0.1);
  border: 2px dashed #58a6ff;
}
```

## 性能优化

### 1. 文件搜索防抖

```typescript
import { debounce } from 'lodash-es'

const debouncedFetchSuggestions = debounce(fetchSuggestions, 300)

function handleInput(event: Event) {
  // ... 检测 @ 输入
  debouncedFetchSuggestions(mentionQuery.value)
}
```

### 2. 虚拟列表（大量建议时）

```vue
<template>
  <div class="mention-suggestions">
    <virtual-list
      :items="filteredSuggestions"
      :item-height="48"
      :max-height="200"
    >
      <template #default="{ item }">
        <SuggestionItem :suggestion="item" />
      </template>
    </virtual-list>
  </div>
</template>
```

### 3. 内容读取限制

```kotlin
// 限制文件大小（避免读取超大文件）
if (file.length > 1_000_000) { // 1MB
  return FrontendResponse(false, error = "File too large")
}
```

## 后续增强计划

### Phase 3 改进

1. **符号搜索**: 支持搜索函数、类、变量等代码符号
2. **最近文件**: 显示最近打开的文件建议
3. **智能排序**: 根据相关性、频率排序建议
4. **预览面板**: 鼠标悬停显示文件内容预览
5. **批量操作**: 一次性添加/移除多个引用

### 技术债务

1. 文件搜索性能优化（使用 IDE 索引）
2. 支持更多引用类型（代码符号、文档章节）
3. 引用持久化（保存到会话历史）
4. 跨平台文件路径处理

## 测试用例

### 单元测试

```typescript
describe('InputArea', () => {
  it('should trigger mention suggestions on @ input', async () => {
    const wrapper = mount(InputArea)
    await wrapper.find('textarea').setValue('Hello @')
    expect(wrapper.find('.mention-suggestions').exists()).toBe(true)
  })

  it('should add file reference on suggestion select', async () => {
    const wrapper = mount(InputArea)
    // ... 触发建议并选择
    expect(wrapper.vm.contextReferences).toHaveLength(1)
  })

  it('should handle file drop', async () => {
    const wrapper = mount(InputArea)
    const file = new File(['content'], 'test.txt')
    await wrapper.trigger('drop', { dataTransfer: { files: [file] } })
    expect(wrapper.vm.contextReferences).toHaveLength(1)
  })
})
```

### 集成测试

```kotlin
class FrontendBridgeTest {
  @Test
  fun testSearchFiles() {
    val request = FrontendRequest(
      action = "ide.searchFiles",
      data = mapOf("query" to "App", "maxResults" to 10)
    )
    val response = bridge.handleRequest(request)
    assertTrue(response.success)
    assertNotNull(response.data["files"])
  }

  @Test
  fun testGetFileContent() {
    val request = FrontendRequest(
      action = "ide.getFileContent",
      data = mapOf("filePath" to "src/App.vue")
    )
    val response = bridge.handleRequest(request)
    assertTrue(response.success)
    assertNotNull(response.data["content"])
  }
}
```

## 总结

输入区域增强功能为用户提供了强大的上下文管理能力:

✅ **上下文引用系统** - 文件、文件夹、URL、代码片段
✅ **@ 提及搜索** - 实时文件搜索和建议
✅ **拖放支持** - 从 IDE 直接拖放文件
✅ **多行编辑** - 完善的键盘快捷键
✅ **主题适配** - 亮色/暗色主题支持
✅ **后端集成** - 完整的文件搜索和内容读取 API

这些功能显著提升了用户与 Claude 交互的效率和体验。
