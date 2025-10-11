package com.claudecodeplus.core.services.impl

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.core.logging.logW
import com.claudecodeplus.core.models.ParseResult
import com.claudecodeplus.core.models.SessionEvent
import com.claudecodeplus.core.models.SessionMetadata
import com.claudecodeplus.core.preprocessor.MessagePreprocessorChain
import com.claudecodeplus.core.preprocessor.PreprocessResult
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * 会话服务实现
 * 简化架构：直接使用 ClaudeCodeSdkClient，无需复杂的会话管理
 */
class SessionServiceImpl(
    private val messageProcessor: MessageProcessor,
    private val toolResultProcessor: ToolResultProcessor = ToolResultProcessor(),
    private val clientFactory: (ClaudeCodeOptions) -> ClaudeCodeSdkClient = { opts -> ClaudeCodeSdkClient(opts) }
) : SessionService {

    private val sessionManager = ClaudeSessionManager()
    private val sessionEvents = ConcurrentHashMap<String, MutableSharedFlow<SessionEvent>>()

    // 为每个会话保存一个 SDK 客户端
    private val sessionClients = ConcurrentHashMap<String, ClaudeCodeSdkClient>()

    // 延迟初始化预处理器链，以便访问实例方法 emitSessionEvent
    private val preprocessorChain: MessagePreprocessorChain by lazy {
        MessagePreprocessorChain.createDefault { sessionId, event ->
            emitSessionEvent(sessionId, event)
        }
    }

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

            // 创建 SDK 客户端（允许通过工厂在测试中注入 FakeTransport/FakeClient）
            val client = clientFactory(options)

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
            // ========== 消息预处理 ==========
            val preprocessResult = preprocessorChain.process(message, client, sessionId)

            when (preprocessResult) {
                is PreprocessResult.Continue -> {
                    val finalMessage = preprocessResult.message
                    logI("消息通过预处理器: sessionId=$sessionId, finalMessage=${finalMessage.take(50)}...")

                    // 发射生成开始事件
                    emitSessionEvent(sessionId, SessionEvent.GenerationStarted)

                    // 发送消息
                    client.query(finalMessage, sessionId)

                    // 监听响应
                    client.receiveResponse()
                        .onEach { sdkMessage ->
                            if (sdkMessage is com.claudecodeplus.sdk.types.SystemMessage) {
                                tryExtractModelId(sdkMessage)?.let { modelId ->
                                    logI("检测到系统消息中的模型信息: subtype=${sdkMessage.subtype}, model=$modelId")
                                    emitSessionEvent(sessionId, SessionEvent.ModelUpdated(modelId))
                                }
                            }
                        }
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

                    // 响应流结束后，发射生成停止事件
                    emitSessionEvent(sessionId, SessionEvent.GenerationStopped)
                    logI("消息发送成功: sessionId=$sessionId")
                }
                is PreprocessResult.Intercepted -> {
                    emitSessionEvent(sessionId, SessionEvent.GenerationStopped)
                    return@suspendResultOf
                }
            }
            // ========== 结束：消息预处理 ==========

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

                    val contentElement = sessionMessage.message.content

                    val jsonArray = contentElement?.jsonArray



                    if (jsonArray != null) {

                        val hasToolResult = jsonArray.any { element ->

                            element.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"

                        }



                        if (hasToolResult) {

                            val toolResultPayload = buildJsonObject {

                                put("type", JsonPrimitive("user"))

                                put("message", buildJsonObject {

                                    put("content", jsonArray)

                                })

                            }.toString()



                            val updatedMessages = toolResultProcessor.processToolResult(

                                toolResultPayload,

                                enhancedMessages

                            )

                            enhancedMessages.clear()

                            enhancedMessages.addAll(updatedMessages)

                            processedToolResults = true

                            return@forEach

                        }

                    }



                    when (val parseResult = messageProcessor.parseHistoryMessage(sessionMessage)) {

                        is ParseResult.Success -> enhancedMessages.add(parseResult.data)

                        is ParseResult.Ignored -> Unit

                        is ParseResult.Error -> logW("历史消息解析失败: ${parseResult.message}")

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

    override suspend fun switchModel(sessionId: String, modelAlias: String): Result<String> = suspendResultOf {
        logI("切换会话模型: sessionId=$sessionId, alias=$modelAlias")
        val client = sessionClients[sessionId]
            ?: throw AppError.SessionError("会话不存在: $sessionId", sessionId = sessionId)

        emitSessionEvent(sessionId, SessionEvent.GenerationStarted)
        try {
            val realModelId = client.setModel(modelAlias) ?: modelAlias
            logI("✅ 模型切换成功: sessionId=$sessionId, model=$realModelId")
            emitSessionEvent(sessionId, SessionEvent.ModelUpdated(realModelId))
            val successMsg = EnhancedMessage.create(
                role = MessageRole.SYSTEM,
                text = "Set model to $modelAlias ($realModelId)",
                timestamp = System.currentTimeMillis()
            )
            emitSessionEvent(sessionId, SessionEvent.MessageReceived(successMsg))
            realModelId
        } catch (e: Exception) {
            logE("模型切换失败", e)
            emitSessionEvent(sessionId, SessionEvent.ErrorOccurred("模型切换失败: ${e.message}"))
            val errorMsg = EnhancedMessage.create(
                role = MessageRole.SYSTEM,
                text = "❌ 模型切换失败: ${e.message}",
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            emitSessionEvent(sessionId, SessionEvent.MessageReceived(errorMsg))
            throw AppError.SessionError("模型切换失败: ${e.message}", sessionId = sessionId)
        } finally {
            emitSessionEvent(sessionId, SessionEvent.GenerationStopped)
        }
    }

    /**
     * 注册会话事件流
     */
    private fun registerSessionEvents(sessionId: String) {
        if (!sessionEvents.containsKey(sessionId)) {
            sessionEvents[sessionId] = MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
    //             logD("已注册会话事件流: sessionId=$sessionId")
        }
    }

    /**
     * 获取或创建事件流
     */
    private fun getOrCreateEventFlow(sessionId: String): MutableSharedFlow<SessionEvent> {
        return sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow<SessionEvent>(replay = 1, extraBufferCapacity = 16).also {
    //                 logD("创建新的会话事件流: sessionId=$sessionId")
            }
        }
    }
    
    /**
     * 发射会话事件
     */
    private suspend fun emitSessionEvent(sessionId: String, event: SessionEvent) {
        try {
            val eventFlow = getOrCreateEventFlow(sessionId)
            eventFlow.emit(event)
            logI("📢 发射会话事件: sessionId=$sessionId, event=${event::class.simpleName}")
        } catch (e: Exception) {
            logE("发射会话事件失败", e)
            throw e
        }
    }

    private fun tryExtractModelId(message: com.claudecodeplus.sdk.types.SystemMessage): String? {
        return try {
            val dataObject = message.data.jsonObject
            dataObject["model"]?.jsonPrimitive?.contentOrNull
                ?: dataObject["model_display_name"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            logW("解析系统消息模型信息失败: subtype=${message.subtype}", e)
            null
        }
    }
}
