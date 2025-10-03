package com.claudecodeplus.ui.viewmodels.tool

import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult

/**
 * 工具调用的 UI 数据模型
 *
 * 这是工具调用在 UI 层的完整表示，完全独立于 SDK 层。
 * UI 组件只依赖此 ViewModel，不直接访问 SDK 类型。
 *
 * 设计原则：
 * 1. **不可变数据**：使用 data class 确保不可变性
 * 2. **计算属性**：显示逻辑封装在属性中，UI 组件无需关心细节
 * 3. **类型安全**：通过 toolDetail 密封类提供编译时类型检查
 * 4. **易于测试**：可独立构造，无需 SDK 依赖
 *
 * @property id 工具调用的唯一标识符
 * @property name 工具名称（如 "Edit", "Read", "Bash"）
 * @property toolDetail 工具详情（不同工具类型的具体信息）
 * @property status 当前状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）
 * @property result 执行结果（可能包含输出或错误信息）
 * @property startTime 开始执行的时间戳
 * @property endTime 结束执行的时间戳（null 表示尚未结束）
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
     *
     * 用于紧凑模式下显示工具调用的关键信息。
     * 副标题由具体的 ToolDetail 子类生成，确保每种工具类型都有适当的显示。
     *
     * 示例：
     * - Edit: "MyFile.kt (单次替换)"
     * - Read: "config.json (offset: 100)"
     * - Bash: "npm install"
     * - TodoWrite: "3 / 5 已完成"
     *
     * @return 副标题文本，如果工具类型不需要副标题则返回 null
     */
    val compactSummary: String?
        get() = toolDetail.compactSummary()

    val displaySubtitle: String?
        get() = compactSummary

    /**
     * 判断是否应在 IDE 中打开
     *
     * 必须同时满足：
     * 1. 工具类型支持 IDE 集成（toolDetail.ideIntegrationType != null）
     * 2. 执行状态为成功（status == SUCCESS）
     *
     * 只有成功的工具调用才有完整数据可以在 IDE 中展示：
     * - Edit: 修改成功才能显示 diff
     * - Read: 读取成功才能打开文件
     * - Write: 写入成功文件才存在
     *
     * 支持 IDE 集成的工具类型：
     * - EDIT, MULTI_EDIT: 显示文件 diff
     * - READ, WRITE: 打开文件
     * - NOTEBOOK_EDIT: 打开笔记本
     *
     * @return true 表示应该在 IDE 中打开，false 表示只展开显示
     */
    fun shouldUseIdeIntegration(): Boolean {
        return toolDetail.ideIntegrationType != null &&
               status == ToolCallStatus.SUCCESS
    }

    /**
     * 获取 IDE 集成类型
     *
     * 用于 IDE 集成组件判断如何处理工具调用：
     * - SHOW_DIFF: 调用 IDE 的 diff 视图
     * - OPEN_FILE: 调用 IDE 的编辑器打开文件
     *
     * @return IDE 集成类型，如果工具不支持 IDE 集成则返回 null
     */
    val ideIntegrationType: IdeIntegrationType?
        get() = toolDetail.ideIntegrationType

    /**
     * 计算执行时长
     *
     * @return 执行时长（毫秒），如果尚未结束则返回 null
     */
    val duration: Long?
        get() = endTime?.let { it - startTime }

    /**
     * 判断是否正在执行
     *
     * @return true 表示工具正在执行中
     */
    val isRunning: Boolean
        get() = status == ToolCallStatus.RUNNING

    /**
     * 判断是否执行完成（成功或失败）
     *
     * @return true 表示工具已完成执行（无论成功或失败）
     */
    val isCompleted: Boolean
        get() = status in setOf(
            ToolCallStatus.SUCCESS,
            ToolCallStatus.FAILED,
            ToolCallStatus.CANCELLED
        )

    /**
     * 判断是否执行成功
     *
     * @return true 表示工具执行成功
     */
    val isSuccess: Boolean
        get() = status == ToolCallStatus.SUCCESS

    /**
     * 判断是否执行失败
     *
     * @return true 表示工具执行失败
     */
    val isFailed: Boolean
        get() = status == ToolCallStatus.FAILED
}
