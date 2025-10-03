# 工具调用 ViewModel 架构重构方案

## 1. 背景与目标

### 1.1 现状问题

当前工具调用显示系统存在以下问题：

1. **UI 层直接依赖 SDK**：`ToolCall` 直接持有 `SpecificToolUse` 类型
2. **显示逻辑分散**：通过扩展函数 `ToolExtensions.kt` 实现显示逻辑
3. **类型判断运行时**：使用 `when (specificTool)` 运行时判断类型
4. **难以测试**：UI 组件测试需要构造复杂的 SDK 对象
5. **耦合度高**：SDK 变化直接影响 UI 层

### 1.2 重构目标

1. **完全解耦**：UI 层不依赖 SDK 类型
2. **类型安全**：使用密封类实现编译时类型检查
3. **逻辑封装**：每个工具类型的显示逻辑在自己的 ViewModel 中
4. **易于测试**：可独立构造和测试 ViewModel
5. **易于维护**：新增工具类型只需修改 Mapper 层

---

## 2. 架构设计

### 2.1 三层架构

```
┌─────────────────────────────────────────┐
│           UI 层 (Compose 组件)           │
│   - CompactToolCallDisplay              │
│   - ExpandedToolCallDisplay             │
│   - TypedToolCallDisplay                │
└──────────────┬──────────────────────────┘
               │ 依赖
               ↓
┌─────────────────────────────────────────┐
│       ViewModel 层 (纯数据模型)          │
│   - ToolCallViewModel                   │
│   - ToolDetailViewModel (密封类)        │
│   - UiToolType, IdeIntegrationType      │
└──────────────┬──────────────────────────┘
               │ 被转换
               ↑
┌─────────────────────────────────────────┐
│        Mapper 层 (转换器)                │
│   - ToolCallMapper                      │
│     - fromSdkToolUse()                  │
│     - toToolDetail()                    │
└──────────────┬──────────────────────────┘
               │ 依赖
               ↓
┌─────────────────────────────────────────┐
│         SDK 层 (claude-code-sdk)         │
│   - SpecificToolUse                     │
│   - BashToolUse, EditToolUse, ...       │
└─────────────────────────────────────────┘
```

### 2.2 数据流

```
SDK 消息到达
    ↓
SdkMessageConverter.convertToolUseBlock()
    ↓
ToolCallMapper.fromSdkToolUse()
    ↓
创建 ToolCallViewModel (包含 ToolDetailViewModel)
    ↓
UI 组件渲染 (只依赖 ViewModel)
```

---

## 3. 核心组件设计

### 3.1 ToolTypes.kt - 枚举类型

```kotlin
/**
 * UI 层工具类型枚举
 * 独立于 SDK，避免 UI 层依赖 SDK 枚举
 */
enum class UiToolType {
    BASH,
    EDIT,
    MULTI_EDIT,
    READ,
    WRITE,
    GLOB,
    GREP,
    TODO_WRITE,
    TASK,
    WEB_FETCH,
    WEB_SEARCH,
    NOTEBOOK_EDIT,
    MCP,
    BASH_OUTPUT,
    KILL_SHELL,
    EXIT_PLAN_MODE,
    LIST_MCP_RESOURCES,
    READ_MCP_RESOURCE,
    SLASH_COMMAND,
    UNKNOWN
}

/**
 * IDE 集成类型
 */
enum class IdeIntegrationType {
    /** 在 IDE 中显示 Diff */
    SHOW_DIFF,

    /** 在 IDE 中打开文件 */
    OPEN_FILE
}
```

### 3.2 ToolCallViewModel.kt - 主 ViewModel

```kotlin
/**
 * 工具调用的 UI 数据模型
 * 完全独立于 SDK 层，UI 只依赖此类型
 */
data class ToolCallViewModel(
    val id: String,
    val name: String,
    val toolDetail: ToolDetailViewModel,
    val status: ToolCallStatus,
    val result: ToolResult?,
    val startTime: Long,
    val endTime: Long?
) {
    /**
     * 计算属性：显示副标题
     */
    val displaySubtitle: String?
        get() = toolDetail.generateSubtitle()

    /**
     * 判断是否应在 IDE 中打开
     */
    fun shouldUseIdeIntegration(): Boolean {
        return toolDetail.ideIntegrationType != null &&
               status == ToolCallStatus.SUCCESS
    }

    /**
     * 获取 IDE 集成类型
     */
    val ideIntegrationType: IdeIntegrationType?
        get() = toolDetail.ideIntegrationType
}
```

