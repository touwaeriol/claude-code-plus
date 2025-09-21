package com.claudecodeplus.ui.components.chat

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.core.ApplicationInitializer
import com.claudecodeplus.ui.components.AssistantMessageDisplay
import com.claudecodeplus.ui.jewel.components.UnifiedChatInput
import com.claudecodeplus.ui.jewel.components.UserMessageDisplay
import com.claudecodeplus.ui.models.MessageRole
import com.claudecodeplus.ui.viewmodels.ChatUiEffect
import com.claudecodeplus.ui.viewmodels.ChatUiEvent
import com.claudecodeplus.ui.viewmodels.ChatUiState
import com.claudecodeplus.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.flow.Flow

/**
 * 现代化聊天界面
 * 基于 ViewModel 模式重构的聊天组件
 */
@Composable
fun ModernChatView(
    sessionId: String? = null,
    projectPath: String = System.getProperty("user.dir"),
    modifier: Modifier = Modifier
) {
    // 确保应用程序已初始化
    LaunchedEffect(Unit) {
        if (!ApplicationInitializer.isInitialized()) {
            ApplicationInitializer.initialize()
        }
    }
    
    // 创建ViewModel
    val viewModel = remember { ChatViewModel() }
    
    // 收集UI状态
    val uiState by viewModel.uiState.collectAsState()
    
    // 处理副作用
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            handleEffect(effect)
        }
    }
    
    // 初始化会话
    LaunchedEffect(sessionId, projectPath) {
        viewModel.handleEvent(
            ChatUiEvent.InitializeSession(sessionId, projectPath)
        )
    }
    
    // 渲染聊天界面
    ChatScreenContent(
        uiState = uiState,
        onEvent = viewModel::handleEvent,
        modifier = modifier
    )
    
    // 错误对话框
    if (uiState.hasError) {
        ErrorDialog(
            error = uiState.errorMessage ?: "未知错误",
            onDismiss = { viewModel.handleEvent(ChatUiEvent.ClearError) }
        )
    }
    
    // 清理ViewModel
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.onCleared()
        }
    }
}

/**
 * 聊天界面内容
 */
@Composable
private fun ChatScreenContent(
    uiState: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 消息列表
        MessageList(
            messages = uiState.messages,
            isLoading = uiState.isLoadingHistory,
            modifier = Modifier.weight(1f)
        )
        
        // 输入区域
        UnifiedChatInput(
            contexts = uiState.contexts,
            onContextAdd = { context ->
                onEvent(ChatUiEvent.AddContext(context))
            },
            onContextRemove = { context ->
                onEvent(ChatUiEvent.RemoveContext(context))
            },
            onSend = { text ->
                onEvent(ChatUiEvent.SendMessage(text))
            },
            onInterruptAndSend = { text ->
                onEvent(ChatUiEvent.InterruptAndSend(text))
            },
            onStop = {
                onEvent(ChatUiEvent.StopGeneration)
            },
            isGenerating = uiState.isGenerating,
            enabled = true,
            selectedModel = uiState.selectedModel,
            onModelChange = { model ->
                onEvent(ChatUiEvent.ChangeModel(model))
            },
            selectedPermissionMode = uiState.selectedPermissionMode,
            onPermissionModeChange = { mode ->
                onEvent(ChatUiEvent.ChangePermissionMode(mode))
            },
            skipPermissions = uiState.skipPermissions,
            onSkipPermissionsChange = { skip ->
                onEvent(ChatUiEvent.ToggleSkipPermissions(skip))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 消息列表组件
 */
@Composable
private fun MessageList(
    messages: List<com.claudecodeplus.ui.models.EnhancedMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 加载指示器
        if (isLoading) {
            item {
                LoadingIndicator(
                    text = "加载历史消息...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 消息列表
        items(messages, key = { it.id }) { message ->
            when (message.role) {
                MessageRole.USER -> {
                    UserMessageDisplay(
                        message = message,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                MessageRole.ASSISTANT -> {
                    AssistantMessageDisplay(
                        message = message,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                MessageRole.SYSTEM -> {
                    // 系统消息暂时不显示
                }
                MessageRole.ERROR -> {
                    // 错误消息暂时不显示
                }
            }
        }
    }
}

/**
 * 加载指示器
 */
@Composable
private fun LoadingIndicator(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        // 这里可以添加加载动画
        org.jetbrains.jewel.ui.component.Text(text = text)
    }
}

/**
 * 错误对话框
 */
@Composable
private fun ErrorDialog(
    error: String,
    onDismiss: () -> Unit
) {
    // 这里可以实现一个简单的错误提示
    // 暂时使用控制台输出
    LaunchedEffect(error) {
    logD("错误: $error")
        // 自动关闭错误
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
}

/**
 * 处理副作用事件
 */
private suspend fun handleEffect(effect: ChatUiEffect) {
    when (effect) {
        is ChatUiEffect.ScrollToBottom -> {
            // 滚动到底部的逻辑已在MessageList中处理
        }
        is ChatUiEffect.FocusInput -> {
            // 输入框焦点的逻辑在UnifiedChatInput中处理
        }
        is ChatUiEffect.ShowSnackbar -> {
    logD("提示: ${effect.message}")
        }
        is ChatUiEffect.NavigateToSession -> {
    logD("导航到会话: ${effect.sessionId}")
        }
    }
}
