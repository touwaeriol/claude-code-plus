# Vue 前端架构迁移方案

## 文档概述

**分支**: `feat/vue-frontend-migration`
**创建日期**: 2025-01-03
**目标**: 将 Claude Code Plus 从 Compose Desktop UI 迁移到 Vue 3 + JCEF 架构
**预计工期**: 4-6 周

---

## 一、迁移动机与目标

### 1.1 为什么要迁移?

#### 当前 Compose 方案的局限性

- **学习曲线陡峭**: Compose Desktop + Jewel 生态小众,文档不足
- **组件库限制**: Jewel 组件功能有限,自定义困难
- **调试困难**: Compose 调试工具不如浏览器 DevTools 成熟
- **开发效率**: 前端常见需求(Markdown 渲染、代码高亮、富交互)在 Compose 中实现复杂
- **生态系统**: npm 生态远比 Compose 丰富

#### Vue 方案的优势

✅ **成熟的生态系统**: Vue 3 + Vite + TypeScript + 海量 npm 包
✅ **开发效率**: 组件化、热重载、丰富的 UI 库
✅ **调试友好**: 浏览器 DevTools + Vue DevTools
✅ **人才优势**: 前端开发者更容易上手
✅ **功能丰富**: Markdown 渲染、代码高亮、Diff 展示等开箱即用
✅ **已验证**: GitHub Copilot Chat 已证明该方案在 AI 聊天界面中的可行性

### 1.2 迁移目标

#### 功能对等 (Phase 1)
- ✅ 完整保留现有所有功能
- ✅ 用户体验不下降
- ✅ 性能在可接受范围内(内存增加 < 150MB)

#### 体验提升 (Phase 2)
- 🎯 更流畅的动画和交互
- 🎯 更好的 Markdown 渲染效果
- 🎯 更强大的代码高亮和 Diff 展示
- 🎯 更灵活的布局和主题定制

#### 架构优化 (Phase 3)
- 🚀 前后端分离,职责清晰
- 🚀 更易维护和扩展
- 🚀 支持独立的前端开发和测试

---

## 二、新架构设计

### 2.1 整体架构图

```
┌────────────────────────────────────────────────────────────────┐
│                        IntelliJ IDEA Platform                  │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  jetbrains-plugin (Kotlin) - 薄壳层                      │ │
│  │  ┌────────────────────────────────────────────────────┐  │ │
│  │  │  ToolWindowFactory                                 │  │ │
│  │  │  └─ 创建 JBCefBrowser,加载 Vue 前端               │  │ │
│  │  ├────────────────────────────────────────────────────┤  │ │
│  │  │  FrontendBridge (通信桥接)                         │  │ │
│  │  │  ├─ JBCefJSQuery Handler (前端 -> 后端)           │  │ │
│  │  │  └─ executeJavaScript (后端 -> 前端)              │  │ │
│  │  ├────────────────────────────────────────────────────┤  │ │
│  │  │  Backend Services                                  │  │ │
│  │  │  ├─ ClaudeCodeSdkClient (现有 SDK)                │  │ │
│  │  │  ├─ IdeaPlatformService (IDE 操作)                │  │ │
│  │  │  ├─ SessionManager (会话管理)                      │  │ │
│  │  │  └─ ThemeProvider (主题同步)                       │  │ │
│  │  └────────────────────────────────────────────────────┘  │ │
│  │                          ↕ JCEF Bridge                    │ │
│  │  ┌────────────────────────────────────────────────────┐  │ │
│  │  │  JBCefBrowser (Chromium)                           │  │ │
│  │  │  ┌──────────────────────────────────────────────┐  │  │ │
│  │  │  │  Vue 3 Frontend (TypeScript)                 │  │  │ │
│  │  │  │  ├─ App.vue (根组件)                         │  │  │ │
│  │  │  │  ├─ ChatView.vue (聊天界面)                  │  │  │ │
│  │  │  │  ├─ MessageList.vue (消息列表)               │  │  │ │
│  │  │  │  ├─ InputArea.vue (输入区域)                 │  │  │ │
│  │  │  │  ├─ ToolCallDisplay.vue (工具调用展示)       │  │  │ │
│  │  │  │  ├─ MarkdownRenderer.vue (Markdown 渲染)     │  │  │ │
│  │  │  │  └─ CodeBlock.vue (代码块)                   │  │  │ │
│  │  │  │                                                │  │  │ │
│  │  │  │  Services:                                    │  │  │ │
│  │  │  │  ├─ ideaBridge.ts (通信桥接)                 │  │  │ │
│  │  │  │  ├─ claudeService.ts (Claude API 封装)       │  │  │ │
│  │  │  │  ├─ themeService.ts (主题管理)               │  │  │ │
│  │  │  │  └─ ideService.ts (IDE 操作)                 │  │  │ │
│  │  │  │                                                │  │  │ │
│  │  │  │  Stores (Pinia):                             │  │  │ │
│  │  │  │  ├─ useSessionStore (会话状态)               │  │  │ │
│  │  │  │  ├─ useMessageStore (消息列表)               │  │  │ │
│  │  │  │  └─ useThemeStore (主题状态)                 │  │  │ │
│  │  │  └──────────────────────────────────────────────┘  │  │ │
│  │  └────────────────────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责划分

#### jetbrains-plugin (Kotlin 后端)

**职责**: IDE 平台集成 + 业务逻辑

| 组件 | 职责 | 代码量估算 |
|------|------|-----------|
| `FrontendBridge` | 前后端通信桥接 | ~400 行 |
| `ClaudeServiceHandler` | Claude SDK 调用封装 | ~300 行 |
| `IdeaPlatformService` | IDE 操作(文件、编辑器、Diff) | ~200 行 (已有) |
| `ThemeProvider` | 主题提取与同步 | ~150 行 |
| `SessionManager` | 会话管理 | ~200 行 (可复用) |
| `ToolWindowFactory` | 工具窗口注册 | ~100 行 |

**保留的现有代码**:
- ✅ `claude-code-sdk` 模块 (完全保留)
- ✅ `IdeaPlatformService` (已有实现)
- ✅ 会话状态管理服务
- ✅ MCP 服务器配置

**移除的代码**:
- ❌ `toolwindow` 模块所有 Compose UI 代码
- ❌ Jewel 组件相关依赖
- ❌ Compose Desktop 相关配置

#### frontend (Vue 3 前端)

**职责**: UI 渲染 + 用户交互

| 组件 | 职责 | 技术栈 |
|------|------|-------|
| 核心框架 | Vue 3 Composition API | TypeScript |
| 构建工具 | Vite | 快速热重载 |
| 状态管理 | Pinia | 轻量级状态管理 |
| 路由 | Vue Router | (如需多页面) |
| UI 组件库 | Element Plus / Ant Design Vue | 可选 |
| Markdown | `markdown-it` + 插件 | GFM 支持 |
| 代码高亮 | Shiki / Prism.js | 语法高亮 |
| Diff 展示 | `monaco-diff-editor` | Monaco Editor |
| 样式方案 | CSS Modules / UnoCSS | 原子化 CSS |

### 2.3 通信协议设计

#### 2.3.1 前端调用后端 (Request/Response)

**协议格式**:
```typescript
interface FrontendRequest {
  action: string;        // 操作类型,如 "claude.query"
  data?: any;            // 请求数据
}