### 3.3 ToolDetailViewModel.kt - 密封类层次

```kotlin
/**
 * 工具详情基类
 * 使用密封类确保类型安全，每种工具类型一个子类
 */
sealed class ToolDetailViewModel {
    abstract val toolType: UiToolType
    abstract val ideIntegrationType: IdeIntegrationType?

    /**
     * 生成显示副标题
     */
    abstract fun generateSubtitle(): String?

    /**
     * 获取关键参数（用于展开显示）
     */
    abstract fun getKeyParameters(): Map<String, Any>
}
```

### 3.4 ToolCallMapper.kt - 转换器

```kotlin
object ToolCallMapper {
    /**
     * 从 SDK 工具调用转换为 ViewModel
     */
    fun fromSdkToolUse(
        id: String,
        name: String,
        sdkTool: SpecificToolUse?,
        status: ToolCallStatus,
        result: ToolResult?,
        startTime: Long,
        endTime: Long?
    ): ToolCallViewModel {
        val toolDetail = sdkTool?.let { toToolDetail(it) }
            ?: GenericToolDetail(UiToolType.UNKNOWN, emptyMap())

        return ToolCallViewModel(
            id = id,
            name = name,
            toolDetail = toolDetail,
            status = status,
            result = result,
            startTime = startTime,
            endTime = endTime
        )
    }

    /**
     * 转换工具详情
     */
    private fun toToolDetail(sdkTool: SpecificToolUse): ToolDetailViewModel {
        return when (sdkTool) {
            is BashToolUse -> BashToolDetail(...)
            is EditToolUse -> EditToolDetail(...)
            // ... 19 种类型
            else -> GenericToolDetail(...)
        }
    }
}
```

---

## 4. 19 种工具类型设计

### 4.1 文件操作类（5种）

#### Read - 文件读取
```kotlin
data class ReadToolDetail(
    val filePath: String,
    val offset: Int?,
    val limit: Int?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.READ
    override val ideIntegrationType = IdeIntegrationType.OPEN_FILE

    override fun generateSubtitle(): String {
        val fileName = filePath.substringAfterLast('/')
        val range = buildString {
            if (offset != null || limit != null) {
                append("(")
                if (offset != null) append("offset: $offset")
                if (limit != null) {
                    if (offset != null) append(", ")
                    append("limit: $limit")
                }
                append(")")
            }
        }
        return "$fileName $range".trim()
    }
}
```

#### Edit - 文件编辑
```kotlin
data class EditToolDetail(
    val filePath: String,
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean
) : ToolDetailViewModel() {
    override val toolType = UiToolType.EDIT
    override val ideIntegrationType = IdeIntegrationType.SHOW_DIFF

    override fun generateSubtitle(): String {
        val fileName = filePath.substringAfterLast('/')
        val editType = if (replaceAll) "替换全部" else "单次替换"
        return "$fileName ($editType)"
    }
}
```

#### MultiEdit - 批量编辑
```kotlin
data class MultiEditToolDetail(
    val filePath: String,
    val edits: List<EditOperationVm>
) : ToolDetailViewModel() {
    override val toolType = UiToolType.MULTI_EDIT
    override val ideIntegrationType = IdeIntegrationType.SHOW_DIFF

    override fun generateSubtitle() =
        "${filePath.substringAfterLast('/')} (${edits.size} 处修改)"

    data class EditOperationVm(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean
    )
}
```

#### Write - 文件写入
```kotlin
data class WriteToolDetail(
    val filePath: String,
    val content: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.WRITE
    override val ideIntegrationType = IdeIntegrationType.OPEN_FILE

    override fun generateSubtitle() =
        filePath.substringAfterLast('/')
}
```

#### NotebookEdit - Jupyter 编辑
```kotlin
data class NotebookEditToolDetail(
    val notebookPath: String,
    val cellId: String?,
    val newSource: String,
    val cellType: String?,
    val editMode: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.NOTEBOOK_EDIT
    override val ideIntegrationType = IdeIntegrationType.OPEN_FILE

    override fun generateSubtitle() =
        "${notebookPath.substringAfterLast('/')} (${editMode ?: "编辑"})"
}
```

