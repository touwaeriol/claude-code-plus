package com.claudecodeplus.ui

import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.sdk.SDKMessage

/**
 * 消息渲染器，负责将不同类型的消息转换为 Markdown 格式
 */
object MessageRenderer {
    
    /**
     * 渲染 SDK 消息为 Markdown 格式
     */
    fun renderMessage(message: SDKMessage): String {
        return when (message.type) {
            MessageType.TEXT -> renderTextMessage(message)
            MessageType.TOOL_USE -> renderToolUseMessage(message)
            MessageType.TOOL_RESULT -> renderToolResultMessage(message)
            MessageType.ERROR -> renderErrorMessage(message)
            MessageType.START -> renderSystemMessage("会话开始")
            MessageType.END -> renderSystemMessage("会话结束")
        }
    }
    
    /**
     * 渲染文本消息
     */
    private fun renderTextMessage(message: SDKMessage): String {
        return message.data.text ?: ""
    }
    
    /**
     * 渲染工具调用消息
     */
    private fun renderToolUseMessage(message: SDKMessage): String {
        val toolName = message.data.toolName ?: "未知工具"
        val toolInput = message.data.toolInput
        
        return buildString {
            appendLine("```tool-use")
            appendLine("🔧 调用工具: $toolName")
            if (toolInput != null) {
                appendLine("输入参数:")
                appendLine("```json")
                appendLine(formatJson(toolInput.toString()))
                appendLine("```")
            }
            appendLine("```")
        }
    }
    
    /**
     * 渲染工具结果消息
     */
    private fun renderToolResultMessage(message: SDKMessage): String {
        val result = message.data.toolResult
        
        return buildString {
            appendLine("```tool-result")
            appendLine("📋 工具执行结果:")
            appendLine()
            when (result) {
                is String -> appendLine(result)
                is Map<*, *> -> {
                    appendLine("```json")
                    appendLine(formatJson(result.toString()))
                    appendLine("```")
                }
                else -> appendLine(result?.toString() ?: "无结果")
            }
            appendLine("```")
        }
    }
    
    /**
     * 渲染错误消息
     */
    private fun renderErrorMessage(message: SDKMessage): String {
        val error = message.data.error ?: "未知错误"
        return buildString {
            appendLine("```error")
            appendLine("❌ 错误: $error")
            appendLine("```")
        }
    }
    
    /**
     * 渲染系统消息
     */
    private fun renderSystemMessage(text: String): String {
        return buildString {
            appendLine("```system")
            appendLine("ℹ️ $text")
            appendLine("```")
        }
    }
    
    /**
     * 格式化 JSON 字符串
     */
    private fun formatJson(json: String): String {
        return try {
            // 简单的 JSON 格式化
            json.replace(",", ",\n    ")
                .replace("{", "{\n    ")
                .replace("}", "\n}")
        } catch (e: Exception) {
            json
        }
    }
    
    /**
     * 渲染工具调用的紧凑格式（类似 Claude Code CLI）
     */
    fun renderCompactToolCall(toolName: String, input: Any? = null): String {
        return buildString {
            append("**🔧 Called the $toolName tool")
            if (input != null) {
                append(" with the following input:** ")
                when (input) {
                    is Map<*, *> -> {
                        val params = input.entries.joinToString(", ") { (k, v) ->
                            "$k: ${if (v is String) "\"$v\"" else v}"
                        }
                        append("{$params}")
                    }
                    else -> append(input.toString())
                }
            } else {
                append("**")
            }
        }
    }
    
    /**
     * 渲染文件引用
     */
    fun renderFileReference(filePath: String, projectPath: String?): String {
        val relativePath = if (projectPath != null && filePath.startsWith(projectPath)) {
            filePath.substring(projectPath.length + 1)
        } else {
            filePath
        }
        return "[@$relativePath](file://$filePath)"
    }
}