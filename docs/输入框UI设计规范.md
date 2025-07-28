# Claude Code Plus 输入框UI设计规范

## 设计理念

参考Cursor等现代AI编辑器的设计，提供统一、清晰、高效的输入体验。核心原则：
- **视觉统一**：单一容器，统一背景，减少视觉噪音
- **层次清晰**：三层布局，功能分区明确
- **现代简约**：扁平化设计，适度的圆角和阴影
- **响应迅速**：即时的视觉反馈，流畅的过渡动画

## 视觉层次

### 1. 统一容器
- 所有输入相关元素包含在一个容器内
- 容器有统一的背景色和边框
- 内部组件无嵌套边框或独立背景

### 2. 三层布局
```
┌─────────────────────────────────────────────┐
│  顶部工具栏：上下文管理                      │
├─────────────────────────────────────────────┤
│  中间输入区：纯净的文本输入                  │
├─────────────────────────────────────────────┤
│  底部选项栏：模型、权限、操作按钮            │
└─────────────────────────────────────────────┘
```

### 3. 最小化装饰
- 减少不必要的边框和分隔线
- 使用间距而非线条来区分区域
- 保持视觉的轻量感

## 颜色方案

### 基础颜色
```kotlin
// 容器
containerBackground = JewelTheme.globalColors.panelBackground
containerBorder = JewelTheme.globalColors.borders.normal

// 悬浮状态
hoverBackground = JewelTheme.globalColors.infoColors.primaryColor.copy(alpha = 0.1f)
hoverBorder = JewelTheme.globalColors.borders.focused

// 焦点状态
focusBorder = JewelTheme.globalColors.borders.focused
focusGlow = JewelTheme.globalColors.infoColors.primaryColor.copy(alpha = 0.2f)

// 禁用状态
disabledText = JewelTheme.globalColors.text.disabled
disabledBackground = JewelTheme.globalColors.text.disabled.copy(alpha = 0.1f)
```

### 功能色
```kotlin
// 按钮状态
sendButtonNormal = Color(0xFF007AFF)  // 蓝色
stopButtonActive = Color(0xFFFF4444)  // 红色
buttonDisabled = JewelTheme.globalColors.text.disabled

// 标签类型
fileTagIcon = Color(0xFF5B9BD5)      // 文件蓝
webTagIcon = Color(0xFF70AD47)       // 网页绿
folderTagIcon = Color(0xFFFFC000)    // 文件夹黄
```

## 组件规范

### 1. 容器组件 (UnifiedChatInput)
- **边框**：1.dp 实线，normal 状态
- **圆角**：8.dp
- **内边距**：12.dp
- **背景**：panelBackground
- **焦点效果**：边框变为 focused 颜色，添加淡光晕

### 2. 选择器组件 (ModernSelectors)
- **高度**：24.dp
- **最小宽度**：120.dp
- **字体大小**：
  - 选中项：11.sp
  - 描述文字：9.sp
- **弹出菜单**：
  - 方向：向上弹出
  - 宽度：200.dp
  - 项目高度：36.dp
  - 圆角：6.dp
  - 阴影：elevation 4.dp
- **悬浮效果**：
  - 背景变化：alpha 0.1f 的主题色
  - 过渡时间：200ms

### 3. 上下文标签 (PillContextTag)
- **形状**：胶囊形 (RoundedCornerShape(10.dp))
- **高度**：20.dp
- **内边距**：horizontal 8.dp, vertical 4.dp
- **图标**：
  - 大小：14.dp
  - 与文字间距：4.dp
- **关闭按钮**：
  - 大小：12.dp
  - 默认隐藏，悬浮时显示
  - 点击区域：16.dp（大于视觉大小）

### 4. 发送/停止按钮 (SendStopButton)
- **形状**：圆形
- **大小**：24.dp
- **图标大小**：12.sp
- **颜色状态**：
  - 发送：#007AFF（可发送）/ disabled（无内容）
  - 停止：#FF4444（生成中）
- **悬浮效果**：
  - 放大：scale 1.1
  - 阴影：elevation 增加

### 5. 输入框 (ChatInputField)
- **最小高度**：40.dp
- **最大高度**：120.dp（自动扩展）
- **字体大小**：14.sp
- **行高**：1.5
- **无边框**：依赖容器的统一边框
- **无背景**：透明，使用容器背景

## 交互规范

### 1. 焦点管理
- 页面加载时自动聚焦到输入框
- ESC 键关闭弹窗并返回焦点到输入框
- Tab 键在各组件间循环切换

### 2. 悬浮状态
- **响应时间**：立即响应（无延迟）
- **过渡动画**：200ms ease-in-out
- **视觉变化**：
  - 可点击元素：背景色变化
  - 文字链接：下划线出现
  - 按钮：轻微放大效果

### 3. 点击反馈
- **按下效果**：scale 0.95
- **释放动画**：100ms 弹回
- **波纹效果**：可选（根据平台）

### 4. 禁用状态
- **透明度**：0.5
- **鼠标指针**：not-allowed
- **移除所有悬浮效果**
- **保持布局不变**

## 动画规范

### 1. 基础动画参数
```kotlin
const val TRANSITION_DURATION = 200L
const val QUICK_TRANSITION = 100L
const val FADE_DURATION = 150L

val standardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
val accelerateEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
val decelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
```

### 2. 组件动画
- **弹出菜单**：fadeIn + slideIn（从下往上）
- **标签添加**：fadeIn + scale（从 0.8 到 1.0）
- **标签删除**：fadeOut + scale（从 1.0 到 0.8）
- **按钮切换**：crossfade（淡入淡出）

### 3. 状态过渡
- **悬浮**：背景色渐变
- **按下**：缩放动画
- **焦点**：边框颜色渐变 + 光晕淡入
- **禁用**：透明度渐变

## 响应式设计

### 1. 最小尺寸
- 容器最小宽度：320.dp
- 输入框最小宽度：200.dp

### 2. 自适应布局
- 窄屏时（< 600.dp）：
  - 选择器改为图标按钮
  - 标签区域可横向滚动
- 宽屏时（>= 600.dp）：
  - 完整显示所有选择器
  - 标签自动换行

### 3. 文字截断
- 选择器文字：省略号截断
- 标签文字：最大宽度 150.dp，中间省略
- 工具提示：悬浮时显示完整内容

## 无障碍支持

### 1. 键盘导航
- Tab 键顺序：输入框 → 上下文按钮 → 选择器 → 发送按钮
- 方向键：在选择器选项间导航
- Space/Enter：激活当前焦点元素

### 2. 屏幕阅读器
- 所有交互元素都有 contentDescription
- 状态变化时发送无障碍事件
- 使用语义化的角色标记

### 3. 高对比度模式
- 边框加粗到 2.dp
- 增加文字与背景的对比度
- 焦点指示器更明显

## 实现检查清单

- [ ] 统一容器组件实现
- [ ] 三层布局结构
- [ ] 现代化选择器样式
- [ ] 胶囊形上下文标签
- [ ] 焦点状态管理
- [ ] 悬浮效果实现
- [ ] 过渡动画添加
- [ ] 键盘导航支持
- [ ] 响应式布局适配
- [ ] 无障碍功能完善

## 参考实现

### Cursor 输入框特点
- 统一的大边框包围整个输入区域
- 清晰的功能分区
- 现代的选择器设计（类似 VS Code）
- 流畅的交互动画

### 其他参考
- VS Code：命令面板设计
- JetBrains IDE：快速修复弹窗
- Slack：消息输入框布局