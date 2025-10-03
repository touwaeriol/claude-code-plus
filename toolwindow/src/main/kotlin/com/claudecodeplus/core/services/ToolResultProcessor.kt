package com.claudecodeplus.core.services

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.MessageTimelineItem
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import kotlinx.serialization.json.*

/**
 * 工具结果处理器
 * 专门处理Claude CLI输出中的工具执行结果
 */
class ToolResultProcessor {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 处理工具结果消息，更新对应的工具调用状态
     * @param jsonLine CLI输出的工具结果消息
     * @param messages 当前消息列表
     * @return 更新后的消息列表
     */
    fun processToolResult(jsonLine: String, messages: List<EnhancedMessage>): List<EnhancedMessage> {
        try {
    //             logD("处理工具结果消息")

            if (!jsonLine.trim().startsWith("{")) return messages

            val jsonObject = json.parseToJsonElement(jsonLine).jsonObject
            if (jsonObject["type"]?.jsonPrimitive?.content != "user") return messages

            val messageObj = jsonObject["message"]?.jsonObject ?: return messages
            val contentElement = messageObj["content"] ?: return messages

            // 处理content数组中的工具结果
            if (contentElement is JsonArray) {
                return processToolResultsFromArray(contentElement, messages)
            }

            return messages
        } catch (e: Exception) {
            logE("处理工具结果失败", e)
            return messages
        }
    }

    /**
     * 从content数组中处理工具结果
     */
    private fun processToolResultsFromArray(
        contentArray: JsonArray,
        messages: List<EnhancedMessage>
    ): List<EnhancedMessage> {
        var updatedMessages = messages

        contentArray.forEach { arrayElement ->
            val contentObj = arrayElement.jsonObject
            if (contentObj["type"]?.jsonPrimitive?.content == "tool_result") {
                val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content
                val resultContent = contentObj["content"]?.jsonPrimitive?.content ?: ""
                val isError = contentObj["is_error"]?.jsonPrimitive?.content?.toBoolean() ?: false

                if (toolUseId != null) {
    //                     logD("处理工具结果: toolId=$toolUseId, isError=$isError")
                    updatedMessages = updateToolCallResult(
                        messages = updatedMessages,
                        toolUseId = toolUseId,
                        resultContent = resultContent,
                        isError = isError
                    )
                }
            }
        }

        return updatedMessages
    }

    /**
     * 更新指定工具调用的结果
     */
    private fun updateToolCallResult(
        messages: List<EnhancedMessage>,
        toolUseId: String,
        resultContent: String,
        isError: Boolean
    ): List<EnhancedMessage> {
        // 找到包含指定工具调用ID的最后一条助手消息
        val messageIndex = messages.indexOfLast { message ->
            message.toolCalls.any { it.id == toolUseId }
        }

        if (messageIndex >= 0) {
            val message = messages[messageIndex]
            // 更新 orderedElements 中的工具调用
            val updatedElements = message.orderedElements.map { element ->
                if (element is MessageTimelineItem.ToolCallItem &&
                    element.toolCall.id == toolUseId) {
                    val result = if (isError) {
                        ToolResult.Failure(resultContent)
                    } else {
                        ToolResult.Success(resultContent)
                    }
                    val updatedToolCall = element.toolCall.copy(
                        status = if (isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                        result = result,
                        endTime = System.currentTimeMillis()
                    )
                    MessageTimelineItem.ToolCallItem(
                        toolCall = updatedToolCall,
                        timestamp = element.timestamp
                    )
                } else {
                    element
                }
            }

            val updatedMessage = message.copy(
                orderedElements = updatedElements,
                timestamp = System.currentTimeMillis()
            )

            // 更新消息列表
            val updatedMessages = messages.toMutableList()
            updatedMessages[messageIndex] = updatedMessage

    //             logD("工具调用结果已更新: toolId=$toolUseId, isError=$isError")
            return updatedMessages
        } else {
    //             logD("未找到工具调用ID为 $toolUseId 的消息")
            return messages
        }
    }

    /**
     * 检查是否有工具正在执行
     */
    fun hasRunningToolCalls(messages: List<EnhancedMessage>): Boolean {
        return messages.any { message ->
            message.toolCalls.any { it.status == ToolCallStatus.RUNNING }
        }
    }

    /**
     * 获取正在执行的工具调用列表
     */
    fun getRunningToolCalls(messages: List<EnhancedMessage>): List<ToolCall> {
        return messages.flatMap { message ->
            message.toolCalls.filter { it.status == ToolCallStatus.RUNNING }
        }
    }

    /**
     * 标记所有运行中的工具为失败状态（用于中断操作）
     */
    fun markRunningToolsAsFailed(
        messages: List<EnhancedMessage>,
        reason: String = "操作被中断"
    ): List<EnhancedMessage> {
        return messages.map { message ->
            if (message.toolCalls.any { it.status == ToolCallStatus.RUNNING }) {
                // 更新 orderedElements 中的工具调用
                val updatedElements = message.orderedElements.map { element ->
                    if (element is MessageTimelineItem.ToolCallItem &&
                        element.toolCall.status == ToolCallStatus.RUNNING) {
                        val updatedToolCall = element.toolCall.copy(
                            status = ToolCallStatus.FAILED,
                            result = ToolResult.Failure(reason),
                            endTime = System.currentTimeMillis()
                        )
                        MessageTimelineItem.ToolCallItem(
                            toolCall = updatedToolCall,
                            timestamp = element.timestamp
                        )
                    } else {
                        element
                    }
                }
                message.copy(orderedElements = updatedElements)
            } else {
                message
            }
        }
    }
}
