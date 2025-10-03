package com.claudecodeplus.ui.components

import com.claudecodeplus.core.logging.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.theme.Dimensions
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.session.models.*
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.jewel.components.QueueIndicator
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.SessionManager
import com.claudecodeplus.ui.jewel.components.tools.CompactToolCallDisplay
import java.time.Instant
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ContextProcessor
import java.lang.reflect.Method
import com.claudecodeplus.core.services.ProjectService
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
// Event services removed - not available in SDK
// import com.claudecodeplus.sdk.ClaudeEventService
// import com.claudecodeplus.sdk.ClaudeEvent
import com.claudecodeplus.ui.utils.ClaudeSessionHistoryLoader
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
    // tabManager: com.claudecodeplus.ui.services.ChatTabManager? = null, // 已移除ChatTabManager
    currentTabId: String? = null,
    currentProject: com.claudecodeplus.ui.models.Project? = null,
    projectManager: com.claudecodeplus.ui.services.ProjectManager? = null,
    backgroundService: Any? = null,  // 新增：后台服务
    sessionStateSync: Any? = null,   // 新增：状态同步器
    onNewSessionRequest: (() -> Unit)? = null,  // 新增：新会话请求回调
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,  // 新增：IDE 集成接口
    modifier: Modifier = Modifier
) {
    // 使用稳定的 CoroutineScope，避免 composition 生命周期问题
    val coroutineScope = rememberCoroutineScope()

    // 创建一个稳定的回调函数引用，避免在 composition 外使用 coroutineScope
    val stableCoroutineScope = remember {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
        )
    }

    // 清理 CoroutineScope
    DisposableEffect(Unit) {
        onDispose {
            // 注意：不取消 stableCoroutineScope，让 AI 响应能够继续完成
            // 这样可以避免 "ChatViewNew disposed" 异常
            // stableCoroutineScope.cancel("ChatViewNew disposed")
        }
    }

    // 移除已删除的消息转换器

    // 使用SessionObject内部的ClaudeCliWrapper实例，支持后台处理
    // val cliWrapper = remember { com.claudecodeplus.sdk.ClaudeCliWrapper() } // 旧方法，已移至SessionObject

    logD("=== ChatViewNew 使用事件驱动架构 ===")
    logD("tabId: $tabId")
    logD("sessionId: $sessionId")
    logD("workingDirectory: $workingDirectory")

    // 获取或创建该标签的会话对象（保持现有架构，但使用增强的SessionObject）
    // 使用全局 ProjectManager 确保 Project 实例的唯一性
    val project = currentProject ?: com.claudecodeplus.ui.services.ProjectManager.getOrCreateProject(workingDirectory)

    // 使用 remember 和 project+tabId 组合键来缓存 SessionObject
    val sessionObjectKey = "${project.id}:$tabId"
    val sessionObject = remember(sessionObjectKey) {
        logD("[ChatViewNew] 创建/获取 SessionObject，key=$sessionObjectKey")
        logD("[ChatViewNew] Project hashCode: ${project.hashCode()}")

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
                    logD("[ChatViewNew] 恢复会话消息: ${initialMessages.size} 条")
                }
            }
            logD("[ChatViewNew] 会话对象已创建/获取: tabId=$tabId, sessionId=${session.sessionId}, messages=${session.messages.size}")
        }
    }

    // 注册会话对象到工具窗口工厂（用于New Chat功能）
    LaunchedEffect(sessionObject) {
        try {
            // 通过反射调用工具窗口工厂的静态方法
            val factoryClass = Class.forName("com.claudecodeplus.plugin.ClaudeCodePlusToolWindowFactory")
            val companionField = factoryClass.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)

            val setMethod = companion.javaClass.getMethod("setCurrentSessionObject", Any::class.java)
            setMethod.invoke(companion, sessionObject)

            logD("[ChatViewNew] 已注册会话对象到工具窗口工厂")
        } catch (e: Exception) {
            // 如果不在插件环境中，忽略错误
            logD("[ChatViewNew] 非插件环境，跳过会话注册: ${e.message}")
        }
    }

    // 监听标签切换，确保正确恢复会话状态
    LaunchedEffect(tabId, currentProject) {
        logD("[ChatViewNew] 标签/项目变化检测: tabId=$tabId, project=${currentProject?.name}")

        // 🎯 关键修复：每次标签显示时检查并恢复 sessionId
        if (sessionObject.sessionId == null && sessionObject.messages.isEmpty()) {
            logD("[ChatViewNew] 检测到 SessionObject 缺少 sessionId，尝试从历史中恢复...")
            try {
                val foundSessionId = com.claudecodeplus.ui.utils.SessionIdRegistry.getSessionId(workingDirectory, tabId)
                if (foundSessionId != null) {
                    logD("[ChatViewNew] 🎯 从历史会话找到 sessionId: $foundSessionId")
                    sessionObject.updateSessionId(foundSessionId)

                    // 注释掉自动加载历史消息，避免启动延迟
                    // 用户可以通过界面按钮主动选择恢复历史会话
                    logD("[ChatViewNew] 开始加载历史消息...")
                    // sessionObject.loadNewMessages(forceFullReload = true)
                    logD("[ChatViewNew] 跳过自动加载历史消息，提升启动速度")
                } else {
                    logD("[ChatViewNew] ⚠️ 未找到历史 sessionId，会话为新会话")
                }
            } catch (e: Exception) {
                logD("[ChatViewNew] 查找历史 sessionId 失败: ${e.message}")
                logE("Exception caught", e)
            }
        }

        if (currentProject != null) {
            // 确保会话状态正确恢复
            val currentSession = currentProject.getSession(tabId)
            if (currentSession != null) {
                logD("[ChatViewNew] 找到现有会话，验证状态完整性")

                // 验证并恢复状态（如果需要）
                if (sessionId != null && currentSession.sessionId != sessionId) {
                    currentSession.updateSessionId(sessionId)
                    logD("[ChatViewNew] 恢复 sessionId: $sessionId")
                }

                if (initialMessages != null && initialMessages.isNotEmpty() &&
                    currentSession.messages.size < initialMessages.size
                ) {
                    currentSession.messages = initialMessages
                    logD("[ChatViewNew] 恢复消息历史: ${initialMessages.size} 条")
                }
            } else {
                // 新项目中没有这个标签的会话，创建新会话
                logD("[ChatViewNew] 在新项目中创建会话")
                currentProject.getOrCreateSession(
                    tabId = tabId,
                    initialSessionId = sessionId,
                    initialMessages = initialMessages ?: emptyList()
                )
            }
        }
    }


    // 从 sessionObject 获取所有状态
    // 分页加载状态
    var loadedMessageCount by remember { mutableIntStateOf(50) } // 默认显示最后50条消息

    val messages by derivedStateOf {
        val totalMessages = sessionObject.messages.size
        logD(
            "[ChatViewNew] messages derivedStateOf 被重新计算: $totalMessages 条总消息, 显示最后 ${
                minOf(
                    loadedMessageCount,
                    totalMessages
                )
            } 条"
        )
        logD("[ChatViewNew] SessionObject实例ID: ${System.identityHashCode(sessionObject)}")

        // 性能优化：只取最后N条消息进行渲染
        if (totalMessages > loadedMessageCount) {
            sessionObject.messages.takeLast(loadedMessageCount)
        } else {
            sessionObject.messages
        }
    }
    val contexts by derivedStateOf { sessionObject.contexts }
    val isGenerating by derivedStateOf {
        logD("[ChatViewNew] isGenerating derivedStateOf 被重新计算: ${sessionObject.isGenerating}")
        sessionObject.isGenerating
    }
    val selectedModel by derivedStateOf { sessionObject.selectedModel }
    val selectedPermissionMode by derivedStateOf { sessionObject.selectedPermissionMode }
    val skipPermissions by derivedStateOf { sessionObject.skipPermissions }
    val inputResetTrigger by derivedStateOf { sessionObject.inputResetTrigger }
    val errorMessage by derivedStateOf { sessionObject.errorMessage }

    // 回退到SessionObject的发送方法
    fun fallbackToSessionObject(markdownText: String) {
        stableCoroutineScope.launch {
            try {
                val result = sessionObject.sendMessage(markdownText, workingDirectory)
                logD("[ChatViewNew] SessionObject.sendMessage完成: success=${result.success}")
            } catch (e: Exception) {
                logD("[ChatViewNew] SessionObject处理异常: ${e.message}")
                logE("Exception caught", e)
            }
        }
    }

    // 修改为使用后台服务的消息发送方法
    fun sendMessage(markdownText: String) {
        logD("[ChatViewNew] 开始发送消息（后台服务模式）: '$markdownText'")

        // 检查生成状态
        if (sessionObject.isGenerating) {
            logD("[ChatViewNew] 会话正在生成中，不能发送新消息")
            return
        }

        // 添加用户消息到UI
        // 如果有 contexts，将其以 front matter 格式嵌入到内容中
        val contentWithContexts = if (sessionObject.contexts.isNotEmpty()) {
            buildContentWithFrontMatter(markdownText, sessionObject.contexts)
        } else {
            markdownText
        }

        val userMessage = EnhancedMessage.create(
            id = java.util.UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = contentWithContexts,
            timestamp = System.currentTimeMillis(),
            model = sessionObject.selectedModel,
            contexts = sessionObject.contexts
        )
        sessionObject.addMessage(userMessage)
        logD("[ChatViewNew] 用户消息已添加到UI")

        // 根据配置决定是否自动清理上下文标签
        if (sessionObject.autoCleanupContexts) {
            sessionObject.contexts = emptyList()
            logD("[ChatViewNew] 上下文标签已根据配置自动清理")
        } else {
            logD("[ChatViewNew] 上下文标签已保留，可作为持续的会话上下文")
        }

        // 🔧 简化协程调用，避免类加载器冲突
        // 直接使用 SessionObject 处理，避免在 UI 层启动协程
        logD("[ChatViewNew] 使用 SessionObject 方法处理消息发送")
        fallbackToSessionObject(markdownText)
    }

    // 🔄 工具窗口状态监听 - 简化方式：通过后台服务自动恢复
    // 注意：工具窗口监听器已在 ClaudeCodePlusToolWindowFactory 中注册
    // 这里只需要响应状态变化即可
    LaunchedEffect(sessionStateSync) {
        if (sessionStateSync != null) {
            logD("[ChatViewNew] 🔄 后台服务已连接，状态将自动同步")
            // 状态同步已通过下面的 observeSessionUpdates 实现
            // 无需额外的工具窗口监听器
        }
    }

    // 🔄 实时监听后台服务状态同步
    LaunchedEffect(sessionStateSync, sessionObject.sessionId) {
        if (sessionStateSync != null && sessionObject.sessionId != null) {
            logD("[ChatViewNew] 🔄 启动后台服务状态监听: sessionId=${sessionObject.sessionId}")

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
                    logD("[ChatViewNew] 📥 收到后台状态更新: $backendState")

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

                        logD("[ChatViewNew] 🔄 同步状态 - 后台消息数: ${backendMessages.size}, UI消息数: ${sessionObject.messages.size}, 生成中: $backendIsGenerating, 流式文本长度: ${backendStreamingText.length}")

                        // 🎯 智能消息同步：只同步新增的消息
                        if (backendMessages.size > sessionObject.messages.size) {
                            val newMessages = backendMessages.drop(sessionObject.messages.size)
                            logD("[ChatViewNew] 🆕 检测到 ${newMessages.size} 条后台新消息，开始同步")

                            newMessages.forEach { newMessage ->
                                sessionObject.addMessage(newMessage)
                                logD("[ChatViewNew] ➕ 同步消息: ${newMessage.role} - '${newMessage.content.take(50)}...'")
                            }
                        } else if (backendMessages.size == sessionObject.messages.size && backendStreamingText.isNotEmpty()) {
                            // 消息数量相同但有流式文本更新，更新最后一条助手消息
                            if (sessionObject.messages.isNotEmpty()) {
                                val lastMessage = sessionObject.messages.last()
                                if (lastMessage.role == MessageRole.ASSISTANT && lastMessage.isStreaming) {
                                    // 后台的 streamingText 已经是完整内容，直接替换
                                    val updatedMessage = lastMessage.withContent(
                                        text = backendStreamingText.toString(),
                                        isStreaming = backendIsGenerating
                                    )
                                    // 替换最后一条消息
                                    sessionObject.messages = sessionObject.messages.dropLast(1) + updatedMessage
                                    logD("[ChatViewNew] 🔄 更新流式消息内容，总长度: ${updatedMessage.content.length}")
                                }
                            }
                        }

                        // 同步生成状态
                        if (sessionObject.isGenerating != backendIsGenerating) {
                            sessionObject.isGenerating = backendIsGenerating
                            logD("[ChatViewNew] 🔄 同步生成状态: ${sessionObject.isGenerating} → $backendIsGenerating")
                        }

                        // 如果生成完成，确保最后一条消息的流式状态也同步
                        if (!backendIsGenerating && sessionObject.messages.isNotEmpty()) {
                            val lastMessage = sessionObject.messages.last()
                            if (lastMessage.role == MessageRole.ASSISTANT && lastMessage.isStreaming) {
                                val finalMessage = lastMessage.copy(isStreaming = false)
                                sessionObject.messages = sessionObject.messages.dropLast(1) + finalMessage
                                logD("[ChatViewNew] ✅ 标记最后一条助手消息为完成状态")
                            }
                        }

                    } catch (reflectionError: Exception) {
                        logD("[ChatViewNew] ⚠️ 反射获取状态属性失败: ${reflectionError.message}")
                        // 继续监听，不中断流程
                    }
                }

            } catch (e: Exception) {
                logD("[ChatViewNew] ❌ 状态监听异常: ${e.message}")
                logE("Exception caught", e)
                // 监听失败，但不影响基本功能
            }
        } else {
            logD("[ChatViewNew] ⚠️ 无后台服务或会话ID为空，跳过状态监听")
        }
    }

    // 旧代码已删除，现在使用SessionObject的sendMessage方法

    // 移除了所有后台服务状态跟踪相关代码
    // 不再需要这些变量和检查，提升性能

    // UI与原来完全相同，只是底层使用事件驱动
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(min = Dimensions.MinWidth.MAIN_WINDOW)  // 始终应用最小宽度保护
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 移除状态指示器栏，因为不再需要显示后台服务状态
        // 这些信息对用户没有实际价值，还会占用界面空间

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

                                    stableCoroutineScope.launch {
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
                                                // delay(1000) // 移除不必要的延迟
                                                // 成功后会自动通过状态同步更新UI
                                            } else {
                                                recoveryMessage = "未找到历史记录"
                                                // delay(2000) // 移除不必要的延迟
                                            }
                                        } catch (e: Exception) {
                                            recoveryMessage = "恢复失败: ${e.message}"
                                            delay(2000)
                                            logD("[ChatViewNew] 会话恢复异常: ${e.message}")
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
                        val toolCallBottomPosition =
                            toolCallTopPosition + (if (isExpanded) toolCallExpandedHeight else 40f)

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
                    logD("[ChatViewNew] 精确遮挡检测: 找到${obscuredExpandedTools.size}个被遮挡的展开工具")
                    obscuredExpandedTools.forEach { visibility ->
                        logD("  - 工具 ${visibility.toolCallId}: 顶部位置=${visibility.estimatedTopPosition}, 底部位置=${visibility.estimatedBottomPosition}")
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
                            ideIntegration = ideIntegration,
                            expandedTools = expandedToolCalls,  // 传递展开状态
                            onExpandedChange = { toolId, expanded ->
                                expandedToolCalls[toolId] = expanded
                                logD("[ChatViewNew] 工具状态更新: $toolId -> $expanded")
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
                        logD("[ChatViewNew] 恢复滚动位置: $savedPosition")
                        scrollState.scrollTo(savedPosition.toInt())
                    } else {
                        // 新会话或没有保存位置，滚动到底部
                        if (messages.isNotEmpty()) {
                            logD("[ChatViewNew] 滚动到底部")
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    }
                }

                // 监听消息变化，新消息时滚动到底部
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        // kotlinx.coroutines.delay(100) // 移除等待，让UI立即响应
                        scrollState.scrollTo(scrollState.maxValue)
                        logD("[ChatViewNew] 新消息滚动到底部")
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
                        // 错误横幅 - 在所有内容之前显示
                        errorMessage?.let { error ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color.Red.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.Red.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 错误图标
                                    Text(
                                        text = "⚠️",
                                        style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
                                    )

                                    // 错误信息
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (error.contains("API key not found") || error.contains("401")) {
                                            Text(
                                                text = "Claude 认证错误",
                                                style = JewelTheme.defaultTextStyle.copy(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.Red
                                                )
                                            )
                                            Text(
                                                text = "请在终端中运行 'claude login' 命令完成认证",
                                                style = JewelTheme.defaultTextStyle.copy(
                                                    fontSize = 12.sp,
                                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                                                )
                                            )
                                        } else {
                                            Text(
                                                text = "Claude CLI 错误",
                                                style = JewelTheme.defaultTextStyle.copy(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.Red
                                                )
                                            )
                                            Text(
                                                text = error,
                                                style = JewelTheme.defaultTextStyle.copy(
                                                    fontSize = 12.sp,
                                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                                                )
                                            )
                                        }
                                    }

                                    // 关闭按钮
                                    IconButton(
                                        onClick = {
                                            sessionObject.clearError()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text(
                                            text = "×",
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 16.sp,
                                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

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
                            // 如果有更多历史消息，显示加载更多按钮
                            if (sessionObject.messages.size > loadedMessageCount) {
                                val remainingCount = sessionObject.messages.size - loadedMessageCount
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable {
                                            // 每次加载更多50条消息
                                            loadedMessageCount += 50
                                            logD("[ChatViewNew] 加载更多消息，当前显示: $loadedMessageCount / ${sessionObject.messages.size}")
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "⬆ 加载更多消息 ($remainingCount 条历史消息)",
                                        style = JewelTheme.defaultTextStyle.copy(
                                            color = JewelTheme.globalColors.text.info,
                                            fontSize = 12.sp
                                        )
                                    )
                                }

                                // 分隔线
                                Divider(
                                    orientation = Orientation.Horizontal,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = JewelTheme.globalColors.borders.normal
                                )
                            }

                            messages.forEach { message ->
                                logD("[ChatViewNew] 🔍 处理消息: role=${message.role}, content长度=${message.content.length}")
                                logD("[ChatViewNew] 📝 消息内容前100字符: ${message.content.take(100)}")
                                if (message.content.contains("##") || message.content.contains("**")) {
                                    logD("[ChatViewNew] ✅ 检测到Markdown标记")
                                }
                                logD("[ChatViewNew] 📊 orderedElements数量: ${message.orderedElements.size}")

                                when (message.role) {
                                    MessageRole.USER -> {
                                        logD("[ChatViewNew] 👤 渲染用户消息")
                                        UnifiedInputArea(
                                            mode = InputAreaMode.DISPLAY,
                                            message = message,
                                            onContextClick = { uri ->
                                                if (uri.startsWith("file://") && ideIntegration != null) {
                                                    val path = uri.removePrefix("file://")
                                                    ideIntegration.openFile(path, null)
                                                }
                                            },
                                            sessionObject = sessionObject,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    MessageRole.ASSISTANT, MessageRole.SYSTEM, MessageRole.ERROR -> {
                                        // 使用 AssistantMessageDisplay 组件来渲染 AI 消息
                                        com.claudecodeplus.ui.components.AssistantMessageDisplay(
                                            message = message,
                                            modifier = Modifier.fillMaxWidth(),
                                            ideIntegration = ideIntegration,
                                            expandedTools = expandedToolCalls,  // 传递展开状态
                                            onExpandedChange = { toolId, expanded ->
                                                expandedToolCalls[toolId] = expanded
                                            }
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
                autoCleanupContexts = sessionObject.autoCleanupContexts,
                onAutoCleanupContextsChange = { autoCleanup ->
                    sessionObject.autoCleanupContexts = autoCleanup
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

/**
 * 构建包含 markdown front matter 的消息内容
 * 格式:
 * ---
 * contexts:
 *   - file:/path/to/file.txt
 *   - web:https://example.com
 * ---
 * 实际消息内容...
 */
private fun buildContentWithFrontMatter(
    originalContent: String,
    contexts: List<ContextReference>
): String {
    return ContextProcessor.generateFrontMatter(contexts) + originalContent
}