interface FrontendResponse {
  success: boolean;      // 是否成功
  data?: any;            // 响应数据
  error?: string;        // 错误信息
}
```

**API 列表**:

| Action | 说明 | 请求参数 | 响应数据 |
|--------|------|---------|---------|
| `claude.connect` | 连接 Claude | `{ model?: string }` | `{ sessionId: string }` |
| `claude.query` | 发送消息 | `{ message: string }` | `{ success: boolean }` |
| `claude.interrupt` | 中断执行 | - | `{ success: boolean }` |
| `claude.disconnect` | 断开连接 | - | `{ success: boolean }` |
| `ide.openFile` | 打开文件 | `{ filePath, line?, column? }` | `{ success: boolean }` |
| `ide.showDiff` | 显示 Diff | `{ filePath, oldContent, newContent }` | `{ success: boolean }` |
| `ide.getTheme` | 获取主题 | - | `{ theme: IdeTheme }` |
| `ide.getProjectFiles` | 获取文件列表 | `{ pattern?: string }` | `{ files: string[] }` |
| `session.list` | 列出会话 | - | `{ sessions: Session[] }` |
| `session.switch` | 切换会话 | `{ sessionId: string }` | `{ success: boolean }` |
| `session.delete` | 删除会话 | `{ sessionId: string }` | `{ success: boolean }` |

#### 2.3.2 后端推送前端 (Event Push)

**协议格式**:
```typescript
interface IdeEvent {
  type: string;          // 事件类型
  data?: any;            // 事件数据
}
```

**事件列表**:

| Event Type | 说明 | 数据格式 |
|------------|------|---------|
| `claude.message` | Claude 消息 | `{ message: Message }` |
| `claude.connected` | 连接成功 | `{ sessionId: string }` |
| `claude.disconnected` | 连接断开 | `{ reason?: string }` |
| `claude.error` | 错误事件 | `{ error: string }` |
| `theme.changed` | 主题变化 | `{ theme: IdeTheme }` |
| `session.updated` | 会话更新 | `{ session: Session }` |

---

## 三、详细迁移步骤

### Phase 1: 基础设施搭建 (Week 1)

#### 3.1.1 前端项目初始化

**目标**: 搭建 Vue 3 项目骨架

```bash
# 创建前端项目
cd claude-code-plus
npm create vite@latest frontend -- --template vue-ts

# 安装依赖
cd frontend
npm install

# 安装核心依赖
npm install pinia vue-router
npm install markdown-it @types/markdown-it
npm install shiki
npm install @vueuse/core

# 安装开发依赖
npm install -D unocss @unocss/reset
npm install -D @types/node
```

**目录结构**:
```
frontend/
├── src/
│   ├── assets/              # 静态资源
│   ├── components/          # Vue 组件
│   │   ├── chat/           # 聊天相关组件
│   │   ├── markdown/       # Markdown 渲染
│   │   ├── tool/           # 工具调用展示
│   │   └── common/         # 通用组件
│   ├── services/            # 服务层
│   │   ├── ideaBridge.ts
│   │   ├── claudeService.ts
│   │   ├── themeService.ts
│   │   └── ideService.ts
│   ├── stores/              # Pinia 状态管理
│   │   ├── session.ts
│   │   ├── message.ts
│   │   └── theme.ts
│   ├── types/               # TypeScript 类型定义
│   │   ├── bridge.ts
│   │   ├── claude.ts
│   │   └── theme.ts
│   ├── utils/               # 工具函数
│   ├── styles/              # 全局样式
│   ├── App.vue              # 根组件
│   └── main.ts              # 入口文件
├── public/
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

**Vite 配置** (`vite.config.ts`):
```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), UnoCSS()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: '../jetbrains-plugin/src/main/resources/frontend',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor': ['vue', 'pinia'],
          'markdown': ['markdown-it', 'shiki']
        }
      }
    }
  },
  base: './' // 使用相对路径
})
```

**任务清单**:
- [ ] 创建 Vue 3 项目
- [ ] 配置 Vite 构建
- [ ] 配置 TypeScript
- [ ] 配置 UnoCSS
- [ ] 配置 Pinia
- [ ] 编写基础类型定义

#### 3.1.2 Kotlin 后端桥接层

**目标**: 实现 FrontendBridge 通信桥接

**文件位置**: `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/bridge/`

**核心文件**:
1. `FrontendBridge.kt` - 主桥接类
2. `BridgeProtocol.kt` - 协议定义
3. `ActionHandler.kt` - 操作处理器接口
4. `ClaudeActionHandler.kt` - Claude 操作处理
5. `IdeActionHandler.kt` - IDE 操作处理

