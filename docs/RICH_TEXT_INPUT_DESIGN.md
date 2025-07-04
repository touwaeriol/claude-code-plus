# 富文本输入框实现方案

## 1. 概述

实现一个支持内联引用的富文本输入框，能够识别 `@file://` 格式并渲染为超链接样式。

## 2. 核心挑战

### 2.1 Compose 的限制
- `BasicTextField` 只支持纯文本，不支持富文本
- `Text` 组件支持 `AnnotatedString`，但是只读的
- 没有原生的富文本编辑器组件

### 2.2 需要解决的问题
1. 如何在输入框中实时渲染超链接样式
2. 如何处理光标位置和选择
3. 如何处理删除操作（整体删除引用）
4. 如何保持底层数据格式不变

## 3. 实现方案

### 方案A：自定义 Visual Transformation（推荐）

使用 `BasicTextField` 的 `visualTransformation` 参数来实现视觉转换。

```kotlin
@Composable
fun RichTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        visualTransformation = InlineReferenceVisualTransformation()
    )
}

class InlineReferenceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // 1. 解析 @file:// 引用
        val references = parseReferences(text.text)
        
        // 2. 构建转换后的文本和样式
        val builder = AnnotatedString.Builder()
        var lastIndex = 0
        
        references.forEach { ref ->
            // 添加普通文本
            builder.append(text.substring(lastIndex, ref.startIndex))
            
            // 添加超链接样式的文件名
            builder.withStyle(
                SpanStyle(
                    color = Color(0xFF007ACC),
                    textDecoration = TextDecoration.Underline
                )
            ) {
                builder.append("@${ref.fileName}")
            }
            
            lastIndex = ref.endIndex
        }
        
        // 添加剩余文本
        builder.append(text.substring(lastIndex))
        
        // 3. 创建偏移映射
        val offsetMapping = createOffsetMapping(text.text, builder.toString(), references)
        
        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}
```

**优点**：
- 保持底层数据不变
- 支持复杂的视觉转换
- 性能较好

**缺点**：
- 需要处理复杂的偏移映射
- 光标位置计算较复杂

### 方案B：双层渲染方案

在输入框上层叠加一个透明的渲染层。

```kotlin
@Composable
fun RichTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    Box {
        // 底层：不可见的输入框，处理实际输入
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
        
        // 上层：渲染富文本显示
        RichTextDisplay(
            text = value.text,
            cursorPosition = value.selection.start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RichTextDisplay(
    text: String,
    cursorPosition: Int,
    modifier: Modifier
) {
    val annotatedText = buildAnnotatedString {
        // 解析并渲染文本
        val references = parseReferences(text)
        // ... 构建带样式的文本
    }
    
    Text(
        text = annotatedText,
        modifier = modifier
    )
}
```

**优点**：
- 实现相对简单
- 渲染效果完全可控

**缺点**：
- 光标同步较困难
- 选择操作处理复杂

### 方案C：分段编辑方案

将输入框分成多个段，每个段独立处理。

```kotlin
@Composable
fun SegmentedRichInput(
    segments: List<TextSegment>,
    onSegmentsChange: (List<TextSegment>) -> Unit
) {
    Row {
        segments.forEach { segment ->
            when (segment) {
                is TextSegment.Plain -> PlainTextInput(segment, onSegmentsChange)
                is TextSegment.Reference -> ReferenceChip(segment, onSegmentsChange)
            }
        }
    }
}

sealed class TextSegment {
    data class Plain(val text: String) : TextSegment()
    data class Reference(val path: String, val fileName: String) : TextSegment()
}
```

**优点**：
- 每个引用是独立的UI组件
- 删除操作简单

**缺点**：
- 用户体验不如单一输入框
- 光标移动不自然

## 4. 推荐实现方案细节（方案A）

### 4.1 核心组件结构

```
RichTextInputField
├── BasicTextField (底层输入)
├── InlineReferenceVisualTransformation (视觉转换)
├── InlineReferenceParser (解析器)
└── OffsetMappingCalculator (偏移映射)
```

### 4.2 关键实现步骤

1. **解析引用**
   ```kotlin
   fun parseReferences(text: String): List<Reference> {
       val pattern = Regex("@file://([^\\s]+)")
       return pattern.findAll(text).map { match ->
           Reference(
               fullText = match.value,
               path = match.groupValues[1],
               fileName = match.groupValues[1].substringAfterLast('/'),
               startIndex = match.range.first,
               endIndex = match.range.last + 1
           )
       }.toList()
   }
   ```

2. **偏移映射**
   - 原始位置 → 显示位置
   - 显示位置 → 原始位置
   - 处理引用长度变化

