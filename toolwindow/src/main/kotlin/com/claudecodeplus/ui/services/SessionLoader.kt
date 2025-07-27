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
        // 注意：orderedElements 不再单独维护，由 MessageProcessor 管理
        
        // 跟踪正在处理的消息ID，以合并相同ID的消息
        val assistantMessageMap = mutableMapOf<String, EnhancedMessage>()
        
        try {
            // 从会话历史服务获取 SDKMessage 流
            sessionHistoryService.loadSessionHistoryAsFlow(sessionFile, maxMessages, maxDaysOld)
                .collect { sdkMessage ->
                    when (sdkMessage.type) {
                        // 处理 START 消息（用户或助手）
                        com.claudecodeplus.sdk.MessageType.START -> {
                            val text = sdkMessage.data.text
                            when {
                                text?.startsWith("USER_MESSAGE:") == true -> {
                                    // 完成当前助手消息（如果有）
                                    currentAssistantMessage?.let { msg ->
                                        messages.add(msg)
                                        emit(LoadResult.MessageCompleted(msg))
                                    }
                                    
                                    // 重置状态
                                    currentAssistantMessage = null
                                    responseBuilder.clear()
                                    toolCalls.clear()
                                    // orderedElements 由 MessageProcessor 管理
                                    
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
                                
                                text?.startsWith("ASSISTANT_MESSAGE:") == true -> {
                                    // 提取消息ID
                                    val messageId = text.substring("ASSISTANT_MESSAGE:".length)
                                    
                                    // 检查是否是同一消息的延续
                                    if (currentAssistantMessage?.id != messageId) {
                                        // 完成之前的助手消息（如果有）
                                        currentAssistantMessage?.let { msg ->
                                            messages.add(msg)
                                            emit(LoadResult.MessageCompleted(msg))
                                        }
                                        
                                        // 开始新的助手消息
                                        currentAssistantMessage = EnhancedMessage(
                                            id = messageId,
                                            role = MessageRole.ASSISTANT,
                                            content = "",
                                            timestamp = System.currentTimeMillis(),
                                            isStreaming = false,
                                            orderedElements = emptyList(),
                                            model = parseModelFromString(sdkMessage.data.model)  // 设置模型信息
                                        )
                                        responseBuilder.clear()
                                        toolCalls.clear()
                                        // orderedElements 由 MessageProcessor 管理
                                    }
                                    // 如果是同一消息，继续使用当前状态
                                }
                                
                                text?.startsWith("COMPACT_SUMMARY:") == true -> {
                                    // 完成当前助手消息（如果有）
                                    currentAssistantMessage?.let { msg ->
                                        messages.add(msg)
                                        emit(LoadResult.MessageCompleted(msg))
                                    }
                                    
                                    // 重置状态
                                    currentAssistantMessage = null
                                    responseBuilder.clear()
                                    toolCalls.clear()
                                    // orderedElements 由 MessageProcessor 管理
                                    
                                    // 创建压缩摘要消息
                                    val summaryId = text.substring("COMPACT_SUMMARY:".length)
                                    val summaryMessage = EnhancedMessage(
                                        id = summaryId,
                                        role = MessageRole.SYSTEM,
                                        content = "",  // 内容将在 TEXT 消息中填充
                                        timestamp = System.currentTimeMillis(),
                                        isCompactSummary = true  // 标记为压缩摘要
                                    )
                                    
                                    // 暂存摘要消息，等待内容
                                    currentAssistantMessage = summaryMessage
                                }
                                
                                else -> {
                                    // 其他类型的 START（如实时会话）
                                    sdkMessage.data.sessionId?.let {
                                        // 这可能是实时助手消息的开始
                                    }
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
                                    isStreaming = false,
                                    orderedElements = emptyList()
                                )
                            }
                            
                            // 使用 MessageProcessor 处理消息
                            val result = messageProcessor.processMessage(
                                sdkMessage = sdkMessage,
                                currentMessage = currentAssistantMessage!!,
                                responseBuilder = responseBuilder,
                                toolCalls = toolCalls,
                                // orderedElements 由 MessageProcessor 内部管理  // 传递可变列表
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
                                    isStreaming = false,
                                    orderedElements = emptyList()
                                )
                            }
                            
                            val result = messageProcessor.processMessage(
                                sdkMessage = sdkMessage,
                                currentMessage = currentAssistantMessage!!,
                                responseBuilder = responseBuilder,
                                toolCalls = toolCalls,
                                // orderedElements 由 MessageProcessor 内部管理
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
                                    toolCalls = toolCalls,
                                    // orderedElements 由 MessageProcessor 内部管理
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
                            // orderedElements 由 MessageProcessor 管理
                            
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
    
    /**
     * 从模型字符串解析出 AiModel
     */
    private fun parseModelFromString(modelString: String?): AiModel? {
        if (modelString == null) return null
        
        return when {
            modelString.contains("opus", ignoreCase = true) -> AiModel.OPUS
            modelString.contains("sonnet", ignoreCase = true) -> AiModel.SONNET
            else -> null
        }
    }
}