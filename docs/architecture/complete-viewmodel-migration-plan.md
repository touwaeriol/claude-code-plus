# 完全移除 SDK 依赖 - 完整执行计划

## 目标

完全移除 UI 层对 SDK 的依赖，实现纯 ViewModel 架构。

---

## 阶段划分

| 阶段 | 步骤数 | 内容 | 预计时间 |
|------|--------|------|----------|
| 阶段0 | 2步 | 修复测试代码 | 10分钟 |
| 阶段1 | 1步 | 分析Display组件结构 | 5分钟 |
| 阶段2 | 8步 | 更新Display组件 | 40分钟 |
| 阶段3 | 2步 | 更新路由和集成 | 15分钟 |
| 阶段4 | 2步 | 清理和验证 | 10分钟 |
| **总计** | **15步** | - | **80分钟** |

---

## 阶段0：修复测试代码（2步）

### 问题诊断

当前 `claude-code-sdk/src/test/kotlin/com/claudecodeplus/sdk/McpServerTest.kt` 存在编码问题：
- 中文字符显示为乱码（如 `���õ�`）
- 导致编译错误：Unresolved reference

### 步骤 0.1：检查文件编码

```bash
file -bi claude-code-sdk/src/test/kotlin/com/claudecodeplus/sdk/McpServerTest.kt
```

**预期结果**：应该显示 `charset=utf-8`

### 步骤 0.2：修复编码问题

**方案A**：如果文件是UTF-8但有BOM
```bash
# 移除BOM
sed -i '1s/^\xEF\xBB\xBF//' McpServerTest.kt
```

**方案B**：重新转换编码
```bash
# 转换为UTF-8
iconv -f GBK -t UTF-8 McpServerTest.kt > McpServerTest.kt.new
mv McpServerTest.kt.new McpServerTest.kt
```

**方案C**：直接修复问题行
- 读取文件找到乱码行（第80-83行）
- 手动替换为正确的中文或删除注释

### 步骤 0.3：验证测试编译

```bash
./gradlew :claude-code-sdk:compileTestKotlin
```

**成功标志**：BUILD SUCCESSFUL

---

## 阶段1：分析Display组件结构（1步）

### 步骤 1.1：统计所有Display组件

**19个Display组件列表**：

| 编号 | 组件名 | SDK依赖类型 | 优先级 |
|------|--------|-------------|--------|
| 1 | ReadToolDisplay | ReadToolUse | P0 |
| 2 | EditToolDisplay | EditToolUse | P0 |
| 3 | MultiEditToolDisplay | MultiEditToolUse | P0 |
| 4 | WriteToolDisplay | WriteToolUse | P0 |
| 5 | BashToolDisplay | BashToolUse | P0 |
| 6 | TodoWriteDisplay | TodoWriteToolUse | P1 |
| 7 | GlobToolDisplay | GlobToolUse | P1 |
| 8 | GrepToolDisplay | GrepToolUse | P1 |
| 9 | TaskToolDisplay | TaskToolUse | P1 |
| 10 | WebFetchToolDisplay | WebFetchToolUse | P1 |
| 11 | WebSearchToolDisplay | WebSearchToolUse | P1 |
| 12 | NotebookEditToolDisplay | NotebookEditToolUse | P1 |
| 13 | McpToolDisplay | McpToolUse | P1 |
| 14 | BashOutputDisplay | BashOutputToolUse | P2 |
| 15 | KillShellDisplay | KillShellToolUse | P2 |
| 16 | ExitPlanModeDisplay | ExitPlanModeToolUse | P2 |
| 17 | ListMcpResourcesDisplay | ListMcpResourcesToolUse | P2 |
| 18 | ReadMcpResourceDisplay | ReadMcpResourceToolUse | P2 |
| 19 | SlashCommandDisplay | SlashCommandToolUse | P2 |

**优先级说明**：
- P0：高频使用，优先修改
- P1：中频使用
- P2：低频使用

### 当前组件签名示例

```kotlin
// 当前（依赖SDK）
fun ReadToolDisplay(
    toolCall: ToolCall,
    readTool: ReadToolUse,  // ← SDK类型
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null
)

// 目标（使用ViewModel）
fun ReadToolDisplay(
    toolCall: ToolCall,  // toolCall.viewModel 包含所有信息
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null
)
```

---

## 阶段2：更新Display组件（8步）

### 策略

**统一修改模式**：
1. 移除 `xxxToolUse` 参数
2. 从 `toolCall.viewModel.toolDetail` 获取数据
3. 添加类型检查确保 toolDetail 正确

### 步骤 2.1：更新 ReadToolDisplay

**文件**：`toolwindow/.../tools/individual/ReadToolDisplay.kt`

