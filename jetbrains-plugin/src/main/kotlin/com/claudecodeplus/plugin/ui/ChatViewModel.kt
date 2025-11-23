package com.claudecodeplus.plugin.ui

import com.intellij.openapi.project.Project
import com.claudecodeplus.plugin.converters.DisplayItemConverter
import com.claudecodeplus.plugin.stream.StreamEventProcessor
import com.claudecodeplus.plugin.stream.StreamEventContext
import com.claudecodeplus.plugin.stream.MutableAssistantMessage
import com.claudecodeplus.plugin.types.*
import com.claudecodeplus.server.tools.IdeTools
import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import java.util.logging.Logger

/**
 * ChatViewModel - 完整复刻 Vue 前端版本
 * 
 * 对应 frontend/src/stores/sessionStore.ts
 * 
 * 核心改动：
 * 1. 使用 DisplayItem 类型系统
 * 2. 实现 StreamEvent 实时处理
 * 3. 使用 StateFlow 管理状态
 */
class ChatViewModel(
    private val project: Project,
    private val ideTools: IdeTools
) {
    
    private val logger = Logger.getLogger(ChatViewModel::class.java.name)
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var claudeClient: ClaudeCodeSdkClient? = null
    
    // === 核心状态（使用 StateFlow） ===
    
    // SDK 原始消息列表
    private val _messages = mutableListOf<com.claudecodeplus.sdk.types.Message>()
    
    // Assistant 消息列表（用于 StreamEvent 处理）
    private val _assistantMessages = mutableListOf<AssistantMessage>()
    
    // DisplayItem 列表（UI 展示）
    private val _displayItems = MutableStateFlow<List<DisplayItem>>(emptyList())
    val displayItems: StateFlow<List<DisplayItem>> = _displayItems.asStateFlow()
    
    // 待处理的工具调用
    private val _pendingToolCalls = mutableMapOf<String, ToolCallItem>()
    
    // 工具输入 JSON 累积器
    private val _toolInputJsonAccumulator = mutableMapOf<String, String>()
    
    // 流式状态
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    
    // 连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Token 统计
    private val _inputTokens = MutableStateFlow(0)
    val inputTokens: StateFlow<Int> = _inputTokens.asStateFlow()
    
    private val _outputTokens = MutableStateFlow(0)
    val outputTokens: StateFlow<Int> = _outputTokens.asStateFlow()
    
    /**
     * 初始化并连接Claude客户端
     */
    suspend fun connect() {
        if (_isConnected.value) {
            logger.info("Already connected")
            return
        }
        
        try {
            val options = buildClaudeOptions()
            claudeClient = ClaudeCodeSdkClient(options)
            claudeClient?.connect()
            _isConnected.value = true
            logger.info("✅ Connected to Claude SDK")
        } catch (e: Exception) {
            logger.severe("❌ Failed to connect: ${e.message}")
            throw e
        }
    }
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(text: String) {
        if (text.isBlank()) {
            return
        }
        
        // 添加用户消息
        val userMessage = UserMessage(
            content = JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("text"),
                            "text" to JsonPrimitive(text)
                        )
                    )
                )
            ),
            sessionId = "default"
        )
        addMessage(userMessage)
        
        // 确保已连接
        if (!_isConnected.value) {
            connect()
        }
        
        val client = claudeClient ?: throw IllegalStateException("Not connected")
        
        try {
            _isStreaming.value = true
            
            // 发送消息
            client.query(text)
            
            // 接收流式响应
            client.receiveResponse().collect { sdkMessage ->
                handleSdkMessage(sdkMessage)
            }
        } catch (e: Exception) {
            _isStreaming.value = false
            logger.severe("❌ Failed to send message: ${e.message}")
            
            // 添加错误消息
            val errorMessage = SystemMessage(
                subtype = "error",
                data = JsonObject(
                    mapOf("error" to JsonPrimitive(e.message ?: "Unknown error"))
                )
            )
            addMessage(errorMessage)
        }
    }
    
    /**
     * 处理 SDK 消息
     */
    private fun handleSdkMessage(sdkMessage: com.claudecodeplus.sdk.types.Message) {
        when (sdkMessage) {
            is StreamEvent -> {
                // ✅ 核心：处理 StreamEvent 实时更新
                handleStreamEvent(sdkMessage)
            }
            
            is AssistantMessage -> {
                // ✅ 处理完整的 AssistantMessage（作为兜底）
                handleAssistantMessage(sdkMessage)
            }
            
            is ResultMessage -> {
                // ✅ 处理结果消息
                handleResultMessage(sdkMessage)
            }
            
            is UserMessage -> {
                // 用户消息回显（通常不需要处理）
                logger.info("收到用户消息回显")
            }
            
            else -> {
                logger.info("收到其他类型消息: ${sdkMessage::class.simpleName}")
            }
        }
    }
    
    /**
     * 处理 StreamEvent
     */
    private fun handleStreamEvent(streamEvent: StreamEvent) {
        // ✅ 修复：直接操作 _assistantMessages，不创建副本
        // 如果需要 MutableAssistantMessage，在处理器内部处理
        
        // 将最后一个 AssistantMessage 转换为 Mutable（如果存在）
        val mutableMessages = mutableListOf<com.claudecodeplus.plugin.stream.MutableAssistantMessage>()
        
        if (_assistantMessages.isNotEmpty()) {
            val last = _assistantMessages.last()
            val mutable = com.claudecodeplus.plugin.stream.MutableAssistantMessage(
                content = last.content.toMutableList(),
                model = last.model,
                tokenUsage = last.tokenUsage
            )
            mutableMessages.add(mutable)
        }
        
        val context = StreamEventContext(
            messages = mutableMessages,
            toolInputJsonAccumulator = _toolInputJsonAccumulator,
            registerToolCall = { block -> registerToolCall(block) }
        )
        
        val result = StreamEventProcessor.process(streamEvent, context)
        
        // ✅ 关键修复：将修改后的 MutableAssistantMessage 同步回 _assistantMessages
        if (mutableMessages.isNotEmpty()) {
            val updated = mutableMessages.last()
            val newAssistantMessage = AssistantMessage(
                content = updated.content,
                model = updated.model,
                tokenUsage = updated.tokenUsage
            )
            
            if (_assistantMessages.isNotEmpty()) {
                _assistantMessages[_assistantMessages.size - 1] = newAssistantMessage
                _messages[_messages.size - 1] = newAssistantMessage
            } else if (result.newMessage != null) {
                _assistantMessages.add(result.newMessage)
                _messages.add(result.newMessage)
            }
        }
        
        if (result.messageUpdated || result.newMessage != null) {
            // ✅ 现在 _messages 已经是最新的，可以正确转换
            updateDisplayItems()
        }
        
        if (result.shouldSetGenerating != null) {
            _isStreaming.value = result.shouldSetGenerating
        }
    }
    
    /**
     * 处理完整的 AssistantMessage
     */
    private fun handleAssistantMessage(assistantMessage: AssistantMessage) {
        logger.info("📨 收到 AssistantMessage, content blocks: ${assistantMessage.content.size}")
        
        // ✅ 使用消息内容哈希判断是否重复（参考 Vue 前端逻辑）
        val lastMessage = _assistantMessages.lastOrNull()
        if (lastMessage != null) {
            // 比较内容和模型，如果相同说明是重复的
            val isSameContent = lastMessage.content.size == assistantMessage.content.size &&
                                lastMessage.model == assistantMessage.model
            if (isSameContent) {
                logger.info("⏭️ 跳过重复的 AssistantMessage（已通过 StreamEvent 处理）")
                
                // 但仍然更新 token 统计（可能更准确）
                assistantMessage.tokenUsage?.let { usage ->
                    _inputTokens.value = usage.inputTokens
                    _outputTokens.value = usage.outputTokens
                }
                return
            }
        }
        
        // 不重复，添加新消息
        _assistantMessages.add(assistantMessage)
        _messages.add(assistantMessage)
        
        // 更新 token 统计
        assistantMessage.tokenUsage?.let { usage ->
            _inputTokens.value = usage.inputTokens
            _outputTokens.value = usage.outputTokens
        }
        
        // 更新 DisplayItems
        updateDisplayItems()
    }
    
    /**
     * 处理 ResultMessage
     */
    private fun handleResultMessage(resultMessage: ResultMessage) {
        logger.info("✅ Response complete: ${resultMessage.subtype}")
        _isStreaming.value = false
        
        // 可以添加结果统计信息
    }
    
    /**
     * 添加消息
     */
    private fun addMessage(message: com.claudecodeplus.sdk.types.Message) {
        _messages.add(message)
        updateDisplayItems()
    }
    
    /**
     * 注册工具调用
     */
    private fun registerToolCall(block: ToolUseBlock) {
        DisplayItemConverter.createToolCall(block, _pendingToolCalls)
        updateDisplayItems()
    }
    
    /**
     * 更新 DisplayItems
     */
    private fun updateDisplayItems() {
        val items = DisplayItemConverter.convertToDisplayItems(_messages, _pendingToolCalls)
        _displayItems.value = items
        
        logger.fine("DisplayItems updated: ${items.size} items")
    }
    
    /**
     * 中断当前操作
     */
    suspend fun interrupt() {
        claudeClient?.interrupt()
        _isStreaming.value = false
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect() {
        claudeClient?.disconnect()
        claudeClient = null
        _isConnected.value = false
    }
    
    /**
     * 构建Claude选项
     * 
     * ⚠️ 注意：参数配置必须与 Vue Web 前端保持一致
     * 参见: frontend/src/stores/sessionStore.ts:buildConnectOptions
     */
    private fun buildClaudeOptions(): ClaudeAgentOptions {
        val projectPath = ideTools.getProjectPath()
        val cwd = if (projectPath.isNotBlank()) {
            Path.of(projectPath)
        } else {
            null
        }
        
        return ClaudeAgentOptions(
            model = "claude-sonnet-4-5-20250929",
            cwd = cwd,
            debugStderr = true,
            maxTurns = 10,
            permissionMode = com.claudecodeplus.sdk.types.PermissionMode.DEFAULT,
            // ✅ 与 Vue Web 前端保持一致的参数配置
            includePartialMessages = true,  // 启用流式事件，用于实时渲染
            print = true,                   // 启用打印输出
            verbose = true,                 // 启用详细日志（与 print + stream-json 一起使用时必需）
            dangerouslySkipPermissions = true,
            allowDangerouslySkipPermissions = true,
            // 设置 outputFormat 为 stream-json
            extraArgs = mapOf("output-format" to "stream-json")
        )
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        viewModelScope.cancel()
        runBlocking {
            disconnect()
        }
    }
}

