package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.ui.models.Message
import com.claudecodeplus.ui.models.MessageRole
import kotlinx.coroutines.flow.toList
import java.nio.file.Paths

/**
 * ClaudeService 的默认实现
 * 使用 ClaudeCliWrapper 与 Claude CLI 交互
 */
class DefaultClaudeService(
    private val cliWrapper: ClaudeCliWrapper = ClaudeCliWrapper(),
    private val workingDirectory: String = System.getProperty("user.dir")
) : ClaudeService {
    
    private val messageHistory = mutableListOf<Message>()
    private var currentSessionId: String? = null
    
    override suspend fun sendMessage(message: String): Result<String> {
        return try {
            // 保存用户消息到历史
            messageHistory.add(
                Message(
                    role = MessageRole.USER.name,
                    content = message,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            val options = ClaudeCliWrapper.QueryOptions(
                model = "claude-opus-4-20250514",
                cwd = workingDirectory,
                resume = currentSessionId
            )
            
            val responseBuilder = StringBuilder()
            val stream = cliWrapper.query(message, options)
            
            stream.collect { sdkMessage ->
                when (sdkMessage.type) {
                    MessageType.TEXT -> {
                        sdkMessage.data.text?.let { responseBuilder.append(it) }
                    }
                    MessageType.ERROR -> {
                        val errorMsg = sdkMessage.data.error ?: "Unknown error"
                        throw Exception(errorMsg)
                    }
                    MessageType.START -> {
                        sdkMessage.data.sessionId?.let { 
                            currentSessionId = it 
                        }
                    }
                    MessageType.TOOL_USE -> {
                        responseBuilder.append("\n\n🔧 ${sdkMessage.data.toolName}: ")
                        responseBuilder.append(sdkMessage.data.toolInput?.toString() ?: "")
                        responseBuilder.append("\n\n")
                    }
                    else -> {
                        // 忽略其他消息类型
                    }
                }
            }
            
            val response = responseBuilder.toString()
            
            // 保存 AI 响应到历史
            messageHistory.add(
                Message(
                    role = MessageRole.ASSISTANT.name,
                    content = response,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getHistory(): List<Message> {
        return messageHistory.toList()
    }
    
    override suspend fun clearHistory() {
        messageHistory.clear()
        currentSessionId = null
    }
}