package com.asakii.plugin.mcp.git

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*

/**
 * 获取 Commit Message 工具
 *
 * 读取 IDEA Commit 面板中的 commit message 输入框内容
 */
class GetCommitMessageTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        val accessor = CommitPanelAccessor.getInstance(project)

        val result = buildJsonObject {
            put("commitPanelOpen", accessor.isCommitPanelOpen())
            put("message", accessor.getCommitMessage() ?: "")
        }

        return result.toString()
    }
}
