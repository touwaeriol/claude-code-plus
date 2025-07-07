# 上下文消息格式设计文档

## 概述

本文档详细说明 Claude Code Plus 中上下文消息格式的设计理念、实现方案和使用方式。

## 背景和需求

### 问题描述

在原始设计中，存在两种不同的上下文添加方式：
1. **Add Context 按钮**：添加的上下文显示为输入框上方的标签
2. **@ 符号触发**：添加的上下文内联显示在输入框文本中

根据用户反馈，现在采用以下区分方式：
- **Add Context 按钮**：添加的上下文创建标签并在消息开头以 Markdown 引用块格式显示
- **@ 符号触发**：添加的上下文仅内联插入到输入框文本中，不创建标签，格式如 `@filename.ext`

### 解决方案

提供两种不同的引用体验：
- **正式上下文**：通过 Add Context 按钮添加，显示为标签，在消息头部以引用块格式显示
- **快速引用**：通过 @ 符号添加，仅在消息文本中显示，不创建标签或在消息头部显示
- **智能显示**：@ 符号引用在消息中显示简短文件名，但发送时包含完整路径信息
- **混合使用**：支持在同一消息中混合使用两种方式
- **用户控制**：用户可以根据需要选择合适的引用方式

## 技术实现

### INLINE 类型上下文设计

#### 需求分析

对于 @ 符号触发的上下文引用，我们需要实现：
1. **用户友好的显示**：在消息中显示简短的文件名，如 `@ContextSelectorTestApp.kt`
2. **完整的路径信息**：发送时包含完整路径信息，确保 AI 能准确定位文件
3. **无标签管理**：不创建上下文标签，保持输入区域简洁
4. **智能解析**：支持从显示文本中提取完整路径信息

#### 实现方案

##### 1. 显示与存储分离

```kotlin
data class InlineFileReference(
    val displayName: String,    // 显示名称：ContextSelectorTestApp.kt
    val fullPath: String,       // 完整路径：desktop/src/main/kotlin/com/claudecodeplus/test/ContextSelectorTestApp.kt
    val relativePath: String    // 相对路径：src/main/kotlin/com/claudecodeplus/test/ContextSelectorTestApp.kt
)
```

##### 2. 消息构建流程

```
输入: @Context...
  ↓
选择文件: /full/path/to/ContextSelectorTestApp.kt
  ↓
插入文本: @ContextSelectorTestApp.kt
  ↓
存储映射: @ContextSelectorTestApp.kt → /full/path/to/ContextSelectorTestApp.kt
  ↓
发送时展开: @/full/path/to/ContextSelectorTestApp.kt
```

##### 3. 文本处理

```kotlin
// 消息发送前的路径展开
fun expandInlineReferences(message: String, referenceMap: Map<String, String>): String {
    val pattern = "@([\\w.-]+)".toRegex()
    return pattern.replace(message) { matchResult ->
        val displayName = matchResult.groupValues[1]
        val fullPath = referenceMap["@$displayName"]
        if (fullPath != null) "@$fullPath" else matchResult.value
    }
}
```

### 数据模型

#### ContextReference 基类

```kotlin
sealed class ContextReference {
    abstract val displayType: ContextDisplayType
}

enum class ContextDisplayType {
    TAG,     // 显示为标签，在消息头部以引用块格式显示
    INLINE   // 仅内联显示在消息文本中，不创建标签，格式如 @filename.ext
}
```

#### 具体类型

```kotlin
data class FileReference(
    val path: String,
    val fullPath: String = path,
    override val displayType: ContextDisplayType = ContextDisplayType.TAG
) : ContextReference()

data class WebReference(
    val url: String,
    val title: String? = null,
    override val displayType: ContextDisplayType = ContextDisplayType.TAG
) : ContextReference()

// ... 其他类型
```

### UI 组件实现

#### 布局结构

```
输入容器
├── 工具栏行
│   ├── Add Context 按钮 (左侧)
│   └── 上下文标签列表 (右侧，水平滚动)
└── 输入框区域
```

#### 关键代码

```kotlin
// 顶部工具栏：Add Context按钮（左）+ 上下文标签（右）
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    // Add Context 按钮
    AddContextButton(
        onClick = { showContextSelector = true }
    )
    
    // 上下文标签显示 - 在同一行右侧
    if (contexts.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(contexts) { context ->
                ContextTag(
                    context = context,
                    onRemove = { onContextRemove(context) }
                )
            }
        }
    }
}
```

### 消息构建

#### buildFinalMessage 函数

