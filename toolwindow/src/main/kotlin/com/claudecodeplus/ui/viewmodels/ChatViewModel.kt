package com.claudecodeplus.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.claudecodeplus.core.di.inject
import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.core.models.SessionEvent
import com.claudecodeplus.core.services.ProjectService
import com.claudecodeplus.core.services.SessionService
import com.claudecodeplus.core.services.ToolResultProcessor
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.services.formatStringResource
import com.claudecodeplus.ui.services.StringResources
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class TaskType { SWITCH_MODEL, QUERY }

enum class TaskStatus { PENDING, RUNNING, SUCCESS, FAILED }

data class PendingTask(
    val id: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val text: String,
    val alias: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val realModelId: String? = null,
    val error: String? = null
)

/**
 * 聊天界面ViewModel
 * 负责管理聊天界面的状态和业务逻辑
 */
class ChatViewModel {
    
    // 依赖注入的服务
    private val sessionService: SessionService by inject()
    private val projectService: ProjectService by inject()
    private val toolResultProcessor = ToolResultProcessor()
    
    // UI状态
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()
    private val _taskState = MutableStateFlow<List<PendingTask>>(emptyList())
    val taskState = _taskState.asStateFlow()
    private val taskChannel = Channel<PendingTask>(Channel.UNLIMITED)
    
    // 副作用事件
    private val _effects = MutableSharedFlow<ChatUiEffect>()
    val effects = _effects.asSharedFlow()
    
