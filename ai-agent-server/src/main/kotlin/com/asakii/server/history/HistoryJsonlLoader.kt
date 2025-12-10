package com.asakii.server.history

import com.asakii.rpc.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.claude.agent.sdk.utils.ProjectPathUtils
import com.asakii.ai.agent.sdk.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.io.File
import kotlin.io.path.useLines

/**
 * 将 JSONL 会话文件转换为 RpcMessage 流（强类型）。
 */
object HistoryJsonlLoader {
    private val log = KotlinLogging.logger {}
    private val parser = Json { ignoreUnknownKeys = true }

    /**
     * 统计历史文件的总行数（物理行数，包含所有类型消息）
     * @return 文件总行数，文件不存在返回 0
     */
    fun countLines(sessionId: String?, projectPath: String?): Int {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            return 0
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")

        return if (historyFile.exists()) {
            historyFile.toPath().useLines { seq -> seq.count { it.isNotBlank() } }
        } else {
            0
        }
    }

    /**
     * @param sessionId 目标会话 ID（必填）
     * @param projectPath 项目路径（必填）
     * @param offset 跳过条数
     * @param limit 限制条数（<=0 表示全部）
     */
    fun loadHistoryMessages(
        sessionId: String?,
        projectPath: String?,
        offset: Int = 0,
        limit: Int = 0
    ): List<UiStreamEvent> {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            log.warn("[History] sessionId/projectPath 缺失，跳过加载")
            return emptyList()
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")
        if (!historyFile.exists()) {
            log.warn("[History] 文件不存在: ${historyFile.absolutePath}")
            return emptyList()
        }

        log.info("[History] 加载 JSONL: session=$sessionId file=${historyFile.absolutePath} offset=$offset limit=$limit")

        val result = mutableListOf<UiStreamEvent>()
        var skipped = 0

        historyFile.bufferedReader().use { reader ->
            while (true) {
                if (limit > 0 && result.size >= limit) break
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    val obj = parser.parseToJsonElement(line).jsonObject
                    val messageType = obj["type"]?.jsonPrimitive?.contentOrNull

                    // 直接过滤掉不需要显示的消息类型
                    if (!shouldDisplay(messageType)) {
                        continue
                    }

                    val uiEvent = toUiStreamEvent(obj) ?: continue
                    if (skipped < offset) {
                        skipped++
                        continue
                    }
                    result.add(uiEvent)
                } catch (e: Exception) {
                    log.warn("[History] 解析行失败: ${e.message}")
                }
            }
        }

        log.info("[History] 完成: loaded=${result.size} skipped=$skipped")
        return result
    }

    /**
     * 判断消息类型是否应该显示给前端
     * 过滤掉系统消息、压缩边界等不需要显示的类型
     */
    private fun shouldDisplay(type: String?): Boolean {
        return when (type) {
            "user", "assistant", "summary" -> true
            "compact_boundary", "status", "file-history-snapshot" -> false
            else -> false  // 未知类型默认不显示
        }
    }

    private fun toUiStreamEvent(json: JsonObject): UiStreamEvent? {
        val type = json["type"]?.jsonPrimitive?.contentOrNull ?: return null

        return when (type) {
            "user" -> buildUserMessage(json)
            "assistant" -> buildAssistantMessage(json)
            "summary" -> buildResultMessage(json)
            else -> null  // 其他类型已经被 shouldDisplay() 过滤，不应该到这里
        }
    }

    private fun buildUserMessage(json: JsonObject): UiStreamEvent? {
        val contentBlocks = extractContentBlocks(json) ?: return null
        return UiUserMessage(
            content = contentBlocks,
            isReplay = null
        )
    }

    private fun buildAssistantMessage(json: JsonObject): UiStreamEvent? {
        val contentBlocks = extractContentBlocks(json) ?: return null
        val messageObj = json["message"]?.jsonObject
        val id = messageObj?.get("id")?.jsonPrimitive?.contentOrNull
        return UiAssistantMessage(
            id = id,
            content = contentBlocks
        )
    }

    private fun buildResultMessage(json: JsonObject): UiStreamEvent? {
        val summary = json["summary"]?.jsonPrimitive?.contentOrNull ?: return null
        // summary 消息暂时不转换，历史加载不需要显示
        return null
    }

    private fun extractContentBlocks(json: JsonObject): List<UnifiedContentBlock>? {
        val messageObj = json["message"]?.jsonObject ?: return null
        val contentElement = messageObj["content"] ?: return null

        return when (contentElement) {
            is kotlinx.serialization.json.JsonPrimitive -> if (contentElement.isString) {
                listOf(TextContent(contentElement.content))
            } else emptyList()
            is JsonArray -> {
                // 即使部分 block 解析失败，也保留成功解析的 blocks
                val parsed = contentElement.mapNotNull { item -> parseContentBlock(item) }
                if (parsed.isEmpty()) {
                    // 如果所有 blocks 都解析失败，记录警告但仍返回空列表而不是 null
                    log.warn("[History] 所有 content blocks 解析失败，返回空列表: ${contentElement.toString().take(100)}")
                }
                parsed
            }
            else -> emptyList()
        }
    }

    private fun parseContentBlock(item: JsonElement): UnifiedContentBlock? {
        if (item !is JsonObject) return null
        return when (item["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> item["text"]?.jsonPrimitive?.contentOrNull?.let { TextContent(it) }

            "thinking" -> item["thinking"]?.jsonPrimitive?.contentOrNull?.let {
                ThinkingContent(it, signature = null)
            }

            "tool_use" -> {
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val inputJson = item["input"]  // 保持原始 JsonElement
                ToolUseContent(
                    id = id,
                    name = name,
                    input = inputJson,
                    status = ContentStatus.IN_PROGRESS
                )
            }

            "tool_result" -> {
                val toolUseId = item["tool_use_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val isError = item["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
                val contentJson = item["content"]
                ToolResultContent(
                    toolUseId = toolUseId,
                    content = contentJson,
                    isError = isError
                )
            }

            else -> null  // 忽略未知类型
        }
    }
}
