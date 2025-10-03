package com.claudecodeplus.ui.services

import com.claudecodeplus.core.logging.*
import com.claudecodeplus.sdk.types.MessageType
import com.claudecodeplus.sdk.types.SDKMessage
import com.claudecodeplus.sdk.types.type
import com.claudecodeplus.sdk.types.data
import com.claudecodeplus.sdk.ToolParser
import com.claudecodeplus.ui.models.*
import java.util.UUID

/**
 * 通用消息处理器 - 消息转换核心组件
 * 
 * 负责将 Claude CLI 返回的 SDKMessage 转换为 UI 使用的 EnhancedMessage。
 * 统一处理实时消息和历史消息，确保一致的消息处理逻辑。
 * 
 * 主要职责：
 * - 处理文本内容，构建完整的消息内容
 * - 管理工具调用，创建和更新 ToolCall 对象
 * - 关联工具调用结果，通过 toolCallId 匹配
 * - 维护消息的有序元素列表（orderedElements）
 * - 处理错误和完成状态
 * 
 * 设计模式：
 * 使用无状态设计，所有状态通过参数传入，返回处理结果。
 * 这使得处理器可以在多个上下文中安全地重用。
 */
class MessageProcessor {
    
    /**
     * 处理消息流中的单个消息
     * 
     * 这是消息处理的核心方法，根据 SDKMessage 的类型执行不同的处理逻辑。
     * 
     * 处理的消息类型：
     * - TEXT: 文本内容，累积到 responseBuilder 中
     * - TOOL_USE: 工具调用开始，创建新的 ToolCall
     * - TOOL_RESULT: 工具执行结果，更新对应的 ToolCall
     * - ERROR: 错误消息，标记消息为失败状态
     * - END: 消息结束，标记消息为完成状态
     * - START: 会话开始，提取会话 ID
     * 
     * @param sdkMessage Claude CLI 返回的 SDK 消息
     * @param currentMessage 当前正在构建的消息（通常是助手消息）
     * @param responseBuilder 响应内容构建器，累积文本内容
     * @param toolCalls 工具调用列表，存储所有的工具调用
     * @param orderedElements 有序元素列表（可选），用于保持内容和工具调用的顺序
     * @return ProcessResult - 封装处理结果，可能是更新、完成、错误等状态
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
                sdkMessage.data?.text?.let { text ->
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
                            orderedElements = elements.toList(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } ?: ProcessResult.NoChange
            }
            
            MessageType.TOOL_USE -> {
                // 只有在 Claude 提供了 toolCallId 时才创建 ToolCall
                val toolCallId = sdkMessage.data?.toolCallId
                if (toolCallId != null) {
                    // 解析工具类型
                    val toolName = sdkMessage.data?.toolName ?: "unknown"
                    val toolInput = sdkMessage.data?.toolInput
                    val parameters = toolInput as? Map<String, Any> ?: emptyMap()

                    val toolCall = ToolCall.createGeneric(
                        id = toolCallId,
                        name = toolName,
                        parameters = parameters,
                        status = ToolCallStatus.RUNNING,
                        result = null,
                        startTime = System.currentTimeMillis(),
                        endTime = null
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
                            orderedElements = elements.toList(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    // 如果没有 toolCallId，记录警告但不创建 ToolCall
    //                     logD("WARNING: Tool use without id: ${sdkMessage.data?.toolName}")
                    ProcessResult.NoChange
                }
            }
            
            MessageType.TOOL_RESULT -> {
                // 通过 toolCallId 查找对应的工具调用
                val toolCallId = sdkMessage.data?.toolCallId
    //                 logD("[MessageProcessor] TOOL_RESULT: toolCallId=$toolCallId, hasError=${sdkMessage.data?.error != null}, resultLength=${sdkMessage.data?.toolResult?.toString()?.length ?: 0}")
    //                 logD("[MessageProcessor] 当前工具调用列表(${toolCalls.size}个)：${toolCalls.map { "${it.name}(${it.id})" }}")
                
                if (toolCallId != null) {
                    // 查找匹配的工具调用
                    val toolCallIndex = toolCalls.indexOfFirst { it.id == toolCallId }
    //                     logD("[MessageProcessor] 查找工具调用: index=$toolCallIndex, toolCalls.size=${toolCalls.size}")
                    
                    if (toolCallIndex >= 0) {
                        val toolCall = toolCalls[toolCallIndex]
    //                         logD("[MessageProcessor] 找到工具调用: ${toolCall.name} (${toolCall.id})")

                        val messageData = sdkMessage.data
                        val updatedToolCall = toolCall.copy(
                            status = if (messageData?.error != null) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                            result = if (messageData?.error != null) {
                                ToolResult.Failure(
                                    error = messageData.error ?: "Unknown error"
                                )
                            } else {
                                ToolResult.Success(
                                    output = messageData?.toolResult?.toString() ?: ""
                                )
                            },
                            endTime = System.currentTimeMillis()
                        )
    //                         logD("[MessageProcessor] 更新工具调用结果: status=${updatedToolCall.status}, hasResult=${updatedToolCall.result != null}")
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
                                orderedElements = elements.toList(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } else {
                        // 如果找不到匹配的工具调用，静默忽略
                        ProcessResult.NoChange
                    }
                } else {
                    // 如果没有 toolCallId，这是一个错误情况
    //                     logD("ERROR: Tool result without toolCallId")
                    ProcessResult.NoChange
                }
            }
            
            MessageType.ERROR -> {
                val errorMsg = sdkMessage.data?.error ?: "Unknown error"
                ProcessResult.Error(
                    EnhancedMessage.create(
                        id = currentMessage.id,
                        role = currentMessage.role,
                        text = "⚠ 错误: $errorMsg",
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.FAILED,
                        isStreaming = false,
                        isError = true,
                        contexts = currentMessage.contexts,
                        model = currentMessage.model
                    )
                )
            }
            
            MessageType.END -> {
                ProcessResult.Complete(
                    currentMessage.copy(
                        orderedElements = elements.toList(),
                        status = MessageStatus.COMPLETE,
                        isStreaming = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            MessageType.START -> {
                // START消息通常包含sessionId，这里返回SessionStart
                sdkMessage.data?.sessionId?.let {
                    ProcessResult.SessionStart(it)
                } ?: ProcessResult.NoChange
            }

            // 添加缺失的分支
            MessageType.USER, MessageType.ASSISTANT, MessageType.SYSTEM -> {
                ProcessResult.NoChange
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
