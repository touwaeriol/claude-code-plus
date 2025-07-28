package com.claudecodeplus.sdk

import com.claudecodeplus.core.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.serialization.json.*

/**
 * Claude CLI 包装器 - 核心组件
 * 
 * 这是与 Claude CLI 交互的核心类，负责将 Kotlin 代码与 Claude 命令行工具连接。
 * 使用 kotlinx.serialization 解析 Claude CLI 输出。
 * 
 * 主要功能：
 * - 自动查找和调用 Claude CLI 命令
 * - 处理流式响应，支持实时显示 AI 生成的内容
 * - 管理会话（新建、继续、恢复）
 * - 支持中断响应（通过 terminate() 方法）
 * - 处理工具调用（文件操作、代码执行等）
 * 
 * 工作原理：
 * 1. 通过 ProcessBuilder 直接调用系统中的 claude CLI 命令
 * 2. 使用 --output-format stream-json 参数获取流式 JSON 输出
 * 3. 使用 kotlinx.serialization 解析 JSON 流并转换为 Kotlin Flow
 * 4. 通过环境变量 CLAUDE_CODE_ENTRYPOINT 标识调用来源
 * 
 * 使用示例：
 * ```kotlin
 * val wrapper = ClaudeCliWrapper()
 * val flow = wrapper.query(
 *     prompt = "请帮我写一个快速排序算法",
 *     options = QueryOptions(model = "opus")
 * )
 * flow.collect { message ->
 *     when (message.type) {
 *         MessageType.TEXT -> println(message.data.text)
 *         MessageType.END -> println("完成")
 *     }
 * }
 * ```
 */