### 4.2 搜索类（2种）

#### Glob - 文件模式匹配
```kotlin
data class GlobToolDetail(
    val pattern: String,
    val path: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.GLOB
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = pattern
}
```

#### Grep - 内容搜索
```kotlin
data class GrepToolDetail(
    val pattern: String,
    val path: String?,
    val glob: String?,
    val outputMode: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.GREP
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = pattern
}
```

### 4.3 命令执行类（3种）

#### Bash - 命令执行
```kotlin
data class BashToolDetail(
    val command: String,
    val description: String?,
    val timeout: Long?,
    val runInBackground: Boolean
) : ToolDetailViewModel() {
    override val toolType = UiToolType.BASH
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = command.take(80)
}
```

#### BashOutput - 获取后台输出
```kotlin
data class BashOutputToolDetail(
    val bashId: String,
    val filter: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.BASH_OUTPUT
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "Shell: $bashId"
}
```

#### KillShell - 终止进程
```kotlin
data class KillShellToolDetail(
    val shellId: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.KILL_SHELL
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "Shell: $shellId"
}
```

### 4.4 任务管理类（2种）

#### Task - 子任务
```kotlin
data class TaskToolDetail(
    val description: String,
    val prompt: String,
    val subagentType: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.TASK
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = description
}
```

#### TodoWrite - 待办事项
```kotlin
data class TodoWriteToolDetail(
    val todos: List<TodoItemVm>
) : ToolDetailViewModel() {
    override val toolType = UiToolType.TODO_WRITE
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle(): String {
        val completed = todos.count { it.status.equals("completed", ignoreCase = true) }
        return "$completed / ${todos.size} 已完成"
    }

    data class TodoItemVm(
        val content: String,
        val status: String,
        val activeForm: String
    )
}
```

### 4.5 网络类（2种）

#### WebFetch - 网页抓取
```kotlin
data class WebFetchToolDetail(
    val url: String,
    val prompt: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.WEB_FETCH
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = url
}
```

#### WebSearch - 网络搜索
```kotlin
data class WebSearchToolDetail(
    val query: String,
    val allowedDomains: List<String>?,
    val blockedDomains: List<String>?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.WEB_SEARCH
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = query
}
```

### 4.6 MCP 和其他（5种）

#### MCP - MCP 工具调用
```kotlin
data class McpToolDetail(
    val serverName: String,
    val toolName: String,
    val arguments: Map<String, Any>
) : ToolDetailViewModel() {
    override val toolType = UiToolType.MCP
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "$serverName::$toolName"
}
```

#### ListMcpResources - 列出 MCP 资源
```kotlin
data class ListMcpResourcesToolDetail(
    val server: String?
) : ToolDetailViewModel() {
    override val toolType = UiToolType.LIST_MCP_RESOURCES
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = server ?: "所有服务器"
}
```

#### ReadMcpResource - 读取 MCP 资源
```kotlin
data class ReadMcpResourceToolDetail(
    val server: String,
    val uri: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.READ_MCP_RESOURCE
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = uri
}
```

#### ExitPlanMode - 退出计划模式
```kotlin
data class ExitPlanModeToolDetail(
    val plan: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.EXIT_PLAN_MODE
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = "退出计划模式"
}
```

#### SlashCommand - 自定义命令
```kotlin
data class SlashCommandToolDetail(
    val command: String
) : ToolDetailViewModel() {
    override val toolType = UiToolType.SLASH_COMMAND
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle() = command
}
```

#### Generic - 通用类型（兜底）
```kotlin
data class GenericToolDetail(
    override val toolType: UiToolType,
    val parameters: Map<String, Any>
) : ToolDetailViewModel() {
    override val ideIntegrationType: IdeIntegrationType? = null

    override fun generateSubtitle(): String? {
        return parameters.entries
            .joinToString(" ") { "${it.key}=${it.value}" }
            .take(100)
            .takeIf { it.isNotEmpty() }
    }
}
```

---

## 5. 实施计划

### 5.1 阶段划分

| 阶段 | 步骤数 | 内容 | 验证方式 |
|------|--------|------|----------|
| 阶段1 | 4步 | 创建 ViewModel 层 | 编译通过 |
| 阶段2 | 3步 | 创建 Mapper 层 | 编译通过 |
| 阶段3 | 2步 | 兼容层迁移 | 编译通过 |
| 阶段4 | 3步 | UI 组件迁移 | 运行测试 |
| 阶段5 | 2步 | 清理旧代码 | 完整测试 |