**实现步骤**:
```kotlin
// 1. 定义协议
@Serializable
data class FrontendRequest(val action: String, val data: JsonElement? = null)

@Serializable
data class FrontendResponse(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null
)

// 2. 实现桥接
class FrontendBridge(
    private val project: Project,
    private val browser: JBCefBrowser,
    private val scope: CoroutineScope
) {
    private val handlers = mutableMapOf<String, ActionHandler>()

    init {
        registerHandlers()
        setupBridge()
    }

    private fun registerHandlers() {
        handlers["claude"] = ClaudeActionHandler(project, this)
        handlers["ide"] = IdeActionHandler(project)
        handlers["session"] = SessionActionHandler(project)
    }

    // ... 其他实现
}

// 3. 注册到 ToolWindow
class VueToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val browser = JBCefBrowser()
        val scope = toolWindow.disposable.coroutineScope
        val bridge = FrontendBridge(project, browser, scope)

        // 加载前端
        val frontendUrl = javaClass.getResource("/frontend/index.html")
        browser.loadURL(frontendUrl.toString())

        // 添加到工具窗口
        val content = ContentFactory.getInstance()
            .createContent(browser.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

**任务清单**:
- [ ] 实现 `FrontendBridge` 核心类
- [ ] 实现 `ClaudeActionHandler`
- [ ] 实现 `IdeActionHandler`
- [ ] 实现 `SessionActionHandler`
- [ ] 编写单元测试
- [ ] 更新 `ToolWindowFactory`

#### 3.1.3 Hello World 验证

**目标**: 验证前后端通信是否正常

**前端测试组件**:
```vue
<!-- frontend/src/App.vue -->
<template>
  <div class="app">
    <h1>Hello from Vue!</h1>
    <button @click="testBridge">Test Bridge</button>
    <div v-if="response">{{ response }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'

const response = ref<any>(null)

onMounted(async () => {
  // 等待桥接就绪
  await ideaBridge.waitForReady()
  console.log('✅ Bridge is ready!')
})

async function testBridge() {
  const result = await ideaBridge.query('ide.getTheme')
  response.value = result
  console.log('Theme:', result)
}
</script>
```

**验证步骤**:
1. 启动 IDEA 插件沙箱
2. 打开 Claude Code Plus 工具窗口
3. 看到 "Hello from Vue!" 标题
4. 打开浏览器 DevTools (右键 -> Inspect)
5. 点击 "Test Bridge" 按钮
6. 控制台输出主题信息

**任务清单**:
- [ ] 编写测试组件
- [ ] 配置插件沙箱
- [ ] 验证前端渲染
- [ ] 验证通信桥接
- [ ] 验证主题获取

---

### Phase 2: 核心功能迁移 (Week 2-3)

#### 3.2.1 消息显示组件

**迁移优先级**: P0 (核心功能)

**对应现有代码**:
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/AssistantMessageDisplay.kt`
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/UserMessageDisplay.kt`
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/SimpleMarkdownRenderer.kt`

**新的 Vue 组件**:

```vue
<!-- frontend/src/components/chat/MessageList.vue -->
<template>
  <div class="message-list">
    <div
      v-for="msg in messages"
      :key="msg.id"
      class="message"
      :class="{ 'user': msg.role === 'user', 'assistant': msg.role === 'assistant' }"
    >
      <UserMessage v-if="msg.role === 'user'" :message="msg" />
      <AssistantMessage v-else :message="msg" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMessageStore } from '@/stores/message'
import UserMessage from './UserMessage.vue'
import AssistantMessage from './AssistantMessage.vue'

const messageStore = useMessageStore()
const messages = computed(() => messageStore.messages)
</script>
```

```vue
<!-- frontend/src/components/chat/AssistantMessage.vue -->
<template>
  <div class="assistant-message">
    <div class="avatar">🤖</div>
    <div class="content">
      <MarkdownRenderer v-if="hasText" :content="textContent" />
      <ToolCallDisplay
        v-for="tool in toolCalls"
        :key="tool.id"
        :tool="tool"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Message } from '@/types/claude'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import ToolCallDisplay from '@/components/tool/ToolCallDisplay.vue'

interface Props {
  message: Message
}

const props = defineProps<Props>()

const hasText = computed(() => {
  return props.message.content.some(block => block.type === 'text')
})

const textContent = computed(() => {
  return props.message.content
    .filter(block => block.type === 'text')
    .map(block => block.text)
    .join('\n')
})

const toolCalls = computed(() => {
  return props.message.content.filter(block => block.type === 'tool_use')
})
</script>
```

**迁移步骤**:
1. 定义 `Message` 类型 (复用 SDK 的定义)
2. 创建 `MessageList.vue` 容器组件
3. 创建 `UserMessage.vue` 组件
4. 创建 `AssistantMessage.vue` 组件
5. 集成 Markdown 渲染器
6. 测试消息显示

**任务清单**:
- [ ] 定义消息类型
- [ ] 实现 `MessageList` 组件
- [ ] 实现 `UserMessage` 组件
- [ ] 实现 `AssistantMessage` 组件
- [ ] 测试消息显示功能

#### 3.2.2 Markdown 渲染

**迁移优先级**: P0 (核心功能)

**技术选型**: `markdown-it` + 插件生态

**对应现有代码**:
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/SimpleMarkdownRenderer.kt`

**新实现**:

```typescript
// frontend/src/services/markdownService.ts
import MarkdownIt from 'markdown-it'
import markdownItGfm from 'markdown-it-gfm'
import markdownItAnchor from 'markdown-it-anchor'
import markdownItTocDoneRight from 'markdown-it-toc-done-right'

class MarkdownService {
  private md: MarkdownIt

  constructor() {
    this.md = new MarkdownIt({
      html: false, // 安全考虑,禁用 HTML
      linkify: true,
      typographer: true,
      breaks: true
    })

    // 注册插件
    this.md.use(markdownItGfm) // GitHub Flavored Markdown
    this.md.use(markdownItAnchor)
    this.md.use(markdownItTocDoneRight)

    // 自定义代码块渲染
    this.setupCodeBlockRenderer()
  }

  render(markdown: string): string {
    return this.md.render(markdown)
  }

  private setupCodeBlockRenderer() {
    const defaultFence = this.md.renderer.rules.fence!

    this.md.renderer.rules.fence = (tokens, idx, options, env, slf) => {
      const token = tokens[idx]
      const lang = token.info.trim()
      const code = token.content

      // 返回自定义结构,Vue 组件会接管渲染
      return `<code-block lang="${lang}" code="${encodeURIComponent(code)}"></code-block>`
    }
  }
}

export const markdownService = new MarkdownService()
```

```vue
<!-- frontend/src/components/markdown/MarkdownRenderer.vue -->
<template>
  <div
    class="markdown-body"
    v-html="renderedHtml"
    @click="handleClick"
  ></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { markdownService } from '@/services/markdownService'
import { ideService } from '@/services/ideService'

interface Props {
  content: string
}

const props = defineProps<Props>()

const renderedHtml = computed(() => {
  return markdownService.render(props.content)
})

function handleClick(event: MouseEvent) {
  const target = event.target as HTMLElement

  // 处理文件路径链接点击
  if (target.tagName === 'A') {
    const href = target.getAttribute('href')
    if (href?.startsWith('file://')) {
      event.preventDefault()
      const filePath = href.replace('file://', '')
      ideService.openFile(filePath)
    }
  }
}
</script>

<style>
/* 导入 GitHub Markdown 样式 */
@import 'github-markdown-css';

.markdown-body {
  color: var(--ide-foreground);
  background: transparent;
}

.markdown-body a {
  color: var(--ide-link);
}

.markdown-body code {
  background: var(--ide-code-bg);
  color: var(--ide-foreground);
}
</style>
```

**任务清单**:
- [ ] 配置 `markdown-it` + 插件
- [ ] 实现 `MarkdownRenderer` 组件
- [ ] 适配 IDE 主题样式
- [ ] 处理代码块特殊渲染
- [ ] 处理链接点击事件
- [ ] 测试各种 Markdown 语法

#### 3.2.3 代码块渲染与高亮

**迁移优先级**: P0 (核心功能)

**技术选型**: Shiki (Monaco Editor 使用的同款引擎)

**对应现有代码**:
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/CodeBlockRenderer.kt`

**新实现**:

```vue
<!-- frontend/src/components/markdown/CodeBlock.vue -->
<template>
  <div class="code-block">
    <div class="code-header">
      <span class="language">{{ language }}</span>
      <button @click="copyCode" class="copy-btn">
        {{ copied ? '✓ 已复制' : '复制' }}
      </button>
    </div>
    <pre class="code-content"><code v-html="highlightedCode"></code></pre>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getHighlighter, type Highlighter } from 'shiki'
import { useThemeStore } from '@/stores/theme'

interface Props {
  code: string
  language: string
}

const props = defineProps<Props>()
const themeStore = useThemeStore()
const copied = ref(false)
let highlighter: Highlighter | null = null

onMounted(async () => {
  highlighter = await getHighlighter({
    themes: ['github-light', 'github-dark'],
    langs: ['javascript', 'typescript', 'python', 'java', 'kotlin', 'bash', 'json', 'xml']
  })
})

const highlightedCode = computed(() => {
  if (!highlighter) return escapeHtml(props.code)

  const theme = themeStore.isDark ? 'github-dark' : 'github-light'

  try {
    return highlighter.codeToHtml(props.code, {
      lang: props.language || 'text',
      theme
    })
  } catch {
    return escapeHtml(props.code)
  }
})

async function copyCode() {
  await navigator.clipboard.writeText(props.code)
  copied.value = true
  setTimeout(() => {
    copied.value = false
  }, 2000)
}

function escapeHtml(text: string): string {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}
</script>

<style scoped>
.code-block {
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  overflow: hidden;
  margin: 8px 0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--ide-panel-bg);
  border-bottom: 1px solid var(--ide-border);
}

.language {
  font-size: 12px;
  color: var(--ide-secondary-fg);
  text-transform: uppercase;
}

.copy-btn {
  font-size: 12px;
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--ide-link);
  cursor: pointer;
}

.copy-btn:hover {
  text-decoration: underline;
}

.code-content {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  background: var(--ide-code-bg);
}
</style>
```

**任务清单**:
- [ ] 集成 Shiki 高亮引擎
- [ ] 实现 `CodeBlock` 组件
- [ ] 支持主题切换
- [ ] 实现代码复制功能
- [ ] 支持常见编程语言
- [ ] 测试高亮效果

#### 3.2.4 工具调用展示

**迁移优先级**: P1 (重要功能)

**对应现有代码**:
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/tools/`

**组件结构**:
```
frontend/src/components/tool/
├── ToolCallDisplay.vue          # 工具调用容器
├── CompactToolView.vue          # 紧凑视图
├── ExpandedToolView.vue         # 展开视图
└── specialized/                 # 专业化展示器
    ├── ReadToolDisplay.vue
    ├── EditToolDisplay.vue
    ├── WriteToolDisplay.vue
    ├── BashToolDisplay.vue
    ├── GrepToolDisplay.vue
    └── TodoWriteDisplay.vue
```

**核心组件**:

```vue
<!-- frontend/src/components/tool/ToolCallDisplay.vue -->
<template>
  <div class="tool-call" :class="{ expanded }">
    <div class="tool-header" @click="toggleExpand">
      <span class="tool-icon">{{ toolIcon }}</span>
      <span class="tool-name">{{ tool.name }}</span>
      <span class="tool-status" :class="tool.status">{{ tool.status }}</span>
      <button class="expand-btn">{{ expanded ? '▼' : '▶' }}</button>
    </div>

    <div v-if="expanded" class="tool-body">
      <!-- 专业化展示器 -->
      <component
        :is="specializedComponent"
        v-if="specializedComponent"
        :tool="tool"
        @open-file="handleOpenFile"
        @show-diff="handleShowDiff"
      />

      <!-- 通用展示器 -->
      <GenericToolView v-else :tool="tool" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolCall } from '@/types/claude'
