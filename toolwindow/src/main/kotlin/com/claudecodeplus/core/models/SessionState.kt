package com.claudecodeplus.core.models

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import com.claudecodeplus.ui.models.*

/**
 * 核心会话状态数据类
 * 只包含状态数据，不包含业务逻辑
 */
@Stable
data class SessionState(
    val sessionId: String? = null,
    val isFirstMessage: Boolean = true,
    val messages: List<EnhancedMessage> = emptyList(),
    val contexts: List<ContextReference> = emptyList(),
    val isGenerating: Boolean = false,
    val selectedModel: AiModel = AiModel.OPUS,
    val selectedPermissionMode: PermissionMode = PermissionMode.BYPASS_PERMISSIONS,
    val skipPermissions: Boolean = true,
    val inputText: TextFieldValue = TextFieldValue(""),
    val scrollPosition: Float = 0f,
    val errorMessage: String? = null,
    val isLoadingHistory: Boolean = false
) {
    /**
     * 是否有有效的会话ID
     */
    val hasValidSessionId: Boolean
        get() = !sessionId.isNullOrEmpty()
    
    /**
     * 是否为新会话
     */
    val isNewSession: Boolean
        get() = sessionId == null
    
    /**
     * 是否有输入内容
     */
    val hasInput: Boolean
        get() = inputText.text.isNotBlank()
    
    /**
     * 是否可以发送消息
     */
    val canSendMessage: Boolean
        get() = hasInput && !isGenerating
}

/**
 * 会话元数据
 */
data class SessionMetadata(
    val sessionId: String,
    val projectId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val model: String? = null,
    val workingDirectory: String? = null
)

/**
 * 消息加载状态
 */
enum class MessageLoadingState {
    IDLE,
    LOADING_HISTORY,
    HISTORY_LOADED,
    LISTENING,
    ERROR
}

/**
 * 会话事件
 */
sealed class SessionEvent {
    data class MessageReceived(val message: EnhancedMessage) : SessionEvent()
    data class HistoryLoaded(val messages: List<EnhancedMessage>) : SessionEvent()
    data class SessionIdUpdated(val oldId: String?, val newId: String) : SessionEvent()
    data class ErrorOccurred(val error: String) : SessionEvent()
    object GenerationStarted : SessionEvent()
    object GenerationStopped : SessionEvent()
}

/**
 * 会话操作命令
 */
sealed class SessionCommand {
    data class SendMessage(val text: String) : SessionCommand()
    data class AddContext(val context: ContextReference) : SessionCommand()
    data class RemoveContext(val context: ContextReference) : SessionCommand()
    data class UpdateModel(val model: AiModel) : SessionCommand()
    data class UpdatePermissionMode(val mode: PermissionMode) : SessionCommand()
    data class UpdateSkipPermissions(val skip: Boolean) : SessionCommand()
    data class UpdateInputText(val text: TextFieldValue) : SessionCommand()
    object LoadHistory : SessionCommand()
    object ClearInput : SessionCommand()
    object InterruptGeneration : SessionCommand()
}