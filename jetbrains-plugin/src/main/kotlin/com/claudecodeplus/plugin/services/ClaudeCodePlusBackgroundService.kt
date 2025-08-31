package com.claudecodeplus.plugin.services

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.SessionObject
import com.claudecodeplus.ui.models.MessageRole
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import com.intellij.openapi.Disposable
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Claude Code Plus 全局后台服务
 * 
 * 这个服务在应用启动时创建，在应用关闭时销毁。
 * 负责管理所有后台的 Claude CLI 进程和会话状态，
 * 确保工具窗口隐藏时后台任务继续运行。
 * 
 * 核心功能：
 * 1. 后台进程生命周期管理
 * 2. 会话状态实时同步
 * 3. UI-后台双向通信
 * 4. 资源自动清理
 */
@Service(Service.Level.APP)
class ClaudeCodePlusBackgroundService : Disposable {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeCodePlusBackgroundService::class.java)
    }
    
    // 服务协程作用域，独立于UI生命周期
    private val serviceScope = CoroutineScope(
        SupervisorJob() + 
        Dispatchers.IO + 
        CoroutineName("ClaudeCodePlusBackgroundService")
    )
    
    // 活跃的Claude CLI进程映射 (sessionId -> Process)
    private val activeProcesses = ConcurrentHashMap<String, Process>()
    
    // 会话状态映射 (sessionId -> SessionState)
    private val sessionStates = ConcurrentHashMap<String, MutableStateFlow<SessionState>>()
    
    // 项目会话映射 (projectPath -> Set<sessionId>)
    private val projectSessions = ConcurrentHashMap<String, MutableSet<String>>()
    
    // 服务状态
    private val isServiceActive = AtomicBoolean(true)
    
    init {
        logger.info("🟢 ClaudeCodePlusBackgroundService 已启动")
    }
    
    /**
     * 会话状态数据类 - 纯内存管理
     */
    data class SessionState(
        val sessionId: String,
        val projectPath: String,
        val messages: MutableList<EnhancedMessage> = mutableListOf(), // 实时维护的消息列表
        val isGenerating: Boolean = false,
        val lastActivity: Long = System.currentTimeMillis(),
        val errorMessage: String? = null,
        val processId: Long? = null,
        val currentStreamingText: StringBuilder = StringBuilder() // 当前流式文本缓冲
    )
    
    /**
     * 会话更新事件
     */
    sealed class SessionUpdate {
        data class MessageAdded(val sessionId: String, val message: EnhancedMessage) : SessionUpdate()
        data class GeneratingStatusChanged(val sessionId: String, val isGenerating: Boolean) : SessionUpdate()
        data class ErrorOccurred(val sessionId: String, val error: String) : SessionUpdate()
        data class SessionCompleted(val sessionId: String, val success: Boolean) : SessionUpdate()
    }
    
    /**
     * 启动后台会话
     * 
     * @param sessionId 会话ID，如果为null则自动生成
     * @param projectPath 项目路径
     * @param prompt 用户输入
     * @param options Claude CLI 选项
     * @return 事件流，包含会话的所有更新
     */
    fun startBackgroundSession(
        sessionId: String?,
        projectPath: String,
        prompt: String,
        options: ClaudeCliWrapper.QueryOptions
    ): Flow<SessionUpdate> = flow {
        if (!isServiceActive.get()) {
            emit(SessionUpdate.ErrorOccurred(sessionId ?: "", "后台服务已关闭"))
            return@flow
        }
        
        val effectiveSessionId = sessionId ?: UUID.randomUUID().toString()
        logger.info("🚀 启动后台会话: $effectiveSessionId, 项目: $projectPath")
        
        try {
            // 创建或更新会话状态
            val stateFlow = sessionStates.getOrPut(effectiveSessionId) {
                MutableStateFlow(
                    SessionState(
                        sessionId = effectiveSessionId,
                        projectPath = projectPath,
                        isGenerating = true
                    )
                )
            }
            
            // 注册项目会话映射
            projectSessions.getOrPut(projectPath) { mutableSetOf() }.add(effectiveSessionId)
            
            // 更新生成状态
            stateFlow.value = stateFlow.value.copy(isGenerating = true)
            emit(SessionUpdate.GeneratingStatusChanged(effectiveSessionId, true))
            
            // 创建 CLI 包装器
            val cliWrapper = ClaudeCliWrapper()
            
            // 设置输出回调，实时处理CLI输出
            cliWrapper.setOutputLineCallback { outputLine ->
                serviceScope.launch {
                    try {
                        // 解析输出并更新会话状态
                        processCliOutput(effectiveSessionId, outputLine, stateFlow)
                    } catch (e: Exception) {
                        logger.warn("处理CLI输出失败: ${e.message}")
                    }
                }
            }
            
            // 执行Claude CLI查询
            val result = if (options.resume != null) {
                val resumeSessionId: String = options.resume ?: throw IllegalArgumentException("resume 不能为空")
                cliWrapper.resumeSession(resumeSessionId, prompt, options) { streamingText ->
                    // 流式文本回调
                    serviceScope.launch {
                        handleStreamingText(effectiveSessionId, streamingText, stateFlow)
                    }
                }
            } else {
                cliWrapper.startNewSession(prompt, options) { streamingText ->
                    // 流式文本回调
                    serviceScope.launch {
                        handleStreamingText(effectiveSessionId, streamingText, stateFlow)
                    }
                }
            }
            
            // 记录活跃进程
            if (result.success && result.processId > 0) {
                // 注意：这里无法直接获取Process对象，只能记录processId
                stateFlow.value = stateFlow.value.copy(processId = result.processId)
                logger.info("✅ 会话 $effectiveSessionId 启动成功，进程ID: ${result.processId}")
            }
            
            // 更新最终状态
            stateFlow.value = stateFlow.value.copy(
                isGenerating = false,
                errorMessage = if (!result.success) result.errorMessage else null,
                lastActivity = System.currentTimeMillis()
            )
            
            emit(SessionUpdate.GeneratingStatusChanged(effectiveSessionId, false))
            emit(SessionUpdate.SessionCompleted(effectiveSessionId, result.success))
            
            if (!result.success) {
                emit(SessionUpdate.ErrorOccurred(effectiveSessionId, result.errorMessage ?: "未知错误"))
            }
            
        } catch (e: Exception) {
            logger.error("后台会话执行失败: $effectiveSessionId", e)
            
            // 更新错误状态
            sessionStates[effectiveSessionId]?.value = sessionStates[effectiveSessionId]?.value?.copy(
                isGenerating = false,
                errorMessage = e.message,
                lastActivity = System.currentTimeMillis()
            ) ?: SessionState(
                sessionId = effectiveSessionId,
                projectPath = projectPath,
                isGenerating = false,
                errorMessage = e.message
            )
            
            emit(SessionUpdate.GeneratingStatusChanged(effectiveSessionId, false))
            emit(SessionUpdate.ErrorOccurred(effectiveSessionId, e.message ?: "执行异常"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 处理CLI输出 - 实时解析消息并更新内存列表
     */
    private suspend fun processCliOutput(
        sessionId: String,
        outputLine: String,
        stateFlow: MutableStateFlow<SessionState>
    ) {
        try {
            logger.debug("📝 会话 $sessionId CLI输出: $outputLine")
            
            // 解析JSONL格式的输出
            if (outputLine.trim().startsWith("{") && outputLine.trim().endsWith("}")) {
                val jsonElement: JsonElement = Json.parseToJsonElement(outputLine.trim())
                if (jsonElement is JsonObject) {
                    val messageType: String? = jsonElement["type"]?.jsonPrimitive?.content
                    
                    when (messageType) {
                        "assistant" -> {
                            // 解析助手消息
                            val message = parseAssistantMessage(jsonElement, sessionId)
                            if (message != null) {
                                // 创建新的消息列表副本并更新状态
                                val currentState = stateFlow.value
                                val updatedMessages = currentState.messages.toMutableList()
                                updatedMessages.add(message)
                                stateFlow.value = currentState.copy(
                                    messages = updatedMessages,
                                    lastActivity = System.currentTimeMillis()
                                )
                                logger.debug("✅ 已添加助手消息到会话 $sessionId, 总消息数: ${stateFlow.value.messages.size}")
                            }
                        }
                        "user" -> {
                            // 解析用户消息
                            val message = parseUserMessage(jsonElement, sessionId)
                            if (message != null) {
                                // 创建新的消息列表副本并更新状态
                                val currentState = stateFlow.value
                                val updatedMessages = currentState.messages.toMutableList()
                                updatedMessages.add(message)
                                stateFlow.value = currentState.copy(
                                    messages = updatedMessages,
                                    lastActivity = System.currentTimeMillis()
                                )
                                logger.debug("✅ 已添加用户消息到会话 $sessionId, 总消息数: ${stateFlow.value.messages.size}")
                            }
                        }
                        "system" -> {
                            // 系统消息，可能包含会话ID信息
                            logger.debug("🔧 系统消息: $outputLine")
                        }
                        else -> {
                            logger.debug("🔍 未处理的消息类型: $messageType")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("解析CLI输出失败: ${e.message}, 输出: $outputLine")
        }
    }
    
    /**
     * 解析助手消息
     */
    private fun parseAssistantMessage(jsonObject: JsonObject, sessionId: String): EnhancedMessage? {
        try {
            val messageObj: JsonObject = jsonObject["message"]?.jsonObject ?: return null
            val contentArray: JsonArray = messageObj["content"]?.jsonArray ?: return null
            
            // 提取文本内容
            val textContent = contentArray
                .mapNotNull { element -> element.jsonObject }
                .filter { contentObj -> contentObj["type"]?.jsonPrimitive?.content == "text" }
                .mapNotNull { contentObj -> contentObj["text"]?.jsonPrimitive?.content }
                .joinToString("")
            
            if (textContent.isNotBlank()) {
                return EnhancedMessage(
                    id = UUID.randomUUID().toString(),
                    role = com.claudecodeplus.ui.models.MessageRole.ASSISTANT,
                    content = textContent,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            logger.warn("解析助手消息失败: ${e.message}")
        }
        return null
    }
    
    /**
     * 解析用户消息
     */
    private fun parseUserMessage(jsonObject: JsonObject, sessionId: String): EnhancedMessage? {
        try {
            val messageObj: JsonObject = jsonObject["message"]?.jsonObject ?: return null
            val contentArray: JsonArray? = messageObj["content"]?.jsonArray
            
            val textContent: String = if (contentArray != null) {
                // 处理数组格式的内容
                contentArray
                    .mapNotNull { element -> element.jsonObject }
                    .filter { contentObj -> contentObj["type"]?.jsonPrimitive?.content == "text" }
                    .mapNotNull { contentObj -> contentObj["text"]?.jsonPrimitive?.content }
                    .joinToString("")
            } else {
                // 处理字符串格式的内容
                messageObj["content"]?.jsonPrimitive?.content ?: ""
            }
            
            if (textContent.isNotBlank()) {
                return EnhancedMessage(
                    id = UUID.randomUUID().toString(),
                    role = com.claudecodeplus.ui.models.MessageRole.USER,
                    content = textContent,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            logger.warn("解析用户消息失败: ${e.message}")
        }
        return null
    }
    
    /**
     * 处理流式文本 - 累积到当前消息中
     */
    private suspend fun handleStreamingText(
        sessionId: String,
        streamingText: String,
        stateFlow: MutableStateFlow<SessionState>
    ) {
        logger.debug("💬 会话 $sessionId 流式文本: ${streamingText.take(50)}...")
        
        val currentState = stateFlow.value
        
        // 累积流式文本到缓冲区（需要创建新的StringBuilder）
        val updatedStreamingText = StringBuilder(currentState.currentStreamingText)
        updatedStreamingText.append(streamingText)
        
        // 创建新的消息列表副本
        val updatedMessages = currentState.messages.toMutableList()
        
        // 找到或创建当前流式消息
        val lastMessage = updatedMessages.lastOrNull()
        if (lastMessage != null && lastMessage.role == com.claudecodeplus.ui.models.MessageRole.ASSISTANT && lastMessage.isStreaming) {
            // 更新现有的流式消息
            val updatedMessage = lastMessage.copy(
                content = updatedStreamingText.toString(),
                timestamp = System.currentTimeMillis()
            )
            // 替换最后一条消息
            updatedMessages[updatedMessages.size - 1] = updatedMessage
        } else {
            // 创建新的流式消息
            val streamingMessage = EnhancedMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = com.claudecodeplus.ui.models.MessageRole.ASSISTANT,
                content = updatedStreamingText.toString(),
                timestamp = System.currentTimeMillis(),
                isStreaming = true
            )
            updatedMessages.add(streamingMessage)
        }
        
        // 创建新的状态对象并更新
        stateFlow.value = currentState.copy(
            messages = updatedMessages,
            currentStreamingText = updatedStreamingText,
            lastActivity = System.currentTimeMillis()
        )
    }
    
    /**
     * 完成流式消息
     */
    private suspend fun finishStreamingMessage(sessionId: String, stateFlow: MutableStateFlow<SessionState>) {
        val currentState = stateFlow.value
        val updatedMessages = currentState.messages.toMutableList()
        val lastMessage = updatedMessages.lastOrNull()
        
        if (lastMessage != null && lastMessage.isStreaming) {
            // 将流式消息标记为完成
            val finishedMessage = lastMessage.copy(
                isStreaming = false,
                content = currentState.currentStreamingText.toString(),
                timestamp = System.currentTimeMillis()
            )
            updatedMessages[updatedMessages.size - 1] = finishedMessage
            
            logger.info("✅ 完成流式消息: 会话 $sessionId, 内容长度: ${finishedMessage.content.length}")
            
            // 创建新的状态，清空流式文本缓冲区
            stateFlow.value = currentState.copy(
                messages = updatedMessages,
                currentStreamingText = StringBuilder(),
                lastActivity = System.currentTimeMillis()
            )
        } else {
            // 即使没有流式消息，也更新活动时间
            stateFlow.value = currentState.copy(lastActivity = System.currentTimeMillis())
        }
    }
    
    /**
     * 注册活跃会话（用于会话恢复等场景）
     */
    fun registerActiveSession(sessionId: String, projectPath: String, initialMessages: List<EnhancedMessage> = emptyList()): MutableStateFlow<SessionState> {
        val sessionState = SessionState(
            sessionId = sessionId,
            projectPath = projectPath,
            messages = initialMessages.toMutableList(),
            isGenerating = false
        )
        
        val stateFlow = MutableStateFlow(sessionState)
        sessionStates[sessionId] = stateFlow
        
        // 注册项目会话映射
        projectSessions.getOrPut(projectPath) { mutableSetOf() }.add(sessionId)
        
        logger.info("🔗 已注册活跃会话: $sessionId, 初始消息数: ${initialMessages.size}")
        return stateFlow
    }
    
    /**
     * 观察会话状态更新
     * 
     * @param sessionId 会话ID
     * @return 会话状态的Flow，返回不可变的消息列表
     */
    fun observeSessionState(sessionId: String): Flow<SessionState>? {
        return sessionStates[sessionId]?.asStateFlow()?.map { state ->
            // 返回包含不可变消息列表的状态副本，避免UI直接修改内存列表
            state.copy(messages = state.messages.toMutableList())
        }
    }
    
    /**
     * 获取会话当前状态
     * 
     * @param sessionId 会话ID
     * @return 当前会话状态，如果不存在返回null
     */
    fun getSessionState(sessionId: String): SessionState? {
        return sessionStates[sessionId]?.value
    }
    
    /**
     * 观察项目的所有会话更新
     * 
     * @param projectPath 项目路径
     * @return 项目所有会话状态的合并Flow
     */
    fun observeProjectSessionUpdates(projectPath: String): Flow<Map<String, SessionState>> = flow {
        val projectSessionIds = projectSessions[projectPath] ?: emptySet()
        
        // 合并所有会话的状态Flow
        val sessionFlows = projectSessionIds.mapNotNull { sessionId ->
            sessionStates[sessionId]?.asStateFlow()?.map { sessionId to it }
        }
        
        if (sessionFlows.isNotEmpty()) {
            combine(sessionFlows) { sessionUpdates ->
                sessionUpdates.toMap()
            }.collect { statesMap ->
                emit(statesMap)
            }
        } else {
            emit(emptyMap())
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 终止会话
     * 
     * @param sessionId 会话ID
     */
    fun terminateSession(sessionId: String) {
        logger.info("🛑 终止会话: $sessionId")
        
        // 移除进程记录
        activeProcesses.remove(sessionId)
        
        // 更新会话状态
        sessionStates[sessionId]?.let { stateFlow ->
            stateFlow.value = stateFlow.value.copy(
                isGenerating = false,
                errorMessage = "会话已被用户终止",
                lastActivity = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 清理过期会话
     * 自动清理超过24小时未活动的会话状态
     */
    fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        val expiredThreshold = 24 * 60 * 60 * 1000L // 24小时
        
        val expiredSessions = sessionStates.filterValues { stateFlow ->
            now - stateFlow.value.lastActivity > expiredThreshold
        }.keys
        
        expiredSessions.forEach { sessionId ->
            logger.info("🧹 清理过期会话: $sessionId")
            sessionStates.remove(sessionId)
            activeProcesses.remove(sessionId)
            
            // 从项目映射中移除
            projectSessions.values.forEach { sessionSet ->
                sessionSet.remove(sessionId)
            }
        }
        
        if (expiredSessions.isNotEmpty()) {
            logger.info("✨ 已清理 ${expiredSessions.size} 个过期会话")
        }
    }
    
    /**
     * 按需恢复会话历史
     * 
     * @param sessionId 要恢复的会话ID
     * @param projectPath 项目路径
     * @return 是否成功恢复
     */
    suspend fun recoverSessionHistory(sessionId: String, projectPath: String): Boolean {
        logger.info("📥 开始恢复会话历史: sessionId=$sessionId")
        
        return withContext(Dispatchers.IO) {
            try {
                // 查找会话文件
                val sessionFile = findSessionFile(sessionId, projectPath)
                if (sessionFile == null) {
                    logger.warn("⚠️ 未找到会话文件: sessionId=$sessionId, path=$projectPath")
                    return@withContext false
                }
                
                logger.info("📁 找到会话文件: ${sessionFile.absolutePath}")
                
                // 解析会话文件获取消息历史
                val recoveredMessages = parseSessionFile(sessionFile)
                if (recoveredMessages.isEmpty()) {
                    logger.warn("⚠️ 会话文件为空或解析失败: $sessionFile")
                    return@withContext false
                }
                
                logger.info("✅ 成功解析会话文件，恢复 ${recoveredMessages.size} 条消息")
                
                // 创建或更新会话状态
                val stateFlow = sessionStates.getOrPut(sessionId) {
                    MutableStateFlow(SessionState(
                        sessionId = sessionId,
                        projectPath = projectPath
                    ))
                }
                
                // 更新会话状态
                val currentState = stateFlow.value
                stateFlow.value = currentState.copy(
                    messages = recoveredMessages.toMutableList(),
                    lastActivity = System.currentTimeMillis()
                )
                
                // 更新项目映射
                projectSessions.getOrPut(projectPath) { mutableSetOf() }.add(sessionId)
                
                logger.info("🔄 会话历史恢复完成: sessionId=$sessionId, messages=${recoveredMessages.size}")
                true
                
            } catch (e: Exception) {
                logger.error("❌ 会话恢复失败: sessionId=$sessionId", e)
                false
            }
        }
    }
    
    /**
     * 查找会话文件
     * 
     * @param sessionId 会话ID
     * @param projectPath 项目路径
     * @return 会话文件路径，如果未找到返回null
     */
    private fun findSessionFile(sessionId: String, projectPath: String): java.io.File? {
        // Claude 会话文件通常在 ~/.config/claude/sessions/ 目录
        val claudeConfigDir = java.io.File(System.getProperty("user.home"), ".config/claude/sessions")
        if (!claudeConfigDir.exists()) {
            logger.warn("⚠️ Claude配置目录不存在: ${claudeConfigDir.absolutePath}")
            return null
        }
        
        // 查找匹配的会话文件
        val sessionFiles = claudeConfigDir.listFiles { file ->
            file.name.contains(sessionId) || 
            file.readText().contains("\"sessionId\":\"$sessionId\"")
        }
        
        return sessionFiles?.firstOrNull()
    }
    
    /**
     * 解析会话文件获取消息列表
     * 
     * @param sessionFile 会话文件
     * @return 解析出的消息列表
     */
    private fun parseSessionFile(sessionFile: java.io.File): List<EnhancedMessage> {
        val messages = mutableListOf<EnhancedMessage>()
        
        try {
            sessionFile.readLines().forEach { line ->
                if (line.trim().isNotEmpty()) {
                    try {
                        val jsonElement = Json.parseToJsonElement(line.trim())
                        if (jsonElement is JsonObject) {
                            val message = parseMessageFromJson(jsonElement)
                            message?.let { messages.add(it) }
                        }
                    } catch (e: Exception) {
                        logger.debug("跳过无法解析的行: $line, 错误: ${e.message}")
                        // 继续解析其他行，不中断整个过程
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("读取会话文件失败: ${sessionFile.absolutePath}", e)
        }
        
        return messages
    }
    
    /**
     * 从JSON对象解析消息
     * 
     * @param jsonObj JSON对象
     * @return 解析出的消息，如果解析失败返回null
     */
    private fun parseMessageFromJson(jsonObj: JsonObject): EnhancedMessage? {
        return try {
            val type = jsonObj["type"]?.jsonPrimitive?.content ?: return null
            val content = jsonObj["message"]?.jsonPrimitive?.content ?: return null
            val timestamp = jsonObj["timestamp"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            
            val role = when (type) {
                "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                else -> return null
            }
            
            EnhancedMessage(
                id = UUID.randomUUID().toString(),
                role = role,
                content = content,
                timestamp = timestamp,
                model = null, // 历史消息模型信息可能缺失
                contexts = emptyList(),
                toolCalls = emptyList(),
                isStreaming = false
            )
        } catch (e: Exception) {
            logger.debug("解析消息JSON失败", e)
            null
        }
    }
    
    /**
     * 获取服务统计信息
     */
    fun getServiceStats(): Map<String, Any> {
        return mapOf(
            "activeProcesses" to activeProcesses.size,
            "activeSessions" to sessionStates.size, // UI需要这个字段
            "sessionStates" to sessionStates.size,
            "projectCount" to projectSessions.size,
            "isServiceActive" to isServiceActive.get(),
            "upTime" to System.currentTimeMillis() // 简化，实际应该记录启动时间
        )
    }
    
    /**
     * 服务销毁时的清理
     */
    override fun dispose() {
        logger.info("🔴 正在关闭 ClaudeCodePlusBackgroundService...")
        
        isServiceActive.set(false)
        
        // 终止所有活跃进程
        activeProcesses.values.forEach { process ->
            try {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            } catch (e: Exception) {
                logger.warn("终止进程失败: ${e.message}")
            }
        }
        
        // 清理状态
        activeProcesses.clear()
        sessionStates.clear()
        projectSessions.clear()
        
        // 取消协程作用域
        serviceScope.cancel("Service disposed")
        
        logger.info("✅ ClaudeCodePlusBackgroundService 已关闭")
    }
}