    // ViewModel作用域
    private val viewModelScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )

    // 会话事件监听作业
    private var sessionEventJob: Job? = null

    init {
        viewModelScope.launch {
            processTasks()
        }
    }

    private fun enqueueTask(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) {
            return
        }

        val pendingTask = if (text.startsWith("/model")) {
            val alias = text.removePrefix("/model").trim()
            if (alias.isBlank()) {
                viewModelScope.launch {
                    showError("模型别名不能为空")
                }
                return
            }
            PendingTask(type = TaskType.SWITCH_MODEL, text = rawText, alias = alias)
        } else {
            PendingTask(type = TaskType.QUERY, text = rawText)
        }

        _taskState.update { it + pendingTask }
        clearInput()
        viewModelScope.launch {
            taskChannel.send(pendingTask)
        }
    }
    
    /**
     * 处理UI事件
     */
    fun handleEvent(event: ChatUiEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is ChatUiEvent.SendMessage -> enqueueTask(event.text)
                    is ChatUiEvent.InterruptAndSend -> interruptAndSend(event.text)
                    is ChatUiEvent.StopGeneration -> stopGeneration()
                    is ChatUiEvent.LoadHistory -> loadHistory()
                    is ChatUiEvent.UpdateInputText -> updateInputText(event.text)
                    is ChatUiEvent.ClearInput -> clearInput()
                    is ChatUiEvent.ToggleContextSelector -> toggleContextSelector(event.show)
                    is ChatUiEvent.AddContext -> addContext(event.context)
                    is ChatUiEvent.RemoveContext -> removeContext(event.context)
                    is ChatUiEvent.ChangePermissionMode -> changePermissionMode(event.mode)
                    is ChatUiEvent.ToggleSkipPermissions -> toggleSkipPermissions(event.skip)
                    is ChatUiEvent.InitializeSession -> initializeSession(event.sessionId, event.projectPath)
                    is ChatUiEvent.SwitchSession -> switchSession(event.sessionId)
                    is ChatUiEvent.ClearError -> clearError()
                    is ChatUiEvent.ShowError -> showError(event.message)
                    is ChatUiEvent.UpdateScrollPosition -> updateScrollPosition(event.position)
                    is ChatUiEvent.SetInputFocus -> setInputFocus(event.focused)
                }
            } catch (e: Exception) {
                logE("处理UI事件失败: ${event::class.simpleName}", e)
                showError(formatStringResource(StringResources.OPERATION_FAILED, e.message ?: ""))
            }
        }
    }
    
    /**
     * 初始化会话
     */
    private suspend fun initializeSession(sessionId: String?, projectPath: String) {
        logI("初始化会话: sessionId=$sessionId, projectPath=$projectPath")
        
        updateState { 
            it.copy(
                sessionId = sessionId,
                projectPath = projectPath,
                isNewSession = sessionId == null,
                isLoadingHistory = sessionId != null
            )
        }
        
        // 停止之前的会话监听
        sessionEventJob?.cancel()
        
        if (sessionId != null) {
            // 启动会话事件监听
            startSessionEventListening(sessionId)
            
            // 加载历史消息
            loadHistory()
        } else {
            // 新会话，清空消息列表
            updateState { it.copy(messages = emptyList()) }
        }
        
        // 请求输入框焦点
        _effects.emit(ChatUiEffect.FocusInput)
    }
    
    private suspend fun processTasks() {
        for (task in taskChannel) {
            markTaskRunning(task.id)
            when (task.type) {
                TaskType.SWITCH_MODEL -> executeSwitchModelTask(task)
                TaskType.QUERY -> executeQueryTask(task)
            }
        }
    }

    private suspend fun executeSwitchModelTask(task: PendingTask) {
        val alias = task.alias
        val sessionId = _uiState.value.sessionId

        if (alias.isNullOrBlank()) {
            markTaskFailure(task.id, "模型别名不能为空")
            return
        }

        if (sessionId.isNullOrBlank()) {
            markTaskFailure(task.id, "尚未建立会话")
            showError("请先创建会话后再切换模型")
            return
        }

        val result = sessionService.switchModel(sessionId, alias)

        result.onSuccess { realId ->
            markTaskSuccess(task.id, realId)
        }.onFailure { appError ->
            val message = appError.message ?: "模型切换失败"
            markTaskFailure(task.id, message)
            viewModelScope.launch { showError(message) }
        }
    }

    /**
     * 发送消息
     */
    private suspend fun executeQueryTask(task: PendingTask) {
        val text = task.text
        if (text.isBlank()) {
            markTaskFailure(task.id, "消息为空")
            return
        }

        logI("执行队列消息: ${text.take(50)}...")

        val currentState = _uiState.value

        try {
            updateState { it.copy(errorMessage = null) }

            val sessionId = currentState.sessionId ?: run {
                val newSessionResult = sessionService.createSession(
                    projectPath = currentState.projectPath,
                    model = currentState.selectedModel,
                    permissionMode = currentState.selectedPermissionMode
                )

                if (newSessionResult.isSuccess) {
                    val newSessionId = newSessionResult.getOrNull()!!
                    logI("新会话创建成功: $newSessionId")
                    updateState {
                        it.copy(
                            sessionId = newSessionId,
                            isNewSession = false
                        )
                    }
                    startSessionEventListening(newSessionId)
                    newSessionId
                } else {
                    throw Exception(formatStringResource(StringResources.SESSION_CREATION_FAILED, newSessionResult))
                }
            }

            sendMessageToSession(sessionId, text)

            _effects.emit(ChatUiEffect.ScrollToBottom)
            markTaskSuccess(task.id)
        } catch (e: Exception) {
            logE("发送消息失败", e)
            markTaskFailure(task.id, e.message ?: formatStringResource(StringResources.SEND_MESSAGE_FAILED, ""))
            updateState {
                it.copy(
                    errorMessage = formatStringResource(StringResources.SEND_MESSAGE_FAILED, e.message ?: "")
                )
            }
        }
    }

    private fun markTaskRunning(taskId: String) {
        updateTask(taskId) { it.copy(status = TaskStatus.RUNNING) }
        refreshGeneratingState()
    }

    private fun markTaskSuccess(taskId: String, realModelId: String? = null) {
        updateTask(taskId) { it.copy(status = TaskStatus.SUCCESS, realModelId = realModelId) }
        removeTask(taskId)
        refreshGeneratingState()
    }

    private fun markTaskFailure(taskId: String, error: String) {
        updateTask(taskId) { it.copy(status = TaskStatus.FAILED, error = error) }
        removeTask(taskId)
        refreshGeneratingState()
    }

    private fun updateTask(taskId: String, transform: (PendingTask) -> PendingTask) {
        _taskState.update { list ->
            list.map { task -> if (task.id == taskId) transform(task) else task }
        }
    }

    private fun removeTask(taskId: String) {
        _taskState.update { list -> list.filterNot { it.id == taskId } }
    }

    private fun refreshGeneratingState() {
        val running = _taskState.value.any { it.status == TaskStatus.RUNNING }
        updateState { it.copy(isGenerating = running) }
    }
    
    /**
     * 发送消息到指定会话
     */
    private suspend fun sendMessageToSession(sessionId: String, text: String) {
        val result = sessionService.sendMessage(
            sessionId = sessionId,
            message = text,
            contexts = _uiState.value.contexts,
            workingDirectory = _uiState.value.projectPath
        )
        
        if (result.isFailure) {
            throw Exception(formatStringResource(StringResources.SEND_MESSAGE_FAILED, result))
        }
    }
    
    /**
     * 中断并发送新消息
     */
    private suspend fun interruptAndSend(text: String) {
        logI("中断当前生成并发送新消息")
        
        // 先中断当前生成
        stopGeneration()
        
        // 延迟一点时间确保中断完成
        delay(100)
        
        // 发送新消息
        enqueueTask(text)
    }
    
    /**
     * 停止生成
     */
    private suspend fun stopGeneration() {
        val sessionId = _uiState.value.sessionId
        if (sessionId != null) {
            logI("停止会话生成: $sessionId")
            sessionService.interruptSession(sessionId)
        }
        
        updateState { 
            it.copy(isGenerating = false)
        }
    }
    
    /**
     * 加载历史消息
     */
    private suspend fun loadHistory() {
        val currentState = _uiState.value
        val sessionId = currentState.sessionId ?: return
        
        logI("加载历史消息: sessionId=$sessionId")
        
        updateState { 
            it.copy(isLoadingHistory = true)
        }
        
        try {
            val result = sessionService.loadSessionHistory(
                sessionId = sessionId,
                projectPath = currentState.projectPath,
                forceReload = false
            )
            
            if (result.isSuccess) {
                val messages = result.getOrNull() ?: emptyList()
                logI("历史消息加载完成: ${messages.size}条消息")
                
                updateState { 
                    it.copy(
                        messages = messages,
                        isLoadingHistory = false
                    )
                }
                
                // 滚动到底部
                _effects.emit(ChatUiEffect.ScrollToBottom)
            } else {
                throw Exception(formatStringResource(StringResources.LOAD_HISTORY_FAILED, result))
            }
        } catch (e: Exception) {
            logE("加载历史消息失败", e)
            updateState { 
                it.copy(
                    isLoadingHistory = false,
                    errorMessage = formatStringResource(StringResources.LOAD_HISTORY_FAILED, e.message ?: "")
                )
            }
        }
    }
    
    /**
     * 启动会话事件监听
     */
    private suspend fun startSessionEventListening(sessionId: String) {
        sessionEventJob?.cancel()
        
        sessionEventJob = viewModelScope.launch {
            try {
                sessionService.observeSessionEvents(sessionId)
                    .catch { e ->
                        logE("会话事件监听异常", e)
                        showError(formatStringResource(StringResources.SESSION_CONNECTION_ERROR, e.message ?: ""))
                    }
                    .collect { event ->
                        handleSessionEvent(event)
                    }
            } catch (e: Exception) {
                logE("启动会话事件监听失败", e)
            }
        }
        
    //         logD("已启动会话事件监听: sessionId=$sessionId")
    }
    
    /**
     * 处理会话事件
     */
    private suspend fun handleSessionEvent(event: SessionEvent) {
    //         logD("收到会话事件: ${event::class.simpleName}")

        when (event) {
            is SessionEvent.MessageReceived -> {
                addMessage(event.message)
            }
            is SessionEvent.HistoryLoaded -> {
                updateState {
                    it.copy(
                        messages = event.messages,
                        isLoadingHistory = false
                    )
                }
                _effects.emit(ChatUiEffect.ScrollToBottom)
            }
            is SessionEvent.SessionIdUpdated -> {
                updateState {
                    it.copy(sessionId = event.newId)
                }
            }
            is SessionEvent.ModelUpdated -> {
                logI("模型更新事件: ${event.modelId}")
                updateState {
                    val resolvedModel = AiModel.fromIdentifier(event.modelId)
                    it.copy(
                        actualModelId = event.modelId,
                        selectedModel = resolvedModel ?: it.selectedModel
                    )
                }
            }
            is SessionEvent.ErrorOccurred -> {
                showError(event.error)
                updateState {
                    it.copy(isGenerating = false)
                }
            }
            SessionEvent.GenerationStarted -> {
                updateState {
                    it.copy(isGenerating = true)
                }
            }
            SessionEvent.GenerationStopped -> {
                updateState {
                    it.copy(isGenerating = false)
                }
            }
        }
    }
    
    /**
     * 添加消息到列表
     */
    private suspend fun addMessage(message: EnhancedMessage) {
        updateState { state ->
            val updatedMessages = state.messages + message
            state.copy(messages = updatedMessages)
        }
        
        // 滚动到底部
        _effects.emit(ChatUiEffect.ScrollToBottom)
    }
    
    /**
     * 更新输入文本
     */
    private fun updateInputText(text: TextFieldValue) {
        updateState { 
            it.copy(inputText = text)
        }
    }
    
    /**
     * 清空输入框
     */
    private fun clearInput() {
        updateState { 
            it.copy(inputText = TextFieldValue(""))
        }
    }
    
    /**
     * 切换上下文选择器显示状态
     */
    private fun toggleContextSelector(show: Boolean) {
        updateState { 
            it.copy(showContextSelector = show)
        }
    }
    
    /**
     * 添加上下文
     */
    private fun addContext(context: com.claudecodeplus.ui.models.ContextReference) {
        updateState { state ->
            if (context !in state.contexts) {
                state.copy(contexts = state.contexts + context)
            } else {
                state
            }
        }
    //         logD("添加上下文: ${context}")
    }
    
    /**
     * 移除上下文
     */
    private fun removeContext(context: com.claudecodeplus.ui.models.ContextReference) {
        updateState { state ->
            state.copy(contexts = state.contexts - context)
        }
    //         logD("移除上下文: ${context}")
    }
    
    /**
     * 切换权限模式
     */
    private fun changePermissionMode(mode: com.claudecodeplus.ui.models.PermissionMode) {
        updateState { 
            it.copy(selectedPermissionMode = mode)
        }
    //         logD("切换权限模式: ${mode.name}")
    }
    
    /**
     * 切换跳过权限设置
     */
    private fun toggleSkipPermissions(skip: Boolean) {
        updateState { 
            it.copy(skipPermissions = skip)
        }
    //         logD("跳过权限设置: $skip")
    }
    
    /**
     * 切换会话
     */
    private suspend fun switchSession(sessionId: String?) {
        logI("切换会话: $sessionId")
        initializeSession(sessionId, _uiState.value.projectPath)
    }
    
    /**
     * 显示错误
     */
    private suspend fun showError(message: String) {
        updateState { 
            it.copy(
                errorMessage = message,
                showError = true
            )
        }
        _effects.emit(ChatUiEffect.ShowSnackbar(message))
    }
    
    /**
     * 清除错误
     */
    private fun clearError() {
        updateState { 
            it.copy(
                errorMessage = null,
                showError = false
            )
        }
    }
    
    /**
     * 更新滚动位置
     */
    private fun updateScrollPosition(position: Float) {
        updateState { 
            it.copy(scrollPosition = position)
        }
    }
    
    /**
     * 设置输入框焦点状态
     */
    private fun setInputFocus(focused: Boolean) {
        updateState { 
            it.copy(isInputFocused = focused)
        }
    }
    
    /**
     * 安全更新状态的辅助方法
     */
    private fun updateState(update: (ChatUiState) -> ChatUiState) {
        _uiState.value = update(_uiState.value)
    }
    
    /**
     * 清理资源
     */
    fun onCleared() {
        logI("ChatViewModel 清理资源")
        sessionEventJob?.cancel()
        viewModelScope.cancel()
    }
}