import { ideService } from '@/services/ideService'
import ReadToolDisplay from './specialized/ReadToolDisplay.vue'
import EditToolDisplay from './specialized/EditToolDisplay.vue'
import WriteToolDisplay from './specialized/WriteToolDisplay.vue'
import GenericToolView from './GenericToolView.vue'

interface Props {
  tool: ToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)

// 工具图标映射
const TOOL_ICONS: Record<string, string> = {
  'Read': '📖',
  'Write': '✍️',
  'Edit': '✏️',
  'Bash': '💻',
  'Grep': '🔍',
  'TodoWrite': '✅',
}

const toolIcon = computed(() => TOOL_ICONS[props.tool.name] || '🔧')

// 专业化组件映射
const specializedComponent = computed(() => {
  const componentMap: Record<string, any> = {
    'Read': ReadToolDisplay,
    'Edit': EditToolDisplay,
    'Write': WriteToolDisplay,
  }
  return componentMap[props.tool.name]
})

function toggleExpand() {
  expanded.value = !expanded.value
}

function handleOpenFile(filePath: string, line?: number) {
  ideService.openFile(filePath, line)
}

function handleShowDiff(filePath: string, oldContent: string, newContent: string) {
  ideService.showDiff(filePath, oldContent, newContent)
}
</script>
```

**任务清单**:
- [ ] 实现 `ToolCallDisplay` 容器
- [ ] 实现紧凑/展开视图切换
- [ ] 实现 `ReadToolDisplay`
- [ ] 实现 `EditToolDisplay`
- [ ] 实现 `WriteToolDisplay`
- [ ] 实现其他工具展示器
- [ ] 测试工具调用展示

#### 3.2.5 输入区域

**迁移优先级**: P0 (核心功能)

**对应现有代码**:
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/InputArea.kt`