class ClaudeCliWrapper {
    companion object {
        private val logger = LoggerFactory.getLogger(ClaudeCliWrapper::class.java)
        
        /**
         * 查找 Claude 命令的路径
         * 
         * 按以下优先级查找：
         * 1. 用户提供的自定义命令路径
         * 2. 系统特定的默认路径（Windows/Unix）
         * 3. 使用系统命令（where/which）动态查找
         * 
         * @param customCommand 自定义的 claude 命令路径（可选）
         * @return Claude 命令的完整路径
         */
        private fun findClaudeCommand(customCommand: String? = null): String {
            // 如果提供了自定义命令，直接使用
            if (!customCommand.isNullOrBlank()) {
                logger.info("Using custom claude command: $customCommand")
                return customCommand
            }
            
            // Windows 上使用 claude.cmd，其他平台使用 claude
            val osName = System.getProperty("os.name").lowercase()
            return if (osName.contains("windows")) {
                logger.info("Using 'claude.cmd' command on Windows")
                "claude.cmd"
            } else {
                logger.info("Using 'claude' command")
                "claude"
            }
        }
        
        /**
         * 检查命令是否可用
         * 
         * 通过尝试执行 “claude --version” 来验证命令是否存在且可执行
         * 
         * @param command 要检查的命令
         * @return 如果命令可用返回 true，否则返回 false
         */
        private fun isCommandAvailable(command: String): Boolean {
            return try {
                val process = ProcessBuilder(command, "--version").start()
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * 存储当前运行的进程引用
     * 使用 AtomicReference 确保线程安全
     * 主要用于实现中断功能（terminate 方法）
     */
    private val currentProcess = AtomicReference<Process?>(null)
    
    /**
     * 权限模式枚举
     * 
     * 定义了 Claude CLI 支持的不同权限模式：
     * - DEFAULT: 正常权限检查，每次操作都需要用户确认
     * - BYPASS_PERMISSIONS: 跳过所有权限检查，适用于信任环境
     * - ACCEPT_EDITS: 自动接受编辑操作，但其他操作仍需确认
     * - PLAN: 计划模式，只生成计划不执行实际操作
     */
    enum class PermissionMode(val cliValue: String) {
        /** 正常权限检查（默认） */
        DEFAULT("default"),
        
        /** 跳过权限检查 */
        BYPASS_PERMISSIONS("bypassPermissions"),
        
        /** 自动接受编辑操作 */
        ACCEPT_EDITS("acceptEdits"),
        
        /** 计划模式 */
        PLAN("plan")
    }
    
    /**
     * Claude CLI 查询选项
     * 
     * 封装了所有可用的 Claude CLI 参数，用于控制 AI 的行为
     */
    data class QueryOptions(
        /**
         * AI 模型配置
         */
        // 主模型（如 "opus", "sonnet" 等）
        val model: String? = null,
        // 备用模型（当主模型不可用时使用）
        val fallbackModel: String? = null,
        
        /**
         * 对话控制参数
         */
        // 最大对话轮数（限制多轮对话的次数）
        val maxTurns: Int? = null,
        // 自定义系统提示词（追加到默认系统提示词后）
        val customSystemPrompt: String? = null,
        
        /**
         * 会话管理
         */
        // 要恢复的会话 ID（用于继续之前的对话）
        val resume: String? = null,
        
        /**
         * 权限设置
         */
        // 权限模式（见 PermissionMode 枚举）
        val permissionMode: String = PermissionMode.DEFAULT.cliValue,
        // 是否跳过所有权限检查（快速操作，慎用）
        val skipPermissions: Boolean = true,
        
        /**
         * 环境配置
         */
        // 工作目录（AI 执行命令和文件操作的基础路径）
        val cwd: String? = null,
        
        /**
         * MCP（Model Context Protocol）服务器配置
         * 用于扩展 Claude 的能力，如添加数据库访问、API 调用等
         */
        val mcpServers: Map<String, Any>? = null,
        
        /**
         * 调试和统计
         */
        // 启用调试模式（输出更多日志信息）
        val debug: Boolean = false,
        // 显示统计信息（token 使用量等）
        val showStats: Boolean = false,
        // 请求 ID（用于跟踪和调试）
        val requestId: String? = null,
        
        /**
         * 高级配置
         */
        // 自定义 claude 命令路径（默认自动查找）
        val customCommand: String? = null
    )
    
    /**
     * 执行查询，返回流式响应
     */
    suspend fun query(prompt: String, options: QueryOptions = QueryOptions()): Flow<SDKMessage> = flow {
        val requestId = options.requestId ?: System.currentTimeMillis().toString()
        
        // 验证输入
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }
        
        // 验证模型配置
        if (options.model != null && options.fallbackModel != null && options.model == options.fallbackModel) {
            throw IllegalArgumentException("Fallback model cannot be the same as the main model")
        }
        
        logger.info("🔵 [$requestId] 开始查询: ${prompt.take(100)}...")
        
        // 检查协程状态
        coroutineContext.ensureActive()
        
        // 构建命令行参数
        val args = mutableListOf<String>()
        
        // 添加 --print 参数以获取输出（非交互模式）
        args.add("--print")
        
        // 根据选项添加 --dangerously-skip-permissions 参数
        if (options.skipPermissions) {
            args.add("--dangerously-skip-permissions")
        }
        
        // 权限模式
        args.addAll(listOf("--permission-mode", options.permissionMode))
        
        // 模型配置
        options.model?.let { args.addAll(listOf("--model", it)) }
        options.fallbackModel?.let { args.addAll(listOf("--fallback-model", it)) }
        
        // 会话管理
        if (options.resume != null && options.resume.isNotBlank()) {
            args.addAll(listOf("--resume", options.resume))
        }
        
        // 对话控制
        options.maxTurns?.let { args.addAll(listOf("--max-turns", it.toString())) }
        
        // 系统提示词
        options.customSystemPrompt?.let { args.addAll(listOf("--append-system-prompt", it)) }
        
        // MCP 服务器配置
        options.mcpServers?.let { servers ->
            val json = Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                servers.forEach { (name, config) ->
                    put(name, Json.encodeToJsonElement(config))
                }
            })
            args.addAll(listOf("--mcp-config", json))
        }
        
        // 调试选项
        if (options.debug) args.add("--debug")
        if (options.showStats) args.add("--show-stats")
        
        // 输出格式
        args.addAll(listOf("--output-format", "stream-json", "--verbose"))
        
        // 添加用户提示
        args.add(prompt)
        
        logger.info("🔵 [$requestId] 构建参数: ${args.joinToString(" ")}")
        
        val claudeCommand = withContext(Dispatchers.IO) {
            findClaudeCommand(options.customCommand)
        }
        
        logger.info("🔵 [$requestId] 完整命令行: $claudeCommand ${args.joinToString(" ")}")
        
        // 直接执行命令，不使用 shell
        val finalCommand = listOf(claudeCommand) + args
        
        val processBuilder = ProcessBuilder(finalCommand)
        options.cwd?.let { 
            processBuilder.directory(java.io.File(it))
            logger.info("🔵 [$requestId] 工作目录: $it")
        }
        
        // 设置环境变量
        val env = processBuilder.environment()
        env["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kotlin"
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "en_US.UTF-8"
        env["PYTHONIOENCODING"] = "utf-8"
        
        // 确保 PATH 环境变量被继承（ProcessBuilder 默认会继承，但明确设置更安全）
        val currentPath = System.getenv("PATH")
        if (currentPath != null) {
            env["PATH"] = currentPath
        }
        
        // Windows 特定的编码设置
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            env["CHCP"] = "65001"  // UTF-8 代码页
        }
        
        logger.info("🔵 [$requestId] 启动 Claude CLI 进程...")
        val process = processBuilder.start()
        currentProcess.set(process)
        logger.info("🔵 [$requestId] 进程已启动，PID: ${process.pid()}")
        
        // 关闭输入流
        process.outputStream.close()
        
        // 读取输出，指定 UTF-8 编码
        val reader = BufferedReader(InputStreamReader(process.inputStream, "UTF-8"))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream, "UTF-8"))
        logger.info("🔵 [$requestId] 开始读取输出流...")
        
        // 启动错误流读取
        val errorBuilder = StringBuilder()
        Thread {
            errorReader.useLines { lines ->
                lines.forEach { line ->
                    errorBuilder.appendLine(line)
                    when {
                        line.contains("[DEP0190]") || line.contains("DeprecationWarning") -> {
                            logger.debug("Node.js deprecation warning: $line")
                        }
                        line.contains("error", ignoreCase = true) -> {
                            logger.error("Claude CLI error: $line", RuntimeException("CLI Error"))
                        }
                        else -> {
                            logger.warn("Claude CLI stderr: $line")
                        }
                    }
                }
            }
        }.start()
        
        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 检查协程是否被取消
                    try {
                        coroutineContext.ensureActive()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        logger.info("Coroutine cancelled, terminating process...")
                        terminate()
                        throw e
                    }
                    
                    if (line.trim().isNotEmpty()) {
                        try {
                            // 清理ANSI序列并记录日志
                            val cleanLine = AnsiProcessor.cleanAnsiSequences(line)
                            logger.info("🔵 [$requestId] 收到行: ${cleanLine.take(200)}")
                            
                            // 跳过空行和非JSON行
                            if (cleanLine.trim().isEmpty() || !cleanLine.trim().startsWith("{")) {
                                logger.info("🔵 [$requestId] 跳过非JSON行")
                                return@forEach
                            }
                            
                            // 使用新的序列化解析消息
                            val message = parseClaudeMessage(cleanLine)
                            if (message != null) {
                                processMessage(message)?.let { sdkMessage ->
                                    emit(sdkMessage)
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("解析消息失败: ${e.message}")
                        }
                    }
                }
            }
            
            // 等待进程结束
            val exitCode = process.waitFor()
            logger.info("🔵 [$requestId] 进程退出，退出码: $exitCode")
            
            // 检查是否有错误消息（排除 Node.js 的弃用警告）
            val errorMsg = errorBuilder.toString()
            val hasRealError = errorMsg.lines().any { line ->
                line.isNotBlank() && 
                !line.contains("[DEP0190]") && 
                !line.contains("DeprecationWarning") &&
                !line.contains("--trace-deprecation")
            }
            
            // 只有在有真正的错误时才发送错误消息
            if (exitCode != 0 && hasRealError) {
                logger.error("Claude CLI 执行失败: $errorMsg", RuntimeException("CLI Execution Failed"))
                emit(SDKMessage(
                    type = MessageType.ERROR,
                    data = MessageData(error = "Claude CLI 执行失败: $errorMsg")
                ))
            } else {
                // 即使退出码不是 0，但如果只是弃用警告，仍然视为成功
                emit(SDKMessage(
                    type = MessageType.END,
                    data = MessageData()
                ))
            }
            
        } catch (e: Exception) {
            logger.error("执行查询时发生错误: ${e.message}", e)
            emit(SDKMessage(
                type = MessageType.ERROR,
                data = MessageData(error = e.message)
            ))
            throw e
        } finally {
            process.destroy()
            currentProcess.set(null)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 处理 Claude 消息并转换为 SDK 消息
     */
    private suspend fun processMessage(message: ClaudeMessage): SDKMessage? {
        return when (message) {
            is AssistantMessage -> {
                processAssistantMessage(message)
            }
            is UserMessage -> {
                processUserMessage(message)
            }
            is SystemMessage -> {
                // 系统消息可以包含会话ID
                SDKMessage(
                    type = MessageType.START,
                    data = MessageData(sessionId = message.sessionId)
                )
            }
            is ResultMessage -> {
                // 结果消息表示会话结束
                SDKMessage(
                    type = MessageType.END,
                    data = MessageData(text = message.result)
                )
            }
            is SummaryMessage -> {
                // 摘要消息通常不需要转发给UI
                null
            }
        }
    }
    
    /**
     * 处理助手消息
     */
    private suspend fun processAssistantMessage(message: AssistantMessage): SDKMessage? {
        val content = message.message?.content ?: return null
        
        // 遍历内容块，逐个发送
        for (block in content) {
            when (block) {
                is TextBlock -> {
                    if (block.text.isNotEmpty()) {
                        return SDKMessage(
                            type = MessageType.TEXT,
                            data = MessageData(text = block.text)
                        )
                    }
                }
                is ToolUseBlock -> {
                    return SDKMessage(
                        type = MessageType.TOOL_USE,
                        data = MessageData(
                            toolName = block.name,
                            toolCallId = block.id,
                            toolInput = block.input
                        )
                    )
                }
                else -> {
                    // 忽略其他类型
                }
            }
        }
        
        return null
    }
    
    /**
     * 处理用户消息（主要是工具结果）
     */
    private suspend fun processUserMessage(message: UserMessage): SDKMessage? {
        val messageData = message.message ?: return null
        
        when (val content = messageData.content) {
            is ContentOrList.ListContent -> {
                // 遍历内容块
                for (block in content.value) {
                    if (block is ToolResultBlock) {
                        val resultContent = when (block.content) {
                            is ContentOrString.StringValue -> block.content.value
                            is ContentOrString.JsonValue -> block.content.value.toString()
                            null -> null
                        }
                        
                        return SDKMessage(
                            type = MessageType.TOOL_RESULT,
                            data = MessageData(
                                toolCallId = block.toolUseId,
                                toolResult = resultContent,
                                error = if (block.isError == true) resultContent else null
                            )
                        )
                    }
                }
            }
            else -> {
                // 忽略简单文本内容
            }
        }
        
        return null
    }
    
    /**
     * 终止当前响应
     */
    fun terminate() {
        currentProcess.get()?.let { process ->
            logger.info("正在终止进程...")
            try {
                process.destroy()
                logger.info("已发送终止信号")
            } catch (e: Exception) {
                logger.error("终止进程失败: ${e.message}", e)
                try {
                    process.destroyForcibly()
                    logger.info("已强制终止进程")
                } catch (e2: Exception) {
                    logger.error("强制终止进程失败: ${e2.message}", e2)
                }
            } finally {
                currentProcess.set(null)  // 立即清理引用
            }
        }
    }
    
    /**
     * 检查进程是否还在运行
     */
    fun isProcessAlive(): Boolean {
        return currentProcess.get()?.isAlive == true
    }
    
    /**
     * 检查 Claude CLI 是否可用
     */
    suspend fun isClaudeCliAvailable(customCommand: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val claudeCommand = findClaudeCommand(customCommand)
            val process = ProcessBuilder(claudeCommand, "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val output = process.inputStream.bufferedReader().readText()
                logger.info("Claude CLI 版本: $output")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warn("Claude CLI 不可用: ${e.message}")
            false
        }
    }
}