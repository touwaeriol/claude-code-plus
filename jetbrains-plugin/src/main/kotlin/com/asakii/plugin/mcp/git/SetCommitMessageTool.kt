package com.asakii.plugin.mcp.git

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*

/**
 * 设置 Commit Message 工具
 *
 * 设置或追加 IDEA Commit 面板中的 commit message
 */
class SetCommitMessageTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        val message = arguments["message"] as? String
        if (message.isNullOrBlank()) {
            return buildJsonObject {
                put("success", false)
                put("error", "message is required")
            }.toString()
        }

        val mode = arguments["mode"] as? String ?: "replace"
        val append = mode == "append"

        val accessor = CommitPanelAccessor.getInstance(project)

        if (!accessor.isCommitPanelOpen()) {
            return buildJsonObject {
                put("success", false)
                put("error", "Commit panel is not open. Please open the Commit tool window first.")
            }.toString()
        }

        accessor.setCommitMessage(message, append)

        return buildJsonObject {
            put("success", true)
            put("mode", mode)
            put("messageLength", message.length)
        }.toString()
    }
}
