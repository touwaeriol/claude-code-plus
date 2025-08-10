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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 简单的JSON消息类用于解析Claude CLI输出
 */
data class SimpleClaudeMessage(
    val type: String?,
    val content: String?,
    val raw: String
)

/**
 * 解析Claude CLI的JSONL输出
 */
private fun parseClaudeJsonLine(jsonLine: String): SimpleClaudeMessage? {
    if (jsonLine.isBlank()) return null
    
    return try {
        val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }
        val jsonElement = json.parseToJsonElement(jsonLine)
        
        if (jsonElement is JsonObject) {
            val type = jsonElement["type"]?.jsonPrimitive?.content
            val content = when {
                // 尝试多种可能的内容字段
                jsonElement.containsKey("content") -> {
                    val contentElement = jsonElement["content"]
                    when {
                        contentElement is JsonPrimitive -> contentElement.content
                        contentElement is JsonObject -> {
                            // 如果content是对象，尝试提取text字段
                            contentElement["text"]?.jsonPrimitive?.content ?: contentElement.toString()
                        }
                        else -> contentElement.toString()
                    }
                }
                jsonElement.containsKey("message") -> {
                    // 从message对象中提取
                    val messageObj = jsonElement["message"]
                    when {
                        messageObj is JsonPrimitive -> messageObj.content
                        messageObj is JsonObject -> {
                            messageObj["content"]?.jsonPrimitive?.content 
                                ?: messageObj["text"]?.jsonPrimitive?.content
                                ?: messageObj.toString()
                        }
                        else -> messageObj.toString()
                    }
                }
                jsonElement.containsKey("text") -> {
                    // 直接从text字段获取
                    jsonElement["text"]?.jsonPrimitive?.content
                }
                jsonElement.containsKey("data") -> {
                    // 从data对象中提取
                    val dataObj = jsonElement["data"]
                    if (dataObj is JsonObject) {
                        dataObj["text"]?.jsonPrimitive?.content ?: dataObj.toString()
                    } else {
                        dataObj?.jsonPrimitive?.content
                    }
                }
                else -> {
                    // 如果没有明确的内容字段，返回整个JSON作为内容
                    jsonLine
                }
            }
            
            SimpleClaudeMessage(
                type = type ?: "unknown",
                content = content,
                raw = jsonLine
            )
        } else {
            // 如果不是JSON对象，创建一个text类型的消息
            SimpleClaudeMessage(
                type = "text",
                content = jsonLine,
                raw = jsonLine
            )
        }
    } catch (e: Exception) {
        println("Error parsing JSON line: ${e.message}")
        // 解析失败时，仍然返回一个文本消息
        SimpleClaudeMessage(
            type = "text",
            content = jsonLine,
            raw = jsonLine
        )
    }
}

/**
 * 从消息中提取内容
 */
