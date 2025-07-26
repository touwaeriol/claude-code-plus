package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.sdk.SDKMessage
import com.claudecodeplus.ui.models.*
import java.util.UUID

/**
 * 通用消息处理器
 * 负责处理 SDKMessage 并更新 EnhancedMessage
 * 统一处理实时消息和历史消息
 */
class MessageProcessor {
    
    /**
     * 处理消息流中的单个消息
     * @param sdkMessage SDK消息
     * @param currentMessage 当前正在构建的消息
     * @param responseBuilder 响应内容构建器
     * @param toolCalls 工具调用列表
     * @return 更新后的消息，如果返回null表示消息还在构建中
     */
    fun processMessage(
        sdkMessage: SDKMessage,
        currentMessage: EnhancedMessage,
        responseBuilder: StringBuilder,
        toolCalls: MutableList<ToolCall>
    ): ProcessResult {
        return when (sdkMessage.type) {
            MessageType.TEXT -> {
                sdkMessage.data.text?.let { text ->
                    responseBuilder.append(text)
                    ProcessResult.Updated(
                        currentMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList()
                        )
                    )
                } ?: ProcessResult.NoChange
            }
            
            MessageType.TOOL_USE -> {
                // 只有在 Claude 提供了 toolCallId 时才创建 ToolCall
                val toolCallId = sdkMessage.data.toolCallId
                if (toolCallId != null) {
                    val toolCall = ToolCall(
                        id = toolCallId,
                        name = sdkMessage.data.toolName ?: "unknown",
                        displayName = sdkMessage.data.toolName ?: "unknown",
                        parameters = sdkMessage.data.toolInput as? Map<String, Any> ?: emptyMap(),
                        status = ToolCallStatus.RUNNING,
                        startTime = System.currentTimeMillis()
                    )
                    toolCalls.add(toolCall)
                    
                    ProcessResult.Updated(
                        currentMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList()
                        )
                    )
                } else {
                    // 如果没有 toolCallId，记录警告但不创建 ToolCall
                    println("WARNING: Tool use without id: ${sdkMessage.data.toolName}")
                    ProcessResult.NoChange
                }
            }
            
            MessageType.TOOL_RESULT -> {
                // 通过 toolCallId 查找对应的工具调用
                val toolCallId = sdkMessage.data.toolCallId
                if (toolCallId != null) {
                    // 查找匹配的工具调用
                    val toolCallIndex = toolCalls.indexOfFirst { it.id == toolCallId }
                    if (toolCallIndex >= 0) {
                        val toolCall = toolCalls[toolCallIndex]
                        val updatedToolCall = toolCall.copy(
                            status = if (sdkMessage.data.error != null) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                            result = if (sdkMessage.data.error != null) {
                                ToolResult.Failure(
                                    error = sdkMessage.data.error ?: "Unknown error"
                                )
                            } else {
                                ToolResult.Success(
                                    output = sdkMessage.data.toolResult?.toString() ?: ""
                                )
                            },
                            endTime = System.currentTimeMillis()
                        )
                        toolCalls[toolCallIndex] = updatedToolCall
                        
                        ProcessResult.Updated(
                            currentMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList()
                            )
                        )
                    } else {
                        // 如果找不到匹配的工具调用，静默忽略
                        ProcessResult.NoChange
                    }
                } else {
                    // 如果没有 toolCallId，这是一个错误情况
                    println("ERROR: Tool result without toolCallId")
                    ProcessResult.NoChange
                }
            }
            
            MessageType.ERROR -> {
                val errorMsg = sdkMessage.data.error ?: "Unknown error"
                ProcessResult.Error(
                    currentMessage.copy(
                        content = "❌ 错误: $errorMsg",
                        status = MessageStatus.FAILED,
                        isError = true,
                        isStreaming = false
                    )
                )
            }
            
            MessageType.END -> {
                ProcessResult.Complete(
                    currentMessage.copy(
                        content = responseBuilder.toString(),
                        toolCalls = toolCalls.toList(),
                        status = MessageStatus.COMPLETE,
                        isStreaming = false
                    )
                )
            }
            
            MessageType.START -> {
                // START消息通常包含sessionId，这里返回SessionStart
                sdkMessage.data.sessionId?.let {
                    ProcessResult.SessionStart(it)
                } ?: ProcessResult.NoChange
            }
        }
    }
    
    /**
     * 处理结果
     */
    sealed class ProcessResult {
        data class Updated(val message: EnhancedMessage) : ProcessResult()
        data class Complete(val message: EnhancedMessage) : ProcessResult()
        data class Error(val message: EnhancedMessage) : ProcessResult()
        data class SessionStart(val sessionId: String) : ProcessResult()
        object NoChange : ProcessResult()
    }
}