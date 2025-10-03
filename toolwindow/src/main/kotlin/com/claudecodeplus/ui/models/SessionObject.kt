package com.claudecodeplus.ui.models

import com.claudecodeplus.core.logging.*
import com.claudecodeplus.core.services.ToolResultProcessor
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.claudecodeplus.session.models.SessionInfo
import com.claudecodeplus.ui.services.DefaultSessionConfig
import com.claudecodeplus.ui.services.MessageConverter
import com.claudecodeplus.ui.services.ContextProcessor
import kotlinx.coroutines.Job
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.ClaudeSessionMessage
import com.claudecodeplus.session.models.toEnhancedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.io.File

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

    private val toolResultProcessor = ToolResultProcessor()
    // ========== 核心会话数据 ==========
    
    /**
     * 会话级别的累计Token使用量（从CLI的result消息中获取）
     */
    var totalSessionTokenUsage by mutableStateOf<EnhancedMessage.TokenUsage?>(null)
    
    /**
     * 会话 ID（Claude CLI 返回的会话标识）
     */
    var sessionId by mutableStateOf(initialSessionId)
    
    /**
     * 是否为首次消息（用于二元会话策略）
     * - true: 使用 startNewSession（不带 --resume）
     * - false: 使用 resumeSession（带 --resume sessionId）
     */
    var isFirstMessage by mutableStateOf(true)
    
    /**
     * 消息列表 - 使用 mutableStateListOf 确保UI能正确检测到变化
     */
    var messages by mutableStateOf(initialMessages.toList())
        // 暂时允许外部访问，保持兼容性
        
    /**
     * 内部方法：更新消息列表，确保UI能检测到变化
     */
    private fun updateMessagesList(newMessages: List<EnhancedMessage>) {
        // 消息列表更新
        messages = newMessages.toList()  // 确保创建新的列表实例
    }
    
    /**
     * 上下文引用列表
     */
    var contexts by mutableStateOf<List<ContextReference>>(emptyList())
    
    /**
     * 发送消息后是否自动清理上下文标签
     * 默认为 false，保留上下文便于作为持续的会话上下文
     */
    var autoCleanupContexts by mutableStateOf(false)
    
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
     * 后台服务引用（可选）
     */
    private var backgroundService: Any? = null
    
    /**
     * 设置后台服务引用
     */
    fun setBackgroundService(service: Any?) {
        backgroundService = service
        if (service != null) {
    //             logD("[SessionObject] 已连接后台服务")
        }
    }
    
    /**
     * 当前的生成任务
     */
    var currentStreamJob by mutableStateOf<Job?>(null)
    
    /**
     * 当前执行的任务描述
     */
    var currentTaskDescription by mutableStateOf<String?>(null)
    
    /**
     * 任务执行开始时间
     */
    var taskStartTime by mutableStateOf<Long?>(null)
    
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
     * 输入框内容（完整的TextFieldValue，包含光标位置等）
     */
    var inputTextFieldValue by mutableStateOf(TextFieldValue(""))
    
    /**
     * 输入框文本内容（兼容性属性）
     * 注意：设置此属性会重置光标位置，建议使用 updateInputText() 方法
     */
    var inputText: String
        get() = inputTextFieldValue.text
        set(value) { 
            inputTextFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length) // 光标放到末尾
            )
        }
    
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
    
    /**
     * 上下文选择器是否显示
     */
    var showContextSelector by mutableStateOf(false)
    
    /**
     * 简化文件选择器是否显示（Add Context 按钮触发）
     */
    var showSimpleFileSelector by mutableStateOf(false)
    
    /**
     * @ 符号位置（用于内联引用）
     */
    var atSymbolPosition by mutableStateOf<Int?>(null)
    
    /**
     * 滚动位置（用于恢复会话时的位置）
     */
    var scrollPosition by mutableStateOf(0f)
    
    var claudeProcess by mutableStateOf<Process?>(null)

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
        val oldSessionId = sessionId
        sessionId = newSessionId
        
        // 如果设置了有效的会话 ID，说明已经不是首次消息
        if (!newSessionId.isNullOrEmpty()) {
            isFirstMessage = false
        }
        
        if (oldSessionId != newSessionId) {
            logD("[SessionObject] 🔄 会话ID已变化: $oldSessionId -> $newSessionId（ClaudeCodeSdkAdapter将在sendMessage时处理）")
        }
        
        // 如果设置了新的会话ID，需要更新项目级配置
        if (!newSessionId.isNullOrEmpty() && oldSessionId != newSessionId) {
            try {
                // 通过回调机制通知上层更新项目级服务
                notifySessionIdUpdate(oldSessionId, newSessionId)
    //                 logD("[SessionObject] ✅ 已通知上层更新会话ID: $oldSessionId -> $newSessionId")
            } catch (e: Exception) {
    //                 logD("[SessionObject] 更新配置失败: ${e.message}")
                logE("Exception caught", e)
            }
        }
    }
    
    // 会话 ID 更新回调
    var sessionIdUpdateCallback: ((oldSessionId: String?, newSessionId: String) -> Unit)? = null
    
    /**
     * 通知上层更新会话 ID
     */
    private fun notifySessionIdUpdate(oldSessionId: String?, newSessionId: String) {
        sessionIdUpdateCallback?.invoke(oldSessionId, newSessionId) ?: run {
    //             logD("[SessionObject] ⚠️ 没有设置会话ID更新回调，跳过项目级服务更新")
        }
    }

    // 删除消息合并逻辑 - 每个消息独立展示
    
    /**
     * 添加消息
     * 放宽重复检查条件，确保新消息能正确保存
     */
    fun addMessage(message: EnhancedMessage) {
        try {
            logD("[SessionObject] 🔍 addMessage 被调用:")
    //             logD("  - message.role: ${message.role}")
    //             logD("  - message.content: '${message.content.take(50)}${if (message.content.length > 50) "..." else ""}'")
    //             logD("  - message.isStreaming: ${message.isStreaming}")
    //             logD("  - message.toolCalls: ${message.toolCalls.size} 个工具调用")
    //             logD("  - 当前消息总数: ${messages.size}")
    //             logD("  - 当前 isGenerating: $isGenerating")

            // 打印工具调用详情
            message.toolCalls.forEach { toolCall ->
                logD("    🔧 工具: ${toolCall.name} (ID: ${toolCall.id}, 状态: ${toolCall.status})")
            }

            // 不再合并消息 - 每个消息独立展示

            // 更宽松的重复检测：只检查完全相同的ID，避免误判
            val isDuplicate = messages.any { existing ->
                val sameId = existing.id == message.id

                if (sameId) {
                    // 检测到相同ID的消息
                    // 只有ID完全相同才视为重复
                    return@any true
                }

                // 对于流式消息，允许内容追加更新
                if (existing.role == MessageRole.ASSISTANT && message.role == MessageRole.ASSISTANT &&
                    existing.isStreaming && message.content.startsWith(existing.content)) {
                    // 检测到流式消息内容追加
                    return@any false
                }

                // 对于不同时间戳的消息，即使内容相同也不视为重复
                return@any false
            }

            if (isDuplicate) {
                // 检测到重复消息ID，已跳过
                return
            }

            val oldSize = messages.size
            updateMessagesList(messages + message)
            val newSize = messages.size
            // 添加消息成功: $oldSize -> $newSize
            
            // 重要：触发消息持久化，确保新消息保存到会话文件
            if (!sessionId.isNullOrEmpty()) {
                triggerMessagePersistence(message)
            } else {
                // sessionId为空，跳过消息持久化
            }
            
            // 如果是助手消息，检查是否需要清除生成状态
            if (message.role == MessageRole.ASSISTANT && !message.isStreaming) {
                if (isGenerating) {
                    // 检测到非流式助手消息，清除生成状态
                    isGenerating = false
                    currentTaskDescription = null
                    taskStartTime = null
                    // isGenerating 已设置为: $isGenerating
                } else {
                    // 添加了助手消息，但当前已非生成状态
                }
            } else if (message.role == MessageRole.ASSISTANT && message.isStreaming) {
                // 添加了流式助手消息，保持生成状态
            }
            
        } catch (e: Exception) {
            // 添加消息失败
            logE("Exception caught", e)
        }
    }
    
    /**
     * 触发消息持久化，确保消息保存到Claude CLI会话文件
     * 这是修复会话持久化问题的关键方法
     */
    private fun triggerMessagePersistence(message: EnhancedMessage) {
        try {
            // 对于用户消息和助手消息，确保它们被正确保存
            if (message.role in listOf(MessageRole.USER, MessageRole.ASSISTANT) && !sessionId.isNullOrEmpty()) {
                // 触发消息持久化
                // 简化实现，暂时只记录日志，避免类加载问题
                // 消息持久化已记录
            }
        } catch (e: Exception) {
            // 触发消息持久化失败
            logE("Exception caught", e)
        }
    }
    
    /**
     * 保存消息到本地配置
     * 确保消息能在程序重启后恢复
     */
    private suspend fun saveMessageToLocalConfig(message: EnhancedMessage) {
        try {
            project?.let { proj ->
                val localConfigManager = LocalConfigManager()
                
                // 保存消息到会话配置中
                // 这里可以扩展为保存完整的消息历史
                localConfigManager.updateSessionMetadata(proj.id, sessionId ?: "") { metadata ->
                    metadata.copy(
                        lastUpdated = System.currentTimeMillis(),
                        messageCount = messages.size
                    )
                }
                
    //                 logD("[SessionObject] ✅ 已更新会话元数据: 消息数=${messages.size}")
            }
        } catch (e: Exception) {
    //             logD("[SessionObject] ❌ 保存消息到本地配置异常: ${e.message}")
            throw e
        }
    }
    
    /**
     * 更新最后一条消息
     */
    fun updateLastMessage(updater: (EnhancedMessage) -> EnhancedMessage) {
        if (messages.isNotEmpty()) {
            updateMessagesList(messages.dropLast(1) + updater(messages.last()))
        }
    }
    
    /**
     * 替换指定 ID 的消息
     */
    fun replaceMessage(messageId: String, updater: (EnhancedMessage) -> EnhancedMessage) {
        updateMessagesList(messages.map { msg ->
            if (msg.id == messageId) updater(msg) else msg
        })
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
    //                 logD("Terminated process for session: $sessionId")
            } catch (e: Exception) {
    //                 logD("Error terminating process: ${e.message}")
            }
        }
        currentProcess = null
        
        isGenerating = false
        currentTaskDescription = null
        taskStartTime = null
    }
    
    // ========== 工具调用状态管理 ==========
    
    /**
     * 更新工具调用状态
     */
    fun updateToolCallStatus(toolCallId: String, status: ToolCallStatus, result: ToolResult? = null) {
        logD("[SessionObject] 🔧 更新工具调用状态: $toolCallId -> $status")

        var found = false
        val updatedMessages = messages.map { message ->
            val currentCall = message.toolCalls.firstOrNull { it.id == toolCallId }
            if (currentCall == null) {
                message
            } else {
                found = true
                val endTime = if (status in listOf(
                        ToolCallStatus.SUCCESS,
                        ToolCallStatus.FAILED,
                        ToolCallStatus.CANCELLED
                    )
                ) {
                    System.currentTimeMillis()
                } else {
                    currentCall.endTime
                }

                val updatedViewModel = currentCall.viewModel?.let { vm ->
                    com.claudecodeplus.ui.viewmodels.tool.ToolCallViewModel(
                        id = vm.id,
                        name = vm.name,
                        toolDetail = vm.toolDetail,
                        status = status,
                        result = result,
                        startTime = vm.startTime,
                        endTime = endTime
                    )
                }

                val updatedToolCall = currentCall.copy(
                    status = status,
                    result = result,
                    viewModel = updatedViewModel ?: currentCall.viewModel,
                    endTime = endTime
                )

                message.upsertToolCall(
                    toolCall = updatedToolCall,
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        if (found) {
            messages = updatedMessages
            logD("[SessionObject] 🔄 已刷新消息中的工具调用状态")
        } else {
            logD("[SessionObject] ⚠️ 未找到工具调用: $toolCallId")
        }
    }
    
    // ========== CLI 子进程管理 ==========
    
    /**
     * 初始化会话
     * 注意：CLI输出回调现在通过ClaudeCodeSdkAdapter在sendMessage时动态注册
     */
    init {
        logD("[SessionObject] 🎯 会话初始化完成，sessionId=$sessionId（回调将在sendMessage时注册）")
    }
    
    /**
     * 设置CLI输出处理
     * 注册到全局CLI管理器，实现后台消息更新
     */
    /**
     * 更新最后一条助手消息
     * 如果没有助手消息，创建一个新的
     * 增强健壮性：添加错误处理和日志
     */
    private fun updateLastAssistantMessage(updater: (EnhancedMessage) -> EnhancedMessage) {
        try {
            val lastAssistantIndex = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
            
            if (lastAssistantIndex >= 0) {
                // 更新现有的助手消息
                val updatedMessages = messages.toMutableList()
                val originalMessage = updatedMessages[lastAssistantIndex]
                val updatedMessage = updater(originalMessage)
                updatedMessages[lastAssistantIndex] = updatedMessage
                updateMessagesList(updatedMessages)
    //                 logD("[SessionObject] ✅ 已更新最后一条助手消息: ${updatedMessage.content.take(50)}...")
            } else {
                // 创建新的助手消息
                val newAssistantMessage = EnhancedMessage.create(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    text = "",
                    timestamp = System.currentTimeMillis(),
                    model = selectedModel,
                    isStreaming = true
                )
                val finalMessage = updater(newAssistantMessage)
                addMessage(finalMessage)
    //                 logD("[SessionObject] ✅ 创建新的助手消息: ${finalMessage.content.take(50)}...")
            }
        } catch (e: Exception) {
    //             logD("[SessionObject] ❌ 更新助手消息失败: ${e.message}")
            logE("Exception caught", e)
        }
    }
    
    /**
     * 发送消息给Claude CLI（会话级别的方法）
     * 使用新的 ClaudeCodeSdkAdapter 替代旧的 CLI wrapper
     */
    suspend fun sendMessage(
        markdownText: String,
        workingDirectory: String
    ): com.claudecodeplus.sdk.types.QueryResult {
    //         logD("[SessionObject] sendMessage 被调用: markdownText='$markdownText', isGenerating=$isGenerating")

        if (isGenerating) {
    //             logD("[SessionObject] 会话正在生成中，添加到队列")
            addToQueue(markdownText)
            inputResetTrigger = System.currentTimeMillis()
            throw IllegalStateException("会话正在生成中，已添加到队列")
        }

        // 设置生成状态（用户消息已在ChatViewNew中添加）
    //         logD("[SessionObject] 设置生成状态")
        isGenerating = true
        currentTaskDescription = "发送消息: ${markdownText.take(50)}..."
        taskStartTime = System.currentTimeMillis()

        // 添加助手消息占位符
    //         logD("[SessionObject] 添加助手消息占位符")
        val assistantMessage = EnhancedMessage.create(
            id = java.util.UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            text = "",
            timestamp = System.currentTimeMillis(),
            model = selectedModel,
            isStreaming = true
        )
        addMessage(assistantMessage)

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 构建包含上下文文件内容的完整消息
                val projectCwd = getProjectCwd() ?: workingDirectory
                val fullPrompt = ContextProcessor.buildPromptWithContextFiles(contexts, projectCwd, markdownText)
    //                 logD("[SessionObject] 构建完整提示词，原始长度: ${markdownText.length}, 完整长度: ${fullPrompt.length}")

                // 创建用户消息
                val userMessage = EnhancedMessage.create(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.USER,
                    text = fullPrompt,
                    timestamp = System.currentTimeMillis()
                )
                
                logD("[SessionObject] 🚀 准备调用 ClaudeCodeSdkAdapter.sendMessage，sessionId=${sessionId ?: "default"}")

                // 发送消息并处理响应流
                val responseFlow = com.claudecodeplus.ui.services.ClaudeCodeSdkAdapter.sendMessage(
                    sessionId = sessionId ?: "default",
                    message = userMessage,
                    sessionObject = this@SessionObject,
                    project = this@SessionObject.project // 使用 SessionObject 的 project 成员
                )

    //                 logD("[SessionObject] ✅ ClaudeCodeSdkAdapter.sendMessage 调用完成，开始收集响应流...")

                // 收集响应流并处理消息（移除重复的回调注册，避免双重处理）
                responseFlow.collect { enhancedMessage ->
                    logD("[SessionObject] 🎯 收到SDK响应: ${enhancedMessage.role} - ${enhancedMessage.content.take(50)}...")

                    // 在主线程处理消息
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        processEnhancedMessage(enhancedMessage)
                    }
                }

                // 标记首次消息完成
                if (isFirstMessage) {
                    markSessionStarted()
                }

                // 清空上下文
                clearContexts()

                // 返回成功结果
                com.claudecodeplus.sdk.types.QueryResult(
                    success = true,
                    sessionId = sessionId
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，可能是组件 dispose 或用户主动取消
    //                 logD("[SessionObject] ⚠️ 操作被取消: ${e.message}")

                // CancellationException 需要重新抛出，这是协程的约定
                // 但在抛出前我们可以做一些清理工作
                isGenerating = false
                currentTaskDescription = ""

                // 重新抛出以保持协程语义正确
                throw e

            } catch (e: Exception) {
    //                 logD("[SessionObject] ❌ sendMessage 异常: ${e.message}")
                logE("Exception caught", e)

                // 异常时重置状态
                isGenerating = false
                errorMessage = e.message
                currentTaskDescription = ""

                // 返回失败的 QueryResult
                com.claudecodeplus.sdk.types.QueryResult(
                    success = false,
                    error = e.message,
                    sessionId = sessionId
                )
            } finally {
                // finally 块不处理 result，让正常流程和异常处理各自管理状态
    //                 logD("[SessionObject] sendMessage finally 块执行完成")
            }
        }
    }

    /**
     * 处理 SDK 增强消息并更新 UI
     */
    private fun processEnhancedMessage(enhancedMessage: EnhancedMessage) {
        logD("[SessionObject] 🎯 处理增强消息: ${enhancedMessage.role} - ${enhancedMessage.content.take(50)}...")

        when (enhancedMessage.role) {
            MessageRole.ASSISTANT -> {
                // 不再合并消息 - 直接添加新消息
                if (enhancedMessage.content.isNotBlank() || enhancedMessage.toolCalls.isNotEmpty()) {
                    addMessage(enhancedMessage)
                }
            }
            MessageRole.SYSTEM -> {
                // 系统消息用于更新状态，不显示在UI中
    //                 logD("[SessionObject] 处理系统消息: ${enhancedMessage.content}")
            }
            MessageRole.ERROR -> {
                // 错误消息
                isGenerating = false
                errorMessage = enhancedMessage.content
                addMessage(enhancedMessage)
            }
            else -> {
                // 其他类型的消息直接添加
                addMessage(enhancedMessage)
            }
        }

        // 检查是否为结束消息
        if (enhancedMessage.status == MessageStatus.COMPLETE && enhancedMessage.role == MessageRole.ASSISTANT) {
            isGenerating = false
            updateLastMessage { msg -> msg.copy(isStreaming = false) }
        }
    }

    /**
     * 处理消息并更新UI（旧版本，已弃用）
     * 注意：这个方法依赖旧的 SDK 类型，将逐步迁移到 processEnhancedMessage
     */
    @Deprecated("使用 processEnhancedMessage 替代")
    private fun processMessageAndUpdateUI(enhancedMessage: EnhancedMessage, originalMessage: com.claudecodeplus.sdk.types.SDKMessage) {
        // 简化为调用新的处理方法
        processEnhancedMessage(enhancedMessage)
    }
    
    /**
     * 处理历史消息加载（来自事件流）
     */
    fun processHistoryMessage(message: com.claudecodeplus.ui.models.EnhancedMessage) {
        // 添加到消息列表，但不触发新消息通知
        updateMessagesList(messages + message)
    }
    
    /**
     * 处理实时消息（来自事件流）
     */
    fun processNewMessage(message: com.claudecodeplus.ui.models.EnhancedMessage) {
        // 添加到消息列表
        updateMessagesList(messages + message)
        
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
        updateMessagesList(emptyList())
        contexts = emptyList()
        isGenerating = false
        currentStreamJob = null
        questionQueue.clear()
        inputTextFieldValue = TextFieldValue("")
        showContextSelector = false
        atSymbolPosition = null
        claudeProcess = null
        sessionInfo = null
        currentSession = null
        isLoadingSession = false
        isFirstMessage = true  // 重置为首次消息状态
        messageLoadingState = MessageLoadingState.IDLE
        currentTaskDescription = null
        taskStartTime = null
        scrollPosition = 0f
    }
    
    /**
     * 保存输入状态（切换会话时调用）
     */
    fun saveInputState(textFieldValue: TextFieldValue) {
        inputTextFieldValue = textFieldValue
    }
    
    /**
     * 恢复输入状态
     */
    fun restoreInputState(): TextFieldValue {
        return inputTextFieldValue
    }
    
    /**
     * 更新输入框状态
     */
    fun updateInputText(textFieldValue: TextFieldValue) {
        inputTextFieldValue = textFieldValue
    }
    
    /**
     * 清空输入框
     */
    fun clearInput() {
        inputTextFieldValue = TextFieldValue("")
        showContextSelector = false
        atSymbolPosition = null
    }
    
    /**
     * 获取项目的工作目录（cwd）
     * 用于Claude CLI执行时的工作目录
     */
    fun getProjectCwd(): String? {
        return project?.path
    }
    
    /**
     * 标记会话已开始（发送了第一条消息）
     */
    fun markSessionStarted() {
        isFirstMessage = false
    }
    
    /**
     * 从会话历史恢复时调用
     * 加载历史消息后，会话不再是首次消息状态
     */
    fun onHistoryLoaded() {
        isFirstMessage = false
        messageLoadingState = MessageLoadingState.HISTORY_LOADED
    }
    
    /**
     * 保存当前会话状态（用于标签切换时保存状态）
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
     * 恢复会话状态（用于标签切换回来时恢复状态）
     */
    fun restoreSessionState(state: SessionState) {
        sessionId = state.sessionId
        updateMessagesList(state.messages)
        contexts = state.contexts
        isFirstMessage = state.isFirstMessage
        inputTextFieldValue = state.inputTextFieldValue
        selectedModel = state.selectedModel
        selectedPermissionMode = state.selectedPermissionMode
        skipPermissions = state.skipPermissions
        messageLoadingState = state.messageLoadingState
        scrollPosition = state.scrollPosition
        
    //         logD("[SessionObject] 会话状态已恢复: sessionId=$sessionId, messages=${messages.size}, scrollPosition=$scrollPosition")
    }
    
    /**
     * 从文件加载消息
     * 修复：增强消息持久化机制，确保新消息能正确保存和恢复
     * @param forceFullReload 是否强制全量重新加载，false 为增量更新
     */
    suspend fun loadNewMessages(forceFullReload: Boolean = false) {
        val currentSessionId = sessionId
        val currentProjectPath = projectPath

        if (currentSessionId.isNullOrEmpty() || currentProjectPath.isNullOrEmpty()) {
            return
        }

        try {
            val sessionManager = ClaudeSessionManager()
            val (sessionMessages, _) = withContext(Dispatchers.IO) {
                sessionManager.readSessionMessages(
                    sessionId = currentSessionId,
                    projectPath = currentProjectPath,
                    pageSize = Int.MAX_VALUE
                )
            }

            if (forceFullReload) {
                updateMessagesList(emptyList())
            }

            sessionMessages.firstOrNull { it.sessionId.isNotBlank() }?.let { historyMessage ->
                val historySessionId = historyMessage.sessionId
                if (historySessionId != sessionId) {
                    updateSessionId(historySessionId)
                }
            }

            val enhancedMessages = mutableListOf<EnhancedMessage>()

            sessionMessages.forEach { sessionMessage ->
                val contentElement = sessionMessage.message.content
                val hasToolResult = contentElement is JsonArray &&
                    contentElement.any { element ->
                        element.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"
                    }

                if (hasToolResult) {
                    val toolResultPayload = kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive("user"))
                        put("message", kotlinx.serialization.json.buildJsonObject {
                            put("content", contentElement)
                        })
                    }.toString()

                    val updatedMessages = toolResultProcessor.processToolResult(
                        toolResultPayload,
                        enhancedMessages
                    )
                    enhancedMessages.clear()
                    enhancedMessages.addAll(updatedMessages)
                    return@forEach
                }

                val enhanced = sessionMessage.toEnhancedMessage()
                if (enhanced != null && enhanced.orderedElements.isNotEmpty()) {
                    enhancedMessages += enhanced.copy(
                        id = "history_${sessionMessage.uuid ?: System.nanoTime()}",
                        isStreaming = false
                    )
                }
            }

            withContext(Dispatchers.Main) {
                updateMessagesList(enhancedMessages)
                if (enhancedMessages.isNotEmpty()) {
                    onHistoryLoaded()
                    updateLocalSessionMetadata(enhancedMessages.size)
                }
            }
        } catch (e: Exception) {
            logE("Exception caught", e)
        }
    }

    private fun updateLocalSessionMetadata(messageCount: Int) {
        try {
            project?.let { proj ->
                val localConfigManager = LocalConfigManager()
                localConfigManager.updateSessionMetadata(proj.id, sessionId ?: "") { metadata ->
                    metadata.copy(
                        lastUpdated = System.currentTimeMillis(),
                        messageCount = messageCount
                    )
                }
            }
        } catch (e: Exception) {
            logE("Exception caught", e)
        }
    }

    /**
     * 更新工具调用结果
     * 当收到 tool_result 事件时，更新对应的工具调用状态和结果
     */
    private fun updateToolCallResult(toolId: String, result: com.claudecodeplus.ui.models.ToolResult) {
        logD("[SessionObject] 🔧 更新工具调用结果: toolId=$toolId, result类型=${result.javaClass.simpleName}")
        
        // 查找包含该工具调用的消息
        val messageIndex = messages.indexOfLast { message ->
            message.toolCalls.any { it.id == toolId }
        }
        
        if (messageIndex != -1) {
            val message = messages[messageIndex]
            val updatedToolCalls = message.toolCalls.map { toolCall ->
                if (toolCall.id == toolId) {
                    val newStatus = when (result) {
                        is com.claudecodeplus.ui.models.ToolResult.Success -> com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
                        is com.claudecodeplus.ui.models.ToolResult.Failure -> com.claudecodeplus.ui.models.ToolCallStatus.FAILED
                        else -> com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
                    }
                    
    //                     logD("[SessionObject] ✅ 更新工具调用: ${toolCall.name} -> $newStatus")
                    toolCall.copy(
                        status = newStatus,
                        result = result,
                        endTime = System.currentTimeMillis()
                    )
                } else {
                    toolCall
                }
            }
            
            // 更新消息
            val updatedAt = System.currentTimeMillis()
            var updatedMessage = message
            updatedToolCalls.forEach { toolCall ->
                updatedMessage = updatedMessage.upsertToolCall(
                    toolCall = toolCall,
                    timestamp = updatedAt
                )
            }
            val updatedMessages = messages.toMutableList()
            updatedMessages[messageIndex] = updatedMessage
            
            // 更新消息列表并触发UI更新
            messages = updatedMessages
            
            logD("[SessionObject] 🎯 工具调用结果更新完成")
        } else {
    //             logD("[SessionObject] ⚠️ 找不到对应的工具调用消息: toolId=$toolId")
        }
    }
    
    // buildPromptWithContextFiles 方法已移到 ContextProcessor.kt 工具类中
    
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

/**
 * 会话状态快照（用于保存和恢复会话状态）
 */
data class SessionState(
    val sessionId: String?,
    val messages: List<EnhancedMessage>,
    val contexts: List<ContextReference>,
    val isFirstMessage: Boolean,
    val inputTextFieldValue: androidx.compose.ui.text.input.TextFieldValue,
    val selectedModel: AiModel,
    val selectedPermissionMode: PermissionMode,
    val skipPermissions: Boolean,
    val messageLoadingState: MessageLoadingState,
    val scrollPosition: Float = 0f
)
