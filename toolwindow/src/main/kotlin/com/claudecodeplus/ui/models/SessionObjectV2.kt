package com.claudecodeplus.ui.models

import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
// import com.claudecodeplus.core.di.ServiceContainer
// import com.claudecodeplus.core.models.SessionEvent
// import com.claudecodeplus.core.services.SessionService
// import com.claudecodeplus.core.logging.logD
// import com.claudecodeplus.core.logging.logE
// import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.ui.services.DefaultSessionConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * 重构后的会话对象 V2
 * 
 * 基于新架构设计，使用依赖注入和服务层，大幅简化了原有的复杂逻辑：
 * - 职责明确：仅负责UI状态管理
 * - 业务逻辑委托给服务层
 * - 使用事件驱动架构处理会话状态更新
 * - 支持响应式UI更新
 * - 保持与原有API的兼容性
 */
@Stable
class SessionObjectV2(
    initialSessionId: String? = null,
    initialMessages: List<EnhancedMessage> = emptyList(),
    initialModel: AiModel? = null,
    initialPermissionMode: PermissionMode? = null,
    initialSkipPermissions: Boolean? = null,
    private val project: Project? = null
) {
    
    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // ========== 核心状态数据 ==========
    
    /**
     * 会话 ID
     */
    var sessionId by mutableStateOf(initialSessionId)
        private set
    
    /**
     * 是否为首次消息（用于二元会话策略）
     */
    var isFirstMessage by mutableStateOf(true)
        private set
    
    /**
     * 消息列表
     */
    var messages by mutableStateOf(initialMessages)
        private set
        
    /**
     * 上下文引用列表
     */
    var contexts by mutableStateOf<List<ContextReference>>(emptyList())
    
    /**
     * 是否正在生成响应
     */
    var isGenerating by mutableStateOf(false)
        private set
    
    /**
     * 错误消息
     */
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    // ========== UI 状态 ==========
    
    /**
     * 输入框内容
     */
    var inputTextFieldValue by mutableStateOf(TextFieldValue(""))
    
    /**
     * 选择的 AI 模型
     */
    var selectedModel by mutableStateOf(initialModel ?: DefaultSessionConfig.defaultModel)
    
    /**
     * 选择的权限模式
     */
    var selectedPermissionMode by mutableStateOf(initialPermissionMode ?: DefaultSessionConfig.defaultPermissionMode)
    
    /**
     * 是否跳过权限确认
     */
    var skipPermissions by mutableStateOf(initialSkipPermissions ?: DefaultSessionConfig.defaultSkipPermissions)
    
    /**
     * 是否正在加载会话
     */
    var isLoadingSession by mutableStateOf(false)
        private set
    
    /**
     * 上下文选择器是否显示
     */
    var showContextSelector by mutableStateOf(false)
    
    /**
     * @ 符号位置（用于内联引用）
     */
    var atSymbolPosition by mutableStateOf<Int?>(null)
    
    /**
     * 滚动位置
     */
    var scrollPosition by mutableStateOf(0f)
    
    /**
     * 问题队列
     */
    val questionQueue = mutableStateListOf<String>()
    
    /**
     * 输入重置触发器
     */
    var inputResetTrigger by mutableStateOf<Any?>(null)
    
    /**
     * 消息加载状态
     */
    var messageLoadingState by mutableStateOf(MessageLoadingState.IDLE)
        private set
    
    // ========== 兼容性属性 ==========
    
    /**
     * 当前任务描述
     */
    var currentTaskDescription by mutableStateOf<String?>(null)
        private set
    
    /**
     * 任务开始时间
     */
    var taskStartTime by mutableStateOf<Long?>(null)
        private set
    
    /**
     * 当前流任务
     */
    var currentStreamJob by mutableStateOf<Job?>(null)
    
    /**
     * 当前进程
     */
    var currentProcess by mutableStateOf<Process?>(null)
    
    /**
     * 会话信息（兼容性）
     */
    var sessionInfo by mutableStateOf<com.claudecodeplus.session.models.SessionInfo?>(null)
    
    /**
     * 当前会话（兼容性）
     */
    var currentSession by mutableStateOf<com.claudecodeplus.session.models.SessionInfo?>(null)
    
    /**
     * 正在执行的工具调用
     */
    val runningToolCalls = mutableStateListOf<ToolCall>()
    
    /**
     * Claude进程（兼容性）
     */
    var claudeProcess by mutableStateOf<Process?>(null)
    
    // ========== 生命周期管理 ==========
    
    init {
        // 如果有初始消息，更新状态
        if (initialMessages.isNotEmpty()) {
            isFirstMessage = false
            messageLoadingState = MessageLoadingState.HISTORY_LOADED
        }
        
        println("[SessionObjectV2] 创建: sessionId=$initialSessionId, messages=${initialMessages.size}")
    }
    
    // ========== 公共方法 ==========
    
    /**
     * 发送消息（简化版，暂不实现实际发送逻辑）
     */
    suspend fun sendMessage(message: String): Boolean {
        if (isGenerating) {
            println("[SessionObjectV2] 会话正在生成中，添加到队列")
            addToQueue(message)
            return false
        }
        
        return try {
            println("[SessionObjectV2] 发送消息: ${message.take(50)}...")
            
            // 创建用户消息
            val userMessage = EnhancedMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = message,
                timestamp = System.currentTimeMillis()
            )
            addMessage(userMessage)
            
            // TODO: 实现实际的发送逻辑
            // 这里可以集成新的服务层
            
            clearInput()
            clearContexts()
            true
            
        } catch (e: Exception) {
            println("[SessionObjectV2] 发送消息异常: ${e.message}")
            setError("发送异常: ${e.message}")
            false
        }
    }
    
    /**
     * 中断生成
     */
    suspend fun interruptGeneration() {
        println("[SessionObjectV2] 中断生成")
        isGenerating = false
        currentStreamJob?.cancel()
        currentStreamJob = null
        currentProcess?.destroyForcibly()
        currentProcess = null
        runningToolCalls.clear()
        currentTaskDescription = null
        taskStartTime = null
    }
    
    /**
     * 加载历史消息（简化版）
     */
    suspend fun loadHistoryMessages(sessionId: String, forceReload: Boolean = false) {
        if (isLoadingSession && !forceReload) {
            return
        }
        
        try {
            println("[SessionObjectV2] 加载历史消息: sessionId=$sessionId, forceReload=$forceReload")
            isLoadingSession = true
            messageLoadingState = MessageLoadingState.LOADING_HISTORY
            
            // TODO: 实现实际的历史消息加载逻辑
            // 这里可以集成新的服务层
            
            messageLoadingState = MessageLoadingState.HISTORY_LOADED
            isLoadingSession = false
        } catch (e: Exception) {
            println("[SessionObjectV2] 加载历史消息异常: ${e.message}")
            setError("加载异常: ${e.message}")
            messageLoadingState = MessageLoadingState.ERROR
            isLoadingSession = false
        }
    }
    
    /**
     * 清空会话
     */
    fun clearSession() {
        sessionId = null
        messages = emptyList()
        contexts = emptyList()
        isGenerating = false
        clearError()
        clearInput()
        questionQueue.clear()
        scrollPosition = 0f
        sessionInfo = null
        currentSession = null
        runningToolCalls.clear()
        claudeProcess = null
        currentStreamJob?.cancel()
        currentStreamJob = null
        currentProcess = null
        currentTaskDescription = null
        taskStartTime = null
        isFirstMessage = true
        messageLoadingState = MessageLoadingState.IDLE
        println("[SessionObjectV2] 会话已清空")
    }
    
    // ========== 状态管理方法 ==========
    
    /**
     * 更新会话ID
     */
    private fun updateSessionId(newSessionId: String) {
        val oldSessionId = sessionId
        sessionId = newSessionId
        
        println("[SessionObjectV2] 会话ID已更新: $oldSessionId -> $newSessionId")
        
        // 标记不再是首次消息
        if (!newSessionId.isNullOrEmpty()) {
            isFirstMessage = false
        }
        
        // TODO: 重新设置事件监听
        // 在集成服务层时实现
    }
    
    /**
     * 添加消息
     */
    fun addMessage(message: EnhancedMessage) {
        // 避免重复添加相同ID的消息
        val isDuplicate = messages.any { it.id == message.id }
        if (!isDuplicate) {
            messages = messages + message
            println("[SessionObjectV2] 添加消息: ${message.role}, content=${message.content.take(50)}...")
        }
    }
    
    /**
     * 更新最后一条消息
     */
    fun updateLastMessage(updater: (EnhancedMessage) -> EnhancedMessage) {
        if (messages.isNotEmpty()) {
            messages = messages.dropLast(1) + updater(messages.last())
        }
    }
    
    /**
     * 替换指定ID的消息
     */
    fun replaceMessage(messageId: String, updater: (EnhancedMessage) -> EnhancedMessage) {
        messages = messages.map { msg ->
            if (msg.id == messageId) updater(msg) else msg
        }
    }
    
    /**
     * 开始生成
     */
    fun startGenerating(job: Job, taskDescription: String? = null) {
        currentStreamJob = job
        isGenerating = true
        currentTaskDescription = taskDescription
        taskStartTime = System.currentTimeMillis()
    }
    
    /**
     * 停止生成
     */
    fun stopGenerating() {
        currentStreamJob?.cancel()
        currentStreamJob = null
        isGenerating = false
        currentTaskDescription = null
        taskStartTime = null
        runningToolCalls.clear()
    }
    
    /**
     * 设置错误
     */
    private fun setError(error: String) {
        errorMessage = error
        println("[SessionObjectV2] 会话错误: $error")
    }
    
    /**
     * 清除错误
     */
    private fun clearError() {
        errorMessage = null
    }
    
    /**
     * 标记会话已开始
     */
    fun markSessionStarted() {
        isFirstMessage = false
    }
    
    /**
     * 历史加载完成回调
     */
    fun onHistoryLoaded() {
        isFirstMessage = false
        messageLoadingState = MessageLoadingState.HISTORY_LOADED
    }
    
    // ========== 输入管理 ==========
    
    /**
     * 更新输入文本
     */
    fun updateInputText(textFieldValue: TextFieldValue) {
        inputTextFieldValue = textFieldValue
    }
    
    /**
     * 清空输入
     */
    fun clearInput() {
        inputTextFieldValue = TextFieldValue("")
        showContextSelector = false
        atSymbolPosition = null
    }
    
    /**
     * 输入文本属性（兼容性）
     */
    var inputText: String
        get() = inputTextFieldValue.text
        set(value) {
            inputTextFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    
    // ========== 队列管理 ==========
    
    /**
     * 添加到队列
     */
    fun addToQueue(question: String) {
        questionQueue.add(question)
    }
    
    /**
     * 从队列获取下一个问题
     */
    fun getNextFromQueue(): String? {
        return if (questionQueue.isNotEmpty()) {
            questionQueue.removeAt(0)
        } else null
    }
    
    /**
     * 清空队列
     */
    fun clearQueue() {
        questionQueue.clear()
    }
    
    /**
     * 队列大小
     */
    val queueSize: Int
        get() = questionQueue.size
    
    /**
     * 是否有待处理的问题
     */
    val hasQueuedQuestions: Boolean
        get() = questionQueue.isNotEmpty()
    
    // ========== 上下文管理 ==========
    
    /**
     * 添加上下文
     */
    fun addContext(context: ContextReference) {
        contexts = contexts + context
    }
    
    /**
     * 移除上下文
     */
    fun removeContext(context: ContextReference) {
        contexts = contexts - context
    }
    
    /**
     * 清空上下文
     */
    fun clearContexts() {
        contexts = emptyList()
    }
    
    // ========== 状态查询 ==========
    
    /**
     * 是否有有效的会话ID
     */
    val hasSessionId: Boolean
        get() = !sessionId.isNullOrEmpty()
    
    /**
     * 是否为新会话
     */
    val isNewSession: Boolean
        get() = sessionId == null
    
    /**
     * 获取项目工作目录
     */
    fun getProjectCwd(): String? = project?.path
    
    // ========== 工具调用管理（兼容性） ==========
    
    /**
     * 添加正在执行的工具调用
     */
    fun addRunningToolCall(toolCall: ToolCall) {
        runningToolCalls.add(toolCall)
    }
    
    /**
     * 移除已完成的工具调用
     */
    fun removeRunningToolCall(toolCallId: String) {
        runningToolCalls.removeAll { it.id == toolCallId }
    }
    
    /**
     * 更新工具调用状态
     */
    fun updateToolCallStatus(toolCallId: String, status: ToolCallStatus, result: ToolResult? = null) {
        runningToolCalls.find { it.id == toolCallId }?.let { toolCall ->
            val updatedToolCall = toolCall.copy(
                status = status,
                result = result,
                endTime = if (status in listOf(ToolCallStatus.SUCCESS, ToolCallStatus.FAILED)) 
                    System.currentTimeMillis() else null
            )
            
            val index = runningToolCalls.indexOf(toolCall)
            if (index >= 0) {
                runningToolCalls[index] = updatedToolCall
            }
            
            // 如果完成，从运行列表中移除
            if (status in listOf(ToolCallStatus.SUCCESS, ToolCallStatus.FAILED)) {
                removeRunningToolCall(toolCallId)
            }
        }
    }
    
    /**
     * 获取正在执行的工具调用数量
     */
    val runningToolCallsCount: Int
        get() = runningToolCalls.size
    
    /**
     * 检查是否有工具正在执行
     */
    val hasRunningToolCalls: Boolean
        get() = runningToolCalls.isNotEmpty()
    
    // ========== 状态保存和恢复（兼容性） ==========
    
    /**
     * 保存会话状态
     */
    fun saveSessionState(): SessionState {
        return SessionState(
            sessionId = sessionId,
            messages = messages,
            contexts = contexts,
            isFirstMessage = isFirstMessage,
            inputTextFieldValue = inputTextFieldValue,
            selectedModel = selectedModel,
            selectedPermissionMode = selectedPermissionMode,
            skipPermissions = skipPermissions,
            messageLoadingState = messageLoadingState,
            scrollPosition = scrollPosition
        )
    }
    
    /**
     * 恢复会话状态
     */
    fun restoreSessionState(state: SessionState) {
        sessionId = state.sessionId
        messages = state.messages
        contexts = state.contexts
        isFirstMessage = state.isFirstMessage
        inputTextFieldValue = state.inputTextFieldValue
        selectedModel = state.selectedModel
        selectedPermissionMode = state.selectedPermissionMode
        skipPermissions = state.skipPermissions
        messageLoadingState = state.messageLoadingState
        scrollPosition = state.scrollPosition
        
        println("[SessionObjectV2] 会话状态已恢复: sessionId=$sessionId, messages=${messages.size}")
    }
    
    // ========== 兼容性方法 ==========
    
    /**
     * 兼容原有的sendMessage方法
     */
    suspend fun sendMessage(
        markdownText: String,
        workingDirectory: String
    ): com.claudecodeplus.sdk.types.QueryResult {
        println("[SessionObjectV2] 发送消息 (兼容模式): ${markdownText.take(50)}...")
        
        if (isGenerating) {
            println("[SessionObjectV2] 会话正在生成中，添加到队列")
            addToQueue(markdownText)
            inputResetTrigger = System.currentTimeMillis()
            throw IllegalStateException("会话正在生成中，已添加到队列")
        }
        
        // 使用新的发送消息方法
        val success = sendMessage(markdownText)
        
        // 构造兼容的返回结果
        return com.claudecodeplus.sdk.types.QueryResult(
            success = success,
            error = if (!success) errorMessage else null,
            sessionId = sessionId
        )
    }
    
    /**
     * 兼容的历史消息加载
     */
    suspend fun loadNewMessages(forceFullReload: Boolean = false) {
        sessionId?.let { id ->
            loadHistoryMessages(id, forceFullReload)
        }
    }
    
    /**
     * 保存输入状态（兼容性）
     */
    fun saveInputState(textFieldValue: TextFieldValue) {
        inputTextFieldValue = textFieldValue
    }
    
    /**
     * 恢复输入状态（兼容性）
     */
    fun restoreInputState(): TextFieldValue {
        return inputTextFieldValue
    }
    
    /**
     * 处理历史消息（兼容性）
     */
    fun processHistoryMessage(message: EnhancedMessage) {
        messages = messages + message
    }
    
    /**
     * 处理新消息（兼容性）
     */
    fun processNewMessage(message: EnhancedMessage) {
        addMessage(message)
    }
    
    // ========== 生命周期清理 ==========
    
    /**
     * 清理资源
     */
    fun dispose() {
        coroutineScope.cancel()
        currentStreamJob?.cancel()
        currentProcess?.destroyForcibly()
    }
    
    override fun toString(): String {
        return "SessionObjectV2(sessionId=$sessionId, messages=${messages.size}, isGenerating=$isGenerating, queue=${questionQueue.size})"
    }
}