```kotlin
private fun buildFinalMessage(contexts: List<ContextReference>, userMessage: String): String {
    // 所有的上下文都是TAG类型（Add Context按钮添加的）
    // @符号添加的上下文不会进入contexts列表，直接在userMessage中
    
    if (contexts.isEmpty()) {
        return userMessage
    }
    
    val contextSection = buildString {
        appendLine("> **上下文资料**")
        appendLine("> ")
        
        contexts.forEach { context ->
            val contextLine = when (context) {
                is ContextReference.FileReference -> {
                    "> - 📄 `${context.path}`"
                }
                is ContextReference.WebReference -> {
                    val title = context.title?.let { " ($it)" } ?: ""
                    "> - 🌐 ${context.url}$title"
                }
                // ... 其他类型处理
            }
            appendLine(contextLine)
        }
        
        appendLine()
    }
    
    return contextSection + userMessage
}
```

## 消息格式规范

### Markdown 格式

```markdown
> **上下文资料**
> 
> - 📄 `src/main/kotlin/Main.kt`
> - 🌐 https://example.com (网页标题)
> - 📁 `src/main` (25个文件)

用户的实际消息内容...
```

### 图标规范

| 上下文类型 | 图标 | 说明 |
|-----------|------|------|
| FileReference | 📄 | 文件 |
| WebReference | 🌐 | 网页 |
| FolderReference | 📁 | 文件夹 |
| SymbolReference | 🔗 | 代码符号 |
| TerminalReference | 💻 | 终端输出 |
| ProblemsReference | ⚠️ | 问题报告 |
| GitReference | 🔀 | Git 操作 |
| SelectionReference | ✏️ | 选择内容 |
| WorkspaceReference | 🏠 | 工作区 |

### 格式化规则

1. **统一前缀**：所有上下文行都以 `> - ` 开头
2. **图标使用**：每种类型使用固定的 emoji 图标
3. **信息层次**：主要信息在前，补充信息在括号中
4. **文件路径**：使用反引号包围，提高可读性

## 使用场景

### 场景1：代码分析

```markdown
> **上下文资料**
> 
> - 📄 `build.gradle.kts`
> - 📄 `src/main/kotlin/Main.kt`
> - 📄 `src/main/kotlin/service/UserService.kt`

请分析这个项目的架构设计，重点关注依赖管理和服务层的实现。
```

### 场景2：问题排查

```markdown
> **上下文资料**
> 
> - 💻 终端输出 (150行) ⚠️
> - ⚠️ 问题报告 (5个) [ERROR]
> - 📄 `application.properties`

应用启动失败，请帮我分析错误原因并提供解决方案。
```

### 场景3：功能开发

```markdown
> **上下文资料**
> 
> - 🌐 https://docs.spring.io/spring-boot/docs/current/reference/html/web.html (Spring Boot Web)
> - 📁 `src/main/kotlin/controller` (8个文件)
> - 🔗 `UserController.createUser()` (FUNCTION) - UserController.kt:45

需要在现有的用户管理系统中添加批量导入功能，参考 Spring Boot 文档和现有的控制器实现。
```

### 场景4：混合使用

```markdown
> **上下文资料**
> 
> - 📄 `build.gradle.kts`
> - 📄 `src/main/resources/application.yml`

请帮我分析 @UserService.kt 和 @DatabaseConfig.kt 中的配置是否一致，特别是数据库连接相关的设置。
```

**显示效果**：
- 用户看到：`@UserService.kt` 和 `@DatabaseConfig.kt`
- 实际发送：`@src/main/kotlin/service/UserService.kt` 和 `@src/main/kotlin/config/DatabaseConfig.kt`

在这个例子中：
- `build.gradle.kts` 和 `application.yml` 是通过 Add Context 按钮添加的（显示为标签，在消息头部）
- `@UserService.kt` 和 `@DatabaseConfig.kt` 是通过@符号输入的（显示简短文件名，发送完整路径）

## 优势和特点

### 1. 用户体验统一
- 所有上下文都以相同方式显示和管理
- 避免了混合显示方式的困惑
- 符合 Cursor 等主流工具的交互模式

### 2. 技术实现简洁
- 统一的数据流处理
- 简化的组件状态管理
- 减少了条件判断逻辑

### 3. 日志和调试友好
- 清晰的格式标记：`> **上下文资料**`
- 易于正则表达式解析
- 便于统计和分析

### 4. Markdown 原生支持
- 引用块在各种 Markdown 渲染器中都能正确显示
- 保持良好的可读性
- 支持语法高亮和格式化

### 5. 可扩展性强
- 新的上下文类型只需添加对应的图标和格式化规则
- 支持复杂的上下文信息展示
- 便于未来功能扩展

## 解析和处理

### 正则表达式

```regex
^> \*\*上下文资料\*\*\n(?:> \n)?((?:> - .+\n)+)\n
```

### 解析示例 (JavaScript)

