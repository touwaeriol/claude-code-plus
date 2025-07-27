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
    private val messageProcessor: MessageProcessor,
    private val isHistoryMode: Boolean = true  // 默认为历史模式
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
                            val toolCallId = sdkMessage.data.toolCallId
                            println("[SessionLoader] 收到TOOL_RESULT: toolCallId=$toolCallId, currentAssistantMessage是否为null=${currentAssistantMessage == null}")
                            
                            // 在历史模式下，currentAssistantMessage 应该还在
                            // 如果为 null，说明可能是在非历史模式下或有其他问题
                            var targetMessage = currentAssistantMessage
                            if (targetMessage == null && toolCallId != null) {
                                println("[SessionLoader] 当前助手消息为null，尝试从已完成消息中查找")
                                // 从后往前查找包含该工具调用的消息
                                for (i in messages.indices.reversed()) {
                                    val msg = messages[i]
                                    if (msg.role == MessageRole.ASSISTANT && 
                                        msg.toolCalls.any { it.id == toolCallId }) {
                                        targetMessage = msg
                                        println("[SessionLoader] 找到包含工具调用的消息: index=$i")
                                        break
                                    }
                                }
                            }
                            
                            // 如果还是找不到，可能工具调用还在 toolCalls 列表中
                            if (targetMessage == null && isHistoryMode && toolCallId != null) {
                                println("[SessionLoader] 历史模式下仍未找到消息，检查本地toolCalls列表")
                                val hasToolCall = toolCalls.any { it.id == toolCallId }
                                if (hasToolCall) {
                                    println("[SessionLoader] 在本地toolCalls中找到了工具调用，使用最后的助手消息")
                                    // 找到最后一条助手消息
                                    targetMessage = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                                    if (targetMessage == null && currentAssistantMessage != null) {
                                        // 如果messages中没有，但currentAssistantMessage存在，使用它
                                        targetMessage = currentAssistantMessage
                                    }
                                }
                            }
                            
                            targetMessage?.let { msg ->
                                // 在历史模式下，使用当前的 toolCalls 列表
                                // 在非历史模式下，使用消息中的 toolCalls
                                val effectiveToolCalls = if (isHistoryMode && currentAssistantMessage != null) {
                                    // 历史模式且当前助手消息存在，使用当前的 toolCalls
                                    toolCalls
                                } else {
                                    // 否则使用消息中的 toolCalls
                                    msg.toolCalls.toMutableList()
                                }
                                
                                println("[SessionLoader] 处理TOOL_RESULT，使用的工具调用数：${effectiveToolCalls.size}，来源：${if (isHistoryMode && currentAssistantMessage != null) "当前toolCalls" else "消息toolCalls"}")
                                
                                val result = messageProcessor.processMessage(
                                    sdkMessage = sdkMessage,
                                    currentMessage = msg,
                                    responseBuilder = responseBuilder,
                                    toolCalls = effectiveToolCalls,
                                    // orderedElements 由 MessageProcessor 内部管理
                                )
                                
                                when (result) {
                                    is MessageProcessor.ProcessResult.Updated -> {
                                        // 如果是已完成的消息，需要更新消息列表中的对应项
                                        if (currentAssistantMessage == null) {
                                            val messageIndex = messages.indexOfFirst { it.id == msg.id }
                                            if (messageIndex >= 0) {
                                                messages[messageIndex] = result.message
                                                println("[SessionLoader] 更新已完成消息: index=$messageIndex")
                                            }
                                        } else {
                                            currentAssistantMessage = result.message
                                        }
                                        emit(LoadResult.MessageUpdated(result.message))
                                    }
                                    else -> {}
                                }
                            }
                        }
                        
                        // 消息结束
                        com.claudecodeplus.sdk.MessageType.END -> {
                            println("[SessionLoader] 收到END消息, isHistoryMode=$isHistoryMode")
                            // 在历史模式下，不处理END消息的状态清理
                            // 因为工具结果可能在END消息之后到达
                            if (!isHistoryMode) {
                                // 完成当前消息
                                currentAssistantMessage?.let { msg ->
                                    messages.add(msg)
                                    emit(LoadResult.MessageCompleted(msg))
                                }
                                
                                // 重置状态（仅在非历史模式下）
                                currentAssistantMessage = null
                                responseBuilder.clear()
                                toolCalls.clear()
                                // orderedElements 由 MessageProcessor 管理
                                
                                // 加载完成
                                emit(LoadResult.LoadComplete(messages))
                            } else {
                                println("[SessionLoader] 历史模式下忽略END消息的状态清理，保持currentAssistantMessage=${currentAssistantMessage?.id}, toolCalls.size=${toolCalls.size}")
                            }
                            // 历史模式下，保持状态以便处理后续的工具结果
                        }
                        
                        // 错误处理
                        com.claudecodeplus.sdk.MessageType.ERROR -> {
                            emit(LoadResult.Error(sdkMessage.data.error ?: "Unknown error"))
                        }
                        
                        else -> {}
                    }
                }
                
                // 在历史模式下，处理完所有消息后需要完成最后的助手消息
                if (isHistoryMode) {
                    currentAssistantMessage?.let { msg ->
                        messages.add(msg)
                        emit(LoadResult.MessageCompleted(msg))
                        println("[SessionLoader] 历史模式下完成最后的助手消息: id=${msg.id}")
                    }
                    emit(LoadResult.LoadComplete(messages))
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