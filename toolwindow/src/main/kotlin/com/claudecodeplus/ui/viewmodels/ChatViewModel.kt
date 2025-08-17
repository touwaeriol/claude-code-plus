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
import com.claudecodeplus.ui.models.EnhancedMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
    
    // 副作用事件
    private val _effects = MutableSharedFlow<ChatUiEffect>()
    val effects = _effects.asSharedFlow()
    
    // ViewModel作用域
    private val viewModelScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )
    
    // 会话事件监听作业
    private var sessionEventJob: Job? = null
    
    /**
     * 处理UI事件
     */
    fun handleEvent(event: ChatUiEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is ChatUiEvent.SendMessage -> sendMessage(event.text)
                    is ChatUiEvent.InterruptAndSend -> interruptAndSend(event.text)
                    is ChatUiEvent.StopGeneration -> stopGeneration()
                    is ChatUiEvent.LoadHistory -> loadHistory()
                    is ChatUiEvent.UpdateInputText -> updateInputText(event.text)
                    is ChatUiEvent.ClearInput -> clearInput()
                    is ChatUiEvent.ToggleContextSelector -> toggleContextSelector(event.show)
                    is ChatUiEvent.AddContext -> addContext(event.context)
                    is ChatUiEvent.RemoveContext -> removeContext(event.context)
                    is ChatUiEvent.ChangeModel -> changeModel(event.model)
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
                showError("操作失败: ${e.message}")
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
    
    /**
     * 发送消息
     */
    private suspend fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return
        
        logI("发送消息: ${text.take(50)}...")
        
        val currentState = _uiState.value
        
        try {
            updateState { 
                it.copy(
                    isGenerating = true,
                    errorMessage = null
                )
            }
            
            val sessionId = currentState.sessionId
            if (sessionId == null) {
                // 创建新会话
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
                    
                    // 启动新会话的事件监听
                    startSessionEventListening(newSessionId)
                    
                    // 发送消息到新会话
                    sendMessageToSession(newSessionId, text)
                } else {
                    throw Exception("创建会话失败: ${newSessionResult}")
                }
            } else {
                // 发送到现有会话
                sendMessageToSession(sessionId, text)
            }
            
            // 清空输入框
            clearInput()
            
            // 滚动到底部
            _effects.emit(ChatUiEffect.ScrollToBottom)
            
        } catch (e: Exception) {
            logE("发送消息失败", e)
            updateState { 
                it.copy(
                    isGenerating = false,
                    errorMessage = "发送消息失败: ${e.message}"
                )
            }
        }
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
            throw Exception("发送消息失败: ${result}")
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
        sendMessage(text)
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
                throw Exception("加载历史消息失败: ${result}")
            }
        } catch (e: Exception) {
            logE("加载历史消息失败", e)
            updateState { 
                it.copy(
                    isLoadingHistory = false,
                    errorMessage = "加载历史消息失败: ${e.message}"
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
                        showError("会话连接异常: ${e.message}")
                    }
                    .collect { event ->
                        handleSessionEvent(event)
                    }
            } catch (e: Exception) {
                logE("启动会话事件监听失败", e)
            }
        }
        
        logD("已启动会话事件监听: sessionId=$sessionId")
    }
    
    /**
     * 处理会话事件
     */
    private suspend fun handleSessionEvent(event: SessionEvent) {
        logD("收到会话事件: ${event::class.simpleName}")
        
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
        logD("添加上下文: ${context}")
    }
    
    /**
     * 移除上下文
     */
    private fun removeContext(context: com.claudecodeplus.ui.models.ContextReference) {
        updateState { state ->
            state.copy(contexts = state.contexts - context)
        }
        logD("移除上下文: ${context}")
    }
    
    /**
     * 切换模型
     */
    private fun changeModel(model: com.claudecodeplus.ui.models.AiModel) {
        updateState { 
            it.copy(selectedModel = model)
        }
        logD("切换模型: ${model.displayName}")
    }
    
    /**
     * 切换权限模式
     */
    private fun changePermissionMode(mode: com.claudecodeplus.ui.models.PermissionMode) {
        updateState { 
            it.copy(selectedPermissionMode = mode)
        }
        logD("切换权限模式: ${mode.name}")
    }
    
    /**
     * 切换跳过权限设置
     */
    private fun toggleSkipPermissions(skip: Boolean) {
        updateState { 
            it.copy(skipPermissions = skip)
        }
        logD("跳过权限设置: $skip")
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