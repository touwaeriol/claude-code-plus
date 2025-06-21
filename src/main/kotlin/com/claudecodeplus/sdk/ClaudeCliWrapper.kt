package com.claudecodeplus.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Claude CLI 包装器
 * 直接调用 claude 命令行工具，避免通过 Node.js 服务中转
 */
class ClaudeCliWrapper {
    private val logger = thisLogger()
    private val objectMapper = jacksonObjectMapper()
    
    data class QueryOptions(
        val model: String? = null,
        val maxTurns: Int? = null,
        val customSystemPrompt: String? = null,
        val appendSystemPrompt: String? = null,
        val permissionMode: String = "default",
        val allowedTools: List<String> = emptyList(),
        val disallowedTools: List<String> = emptyList(),
        val mcpServers: Map<String, Any>? = null,
        val cwd: String? = null,
        val continueConversation: Boolean = false,
        val resume: String? = null,
        val fallbackModel: String? = null
    )
    
    /**
     * 执行 Claude 查询
     * @param prompt 用户提示
     * @param options 查询选项
     * @return 响应消息流
     */
    fun query(prompt: String, options: QueryOptions = QueryOptions()): Flow<SDKMessage> = flow {
        val args = mutableListOf<String>()
        
        // 基础参数
        args.addAll(listOf("--output-format", "stream-json", "--verbose"))
        
        // 可选参数
        options.customSystemPrompt?.let { args.addAll(listOf("--system-prompt", it)) }
        options.appendSystemPrompt?.let { args.addAll(listOf("--append-system-prompt", it)) }
        options.maxTurns?.let { args.addAll(listOf("--max-turns", it.toString())) }
        options.model?.let { args.addAll(listOf("--model", it)) }
        
        if (options.continueConversation) {
            args.add("--continue")
        }
        
        options.resume?.let { args.addAll(listOf("--resume", it)) }
        
        if (options.allowedTools.isNotEmpty()) {
            args.addAll(listOf("--allowedTools", options.allowedTools.joinToString(",")))
        }
        
        if (options.disallowedTools.isNotEmpty()) {
            args.addAll(listOf("--disallowedTools", options.disallowedTools.joinToString(",")))
        }
        
        options.mcpServers?.let {
            args.addAll(listOf("--mcp-config", objectMapper.writeValueAsString(mapOf("mcpServers" to it))))
        }
        
        if (options.permissionMode != "default") {
            args.addAll(listOf("--permission-mode", options.permissionMode))
        }
        
        options.fallbackModel?.let {
            if (options.model == it) {
                throw IllegalArgumentException("Fallback model cannot be the same as the main model")
            }
            args.addAll(listOf("--fallback-model", it))
        }
        
        // 添加提示词
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt is required")
        }
        args.addAll(listOf("--print", prompt.trim()))
        
        // 构建进程
        val processBuilder = ProcessBuilder("claude", *args.toTypedArray())
        options.cwd?.let { processBuilder.directory(java.io.File(it)) }
        
        // 设置环境变量
        processBuilder.environment()["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kotlin"
        
        withContext(Dispatchers.IO) {
            val process = processBuilder.start()
            
            // 关闭输入流
            process.outputStream.close()
            
            // 读取输出
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            // 启动错误流读取
            val errorBuilder = StringBuilder()
            Thread {
                errorReader.useLines { lines ->
                    lines.forEach { line ->
                        errorBuilder.appendLine(line)
                        logger.warn("Claude CLI stderr: $line")
                    }
                }
            }.start()
            
            try {
                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (line.trim().isNotEmpty()) {
                            try {
                                val message = objectMapper.readValue<SDKMessage>(line)
                                emit(message)
                            } catch (e: Exception) {
                                // 解析失败，可能是非JSON输出
                                logger.warn("Failed to parse line: $line")
                            }
                        }
                    }
                }
                
                // 等待进程结束
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val errorMessage = errorBuilder.toString()
                    throw RuntimeException("Claude process exited with code $exitCode. Error: $errorMessage")
                }
            } finally {
                process.destroy()
            }
        }
    }
    
    /**
     * 执行简单的单轮对话
     */
    suspend fun chat(prompt: String, model: String? = null): String {
        val messages = mutableListOf<String>()
        
        query(prompt, QueryOptions(model = model)).collect { message ->
            when (message.type) {
                MessageType.TEXT -> {
                    messages.add(message.data.text ?: "")
                }
                MessageType.ERROR -> {
                    throw RuntimeException("Claude error: ${message.data.error}")
                }
                else -> {
                    // 忽略其他类型的消息
                }
            }
        }
        
        return messages.joinToString("")
    }
}