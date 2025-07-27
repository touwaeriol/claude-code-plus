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
     * @param orderedElements 有序元素列表（可选，如果未提供则从当前消息复制）
     * @return 更新后的消息，如果返回null表示消息还在构建中
     */
    fun processMessage(
        sdkMessage: SDKMessage,
        currentMessage: EnhancedMessage,
        responseBuilder: StringBuilder,
        toolCalls: MutableList<ToolCall>,
        orderedElements: MutableList<MessageTimelineItem>? = null
    ): ProcessResult {
        // 如果没有提供 orderedElements，从当前消息复制
        val elements = orderedElements ?: currentMessage.orderedElements.toMutableList()
        return when (sdkMessage.type) {
            MessageType.TEXT -> {
                sdkMessage.data.text?.let { text ->
                    responseBuilder.append(text)
                    
                    // 更新或添加内容元素
                    val lastElement = elements.lastOrNull()
                    if (lastElement is MessageTimelineItem.ContentItem) {
                        // 更新最后一个内容元素
                        elements[elements.lastIndex] = lastElement.copy(
                            content = responseBuilder.toString()
                        )
                    } else {
                        // 添加新的内容元素
                        elements.add(
                            MessageTimelineItem.ContentItem(
                                content = responseBuilder.toString(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    
                    ProcessResult.Updated(
                        currentMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList(),
                            orderedElements = elements.toList()
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
                    
                    // 添加工具调用到有序元素
                    elements.add(
                        MessageTimelineItem.ToolCallItem(
                            toolCall = toolCall,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    
                    ProcessResult.Updated(
                        currentMessage.copy(
                            content = responseBuilder.toString(),
                            toolCalls = toolCalls.toList(),
                            orderedElements = elements.toList()
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
                println("[MessageProcessor] TOOL_RESULT: toolCallId=$toolCallId, hasError=${sdkMessage.data.error != null}, resultLength=${sdkMessage.data.toolResult?.toString()?.length ?: 0}")
                println("[MessageProcessor] 当前工具调用列表(${toolCalls.size}个)：${toolCalls.map { "${it.name}(${it.id})" }}")
                
                if (toolCallId != null) {
                    // 查找匹配的工具调用
                    val toolCallIndex = toolCalls.indexOfFirst { it.id == toolCallId }
                    println("[MessageProcessor] 查找工具调用: index=$toolCallIndex, toolCalls.size=${toolCalls.size}")
                    
                    if (toolCallIndex >= 0) {
                        val toolCall = toolCalls[toolCallIndex]
                        println("[MessageProcessor] 找到工具调用: ${toolCall.name} (${toolCall.id})")
                        
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
                        println("[MessageProcessor] 更新工具调用结果: status=${updatedToolCall.status}, hasResult=${updatedToolCall.result != null}")
                        toolCalls[toolCallIndex] = updatedToolCall
                        
                        // 更新有序元素中对应的工具调用
                        for (i in elements.indices.reversed()) {
                            val element = elements[i]
                            if (element is MessageTimelineItem.ToolCallItem && 
                                element.toolCall.id == toolCallId) {
                                elements[i] = element.copy(toolCall = updatedToolCall)
                                break
                            }
                        }
                        
                        ProcessResult.Updated(
                            currentMessage.copy(
                                content = responseBuilder.toString(),
                                toolCalls = toolCalls.toList(),
                                orderedElements = elements.toList()
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
                        orderedElements = elements.toList(),
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