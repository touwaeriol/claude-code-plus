# ChatInputArea 组件设计文档

## 概述

`ChatInputArea` 是 Claude Code Plus 插件的核心输入组件，封装了所有与用户输入相关的功能。该组件采用现代化的设计理念，参考 Cursor 编辑器的界面风格，提供了统一、完整的用户交互体验。

## 设计目标

### 1. 功能完整性
- 将输入框、发送按钮、模型选择器、上下文选择等功能统一封装
- 避免组件功能在界面更新时丢失
- 确保所有输入相关功能的一致性

### 2. 用户体验
- 现代化的界面设计，符合主流聊天应用习惯
- 响应式设计，组件状态实时反映系统状态
- 键盘友好，支持完整的快捷键操作

### 3. 视觉统一
- 统一的边框设计，避免嵌套边框的视觉混乱
- 一致的背景色和间距设计
- 紧凑的布局，最大化空间利用率

## 组件架构

### 布局结构

```
┌─ 统一边框 (JewelTheme.globalColors.borders.normal) ────────────┐
│ 📎 Add Context [上下文标签1] [上下文标签2] ...              │
│ ─────────────────────────────────────────────────────────── │
│ 输入消息，使用 @ 内联引用文件，或 ⌘K 添加上下文...             │
│                                                           │
│ ─────────────────────────────────────────────────────────── │
│ Claude 4 Opus ▼                                       ↑  │
└─────────────────────────────────────────────────────────────┘
```

### 层次结构

```kotlin
ChatInputArea {
    Column {
        // 第一行：上下文管理
        Row {
            AddContextButton()
            LazyRow {
                ContextTags()
            }
        }
        
        // 第二行：输入框
        BasicTextField()
        
        // 第三行：控制栏
        Row {
            ModelSelector()
            Spacer()
            SendStopButton()
        }
    }
}
```

## 组件详细设计

### 1. Add Context 按钮

**目的**：提供点击方式触发上下文选择器

**设计规范**：
- 背景色：`JewelTheme.globalColors.panelBackground`（与输入框统一）
- 尺寸：高度 24dp
- 圆角：4dp
- 内边距：水平 6dp，垂直 2dp
- 字体：10sp
- 图标：📎（回形针图标，表示附加内容）

**交互行为**：
- 点击触发 `onShowContextSelector()` 回调
- 鼠标悬停效果（略微变暗）
- 支持焦点状态

### 2. 上下文标签区域

**目的**：显示已选择的上下文项目，支持管理操作

**设计规范**：
- 布局：水平滚动列表（LazyRow）
- 间距：标签间 4dp 间距
- 最大宽度：自适应，超出时显示滚动条

**标签设计**：
- 背景：`JewelTheme.globalColors.panelBackground`
- 边框：1dp，`JewelTheme.globalColors.borders.normal`
- 圆角：12dp（胶囊形状）
- 内边距：水平 8dp，垂直 4dp
- 字体：9sp

**交互行为**：
- 点击标签：可能的编辑操作（未来扩展）
- 点击删除按钮（×）：移除对应上下文项
- 支持键盘导航

### 3. 输入框区域

**目的**：主要的文本输入区域，支持多行输入和内联引用

**设计规范**：
- 组件：BasicTextField（Compose）
- 最小高度：32dp
- 最大高度：120dp（约 6 行文本）
- 字体大小：14sp
- 内边距：4dp
- 背景：透明（依赖外层容器背景）
- 无边框：避免与外层边框重复

**功能特性**：
- 占位符文本："输入消息，使用 @ 内联引用文件，或 ⌘K 添加上下文..."
- 自动换行和垂直滚动
- @ 符号触发内联文件引用
- 支持富文本显示（未来扩展）

**键盘事件处理**：
- Enter：发送消息（非 Shift+Enter 时）
- Shift+Enter：插入换行符
- ⌘K：触发上下文选择器
- ESC：关闭上下文选择器（如果打开）
- @：触发内联文件引用自动补全

### 4. 模型选择器

**目的**：允许用户选择不同的 AI 模型

**设计规范**：
- 位置：底部控制栏左侧
- 高度：24dp
- 字体大小：9sp
- 下拉箭头：7sp
- 背景：透明
- 边框：无

**交互设计**：
- 点击显示下拉菜单
- 菜单向上弹出，避免遮挡输入内容
- 当前选中模型高亮显示
- 选择后立即切换模型

**支持的模型**：
- Claude 4 Opus（默认）
- Claude 4 Sonnet

### 5. 发送/停止按钮

**目的**：发送消息或停止当前生成

**设计规范**：
- 尺寸：24dp × 24dp
- 图标大小：10sp
- 背景：圆形，`JewelTheme.globalColors.panelBackground`
- 边框：1dp，`JewelTheme.globalColors.borders.normal`

**状态管理**：
- 发送状态：显示 ↑ 图标，仅在有输入内容时启用
- 停止状态：显示 ⏹ 图标，在生成过程中启用
- 禁用状态：灰色显示，不可点击

**交互行为**：
- 发送模式：`onSendMessage()` 回调
- 停止模式：`onStopGeneration()` 回调
- 键盘等价：Enter 键触发发送

## UnifiedInputArea 统一组件

### 组件重构

为了确保输入和显示的完全视觉一致性，我们将 `ChatInputArea`（输入模式）和 `UserMessageDisplay`（显示模式）统一为单一的 `UnifiedInputArea` 组件。

