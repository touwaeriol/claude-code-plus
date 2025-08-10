package com.claudecodeplus.ui.models

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import com.claudecodeplus.session.models.SessionInfo
import com.claudecodeplus.ui.services.DefaultSessionConfig
import kotlinx.coroutines.Job
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.ClaudeSessionMessage
import com.claudecodeplus.session.models.toEnhancedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    initialSkipPermissions: Boolean? = null,
    private val project: Project? = null  // 关联的项目对象，用于获取cwd和项目信息
) {
    // 兼容性属性：从project获取路径
    private val projectPath: String? 
        get() = project?.path
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
    
    // ========== 状态管理 ==========
    
    /**
     * 消息加载状态
     */
    var messageLoadingState by mutableStateOf(MessageLoadingState.IDLE)
    
    /**
     * 当前运行的进程（用于中断功能）
     */
    var currentProcess by mutableStateOf<Process?>(null)
    
    /**
     * 错误消息
     */
    var errorMessage by mutableStateOf<String?>(null)
    
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
    fun interruptGeneration() {
        // 取消协程任务
        currentStreamJob?.cancel()
        currentStreamJob = null
        
        // 终止进程
        currentProcess?.let { process ->
            try {
                process.destroyForcibly()
                println("Terminated process for session: $sessionId")
            } catch (e: Exception) {
                println("Error terminating process: ${e.message}")
            }
        }
        currentProcess = null
        
        isGenerating = false
    }
    
    /**
     * 处理历史消息加载（来自事件流）
     */
    fun processHistoryMessage(message: com.claudecodeplus.ui.models.EnhancedMessage) {
        // 添加到消息列表，但不触发新消息通知
        messages = messages + message
    }
    
    /**
     * 处理实时消息（来自事件流）
     */
    fun processNewMessage(message: com.claudecodeplus.ui.models.EnhancedMessage) {
        // 添加到消息列表
        messages = messages + message
        
        // EnhancedMessage 不包含 sessionId 属性，会话 ID 由其他途径获取
        // 此处保留原有逻辑结构，但移除对 sessionId 属性的引用
    }
    
    /**
     * 设置错误消息
     */
    fun setError(error: String) {
        errorMessage = error
        isGenerating = false
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        errorMessage = null
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
        // fileTracker = null // 已移除文件追踪器
        messageLoadingState = MessageLoadingState.IDLE
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
    
    /**
     * 获取项目的工作目录（cwd）
     * 用于Claude CLI执行时的工作目录
     */
    fun getProjectCwd(): String? {
        return project?.path
    }
    
    /**
     * 从文件加载消息
     * @param forceFullReload 是否强制全量重新加载，false 为增量更新
     */
    suspend fun loadNewMessages(forceFullReload: Boolean = false) {
        val currentSessionId = sessionId
        val currentProjectPath = projectPath
        
        println("[SessionObject] 📂 loadNewMessages 被调用")
        println("[SessionObject] - sessionId: $currentSessionId")
        println("[SessionObject] - projectPath: $currentProjectPath") 
        println("[SessionObject] - forceFullReload: $forceFullReload")
        println("[SessionObject] - 当前消息数量: ${messages.size}")
        
        if (currentSessionId.isNullOrEmpty() || currentProjectPath.isNullOrEmpty()) {
            println("[SessionObject] ❌ 无法加载消息：sessionId=$currentSessionId, projectPath=$currentProjectPath")
            return
        }
        
        try {
            println("[SessionObject] 📖 开始使用 ClaudeSessionManager 读取会话文件")
            
            // 使用 ClaudeSessionManager 读取会话文件
            val sessionManager = ClaudeSessionManager()
            val (sessionMessages, totalCount) = withContext(Dispatchers.IO) {
                println("[SessionObject] 🔍 在 IO 线程中读取消息...")
                
                val result = if (forceFullReload) {
                    println("[SessionObject] 🔄 执行全量重新加载")
                    // 全量重新加载
                    sessionManager.readSessionMessages(
                        sessionId = currentSessionId,
                        projectPath = currentProjectPath,
                        pageSize = Int.MAX_VALUE  // 读取所有消息
                    )
                } else {
                    // 增量加载：只读取比当前消息数量更多的消息
                    val currentCount = messages.size
                    val pageSize = if (currentCount > 0) currentCount + 50 else 100
                    println("[SessionObject] 📈 执行增量加载 - currentCount: $currentCount, pageSize: $pageSize")
                    
                    sessionManager.readSessionMessages(
                        sessionId = currentSessionId,
                        projectPath = currentProjectPath,
                        pageSize = pageSize
                    )
                }
                
                println("[SessionObject] 📊 读取结果 - sessionMessages: ${result.first.size}, totalCount: ${result.second}")
                result
            }
            
            println("[SessionObject] 🔄 转换为 EnhancedMessage...")
            // 转换为 EnhancedMessage
            val enhancedMessages = sessionMessages.mapNotNull { message ->
                message.toEnhancedMessage() 
            }
            
            println("[SessionObject] ✅ 转换完成 - enhancedMessages: ${enhancedMessages.size}")
            
            // 在主线程更新消息列表
            withContext(Dispatchers.Main) {
                println("[SessionObject] 🎯 在主线程中更新消息列表...")
                println("[SessionObject] - 旧消息数量: ${messages.size}")
                println("[SessionObject] - 新消息数量: ${enhancedMessages.size}")
                println("[SessionObject] - 是否需要更新: ${forceFullReload || enhancedMessages.size != messages.size}")
                
                if (forceFullReload || enhancedMessages.size != messages.size) {
                    // 只有在强制重载或消息数量变化时才更新
                    messages = enhancedMessages
                    val action = if (forceFullReload) "强制全量重载" else "增量更新"
                    println("[SessionObject] ✅ $action 消息列表，共 ${enhancedMessages.size} 条消息")
                } else {
                    println("[SessionObject] ⏩ 消息无变化，跳过更新")
                }
            }
        } catch (e: Exception) {
            println("[SessionObject] ❌ 加载消息失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun toString(): String {
        return "SessionObject(sessionId=$sessionId, messages=${messages.size}, isGenerating=$isGenerating, queue=${questionQueue.size})"
    }
}

/**
 * 消息加载状态
 */
enum class MessageLoadingState {
    /**
     * 空闲状态
     */
    IDLE,
    
    /**
     * 正在加载历史消息
     */
    LOADING_HISTORY,
    
    /**
     * 历史消息加载完成
     */
    HISTORY_LOADED,
    
    /**
     * 正在监听新消息
     */
    LISTENING,
    
    /**
     * 加载失败
     */
    ERROR
}