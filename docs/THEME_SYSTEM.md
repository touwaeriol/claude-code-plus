# Vue 前端主题适配系统

## 📋 概述

本文档描述了 Claude Code Plus Vue 前端的主题适配系统，该系统确保前端 UI 能够自动适配 IntelliJ IDEA 的主题（亮色/暗色）。

## 🎨 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    IntelliJ IDEA 主题                        │
│                  (Light / Dark / Custom)                     │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              后端 (HttpApiServer.kt)                         │
│                                                              │
│  extractIdeTheme() → IdeTheme {                             │
│    isDark, background, foreground,                          │
│    panelBackground, border, linkColor, ...                  │
│  }                                                           │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼ HTTP API / JCEF Bridge
┌─────────────────────────────────────────────────────────────┐
│              前端 (themeService.ts)                          │
│                                                              │
│  1. 获取主题数据 (ide.getTheme)                             │
│  2. 注入 CSS 变量到 :root                                   │
│  3. 监听主题变化事件                                         │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              CSS 变量系统 (global.css)                       │
│                                                              │
│  :root {                                                     │
│    --ide-background: #ffffff;                               │
│    --ide-foreground: #24292e;                               │
│    --ide-border: #e1e4e8;                                   │
│    ...                                                       │
│  }                                                           │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              Vue 组件样式                                    │
│                                                              │
│  background: var(--ide-background, #ffffff);                │
│  color: var(--ide-foreground, #24292e);                     │
│  border: 1px solid var(--ide-border, #e1e4e8);             │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 实现细节

### 1. 后端主题提取

**文件**: `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/server/HttpApiServer.kt`

```kotlin
private fun extractIdeTheme(): IdeTheme {
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    val isDark = ColorUtil.isDark(globalScheme.defaultBackground)

    return IdeTheme(
        isDark = isDark,
        background = globalScheme.defaultBackground.toHex(),
        foreground = globalScheme.defaultForeground.toHex(),
        panelBackground = UIUtil.getPanelBackground().toHex(),
        border = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground().toHex(),
        linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED.toHex(),
        // ... 更多颜色
    )
}
```

### 2. 前端主题服务

**文件**: `frontend/src/services/themeService.ts`

```typescript
class ThemeService {
  async loadTheme(): Promise<void> {
    const response = await apiClient.request('ide.getTheme', {})
    if (response.success && response.data?.theme) {
      this.currentTheme = response.data.theme as IdeTheme
      this.injectCssVariables(this.currentTheme)
    }
  }

  private injectCssVariables(theme: IdeTheme): void {
    const root = document.documentElement
    
    // 基础颜色
    root.style.setProperty('--ide-background', theme.background)
    root.style.setProperty('--ide-foreground', theme.foreground)
    root.style.setProperty('--ide-border', theme.border)
    
    // 面板颜色
    root.style.setProperty('--ide-panel-background', theme.panelBackground)
    
    // 链接和强调色
    root.style.setProperty('--ide-link', theme.linkColor)
    root.style.setProperty('--ide-accent', theme.linkColor)
    
    // 代码编辑器颜色
    root.style.setProperty('--ide-code-background', theme.panelBackground)
    root.style.setProperty('--ide-code-foreground', theme.foreground)
    
    // 警告背景色（根据主题动态计算）
    const warningBg = theme.isDark ? '#3d3416' : '#fff8dc'
    root.style.setProperty('--ide-warning-background', warningBg)
    
    // 选择背景色
    const selectionBg = theme.isDark ? '#1a3a52' : '#e8f2ff'
    root.style.setProperty('--ide-selection-background', selectionBg)
  }
}
```

### 3. CSS 变量定义

**文件**: `frontend/src/styles/global.css`

```css
:root {
  /* 基础颜色 */
  --ide-background: #ffffff;
  --ide-foreground: #24292e;
  --ide-border: #e1e4e8;
  
  /* 面板颜色 */
  --ide-panel-background: #f6f8fa;
  
  /* 链接和强调色 */
  --ide-link: #0366d6;
  --ide-accent: #0366d6;
  
  /* 代码编辑器 */
  --ide-code-background: #f6f8fa;
  --ide-code-foreground: #24292e;
  
  /* 状态颜色 */
  --ide-success: #22863a;
  --ide-warning: #856404;
  --ide-error: #d73a49;
  
  /* 特殊背景 */
  --ide-warning-background: #fff8dc;
  --ide-selection-background: #e8f2ff;
  --ide-selection-foreground: #24292e;
}

/* 暗色主题默认值 */
.theme-dark {
  --ide-background: #2b2b2b;
  --ide-foreground: #a9b7c6;
  --ide-border: #3c3f41;
  --ide-panel-background: #3c3f41;
  --ide-code-background: #2b2b2b;
  --ide-code-foreground: #a9b7c6;
  --ide-warning-background: #3d3416;
  --ide-selection-background: #1a3a52;
  --ide-selection-foreground: #e1e4e8;
}
```

## 📝 使用指南

### 在 Vue 组件中使用主题

**推荐做法**：始终使用 CSS 变量，并提供后备值

```vue
<style scoped>
.message-item {
  /* ✅ 正确：使用 CSS 变量 + 后备值 */
  background: var(--ide-background, #ffffff);
  color: var(--ide-foreground, #24292e);
  border: 1px solid var(--ide-border, #e1e4e8);
}

.message-item:hover {
  /* ✅ 正确：使用透明度实现 hover 效果 */
  background: var(--ide-selection-background, #f6f8fa);
}

/* ❌ 错误：硬编码颜色 */
.bad-example {
  background: #ffffff;  /* 不会适配暗色主题 */
  color: #24292e;
}
</style>
```

### 可用的 CSS 变量列表

| 变量名 | 用途 | 亮色默认值 | 暗色默认值 |
|--------|------|-----------|-----------|
| `--ide-background` | 主背景色 | `#ffffff` | `#2b2b2b` |
| `--ide-foreground` | 主文本色 | `#24292e` | `#a9b7c6` |
| `--ide-border` | 边框颜色 | `#e1e4e8` | `#3c3f41` |
| `--ide-panel-background` | 面板背景 | `#f6f8fa` | `#3c3f41` |
| `--ide-link` | 链接颜色 | `#0366d6` | `#589df6` |
| `--ide-accent` | 强调色 | `#0366d6` | `#589df6` |
| `--ide-code-background` | 代码背景 | `#f6f8fa` | `#2b2b2b` |
| `--ide-code-foreground` | 代码文本 | `#24292e` | `#a9b7c6` |
| `--ide-success` | 成功状态 | `#22863a` | `#34d058` |
| `--ide-warning` | 警告状态 | `#856404` | `#ffc107` |
| `--ide-error` | 错误状态 | `#d73a49` | `#f85149` |
| `--ide-warning-background` | 警告背景 | `#fff8dc` | `#3d3416` |
| `--ide-selection-background` | 选择背景 | `#e8f2ff` | `#1a3a52` |
| `--ide-selection-foreground` | 选择文本 | `#24292e` | `#e1e4e8` |

## 🔄 主题变化监听

### 自动更新机制

当用户在 IDEA 中切换主题时，前端会自动更新：

**后端推送**：
```kotlin
// HttpApiServer.kt
private fun setupThemeListener() {
    ApplicationManager.getApplication().messageBus
        .connect()
        .subscribe(LafManagerListener.TOPIC, LafManagerListener {
            val theme = extractIdeTheme()
            pushEvent(IdeEvent(
                type = "theme",
                data = mapOf("theme" to json.parseToJsonElement(json.encodeToString(theme)))
            ))
        })
}
```

**前端监听**：
```typescript
// themeService.ts
ideaBridge.addEventListener('theme', (event) => {
  if (event.data?.theme) {
    this.currentTheme = event.data.theme as IdeTheme
    this.injectCssVariables(this.currentTheme)
  }
})
```

## 🎯 工具组件主题适配

### 工具显示组件

所有工具显示组件都已适配主题系统：

#### ReadToolDisplay.vue
```vue
<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-panel-background, #f6f8fa);
}

.tool-name {
  color: var(--ide-accent, #0366d6);
}

.clickable {
  color: var(--ide-link, #0366d6);
  cursor: pointer;
}

.clickable:hover {
  opacity: 0.8;
}
</style>
```

#### EditToolDisplay.vue
```vue
<style scoped>
.edit-tool {
  border-color: var(--ide-error, #f9826c);
}

.diff-header.old {
  background: #ffeef0;
  color: var(--ide-error, #d73a49);
}

.diff-header.new {
  background: #e6ffed;
  color: var(--ide-success, #22863a);
}

/* 暗色主题特殊处理 */
.theme-dark .diff-header.old {
  background: #3d1f1f;
}

.theme-dark .diff-header.new {
  background: #1f3d1f;
}
</style>
```

### 特殊颜色处理

某些颜色需要在暗色主题下特殊处理：

**Diff 背景色**：
- 亮色主题：`#ffeef0`（浅红）/ `#e6ffed`（浅绿）
- 暗色主题：`#3d1f1f`（暗红）/ `#1f3d1f`（暗绿）

**状态标签**：
- 使用 `.theme-dark` 类选择器覆盖特定样式
- 保持足够的对比度

## 🐛 常见问题

### 1. 主题未生效

**症状**：前端显示白色背景，未适配暗色主题

**原因**：
- CSS 使用了硬编码颜色
- 未调用 `themeService.loadTheme()`

**解决**：
```typescript
// main.ts
import { themeService } from '@/services/themeService'

app.mount('#app')
await themeService.loadTheme()  // 确保加载主题
```

### 2. 主题切换不生效

**症状**：在 IDEA 中切换主题后，前端未更新

**原因**：
- WebSocket 未连接
- 事件监听器未注册

**解决**：
```typescript
// 检查 WebSocket 连接状态
console.log('WebSocket connected:', ideaBridge.isWebSocketConnected())

// 确保监听器已注册
themeService.setupThemeListener()
```

### 3. 部分组件颜色不对

**症状**：某些组件颜色未适配主题

**原因**：
- 使用了硬编码颜色
- CSS 变量名拼写错误
- 缺少后备值

**解决**：
```css
/* ❌ 错误 */
.component {
  background: #ffffff;
}

/* ✅ 正确 */
.component {
  background: var(--ide-background, #ffffff);
}
```

## 📊 测试清单

### 主题适配测试

- [ ] 亮色主题下所有组件显示正常
- [ ] 暗色主题下所有组件显示正常
- [ ] 主题切换时自动更新（无需刷新）
- [ ] 消息列表背景色正确
- [ ] 工具显示组件颜色正确
- [ ] Diff 对比颜色对比度足够
- [ ] 链接颜色可点击且可见
- [ ] 边框颜色清晰可见

### 组件测试

**MessageDisplay.vue**：
- [ ] 用户消息背景色适配
- [ ] AI 消息背景色适配
- [ ] Hover 效果正确

**工具组件**：
- [ ] ReadToolDisplay - 边框、背景、文本颜色
- [ ] EditToolDisplay - Diff 颜色、状态标签
- [ ] MultiEditToolDisplay - 批量 Diff 显示
- [ ] WriteToolDisplay - 文件路径链接颜色

## 🚀 最佳实践

### 1. 始终使用 CSS 变量

```css
/* ✅ 推荐 */
.component {
  background: var(--ide-background, #ffffff);
  color: var(--ide-foreground, #24292e);
}

/* ❌ 不推荐 */
.component {
  background: #ffffff;
  color: #24292e;
}
```

### 2. 提供后备值

```css
/* ✅ 有后备值 - 即使主题未加载也能显示 */
background: var(--ide-background, #ffffff);

/* ❌ 无后备值 - 主题未加载时可能透明 */
background: var(--ide-background);
```

### 3. 使用透明度而非硬编码

```css
/* ✅ 使用透明度 - 适配所有主题 */
.hover {
  opacity: 0.8;
}

/* ❌ 硬编码 hover 颜色 - 只适配一种主题 */
.hover {
  background: #f0f0f0;
}
```

### 4. 暗色主题特殊处理

```css
/* 基础样式 */
.diff-old {
  background: #ffeef0;
}

/* 暗色主题覆盖 */
.theme-dark .diff-old {
  background: #3d1f1f;
}
```

## 📚 相关文档

- [HTTP API 架构](HTTP_API_ARCHITECTURE.md) - API 接口定义
- [故障排除指南](TROUBLESHOOTING.md) - 常见问题解决

---

**最后更新**: 2025-11-13
**作者**: Claude Code Plus Team