**新实现**:

```vue
<!-- frontend/src/components/chat/InputArea.vue -->
<template>
  <div class="input-area">
    <!-- 上下文引用显示 -->
    <div v-if="contextRefs.length > 0" class="context-refs">
      <div
        v-for="ref in contextRefs"
        :key="ref.id"
        class="context-ref"
      >
        <span class="ref-icon">{{ ref.icon }}</span>
        <span class="ref-path">{{ ref.path }}</span>
        <button @click="removeRef(ref.id)" class="remove-btn">×</button>
      </div>
    </div>

    <!-- 输入框 -->
    <textarea
      ref="inputEl"
      v-model="inputText"
      placeholder="输入消息... (Ctrl+Enter 发送)"
      @keydown="handleKeydown"
      @paste="handlePaste"
      @drop="handleDrop"
      class="input-textarea"
    ></textarea>

    <!-- 工具栏 -->
    <div class="input-toolbar">
      <button @click="attachFile" title="添加文件引用">
        📎 附加文件
      </button>
      <button
        @click="sendMessage"
        :disabled="!canSend"
        class="send-btn"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessageStore } from '@/stores/message'
import { claudeService } from '@/services/claudeService'
import type { ContextRef } from '@/types/context'

const messageStore = useMessageStore()
const inputText = ref('')
const contextRefs = ref<ContextRef[]>([])
const inputEl = ref<HTMLTextAreaElement>()

const canSend = computed(() => {
  return inputText.value.trim().length > 0
})

async function sendMessage() {
  if (!canSend.value) return

  const message = inputText.value.trim()
  const refs = contextRefs.value

  // 清空输入
  inputText.value = ''
  contextRefs.value = []

  // 添加到消息列表
  messageStore.addUserMessage(message, refs)

  // 发送到 Claude
  await claudeService.query(message)
}

function handleKeydown(event: KeyboardEvent) {
  // Ctrl+Enter 发送
  if (event.ctrlKey && event.key === 'Enter') {
    event.preventDefault()
    sendMessage()
  }

  // Ctrl+U 删除到行首
  if (event.ctrlKey && event.key === 'u') {
    event.preventDefault()
    const textarea = inputEl.value!
    const start = textarea.selectionStart
    const text = textarea.value
    const lineStart = text.lastIndexOf('\n', start - 1) + 1
    inputText.value = text.slice(0, lineStart) + text.slice(start)
    textarea.setSelectionRange(lineStart, lineStart)
  }
}

function handlePaste(event: ClipboardEvent) {
  // 处理粘贴文件路径
  const text = event.clipboardData?.getData('text')
  if (text && text.startsWith('file://')) {
    event.preventDefault()
    addFileRef(text.replace('file://', ''))
  }
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  // 处理拖放文件
  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    Array.from(files).forEach(file => {
      addFileRef(file.path)
    })
  }
}

function attachFile() {
  // 触发文件选择对话框 (通过 IDE)
  ideService.selectFiles().then(files => {
    files.forEach(addFileRef)
  })
}

function addFileRef(filePath: string) {
  contextRefs.value.push({
    id: Date.now().toString(),
    type: 'file',
    path: filePath,
    icon: '📄'
  })
}

function removeRef(id: string) {
  contextRefs.value = contextRefs.value.filter(ref => ref.id !== id)
}
</script>

<style scoped>
.input-area {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--ide-border);
  background: var(--ide-panel-bg);
}

.context-refs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: var(--ide-info-bg);
}

.context-ref {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--ide-background);
  border: 1px solid var(--ide-border);
  font-size: 12px;
}

.remove-btn {
  border: none;
  background: none;
  color: var(--ide-error);
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
}

.input-textarea {
  flex: 1;
  min-height: 80px;
  max-height: 200px;
  padding: 12px;
  border: none;
  background: var(--ide-input-bg);
  color: var(--ide-foreground);
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
  outline: none;
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--ide-panel-bg);
}

.send-btn {
  background: var(--ide-accent);
  color: white;
  border: none;
  padding: 6px 16px;
  border-radius: 4px;
  cursor: pointer;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
```

**任务清单**:
- [ ] 实现 `InputArea` 组件
- [ ] 实现上下文引用功能
- [ ] 实现快捷键支持
- [ ] 实现拖放文件功能
- [ ] 实现自动高度调整
- [ ] 测试输入功能

---

### Phase 3: 高级功能 (Week 4)