**重构架构**：
```
UnifiedInputArea (统一基础组件)
├── InputAreaMode.INPUT  → ChatInputArea (包装器)
└── InputAreaMode.DISPLAY → UserMessageDisplay (包装器)
```

**统一特性**：
- **相同的边框和背景**: 统一的圆角矩形设计
- **相同的布局结构**: 三行布局（上下文、内容、工具栏）
- **相同的间距和内边距**: 8dp 统一内边距，6dp 行间距
- **相同的字体和颜色**: 完全一致的文本样式

**模式差异**：
| 特性 | INPUT 模式 | DISPLAY 模式 |
|------|------------|--------------|
| Add Context 按钮 | ✅ 显示并可点击 | ❌ 隐藏 |
| 上下文标签 | ✅ 可删除的标签 | ✅ 只读标签 |
| 文本输入 | ✅ 多行可编辑输入框 | ✅ 只读文本显示 |
| 模型选择 | ✅ 可切换的下拉选择器 | ✅ 只显示模型名称 |
| 发送按钮 | ✅ 发送/停止按钮组合 | ❌ 隐藏 |
| 图片选择 | ✅ 图片选择按钮 | ❌ 隐藏 |
| 时间戳 | ❌ 不显示 | ✅ 显示消息时间 |

## 用户消息显示组件

### 设计理念

用户消息显示组件 (`UserMessageDisplay.kt`) 现在作为 `UnifiedInputArea` 的包装器，复用了 ChatInputArea 的设计和布局，确保在对话界面中保持视觉一致性。

### 主要特点

**视觉一致性**：
- 使用与 ChatInputArea 相同的统一边框和背景色
- 保持相同的8dp圆角边框设计
- 相同的字体大小和间距规范

**只读显示模式**：
- 所有组件不可交互，纯展示用途
- 隐藏 Add Context 按钮（用户已完成上下文选择）
- 隐藏发送按钮（消息已发送）
- 模型信息只显示，不可切换

**动态布局结构**：
1. **第一行**：上下文标签（仅在有上下文时显示，无上下文时隐藏整行）
2. **第二行**：用户输入的文本内容（必显示）
3. **第三行**：使用的AI模型信息和时间戳（必显示）

### 功能实现

**上下文解析**：
- 自动解析消息中的上下文信息
- 支持文件、网页、文件夹等多种上下文类型
- 使用与输入组件相同的 ContextTag 显示

**内容格式化**：
- 将内联引用 `@path/to/file` 转换为简短的 `@filename` 显示
- 保持文本的可读性和简洁性

**时间戳显示**：
- 在底部右侧显示消息发送时间
- 使用9sp小字体，透明度0.6

## 状态管理

### 组件状态

```kotlin
data class ChatInputAreaState(
    val inputText: String = "",
    val selectedModel: AiModel = AiModel.OPUS,
    val isGenerating: Boolean = false,
    val contextItems: List<ContextItem> = emptyList(),
    val showContextSelector: Boolean = false
)
```

### 状态同步

组件通过回调函数与父组件同步状态：

```kotlin
ChatInputArea(
    state = inputState,
    onInputChange = { /* 更新输入文本 */ },
    onModelChange = { /* 更新选中模型 */ },
    onSendMessage = { /* 发送消息 */ },
    onStopGeneration = { /* 停止生成 */ },
    onContextChange = { /* 更新上下文列表 */ },
    onShowContextSelector = { /* 显示上下文选择器 */ }
)
```

## 主题适配

### 颜色使用

- **主背景**：`JewelTheme.globalColors.panelBackground`
- **边框颜色**：`JewelTheme.globalColors.borders.normal`
- **文本颜色**：`JewelTheme.globalColors.text.normal`
- **禁用状态**：`JewelTheme.globalColors.text.disabled`
- **悬停状态**：`JewelTheme.globalColors.panelBackground.darker(0.1f)`

### 响应式设计

- 自动适配 Light/Dark 主题
- 颜色随系统主题变化实时更新
- 保持与 IDE 整体风格的一致性

## 可访问性

### 键盘导航

- Tab 键在组件间切换焦点
- 方向键在上下文标签间导航
- Enter/Space 激活按钮
- ESC 关闭弹窗

### 屏幕阅读器支持

- 为所有交互元素提供语义化标签
- 状态变化时提供适当的通知
- 支持焦点指示器

## 性能优化

### 渲染优化

- 使用 `remember` 缓存稳定的状态
- LazyRow 实现上下文标签的虚拟化
- 避免不必要的重新组合

### 内存管理

- 及时清理事件监听器
- 避免内存泄漏
- 合理使用 Composition 生命周期

## 扩展性设计

### 插件化支持

- 上下文类型可扩展（文件、网页、代码片段等）
- 模型列表可配置
- 自定义键盘快捷键

### 主题定制

- 支持自定义颜色方案
- 可配置的间距和尺寸
- 图标替换支持

## 测试策略

### 单元测试

- 状态管理逻辑测试
- 键盘事件处理测试
- 模型切换逻辑测试

### 集成测试

- 与父组件的交互测试
- 上下文选择器集成测试
- 主题适配测试

### 用户测试

- 可用性测试
- 键盘导航测试
- 视觉一致性验证

## 维护指南

### 代码结构

- 保持组件的单一职责
- 明确的接口定义
- 完善的文档注释

### 版本管理

- 向后兼容的接口设计
- 渐进式功能升级
- 清晰的变更日志

### 故障排除

- 详细的日志记录
- 错误状态的优雅处理
- 用户友好的错误提示 