package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.*
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.jewel.components.QueueIndicator
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.SessionManager
import java.time.Instant
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.util.UUID
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import com.claudecodeplus.ui.services.SessionPersistenceService
import com.claudecodeplus.sdk.ClaudeEventService
import com.claudecodeplus.sdk.ClaudeEvent
import com.claudecodeplus.sdk.SessionHistoryLoader
import com.claudecodeplus.ui.services.MessageConverter.toEnhancedMessage
import kotlinx.coroutines.Dispatchers

/**
 * 注意：已移除简化的消息解析器
 * 现在通过 SessionObject.processCliOutput 和 MessageConverter 正确处理消息
 * ChatViewNew 只负责UI展示，不再处理消息解析
 */

/**
 * 新版聊天视图组件 - 完全基于事件驱动架构
 * 按照 Claudia 项目的实现方式，使用进程监听替代文件监听
 */
@Composable
fun ChatViewNew(
    unifiedSessionService: UnifiedSessionService,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = ClaudeSessionManager(),
    tabId: String,
    initialMessages: List<EnhancedMessage>? = null,
    sessionId: String? = null,
    tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null,
    currentTabId: String? = null,
    currentProject: com.claudecodeplus.ui.models.Project? = null,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 移除已删除的消息转换器
    
    // 使用SessionObject内部的ClaudeCliWrapper实例，支持后台处理
    // val cliWrapper = remember { com.claudecodeplus.sdk.ClaudeCliWrapper() } // 旧方法，已移至SessionObject
    
    println("=== ChatViewNew 使用事件驱动架构 ===")
    println("tabId: $tabId")
    println("sessionId: $sessionId") 
    println("workingDirectory: $workingDirectory")
    
    // 获取或创建该标签的会话对象（保持现有架构，但使用增强的SessionObject）
    val sessionObject = remember(tabId) {
        // 只依赖 tabId，确保同一标签总是返回同一实例
        val project = currentProject ?: com.claudecodeplus.ui.models.Project(
            id = "temp",
            name = "临时项目", 
            path = workingDirectory
        )
        
        project.getOrCreateSession(
            tabId = tabId, 
            initialSessionId = sessionId, 
            initialMessages = initialMessages ?: emptyList()
        ).also { session ->
            // 确保初始参数被正确设置（防止项目切换后丢失）
            if (sessionId != null && session.sessionId != sessionId) {
                session.updateSessionId(sessionId)
            }
            // 智能状态恢复：只在会话真正为空时设置初始消息
            if (initialMessages != null && initialMessages.isNotEmpty()) {
                if (session.messages.isEmpty() || session.messages.size < initialMessages.size) {
                    // 如果当前会话消息少于初始消息，说明可能是状态丢失，需要恢复
                    session.messages = initialMessages
                    println("[ChatViewNew] 恢复会话消息: ${initialMessages.size} 条")
                }
            }
            println("[ChatViewNew] 会话对象已创建/获取: tabId=$tabId, sessionId=${session.sessionId}, messages=${session.messages.size}")
        }
    }
    
    // 监听标签切换，确保正确恢复会话状态
    LaunchedEffect(tabId, currentProject) {
        println("[ChatViewNew] 标签/项目变化检测: tabId=$tabId, project=${currentProject?.name}")
        
        if (currentProject != null) {
            // 确保会话状态正确恢复
            val currentSession = currentProject.getSession(tabId)
            if (currentSession != null) {
                println("[ChatViewNew] 找到现有会话，验证状态完整性")
                
                // 验证并恢复状态（如果需要）
                if (sessionId != null && currentSession.sessionId != sessionId) {
                    currentSession.updateSessionId(sessionId)
                    println("[ChatViewNew] 恢复 sessionId: $sessionId")
                }
                
                if (initialMessages != null && initialMessages.isNotEmpty() && 
                    currentSession.messages.size < initialMessages.size) {
                    currentSession.messages = initialMessages
                    println("[ChatViewNew] 恢复消息历史: ${initialMessages.size} 条")
                }
            } else {
                // 新项目中没有这个标签的会话，创建新会话
                println("[ChatViewNew] 在新项目中创建会话")
                currentProject.getOrCreateSession(
                    tabId = tabId,
                    initialSessionId = sessionId,
                    initialMessages = initialMessages ?: emptyList()
                )
            }
        }
    }
    
    
    // 从 sessionObject 获取所有状态
    val messages by derivedStateOf { sessionObject.messages }
    val contexts by derivedStateOf { sessionObject.contexts }
    val isGenerating by derivedStateOf { sessionObject.isGenerating }
    val selectedModel by derivedStateOf { sessionObject.selectedModel }
    val selectedPermissionMode by derivedStateOf { sessionObject.selectedPermissionMode }
    val skipPermissions by derivedStateOf { sessionObject.skipPermissions }
    val inputResetTrigger by derivedStateOf { sessionObject.inputResetTrigger }
    
    // 统一使用SessionObject的消息发送方法，避免重复处理
    fun sendMessage(markdownText: String) {
        println("[ChatViewNew] 开始发送消息: '$markdownText'")
        
        // 检查生成状态
        if (sessionObject.isGenerating) {
            println("[ChatViewNew] 会话正在生成中，不能发送新消息")
            return
        }
        
        // 添加用户消息到UI
        val userMessage = EnhancedMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = markdownText,
            timestamp = System.currentTimeMillis(),
            model = sessionObject.selectedModel,
            contexts = sessionObject.contexts
        )
        sessionObject.addMessage(userMessage)
        println("[ChatViewNew] 用户消息已添加到UI")
        
        // 启动协程调用SessionObject的统一发送方法
        val job = coroutineScope.launch {
            try {
                // 直接使用SessionObject的sendMessage方法，避免重复逻辑
                println("[ChatViewNew] 调用SessionObject.sendMessage")
                val result = sessionObject.sendMessage(markdownText, workingDirectory)
                println("[ChatViewNew] SessionObject.sendMessage完成: success=${result.success}")
                
                // SessionObject已经处理了所有错误情况和状态更新
            } catch (e: Exception) {
                println("[ChatViewNew] CLI处理异常: ${e.message}")
                e.printStackTrace()
                // SessionObject的sendMessage已经处理了异常和状态清理
            }
        }
        
        // 不使用sessionObject.startGenerating，因为它会设置isGenerating=true导致重复调用问题
    }
    
    // 旧代码已删除，现在使用SessionObject的sendMessage方法
    
    // UI与原来完全相同，只是底层使用事件驱动
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 聊天内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 消息列表
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground)
            ) {
                val scrollState = rememberScrollState()
                
                // 恢复滚动位置
                LaunchedEffect(sessionObject) {
                    val savedPosition = sessionObject.scrollPosition
                    if (savedPosition > 0f) {
                        println("[ChatViewNew] 恢复滚动位置: $savedPosition")
                        scrollState.scrollTo(savedPosition.toInt())
                    } else {
                        // 新会话或没有保存位置，滚动到底部
                        if (messages.isNotEmpty()) {
                            println("[ChatViewNew] 滚动到底部")
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    }
                }
                
                // 监听消息变化，新消息时滚动到底部
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        kotlinx.coroutines.delay(100) // 等待UI更新
                        scrollState.scrollTo(scrollState.maxValue)
                        println("[ChatViewNew] 新消息滚动到底部")
                    }
                }
                
                // 监听滚动位置变化，保存到会话对象
                LaunchedEffect(scrollState.value) {
                    sessionObject.scrollPosition = scrollState.value.toFloat()
                }
                
                VerticallyScrollableContainer(
                    scrollState = scrollState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "发送消息开始对话",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = JewelTheme.globalColors.text.disabled
                                    )
                                )
                            }
                        } else {
                            messages.forEach { message ->
                                when (message.role) {
                                    MessageRole.USER -> {
                                        UnifiedInputArea(
                                            mode = InputAreaMode.DISPLAY,
                                            message = message,
                                            onContextClick = { uri ->
                                                if (uri.startsWith("file://") && projectService != null) {
                                                    val path = uri.removePrefix("file://")
                                                    projectService.openFile(path)
                                                }
                                            },
                                            sessionObject = sessionObject,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    MessageRole.ASSISTANT, MessageRole.SYSTEM, MessageRole.ERROR -> {
                                        AssistantMessageDisplay(
                                            message = message,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
        
        // 输入区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            UnifiedChatInput(
                contexts = contexts,
                onContextAdd = { context -> sessionObject.addContext(context) },
                onContextRemove = { context -> sessionObject.removeContext(context) },
                selectedModel = selectedModel,
                onModelChange = { model -> 
                    sessionObject.selectedModel = model
                    // 保存配置等逻辑...
                },
                selectedPermissionMode = selectedPermissionMode,
                onPermissionModeChange = { mode -> 
                    sessionObject.selectedPermissionMode = mode
                },
                skipPermissions = skipPermissions,
                onSkipPermissionsChange = { skip -> 
                    sessionObject.skipPermissions = skip
                },
                fileIndexService = fileIndexService,
                projectService = projectService,
                resetTrigger = inputResetTrigger,
                sessionObject = sessionObject,
                onSend = { markdownText ->
                    sendMessage(markdownText)
                },
                onInterruptAndSend = { markdownText ->
                    // 中断当前任务并发送新消息
                    sessionObject.interruptGeneration()
                    sendMessage(markdownText)
                },
                enabled = true,
                isGenerating = isGenerating  // 正确传递生成状态
            )
        }
    }
}

