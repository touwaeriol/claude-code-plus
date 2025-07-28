package com.claudecodeplus.ui.models

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import com.claudecodeplus.session.models.SessionInfo
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.services.DefaultSessionConfig
import kotlinx.coroutines.Job

/**
 * 完整的会话对象，包含会话的所有状态
 * 
 * 这个对象作为会话的单一数据源，包含：
 * - 会话标识和元信息
 * - 消息列表和上下文
 * - 生成状态和队列
 * - UI 状态（输入框内容、选择的模型等）
 * 
 * 使用对象引用而不是原始值，这样更新内部属性时
 * 对象引用保持不变，不会触发以对象为 key 的 LaunchedEffect
 */
@Stable
class SessionObject(
    initialSessionId: String? = null,
    initialMessages: List<EnhancedMessage> = emptyList(),
    initialModel: AiModel? = null,
    initialPermissionMode: PermissionMode? = null,
    initialSkipPermissions: Boolean? = null
) {
    // ========== 核心会话数据 ==========
    
    /**
     * 会话 ID（Claude CLI 返回的会话标识）
     */
    var sessionId by mutableStateOf(initialSessionId)
    
    /**
     * 消息列表
     */
    var messages by mutableStateOf(initialMessages)
    
    /**
     * 上下文引用列表
     */
    var contexts by mutableStateOf<List<ContextReference>>(emptyList())
    
    /**
     * 会话元信息
     */
    var sessionInfo by mutableStateOf<SessionInfo?>(null)
    
    /**
     * 当前会话信息（用于显示）
     */
    var currentSession by mutableStateOf<SessionInfo?>(null)
    
    // ========== 生成状态管理 ==========
    
    /**
     * 是否正在生成响应
     */
    var isGenerating by mutableStateOf(false)
    
    /**
     * 当前的生成任务
     */
    var currentStreamJob by mutableStateOf<Job?>(null)
    
    /**
     * 问题队列
     */
    val questionQueue = mutableStateListOf<String>()
    
    // ========== UI 状态 ==========
    
    /**
     * 输入框内容（用于切换会话时保存/恢复）
     */
    var inputText by mutableStateOf("")
    
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
    
    /**
     * 输入重置触发器
     */
    var inputResetTrigger by mutableStateOf<Any?>(null)
    
    // ========== 状态查询方法 ==========
    
    /**
     * 判断是否有有效的会话 ID
     */
    val hasSessionId: Boolean
        get() = !sessionId.isNullOrEmpty()
    
    /**
     * 判断是否为新会话（sessionId 为 null）
     */
    val isNewSession: Boolean
        get() = sessionId == null
    
    /**
     * 获取队列中的问题数量
     */
    val queueSize: Int
        get() = questionQueue.size
    
    /**
     * 是否有待处理的问题
     */
    val hasQueuedQuestions: Boolean
        get() = questionQueue.isNotEmpty()
    
    // ========== 状态管理方法 ==========
    
    /**
     * 更新会话 ID
     */
    fun updateSessionId(newSessionId: String?) {
        sessionId = newSessionId
    }
    
    /**
     * 添加消息
     */
    fun addMessage(message: EnhancedMessage) {
        messages = messages + message
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
     * 替换指定 ID 的消息
     */
    fun replaceMessage(messageId: String, updater: (EnhancedMessage) -> EnhancedMessage) {
        messages = messages.map { msg ->
            if (msg.id == messageId) updater(msg) else msg
        }
    }
    
    /**
     * 开始生成
     */
    fun startGenerating(job: Job) {
        currentStreamJob = job
        isGenerating = true
    }
    
    /**
     * 停止生成
     */
    fun stopGenerating() {
        currentStreamJob?.cancel()
        currentStreamJob = null
        isGenerating = false
    }
    
    /**
     * 中断当前任务（强制停止）
     */
    fun interruptGeneration(cliWrapper: ClaudeCliWrapper) {
        // 先取消协程任务
        currentStreamJob?.cancel()
        currentStreamJob = null
        
        // 终止 CLI 进程
        try {
            cliWrapper.terminate()
        } catch (e: Exception) {
            // 忽略终止异常
        }
        
        // 注意：不要在这里设置 isGenerating = false
        // 应该等到进程真正结束后再设置
    }
    
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
    
    /**
     * 清空整个会话
     */
    fun clearSession() {
        sessionId = null
        messages = emptyList()
        contexts = emptyList()
        isGenerating = false
        currentStreamJob = null
        questionQueue.clear()
        inputText = ""
        sessionInfo = null
        currentSession = null
        isLoadingSession = false
    }
    
    /**
     * 保存输入状态（切换会话时调用）
     */
    fun saveInputState(text: String) {
        inputText = text
    }
    
    /**
     * 恢复输入状态
     */
    fun restoreInputState(): String {
        return inputText
    }
    
    override fun toString(): String {
        return "SessionObject(sessionId=$sessionId, messages=${messages.size}, isGenerating=$isGenerating, queue=${questionQueue.size})"
    }
}