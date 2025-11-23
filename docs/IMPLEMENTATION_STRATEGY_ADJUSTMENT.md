# 实施策略调整说明

## 当前情况

在尝试一次性完整复刻 Vue 前端时，遇到了以下问题：

1. **大量现有代码依赖旧的类型定义**
   - `ChatInputActions.kt` 依赖旧的 `SessionObject`
   - `IdeIntegration.kt` 依赖旧的 `ToolCall`
   - `ToolClickManager.kt` 等依赖旧的类型

2. **类型系统重构影响范围巨大**
   - 需要同时修改 20+ 个文件
   - 编译错误会级联传播
   - 难以保持代码在每一步都能编译通过

3. **全量重构风险高**
   - 可能引入新的 Bug
   - 难以调试
   - 无法增量验证

## 建议的调整策略

### 方案 A：增量迭代（推荐）

#### 第1步：先修复当前问题（消息不展示）
- 临时禁用 `includePartialMessages`
- 或实现最小化的 StreamEvent 处理
- 确保消息能正常展示

#### 第2步：建立新旧类型共存机制
- 保留旧的 `Message(type, content)` 类型
- 新增 `DisplayItem` 类型系统
- 两套系统并行，逐步迁移

#### 第3步：逐个模块迁移
- 先迁移 MessageDisplay
- 再迁移 ToolCallDisplay  
- 最后迁移 ChatPanel

### 方案 B：继续全量重构（原计划）

需要修复的文件清单：
1. `ChatInputActions.kt` - 移除 SessionObject 引用
2. `IdeIntegration.kt` - 更新 ToolCall 类型
3. `ToolClickManager.kt` - 更新工具处理逻辑
4. `EditToolHandler.kt`, `ReadToolHandler.kt`, `WriteToolHandler.kt` - 更新类型
5. `SessionTypes.kt` - 重新定义或删除
6. ... 还有更多

**预计修复时间**：需要修改 15-20 个文件，可能需要 2-3 小时持续工作。

## 建议

考虑到：
1. 用户当前最紧迫的需求是**修复消息不展示问题**
2. 完整复刻是一个长期目标（2-3周）
3. 增量迭代风险更低

**建议采用方案 A**：
1. 立即修复消息展示问题（方案 1 或方案 2）
2. 然后逐步实现完整复刻

或者：
**如果坚持方案 B**：
- 我将继续修复所有编译错误
- 预计需要修改 15-20 个文件
- 可能需要 1-2 个上下文窗口切换

请确认希望采用哪个方案？


