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
import com.claudecodeplus.ui.services.SdkMessageConverter
import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * 会话服务实现
 * 简化架构：直接使用 ClaudeCodeSdkClient，无需复杂的会话管理
 */
class SessionServiceImpl(
    private val messageProcessor: MessageProcessor,
    private val toolResultProcessor: ToolResultProcessor = ToolResultProcessor()
) : SessionService {

    private val sessionManager = ClaudeSessionManager()
    private val sessionEvents = ConcurrentHashMap<String, MutableSharedFlow<SessionEvent>>()

    // 为每个会话保存一个 SDK 客户端
    private val sessionClients = ConcurrentHashMap<String, ClaudeCodeSdkClient>()

    init {
        logI("SessionServiceImpl 初始化，直接使用 ClaudeCodeSdkClient")
    }
    
    override suspend fun createSession(
        projectPath: String,
        model: AiModel,
        permissionMode: PermissionMode
    ): Result<String> = suspendResultOf {
        logI("创建新会话: projectPath=$projectPath, model=${model.displayName}")

        // 生成新的会话ID
        val sessionId = UUID.randomUUID().toString()

        try {
            // 创建 ClaudeCodeOptions
            val options = ClaudeCodeOptions(
                model = model.cliName,
                cwd = java.nio.file.Paths.get(projectPath),
                permissionMode = when (permissionMode) {
                    PermissionMode.DEFAULT -> com.claudecodeplus.sdk.types.PermissionMode.DEFAULT
                    PermissionMode.ACCEPT -> com.claudecodeplus.sdk.types.PermissionMode.ACCEPT_EDITS
                    PermissionMode.BYPASS -> com.claudecodeplus.sdk.types.PermissionMode.BYPASS_PERMISSIONS
                    PermissionMode.PLAN -> com.claudecodeplus.sdk.types.PermissionMode.PLAN
                }
            )

            // 创建 SDK 客户端
            val client = ClaudeCodeSdkClient(options)

            // 连接客户端
            client.connect()

            // 保存客户端
            sessionClients[sessionId] = client

            // 注册事件流
            registerSessionEvents(sessionId)

            logI("新会话创建成功: sessionId=$sessionId")
            sessionId
        } catch (e: Exception) {
            logE("创建会话失败", e)
            throw AppError.SessionError("创建会话失败: ${e.message}")
        }
    }
    
    override suspend fun sendMessage(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workingDirectory: String
    ): Result<Unit> = suspendResultOf {
        logI("发送消息到会话: sessionId=$sessionId, message=${message.take(50)}...")

        val client = sessionClients[sessionId]
            ?: throw AppError.SessionError("会话不存在: $sessionId", sessionId = sessionId)

        try {
            // 发射生成开始事件
            emitSessionEvent(sessionId, SessionEvent.GenerationStarted)

            // 发送消息
            client.query(message, sessionId)

            // 监听响应
            client.receiveResponse()
                .map { sdkMessage ->
                    SdkMessageConverter.fromSdkMessage(sdkMessage)
                }
                .onEach { enhancedMessage ->
                    // 发射消息接收事件
                    emitSessionEvent(sessionId, SessionEvent.MessageReceived(enhancedMessage))
                }
                .catch { error ->
                    logE("消息接收失败", error)
                    emitSessionEvent(sessionId, SessionEvent.ErrorOccurred(error.message ?: "未知错误"))
                }
                .collect { }

            logI("消息发送成功: sessionId=$sessionId")
        } catch (e: Exception) {
            logE("发送消息失败", e)
            emitSessionEvent(sessionId, SessionEvent.ErrorOccurred(e.message ?: "发送失败"))
            throw AppError.SessionError("发送消息失败: ${e.message}", sessionId = sessionId)
        }
    }

    override suspend fun resumeSession(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workingDirectory: String
    ): Result<Unit> = suspendResultOf {
        logI("恢复会话并发送消息: sessionId=$sessionId")

        // 检查会话客户端是否存在
        if (!sessionClients.containsKey(sessionId)) {
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
            
    //             logD("读取到 ${sessionMessages.size} 条历史消息")
            
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
    //                             logD("历史消息解析成功: ${parseResult.data.role}")
                        }
                        is ParseResult.Ignored -> {
    //                             logD("历史消息被忽略: ${parseResult.reason}")
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

        val client = sessionClients[sessionId]
            ?: throw AppError.SessionError("会话不存在: $sessionId", sessionId = sessionId)

        try {
            // 中断 SDK 客户端
            client.interrupt()

            // 发射生成停止事件
            emitSessionEvent(sessionId, SessionEvent.GenerationStopped)

            logI("会话已中断: sessionId=$sessionId")
        } catch (e: Exception) {
            logE("中断会话失败", e)
            throw AppError.SessionError("中断会话失败: ${e.message}", sessionId = sessionId)
        }
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
    //         logD("更新会话元数据: sessionId=$sessionId")
    }
    
    override suspend fun deleteSession(sessionId: String, projectPath: String): Result<Unit> = suspendResultOf {
        logI("删除会话: sessionId=$sessionId")

        try {
            // 断开并移除 SDK 客户端
            sessionClients[sessionId]?.let { client ->
                client.disconnect()
                sessionClients.remove(sessionId)
            }

            // 清理事件流
            sessionEvents.remove(sessionId)

            // TODO: 删除会话文件（如果需要的话）

            logI("会话已删除: sessionId=$sessionId")
        } catch (e: Exception) {
            logE("删除会话失败", e)
            throw AppError.SessionError("删除会话失败: ${e.message}", sessionId = sessionId)
        }
    }
    
    override suspend fun sessionExists(sessionId: String, projectPath: String): Boolean {
        return try {
            // 检查内存中是否有会话客户端
            if (sessionClients.containsKey(sessionId)) {
                return true
            }

            // 检查文件系统中是否有会话文件
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
    //                     logD("处理历史工具结果: toolId=$toolUseId, isError=$isError")

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
    //             logD("已注册会话事件流: sessionId=$sessionId")
        }
    }
    
    /**
     * 获取或创建事件流
     */
    private fun getOrCreateEventFlow(sessionId: String): MutableSharedFlow<SessionEvent> {
        return sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow<SessionEvent>().also {
    //                 logD("创建新的会话事件流: sessionId=$sessionId")
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
    //             logD("发射会话事件: sessionId=$sessionId, event=${event::class.simpleName}")
        } catch (e: Exception) {
            logE("发射会话事件失败", e)
        }
    }
}