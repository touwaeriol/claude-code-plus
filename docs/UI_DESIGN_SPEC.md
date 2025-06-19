# UI 设计规范文档

## 1. 设计理念

### 1.1 核心原则
- **清晰性**：主要内容（对话）应该占据视觉焦点
- **简洁性**：辅助信息（工具调用）应该紧凑展示
- **可控性**：用户可以选择显示级别
- **一致性**：遵循 IntelliJ 平台的设计规范

### 1.2 信息层级
1. **主要信息**：用户消息和 Claude 的文本回复
2. **次要信息**：工具调用和结果
3. **辅助信息**：状态、错误、系统提示

## 2. 工具消息显示设计

### 2.1 显示模式

#### 隐藏模式
- 完全不显示任何工具调用信息
- 适合只关注对话内容的用户

#### 紧凑模式（推荐）
```
🔧 read_file → MainActivity.kt [查看详情]
🔧 bash → gradle build ✓ 成功 [查看详情]
🔧 write_file → Config.json ✗ 失败: 权限不足 [查看详情]
```

特点：
- 单行显示
- 显示工具名、关键参数、状态
- 可点击展开查看详情
- 使用颜色区分状态：✓ 绿色（成功）、✗ 红色（失败）、⏳ 灰色（执行中）

#### 展开模式
```
🔧 工具调用: read_file
┌─ 参数 ─────────────────────┐
│ path: /src/MainActivity.kt  │
│ line_start: 1              │
│ line_end: 50               │
└───────────────────────────┘
📋 结果: 50 行代码已读取
```

### 2.2 交互设计

#### 展开/收起动画
- 使用平滑的滑动动画（200ms）
- 保持滚动位置不变

#### 工具结果处理
- 超过 3 行自动折叠
- 显示 "... 查看更多" 链接
- 二进制内容显示为 "[二进制数据 1.2MB]"

### 2.3 视觉样式

#### 颜色方案
```
工具名称: IDE 主题的关键字颜色
参数值: IDE 主题的字符串颜色
成功状态: #4CAF50 (绿色)
失败状态: #F44336 (红色)
执行中: #9E9E9E (灰色)
背景: IDE 主题的轻微高亮背景
```

#### 字体样式
- 工具名称：等宽字体，正常粗细
- 参数和结果：等宽字体，较小字号（-1pt）
- 状态图标：使用 emoji 或 IDE 图标

## 3. 布局优化

### 3.1 消息区域空间分配
- 文本消息：100% 宽度
- 工具消息（紧凑）：90% 宽度，左侧缩进
- 工具消息（展开）：85% 宽度，带边框

### 3.2 响应式设计
- 窗口宽度 < 400px：隐藏 "[查看详情]" 链接，点击整行展开
- 窗口宽度 < 300px：自动切换到隐藏模式

## 4. 实现建议

### 4.1 数据结构
```kotlin
data class ToolCall(
    val id: String,
    val name: String,
    val parameters: Map<String, Any>,
    val status: ToolStatus,
    val result: String?,
    val isExpanded: Boolean = false
)

enum class ToolStatus {
    RUNNING, SUCCESS, FAILURE
}
```

### 4.2 渲染优化
- 使用虚拟滚动处理长对话
- 延迟加载工具详情内容
- 缓存渲染后的 Markdown

### 4.3 用户偏好存储
```kotlin
// 保存用户的显示偏好
interface ToolDisplayPreferences {
    var showToolCalls: Boolean
    var defaultExpandMode: ExpandMode
    var autoCollapseResults: Boolean
    var maxResultLines: Int
}
```

## 5. 无障碍设计

### 5.1 键盘导航
- Tab 键在工具调用之间导航
- Enter/Space 展开/收起工具详情
- Esc 关闭所有展开的工具详情

### 5.2 屏幕阅读器
- 工具调用包含 aria-label
- 状态变化时发出通知
- 结果截断时说明完整长度

## 6. 性能指标

### 6.1 目标
- 工具消息渲染 < 50ms
- 展开/收起动画流畅（60fps）
- 1000+ 消息时滚动流畅

### 6.2 优化策略
- 懒加载未展开的工具详情
- 使用 StringBuilder 而非字符串拼接
- 批量更新 DOM 而非逐个更新