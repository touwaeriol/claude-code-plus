package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.SDKMessage
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 会话加载器
 * 负责加载和处理历史会话，支持流式处理
 */
class SessionLoader(
    private val sessionHistoryService: SessionHistoryService,
    private val messageProcessor: MessageProcessor
) {
    
    /**
     * 加载历史会话并转换为增强消息流
     * @param sessionFile 会话文件
     * @param maxMessages 最大消息数
     * @param maxDaysOld 最大天数
     * @return 增强消息流
     */
    fun loadSessionAsMessageFlow(
        sessionFile: File,
        maxMessages: Int = 50,
        maxDaysOld: Int = 7
    ): Flow<LoadResult> = flow {
        val messages = mutableListOf<EnhancedMessage>()
        var currentAssistantMessage: EnhancedMessage? = null
        val responseBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        
        try {
            // 从会话历史服务获取 SDKMessage 流
            sessionHistoryService.loadSessionHistoryAsFlow(sessionFile, maxMessages, maxDaysOld)
                .collect { sdkMessage ->
                    when (sdkMessage.type) {
                        // 处理特殊的用户消息标记
                        com.claudecodeplus.sdk.MessageType.START -> {
                            sdkMessage.data.text?.let { text ->
                                if (text.startsWith("USER_MESSAGE:")) {
                                    // 完成当前助手消息（如果有）
                                    currentAssistantMessage?.let { msg ->
                                        messages.add(msg)
                                        emit(LoadResult.MessageCompleted(msg))
                                    }
                                    
                                    // 重置状态
                                    currentAssistantMessage = null
                                    responseBuilder.clear()
                                    toolCalls.clear()
                                    
                                    // 创建用户消息
                                    val userContent = text.substring("USER_MESSAGE:".length)
                                    val userMessage = EnhancedMessage(
                                        id = sdkMessage.data.sessionId ?: System.currentTimeMillis().toString(),
                                        role = MessageRole.USER,
                                        content = userContent,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    messages.add(userMessage)
                                    emit(LoadResult.MessageCompleted(userMessage))
                                }
                            }
                        }
                        // 处理文本消息的开始
                        com.claudecodeplus.sdk.MessageType.TEXT -> {
                            if (currentAssistantMessage == null) {
                                // 创建新的助手消息
                                currentAssistantMessage = EnhancedMessage(
                                    role = MessageRole.ASSISTANT,
                                    content = "",
                                    timestamp = System.currentTimeMillis(),
                                    isStreaming = false
                                )
                            }
                            
                            // 使用 MessageProcessor 处理消息
                            val result = messageProcessor.processMessage(
                                sdkMessage = sdkMessage,
                                currentMessage = currentAssistantMessage!!,
                                responseBuilder = responseBuilder,
                                toolCalls = toolCalls
                            )
                            
                            when (result) {
                                is MessageProcessor.ProcessResult.Updated -> {
                                    currentAssistantMessage = result.message
                                    emit(LoadResult.MessageUpdated(result.message))
                                }
                                else -> {}
                            }
                        }
                        
                        // 处理工具调用
                        com.claudecodeplus.sdk.MessageType.TOOL_USE -> {
                            if (currentAssistantMessage == null) {
                                currentAssistantMessage = EnhancedMessage(
                                    role = MessageRole.ASSISTANT,
                                    content = "",
                                    timestamp = System.currentTimeMillis(),
                                    isStreaming = false
                                )
                            }
                            
                            val result = messageProcessor.processMessage(
                                sdkMessage = sdkMessage,
                                currentMessage = currentAssistantMessage!!,
                                responseBuilder = responseBuilder,
                                toolCalls = toolCalls
                            )
                            
                            when (result) {
                                is MessageProcessor.ProcessResult.Updated -> {
                                    currentAssistantMessage = result.message
                                    emit(LoadResult.MessageUpdated(result.message))
                                }
                                else -> {}
                            }
                        }
                        
                        // 处理工具结果
                        com.claudecodeplus.sdk.MessageType.TOOL_RESULT -> {
                            currentAssistantMessage?.let { msg ->
                                val result = messageProcessor.processMessage(
                                    sdkMessage = sdkMessage,
                                    currentMessage = msg,
                                    responseBuilder = responseBuilder,
                                    toolCalls = toolCalls
                                )
                                
                                when (result) {
                                    is MessageProcessor.ProcessResult.Updated -> {
                                        currentAssistantMessage = result.message
                                        emit(LoadResult.MessageUpdated(result.message))
                                    }
                                    else -> {}
                                }
                            }
                        }
                        
                        // 消息结束
                        com.claudecodeplus.sdk.MessageType.END -> {
                            // 完成当前消息
                            currentAssistantMessage?.let { msg ->
                                messages.add(msg)
                                emit(LoadResult.MessageCompleted(msg))
                            }
                            
                            // 重置状态
                            currentAssistantMessage = null
                            responseBuilder.clear()
                            toolCalls.clear()
                            
                            // 加载完成
                            emit(LoadResult.LoadComplete(messages))
                        }
                        
                        // 错误处理
                        com.claudecodeplus.sdk.MessageType.ERROR -> {
                            emit(LoadResult.Error(sdkMessage.data.error ?: "Unknown error"))
                        }
                        
                        else -> {}
                    }
                }
        } catch (e: Exception) {
            emit(LoadResult.Error("加载会话失败: ${e.message}"))
        }
    }
    
    /**
     * 加载结果
     */
    sealed class LoadResult {
        data class MessageUpdated(val message: EnhancedMessage) : LoadResult()
        data class MessageCompleted(val message: EnhancedMessage) : LoadResult()
        data class LoadComplete(val messages: List<EnhancedMessage>) : LoadResult()
        data class Error(val error: String) : LoadResult()
    }
}