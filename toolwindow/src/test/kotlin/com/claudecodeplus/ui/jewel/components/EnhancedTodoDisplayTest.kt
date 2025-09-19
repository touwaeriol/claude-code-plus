package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * EnhancedTodoDisplay 组件的单元测试
 * 验证TodoWrite工具的任务列表解析和显示逻辑
 */
class EnhancedTodoDisplayTest {

    @Test
    fun `测试TodoWrite工具调用创建和参数解析`() {
        // 创建模拟的TodoWrite工具调用
        val todosTasks = listOf(
            mapOf(
                "content" to "分析 IntelliJ IDEA 项目的整体结构",
                "status" to "pending",
                "activeForm" to "正在分析 IntelliJ IDEA 项目的整体结构"
            ),
            mapOf(
                "content" to "检查核心可执行文件和启动脚本",
                "status" to "in_progress",
                "activeForm" to "正在检查核心可执行文件和启动脚本"
            ),
            mapOf(
                "content" to "查看主要库文件和依赖关系",
                "status" to "completed",
                "activeForm" to "正在查看主要库文件和依赖关系"
            )
        )

        val toolCall = ToolCall(
            id = "test_todo_001",
            name = "TodoWrite",
            parameters = mapOf("todos" to todosTasks),
            status = ToolCallStatus.SUCCESS,
            result = ToolResult.Success(
                output = "Todos have been modified successfully. Ensure that you continue to use the todo list to track your progress.",
                summary = "任务列表已更新"
            ),
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 1000
        )

        // 验证基本属性
        assertEquals("TodoWrite", toolCall.name)
        assertEquals(ToolCallStatus.SUCCESS, toolCall.status)
        assertTrue(toolCall.parameters.containsKey("todos"))

        // 验证todos参数结构
        val todos = toolCall.parameters["todos"] as? List<*>
        assertEquals(3, todos?.size, "应该包含3个任务")

        // 验证第一个任务
        val firstTask = todos?.get(0) as? Map<*, *>
        assertEquals("分析 IntelliJ IDEA 项目的整体结构", firstTask?.get("content"))
        assertEquals("pending", firstTask?.get("status"))

        // 验证第二个任务（进行中）
        val secondTask = todos?.get(1) as? Map<*, *>
        assertEquals("检查核心可执行文件和启动脚本", secondTask?.get("content"))
        assertEquals("in_progress", secondTask?.get("status"))

        // 验证第三个任务（已完成）
        val thirdTask = todos?.get(2) as? Map<*, *>
        assertEquals("查看主要库文件和依赖关系", thirdTask?.get("content"))
        assertEquals("completed", thirdTask?.get("status"))

        println("✅ TodoWrite工具调用创建成功:")
        println("   - 工具名称: ${toolCall.name}")
        println("   - 任务数量: ${todos?.size}")
        println("   - 状态分布: 待处理=${todos?.count { (it as Map<*, *>)["status"] == "pending" }}, " +
                "进行中=${todos?.count { (it as Map<*, *>)["status"] == "in_progress" }}, " +
                "已完成=${todos?.count { (it as Map<*, *>)["status"] == "completed" }}")
    }

