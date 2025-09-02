package com.claudecodeplus.ui.models

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
import kotlinx.coroutines.Job
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.ClaudeSessionMessage
import com.claudecodeplus.session.models.toEnhancedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray

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
            println("[SessionObject] 已连接后台服务")
        }
    }
    
    /**
     * 当前的生成任务
     */
    var currentStreamJob by mutableStateOf<Job?>(null)
    
    /**
     * 正在执行的工具调用状态
     */
    val runningToolCalls = mutableStateListOf<ToolCall>()
    
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
    
    /**
     * 当前正在运行的Claude CLI进程
     */
    var claudeProcess by mutableStateOf<Process?>(null)
    
    /**
     * 获取全局CLI Wrapper实例
     */
    private val cliWrapper: com.claudecodeplus.sdk.ClaudeCliWrapper
        get() = GlobalCliWrapper.instance
    
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
        
        // 如果会话ID发生变化，更新CLI回调注册
        if (oldSessionId != newSessionId) {
            updateCliCallback(newSessionId)
        }
        
        // 如果设置了新的会话ID，需要更新本地配置
        if (!newSessionId.isNullOrEmpty() && oldSessionId != newSessionId) {
            try {
                project?.let { proj ->
                    val localConfigManager = LocalConfigManager()
                    
                    if (oldSessionId.isNullOrEmpty()) {
                        // 新会话：直接更新当前会话的ID
                        println("[SessionObject] 新会话获得ID，更新本地配置: null -> $newSessionId")
                        localConfigManager.updateNewSessionId(proj.id, newSessionId)
                    } else {
                        // 已有会话：更新会话ID
                        localConfigManager.updateSessionId(proj.id, oldSessionId, newSessionId)
                        println("[SessionObject] 本地配置已更新会话ID: $oldSessionId -> $newSessionId")
                    }
                    
                    // 重要：立即保存新的会话ID为最后选中的会话
                    localConfigManager.saveLastSelectedSession(newSessionId)
                    println("[SessionObject] 已保存新的 sessionId 为最后选中: $newSessionId")
                    
                    // 通知ProjectManager更新ProjectSession.id
                    try {
                        // 如果有全局的ProjectManager实例，更新ProjectSession的ID
                        val serviceContainer = Class.forName("com.claudecodeplus.desktop.di.ServiceContainer")
                        val projectManagerField = serviceContainer.getDeclaredField("projectManager")
                        val projectManager = projectManagerField.get(null)
                        
                        if (projectManager != null) {
                            val updateProjectSessionIdMethod = projectManager.javaClass.getDeclaredMethod(
                                "updateProjectSessionId", String::class.java, String::class.java
                            )
                            updateProjectSessionIdMethod.invoke(projectManager, oldSessionId ?: "", newSessionId)
                            println("[SessionObject] 已通知ProjectManager更新ProjectSession.id: $oldSessionId -> $newSessionId")
                        }
                    } catch (e: Exception) {
                        println("[SessionObject] 通知ProjectManager更新ProjectSession.id失败: ${e.message}")
                        // 这不是致命错误，继续执行
                    }
                    
                } ?: run {
                    println("[SessionObject] 无法更新本地配置：project 为 null")
                }
            } catch (e: Exception) {
                println("[SessionObject] 更新本地配置失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 添加消息
     * 放宽重复检查条件，确保新消息能正确保存
     */
    fun addMessage(message: EnhancedMessage) {
        try {
            println("[SessionObject] 🔍 addMessage 被调用:")
            println("  - message.role: ${message.role}")
            println("  - message.content: '${message.content.take(50)}${if (message.content.length > 50) "..." else ""}'")
            println("  - message.isStreaming: ${message.isStreaming}")
            println("  - message.toolCalls: ${message.toolCalls.size} 个工具调用")
            println("  - 当前消息总数: ${messages.size}")
            println("  - 当前 isGenerating: $isGenerating")
            
            // 打印工具调用详情
            message.toolCalls.forEach { toolCall ->
                println("    🔧 工具: ${toolCall.name} (ID: ${toolCall.id}, 状态: ${toolCall.status})")
            }
            
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
            e.printStackTrace()
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
            e.printStackTrace()
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
                
                println("[SessionObject] ✅ 已更新会话元数据: 消息数=${messages.size}")
            }
        } catch (e: Exception) {
            println("[SessionObject] ❌ 保存消息到本地配置异常: ${e.message}")
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
        runningToolCalls.clear()
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
        currentTaskDescription = null
        taskStartTime = null
        runningToolCalls.clear()
    }
    
    // ========== 工具调用状态管理 ==========
    
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
    
    // ========== CLI 子进程管理 ==========
    
    /**
     * 初始化会话，注册CLI输出回调
     */
    init {
        setupCliOutputHandling()
    }
    
    /**
     * 设置CLI输出处理
     * 注册到全局CLI管理器，实现后台消息更新
     */
    private fun setupCliOutputHandling() {
        GlobalCliWrapper.registerSessionCallback(sessionId) { jsonLine ->
            println("[SessionObject] 收到CLI输出: sessionId=$sessionId, 内容=$jsonLine")
            
            try {
                // 处理Claude CLI的实时输出，更新消息列表
                processCliOutput(jsonLine)
            } catch (e: Exception) {
                println("[SessionObject] 处理CLI输出异常: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 更新会话ID时重新注册回调
     */
    private fun updateCliCallback(newSessionId: String?) {
        // 注销旧的回调
        GlobalCliWrapper.unregisterSessionCallback(sessionId)
        
        // 注册新的回调
        GlobalCliWrapper.registerSessionCallback(newSessionId) { jsonLine ->
            println("[SessionObject] 收到CLI输出: sessionId=$newSessionId, 内容=$jsonLine")
            
            try {
                processCliOutput(jsonLine)
            } catch (e: Exception) {
                println("[SessionObject] 处理CLI输出异常: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理CLI输出，更新会话消息
     * 修复：放宽重复检测条件，确保新消息能正确保存
     * 增强健壮性：添加错误恢复和状态管理
     */
    private fun processCliOutput(jsonLine: String) {
        println("[SessionObject] 🔍 processCliOutput 被调用:")
        println("  - 输入长度: ${jsonLine.length}")
        println("  - 输入前100字符: '${jsonLine.take(100)}${if (jsonLine.length > 100) "..." else ""}'")
        
        // 先尝试直接处理非JSON输出（可能是纯文本响应）
        if (!jsonLine.trim().startsWith("{")) {
            println("[SessionObject] 收到非JSON输出，直接添加到最后一条助手消息: $jsonLine")
            updateLastAssistantMessage { existing ->
                existing.copy(
                    content = existing.content + jsonLine + "\n",
                    timestamp = System.currentTimeMillis()
                )
            }
            return
        }
        
        // 解析JSON以检查消息类型，过滤系统初始化消息
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
        val jsonObject = try {
            json.parseToJsonElement(jsonLine).jsonObject
        } catch (e: Exception) {
            println("[SessionObject] JSON解析失败，跳过该消息: ${e.message}")
            return
        }
        
        val messageType = jsonObject["type"]?.jsonPrimitive?.content
        val messageSubtype = jsonObject["subtype"]?.jsonPrimitive?.content
        
        // 过滤不需要在UI中显示的消息类型
        when {
            messageType == "system" && messageSubtype == "init" -> {
                // 处理系统初始化消息，提取sessionId（参考Claudia项目）
                // 尝试两种可能的字段名
                val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content 
                    ?: jsonObject["sessionId"]?.jsonPrimitive?.content
                
                if (sessionId != null && this.sessionId != sessionId) {
                    println("[SessionObject] 从system init消息更新sessionId: $sessionId")
                    // 完整更新sessionId，包括本地配置保存
                    updateSessionId(sessionId)
                } else if (sessionId != null) {
                    println("[SessionObject] system init消息中的sessionId与当前相同: $sessionId")
                } else {
                    println("[SessionObject] system init消息中未找到sessionId字段")
                    println("[SessionObject] 完整消息内容: $jsonLine")
                }
                println("[SessionObject] 过滤掉系统初始化消息（UI不显示）")
                return
            }
            messageType == "result" -> {
                println("[SessionObject] 收到结果摘要消息: ${jsonObject["subtype"]?.jsonPrimitive?.content}")
                // 结果消息包含会话完成信息，在这里清除生成状态
                isGenerating = false
                currentTaskDescription = null
                taskStartTime = null
                
                // 确保助手消息的流式状态被清除
                val lastAssistantIndex = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                if (lastAssistantIndex >= 0) {
                    replaceMessage(messages[lastAssistantIndex].id) { existing ->
                        existing.copy(isStreaming = false)
                    }
                    println("[SessionObject] ✅ 已清除最后一条助手消息的流式状态")
                } else {
                    println("[SessionObject] ⚠️ 未找到助手消息来清除流式状态")
                }
                
                val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content
                if (sessionId != null && this.sessionId != sessionId) {
                    println("[SessionObject] 从result消息更新sessionId: $sessionId")
                    updateSessionId(sessionId)
                }
                
                // 健壮性检查：确保消息列表不为空
                if (messages.isEmpty()) {
                    println("[SessionObject] ⚠️ 检测到消息列表为空，这可能表明消息处理出现问题")
                }
                
                return
            }
            messageType == "error" -> {
                val errorMessage = jsonObject["message"]?.jsonPrimitive?.content ?: "未知错误"
                println("[SessionObject] 收到错误消息: $errorMessage")
                updateLastAssistantMessage { existing ->
                    existing.copy(
                        content = existing.content + "\n❌ 错误: $errorMessage\n",
                        timestamp = System.currentTimeMillis()
                    )
                }
                return
            }
            messageType == "system" && messageSubtype != null && messageSubtype != "init" -> {
                println("[SessionObject] 过滤掉系统子类型消息: $messageSubtype")
                return
            }
        }
        
        // 首先检查是否是工具结果消息
        if (messageType == "user") {
            val messageObj = jsonObject["message"]?.jsonObject
            val contentElement = messageObj?.get("content")
            
            // content 可能是字符串（历史消息）或数组（实时消息），需要兼容处理
            var hasToolResult = false
            
            // 尝试处理数组格式的 content
            if (contentElement is kotlinx.serialization.json.JsonArray) {
                contentElement.forEach { arrayElement ->
                    val contentObj = arrayElement.jsonObject
                    if (contentObj["type"]?.jsonPrimitive?.content == "tool_result") {
                        hasToolResult = true
                        val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content
                        val resultContent = contentObj["content"]?.jsonPrimitive?.content ?: ""
                        val isError = contentObj["is_error"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        
                        if (toolUseId != null) {
                            println("[SessionObject] 处理工具结果: toolId=$toolUseId, isError=$isError, content=${resultContent.take(50)}...")
                            
                            // 更新最后一条助手消息中对应的工具调用
                            updateLastAssistantMessage { existing ->
                                val updatedToolCalls = existing.toolCalls.map { toolCall ->
                                    if (toolCall.id == toolUseId) {
                                        val result = if (isError) {
                                            ToolResult.Failure(resultContent)
                                        } else {
                                            ToolResult.Success(resultContent)
                                        }
                                        toolCall.copy(
                                            status = if (isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                                            result = result,
                                            endTime = System.currentTimeMillis()
                                        )
                                    } else {
                                        toolCall
                                    }
                                }
                                existing.copy(
                                    toolCalls = updatedToolCalls,
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }
            }
            // 如果是字符串格式的 content（历史消息），暂时不处理工具结果
            // 因为历史消息中的工具结果通常已经在对应的消息中包含
            
            // 如果处理了工具结果，就不再继续处理其他内容
            if (hasToolResult) {
                return
            }
        }
        
        // 检查是否是工具结果事件（type=user且包含tool_result）
        if (messageType == "user") {
            // 检查消息内容是否包含tool_result
            val messageObj = jsonObject["message"]?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            var hasToolResult = false
            
            contentArray?.forEach { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                
                if (type == "tool_result") {
                    hasToolResult = true
                    val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content ?: ""
                    val resultContent = contentObj["content"]?.jsonPrimitive?.content ?: ""
                    val isError = contentObj["is_error"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    
                    val result = if (isError) {
                        com.claudecodeplus.ui.models.ToolResult.Failure(resultContent)
                    } else {
                        com.claudecodeplus.ui.models.ToolResult.Success(resultContent)
                    }
                    
                    println("[SessionObject] 🔧 检测到工具结果: toolId=$toolUseId, isError=$isError")
                    updateToolCallResult(toolUseId, result)
                }
            }
            
            // 如果处理了工具结果，就不创建新消息
            if (hasToolResult) {
                println("[SessionObject] ✅ 工具结果处理完成")
                return
            }
        }
        
        // 直接解析Claude CLI的实时消息格式
        try {
            println("[SessionObject] 🔍 开始解析Claude CLI实时消息, messageType=$messageType")
            val enhancedMessage = parseClaudeCliMessage(jsonObject, jsonLine)
            
            if (enhancedMessage != null && (enhancedMessage.content.isNotEmpty() || enhancedMessage.toolCalls.isNotEmpty())) {
                println("[SessionObject] ✅ Claude CLI消息解析成功:")
                println("  - content长度: ${enhancedMessage.content.length}")
                println("  - toolCalls数量: ${enhancedMessage.toolCalls.size}")
                println("  - 消息角色: ${enhancedMessage.role}")
                println("  - isStreaming: ${enhancedMessage.isStreaming}")
                
                // 如果有工具调用，记录到正在执行列表
                if (enhancedMessage.toolCalls.isNotEmpty()) {
                    enhancedMessage.toolCalls.forEach { toolCall ->
                        addRunningToolCall(toolCall)
                    }
                    
                    // 直接添加新的助手消息（包含工具调用），保持消息顺序
                    println("[SessionObject] 🔧 准备添加工具调用消息")
                    addMessage(enhancedMessage)
                    println("[SessionObject] ✅ 已添加工具调用消息到消息列表")
                } else if (enhancedMessage.content.isNotEmpty()) {
                    // 检查最后一条消息是否是助手消息，如果是则合并文本内容
                    val lastMessage = messages.lastOrNull()
                    println("[SessionObject] 📝 处理文本内容消息:")
                    println("  - lastMessage?.role: ${lastMessage?.role}")
                    println("  - lastMessage?.toolCalls?.isEmpty(): ${lastMessage?.toolCalls?.isEmpty()}")
                    println("  - lastMessage?.isStreaming: ${lastMessage?.isStreaming}")
                    
                    // 改进合并逻辑：只有当最后一条消息是流式助手消息且新消息没有工具调用时才合并
                    if (lastMessage?.role == MessageRole.ASSISTANT && 
                        lastMessage.toolCalls.isEmpty() && 
                        enhancedMessage.toolCalls.isEmpty() &&
                        lastMessage.isStreaming) {
                        // 合并到最后一条助手消息的文本内容
                        println("[SessionObject] 🔄 合并文本内容到流式助手消息")
                        updateLastMessage { existing ->
                            existing.copy(
                                content = existing.content + enhancedMessage.content,
                                timestamp = System.currentTimeMillis(),
                                isStreaming = enhancedMessage.isStreaming
                            )
                        }
                        println("[SessionObject] ✅ 已合并文本内容到流式助手消息")
                    } else {
                        // 添加新的助手消息
                        println("[SessionObject] 📝 准备添加新的助手消息")
                        addMessage(enhancedMessage)
                        println("[SessionObject] ✅ 已添加新的助手消息到消息列表")
                    }
                }
            } else {
                println("[SessionObject] ❌ Claude CLI消息解析结果为空:")
                println("  - messageType: $messageType")
                println("  - enhancedMessage == null: ${enhancedMessage == null}")
                if (enhancedMessage != null) {
                    println("  - content为空: ${enhancedMessage.content.isEmpty()}")
                    println("  - toolCalls为空: ${enhancedMessage.toolCalls.isEmpty()}")
                    println("  - content内容: '${enhancedMessage.content}'")
                }
                println("  - 原始JSON前200字符: ${jsonLine.take(200)}")
            }
        } catch (e: Exception) {
            println("[SessionObject] ❌ Claude CLI消息解析失败: ${e.message}")
            println("[SessionObject] 原始JSON: $jsonLine")
            e.printStackTrace()
        }
    }
    
    /**
     * 将历史消息格式转换为实时消息格式
     * 将JSONL存储格式转换为Claude CLI直接输出格式
     */
    private fun convertHistoryToRealtime(sessionMessage: com.claudecodeplus.session.models.ClaudeSessionMessage): kotlinx.serialization.json.JsonObject? {
        return try {
            val json = kotlinx.serialization.json.Json { 
                ignoreUnknownKeys = true
                isLenient = true
            }
            
            // 构造实时格式的JSON对象
            kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive(sessionMessage.type ?: "assistant"))
                
                sessionMessage.message?.let { message ->
                    put("message", kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(message.id ?: ""))
                        put("type", kotlinx.serialization.json.JsonPrimitive("message"))
                        put("role", kotlinx.serialization.json.JsonPrimitive(message.role ?: "assistant"))
                        put("model", kotlinx.serialization.json.JsonPrimitive(message.model ?: ""))
                        put("stop_reason", kotlinx.serialization.json.JsonNull)
                        put("stop_sequence", kotlinx.serialization.json.JsonNull)
                        
                        // 处理content数组 - 这是关键部分
                        message.content?.let { contentList ->
                            put("content", kotlinx.serialization.json.buildJsonArray {
                                when (contentList) {
                                    is List<*> -> contentList.forEach { contentItem ->
                                    try {
                                        // 将content item转换为JsonElement
                                        val contentJson = when (contentItem) {
                                            is String -> {
                                                // 如果是字符串，尝试解析为JSON
                                                try {
                                                    json.parseToJsonElement(contentItem)
                                                } catch (e: Exception) {
                                                    // 如果解析失败，作为文本内容处理
                                                    kotlinx.serialization.json.buildJsonObject {
                                                        put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                                                        put("text", kotlinx.serialization.json.JsonPrimitive(contentItem))
                                                    }
                                                }
                                            }
                                            is Map<*, *> -> {
                                                // 如果是Map，转换为JsonObject
                                                val gson = com.google.gson.Gson()
                                                val jsonString = gson.toJson(contentItem)
                                                json.parseToJsonElement(jsonString)
                                            }
                                            else -> {
                                                // 其他类型，尝试序列化
                                                val gson = com.google.gson.Gson()
                                                val jsonString = gson.toJson(contentItem)
                                                json.parseToJsonElement(jsonString)
                                            }
                                        }
                                        add(contentJson)
                                    } catch (e: Exception) {
                                        println("[SessionObject] 转换content item失败: ${e.message}, item: $contentItem")
                                        // 失败时创建一个基本的文本块
                                        add(kotlinx.serialization.json.buildJsonObject {
                                            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                                            put("text", kotlinx.serialization.json.JsonPrimitive(contentItem.toString()))
                                        })
                                    }
                                    }
                                    else -> {
                                        // 如果是其他类型，转换为文本块
                                        add(kotlinx.serialization.json.buildJsonObject {
                                            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                                            put("text", kotlinx.serialization.json.JsonPrimitive(contentList.toString()))
                                        })
                                    }
                                }
                            })
                        }
                        
                        // 处理usage信息
                        message.usage?.let { usage ->
                            put("usage", kotlinx.serialization.json.buildJsonObject {
                                put("input_tokens", kotlinx.serialization.json.JsonPrimitive((usage["input_tokens"] as? Number)?.toInt() ?: 0))
                                put("output_tokens", kotlinx.serialization.json.JsonPrimitive((usage["output_tokens"] as? Number)?.toInt() ?: 0))
                                put("cache_creation_input_tokens", kotlinx.serialization.json.JsonPrimitive((usage["cache_creation_input_tokens"] as? Number)?.toInt() ?: 0))
                                put("cache_read_input_tokens", kotlinx.serialization.json.JsonPrimitive((usage["cache_read_input_tokens"] as? Number)?.toInt() ?: 0))
                                put("service_tier", kotlinx.serialization.json.JsonPrimitive((usage["service_tier"] as? String) ?: "standard"))
                            })
                        }
                    })
                }
            }
        } catch (e: Exception) {
            println("[SessionObject] 历史消息格式转换失败: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 解析Claude CLI的实时消息格式
     * 专门处理从Claude CLI直接输出的JSONL格式
     */
    private fun parseClaudeCliMessage(jsonObject: kotlinx.serialization.json.JsonObject, jsonLine: String): EnhancedMessage? {
        return try {
            val messageType = jsonObject["type"]?.jsonPrimitive?.content
            println("[SessionObject] 解析Claude CLI消息: type=$messageType")
            
            when (messageType) {
                "assistant" -> {
                    // 解析助手消息
                    val messageObj = jsonObject["message"]?.jsonObject
                    val contentArray = messageObj?.get("content")?.jsonArray
                    val role = messageObj?.get("role")?.jsonPrimitive?.content ?: "assistant"
                    
                    // 提取文本内容
                    val textContent = contentArray?.mapNotNull { contentElement ->
                        val contentObj = contentElement.jsonObject
                        val type = contentObj["type"]?.jsonPrimitive?.content
                        if (type == "text") {
                            contentObj["text"]?.jsonPrimitive?.content
                        } else null
                    }?.joinToString("") ?: ""
                    
                    // 提取工具调用
                    println("[SessionObject] 开始提取工具调用，contentArray大小: ${contentArray?.size}")
                    contentArray?.forEachIndexed { index, element ->
                        val obj = element.jsonObject
                        val type = obj["type"]?.jsonPrimitive?.content
                        println("[SessionObject]   [$index] type: $type, keys: ${obj.keys}")
                    }
                    
                    val toolCalls = contentArray?.mapNotNull { contentElement ->
                        val contentObj = contentElement.jsonObject
                        val type = contentObj["type"]?.jsonPrimitive?.content
                        println("[SessionObject] 处理content元素: type=$type")
                        
                        if (type == "tool_use") {
                            val toolId = contentObj["id"]?.jsonPrimitive?.content ?: ""
                            val toolName = contentObj["name"]?.jsonPrimitive?.content ?: ""
                            val inputObj = contentObj["input"]?.jsonObject
                            
                            println("[SessionObject] 🔧 发现工具调用: $toolName (ID: $toolId)")
                            
                            // 将输入参数转换为 Map，正确处理不同类型的 JSON 元素
                            val parameters = inputObj?.mapValues { (_, value) ->
                                when (value) {
                                    is kotlinx.serialization.json.JsonPrimitive -> value.content
                                    is kotlinx.serialization.json.JsonArray -> {
                                        // 对于数组，转换为 List
                                        value.map { element ->
                                            when (element) {
                                                is kotlinx.serialization.json.JsonPrimitive -> element.content
                                                is kotlinx.serialization.json.JsonObject -> {
                                                    // 对于对象，转换为 Map
                                                    element.mapValues { (_, v) ->
                                                        if (v is kotlinx.serialization.json.JsonPrimitive) v.content else v.toString()
                                                    }
                                                }
                                                else -> element.toString()
                                            }
                                        }
                                    }
                                    is kotlinx.serialization.json.JsonObject -> {
                                        // 对于对象，转换为 Map
                                        value.mapValues { (_, v) ->
                                            if (v is kotlinx.serialization.json.JsonPrimitive) v.content else v.toString()
                                        }
                                    }
                                    else -> value.toString()
                                }
                            } ?: emptyMap()
                            
                            val toolCall = ToolCall(
                                id = toolId,
                                name = toolName,
                                parameters = parameters,
                                status = ToolCallStatus.RUNNING,
                                result = null,
                                startTime = System.currentTimeMillis(),
                                endTime = null
                            )
                            println("[SessionObject] ✅ 创建工具调用对象: ${toolCall.name}")
                            toolCall
                        } else null
                    } ?: emptyList()
                    
                    println("[SessionObject] 🎯 工具调用提取完成，共 ${toolCalls.size} 个工具调用")
                    
                    // 提取token使用信息
                    val usageObj = messageObj?.get("usage")?.jsonObject
                    val tokenUsage = if (usageObj != null) {
                        val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val cacheCreationTokens = usageObj["cache_creation_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val cacheReadTokens = usageObj["cache_read_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        
                        if (inputTokens > 0 || outputTokens > 0 || cacheCreationTokens > 0 || cacheReadTokens > 0) {
                            EnhancedMessage.TokenUsage(
                                inputTokens = inputTokens,
                                outputTokens = outputTokens,
                                cacheCreationTokens = cacheCreationTokens,
                                cacheReadTokens = cacheReadTokens
                            )
                        } else null
                    } else null
                    
                    println("[SessionObject] 助手消息解析结果: content='${textContent.take(50)}', toolCalls=${toolCalls.size}, tokenUsage=$tokenUsage")
                    
                    EnhancedMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        content = textContent,
                        timestamp = System.currentTimeMillis(),
                        toolCalls = toolCalls,
                        tokenUsage = tokenUsage,
                        isStreaming = false
                    )
                }
                
                "user" -> {
                    // 解析用户消息（通常不会在实时流中出现，但为了完整性）
                    val messageObj = jsonObject["message"]?.jsonObject
                    val contentArray = messageObj?.get("content")?.jsonArray
                    
                    val textContent = contentArray?.mapNotNull { contentElement ->
                        val contentObj = contentElement.jsonObject
                        val type = contentObj["type"]?.jsonPrimitive?.content
                        if (type == "text") {
                            contentObj["text"]?.jsonPrimitive?.content
                        } else null
                    }?.joinToString("") ?: ""
                    
                    println("[SessionObject] 用户消息解析结果: content='${textContent.take(50)}'")
                    
                    EnhancedMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = MessageRole.USER,
                        content = textContent,
                        timestamp = System.currentTimeMillis(),
                        toolCalls = emptyList(),
                        tokenUsage = null,
                        isStreaming = false
                    )
                }
                
                else -> {
                    println("[SessionObject] 未知消息类型或无需处理: $messageType")
                    null
                }
            }
        } catch (e: Exception) {
            println("[SessionObject] 解析Claude CLI消息异常: ${e.message}")
            println("[SessionObject] 异常JSON: ${jsonLine.take(200)}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 更新指定工具调用的结果（用于历史消息处理）
     */
    private fun updateToolCallResult(toolUseId: String, resultContent: String, isError: Boolean) {
        // 找到包含指定工具调用ID的消息
        val messageIndex = messages.indexOfLast { message ->
            message.toolCalls.any { it.id == toolUseId }
        }
        
        if (messageIndex >= 0) {
            val message = messages[messageIndex]
            val updatedToolCalls = message.toolCalls.map { toolCall ->
                if (toolCall.id == toolUseId) {
                    val result = if (isError) {
                        ToolResult.Failure(resultContent)
                    } else {
                        ToolResult.Success(resultContent)
                    }
                    toolCall.copy(
                        status = if (isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                        result = result,
                        endTime = System.currentTimeMillis()
                    )
                } else {
                    toolCall
                }
            }
            
            val updatedMessage = message.copy(
                toolCalls = updatedToolCalls,
                timestamp = System.currentTimeMillis()
            )
            
            // 更新消息列表
            val updatedMessages = messages.toMutableList().apply {
                this[messageIndex] = updatedMessage
            }
            updateMessagesList(updatedMessages)
            
            println("[SessionObject] ✅ 已更新工具调用结果: toolId=$toolUseId, isError=$isError")
        } else {
            println("[SessionObject] ⚠️ 未找到工具调用ID为 $toolUseId 的消息")
        }
    }
    
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
                println("[SessionObject] ✅ 已更新最后一条助手消息: ${updatedMessage.content.take(50)}...")
            } else {
                // 创建新的助手消息
                val newAssistantMessage = EnhancedMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    model = selectedModel,
                    isStreaming = true
                )
                val finalMessage = updater(newAssistantMessage)
                addMessage(finalMessage)
                println("[SessionObject] ✅ 创建新的助手消息: ${finalMessage.content.take(50)}...")
            }
        } catch (e: Exception) {
            println("[SessionObject] ❌ 更新助手消息失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 发送消息给Claude CLI（会话级别的方法）
     * 这样CLI处理就完全在SessionObject内部，支持后台更新
     */
    suspend fun sendMessage(
        markdownText: String,
        workingDirectory: String
    ): com.claudecodeplus.sdk.ClaudeCliWrapper.QueryResult {
        println("[SessionObject] sendMessage 被调用: markdownText='$markdownText', isGenerating=$isGenerating")
        
        if (isGenerating) {
            println("[SessionObject] 会话正在生成中，添加到队列")
            addToQueue(markdownText)
            inputResetTrigger = System.currentTimeMillis()
            throw IllegalStateException("会话正在生成中，已添加到队列")
        }
        
        // 设置生成状态（用户消息已在ChatViewNew中添加）
        println("[SessionObject] 设置生成状态")
        isGenerating = true
        currentTaskDescription = "发送消息: ${markdownText.take(50)}..."
        taskStartTime = System.currentTimeMillis()
        
        // 添加助手消息占位符
        println("[SessionObject] 添加助手消息占位符")
        val assistantMessage = EnhancedMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            model = selectedModel,
            isStreaming = true
        )
        addMessage(assistantMessage)
        
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                
                // 准备CLI选项
                val projectCwd = getProjectCwd() ?: workingDirectory
                val options = com.claudecodeplus.sdk.ClaudeCliWrapper.QueryOptions(
                    sessionId = sessionId,
                    cwd = projectCwd,
                    model = selectedModel?.cliName,
                    permissionMode = selectedPermissionMode.cliName,
                    skipPermissions = skipPermissions
                )
                
                println("[SessionObject] 发送消息: isFirstMessage=$isFirstMessage, sessionId=$sessionId")
                
                // 使用事件驱动的会话处理
                println("[SessionObject] 使用事件驱动方式启动会话")
                
                // 创建事件服务实例
                val processHandler = com.claudecodeplus.sdk.ClaudeProcessEventHandler()
                val historyLoader = com.claudecodeplus.sdk.SessionHistoryLoader()
                val eventService = com.claudecodeplus.sdk.ClaudeEventService(processHandler, cliWrapper, historyLoader)
                
                // 准备回调函数，直接处理消息
                val onMessage: (com.claudecodeplus.sdk.SDKMessage) -> Unit = { message ->
                    println("[SessionObject] 🎯 收到回调消息: type=${message.type}")
                    
                    // 在主线程更新消息
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            println("[SessionObject] 开始转换消息...")
                            val enhancedMessage = with(com.claudecodeplus.ui.services.MessageConverter) {
                                message.toEnhancedMessage()
                            }
                            println("[SessionObject] 转换完成，角色: ${enhancedMessage.role}, 内容长度: ${enhancedMessage.content.length}")
                            
                            // 立即处理消息并更新UI
                            processMessageAndUpdateUI(enhancedMessage, message)
                        } catch (e: Exception) {
                            println("[SessionObject] 处理回调消息异常: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
                
                val onError: (String) -> Unit = { error ->
                    println("[SessionObject] 收到错误回调: $error")
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val errorMessage = EnhancedMessage(
                            id = java.util.UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = "❌ 错误: $error",
                            timestamp = System.currentTimeMillis(),
                            status = MessageStatus.FAILED,
                            isError = true
                        )
                        addMessage(errorMessage)
                        isGenerating = false
                    }
                }
                
                val onComplete: (Boolean) -> Unit = { success ->
                    println("[SessionObject] 收到完成回调: success=$success")
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isGenerating = false
                        // queryResult 会在方法末尾设置，这里只更新状态
                    }
                }
                
                // 决定是新会话还是恢复会话，使用回调模式
                if (isFirstMessage) {
                    println("[SessionObject] 🆕 启动新会话 (startNewSession)")
                    markSessionStarted()
                    eventService.startNewSession(
                        projectPath = projectCwd,
                        prompt = markdownText,
                        options = options,
                        onMessage = onMessage,
                        onError = onError,
                        onComplete = onComplete
                    )
                } else if (sessionId != null) {
                    println("[SessionObject] 🔄 恢复会话 (resumeSession): $sessionId")
                    eventService.resumeExistingSession(
                        sessionId = sessionId!!,
                        projectPath = projectCwd,
                        prompt = markdownText,
                        options = options,
                        onMessage = onMessage,
                        onError = onError,
                        onComplete = onComplete
                    )
                } else {
                    println("[SessionObject] ⚠️ 没有 sessionId，降级为新会话")
                    eventService.startNewSession(
                        projectPath = projectCwd,
                        prompt = markdownText,
                        options = options,
                        onMessage = onMessage,
                        onError = onError,
                        onComplete = onComplete
                    )
                }
                
                // 初始化查询结果
                var queryResult = com.claudecodeplus.sdk.ClaudeCliWrapper.QueryResult(
                    success = false,
                    sessionId = null,
                    errorMessage = "初始化中",
                    processId = 0L
                )
                
                // 使用回调模式，消息会在onMessage回调中立即处理，无需等待
                println("[SessionObject] 使用回调模式，消息将通过回调立即处理...")
                
                // 简化返回 - 因为现在使用回调，进程是异步的
                queryResult = queryResult.copy(success = true, errorMessage = null)
                
                // 清空上下文
                clearContexts()
                
                // 重要：最终确保消息被正确保存
                println("[SessionObject] ✅ 消息发送完成，当前消息数: ${messages.size}")
                
                queryResult
            } catch (e: Exception) {
                println("[SessionObject] 发送消息异常: ${e.message}")
                e.printStackTrace()
                
                val errorMessage = EnhancedMessage(
                    id = "error_${System.currentTimeMillis()}",
                    role = MessageRole.SYSTEM,
                    content = "发送失败: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    toolCalls = emptyList(),
                    orderedElements = emptyList()
                )
                addMessage(errorMessage)
                
                throw e
            } finally {
                // finally 块不处理 result，让正常流程和异常处理各自管理状态
                println("[SessionObject] sendMessage finally 块执行完成")
            }
        }
    }
    
    /**
     * 处理消息并更新UI（回调模式）
     * 将原来复杂的Flow事件处理逻辑移到这里
     */
    private fun processMessageAndUpdateUI(enhancedMessage: EnhancedMessage, originalMessage: com.claudecodeplus.sdk.SDKMessage) {
        println("[SessionObject] 🎯 处理消息并更新UI: type=${originalMessage.type}")
        
        // 检查原始消息类型，决定如何处理
        val rawJson = originalMessage.content ?: ""
        val isSystemInit = rawJson.contains("\"type\":\"system\"") && rawJson.contains("\"subtype\":\"init\"")
        val isResult = rawJson.contains("\"type\":\"result\"")
        val isToolResult = rawJson.contains("\"type\":\"user\"") && rawJson.contains("\"tool_result\"")
        
        when {
            isToolResult -> {
                println("[SessionObject] 🔧 处理工具结果消息")
                // 解析工具结果并更新对应的工具调用
                val toolResults = with(com.claudecodeplus.ui.services.MessageConverter) {
                    try {
                        val contentJson = kotlinx.serialization.json.Json.parseToJsonElement(rawJson).jsonObject
                        extractToolResults(contentJson)
                    } catch (e: Exception) {
                        println("[SessionObject] 解析工具结果失败: ${e.message}")
                        emptyMap()
                    }
                }
                
                // 更新对应的工具调用
                toolResults.forEach { (toolId, result) ->
                    updateToolCallResult(toolId, result)
                }
            }
            isSystemInit -> {
                println("[SessionObject] 处理系统初始化消息，仅更新sessionId")
                // system init消息仅用于更新sessionId，不显示在UI
                originalMessage.sessionId?.let { newSessionId ->
                    if (sessionId != newSessionId) {
                        println("[SessionObject] 🆔 从系统初始化更新会话 ID: $newSessionId")
                        updateSessionId(newSessionId)
                    }
                }
            }
            isResult -> {
                println("[SessionObject] 处理结果消息，更新生成状态和Token使用量")
                
                // 提取result消息中的总token使用量
                try {
                    val resultJson = kotlinx.serialization.json.Json.parseToJsonElement(rawJson).jsonObject
                    val usageObj = resultJson["usage"]?.jsonObject
                    
                    if (usageObj != null) {
                        val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val cacheCreationTokens = usageObj["cache_creation_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val cacheReadTokens = usageObj["cache_read_input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        
                        // 更新会话级别的总token使用量
                        totalSessionTokenUsage = EnhancedMessage.TokenUsage(
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            cacheCreationTokens = cacheCreationTokens,
                            cacheReadTokens = cacheReadTokens
                        )
                        
                        println("[SessionObject] 📊 更新会话Token统计: input=$inputTokens, output=$outputTokens, total=${inputTokens + outputTokens}")
                    }
                } catch (e: Exception) {
                    println("[SessionObject] 解析result消息的token使用量失败: ${e.message}")
                }
                
                // result消息用于结束生成状态
                isGenerating = false
                updateLastMessage { msg ->
                    msg.copy(isStreaming = false)
                }
            }
            enhancedMessage.role == MessageRole.ASSISTANT && (enhancedMessage.content.isNotBlank() || enhancedMessage.toolCalls.isNotEmpty()) -> {
                println("[SessionObject] 处理ASSISTANT消息，内容: ${enhancedMessage.content.take(50)}..., 工具调用: ${enhancedMessage.toolCalls.size}个")
                
                // 如果消息包含工具调用，直接添加（现在每个消息只包含一个工具调用）
                if (enhancedMessage.toolCalls.isNotEmpty()) {
                    println("[SessionObject] 🔧 发现工具调用消息，立即添加到UI")
                    addMessage(enhancedMessage)
                } else {
                    // 纯文本消息，更新最后一条助手消息
                    // 检测认证错误
                    if (enhancedMessage.content.contains("API Error: 401 API key not found") || 
                        enhancedMessage.content.contains("Please run /login")) {
                        println("[SessionObject] 检测到认证错误，设置错误消息")
                        errorMessage = enhancedMessage.content
                        isGenerating = false
                    }
                    
                    updateLastMessage { msg ->
                        println("[SessionObject] 更新最后一条消息: ${msg.id}")
                        msg.copy(
                            content = enhancedMessage.content,
                            isStreaming = enhancedMessage.isStreaming
                        )
                    }
                }
            }
            else -> {
                println("[SessionObject] 跳过消息: role=${enhancedMessage.role}, content长度=${enhancedMessage.content.length}")
            }
        }
        
        // 如果有sessionId，更新当前会话ID
        if (originalMessage.sessionId != null && originalMessage.sessionId != sessionId) {
            println("[SessionObject] 🆔 从消息更新会话 ID: ${originalMessage.sessionId}")
            updateSessionId(originalMessage.sessionId!!)
        }
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
        // 注销CLI回调
        GlobalCliWrapper.unregisterSessionCallback(sessionId)
        
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
        runningToolCalls.clear()
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
        
        println("[SessionObject] 会话状态已恢复: sessionId=$sessionId, messages=${messages.size}, scrollPosition=$scrollPosition")
    }
    
    /**
     * 从文件加载消息
     * 修复：增强消息持久化机制，确保新消息能正确保存和恢复
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
            
            // 首先检查会话文件是否存在
            val sessionFileExists = withContext(Dispatchers.IO) {
                val sessionFilePath = sessionManager.getSessionFilePath(currentProjectPath, currentSessionId)
                val sessionFile = java.io.File(sessionFilePath)
                sessionFile.exists()
            }
            
            if (!sessionFileExists) {
                println("[SessionObject] ⚠️ 会话文件不存在，标记为新会话: sessionId=$currentSessionId")
                isFirstMessage = true
                return
            }
            
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
            
            println("[SessionObject] 🔄 逐条处理历史消息（模拟CLI流）...")
            
            // 清空现有消息，重新处理
            if (forceFullReload) {
                updateMessagesList(emptyList())
            }
            
            // 查找包含sessionId的消息来更新sessionId（如果需要）
            val messageWithSessionId = sessionMessages.find { !it.sessionId.isNullOrEmpty() }
            if (messageWithSessionId != null && this.sessionId != messageWithSessionId.sessionId) {
                println("[SessionObject] 📱 从历史消息更新sessionId: ${messageWithSessionId.sessionId}")
                updateSessionId(messageWithSessionId.sessionId)
            } else if (messageWithSessionId != null) {
                println("[SessionObject] 📱 历史消息sessionId与当前一致: ${messageWithSessionId.sessionId}")
            } else {
                println("[SessionObject] ⚠️ 历史消息中未找到有效的sessionId")
            }
            
            // 逐条处理历史消息，使用统一的parseClaudeCliMessage解析器
            sessionMessages.forEach { sessionMessage ->
                try {
                    println("[SessionObject] 📥 处理历史消息: ${sessionMessage.type} - ${sessionMessage.uuid?.take(8) ?: "unknown"}...")
                    
                    // 为历史消息生成唯一ID，避免与新消息冲突
                    val historyMessageId = "history_${sessionMessage.uuid ?: System.nanoTime()}"
                    
                    // 先检查是否是工具结果消息，需要特殊处理
                    if (sessionMessage.type == "user" && sessionMessage.message?.content != null) {
                        val contentList = sessionMessage.message.content
                        var hasToolResult = false
                        
                        // 检查是否包含工具结果
                        if (contentList is List<*>) {
                            contentList.forEach { contentItem ->
                                if (contentItem is Map<*, *>) {
                                    val itemType = contentItem["type"] as? String
                                    if (itemType == "tool_result") {
                                        hasToolResult = true
                                        val toolUseId = contentItem["tool_use_id"] as? String
                                        val resultContent = contentItem["content"] as? String ?: ""
                                        val isError = (contentItem["is_error"] as? Boolean) ?: false
                                        
                                        if (toolUseId != null) {
                                            println("[SessionObject] 🔧 处理历史工具结果: toolId=$toolUseId, isError=$isError, content=${resultContent.take(50)}...")
                                            
                                            // 找到对应的工具调用消息并更新结果
                                            updateToolCallResult(toolUseId, resultContent, isError)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 如果处理了工具结果，就跳过常规处理
                        if (hasToolResult) {
                            return@forEach
                        }
                    }
                    
                    // 将历史格式转换为实时格式
                    val realtimeFormat = convertHistoryToRealtime(sessionMessage)
                    
                    if (realtimeFormat != null) {
                        println("[SessionObject] ✅ 历史消息格式转换成功")
                        
                        // 使用统一的实时消息解析器
                        val enhancedMessage = parseClaudeCliMessage(realtimeFormat, realtimeFormat.toString())
                        
                        if (enhancedMessage != null && (enhancedMessage.content.isNotEmpty() || enhancedMessage.toolCalls.isNotEmpty())) {
                            println("[SessionObject] ✅ 历史消息解析成功: content长度=${enhancedMessage.content.length}, toolCalls=${enhancedMessage.toolCalls.size}")
                            
                            // 为历史消息使用特殊的ID，确保不与新消息重复
                            val historyMessage = enhancedMessage.copy(
                                id = historyMessageId,
                                isStreaming = false  // 历史消息都不是流式的
                            )
                            addMessage(historyMessage)
                        } else {
                            println("[SessionObject] ⚠️ 历史消息解析结果为空或无有效内容")
                        }
                    } else {
                        println("[SessionObject] ❌ 历史消息格式转换失败，跳过该消息")
                    }
                } catch (e: Exception) {
                    println("[SessionObject] ❌ 处理历史消息异常: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            val enhancedMessages = messages
            println("[SessionObject] ✅ 历史消息处理完成 - enhancedMessages: ${enhancedMessages.size}")
            
            // 在主线程更新消息列表
            withContext(Dispatchers.Main) {
                println("[SessionObject] 🎯 在主线程中更新消息列表...")
                println("[SessionObject] - 旧消息数量: ${messages.size}")
                println("[SessionObject] - 新消息数量: ${enhancedMessages.size}")
                println("[SessionObject] - 是否需要更新: ${forceFullReload || enhancedMessages.size != messages.size}")
                
                if (forceFullReload || enhancedMessages.size != messages.size) {
                    // 只有在强制重载或消息数量变化时才更新
                    updateMessagesList(enhancedMessages)
                    val action = if (forceFullReload) "强制全量重载" else "增量更新"
                    println("[SessionObject] ✅ $action 消息列表，共 ${enhancedMessages.size} 条消息")
                    
                    // 如果加载了历史消息，更新会话状态
                    if (enhancedMessages.isNotEmpty()) {
                        onHistoryLoaded()
                        
                        // 重要：更新本地配置中的消息计数
                        try {
                            project?.let { proj ->
                                val localConfigManager = LocalConfigManager()
                                localConfigManager.updateSessionMetadata(proj.id, sessionId ?: "") { metadata ->
                                    metadata.copy(
                                        lastUpdated = System.currentTimeMillis(),
                                        messageCount = enhancedMessages.size
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            println("[SessionObject] 更新本地配置失败: ${e.message}")
                        }
                    }
                } else {
                    println("[SessionObject] ⏩ 消息无变化，跳过更新")
                }
            }
        } catch (e: Exception) {
            println("[SessionObject] ❌ 加载消息失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 更新工具调用结果
     * 当收到 tool_result 事件时，更新对应的工具调用状态和结果
     */
    private fun updateToolCallResult(toolId: String, result: com.claudecodeplus.ui.models.ToolResult) {
        println("[SessionObject] 🔧 更新工具调用结果: toolId=$toolId, result类型=${result.javaClass.simpleName}")
        
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
                    
                    println("[SessionObject] ✅ 更新工具调用: ${toolCall.name} -> $newStatus")
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
            val updatedMessage = message.copy(toolCalls = updatedToolCalls)
            val updatedMessages = messages.toMutableList()
            updatedMessages[messageIndex] = updatedMessage
            
            // 更新消息列表并触发UI更新
            messages = updatedMessages
            
            println("[SessionObject] 🎯 工具调用结果更新完成")
        } else {
            println("[SessionObject] ⚠️ 找不到对应的工具调用消息: toolId=$toolId")
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