#### 3.3.1 会话管理

**迁移优先级**: P1 (重要功能)

**对应现有代码**:
- `toolwindow/src/main/kotlin/com/claudecodeplus/ui/session/SessionManager.kt`

**新的状态管理**:

```typescript
// frontend/src/stores/session.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Session } from '@/types/session'
import { ideaBridge } from '@/services/ideaBridge'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<Session[]>([])
  const currentSessionId = ref<string | null>(null)

  const currentSession = computed(() => {
    return sessions.value.find(s => s.id === currentSessionId.value)
  })

  async function loadSessions() {
    const response = await ideaBridge.query('session.list')
    if (response.success) {
      sessions.value = response.data.sessions
    }
  }

  async function createSession(name?: string) {
    const response = await ideaBridge.query('session.create', { name })
    if (response.success) {
      const newSession = response.data.session
      sessions.value.push(newSession)
      currentSessionId.value = newSession.id
    }
  }

  async function switchSession(sessionId: string) {
    const response = await ideaBridge.query('session.switch', { sessionId })
    if (response.success) {
      currentSessionId.value = sessionId
    }
  }

  async function deleteSession(sessionId: string) {
    const response = await ideaBridge.query('session.delete', { sessionId })
    if (response.success) {
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = sessions.value[0]?.id || null
      }
    }
  }

  async function renameSession(sessionId: string, newName: string) {
    const response = await ideaBridge.query('session.rename', { sessionId, name: newName })
    if (response.success) {
      const session = sessions.value.find(s => s.id === sessionId)
      if (session) {
        session.name = newName
      }
    }
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    loadSessions,
    createSession,
    switchSession,
    deleteSession,
    renameSession
  }
})
```

**会话列表组件**:

```vue
<!-- frontend/src/components/session/SessionTabBar.vue -->
<template>
  <div class="session-tab-bar">
    <div class="session-tabs">
      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-tab"
        :class="{ active: session.id === currentSessionId }"
        @click="switchSession(session.id)"
      >
        <input
          v-if="editingId === session.id"
          v-model="editingName"
          @blur="finishEdit"
          @keydown.enter="finishEdit"
          @keydown.esc="cancelEdit"
          class="session-name-input"
          ref="nameInput"
        />
        <span v-else class="session-name">{{ session.name }}</span>

        <div class="session-actions">
          <button @click.stop="startEdit(session)" title="重命名">✏️</button>
          <button @click.stop="deleteSession(session.id)" title="删除">×</button>
        </div>
      </div>
    </div>

    <button @click="createSession" class="new-session-btn" title="新建会话">
      + 新会话
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useSessionStore } from '@/stores/session'

const sessionStore = useSessionStore()
const { sessions, currentSessionId } = storeToRefs(sessionStore)

const editingId = ref<string | null>(null)
const editingName = ref('')
const nameInput = ref<HTMLInputElement>()

function switchSession(sessionId: string) {
  sessionStore.switchSession(sessionId)
}

function createSession() {
  sessionStore.createSession('新会话')
}

function deleteSession(sessionId: string) {
  if (confirm('确定删除此会话吗?')) {
    sessionStore.deleteSession(sessionId)
  }
}

async function startEdit(session: any) {
  editingId.value = session.id
  editingName.value = session.name
  await nextTick()
  nameInput.value?.focus()
  nameInput.value?.select()
}

function finishEdit() {
  if (editingId.value && editingName.value.trim()) {
    sessionStore.renameSession(editingId.value, editingName.value.trim())
  }
  cancelEdit()
}

function cancelEdit() {
  editingId.value = null
  editingName.value = ''
}
</script>
```

**任务清单**:
- [ ] 实现 `useSessionStore`
- [ ] 实现 `SessionTabBar` 组件
- [ ] 实现会话切换功能
- [ ] 实现会话重命名功能
- [ ] 实现会话删除功能
- [ ] 测试会话管理

#### 3.3.2 主题系统

**迁移优先级**: P1 (重要功能)

**实现方案**: 已在前面详细说明,这里补充完整实现

**任务清单**:
- [ ] 实现 `ThemeProvider` (Kotlin)
- [ ] 实现 `useThemeStore` (Vue)
- [ ] 实现主题变化监听
- [ ] 实现 CSS 变量注入
- [ ] 适配所有组件样式
- [ ] 测试主题切换

#### 3.3.3 设置面板

**迁移优先级**: P2 (可选功能)

**新实现**:

```vue
<!-- frontend/src/components/settings/SettingsPanel.vue -->
<template>
  <div class="settings-panel">
    <h2>设置</h2>

    <div class="setting-group">
      <h3>模型配置</h3>
      <label>
        模型:
        <select v-model="settings.model">
          <option value="claude-sonnet-4-5-20250929">Claude Sonnet 4.5</option>
          <option value="claude-opus-4-20250514">Claude Opus 4</option>
        </select>
      </label>

      <label>
        最大轮次:
        <input type="number" v-model.number="settings.maxTurns" min="1" max="100" />
      </label>
    </div>

    <div class="setting-group">
      <h3>权限模式</h3>
      <label>
        <input type="radio" v-model="settings.permissionMode" value="auto" />
        自动批准
      </label>
      <label>
        <input type="radio" v-model="settings.permissionMode" value="manual" />
        手动确认
      </label>
    </div>

    <div class="setting-actions">
      <button @click="saveSettings">保存</button>
      <button @click="resetSettings">重置</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'

const settings = ref({
  model: 'claude-sonnet-4-5-20250929',
  maxTurns: 10,
  permissionMode: 'auto'
})

onMounted(async () => {
  const response = await ideaBridge.query('settings.get')
  if (response.success) {
    settings.value = response.data.settings
  }
})

async function saveSettings() {
  const response = await ideaBridge.query('settings.save', settings.value)
  if (response.success) {
    alert('设置已保存')
  }
}

function resetSettings() {
  settings.value = {
    model: 'claude-sonnet-4-5-20250929',
    maxTurns: 10,
    permissionMode: 'auto'
  }
}
</script>
```

