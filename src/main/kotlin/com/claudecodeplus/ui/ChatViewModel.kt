package com.claudecodeplus.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    init {
        // 添加欢迎消息
        addMessage(ChatMessage(
            content = "欢迎使用 Claude Code Plus！这是一个独立的 UI 演示。",
            isUser = false,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    fun updateInputText(text: String) {
        _inputText.value = text
    }
    
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isNotEmpty()) {
            // 添加用户消息
            addMessage(ChatMessage(
                content = text,
                isUser = true,
                timestamp = System.currentTimeMillis()
            ))
            
            // 清空输入
            _inputText.value = ""
            
            // 模拟 AI 回复
            simulateAIResponse(text)
        }
    }
    
    fun connect() {
        _isConnected.value = true
        addMessage(ChatMessage(
            content = "已连接到 Claude 服务。",
            isUser = false,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    fun disconnect() {
        _isConnected.value = false
        addMessage(ChatMessage(
            content = "已断开连接。",
            isUser = false,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }
    
    private fun simulateAIResponse(userMessage: String) {
        // 模拟不同类型的回复
        val response = when {
            userMessage.contains("代码", ignoreCase = true) -> 
                "这是一个代码相关的问题。我可以帮你编写、调试或解释代码。\n\n```kotlin\n// 示例代码\nfun example() {\n    println(\"Hello, World!\")\n}\n```"
            userMessage.contains("你好", ignoreCase = true) || userMessage.contains("hello", ignoreCase = true) -> 
                "你好！我是 Claude，很高兴为你提供帮助。有什么可以帮助你的吗？"
            userMessage.contains("功能", ignoreCase = true) -> 
                "Claude Code Plus 的主要功能包括：\n\n1. 智能代码补全\n2. 代码解释和文档生成\n3. 错误诊断和修复建议\n4. 代码重构建议\n5. 与 IntelliJ IDEA 深度集成"
            else -> 
                "我理解了你的问题：\"$userMessage\"。这是一个模拟回复，实际使用时会连接到真正的 Claude API。"
        }
        
        addMessage(ChatMessage(
            content = response,
            isUser = false,
            timestamp = System.currentTimeMillis()
        ))
    }
}