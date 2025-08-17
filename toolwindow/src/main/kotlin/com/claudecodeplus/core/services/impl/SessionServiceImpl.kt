package com.claudecodeplus.core.services.impl

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.core.logging.logW
import com.claudecodeplus.core.models.ParseResult
import com.claudecodeplus.core.models.SessionEvent
import com.claudecodeplus.core.models.SessionMetadata
import com.claudecodeplus.core.services.MessageProcessor
import com.claudecodeplus.core.services.SessionService
import com.claudecodeplus.core.services.ToolResultProcessor
import com.claudecodeplus.core.types.AppError
import com.claudecodeplus.core.types.Result
import com.claudecodeplus.core.types.suspendResultOf
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话服务实现
 * 负责会话的生命周期管理、消息处理和CLI交互
 */
class SessionServiceImpl(
    private val messageProcessor: MessageProcessor,
    private val toolResultProcessor: ToolResultProcessor = ToolResultProcessor()
) : SessionService {
    
    private val sessionManager = ClaudeSessionManager()
    private val sessionEvents = ConcurrentHashMap<String, MutableSharedFlow<SessionEvent>>()
    
    // 全局CLI Wrapper实例
    private val cliWrapper: com.claudecodeplus.sdk.ClaudeCliWrapper
        get() = GlobalCliWrapper.instance
    
    init {
        // 设置全局CLI输出处理
        setupGlobalCliOutputHandling()
    }
    
    override suspend fun createSession(
        projectPath: String,
        model: AiModel,
        permissionMode: PermissionMode
    ): Result<String> = suspendResultOf {
        logI("创建新会话: projectPath=$projectPath, model=${model.displayName}")
        
        val options = com.claudecodeplus.sdk.ClaudeCliWrapper.QueryOptions(
            sessionId = null, // 新会话不需要sessionId
            cwd = projectPath,
            model = model.cliName,
            permissionMode = permissionMode.cliName
        )
        
        // 使用空消息启动新会话来获取sessionId
        val result = cliWrapper.startNewSession("initialize", options)
        
        if (result.success && !result.sessionId.isNullOrEmpty()) {
            val sessionId = result.sessionId!!
            logI("新会话创建成功: sessionId=$sessionId")
            
            // 注册事件流
            registerSessionEvents(sessionId)
            
            sessionId
        } else {
            throw AppError.SessionError("创建会话失败: ${result.toString()}")
        }
    }
    
    override suspend fun sendMessage(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workingDirectory: String
    ): Result<Unit> = suspendResultOf {
        logI("发送消息到会话: sessionId=$sessionId, message=${message.take(50)}...")
        
        val options = com.claudecodeplus.sdk.ClaudeCliWrapper.QueryOptions(
            sessionId = sessionId,
            cwd = workingDirectory
            // model 和 permissionMode 使用默认值
        )
        
        // 发射生成开始事件
        emitSessionEvent(sessionId, SessionEvent.GenerationStarted)
        
        val result = cliWrapper.resumeSession(sessionId, message, options)
        
        if (!result.success) {
            // 发射错误事件
            val errorMessage = result.toString()
            emitSessionEvent(sessionId, SessionEvent.ErrorOccurred(errorMessage))
            throw AppError.SessionError("发送消息失败: $errorMessage", sessionId = sessionId)
        }
        
        logI("消息发送成功: sessionId=$sessionId")
    }
    
    override suspend fun resumeSession(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workingDirectory: String
    ): Result<Unit> = suspendResultOf {
        logI("恢复会话并发送消息: sessionId=$sessionId")
        
        // 先检查会话是否存在
        if (!sessionExists(sessionId, workingDirectory)) {
            throw AppError.SessionError("会话不存在: $sessionId", sessionId = sessionId)
        }
        
        // 注册事件流（如果尚未注册）
        registerSessionEvents(sessionId)
        
        // 调用sendMessage处理实际发送
        sendMessage(sessionId, message, contexts, workingDirectory)
    }
    
    override suspend fun loadSessionHistory(
        sessionId: String,
        projectPath: String,
        forceReload: Boolean
    ): Result<List<EnhancedMessage>> = suspendResultOf {
        logI("加载会话历史: sessionId=$sessionId, forceReload=$forceReload")
        
        withContext(Dispatchers.IO) {
            // 使用ClaudeSessionManager读取会话文件
            val (sessionMessages, totalCount) = sessionManager.readSessionMessages(
                sessionId = sessionId,
                projectPath = projectPath,
                pageSize = if (forceReload) Int.MAX_VALUE else 100
            )
            
            logD("读取到 ${sessionMessages.size} 条历史消息")
            
            // 逐条解析历史消息
            val enhancedMessages = mutableListOf<EnhancedMessage>()
            var processedToolResults = false
            
            sessionMessages.forEach { sessionMessage ->
                try {
                    // 首先检查是否为工具结果消息
                    if (sessionMessage.type == "user" && sessionMessage.message?.content != null) {
                        val contentList = sessionMessage.message.content
                        if (contentList is List<*>) {
                            val hasToolResult = contentList.any { contentItem ->
                                (contentItem as? Map<*, *>)?.get("type") == "tool_result"
                            }
                            
                            if (hasToolResult) {
                                // 处理工具结果，更新已有的助手消息
                                processToolResultFromHistory(contentList, enhancedMessages)
                                processedToolResults = true
                                return@forEach
                            }
                        }
                    }
                    
                    // 解析普通消息
                    when (val parseResult = messageProcessor.parseHistoryMessage(sessionMessage)) {
                        is ParseResult.Success -> {
                            enhancedMessages.add(parseResult.data)
                            logD("历史消息解析成功: ${parseResult.data.role}")
                        }
                        is ParseResult.Ignored -> {
                            logD("历史消息被忽略: ${parseResult.reason}")
                        }
                        is ParseResult.Error -> {
                            logW("历史消息解析失败: ${parseResult.message}")
                        }
                    }
                } catch (e: Exception) {
                    logE("处理历史消息异常", e)
                }
            }
            
            logI("历史消息加载完成: 共${enhancedMessages.size}条，工具结果处理=$processedToolResults")
            
            // 发射历史加载完成事件
            emitSessionEvent(sessionId, SessionEvent.HistoryLoaded(enhancedMessages))
            
            enhancedMessages
        }
    }
    
    override suspend fun interruptSession(sessionId: String): Result<Unit> = suspendResultOf {
        logI("中断会话: sessionId=$sessionId")
        
        // 发射生成停止事件
        emitSessionEvent(sessionId, SessionEvent.GenerationStopped)
        
        // TODO: 实现实际的中断逻辑
        // 这里可能需要终止相关的CLI进程
        
        logI("会话已中断: sessionId=$sessionId")
    }
    
    override fun observeSessionEvents(sessionId: String): Flow<SessionEvent> {
        return getOrCreateEventFlow(sessionId)
    }
    
    override suspend fun getSessionMetadata(sessionId: String): Result<SessionMetadata> = suspendResultOf {
        // TODO: 实现从配置文件或会话文件中读取元数据
        SessionMetadata(
            sessionId = sessionId,
            projectId = "", // 需要从上下文获取
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            messageCount = 0
        )
    }
    
    override suspend fun updateSessionMetadata(sessionId: String, metadata: SessionMetadata): Result<Unit> = suspendResultOf {
        // TODO: 实现元数据更新到配置文件
        logD("更新会话元数据: sessionId=$sessionId")
    }
    
    override suspend fun deleteSession(sessionId: String, projectPath: String): Result<Unit> = suspendResultOf {
        logI("删除会话: sessionId=$sessionId")
        
        // 清理事件流
        sessionEvents.remove(sessionId)
        
        // TODO: 删除会话文件
        
        logI("会话已删除: sessionId=$sessionId")
    }
    
    override suspend fun sessionExists(sessionId: String, projectPath: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val sessionFilePath = sessionManager.getSessionFilePath(projectPath, sessionId)
                val sessionFile = java.io.File(sessionFilePath)
                sessionFile.exists()
            }
        } catch (e: Exception) {
            logE("检查会话存在性失败", e)
            false
        }
    }
    
    /**
     * 设置全局CLI输出处理
     */
    private fun setupGlobalCliOutputHandling() {
        GlobalCliWrapper.instance.setOutputLineCallback { jsonLine ->
            handleGlobalCliOutput(jsonLine)
        }
    }
    
    /**
     * 处理全局CLI输出
     */
    private fun handleGlobalCliOutput(jsonLine: String) {
        try {
            logD("收到CLI输出: ${jsonLine.take(100)}...")
            
            // 提取会话ID
            val sessionId = messageProcessor.extractSessionId(jsonLine)
            
            // 检查是否为系统消息
            if (messageProcessor.isSystemMessage(jsonLine)) {
                handleSystemMessage(jsonLine, sessionId)
                return
            }
            
            // 检查是否为工具结果消息
            if (messageProcessor.isToolResultMessage(jsonLine)) {
                handleToolResultMessage(jsonLine, sessionId)
                return
            }
            
            // 解析普通消息
            when (val parseResult = messageProcessor.parseRealtimeMessage(jsonLine)) {
                is ParseResult.Success -> {
                    sessionId?.let { id ->
                        emitSessionEvent(id, SessionEvent.MessageReceived(parseResult.data))
                    }
                }
                is ParseResult.Ignored -> {
                    logD("实时消息被忽略: ${parseResult.reason}")
                }
                is ParseResult.Error -> {
                    logW("实时消息解析失败: ${parseResult.message}")
                    sessionId?.let { id ->
                        emitSessionEvent(id, SessionEvent.ErrorOccurred(parseResult.message))
                    }
                }
            }
        } catch (e: Exception) {
            logE("处理CLI输出异常", e)
        }
    }
    
    /**
     * 处理系统消息（如初始化、结果摘要）
     */
    private fun handleSystemMessage(jsonLine: String, sessionId: String?) {
        try {
            if (sessionId != null && jsonLine.contains("\"subtype\":\"init\"")) {
                // 系统初始化消息，可能包含sessionId更新
                val extractedSessionId = messageProcessor.extractSessionId(jsonLine)
                if (extractedSessionId != null && extractedSessionId != sessionId) {
                    emitSessionEvent(sessionId, SessionEvent.SessionIdUpdated(sessionId, extractedSessionId))
                }
            } else if (sessionId != null && jsonLine.contains("\"type\":\"result\"")) {
                // 结果摘要消息，表示生成完成
                emitSessionEvent(sessionId, SessionEvent.GenerationStopped)
            }
        } catch (e: Exception) {
            logE("处理系统消息异常", e)
        }
    }
    
    /**
     * 处理工具结果消息
     */
    private fun handleToolResultMessage(jsonLine: String, sessionId: String?) {
        // 工具结果消息需要在UI层处理，这里只记录
        logD("收到工具结果消息，sessionId=$sessionId")
        // TODO: 可以发送特殊的工具结果事件
    }
    
    /**
     * 处理历史消息中的工具结果
     */
    private fun processToolResultFromHistory(
        contentList: List<*>,
        enhancedMessages: MutableList<EnhancedMessage>
    ) {
        contentList.forEach { contentItem ->
            if (contentItem is Map<*, *> && contentItem["type"] == "tool_result") {
                val toolUseId = contentItem["tool_use_id"] as? String
                val resultContent = contentItem["content"] as? String ?: ""
                val isError = (contentItem["is_error"] as? Boolean) ?: false
                
                if (toolUseId != null) {
                    logD("处理历史工具结果: toolId=$toolUseId, isError=$isError")
                    
                    // 更新对应的工具调用结果
                    val updatedMessages = toolResultProcessor.processToolResult(
                        buildToolResultJson(toolUseId, resultContent, isError),
                        enhancedMessages
                    )
                    
                    enhancedMessages.clear()
                    enhancedMessages.addAll(updatedMessages)
                }
            }
        }
    }
    
    /**
     * 构建工具结果的JSON字符串
     */
    private fun buildToolResultJson(toolUseId: String, resultContent: String, isError: Boolean): String {
        return """
        {
            "type": "user",
            "message": {
                "content": [
                    {
                        "type": "tool_result",
                        "tool_use_id": "$toolUseId",
                        "content": "$resultContent",
                        "is_error": $isError
                    }
                ]
            }
        }
        """.trimIndent()
    }
    
    /**
     * 注册会话事件流
     */
    private fun registerSessionEvents(sessionId: String) {
        if (!sessionEvents.containsKey(sessionId)) {
            sessionEvents[sessionId] = MutableSharedFlow()
            logD("已注册会话事件流: sessionId=$sessionId")
        }
    }
    
    /**
     * 获取或创建事件流
     */
    private fun getOrCreateEventFlow(sessionId: String): MutableSharedFlow<SessionEvent> {
        return sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow<SessionEvent>().also {
                logD("创建新的会话事件流: sessionId=$sessionId")
            }
        }
    }
    
    /**
     * 发射会话事件
     */
    private fun emitSessionEvent(sessionId: String, event: SessionEvent) {
        try {
            val eventFlow = getOrCreateEventFlow(sessionId)
            eventFlow.tryEmit(event)
            logD("发射会话事件: sessionId=$sessionId, event=${event::class.simpleName}")
        } catch (e: Exception) {
            logE("发射会话事件失败", e)
        }
    }
}

/**
 * 全局CLI Wrapper管理器（简化版）
 * 在后续步骤中会被依赖注入替代
 */
object GlobalCliWrapper {
    val instance: com.claudecodeplus.sdk.ClaudeCliWrapper by lazy {
        com.claudecodeplus.sdk.ClaudeCliWrapper()
    }
}