### 5.2 详细步骤

#### 阶段1：创建 ViewModel 层

**步骤 1.1**：创建 `ToolTypes.kt`
- 19个 `UiToolType` 枚举值
- 2个 `IdeIntegrationType` 枚举值

**步骤 1.2**：创建 `ToolDetailViewModel.kt`（前8工具）
- 密封类基类
- Bash, Edit, MultiEdit, Read, Write, Glob, Grep, TodoWrite

**步骤 1.3**：创建 `ToolCallViewModel.kt`
- 主 ViewModel 数据类
- 计算属性：`displaySubtitle`, `shouldUseIdeIntegration()`, `ideIntegrationType`

**步骤 1.4**：补充其余11个工具类
- Task, WebFetch, WebSearch, NotebookEdit, Mcp, BashOutput, KillShell, ExitPlanMode, ListMcpResources, ReadMcpResource, SlashCommand, Generic

#### 阶段2：创建 Mapper 层

**步骤 2.1**：创建 `ToolCallMapper.kt` 骨架
- `fromSdkToolUse()` 方法
- `toToolDetail()` 方法（暂时返回 GenericToolDetail）
- `mapToolType()` 辅助方法

**步骤 2.2**：实现前8个工具转换
- 完成 `when (sdkTool)` 的前8个分支

**步骤 2.3**：完成所有19个工具转换
- 完成剩余11个分支

#### 阶段3：兼容层迁移

**步骤 3.1**：修改 `ToolCall` 数据类
```kotlin
data class ToolCall(
    val viewModel: ToolCallViewModel,  // 新增
    val specificTool: SpecificToolUse? = null  // 保留（兼容）
)
```

**步骤 3.2**：更新 `SdkMessageConverter`
- 使用 `ToolCallMapper` 创建 ViewModel
- 同时填充新旧两个字段

#### 阶段4：UI 组件迁移

**步骤 4.1**：更新 `CompactToolCallDisplay`
- 使用 `viewModel.displaySubtitle`
- 使用 `viewModel.shouldUseIdeIntegration()`

**步骤 4.2**：更新 `ExpandedToolCallDisplay`
- 使用 `viewModel.toolDetail.getKeyParameters()`
- 移除对 `specificTool` 的直接访问

**步骤 4.3**：更新 IDE 集成
- 使用 `viewModel.ideIntegrationType`
- 使用 `viewModel.toolDetail` 获取参数

#### 阶段5：清理旧代码

**步骤 5.1**：移除 `ToolExtensions.kt`
- 删除整个扩展函数文件

**步骤 5.2**：移除 `ToolCall.specificTool` 字段
- 只保留 `viewModel` 字段

---

## 6. 风险控制

### 6.1 每步独立验证

每个步骤完成后必须：
1. ✅ 运行 `./gradlew :toolwindow:compileKotlin` 或 `./gradlew build`
2. ✅ 确认编译通过
3. ✅ Git 提交该步骤

### 6.2 双轨运行期

阶段3完成后：
- ✅ 新旧代码共存
- ✅ 可随时切回旧代码路径
- ✅ 逐步验证新代码路径

### 6.3 回滚策略

如果某步出现问题：
1. `git revert` 该步骤提交
2. 分析问题原因
3. 修复后重新执行

---

## 7. 收益分析

### 7.1 架构收益

| 方面 | 当前架构 | 新架构 |
|------|---------|--------|
| 解耦 | UI 直接依赖 SDK | UI 完全独立 |
| 测试 | 需要构造 SDK 对象 | 可直接构造 ViewModel |
| 显示逻辑 | 扩展函数分散 | 集中在 ViewModel 中 |
| 类型安全 | 运行时 when 判断 | 编译时密封类检查 |
| 可维护性 | SDK 变化影响 UI | SDK 变化只影响 Mapper |

### 7.2 代码量对比