3. **删除处理（整体删除机制）**
   
   删除引用时需要作为一个整体单元处理：
   
   ```kotlin
   fun handleDelete(
       value: TextFieldValue,
       isBackspace: Boolean
   ): TextFieldValue {
       val cursorPos = value.selection.start
       val text = value.text
       
       // 查找所有引用
       val references = parseReferences(text)
       
       if (isBackspace && cursorPos > 0) {
           // 检查光标前是否是引用的结尾
           val refToDelete = references.find { ref ->
               // 光标在引用后面
               cursorPos == ref.endIndex ||
               // 光标在显示文本的末尾（考虑visual transformation）
               cursorPos == ref.visualEndIndex
           }
           
           if (refToDelete != null) {
               // 删除整个引用
               val newText = text.substring(0, refToDelete.startIndex) + 
                             text.substring(refToDelete.endIndex)
               return TextFieldValue(
                   text = newText,
                   selection = TextRange(refToDelete.startIndex)
               )
           }
       }
       
       // Delete键的处理类似
       if (!isBackspace && cursorPos < text.length) {
           val refToDelete = references.find { ref ->
               cursorPos == ref.startIndex
           }
           
           if (refToDelete != null) {
               val newText = text.substring(0, refToDelete.startIndex) + 
                             text.substring(refToDelete.endIndex)
               return TextFieldValue(
                   text = newText,
                   selection = TextRange(refToDelete.startIndex)
               )
           }
       }
       
       // 正常删除单个字符
       return defaultDelete(value, isBackspace)
   }
   ```
   
   **关键点**：
   - 退格键：光标在引用后面时，删除整个引用
   - Delete键：光标在引用前面时，删除整个引用
   - 选择删除：如果选择范围包含引用，删除整个引用

4. **点击处理**
   - 检测点击位置
   - 判断是否点击在引用上
   - 触发文件打开操作

### 4.3 消息显示区域

使用现有的 `ClickableInlineText` 组件，只需要确保：

1. 解析逻辑一致
2. 样式一致
3. 点击处理一致

## 5. 实施计划

### 第一阶段：基础功能
1. 实现 `InlineReferenceVisualTransformation`
2. 实现基本的解析和渲染
3. 测试基本显示效果

### 第二阶段：交互优化
1. 实现偏移映射
2. 处理光标位置
3. 实现整体删除

### 第三阶段：完善功能
1. 添加点击处理
2. 优化性能
3. 处理边缘情况

### 4.4 整体删除的详细实现

#### 光标移动行为
```kotlin
fun handleCursorMovement(
    value: TextFieldValue,
    direction: Direction
): TextFieldValue {
    val cursorPos = value.selection.start
    val references = parseReferences(value.text)
    
    // 检查光标是否在引用内部
    val currentRef = references.find { ref ->
        cursorPos > ref.startIndex && cursorPos < ref.endIndex
    }
    
    if (currentRef != null) {
        // 光标在引用内部，跳到引用边界
        return when (direction) {
            Direction.LEFT -> TextFieldValue(
                text = value.text,
                selection = TextRange(currentRef.startIndex)
            )
            Direction.RIGHT -> TextFieldValue(
                text = value.text,
                selection = TextRange(currentRef.endIndex)
            )
        }
    }
    
    // 正常移动
    return defaultCursorMove(value, direction)
}
```

#### 选择范围处理
```kotlin
fun handleSelection(
    value: TextFieldValue,
    newSelection: TextRange
): TextFieldValue {
    val references = parseReferences(value.text)
    var adjustedStart = newSelection.start
    var adjustedEnd = newSelection.end
    
    // 如果选择范围部分包含引用，扩展到整个引用
    references.forEach { ref ->
        // 开始位置在引用内
        if (adjustedStart > ref.startIndex && adjustedStart < ref.endIndex) {
            adjustedStart = ref.startIndex
        }
        
        // 结束位置在引用内
        if (adjustedEnd > ref.startIndex && adjustedEnd < ref.endIndex) {
            adjustedEnd = ref.endIndex
        }
    }
    
    return value.copy(
        selection = TextRange(adjustedStart, adjustedEnd)
    )
}
```

#### 剪切/复制/粘贴处理
- **复制**：复制完整的 `@file://` 格式
- **剪切**：剪切完整的引用，不允许部分剪切
- **粘贴**：识别粘贴内容中的 `@file://` 格式并正确渲染

## 6. 测试要点

1. **功能测试**
   - 正确识别和渲染引用
   - 光标位置正确
   - 删除操作正确

2. **性能测试**
   - 大量文本时的渲染性能
   - 快速输入时的响应

3. **兼容性测试**
   - 不同输入法
   - 复制粘贴
   - 撤销重做

## 7. 风险和挑战

1. **技术风险**
   - Compose 的 `VisualTransformation` API 可能有限制
   - 偏移映射计算可能很复杂

2. **用户体验风险**
   - 光标跳动
   - 输入延迟

3. **缓解措施**
   - 先实现简单版本，逐步优化
   - 准备备选方案
   - 充分测试各种场景

## 8. 实现详情

### 8.1 实现原理

富文本输入系统通过 Compose 的 **Visual Transformation API** 实现，核心思想是：

