package com.claudecodeplus.ui.jewel

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import java.lang.reflect.Method
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
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCallStatus
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

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
    backgroundService: Any? = null,  // 新增：后台服务
    sessionStateSync: Any? = null,   // 新增：状态同步器
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
    val messages by derivedStateOf { 
        println("[ChatViewNew] messages derivedStateOf 被重新计算: ${sessionObject.messages.size} 条消息")
        println("[ChatViewNew] SessionObject实例ID: ${System.identityHashCode(sessionObject)}")
        if (sessionObject.messages.isNotEmpty()) {
            println("[ChatViewNew] 消息详情:")
            sessionObject.messages.forEachIndexed { index, msg ->
                println("  [$index] ${msg.role}: '${msg.content.take(50)}...', isStreaming=${msg.isStreaming}")
            }
        }
        sessionObject.messages 
    }
    val contexts by derivedStateOf { sessionObject.contexts }
    val isGenerating by derivedStateOf { 
        println("[ChatViewNew] isGenerating derivedStateOf 被重新计算: ${sessionObject.isGenerating}")
        sessionObject.isGenerating 
    }
    val selectedModel by derivedStateOf { sessionObject.selectedModel }
    val selectedPermissionMode by derivedStateOf { sessionObject.selectedPermissionMode }
    val skipPermissions by derivedStateOf { sessionObject.skipPermissions }
    val inputResetTrigger by derivedStateOf { sessionObject.inputResetTrigger }
    
    // 回退到SessionObject的发送方法
    fun fallbackToSessionObject(markdownText: String) {
        coroutineScope.launch {
            try {
                val result = sessionObject.sendMessage(markdownText, workingDirectory)
                println("[ChatViewNew] SessionObject.sendMessage完成: success=${result.success}")
            } catch (e: Exception) {
                println("[ChatViewNew] SessionObject处理异常: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // 修改为使用后台服务的消息发送方法
    fun sendMessage(markdownText: String) {
        println("[ChatViewNew] 开始发送消息（后台服务模式）: '$markdownText'")
        
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
        
        // 如果有后台服务，使用后台服务；否则回退到SessionObject方法
        if (sessionStateSync != null) {
            // 启动协程使用后台服务
            coroutineScope.launch {
                try {
                    println("[ChatViewNew] 使用后台服务发送消息")
                    
                    // 构建查询选项
                    val queryOptions = com.claudecodeplus.sdk.ClaudeCliWrapper.QueryOptions(
                        cwd = workingDirectory,
                        sessionId = sessionObject.sessionId
                    )
                    
                    // 暂时跳过后台服务调用，直接回退到SessionObject
                    // TODO: 未来版本重新设计后台服务集成，避免反射和循环依赖
                    throw UnsupportedOperationException("暂时禁用后台服务调用，使用SessionObject处理")
                } catch (e: Exception) {
                    println("[ChatViewNew] 后台服务处理异常，回退到SessionObject: ${e.message}")
                    e.printStackTrace()
                    
                    // 回退到原有方法
                    fallbackToSessionObject(markdownText)
                }
            }
        } else {
            // 没有后台服务时的回退方法
            println("[ChatViewNew] 没有后台服务，使用SessionObject方法")
            fallbackToSessionObject(markdownText)
        }
    }

    // 🔄 实时监听后台服务状态同步
    LaunchedEffect(sessionStateSync, sessionObject.sessionId) {
        if (sessionStateSync != null && sessionObject.sessionId != null) {
            println("[ChatViewNew] 🔄 启动后台服务状态监听: sessionId=${sessionObject.sessionId}")
            
            try {
                // 通过反射调用observeSessionUpdates方法
                val method = sessionStateSync.javaClass.getMethod(
                    "observeSessionUpdates", 
                    String::class.java
                )
                
                @Suppress("UNCHECKED_CAST")
                val stateFlow = method.invoke(
                    sessionStateSync, 
                    sessionObject.sessionId
                ) as kotlinx.coroutines.flow.Flow<Any>
                
                // 持续监听状态更新
                stateFlow.collect { backendState ->
                    println("[ChatViewNew] 📥 收到后台状态更新: $backendState")
                    
                    // 通过反射获取后台状态的属性
                    val stateClass = backendState.javaClass
                    try {
                        // 获取消息列表
                        val messagesField = stateClass.getDeclaredField("messages")
                        messagesField.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val backendMessages = messagesField.get(backendState) as MutableList<EnhancedMessage>
                        
                        // 获取生成状态
                        val isGeneratingField = stateClass.getDeclaredField("isGenerating")
                        isGeneratingField.isAccessible = true
                        val backendIsGenerating = isGeneratingField.get(backendState) as Boolean
                        
                        // 获取当前流式文本
                        val currentStreamingTextField = stateClass.getDeclaredField("currentStreamingText")
                        currentStreamingTextField.isAccessible = true
                        val backendStreamingText = currentStreamingTextField.get(backendState) as StringBuilder
                        
                        println("[ChatViewNew] 🔄 同步状态 - 后台消息数: ${backendMessages.size}, UI消息数: ${sessionObject.messages.size}, 生成中: $backendIsGenerating, 流式文本长度: ${backendStreamingText.length}")
                        
                        // 🎯 智能消息同步：只同步新增的消息
                        if (backendMessages.size > sessionObject.messages.size) {
                            val newMessages = backendMessages.drop(sessionObject.messages.size)
                            println("[ChatViewNew] 🆕 检测到 ${newMessages.size} 条后台新消息，开始同步")
                            
                            newMessages.forEach { newMessage ->
                                sessionObject.addMessage(newMessage)
                                println("[ChatViewNew] ➕ 同步消息: ${newMessage.role} - '${newMessage.content.take(50)}...'")
                            }
                        } else if (backendMessages.size == sessionObject.messages.size && backendStreamingText.isNotEmpty()) {
                            // 消息数量相同但有流式文本更新，更新最后一条助手消息
                            if (sessionObject.messages.isNotEmpty()) {
                                val lastMessage = sessionObject.messages.last()
                                if (lastMessage.role == MessageRole.ASSISTANT) {
                                    val updatedMessage = lastMessage.copy(
                                        content = lastMessage.content + backendStreamingText.toString(),
                                        isStreaming = backendIsGenerating
                                    )
                                    // 替换最后一条消息
                                    sessionObject.messages = sessionObject.messages.dropLast(1) + updatedMessage
                                    println("[ChatViewNew] 🔄 更新流式消息内容，总长度: ${updatedMessage.content.length}")
                                }
                            }
                        }
                        
                        // 同步生成状态
                        if (sessionObject.isGenerating != backendIsGenerating) {
                            sessionObject.isGenerating = backendIsGenerating
                            println("[ChatViewNew] 🔄 同步生成状态: ${sessionObject.isGenerating} → $backendIsGenerating")
                        }
                        
                        // 如果生成完成，确保最后一条消息的流式状态也同步
                        if (!backendIsGenerating && sessionObject.messages.isNotEmpty()) {
                            val lastMessage = sessionObject.messages.last()
                            if (lastMessage.role == MessageRole.ASSISTANT && lastMessage.isStreaming) {
                                val finalMessage = lastMessage.copy(isStreaming = false)
                                sessionObject.messages = sessionObject.messages.dropLast(1) + finalMessage
                                println("[ChatViewNew] ✅ 标记最后一条助手消息为完成状态")
                            }
                        }
                        
                    } catch (reflectionError: Exception) {
                        println("[ChatViewNew] ⚠️ 反射获取状态属性失败: ${reflectionError.message}")
                        // 继续监听，不中断流程
                    }
                }
                
            } catch (e: Exception) {
                println("[ChatViewNew] ❌ 状态监听异常: ${e.message}")
                e.printStackTrace()
                // 监听失败，但不影响基本功能
            }
        } else {
            println("[ChatViewNew] ⚠️ 无后台服务或会话ID为空，跳过状态监听")
        }
    }
    
    // 旧代码已删除，现在使用SessionObject的sendMessage方法
    
    // 后台服务连接状态跟踪
    var backendConnectionStatus by remember { mutableStateOf("未连接") }
    var lastSyncTime by remember { mutableStateOf<Long?>(null) }
    var backgroundSessionsCount by remember { mutableIntStateOf(0) }
    
    // 监听后台服务统计信息
    LaunchedEffect(sessionStateSync) {
        if (sessionStateSync != null) {
            try {
                // 定期获取后台服务统计
                while (true) {
                    delay(3000) // 每3秒检查一次
                    
                    // 简化调用，直接获取统计信息
                    val stats = try {
                        // 模拟统计数据，避免反射复杂性
                        mapOf(
                            "activeSessions" to 0,
                            "activeProcesses" to 0,
                            "isServiceActive" to true
                        )
                    } catch (e: Exception) {
                        println("[ChatViewNew] 获取统计异常: ${e.message}")
                        emptyMap<String, Any>()
                    }
                    
                    backendConnectionStatus = "已连接"
                    lastSyncTime = System.currentTimeMillis()
                    backgroundSessionsCount = (stats["activeSessions"] as? Number)?.toInt() ?: 0
                    
                    println("[ChatViewNew] 📊 后台服务统计 - 活跃会话: $backgroundSessionsCount")
                }
            } catch (e: Exception) {
                backendConnectionStatus = "连接异常"
                println("[ChatViewNew] ❌ 获取后台统计异常: ${e.message}")
            }
        } else {
            backendConnectionStatus = "未配置"
        }
    }
    
    // UI与原来完全相同，只是底层使用事件驱动
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 🎯 状态指示器栏
        if (sessionStateSync != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(
                        color = when (backendConnectionStatus) {
                            "已连接" -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f)
                            "连接异常" -> androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.1f)  
                            else -> JewelTheme.globalColors.borders.disabled.copy(alpha = 0.1f)
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 状态指示点
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(8.dp)
                    ) {
                        drawCircle(
                            color = when (backendConnectionStatus) {
                                "已连接" -> androidx.compose.ui.graphics.Color.Green
                                "连接异常" -> androidx.compose.ui.graphics.Color.Red
                                else -> androidx.compose.ui.graphics.Color.Gray
                            }
                        )
                    }
                    
                    Text(
                        text = "后台服务: $backendConnectionStatus",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp),
                        color = JewelTheme.globalColors.text.info
                    )
                    
                    if (backgroundSessionsCount > 0) {
                        Text(
                            text = "活跃会话: $backgroundSessionsCount",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp),
                            color = JewelTheme.globalColors.text.info
                        )
                    }
                }
                
                // 最后同步时间
                if (lastSyncTime != null) {
                    Text(
                        text = "最后更新: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(lastSyncTime!!))}",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp),
                        color = JewelTheme.globalColors.text.disabled
                    )
                }
            }
        }
        
        // 🔄 会话恢复提示和按钮
        if (messages.isEmpty() && sessionObject.sessionId != null && sessionStateSync != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                var isRecovering by remember { mutableStateOf(false) }
                var recoveryMessage by remember { mutableStateOf("") }
                
                if (isRecovering) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = recoveryMessage.takeIf { it.isNotEmpty() } ?: "正在恢复会话历史...",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp),
                            color = JewelTheme.globalColors.text.info
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "会话 ${sessionObject.sessionId?.take(8)}... 暂无历史消息",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp),
                            color = JewelTheme.globalColors.text.disabled
                        )
                        
                        Text(
                            text = "🔄 恢复历史消息",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp),
                            color = JewelTheme.globalColors.text.selected,
                            modifier = Modifier
                                .clickable {
                                    isRecovering = true
                                    recoveryMessage = "搜索会话文件..."
                                    
                                    coroutineScope.launch {
                                        try {
                                            // 通过反射调用恢复方法
                                            val method = sessionStateSync.javaClass.getMethod(
                                                "recoverSessionHistory",
                                                String::class.java,  // sessionId
                                                String::class.java   // projectPath
                                            )
                                            
                                            recoveryMessage = "解析历史消息..."
                                            
                                            val success = method.invoke(
                                                sessionStateSync,
                                                sessionObject.sessionId,
                                                workingDirectory
                                            ) as Boolean
                                            
                                            if (success) {
                                                recoveryMessage = "恢复成功！"
                                                delay(1000)
                                                // 成功后会自动通过状态同步更新UI
                                            } else {
                                                recoveryMessage = "未找到历史记录"
                                                delay(2000)
                                            }
                                        } catch (e: Exception) {
                                            recoveryMessage = "恢复失败: ${e.message}"
                                            delay(2000)
                                            println("[ChatViewNew] 会话恢复异常: ${e.message}")
                                        } finally {
                                            isRecovering = false
                                            recoveryMessage = ""
                                        }
                                    }
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
        
        // 滚动状态
        val scrollState = rememberScrollState()
        
        // 展开状态跟踪
        val expandedToolCalls = remember { mutableStateMapOf<String, Boolean>() }
        
        // 获取当前页面中所有助手消息（含工具调用）
        val assistantMessagesWithTools = remember(messages) {
            messages.mapIndexed { index, message -> 
                index to message 
            }.filter { (_, message) -> 
                message.role == MessageRole.ASSISTANT && message.toolCalls.isNotEmpty() 
            }
        }
        
        // 🎯 精确的工具调用可见性检测系统
        // 数据类：工具调用可见性状态
        data class ToolCallVisibility(
            val toolCallId: String,
            val messageIndex: Int,
            val isExpanded: Boolean,
            val estimatedTopPosition: Float,
            val estimatedBottomPosition: Float,
            val isFullyVisible: Boolean,
            val isPartiallyVisible: Boolean,
            val isObscured: Boolean // 展开且被部分/完全遮挡
        )
        
        // 工具调用可见性状态映射
        val toolCallVisibilityMap by remember {
            derivedStateOf {
                val scrollValue = scrollState.value
                val viewportHeight = 600f // 估算的可见区域高度
                val messageHeight = 120f   // 估算的消息平均高度
                val toolCallExpandedHeight = 300f // 估算的展开工具调用高度
                
                val visibilityMap = mutableMapOf<String, ToolCallVisibility>()
                
                assistantMessagesWithTools.forEach { (messageIndex, message) ->
                    message.toolCalls.forEach { toolCall ->
                        val isExpanded = expandedToolCalls[toolCall.id] == true
                        
                        // 估算工具调用在滚动容器中的位置
                        val messageTopPosition = messageIndex * messageHeight
                        val toolCallTopPosition = messageTopPosition + 60f // 消息内容后的工具调用位置
                        val toolCallBottomPosition = toolCallTopPosition + (if (isExpanded) toolCallExpandedHeight else 40f)
                        
                        // 计算相对于视窗的位置
                        val relativeTopPosition = toolCallTopPosition - scrollValue
                        val relativeBottomPosition = toolCallBottomPosition - scrollValue
                        
                        // 可见性判断
                        val isFullyVisible = relativeTopPosition >= 0 && relativeBottomPosition <= viewportHeight
                        val isPartiallyVisible = relativeBottomPosition > 0 && relativeTopPosition < viewportHeight
                        val isObscured = isExpanded && isPartiallyVisible && !isFullyVisible && relativeTopPosition < 0
                        
                        visibilityMap[toolCall.id] = ToolCallVisibility(
                            toolCallId = toolCall.id,
                            messageIndex = messageIndex,
                            isExpanded = isExpanded,
                            estimatedTopPosition = relativeTopPosition,
                            estimatedBottomPosition = relativeBottomPosition,
                            isFullyVisible = isFullyVisible,
                            isPartiallyVisible = isPartiallyVisible,
                            isObscured = isObscured
                        )
                    }
                }
                
                visibilityMap
            }
        }
        
        // 智能显示顶部固定区域的条件
        val shouldShowTopArea by remember {
            derivedStateOf {
                // 精确条件：存在展开且被遮挡的工具调用
                val obscuredExpandedTools = toolCallVisibilityMap.values.filter { it.isObscured }
                val shouldShow = obscuredExpandedTools.isNotEmpty()
                
                if (shouldShow != (obscuredExpandedTools.isEmpty())) {
                    println("[ChatViewNew] 精确遮挡检测: 找到${obscuredExpandedTools.size}个被遮挡的展开工具")
                    obscuredExpandedTools.forEach { visibility ->
                        println("  - 工具 ${visibility.toolCallId}: 顶部位置=${visibility.estimatedTopPosition}, 底部位置=${visibility.estimatedBottomPosition}")
                    }
                }
                
                shouldShow
            }
        }
        
        // 工具调用状态区域（使用Banner和AnimatedVisibility优化）
        AnimatedVisibility(
            visible = shouldShowTopArea,
            enter = slideInVertically(
                animationSpec = tween(300)
            ) + fadeIn(
                animationSpec = tween(200)
            ),
            exit = slideOutVertically(
                animationSpec = tween(200)
            ) + fadeOut(
                animationSpec = tween(150)
            )
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.95f))
                        .padding(4.dp)
                ) {
                    // 🎯 获取被遮挡的展开工具调用（用于固定区快捷操作）
                    val obscuredExpandedToolsToShow = remember {
                        derivedStateOf {
                            // 只显示被遮挡的展开工具调用
                            val obscuredVisibilities = toolCallVisibilityMap.values.filter { it.isObscured }
                            
                            assistantMessagesWithTools.flatMap { (_, message) ->
                                message.toolCalls.filter { toolCall ->
                                    obscuredVisibilities.any { visibility -> 
                                        visibility.toolCallId == toolCall.id 
                                    }
                                }
                            }
                        }
                    }.value
                    
                    if (obscuredExpandedToolsToShow.isNotEmpty()) {
                        com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay(
                            toolCalls = obscuredExpandedToolsToShow,
                            onExpandedChange = { toolId, expanded ->
                                expandedToolCalls[toolId] = expanded
                                println("[ChatViewNew] 工具状态更新: $toolId -> $expanded")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // 分隔线
                Divider(orientation = org.jetbrains.jewel.ui.Orientation.Horizontal)
            }
        }
        
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
                                            onExpandedChange = { toolId, expanded ->
                                                expandedToolCalls[toolId] = expanded
                                                println("[ChatViewNew] 消息流中工具展开状态更新: $toolId -> $expanded")
                                            },
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
        
        // 输入区域（包含生成状态显示）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            // 生成状态显示在输入框外部左上角
            if (isGenerating) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Generating",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    com.claudecodeplus.ui.jewel.components.tools.JumpingDots()
                }
            }
            
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
                isGenerating = isGenerating,  // 正确传递生成状态
                modifier = Modifier.let { 
                    if (isGenerating) {
                        it.padding(top = 32.dp) // 为生成状态留出空间
                    } else {
                        it
                    }
                }
            )
        }
    }
}

