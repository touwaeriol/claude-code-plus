package com.claudecodeplus.core

import com.claudecodeplus.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.time.LocalDateTime
import java.util.UUID

/**
 * 使用 GraalVM Python 的 Claude 会话实现
 */
class GraalPythonSession(
    override val config: SessionConfig,
    private val pythonContext: Context
) : ClaudeSession {
    
    override val sessionId: String = UUID.randomUUID().toString()
    
    private var _state = SessionState(
        sessionId = sessionId,
        status = SessionStatus.IDLE
    )
    
    override val state: SessionState
        get() = _state
    
    private val messages = mutableListOf<SessionMessage>()
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            _state = _state.copy(
                status = SessionStatus.INITIALIZING,
                startTime = LocalDateTime.now()
            )
            
            // 初始化 Python 环境
            pythonContext.eval("python", """
import sys
import os

# 设置工作目录
os.chdir('${config.workingDirectory.replace("\\", "\\\\")}')

# 导入 claude-sdk-wrapper
try:
    from claude_sdk_wrapper import ClaudeSDKWrapper
    wrapper = ClaudeSDKWrapper()
    wrapper.initialize({
        'api_key': '${config.apiKey ?: ""}',
        'model': '${config.model}',
        'max_tokens': ${config.maxTokens},
        'temperature': ${config.temperature}
    })
    print("Claude SDK initialized successfully")
except Exception as e:
    print(f"Failed to initialize Claude SDK: {e}")
    raise
""")
            
            _state = _state.copy(status = SessionStatus.READY)
        } catch (e: Exception) {
            _state = _state.copy(status = SessionStatus.ERROR)
            throw RuntimeException("Failed to initialize GraalPython session", e)
        }
    }
    
    override suspend fun sendMessage(message: String): Flow<String> = flow {
        withContext(Dispatchers.IO) {
            try {
                _state = _state.copy(status = SessionStatus.PROCESSING)
                
                // 添加用户消息到历史
                val userMessage = SessionMessage(
                    role = MessageRole.USER,
                    content = message
                )
                messages.add(userMessage)
                _state = _state.copy(messages = messages.toList())
                
                // 调用 Python wrapper
                val pythonResult = pythonContext.eval("python", """
import json

try:
    # 发送消息到 Claude
    response = wrapper.send_message('${message.replace("'", "\\'")}')
    
    # 解析响应
    if response['success']:
        messages = response.get('messages', [])
        text_content = []
        for msg in messages:
            if msg['type'] == 'assistant':
                for content in msg.get('content', []):
                    if content['type'] == 'text':
                        text_content.append(content['text'])
        
        result = {
            'success': True,
            'response': ''.join(text_content)
        }
    else:
        result = {
            'success': False,
            'error': response.get('error', 'Unknown error')
        }
except Exception as e:
    result = {
        'success': False,
        'error': str(e)
    }

json.dumps(result)
""")
                
                val resultJson = pythonResult.asString()
                val result = parseJsonResult(resultJson)
                
                if (result["success"] as? Boolean == true) {
                    val response = result["response"] as String
                    
                    // 添加助手消息到历史
                    val assistantMessage = SessionMessage(
                        role = MessageRole.ASSISTANT,
                        content = response
                    )
                    messages.add(assistantMessage)
                    _state = _state.copy(messages = messages.toList())
                    
                    emit(response)
                } else {
                    val error = result["error"] as? String ?: "Unknown error"
                    throw RuntimeException("Claude API error: $error")
                }
                
                _state = _state.copy(status = SessionStatus.READY)
            } catch (e: Exception) {
                _state = _state.copy(status = SessionStatus.ERROR)
                
                // 添加错误消息
                val errorMessage = SessionMessage(
                    role = MessageRole.ERROR,
                    content = "Error: ${e.message}"
                )
                messages.add(errorMessage)
                _state = _state.copy(messages = messages.toList())
                
                emit("Error: ${e.message}")
            }
        }
    }
    
    override suspend fun stop() {
        // 停止当前处理
        _state = _state.copy(status = SessionStatus.READY)
    }
    
    override suspend fun terminate(): Unit = withContext(Dispatchers.IO) {
        try {
            _state = _state.copy(
                status = SessionStatus.TERMINATED,
                endTime = LocalDateTime.now()
            )
            
            // 清理 Python 环境
            pythonContext.eval("python", """
try:
    if 'wrapper' in globals():
        del wrapper
except:
    pass
""")
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
    
    override fun getHistory(): List<SessionMessage> = messages.toList()
    
    override fun clearHistory() {
        messages.clear()
        _state = _state.copy(messages = emptyList())
    }
    
    override fun isProcessing(): Boolean = _state.status == SessionStatus.PROCESSING
    
    private fun parseJsonResult(json: String): Map<String, Any> {
        // 简单的 JSON 解析实现
        val cleanJson = json.trim().removeSurrounding("\"").replace("\\\"", "\"")
        val map = mutableMapOf<String, Any>()
        
        if (cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
            val content = cleanJson.substring(1, cleanJson.length - 1)
            val pairs = content.split(", ")
            
            for (pair in pairs) {
                val keyValue = pair.split(": ", limit = 2)
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim().removeSurrounding("\"").removeSurrounding("'")
                    var value: Any = keyValue[1].trim()
                    
                    // 解析布尔值
                    when (value) {
                        "true", "True" -> value = true
                        "false", "False" -> value = false
                        else -> {
                            // 移除引号
                            if (value is String) {
                                value = value.toString().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    }
                    
                    map[key] = value
                }
            }
        }
        
        return map
    }
}