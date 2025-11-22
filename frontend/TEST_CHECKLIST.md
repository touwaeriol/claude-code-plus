# 前端测试检查清单

## ✅ Stream Event 处理功能测试

### 1. 类型守卫函数测试
- [x] `parseStreamEventData` - 解析 Stream Event 数据
- [x] `isMessageStartEvent` - 识别消息开始事件
- [x] `isContentBlockDeltaEvent` - 识别内容块增量事件
- [x] `isTextDelta` - 识别文本增量
- [x] `isInputJsonDelta` - 识别工具输入 JSON 增量
- [x] `isThinkingDelta` - 识别 Thinking 增量

### 2. 增量更新函数测试
- [x] `applyTextDelta` - 处理文本增量更新
- [x] `applyInputJsonDelta` - 处理工具输入 JSON 增量更新
- [x] `applyThinkingDelta` - 处理 Thinking 增量更新

### 3. 集成测试
- [x] `sessionStore.handleStreamEvent` - 完整的事件处理流程
- [x] 消息占位符创建和管理
- [x] 工具调用块创建和更新
- [x] Thinking 块创建和更新

## 🔍 测试步骤

### 步骤 1: 启动开发服务器
```bash
cd frontend
npm run dev
```
✅ 开发服务器已在端口 5174 运行

### 步骤 2: 打开浏览器
访问: http://localhost:5174

### 步骤 3: 测试 Stream Event 处理

#### 3.1 测试文本流式响应
1. 发送一条消息（如："你好"）
2. 观察控制台日志，应该看到：
   - `📡 [handleStreamEvent] 处理事件类型: message_start`
   - `📝 [handleStreamEvent] 更新文本块 #0, 当前长度: X`
3. 检查消息是否正确显示在界面上

#### 3.2 测试工具调用
1. 发送一条需要工具调用的消息（如："读取 gradle.properties 文件"）
2. 观察控制台日志，应该看到：
   - `🔧 [handleStreamEvent] 添加工具调用块: read at index X`
   - `🔧 [handleStreamEvent] 更新工具输入 JSON: read, 累积长度: X`
3. 检查工具调用是否正确显示

#### 3.3 测试 Thinking 块
1. 发送一条可能触发 Thinking 的消息
2. 观察控制台日志，应该看到：
   - `💭 [handleStreamEvent] Thinking 块开始 at index X`
   - `💭 [handleStreamEvent] 更新 Thinking 块 #X`
3. 检查 Thinking 内容是否正确显示

### 步骤 4: 检查控制台错误
打开浏览器开发者工具（F12），检查：
- [ ] 是否有 JavaScript 错误
- [ ] 是否有 TypeScript 类型错误
- [ ] 是否有网络请求失败
- [ ] 是否有 WebSocket 连接错误

### 步骤 5: 验证响应式更新
- [ ] 消息内容应该实时更新（流式显示）
- [ ] 工具调用应该实时显示
- [ ] UI 应该响应状态变化

## 🐛 已知问题

1. **vue-tsc 类型检查错误**
   - 问题：`vue-tsc` 在 Windows 上可能有问题
   - 状态：不影响运行时，仅影响类型检查
   - 解决方案：使用 IDE 的类型检查功能

2. **控制台乱码**
   - 问题：PowerShell 控制台显示乱码
   - 状态：需要设置正确的编码
   - 解决方案：设置 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`

## 📝 测试结果记录

### 测试日期: 2025-11-22

#### 功能测试
- [ ] 文本流式响应 ✅/❌
- [ ] 工具调用显示 ✅/❌
- [ ] Thinking 块显示 ✅/❌
- [ ] 消息状态管理 ✅/❌

#### 性能测试
- [ ] 大量消息处理 ✅/❌
- [ ] 长时间连接 ✅/❌
- [ ] 内存使用 ✅/❌

#### 错误处理
- [ ] 网络断开恢复 ✅/❌
- [ ] 无效事件处理 ✅/❌
- [ ] 错误消息显示 ✅/❌

## 🔧 调试技巧

### 查看 Stream Event 日志
在浏览器控制台中，应该能看到以下日志：
- `📡 [handleStreamEvent] 处理事件类型: XXX`
- `📝 [handleStreamEvent] 更新文本块 #X`
- `🔧 [handleStreamEvent] 添加工具调用块: XXX`
- `💭 [handleStreamEvent] Thinking 块开始 at index X`

### 检查消息状态
在 Vue DevTools 中检查 `sessionStore`：
- `messages` 数组应该包含所有消息
- 最后一条 assistant 消息应该实时更新
- `isGenerating` 状态应该正确切换

### 网络调试
在 Network 标签中：
- 检查 WebSocket 连接 (`/ws`)
- 检查 HTTP API 请求 (`/api/`)
- 查看请求和响应内容

