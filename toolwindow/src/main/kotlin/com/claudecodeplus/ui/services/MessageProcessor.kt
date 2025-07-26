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
                val toolCall = ToolCall(
                    id = UUID.randomUUID().toString(),
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
            }
            
            MessageType.TOOL_RESULT -> {
                // 更新最后一个工具调用的结果
                val lastToolCall = toolCalls.lastOrNull()
                if (lastToolCall != null) {
                    val updatedToolCall = lastToolCall.copy(
                        status = ToolCallStatus.SUCCESS,
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
                    toolCalls[toolCalls.lastIndex] = updatedToolCall
                    
                    ProcessResult.Updated(
                        currentMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList()
                        )
                    )
                } else {
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