| 模块 | 当前行数 | 新增行数 | 删除行数 |
|------|----------|----------|----------|
| ViewModel 层 | 0 | ~400 | 0 |
| Mapper 层 | 0 | ~150 | 0 |
| ToolExtensions.kt | ~150 | 0 | ~150 |
| UnifiedModels.kt | ~50 | ~20 | ~10 |
| UI 组件 | ~300 | ~50 | ~50 |
| **总计** | ~500 | ~620 | ~210 |
| **净增** | - | **+410** | - |

### 7.3 维护成本

**新增工具类型需要修改的文件**：

当前架构：
1. SDK 层添加 `SpecificToolUse` 子类
2. `ToolExtensions.kt` 添加扩展函数
3. UI 组件添加 `when` 分支

新架构：
1. SDK 层添加 `SpecificToolUse` 子类
2. `ToolDetailViewModel.kt` 添加子类
3. `ToolCallMapper.kt` 添加 `when` 分支

**结论**：维护成本基本持平，但新架构的类型安全性更强。

---

## 8. 测试策略

### 8.1 单元测试

#### Mapper 测试
```kotlin
class ToolCallMapperTest {
    @Test
    fun `EditToolUse 转换为 EditToolDetail`() {
        val sdkTool = EditToolUse(...)
        val viewModel = ToolCallMapper.fromSdkToolUse(...)

        assertTrue(viewModel.toolDetail is EditToolDetail)
        assertEquals("file.kt (单次替换)", viewModel.displaySubtitle)
        assertTrue(viewModel.shouldUseIdeIntegration())
    }
}
```

#### ViewModel 测试
```kotlin
class ToolDetailViewModelTest {
    @Test
    fun `EditToolDetail 生成正确的 subtitle`() {
        val detail = EditToolDetail(...)

        assertEquals("MyFile.kt (替换全部)", detail.generateSubtitle())
        assertEquals(IdeIntegrationType.SHOW_DIFF, detail.ideIntegrationType)
    }
}
```

### 8.2 集成测试

每个阶段完成后运行：
1. ✅ 编译检查：`./gradlew build`
2. ✅ 运行插件：`./gradlew :jetbrains-plugin:runIde`
3. ✅ 功能测试：
   - 发送消息，查看工具调用显示
   - 点击工具，验证 IDE 集成
   - 展开/折叠工具详情

---

## 9. 文件结构

### 9.1 新增文件

```
toolwindow/src/main/kotlin/com/claudecodeplus/ui/
├── viewmodels/
│   └── tool/
│       ├── ToolCallViewModel.kt       (主 ViewModel)
│       ├── ToolDetailViewModel.kt     (19个工具类型)
│       └── ToolTypes.kt               (枚举类型)
│
└── mappers/
    └── ToolCallMapper.kt              (转换器)
```

### 9.2 修改文件

```
toolwindow/src/main/kotlin/com/claudecodeplus/ui/
├── models/
│   └── UnifiedModels.kt               (ToolCall 添加 viewModel 字段)
│
├── services/
│   └── SdkMessageConverter.kt         (使用 Mapper)
│
└── jewel/components/tools/
    ├── CompactToolCallDisplay.kt      (使用 ViewModel)
    └── ExpandedToolCallDisplay.kt     (使用 ViewModel)

jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/
└── integration/
    └── IdeaIdeIntegration.kt          (使用 ViewModel)
```

### 9.3 删除文件

```
toolwindow/src/main/kotlin/com/claudecodeplus/ui/
└── extensions/
    └── ToolExtensions.kt              (整个文件删除)
```

---

## 10. 附录

### 10.1 关键决策记录

**Q: 为什么使用密封类而不是接口？**
A: 密封类提供编译时类型检查，确保 `when` 表达式覆盖所有情况。

**Q: 为什么要独立的 `UiToolType` 枚举？**
A: 避免 UI 层依赖 SDK 的 `ToolType` 枚举，实现完全解耦。

**Q: 为什么不直接在 SDK 层添加 `generateSubtitle()` 方法？**
A: SDK 层应该是纯数据模型，不应包含 UI 显示逻辑。

**Q: 双轨运行期有性能影响吗？**
A: 有轻微影响（多一次对象创建），但可忽略不计。

### 10.2 相关文档

- SDK 工具类型定义：`claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/protocol/ToolUse.kt`
- UI 组件文档：`docs/jewel-components.md`
- 架构设计文档：`CLAUDE.md`

---

**文档版本**：v1.0
**创建日期**：2025-01-XX
**最后更新**：2025-01-XX