private fun extractContentFromMessage(message: SimpleClaudeMessage): String {
    return message.content ?: ""
}

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
    
    // 直接使用ClaudeCliWrapper实例
    val cliWrapper = remember { 
        com.claudecodeplus.sdk.ClaudeCliWrapper()
    }
    
    println("=== ChatViewNew 使用事件驱动架构 ===")
    println("tabId: $tabId")
    println("sessionId: $sessionId") 
    println("workingDirectory: $workingDirectory")
    
    // 获取或创建该标签的会话对象
    val sessionObject = remember(tabId, currentProject) {
        if (currentProject != null) {
            currentProject.getOrCreateSession(
                tabId = tabId, 
                initialSessionId = sessionId, 
                initialMessages = initialMessages ?: emptyList()
            )
        } else {
            val tempProject = com.claudecodeplus.ui.models.Project(
                id = "temp",
                name = "临时项目", 
                path = workingDirectory
            )
            tempProject.getOrCreateSession(
                tabId = tabId, 
                initialSessionId = sessionId, 
                initialMessages = initialMessages ?: emptyList()
            )
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
    
    // 简化版的消息发送函数
    fun sendMessage(markdownText: String) {
        if (sessionObject.isGenerating) {
            sessionObject.addToQueue(markdownText)
            sessionObject.inputResetTrigger = System.currentTimeMillis()
            return
        }
        
        val job = coroutineScope.launch {
            try {
                // 在协程开始时立即设置生成状态
                sessionObject.isGenerating = true
                println("[ChatViewNew] 设置 isGenerating = true")
                // 处理斜杠命令
                val processedText = if (markdownText.trim().startsWith("/")) {
                    val parts = markdownText.trim().split(" ", limit = 2)
                    val command = parts[0].substring(1)
                    val args = if (parts.size > 1) parts[1] else ""
                    """<command-name>/$command</command-name>
<command-message>$command</command-message>
<command-args>$args</command-args>"""
                } else {
                    markdownText
                }
                
                // 创建用户消息并立即添加到界面
                val userMessage = EnhancedMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.USER,
                    content = markdownText,
                    timestamp = System.currentTimeMillis(),
                    model = selectedModel,
                    contexts = contexts
                )
                sessionObject.addMessage(userMessage)
                
                // 判断是否为首次消息
                val userMessageCount = messages.count { it.role == MessageRole.USER }
                val isFirstMessage = userMessageCount == 1 // 刚添加了用户消息，所以现在是1表示首次
                
                // 准备CLI选项
                val projectCwd = sessionObject.getProjectCwd() ?: workingDirectory
                val options = ClaudeCliWrapper.QueryOptions(
                    sessionId = sessionObject.sessionId,
                    cwd = projectCwd,
                    model = selectedModel?.cliName,
                    permissionMode = selectedPermissionMode.cliName
                )
                
                println("[ChatViewNew] 发送消息: isFirstMessage=$isFirstMessage, sessionId=${sessionObject.sessionId}")
                println("[ChatViewNew] 准备调用 ClaudeCliWrapper.query")
                println("[ChatViewNew] options = $options")
                
                // 添加一个助手消息占位符来显示响应
                val assistantMessage = EnhancedMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = "", // 开始为空，会逐步添加内容
                    timestamp = System.currentTimeMillis(),
                    model = selectedModel,
                    isStreaming = true
                )
                sessionObject.addMessage(assistantMessage)
                
                // 设置输出回调来处理Claude CLI的实时输出
                cliWrapper.setOutputLineCallback { jsonLine ->
                    println("[ChatViewNew] 收到Claude CLI输出: $jsonLine")
                    
                    coroutineScope.launch {
                        try {
                            // 先尝试直接处理非JSON输出（可能是纯文本响应）
                            if (!jsonLine.trim().startsWith("{")) {
                                println("[ChatViewNew] 收到非JSON输出，直接添加到助手消息: $jsonLine")
                                sessionObject.replaceMessage(assistantMessage.id) { existing ->
                                    existing.copy(
                                        content = existing.content + jsonLine + "\n",
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                                return@launch
                            }
                            
                            // 尝试解析JSONL消息
                            val message = parseClaudeJsonLine(jsonLine)
                            if (message != null) {
                                println("[ChatViewNew] 解析消息成功: type=${message.type}, content长度=${message.content?.length ?: 0}")
                                
                                // 根据消息类型处理
                                when (message.type?.lowercase()) {
                                    "assistant", "text" -> {
                                        // 更新助手消息内容
                                        val content = extractContentFromMessage(message)
                                        if (content.isNotEmpty()) {
                                            println("[ChatViewNew] 更新助手消息内容: $content")
                                            sessionObject.replaceMessage(assistantMessage.id) { existing ->
                                                existing.copy(
                                                    content = if (existing.content.isEmpty()) content else existing.content + content,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            }
                                        }
                                    }
                                    "tool_use" -> {
                                        println("[ChatViewNew] 收到工具调用消息")
                                        // TODO: 处理工具调用消息
                                    }
                                    "tool_result" -> {
                                        println("[ChatViewNew] 收到工具结果消息") 
                                        // TODO: 处理工具结果消息
                                    }
                                    "end" -> {
                                        println("[ChatViewNew] 收到结束消息")
                                        // 标记助手消息完成
                                        sessionObject.replaceMessage(assistantMessage.id) { existing ->
                                            existing.copy(isStreaming = false)
                                        }
                                    }
                                    else -> {
                                        println("[ChatViewNew] 未处理的消息类型: ${message.type}，原始内容: $jsonLine")
                                        // 对于未知类型，也尝试提取内容
                                        val content = extractContentFromMessage(message)
                                        if (content.isNotEmpty()) {
                                            sessionObject.replaceMessage(assistantMessage.id) { existing ->
                                                existing.copy(
                                                    content = existing.content + content,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                println("[ChatViewNew] 解析JSON失败，原始内容: $jsonLine")
                                // 如果解析失败，直接添加原始内容
                                sessionObject.replaceMessage(assistantMessage.id) { existing ->
                                    existing.copy(
                                        content = existing.content + jsonLine + "\n",
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            println("[ChatViewNew] 解析输出异常: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
                
                // 直接调用ClaudeCliWrapper
                val result = try {
                    cliWrapper.query(processedText, options)
                } catch (e: Exception) {
                    println("[ChatViewNew] ClaudeCliWrapper.query 调用异常: ${e.message}")
                    e.printStackTrace()
                    throw e
                }
                
                println("[ChatViewNew] 查询完成: success=${result.success}, sessionId=${result.sessionId}")
                
                if (!result.success) {
                    val errorMessage = EnhancedMessage(
                        id = "error_${System.currentTimeMillis()}",
                        role = MessageRole.SYSTEM,
                        content = "Claude CLI 执行失败: ${result.errorMessage}",
                        timestamp = System.currentTimeMillis(),
                        toolCalls = emptyList(),
                        orderedElements = emptyList()
                    )
                    sessionObject.addMessage(errorMessage)
                }
                
                // 如果是新会话且返回了sessionId，更新sessionObject
                if (result.sessionId != null && sessionObject.sessionId != result.sessionId) {
                    sessionObject.sessionId = result.sessionId
                    println("[ChatViewNew] 更新会话ID: ${result.sessionId}")
                }
                
                // 清空上下文
                sessionObject.clearContexts()
                
            } catch (e: Exception) {
                println("[ChatViewNew] 发送消息异常: ${e.message}")
                e.printStackTrace()
                
                val errorMessage = EnhancedMessage(
                    id = "error_${System.currentTimeMillis()}",
                    role = MessageRole.SYSTEM,
                    content = "发送失败: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    toolCalls = emptyList(),
                    orderedElements = emptyList()
                )
                sessionObject.addMessage(errorMessage)
            } finally {
                sessionObject.isGenerating = false
                sessionObject.currentStreamJob = null
            }
        }
        
        sessionObject.startGenerating(job)
    }
    
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
                VerticallyScrollableContainer(
                    scrollState = rememberScrollState(),
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