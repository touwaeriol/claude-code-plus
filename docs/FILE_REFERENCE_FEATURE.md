# 文件引用功能 (@符号补全)

## 功能概述

Claude Code Plus 插件支持使用 `@` 符号快速引用项目中的文件。这个功能类似于 GitHub Copilot Chat 和 Cursor 的文件引用功能。

## 使用方法

1. **触发补全**：
   - @ 符号前面必须有空格（或者是输入框的开头）
   - @ 符号后面没有字符或只有空格时才会触发
   - 例如：`请分析 @` 或 `@ `

2. **搜索文件**：
   - 输入 `@` 后会显示项目中的文件列表
   - 继续输入文件名进行过滤
   - 支持模糊搜索

3. **选择文件**：
   - 使用上下箭头选择文件
   - 按 Enter 或 Tab 确认选择
   - 文件的**完整路径**会自动插入到输入框

## 示例

```
用户输入: 请帮我分析 @MyClass
选择文件后: 请帮我分析 @/Users/username/project/src/main/kotlin/MyClass.kt 中的代码
```

注意：
- @ 符号前必须有空格，例如 `email@domain.com` 中的 @ 不会触发补全
- 选择文件后会插入完整的绝对路径，而不是相对路径

## 搜索优先级

1. **精确匹配**：完全匹配文件名
2. **前缀匹配**：文件名以输入内容开头
3. **包含匹配**：文件名包含输入内容
4. **路径匹配**：相对路径包含输入内容

## 支持的文件类型

- Kotlin (`.kt`)
- Java (`.java`)
- XML (`.xml`)
- JSON (`.json`)
- Markdown (`.md`)
- Python (`.py`)
- JavaScript (`.js`)
- TypeScript (`.ts`)
- HTML (`.html`)
- CSS (`.css`)
- YAML (`.yml`, `.yaml`)
- Properties (`.properties`)
- Gradle (`.gradle`, `.gradle.kts`)
- 其他文本文件

## 技术实现

### 核心组件

1. **FileReferenceEditorField**
   - 自定义的编辑器字段
   - 继承自 `LanguageTextField`
   - 处理 @ 符号检测和补全触发

2. **FileSearchUtil**
   - 文件搜索工具类
   - 使用 IntelliJ 的 `FilenameIndex` API
   - 智能排序和过滤

3. **FileReferenceCompletionContributor**
   - 补全贡献者
   - 注册到 IntelliJ 的补全系统
   - 提供文件列表和插入处理

### 搜索算法

```kotlin
// 1. 获取项目基础路径（考虑内容根）
val basePath = getProjectBasePath(project)

// 2. 搜索匹配的文件
val results = searchProjectFiles(project, query, limit = 30)

// 3. 按相关性排序
results.sortedBy { 
    matchScore,           // 匹配分数
    pathDepth,           // 路径深度
    relativePath         // 相对路径
}
```

### 文件路径处理

- 使用相对路径显示（相对于项目根目录）
- 处理单文件项目（内容根是文件的情况）
- 支持多模块项目

## 限制和注意事项

1. **搜索限制**：默认显示最多 30 个结果
2. **性能考虑**：大型项目可能有轻微延迟
3. **路径格式**：使用正斜杠 `/` 作为路径分隔符
4. **文件过滤**：不显示隐藏文件和二进制文件

## 未来改进

1. **文件预览**：悬停显示文件内容预览
2. **最近文件**：优先显示最近编辑的文件
3. **文件夹支持**：支持引用整个文件夹
4. **多文件选择**：支持一次引用多个文件
5. **文件内容搜索**：不仅搜索文件名，还搜索内容

## 故障排查

### 问题：输入 @ 后没有显示补全

**解决方案**：
1. 确保项目已正确加载
2. 检查是否有项目文件
3. 查看 IDEA 日志是否有错误

### 问题：找不到某些文件

**解决方案**：
1. 确认文件在项目范围内
2. 检查文件类型是否被支持
3. 尝试更精确的搜索词

### 问题：路径显示不正确

**解决方案**：
1. 检查项目路径设置
2. 查看内容根配置
3. 确认使用了正确的相对路径