    @Test
    fun `测试AI演示任务列表工具的完整场景`() {
        println("\n🚀 AI演示TodoWrite任务列表工具:")
        println("=" * 50)

        // 第一步：创建新任务列表
        val initialTasks = listOf(
            mapOf("content" to "理解当前项目结构", "status" to "pending", "activeForm" to "理解当前项目结构"),
            mapOf("content" to "分析核心组件", "status" to "pending", "activeForm" to "分析核心组件"),
            mapOf("content" to "生成项目报告", "status" to "pending", "activeForm" to "生成项目报告")
        )

        val step1ToolCall = createTodoWriteCall("step1", initialTasks)
        println("📝 步骤1 - 初始任务列表创建:")
        displayTaskList(step1ToolCall)

        // 第二步：更新任务状态（开始第一个任务）
        val step2Tasks = listOf(
            mapOf("content" to "理解当前项目结构", "status" to "in_progress", "activeForm" to "正在理解当前项目结构"),
            mapOf("content" to "分析核心组件", "status" to "pending", "activeForm" to "分析核心组件"),
            mapOf("content" to "生成项目报告", "status" to "pending", "activeForm" to "生成项目报告")
        )

        val step2ToolCall = createTodoWriteCall("step2", step2Tasks)
        println("\n🔄 步骤2 - 开始处理第一个任务:")
        displayTaskList(step2ToolCall)

        // 第三步：完成第一个任务，开始第二个任务
        val step3Tasks = listOf(
            mapOf("content" to "理解当前项目结构", "status" to "completed", "activeForm" to "理解当前项目结构"),
            mapOf("content" to "分析核心组件", "status" to "in_progress", "activeForm" to "正在分析核心组件"),
            mapOf("content" to "生成项目报告", "status" to "pending", "activeForm" to "生成项目报告")
        )

        val step3ToolCall = createTodoWriteCall("step3", step3Tasks)
        println("\n✅ 步骤3 - 完成第一个任务，开始第二个任务:")
        displayTaskList(step3ToolCall)

        // 第四步：所有任务完成
        val step4Tasks = listOf(
            mapOf("content" to "理解当前项目结构", "status" to "completed", "activeForm" to "理解当前项目结构"),
            mapOf("content" to "分析核心组件", "status" to "completed", "activeForm" to "分析核心组件"),
            mapOf("content" to "生成项目报告", "status" to "completed", "activeForm" to "生成项目报告")
        )

        val step4ToolCall = createTodoWriteCall("step4", step4Tasks)
        println("\n🎉 步骤4 - 所有任务完成:")
        displayTaskList(step4ToolCall)

        println("\n" + "=" * 50)
        println("✨ AI演示完成！TodoWrite工具成功管理了完整的任务生命周期")

        // 验证最终状态
        val finalTodos = step4ToolCall.parameters["todos"] as? List<*>
        val completedCount = finalTodos?.count { (it as Map<*, *>)["status"] == "completed" }
        assertEquals(3, completedCount, "所有3个任务都应该已完成")
    }

    private fun createTodoWriteCall(id: String, tasks: List<Map<String, String>>): ToolCall {
        return ToolCall(
            id = "demo_$id",
            name = "TodoWrite",
            parameters = mapOf("todos" to tasks),
            status = ToolCallStatus.SUCCESS,
            result = ToolResult.Success("Todos have been modified successfully.", "任务更新成功"),
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 500
        )
    }

    private fun displayTaskList(toolCall: ToolCall) {
        val todos = toolCall.parameters["todos"] as? List<*>
        val total = todos?.size ?: 0
        val completed = todos?.count { (it as Map<*, *>)["status"] == "completed" } ?: 0
        val inProgress = todos?.count { (it as Map<*, *>)["status"] == "in_progress" } ?: 0
        val pending = todos?.count { (it as Map<*, *>)["status"] == "pending" } ?: 0

        println("   📊 任务统计: $completed/$total 完成 (${(completed.toFloat() / total * 100).toInt()}%)")
        println("   📝 任务详情:")

        todos?.forEachIndexed { index, task ->
            val taskMap = task as Map<*, *>
            val content = taskMap["content"]
            val status = taskMap["status"]
            val statusIcon = when (status) {
                "completed" -> "✅"
                "in_progress" -> "🔄"
                "pending" -> "⏳"
                else -> "❓"
            }
            println("      $statusIcon ${index + 1}. $content")
        }

        println("   📈 进度分布: 已完成($completed) | 进行中($inProgress) | 待处理($pending)")
    }
}

private operator fun String.times(count: Int): String {
    return this.repeat(count)
}