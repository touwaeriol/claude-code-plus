# Claude Code Plus - Vue Frontend

这是 Claude Code Plus 的 Vue 3 前端部分,使用 JCEF 在 IntelliJ IDEA 中运行。

## 🚀 快速开始

### 开发模式

1. **安装依赖**:
```bash
npm install
```

2. **启动开发服务器**:
```bash
npm run dev
```

3. **在 IDEA 中测试**:
   - 运行插件沙箱 (Run Plugin)
   - 打开 "Claude Code Plus (Vue)" 工具窗口
   - 前端会从 `http://localhost:5173` 加载 (热重载)

### 生产模式

1. **构建前端**:
```bash
npm run build
```

2. **构建产物位置**:
```
../jetbrains-plugin/src/main/resources/frontend/
```

3. **在 IDEA 中测试**:
   - 运行插件沙箱
   - 打开 "Claude Code Plus (Vue)" 工具窗口
   - 前端从打包的资源加载

## 📁 项目结构

```
frontend/
├── src/
│   ├── components/          # Vue 组件 (待扩展)
│   ├── services/            # 服务层
│   │   └── ideaBridge.ts    # IDEA 通信桥接
│   ├── stores/              # Pinia 状态管理 (待扩展)
│   ├── types/               # TypeScript 类型定义
│   │   └── bridge.ts        # 桥接协议类型
│   ├── App.vue              # 根组件 (POC 测试页面)
│   └── main.ts              # 入口文件
├── index.html               # HTML 模板
├── vite.config.ts           # Vite 配置
├── tsconfig.json            # TypeScript 配置
└── package.json             # 项目配置
```

## 🔌 通信协议

### 前端调用后端 (Request/Response)

```typescript
import { ideaBridge } from '@/services/ideaBridge'

// 调用后端 API
const response = await ideaBridge.query('ide.getTheme')
// 返回: { success: boolean, data?: any, error?: string }
```

### 后端推送前端 (Event Push)

```typescript
import { ideaBridge } from '@/services/ideaBridge'

// 监听后端事件
ideaBridge.on('claude.message', (data) => {
  console.log('Received message:', data)
})
```

### 便捷 API

```typescript
import { ideService, claudeService } from '@/services/ideaBridge'

// IDE 操作
await ideService.getTheme()
await ideService.openFile('/path/to/file.kt', 42)
await ideService.showDiff('/path', 'old', 'new')

// Claude 操作
await claudeService.connect()
await claudeService.query('Hello Claude!')
claudeService.onMessage((msg) => console.log(msg))
```

## 🧪 POC 测试功能

当前 `App.vue` 提供以下测试功能:

### 1. 通信桥接测试
- 点击 "测试桥接" 验证前后端通信
- 发送 `test.ping` 请求
- 显示响应结果

### 2. 获取 IDE 主题
- 点击 "获取主题" 按钮
- 显示当前 IDE 主题的所有颜色值
- 验证主题提取功能

### 3. Claude 消息测试
- 输入消息并点击 "发送"
- 调用 `claude.query` API
- 显示用户消息和 AI 响应
- 验证完整的消息流程

## 🛠️ 开发指南

### 添加新组件

```bash
# 在 src/components/ 目录创建组件
src/components/chat/MessageList.vue
```

### 添加新 API

1. **在后端定义处理器** (`FrontendBridge.kt`):
```kotlin
private fun handleRequest(request: FrontendRequest): FrontendResponse {
    return when (request.action) {
        "myFeature.doSomething" -> handleMyFeature(request)
        // ...
    }
}
```

2. **在前端调用** (`ideaBridge.ts`):
```typescript
export const myFeatureService = {
  async doSomething() {
    return ideaBridge.query('myFeature.doSomething')
  }
}
```

### 调试技巧

1. **浏览器 DevTools**:
   - 在 JCEF 页面右键 -> "Inspect"
   - 查看 Console 日志
   - 调试 JavaScript 代码

2. **日志输出**:
```typescript
console.log('🚀 Debug info:', data)
```

3. **Kotlin 日志**:
```kotlin
logger.info("📨 Received request: $request")
```

## 📦 构建配置

### Vite 配置要点

```typescript
// vite.config.ts
export default defineConfig({
  build: {
    // 构建到插件资源目录
    outDir: '../jetbrains-plugin/src/main/resources/frontend',
    // 使用相对路径 (JCEF 要求)
  },
  base: './'
})
```

### TypeScript 配置

```json
// tsconfig.json
{
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"]  // 路径别名
    }
  }
}
```

## 🐛 常见问题

### Q: 前端页面不显示?

**A**: 检查以下几点:
1. 是否运行了 `npm run build`?
2. 构建产物是否在 `jetbrains-plugin/src/main/resources/frontend/`?
3. 查看 IDEA 日志是否有错误

### Q: 通信桥接失败?

**A**:
1. 打开浏览器 DevTools 查看错误
2. 检查 `window.ideaBridge` 是否存在
3. 查看 Kotlin 后端日志

### Q: 热重载不工作?

**A**:
1. 确保 Vite dev server 在运行 (`npm run dev`)
2. 检查端口 5173 是否被占用
3. 刷新 IDEA 工具窗口

## 📚 参考资源

- [Vue 3 文档](https://vuejs.org/)
- [Vite 文档](https://vitejs.dev/)
- [JCEF 文档](https://plugins.jetbrains.com/docs/intellij/jcef.html)
- [迁移方案](../docs/VUE_MIGRATION_PLAN.md)

## 🎯 下一步计划

- [ ] 实现完整的消息列表组件
- [ ] 集成 Markdown 渲染器
- [ ] 添加代码块语法高亮
- [ ] 实现会话管理
- [ ] 适配 IDE 主题样式
- [ ] 性能优化 (虚拟滚动)

---

**状态**: 🟢 POC 阶段 - 基础通信已验证
**最后更新**: 2025-01-03
