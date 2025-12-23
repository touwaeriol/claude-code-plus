package com.asakii.plugin.mcp.git

import com.intellij.openapi.project.Project

/**
 * 获取 Commit Message 工具
 *
 * 读取 IDEA Commit 面板中的 commit message 输入框内容
 * 返回格式：Markdown
 */
class GetCommitMessageTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        val accessor = CommitPanelAccessor.getInstance(project)
        val isOpen = accessor.isCommitPanelOpen()
        val message = accessor.getCommitMessage() ?: ""

        return buildString {
            appendLine("# Current Commit Message")
            appendLine()
            appendLine("- **Commit Panel Open**: ${if (isOpen) "Yes" else "No"}")
            appendLine()

            if (message.isNotBlank()) {
                appendLine("## Message Content")
                appendLine("```")
                appendLine(message)
                appendLine("```")
            } else {
                appendLine("*No commit message set.*")
            }
        }
    }
}
