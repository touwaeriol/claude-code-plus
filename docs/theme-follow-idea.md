# IDEA 主题跟随功能

## 功能概述

Claude Code Plus 插件的工具窗口现在支持自动跟随 IntelliJ IDEA 的主题设置。当您切换 IDEA 的主题时，插件界面会自动适应新的主题风格。

## 实现原理

### 1. 主题检测

插件通过 `IdeaThemeAdapter` 类检测当前 IDEA 使用的主题：

```kotlin
// 判断是否为暗色主题
val isDarkTheme = UIUtil.isUnderDarcula()

// 或通过 Look and Feel 名称判断
val laf = UIManager.getLookAndFeel()
val isDark = laf.name.contains("Darcula") || laf.name.contains("Dark")
```

### 2. 主题监听

通过订阅 IDEA 的主题变化事件，实时响应主题切换：

```kotlin
ApplicationManager.getApplication().messageBus.connect()
    .subscribe(LafManagerListener.TOPIC, LafManagerListener {
        val isDark = isDarkTheme()
        // 更新 Compose UI 主题
        PluginComposeFactory.updateTheme(isDark)
    })
```

### 3. Compose UI 响应式更新

使用 Compose 的响应式状态管理，主题变化时自动重组 UI：

```kotlin
// 存储主题状态
private var isDarkTheme by mutableStateOf(false)

// Compose UI 自动响应主题变化
IntUiTheme(isDark = isDarkTheme) {
    // UI 内容
}
```

## 核心组件

### IdeaThemeAdapter

负责：
- 检测当前 IDEA 主题状态
- 注册主题变化监听器
- 提供主题相关的颜色配置

### PluginComposeFactory

负责：
- 管理 Compose UI 的主题状态
- 接收主题更新通知
- 触发 UI 重组

### ClaudeCodePlusToolWindowFactory

负责：
- 初始化主题检测
- 设置主题监听器
- 将主题状态传递给 UI

## 支持的主题

插件自动适配以下 IDEA 主题类型：

### 亮色主题
- IntelliJ Light
- Windows 10 Light
- macOS Light
- 其他亮色主题

### 暗色主题
- Darcula
- High Contrast
- Carbon
- One Dark
- 其他暗色主题

## 主题切换效果

当您在 IDEA 中切换主题时（Settings → Appearance & Behavior → Appearance → Theme），插件会：

1. **即时响应** - 无需重启 IDE 或重新打开工具窗口
2. **平滑过渡** - UI 组件颜色自动调整
3. **保持一致性** - 与 IDEA 主题风格保持统一

## 颜色配置

### 暗色主题颜色
```kotlin
background = 0xFF2B2B2B      // 深灰背景
foreground = 0xFFBBBBBB      // 浅灰文字
primaryColor = 0xFF4C9AFF    // 蓝色强调
borderColor = 0xFF3C3F41     // 边框颜色
```

### 亮色主题颜色
```kotlin
background = 0xFFFFFFFF      // 白色背景
foreground = 0xFF000000      // 黑色文字
primaryColor = 0xFF0066CC    // 蓝色强调
borderColor = 0xFFD1D5DB     // 边框颜色
```

## 调试和日志

主题相关的日志信息：

```
初始主题检测: isDark = true
主题变更通知: isDark = false
Claude Code Plus tool window created successfully with theme support
```

## 已知限制

1. **首次加载** - 插件首次加载时需要检测当前主题
2. **自定义主题** - 某些高度自定义的主题可能无法完美匹配
3. **性能影响** - 主题切换时会触发整个 UI 重组

## 未来改进

- 支持更细粒度的主题定制
- 添加主题偏好设置
- 支持自定义颜色方案
- 优化主题切换性能