```javascript
function parseContextMessage(message) {
    const contextRegex = /^> \*\*上下文资料\*\*\n(?:> \n)?((?:> - .+\n)+)\n/;
    const match = message.match(contextRegex);
    
    if (!match) {
        return {
            hasContext: false,
            contexts: [],
            userMessage: message
        };
    }
    
    const contextSection = match[1];
    const contextLines = contextSection
        .split('\n')
        .filter(line => line.startsWith('> - '))
        .map(line => line.substring(4)); // 移除 "> - " 前缀
    
    const userMessage = message.substring(match[0].length);
    
    return {
        hasContext: true,
        contexts: contextLines,
        userMessage: userMessage
    };
}
```

### 使用示例

```javascript
const message = `> **上下文资料**
> 
> - 📄 \`Main.kt\`
> - 🌐 https://example.com

请分析这个文件`;

const parsed = parseContextMessage(message);
console.log(parsed);
// {
//   hasContext: true,
//   contexts: ['📄 `Main.kt`', '🌐 https://example.com'],
//   userMessage: '请分析这个文件'
// }
```

## 最佳实践

### 1. 上下文选择
- 优先选择最相关的文件和资源
- 避免添加过多上下文，影响 AI 处理效率
- 根据问题类型选择合适的上下文类型

### 2. 文件路径显示
- 优先显示相对路径，提高可读性
- 对于深层嵌套，可以显示关键路径部分
- 在悬停提示中显示完整路径

### 3. 错误处理
- 对于无效的上下文引用，显示警告标记
- 提供移除失效上下文的快捷操作
- 在发送前验证上下文的有效性

### 4. 性能优化
- 使用 LazyRow 支持大量上下文标签
- 实现上下文预览和延迟加载
- 合理控制上下文数量的上限

## 未来扩展

### 1. 智能上下文推荐
- 基于当前对话内容推荐相关文件
- 学习用户的上下文使用习惯
- 自动检测代码中的依赖关系

### 2. 上下文分组
- 支持将相关上下文分组管理
- 一键添加常用的上下文组合
- 项目级别的上下文模板

### 3. 高级格式化
- 支持上下文的自定义显示名称
- 添加上下文的优先级标记
- 支持条件性上下文（根据模型选择）

### 4. 导出和分享
- 导出包含上下文的对话记录
- 分享上下文配置给团队成员
- 上下文使用情况的统计报告

## 实现状态

### ✅ 已完成功能

#### 1. 核心数据模型
- [x] `InlineFileReference` 数据类
- [x] `InlineReferenceManager` 管理器
- [x] 路径展开和映射功能

#### 2. UI 组件集成
- [x] `EnhancedSmartInputArea` 支持内联引用
- [x] `JewelChatApp` 主聊天应用集成
- [x] `JewelChatPanel` Swing 包装器集成
- [x] `JewelConversationView` 对话视图集成

#### 3. 消息处理流程
- [x] 显示与发送分离逻辑
- [x] 自动路径展开功能
- [x] 发送后自动清理机制

#### 4. 测试和验证
- [x] 多功能测试环境
- [x] 聊天功能完整性验证
- [x] 上下文功能专项测试

### 🎯 功能特点

#### 智能显示
- **用户友好**：显示简短文件名如 `@ContextSelectorTestApp.kt`
- **技术完整**：发送完整路径如 `@src/main/kotlin/com/claudecodeplus/test/ContextSelectorTestApp.kt`
- **无感知切换**：用户体验流畅，技术处理透明

#### 双重上下文支持
- **正式上下文**：Add Context 按钮 → 标签显示 → 消息头部引用块
- **快速引用**：@ 符号触发 → 内联显示 → 仅在消息文本中

#### 完整集成
- **toolwindow 主模块**：所有功能完全集成
- **IntelliJ IDEA 插件**：可直接使用新功能
- **向后兼容**：保持所有原有功能

### 📋 使用指南

#### 开发者使用
```kotlin
// 创建内联引用管理器
val inlineReferenceManager = InlineReferenceManager()

// 添加到组件
EnhancedSmartInputArea(
    // ... 其他参数
    inlineReferenceManager = inlineReferenceManager
)

// 发送时展开引用
val expandedMessage = inlineReferenceManager.expandInlineReferences(userMessage)
```

#### 用户使用
1. **正式上下文**：点击 📎 Add Context → 选择文件 → 显示为标签
2. **快速引用**：输入 `@` → 选择文件 → 插入 `@filename.ext`

### 🔄 版本更新

#### v1.0.0 - 基础上下文功能
- TAG 类型上下文支持
- 基础消息格式

#### v1.1.0 - 内联引用功能
- INLINE 类型上下文支持
- 内联引用管理器
- 智能路径展开
- 双重上下文支持

## 总结

通过智能的上下文消息格式设计，我们实现了：

1. **用户体验优化**：简短显示 + 完整路径的最佳平衡
2. **功能灵活性**：正式上下文 + 快速引用的双重选择
3. **技术架构清晰**：组件解耦，数据流简洁
4. **开发友好**：易于扩展，便于维护

这种设计为 Claude Code Plus 提供了一个现代化、智能化的上下文管理系统，大大提升了用户的编程体验。 