**修改前**：
```kotlin
fun ReadToolDisplay(
    toolCall: ToolCall,
    readTool: ReadToolUse,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val filePath = readTool.filePath
    val offset = readTool.offset
    val limit = readTool.limit
    // ...
}
```

**修改后**：
```kotlin
fun ReadToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 获取数据
    val toolDetail = toolCall.viewModel?.toolDetail as? ReadToolDetail
        ?: return Text("错误：无法获取工具详情")

    val filePath = toolDetail.filePath
    val offset = toolDetail.offset
    val limit = toolDetail.limit
    // ...
}
```

**验证**：
```bash
./gradlew :toolwindow:compileKotlin
```

### 步骤 2.2：更新 EditToolDisplay

**文件**：`toolwindow/.../tools/individual/EditToolDisplay.kt`

**修改模式**：同上，使用 `EditToolDetail`

### 步骤 2.3：更新 MultiEditToolDisplay

**文件**：`toolwindow/.../tools/individual/MultiEditToolDisplay.kt`

**修改模式**：同上，使用 `MultiEditToolDetail`

**注意**：`MultiEditToolDetail.edits` 是 `List<EditOperationVm>`

### 步骤 2.4：更新 WriteToolDisplay

**文件**：`toolwindow/.../tools/individual/WriteToolDisplay.kt`

**修改模式**：同上，使用 `WriteToolDetail`

### 步骤 2.5：更新 BashToolDisplay

**文件**：`toolwindow/.../tools/individual/BashToolDisplay.kt`

**修改模式**：同上，使用 `BashToolDetail`

### 步骤 2.6：批量更新 P1 组件（6个）

**文件列表**：
1. `TodoWriteDisplay.kt` → `TodoWriteToolDetail`
2. `GlobToolDisplay.kt` → `GlobToolDetail`
3. `GrepToolDisplay.kt` → `GrepToolDetail`
4. `TaskToolDisplay.kt` → `TaskToolDetail`
5. `WebFetchToolDisplay.kt` → `WebFetchToolDetail`
6. `WebSearchToolDisplay.kt` → `WebSearchToolDetail`

**统一修改脚本思路**：
```bash
# 对每个文件执行类似操作
# 1. 移除 SDK 导入
# 2. 移除 xxxToolUse 参数
# 3. 添加 toolDetail 获取逻辑
```

### 步骤 2.7：批量更新 P2 组件（8个）

**文件列表**：
1. `NotebookEditToolDisplay.kt`
2. `McpToolDisplay.kt`
3. `BashOutputDisplay.kt`
4. `KillShellDisplay.kt`
5. `ExitPlanModeDisplay.kt`
6. `ListMcpResourcesDisplay.kt`
7. `ReadMcpResourceDisplay.kt`
8. `SlashCommandDisplay.kt`

### 步骤 2.8：验证所有Display编译

```bash
./gradlew :toolwindow:compileKotlin
```

---

## 阶段3：更新路由和集成（2步）

### 步骤 3.1：更新 TypedToolCallDisplay

**文件**：`toolwindow/.../tools/TypedToolCallDisplay.kt`

**当前路由**：
```kotlin
when (val specificTool = toolCall.specificTool) {
    is ReadToolUse -> ReadToolDisplay(
        toolCall = toolCall,
        readTool = specificTool,  // ← 传递SDK类型
        // ...
    )
    // ... 19个分支
}
```

**目标路由**：
```kotlin
when (val toolDetail = toolCall.viewModel?.toolDetail) {
    is ReadToolDetail -> ReadToolDisplay(
        toolCall = toolCall,  // ← 只传递 toolCall
        showDetails = showDetails,
        onFileClick = { ideIntegration?.handleToolClick(toolCall) }
    )
    is EditToolDetail -> EditToolDisplay(
        toolCall = toolCall,
        showDetails = showDetails,
        onFileClick = { ideIntegration?.handleToolClick(toolCall) }
    )
    // ... 19个分支
    null -> Text("工具详情不可用")
}
```

**优势**：
- 类型安全的密封类检查
- 不再依赖SDK枚举
- 编译时检查所有分支

### 步骤 3.2：更新 IDE 集成

**文件**：`jetbrains-plugin/.../handlers/ReadToolHandler.kt` 等

**当前实现**：
```kotlin
class ReadToolHandler : ToolClickHandler {
    override fun handleToolClick(toolCall: ToolCall, ...): Boolean {
        val readTool = toolCall.specificTool as? ReadToolUse
            ?: return false

        val filePath = readTool.filePath
        // ...
    }
}
```

**目标实现**：
```kotlin
class ReadToolHandler : ToolClickHandler {
    override fun handleToolClick(toolCall: ToolCall, ...): Boolean {
        val toolDetail = toolCall.viewModel?.toolDetail as? ReadToolDetail
            ?: return false

        val filePath = toolDetail.filePath
        // ...
    }
}
```