1. **数据不变性**：底层数据始终保持 `@file://path/to/file.kt` 格式
2. **视觉转换**：仅在显示层将其渲染为蓝色超链接 `@file.kt`
3. **偏移映射**：精确处理原始文本和显示文本之间的光标位置对应关系

### 8.2 核心组件架构

```
富文本输入系统
├── InlineReferenceVisualTransformation  # 视觉转换器
│   ├── 解析引用格式
│   ├── 构建 AnnotatedString（带样式）
│   └── 创建偏移映射
├── InlineReferenceInputProcessor        # 输入处理器
│   ├── 整体删除处理
│   ├── 光标跳跃逻辑
│   └── 选择范围调整
└── ChatInputField                       # 集成组件
    ├── 应用 visualTransformation
    ├── 处理键盘事件
    └── 保持原有功能
```

### 8.3 实现代码示例

#### Visual Transformation 核心逻辑

```kotlin
class InlineReferenceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // 1. 解析引用：找到所有 @file://path 格式
        val references = parseInlineReferences(text.text)
        
        // 2. 构建转换后的文本
        val transformedText = buildAnnotatedString {
            references.forEach { ref ->
                // 普通文本保持不变
                append(normalText)
                
                // 引用文本应用超链接样式
                withStyle(SpanStyle(
                    color = Color(0xFF007ACC),      // 蓝色
                    textDecoration = TextDecoration.Underline
                )) {
                    append("@${ref.fileName}")     // 只显示文件名
                }
            }
        }
        
        // 3. 创建偏移映射（原始位置 ↔ 显示位置）
        val offsetMapping = createOffsetMapping(originalText, transformedText, references)
        
        return TransformedText(transformedText, offsetMapping)
    }
}
```

#### 整体删除处理

```kotlin
object InlineReferenceInputProcessor {
    fun handleDelete(value: TextFieldValue, isBackspace: Boolean): TextFieldValue? {
        val cursorPos = value.selection.start
        val references = findAllReferences(value.text)
        
        if (isBackspace && cursorPos > 0) {
            // 检查光标是否在引用的结尾
            val refToDelete = references.find { ref ->
                cursorPos == ref.endIndex  // 光标在引用后面
            }
            
            if (refToDelete != null) {
                // 删除整个引用
                val newText = value.text.removeRange(
                    refToDelete.startIndex, 
                    refToDelete.endIndex
                )
                return TextFieldValue(
                    text = newText,
                    selection = TextRange(refToDelete.startIndex)
                )
            }
        }
        
        return null  // 没有特殊处理，使用默认删除
    }
}
```

#### 集成到输入框

```kotlin
@Composable
fun ChatInputField(...) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        // 应用视觉转换
        visualTransformation = InlineReferenceVisualTransformation(),
        modifier = Modifier.onPreviewKeyEvent { keyEvent ->
            when {
                // 拦截删除键，实现整体删除
                keyEvent.key == Key.Backspace -> {
                    val processed = InlineReferenceInputProcessor.handleDelete(value, true)
                    if (processed != null) {
                        onValueChange(processed)
                        true  // 消费事件
                    } else {
                        false  // 使用默认处理
                    }
                }
                else -> false
            }
        }
    )
}
```

### 8.4 关键技术点

#### 偏移映射（Offset Mapping）

这是最复杂的部分，需要处理原始文本和显示文本长度不同的问题：

- 原始：`Hello @file://src/main.kt world` (29 字符)
- 显示：`Hello @main.kt world` (18 字符)

偏移映射确保：
- 光标在显示文本中的位置能正确映射到原始文本
- 反之亦然

#### 智能删除逻辑

检测光标位置是否在引用边界：
- 退格键：光标在引用末尾时，删除整个引用
- Delete键：光标在引用开头时，删除整个引用
- 光标在引用内部时，跳到边界而不是逐字符移动

#### 样式保持

Visual Transformation 只影响显示，不改变底层数据：
- 发送给 AI：`@file://src/main.kt`（完整路径）
- 显示给用户：`@main.kt`（蓝色超链接）
- 存储格式：`@file://src/main.kt`（保持原样）

### 8.5 使用效果

1. **输入体验**：用户选择文件后，自动插入 `@file://path `（带空格），但立即显示为蓝色 `@filename `
2. **智能分隔**：引用后自动添加空格，避免后续输入被误识别为引用的一部分
3. **编辑体验**：可以正常编辑超链接前后的文本，光标移动自然
4. **删除体验**：退格键可以整体删除一个文件引用，避免残留
5. **发送体验**：发送时保持完整路径格式，AI 能够正确理解

### 8.6 扩展性

系统使用枚举定义引用类型，新增支持只需：

```kotlin
enum class InlineReferenceScheme(val prefix: String, val displayName: String) {
    FILE("file://", "文件"),
    HTTPS("https://", "网页"),
    // 新增类型只需一行
    DATABASE("db://", "数据库")
}
```

所有解析、渲染、删除逻辑都会自动支持新类型。