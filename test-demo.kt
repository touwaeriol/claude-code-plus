// 测试文件 - 演示各种工具的使用
package com.claudecodeplus.demo

fun main() {
    println("Claude Code Plus 工具演示")

    // TodoWrite 工具演示 - 所有任务已完成！
    val tasks = listOf(
        "✅ 分析 TodoWrite 组件",
        "✅ 查看 Markdown 渲染器",
        "✅ 创建测试文件",
        "✅ 运行 Bash 命令",
        "✅ 使用 Grep 搜索"
    )

    // 演示的工具包括：
    // - TodoWrite: 任务管理
    // - Read: 文件读取
    // - Write: 文件创建
    // - Edit: 文件编辑
    // - Bash: 命令执行
    // - Grep: 代码搜索

    tasks.forEach { task ->
        println("任务: $task")
    }
}