**任务清单**:
- [ ] 实现 `SettingsPanel` 组件
- [ ] 实现设置持久化
- [ ] 实现设置重置功能
- [ ] 测试设置功能

---

### Phase 4: 测试与优化 (Week 5)

#### 3.4.1 单元测试

**前端测试** (Vitest):

```typescript
// frontend/tests/components/MessageList.spec.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageList from '@/components/chat/MessageList.vue'
import { createPinia, setActivePinia } from 'pinia'

describe('MessageList', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders user messages', () => {
    const wrapper = mount(MessageList)
    const messageStore = useMessageStore()

    messageStore.addUserMessage('Hello')

    expect(wrapper.find('.user-message').text()).toContain('Hello')
  })

  it('renders assistant messages', () => {
    const wrapper = mount(MessageList)
    const messageStore = useMessageStore()

    messageStore.addAssistantMessage('Hi there!')

    expect(wrapper.find('.assistant-message').text()).toContain('Hi there!')
  })
})
```

**后端测试** (JUnit):

```kotlin
// jetbrains-plugin/src/test/kotlin/FrontendBridgeTest.kt
class FrontendBridgeTest {
    @Test
    fun `test query handler`() {
        val bridge = FrontendBridge(project, browser, scope)

        val request = FrontendRequest("ide.getTheme")
        val response = bridge.handleRequest(request)

        assertTrue(response.success)
        assertNotNull(response.data)
    }
}
```

**任务清单**:
- [ ] 编写前端组件测试
- [ ] 编写前端 Store 测试
- [ ] 编写后端桥接测试
- [ ] 编写 E2E 测试
- [ ] 达到 80% 代码覆盖率

#### 3.4.2 性能优化

**前端优化**:
- 虚拟滚动 (vue-virtual-scroller)
- 代码分割 (Vite 自动处理)
- 组件懒加载
- Markdown 渲染缓存
- 防抖节流

**后端优化**:
- 消息批量推送
- 减少不必要的主题同步
- 优化 JSON 序列化

**任务清单**:
- [ ] 实现虚拟滚动
- [ ] 优化 Markdown 渲染
- [ ] 优化消息推送频率
- [ ] 性能基准测试
- [ ] 内存占用测试

#### 3.4.3 用户体验优化

**动画与过渡**:
```vue
<transition-group name="message" tag="div">
  <div v-for="msg in messages" :key="msg.id">
    <!-- ... -->
  </div>
</transition-group>

<style>
.message-enter-active {
  transition: all 0.3s ease;
}

.message-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
</style>
```

**加载状态**:
```vue
<div v-if="loading" class="loading-indicator">
  <div class="spinner"></div>
  <span>Claude 正在思考...</span>
</div>
```

**任务清单**:
- [ ] 添加消息进入动画
- [ ] 添加加载状态指示
- [ ] 添加错误提示
- [ ] 优化滚动行为
- [ ] 添加键盘快捷键提示

---

### Phase 5: 兼容性与发布 (Week 6)

#### 3.5.1 多平台测试

**测试矩阵**:
| 平台 | IDE 版本 | 测试状态 |
|------|---------|---------|
| Windows 11 | 2024.3 | ⬜ |
| Windows 11 | 2025.1 | ⬜ |
| macOS Sonoma | 2024.3 | ⬜ |
| macOS Sonoma | 2025.1 | ⬜ |
| Linux (Ubuntu) | 2024.3 | ⬜ |
| Linux (Ubuntu) | 2025.1 | ⬜ |

**任务清单**:
- [ ] Windows 平台测试
- [ ] macOS 平台测试
- [ ] Linux 平台测试
- [ ] 修复平台特定问题
- [ ] 文档更新

#### 3.5.2 向后兼容

**数据迁移**:
- 会话状态迁移
- 配置文件迁移
- 缓存数据迁移

**Compose 回退**:
```kotlin
// 保留一个开关,允许用户回退到 Compose UI
object UiConfig {
    var useVueFrontend: Boolean = true // 默认使用 Vue

    fun shouldUseCompose(): Boolean {
        return !useVueFrontend || !isJcefAvailable()
    }
}
```

**任务清单**:
- [ ] 实现数据迁移脚本
- [ ] 提供 UI 切换选项
- [ ] 编写迁移指南
- [ ] 测试回退机制

#### 3.5.3 文档更新

**需要更新的文档**:
- [ ] `README.md` - 项目介绍
- [ ] `CLAUDE.md` - 索引文档
- [ ] `docs/ARCHITECTURE.md` - 架构文档
- [ ] `docs/DEVELOPMENT.md` - 开发指南
- [ ] `docs/MIGRATION_GUIDE.md` - 迁移指南

---

## 四、风险评估与应对

### 4.1 技术风险

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|---------|
| JCEF 兼容性问题 | 高 | 中 | 提供 Compose 回退方案 |
| 性能不达标 | 高 | 低 | 性能基准测试,优化关键路径 |
| 主题适配不完美 | 中 | 中 | 增加主题测试覆盖率 |
| 通信桥接不稳定 | 高 | 低 | 完善错误处理和重连机制 |

### 4.2 项目风险

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|---------|
| 工期延误 | 中 | 中 | 分阶段交付,优先核心功能 |
| 资源不足 | 高 | 低 | 提前规划,合理分配任务 |
| 需求变更 | 中 | 中 | 敏捷迭代,保持灵活性 |

---

## 五、成功标准

### 5.1 功能完整性

- ✅ 所有现有功能正常工作
- ✅ 用户体验不下降
- ✅ 无严重 Bug

### 5.2 性能指标

- ✅ 内存占用增加 < 150MB
- ✅ 消息渲染延迟 < 100ms
- ✅ Markdown 渲染延迟 < 200ms
- ✅ 主题切换延迟 < 50ms

### 5.3 代码质量

- ✅ 单元测试覆盖率 > 80%
- ✅ 无严重代码坏味道
- ✅ 文档完整更新

---

## 六、时间表与里程碑

