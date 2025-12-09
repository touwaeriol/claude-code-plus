package com.asakii.server.history

import com.asakii.rpc.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.claude.agent.sdk.utils.ProjectPathUtils
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
    ): Flow<RpcMessage> {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            log.warn("[History] sessionId/projectPath 缺失，跳过加载")
            return flow { }
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")
        if (!historyFile.exists()) {
            log.warn("[History] 文件不存在: ${historyFile.absolutePath}")
            return flow { }
        }

        val totalLines = historyFile.toPath().useLines { seq -> seq.count { it.isNotBlank() } }
        log.info("[History] 加载 JSONL: session=$sessionId file=${historyFile.absolutePath} offset=$offset limit=$limit")

        return flow {
            var emitted = 0
            var skipped = 0
            var lineIndex = 0

            historyFile.bufferedReader().use { reader ->
                while (true) {
                    if (limit > 0 && emitted >= limit) break
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val currentSeq = lineIndex.toLong()
                    lineIndex++

                    try {
                        val obj = parser.parseToJsonElement(line).jsonObject
                        val messageType = obj["type"]?.jsonPrimitive?.contentOrNull
                        val metadata = RpcMessageMetadata(
                            replaySeq = currentSeq,
                            historyStart = offset,
                            historyTotal = totalLines,
                            isDisplayable = isDisplayable(messageType),
                            messageId = obj["id"]?.jsonPrimitive?.contentOrNull ?: "history-$currentSeq"
                        )
                        val rpcMessage = toRpcMessage(obj, metadata) ?: continue
                        if (skipped < offset) {
                            skipped++
                            continue
                        }
                        emit(rpcMessage)
                        emitted++
                    } catch (e: Exception) {
                        log.warn("[History] 解析行失败: ${e.message}")
                    }
                }
            }

            log.info("[History] 完成: emitted=$emitted skipped=$skipped")
        }
    }

    private fun isDisplayable(type: String?): Boolean {
        return when (type) {
            "user", "assistant", "summary" -> true
            "tool_use", "tool_result" -> true
            else -> false
        }
    }

    private fun toRpcMessage(json: JsonObject, metadata: RpcMessageMetadata): RpcMessage? {
        val type = json["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val provider = RpcProvider.CLAUDE

        return when (type) {
            "user" -> buildUserMessage(json, provider, metadata)
            "assistant" -> buildAssistantMessage(json, provider, metadata)
            "summary" -> buildResultMessage(json, provider, metadata)
            "compact_boundary", "status" -> buildStatusMessage(json, provider, metadata)
            "file-history-snapshot" -> buildUnknownSnapshot(json, provider, metadata)
            else -> buildUnknownSnapshot(json, provider, metadata) // 保底不丢数据
        }
    }

    private fun buildUserMessage(json: JsonObject, provider: RpcProvider, metadata: RpcMessageMetadata): RpcMessage? {
        val contentBlocks = extractContentBlocks(json) ?: return null
        val model = json["message"]?.jsonObject?.get("model")?.jsonPrimitive?.contentOrNull
        return RpcUserMessage(
            message = RpcMessageContent(content = contentBlocks, model = model),
            parentToolUseId = null,
            provider = provider,
            isReplay = null,
            metadata = metadata
        )
    }

    private fun buildAssistantMessage(json: JsonObject, provider: RpcProvider, metadata: RpcMessageMetadata): RpcMessage? {
        val contentBlocks = extractContentBlocks(json) ?: return null
        val model = json["message"]?.jsonObject?.get("model")?.jsonPrimitive?.contentOrNull
        return RpcAssistantMessage(
            message = RpcMessageContent(content = contentBlocks, model = model),
            provider = provider,
            metadata = metadata
        )
    }

    private fun buildResultMessage(json: JsonObject, provider: RpcProvider, metadata: RpcMessageMetadata): RpcMessage? {
        val summary = json["summary"]?.jsonPrimitive?.contentOrNull ?: return null
        return RpcResultMessage(
            subtype = "summary",
            result = summary,
            isError = false,
            numTurns = 0,
            provider = provider,
            metadata = metadata.copy(isDisplayable = true)
        )
    }

    private fun buildStatusMessage(json: JsonObject, provider: RpcProvider, metadata: RpcMessageMetadata): RpcMessage? {
        val subtype = json["type"]?.jsonPrimitive?.contentOrNull ?: "status"
        return RpcStatusSystemMessage(
            subtype = subtype,
            status = json["status"]?.jsonPrimitive?.contentOrNull,
            sessionId = json["sessionId"]?.jsonPrimitive?.contentOrNull ?: "",
            provider = provider,
            metadata = metadata.copy(isDisplayable = false)
        )
    }

    private fun buildUnknownSnapshot(json: JsonObject, provider: RpcProvider, metadata: RpcMessageMetadata): RpcMessage {
        val data = parser.encodeToString(JsonObject.serializer(), json)
        val content = RpcMessageContent(content = listOf(RpcUnknownBlock(type = json["type"]?.jsonPrimitive?.contentOrNull ?: "unknown", data = data)))
        return RpcAssistantMessage(message = content, provider = provider, metadata = metadata.copy(isDisplayable = false))
    }

    private fun extractContentBlocks(json: JsonObject): List<RpcContentBlock>? {
        val messageObj = json["message"]?.jsonObject ?: return null
        val contentElement = messageObj["content"] ?: return null

        return when (contentElement) {
            is kotlinx.serialization.json.JsonPrimitive -> if (contentElement.isString) {
                listOf(RpcTextBlock(contentElement.content))
            } else emptyList()
            is JsonArray -> contentElement.mapNotNull { item -> parseContentBlock(item) }.takeIf { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun parseContentBlock(item: JsonElement): RpcContentBlock? {
        if (item !is JsonObject) return null
        return when (item["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> item["text"]?.jsonPrimitive?.contentOrNull?.let { RpcTextBlock(it) }
            "thinking" -> item["thinking"]?.jsonPrimitive?.contentOrNull?.let { RpcThinkingBlock(it, null) }
            "tool_use" -> {
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val toolType = item["type"]?.jsonPrimitive?.contentOrNull ?: ""
                val inputJson = item["input"]?.let { parser.encodeToJsonElement(it) }
                RpcToolUseBlock(
                    id = id,
                    toolName = name,
                    toolType = toolType,
                    input = inputJson,
                    status = RpcContentStatus.IN_PROGRESS
                )
            }
            "tool_result" -> {
                val toolUseId = item["tool_use_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val isError = item["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
                val contentJson = item["content"]?.let { parser.encodeToJsonElement(it) }
                RpcToolResultBlock(
                    toolUseId = toolUseId,
                    content = contentJson,
                    isError = isError
                )
            }
            else -> RpcUnknownBlock(
                type = item["type"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                data = item.toString()
            )
        }
    }
}