**需要更新的Handler**：
1. `ReadToolHandler.kt`
2. `EditToolHandler.kt`
3. `WriteToolHandler.kt`

---

## 阶段4：清理和验证（2步）

### 步骤 4.1：移除 ToolCall.specificTool 字段

**文件**：`toolwindow/.../models/UnifiedModels.kt`

**修改前**：
```kotlin
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val viewModel: ToolCallViewModel? = null,
    val specificTool: SpecificToolUse? = null,  // ← 删除此行
    // ...
)
```

**修改后**：
```kotlin
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val viewModel: ToolCallViewModel,  // 改为非空（必需）
    @Deprecated("Use viewModel property instead")
    val toolType: ToolType = ToolType.OTHER,
    val displayName: String = name,
    val parameters: Map<String, Any> = emptyMap(),
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val result: ToolResult? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)
```

**同时更新 SdkMessageConverter**：
```kotlin
// 移除 specificTool 参数
return ToolCall(
    id = specificTool.id,
    name = toolName,
    viewModel = viewModel,  // 只保留这个
    // specificTool = specificTool,  ← 删除
    parameters = parameters,
    status = ToolCallStatus.RUNNING,
    result = null,
    startTime = System.currentTimeMillis()
)
```

### 步骤 4.2：完整验证

**编译验证**：
```bash
./gradlew build -x test
```

**运行插件**：
```bash
./gradlew :jetbrains-plugin:runIde
```

**功能测试清单**：
- [ ] 发送消息，查看工具调用显示
- [ ] 点击 Read 工具，文件在IDE中打开
- [ ] 点击 Edit 工具，显示 Diff 视图
- [ ] 展开/折叠工具详情
- [ ] TodoWrite 显示任务进度
- [ ] Bash 命令显示正确

---

## 风险控制

### 回滚策略

每个阶段使用 Git 提交：
```bash
git add .
git commit -m "阶段X完成: [描述]"
```

如果出现问题：
```bash
git revert HEAD
```

### 常见问题

**问题1**：Display 组件找不到 toolDetail
**解决**：添加空值检查和错误提示
```kotlin
val toolDetail = toolCall.viewModel?.toolDetail as? XxxToolDetail
    ?: return Text("错误：无法获取工具详情", color = Color.Red)
```

**问题2**：类型转换失败
**解决**：检查 Mapper 是否正确实现了该工具类型的转换

**问题3**：编译错误提示缺少参数
**解决**：确保所有调用 Display 的地方都移除了 SDK 参数

---

## 成功标志

✅ 所有测试编译通过
✅ 插件可以正常运行
✅ 工具调用显示正常
✅ IDE 集成功能正常
✅ 无编译警告（SDK相关）
✅ `specificTool` 字段已完全移除

---

## 附录

### A. 文件清单

**需要修改的文件（约30个）**：

**ViewModel层**（已完成）：
- ✅ `ToolTypes.kt`
- ✅ `ToolDetailViewModel.kt`
- ✅ `ToolCallViewModel.kt`
- ✅ `ToolCallMapper.kt`

**Display组件**（待修改19个）：
1. `ReadToolDisplay.kt`
2. `EditToolDisplay.kt`
3. `MultiEditToolDisplay.kt`
4. `WriteToolDisplay.kt`
5. `BashToolDisplay.kt`
6. `TodoWriteDisplay.kt`
7. `GlobToolDisplay.kt`
8. `GrepToolDisplay.kt`
9. `TaskToolDisplay.kt`
10. `WebFetchToolDisplay.kt`
11. `WebSearchToolDisplay.kt`
12. `NotebookEditToolDisplay.kt`
13. `McpToolDisplay.kt`
14. `BashOutputDisplay.kt`
15. `KillShellDisplay.kt`
16. `ExitPlanModeDisplay.kt`
17. `ListMcpResourcesDisplay.kt`
18. `ReadMcpResourceDisplay.kt`
19. `SlashCommandDisplay.kt`

**路由和集成**（待修改5个）：
- `TypedToolCallDisplay.kt`
- `ReadToolHandler.kt`
- `EditToolHandler.kt`
- `WriteToolHandler.kt`
- `ToolClickManager.kt`

**数据模型**（待修改2个）：
- `UnifiedModels.kt`
- `SdkMessageConverter.kt`

### B. 预计代码变更量

| 类别 | 新增 | 修改 | 删除 |
|------|------|------|------|
| Display组件 | ~50 | ~300 | ~200 |
| 路由逻辑 | ~20 | ~150 | ~100 |
| IDE集成 | ~10 | ~50 | ~30 |
| 数据模型 | 0 | ~20 | ~50 |
| **总计** | **~80** | **~520** | **~380** |

---

**文档版本**：v1.0
**创建日期**：2025-01-XX
**预计完成时间**：80分钟