```
Week 1: [████████░░░░░░░░░░░░░░] 基础设施搭建
Week 2: [████████████░░░░░░░░░░] 核心功能迁移 - Part 1
Week 3: [████████████████░░░░░░] 核心功能迁移 - Part 2
Week 4: [████████████████████░░] 高级功能
Week 5: [██████████████████████] 测试与优化
Week 6: [██████████████████████] 兼容性与发布
```

**关键里程碑**:

| 日期 | 里程碑 | 交付物 |
|------|--------|--------|
| Week 1 End | M1: Hello World | 前后端通信验证 |
| Week 2 End | M2: 基础聊天 | 消息显示 + Markdown 渲染 |
| Week 3 End | M3: 完整聊天 | + 工具调用 + 输入 |
| Week 4 End | M4: 高级功能 | + 会话管理 + 主题 |
| Week 5 End | M5: 测试完成 | 测试覆盖率 > 80% |
| Week 6 End | M6: 正式发布 | 合并到主分支 |

---

## 七、团队协作

### 7.1 分工建议

| 角色 | 负责模块 | 工作量 |
|------|---------|--------|
| 前端开发 | Vue 组件 + 状态管理 | 60% |
| 后端开发 | Kotlin 桥接 + 服务层 | 30% |
| 测试 | 单元测试 + E2E 测试 | 10% |

### 7.2 协作流程

1. **每日站会** (15 分钟)
   - 进度同步
   - 问题讨论
   - 任务分配

2. **代码审查**
   - 所有 PR 需要审查
   - 关键模块双人审查

3. **文档同步**
   - 及时更新文档
   - 记录重要决策

---

## 八、参考资源

### 8.1 技术文档

- [Vue 3 官方文档](https://vuejs.org/)
- [Pinia 文档](https://pinia.vuejs.org/)
- [JCEF 文档](https://plugins.jetbrains.com/docs/intellij/jcef.html)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)

### 8.2 参考实现

- [GitHub Copilot Chat](https://github.com/github/copilot-docs) - AI 聊天界面参考
- [AWS Toolkit](https://github.com/aws/aws-toolkit-jetbrains) - JCEF 使用案例
- [Claude Agent SDK (Python)](https://github.com/anthropics/claude-agent-sdk-python) - SDK 参考

---

## 九、附录

### 9.1 关键文件清单

**需要新建的文件**:
```
frontend/                            # 整个前端项目 (新建)
├── src/
│   ├── components/                  # ~20 个 Vue 组件
│   ├── services/                    # ~5 个服务类
│   ├── stores/                      # ~3 个 Pinia store
│   └── types/                       # ~5 个类型定义文件

jetbrains-plugin/src/main/kotlin/
├── com/claudecodeplus/bridge/       # 新建
│   ├── FrontendBridge.kt
│   ├── ClaudeActionHandler.kt
│   └── IdeActionHandler.kt
└── com/claudecodeplus/theme/        # 新建
    └── ThemeProvider.kt

docs/                                # 新建文档
├── VUE_MIGRATION_PLAN.md           # 本文档
├── ARCHITECTURE_V2.md              # 新架构文档
└── MIGRATION_GUIDE.md              # 用户迁移指南
```

**需要删除的文件**:
```
toolwindow/                          # 整个 toolwindow 模块
└── src/main/kotlin/                 # ~50 个 Compose 组件文件
```

**需要修改的文件**:
```
CLAUDE.md                            # 更新索引
README.md                            # 更新介绍
build.gradle.kts                     # 移除 Compose 依赖
settings.gradle.kts                  # 移除 toolwindow 模块
```

### 9.2 依赖变更

**移除的依赖**:
```kotlin
// build.gradle.kts
dependencies {
    // ❌ 移除 Compose Desktop
    // implementation(compose.desktop.currentOs)
    // implementation("org.jetbrains.jewel:jewel-ide-laf-bridge-...")
}
```

**新增的依赖**:
```kotlin
// build.gradle.kts
dependencies {
    // ✅ JCEF (IntelliJ 平台自带)
    // ✅ Ktor (如需本地服务器,可选)
    implementation("io.ktor:ktor-server-core:2.3.7")
}
```

### 9.3 配置变更

**plugin.xml 变更**:
```xml
<!-- 移除 -->
<extensions defaultExtensionNs="com.intellij">
  <!-- ❌ 移除 toolwindow 的 Compose 实现 -->
</extensions>

<!-- 新增 -->
<extensions defaultExtensionNs="com.intellij">
  <!-- ✅ 新的 JCEF ToolWindowFactory -->
  <toolWindow
    id="Claude Code Plus"
    anchor="right"
    factoryClass="com.claudecodeplus.toolwindow.VueToolWindowFactory"
  />
</extensions>
```

---

## 十、常见问题 (FAQ)

### Q1: 为什么选择 Vue 而不是 React?

**A**: Vue 3 的 Composition API 更接近 Kotlin 的函数式风格,学习曲线更平缓,工具链更统一。

### Q2: JCEF 的内存占用会不会太大?

**A**: JCEF 内置在 IntelliJ 平台中,不会额外下载。实际测试中,一个简单的 JCEF 页面增加约 50-100MB 内存,复杂应用约 150MB,在可接受范围内。

### Q3: 如果 JCEF 不可用怎么办?

**A**: 提供 Compose 回退方案,用户可以在设置中切换。

### Q4: 迁移会影响现有用户吗?

**A**: 不会。我们会:
1. 保持 API 兼容
2. 自动迁移用户数据
3. 提供 UI 切换选项

### Q5: 性能会下降吗?

**A**: 理论上会有轻微下降,但通过优化可以做到用户无感知:
- 虚拟滚动减少 DOM 数量
- 代码分割减少初始加载
- 缓存机制减少重复计算

---

## 结语

本迁移方案经过充分调研和设计,目标是在保持功能完整性的前提下,提升开发效率和用户体验。

**关键成功因素**:
1. ✅ 分阶段交付,降低风险
2. ✅ 保持向后兼容,用户平滑升级
3. ✅ 充分测试,保证质量
4. ✅ 及时沟通,快速响应问题

让我们开始这次激动人心的架构升级吧! 🚀
