package com.claudecodeplus.ui.viewmodels

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import com.claudecodeplus.ui.models.*

/**
 * 聊天界面UI状态
 */
@Stable
data class ChatUiState(
    // 消息相关状态
    val messages: List<EnhancedMessage> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val isGenerating: Boolean = false,
    
    // 输入相关状态
    val inputText: TextFieldValue = TextFieldValue(""),
    val contexts: List<ContextReference> = emptyList(),
    val showContextSelector: Boolean = false,
    
    // 模型和权限设置
    val selectedModel: AiModel = AiModel.OPUS,
    val selectedPermissionMode: PermissionMode = PermissionMode.BYPASS,
    val skipPermissions: Boolean = true,
    
    // 会话相关状态
    val sessionId: String? = null,
    val isNewSession: Boolean = true,
    val projectPath: String = "",
    
    // 错误状态
    val errorMessage: String? = null,
    val showError: Boolean = false,
    
    // UI状态
    val scrollPosition: Float = 0f,
    val isInputFocused: Boolean = false
) {
    /**
     * 是否可以发送消息
     */
    val canSendMessage: Boolean
        get() = inputText.text.isNotBlank() && !isGenerating
    
    /**
     * 是否有有效的会话
     */
    val hasValidSession: Boolean
        get() = !sessionId.isNullOrEmpty()
    
    /**
     * 是否显示任何错误
     */
    val hasError: Boolean
        get() = !errorMessage.isNullOrEmpty()
}

/**
 * 聊天界面事件
 */
sealed class ChatUiEvent {
    // 消息相关事件
    data class SendMessage(val text: String) : ChatUiEvent()
    data class InterruptAndSend(val text: String) : ChatUiEvent()
    object StopGeneration : ChatUiEvent()
    object LoadHistory : ChatUiEvent()
    
    // 输入相关事件
    data class UpdateInputText(val text: TextFieldValue) : ChatUiEvent()
    object ClearInput : ChatUiEvent()
    data class ToggleContextSelector(val show: Boolean) : ChatUiEvent()
    
    // 上下文相关事件
    data class AddContext(val context: ContextReference) : ChatUiEvent()
    data class RemoveContext(val context: ContextReference) : ChatUiEvent()
    
    // 设置相关事件
    data class ChangeModel(val model: AiModel) : ChatUiEvent()
    data class ChangePermissionMode(val mode: PermissionMode) : ChatUiEvent()
    data class ToggleSkipPermissions(val skip: Boolean) : ChatUiEvent()
    
    // 会话相关事件
    data class InitializeSession(val sessionId: String?, val projectPath: String) : ChatUiEvent()
    data class SwitchSession(val sessionId: String?) : ChatUiEvent()
    
    // 错误处理事件
    object ClearError : ChatUiEvent()
    data class ShowError(val message: String) : ChatUiEvent()
    
    // UI事件
    data class UpdateScrollPosition(val position: Float) : ChatUiEvent()
    data class SetInputFocus(val focused: Boolean) : ChatUiEvent()
}

/**
 * 聊天界面副作用
 */
sealed class ChatUiEffect {
    object ScrollToBottom : ChatUiEffect()
    object FocusInput : ChatUiEffect()
    data class ShowSnackbar(val message: String) : ChatUiEffect()
    data class NavigateToSession(val sessionId: String) : ChatUiEffect()
}