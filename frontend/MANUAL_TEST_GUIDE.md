# 手动测试指南 - Stream Event 处理

## 🌐 访问地址
http://localhost:5174/

## 🔍 测试步骤

### 1. 打开浏览器开发者工具
- 按 `F12` 或右键点击页面 → "检查"
- 切换到 **Console** 标签
- 切换到 **Network** 标签（可选，用于查看网络请求）

### 2. 测试基础连接
1. 打开页面后，检查控制台是否有错误
2. 应该看到类似 `✅ Environment: Browser Mode` 或 `✅ Environment: IDEA Plugin Mode` 的日志

### 3. 测试文本流式响应

#### 测试步骤：
1. 在聊天输入框中输入：`你好`
2. 点击发送按钮
3. **观察控制台日志**，应该看到：
   ```
   📡 [handleStreamEvent] 处理事件类型: message_start
   📝 [handleStreamEvent] 更新文本块 #0, 当前长度: X
   📝 [handleStreamEvent] 更新文本块 #0, 当前长度: Y
   ...
   📡 [handleStreamEvent] 处理事件类型: message_stop
   ```
4. **检查界面**：
   - 消息应该流式显示（逐字出现）
   - 消息内容应该正确显示

#### 预期结果：
- ✅ 控制台有正确的日志输出
- ✅ 消息流式显示
- ✅ 无 JavaScript 错误

### 4. 测试工具调用（Read 工具）

#### 测试步骤：
1. 在聊天输入框中输入：`读取 gradle.properties 文件`
2. 点击发送按钮
3. **观察控制台日志**，应该看到：
   ```
   📡 [handleStreamEvent] 处理事件类型: message_start
   🔧 [handleStreamEvent] 添加工具调用块: read at index 0
   🔧 [handleStreamEvent] 更新工具输入 JSON: read, 累积长度: X
   ...
   📡 [handleStreamEvent] 处理事件类型: message_stop
   ```
4. **检查界面**：
   - 应该显示工具调用卡片
   - 工具名称应该是 `read`
   - 工具参数应该正确显示

#### 预期结果：
- ✅ 工具调用块正确创建
- ✅ 工具输入 JSON 正确累积
- ✅ 工具卡片正确显示

### 5. 测试工具调用（Write 工具）

#### 测试步骤：
1. 在聊天输入框中输入：`创建一个测试文件 test.txt，内容为 "Hello World"`
2. 点击发送按钮
3. **观察控制台日志**，应该看到：
   ```
   🔧 [handleStreamEvent] 添加工具调用块: write at index 0
   🔧 [handleStreamEvent] 更新工具输入 JSON: write, 累积长度: X
   ```
4. **检查界面**：
   - 应该显示 `write` 工具调用卡片
   - 文件路径和内容应该正确显示

#### 预期结果：
- ✅ Write 工具调用正确显示
- ✅ 文件路径和内容正确

### 6. 测试工具调用（Edit 工具）

#### 测试步骤：
1. 在聊天输入框中输入：`编辑 test.txt，将 "Hello" 改为 "Hi"`
2. 点击发送按钮
3. **观察控制台日志**，应该看到：
   ```
   🔧 [handleStreamEvent] 添加工具调用块: edit at index 0
   🔧 [handleStreamEvent] 更新工具输入 JSON: edit, 累积长度: X
   ```
4. **检查界面**：
   - 应该显示 `edit` 工具调用卡片
   - oldString 和 newString 应该正确显示

#### 预期结果：
- ✅ Edit 工具调用正确显示
- ✅ 编辑内容正确显示

### 7. 测试 Thinking 块（如果支持）

#### 测试步骤：
1. 发送一条可能触发 Thinking 的消息
2. **观察控制台日志**，应该看到：
   ```
   💭 [handleStreamEvent] Thinking 块开始 at index 0
   💭 [handleStreamEvent] 更新 Thinking 块 #0
   ```
3. **检查界面**：
   - 应该显示 Thinking 内容（如果有 UI 支持）

#### 预期结果：
- ✅ Thinking 块正确创建和更新
- ✅ Thinking 内容正确显示

### 8. 测试错误处理

#### 测试步骤：
1. 断开网络连接（或停止后端服务器）
2. 尝试发送消息
3. **观察控制台**，应该看到错误信息
4. 重新连接网络（或重启服务器）
5. 再次发送消息，应该能正常处理

#### 预期结果：
- ✅ 错误信息正确显示
- ✅ 重连后功能正常

## 📊 检查清单

### 控制台日志检查
- [ ] 看到 `📡 [handleStreamEvent] 处理事件类型: XXX` 日志
- [ ] 看到 `📝 [handleStreamEvent] 更新文本块` 日志（文本消息）
- [ ] 看到 `🔧 [handleStreamEvent] 添加工具调用块` 日志（工具调用）
- [ ] 看到 `🔧 [handleStreamEvent] 更新工具输入 JSON` 日志（工具输入）
- [ ] 看到 `💭 [handleStreamEvent] Thinking 块开始` 日志（如果有 Thinking）
- [ ] 无 JavaScript 错误
- [ ] 无 TypeScript 类型错误

### 界面检查
- [ ] 消息正确显示
- [ ] 消息流式更新（逐字显示）
- [ ] 工具调用卡片正确显示
- [ ] 工具参数正确显示
- [ ] UI 响应流畅
- [ ] 无界面卡顿

### 网络检查（Network 标签）
- [ ] WebSocket 连接成功 (`/ws`)
- [ ] HTTP API 请求成功 (`/api/`)
- [ ] 无请求失败
- [ ] 响应时间合理

## 🐛 常见问题排查

### 问题 1: 控制台没有日志
**可能原因**：
- 日志级别设置问题
- 事件处理函数未调用

**解决方法**：
- 检查 `sessionStore.ts` 中的 `handleStreamEvent` 是否被调用
- 检查 WebSocket 连接是否建立

### 问题 2: 工具调用不显示
**可能原因**：
- `content_block_start` 事件未正确处理
- 工具调用块未正确创建

**解决方法**：
- 检查控制台是否有 `🔧 [handleStreamEvent] 添加工具调用块` 日志
- 检查 `sessionStore.ts` 中的 `content_block_start` 处理逻辑

### 问题 3: 文本不流式显示
**可能原因**：
- `content_block_delta` 事件未正确处理
- 文本增量更新函数未调用

**解决方法**：
- 检查控制台是否有 `📝 [handleStreamEvent] 更新文本块` 日志
- 检查 `applyTextDelta` 函数是否正确调用

### 问题 4: JSON 解析错误
**可能原因**：
- 工具输入 JSON 不完整
- JSON 累积逻辑有问题

**解决方法**：
- 检查控制台是否有 `⏳ [handleStreamEvent] JSON 解析中（可能不完整）` 日志
- 检查 `applyInputJsonDelta` 函数的累积逻辑

## 📝 测试结果记录

### 测试日期: ___________

#### 功能测试结果
- [ ] 文本流式响应 ✅/❌
- [ ] Read 工具调用 ✅/❌
- [ ] Write 工具调用 ✅/❌
- [ ] Edit 工具调用 ✅/❌
- [ ] Thinking 块 ✅/❌
- [ ] 错误处理 ✅/❌

#### 性能测试结果
- [ ] 响应速度 ✅/❌
- [ ] 内存使用 ✅/❌
- [ ] UI 流畅度 ✅/❌

#### 问题记录
1. _________________________________
2. _________________________________
3. _________________________________

---

**提示**: 如果遇到问题，请截图控制台日志和界面，以便进一步排查。







