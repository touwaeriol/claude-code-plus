package com.claudecodeplus.sdk

import com.claudecodeplus.core.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Claude CLI 包装器 - 直接调用 claude 命令
 * 
 * 这是与 Claude CLI 交互的核心类，直接调用 `claude` 命令而不是通过 Node.js SDK。
 * 
 * 主要功能：
 * - 直接调用系统安装的 claude 命令
 * - 管理会话（新建、继续、恢复）
 * - 支持中断响应（通过 terminate() 方法）
 * - 实时监听进程输出并发送事件
 * 
 * 工作原理：
 * 1. 在指定工作目录中执行 claude 命令
 * 2. 通过命令行参数传递配置选项
 * 3. 监听 claude 进程的 stdout，解析返回的消息
 * 4. UI 组件监听事件来更新消息显示
 */
class ClaudeCliWrapper {
    companion object {
        private val logger = LoggerFactory.getLogger(ClaudeCliWrapper::class.java)
        
        /**
         * 获取 claude 命令的路径
         * 使用多层路径解析策略，查找系统中安装的 claude 命令
         * 
         * @return claude 命令的路径
         */
        private fun getClaudeCommandPath(): String {
            // 首先尝试从 PATH 中查找
            val pathCommand = findClaudeInPath()
            if (pathCommand != null) {
                logger.info("在 PATH 中找到 claude 命令: $pathCommand")
                return pathCommand
            }
            
            // 在常见位置查找
            val commonPaths = listOf(
                "/usr/local/bin/claude",
                "/opt/homebrew/bin/claude", 
                "/usr/bin/claude"
            )
            
            for (path in commonPaths) {
                val file = java.io.File(path)
                if (file.exists() && file.canExecute()) {
                    logger.info("在常见位置找到 claude 命令: $path")
                    return path
                }
            }
            
            throw IllegalStateException(
                "未找到 claude 命令。请确保已安装 Claude CLI 并在 PATH 中。\n" +
                "安装方法：curl -fsSL https://claude.ai/install.sh | sh"
            )
        }
        
        /**
         * 在 PATH 中查找 claude 命令
         */
        private fun findClaudeInPath(): String? {
            val pathEnv = System.getenv("PATH") ?: return null
            val pathSeparator = if (System.getProperty("os.name").lowercase().contains("windows")) ";" else ":"
            
            for (pathDir in pathEnv.split(pathSeparator)) {
                if (pathDir.isBlank()) continue
                
                val commandName = if (System.getProperty("os.name").lowercase().contains("windows")) "claude.cmd" else "claude"
                val claudeFile = java.io.File(pathDir, commandName)
                
                if (claudeFile.exists() && claudeFile.canExecute()) {
                    return claudeFile.absolutePath
                }
            }
            
            return null
        }
        
        /**
         * 检查 claude 命令是否可用
         */
        private fun isClaudeAvailable(): Boolean {
            return try {
                getClaudeCommandPath()
                true
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * 构建 claude 命令和工作目录
         * 在用户指定的 cwd 目录中执行 claude 命令
         * 
         * @param claudePath claude 命令路径
         * @param prompt 用户提示
         * @param options 查询选项
         * @return Pair(完整的命令列表, 工作目录)
         */
        private fun buildClaudeCommand(claudePath: String, prompt: String, options: QueryOptions): Pair<List<String>, java.io.File> {
            // 工作目录使用用户指定的 cwd
            val workingDir = java.io.File(options.cwd)
            if (!workingDir.exists()) {
                throw IllegalStateException("指定的工作目录不存在: ${options.cwd}")
            }
            if (!workingDir.isDirectory) {
                throw IllegalStateException("指定的路径不是目录: ${options.cwd}")
            }
            
            logger.debug("Claude 工作目录: ${workingDir.absolutePath}")
            
            val command = mutableListOf<String>()
            command.add(claudePath)
            
            // 添加固定参数
            command.add("--verbose")
            command.add("--print")
            command.add("--output-format")
            command.add("stream-json")
            command.add("--input-format")
            command.add("text")
            
            // 添加模型参数
            options.model?.let { model ->
                command.add("--model")
                command.add(model)
            }
            
            // 添加权限模式参数
            command.add("--permission-mode")
            command.add(options.permissionMode)
            
            // 添加跳过权限参数（基于复选框状态）
            if (options.skipPermissions) {
                command.add("--dangerously-skip-permissions")
            }
            
            // 添加会话相关参数
            options.resume?.let { sessionId ->
                if (sessionId.isNotBlank()) {
                    command.add("--resume")
                    command.add(sessionId)
                }
            }
            
            // 添加自定义系统提示
            options.customSystemPrompt?.let { systemPrompt ->
                command.add("--append-system-prompt")
                command.add(systemPrompt)
            }
            
            // 添加 MCP 配置
            options.mcpServers?.let { servers ->
                if (servers.isNotEmpty()) {
                    command.add("--mcp-config")
                    // 这里需要将 MCP 配置写入临时文件或使用其他方式传递
                }
            }
            
            // 添加用户提示作为最后一个参数
            command.add(prompt)
            
            return Pair(command, workingDir)
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
        BYPASS("bypass"),
        
        /** 自动接受编辑操作 */
        ACCEPT("accept"),
        
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
        // 指定新会话的 ID（用于预设会话标识符，便于监听）
        val sessionId: String? = null,
        
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
        // 工作目录（AI 执行命令和文件操作的基础路径）- 必须指定
        val cwd: String,
        
        /**
         * MCP（Model Context Protocol）服务器配置
         * 用于扩展 Claude 的能力，如添加数据库访问、API 调用等
         */
        val mcpServers: Map<String, Any>? = null,
        
        /**
         * 输出和格式控制
         */
        // 是否使用打印模式（非交互模式）
        val print: Boolean = true,
        // 输出格式（text, json, stream-json）
        val outputFormat: String? = null,
        // 输入格式（text, stream-json）
        val inputFormat: String? = null,
        // 详细模式
        val verbose: Boolean? = null,
        
        /**
         * 工具权限控制
         */
        // 允许的工具列表（如 "Bash(git:*) Edit"）
        val allowedTools: List<String>? = null,
        // 禁止的工具列表
        val disallowedTools: List<String>? = null,
        
        /**
         * 高级会话控制
         */
        // 继续最近的对话
        val continueRecent: Boolean = false,
        // 设置文件路径
        val settingsFile: String? = null,
        // 额外允许访问的目录
        val additionalDirectories: List<String>? = null,
        // 自动连接 IDE
        val autoConnectIde: Boolean = false,
        // 严格 MCP 配置模式
        val strictMcpConfig: Boolean = false,
        
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
     * 查询结果数据类
     */
    data class QueryResult(
        val sessionId: String?,
        val processId: Long,
        val success: Boolean,
        val errorMessage: String? = null,
        val assistantMessage: String? = null // Claude 的回复内容
    )
    
    /**
     * 会话类型枚举
     * 基于 Claudia 项目的二元会话策略
     */
    enum class SessionType {
        /** 新会话 - 不使用 --resume 参数 */
        NEW,
        /** 恢复会话 - 使用 --resume sessionId 参数 */
        RESUME
    }
    
    /**
     * 启动新会话（基于 Claudia 的 executeClaudeCode）
     * 不使用 --resume 参数，创建全新的会话
     * 
     * @param prompt 用户提示词
     * @param options 查询选项（resume 参数会被忽略）
     * @return 查询结果，包含新的 sessionId
     */
    suspend fun startNewSession(
        prompt: String, 
        options: QueryOptions,
        onStreamingMessage: ((String) -> Unit)? = null
    ): QueryResult {
        // 确保不使用 resume 参数
        val newOptions = options.copy(resume = null)
        return executeQuery(prompt, newOptions, SessionType.NEW, onStreamingMessage)
    }
    
    /**
     * 恢复会话（基于 Claudia 的 resumeClaudeCode）
     * 使用 --resume sessionId 参数延续之前的会话
     * 
     * @param sessionId 要恢复的会话 ID
     * @param prompt 用户提示词
     * @param options 查询选项
     * @return 查询结果
     */
    suspend fun resumeSession(
        sessionId: String, 
        prompt: String, 
        options: QueryOptions,
        onStreamingMessage: ((String) -> Unit)? = null
    ): QueryResult {
        // 确保使用 resume 参数
        val resumeOptions = options.copy(resume = sessionId)
        return executeQuery(prompt, resumeOptions, SessionType.RESUME, onStreamingMessage)
    }
    
    /**
     * 执行查询（向后兼容）
     * 根据 options.resume 参数自动判断是新会话还是恢复会话
     * 
     * @deprecated 建议使用 startNewSession 或 resumeSession 明确指定会话类型
     */
    @Deprecated("Use startNewSession or resumeSession for explicit session control", 
                ReplaceWith("if (options.resume != null) resumeSession(options.resume, prompt, options) else startNewSession(prompt, options)"))
    suspend fun query(prompt: String, options: QueryOptions): QueryResult {
        return if (options.resume != null) {
            resumeSession(options.resume, prompt, options)
        } else {
            startNewSession(prompt, options)
        }
    }
    
    /**
     * 内部执行查询方法
     * 实际执行 Claude CLI 调用的核心逻辑
     */
    private suspend fun executeQuery(
        prompt: String, 
        options: QueryOptions, 
        sessionType: SessionType,
        onStreamingMessage: ((String) -> Unit)? = null
    ): QueryResult {
        val requestId = options.requestId ?: System.currentTimeMillis().toString()
        
        // 验证输入
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }
        
        // 验证模型配置
        if (options.model != null && options.fallbackModel != null && options.model == options.fallbackModel) {
            throw IllegalArgumentException("Fallback model cannot be the same as the main model")
        }
        
        val sessionTypeStr = when(sessionType) {
            SessionType.NEW -> "新会话"
            SessionType.RESUME -> "恢复会话 (${options.resume})"
        }
        logger.info("🔵 [$requestId] 开始查询 (CLI - $sessionTypeStr): ${prompt.take(100)}...")
        
        return withContext(Dispatchers.IO) {
            
            // 检查协程状态
            coroutineContext.ensureActive()
            
            // 检查 claude 命令是否可用
            if (!isClaudeAvailable()) {
                throw IllegalStateException(
                    "Claude CLI 不可用。请确保已安装 Claude CLI。\n" +
                    "安装方法：curl -fsSL https://claude.ai/install.sh | sh\n" +
                    "或者访问 https://docs.anthropic.com/en/docs/claude-code 获取安装指南"
                )
            }
            
            // 获取 claude 命令路径
            val claudePath = getClaudeCommandPath()
            
            // 构建 claude 命令
            val (claudeCommand, claudeWorkingDir) = buildClaudeCommand(claudePath, prompt, options)
            logger.info("🔵 [$requestId] Claude 命令: ${claudeCommand.joinToString(" ")}")
            logger.info("🔵 [$requestId] Claude 工作目录: ${claudeWorkingDir.absolutePath}")
            
            val processBuilder = ProcessBuilder(claudeCommand)
            processBuilder.directory(claudeWorkingDir)
            
            // Claude 命令在用户指定的 cwd 中执行，继承用户的环境变量
            logger.info("🔵 [$requestId] Claude 将在工作目录中执行: ${options.cwd}")
            
            // 不设置额外的环境变量，让用户自己管理环境
            
            logger.info("🔵 [$requestId] 启动 Claude CLI 进程...")
            
            // 检查是否有旧进程还在运行
            currentProcess.get()?.let { oldProcess ->
                if (oldProcess.isAlive) {
                    logger.warn("🔴 [$requestId] 警告：检测到旧进程仍在运行，PID: ${oldProcess.pid()}")
                    logger.warn("🔴 [$requestId] 将强制终止旧进程")
                    try {
                        oldProcess.destroyForcibly()
                        logger.info("🔴 [$requestId] 已强制终止旧进程")
                    } catch (e: Exception) {
                        logger.error("🔴 [$requestId] 强制终止旧进程失败", e)
                    }
                }
            }
            
            val process = processBuilder.start()
            currentProcess.set(process)
            logger.info("🔵 [$requestId] Claude CLI 进程已启动，PID: ${process.pid()}")
            
            // 启动输出监听协程
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            // 监听变量，用于跟踪 Claude CLI 返回的会话ID和助手回复
            var detectedSessionId: String? = null
            val assistantResponseBuilder = StringBuilder()
            
            // 启动 stdout 监听 - 处理 Claude CLI 返回的消息
            scope.launch {
                try {
                    logger.info("🔵 [$requestId] 开始监听 Claude CLI stdout...")
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { currentLine ->
                                if (currentLine.isNotBlank()) {
                                    try {
                                        // 尝试解析 Claude CLI 返回的输出
                                        logger.info("🔵 [$requestId] Claude CLI 输出: $currentLine")
                                        processOutputLine(currentLine)
                                        // 收集助手回复内容
                                        assistantResponseBuilder.append(currentLine).append("\n")
                                        // 调用流式回调（实时更新UI）
                                        onStreamingMessage?.invoke(assistantResponseBuilder.toString())
                                    } catch (e: Exception) {
                                        logger.info("🔵 [$requestId] Claude CLI 输出: $currentLine")
                                        processOutputLine(currentLine)
                                    }
                                }
                            }
                        }
                    }
                    logger.info("🔵 [$requestId] Claude CLI stdout 流结束")
                } catch (e: Exception) {
                    if (e.message?.contains("Stream closed") != true) {
                        logger.error("🔴 [$requestId] Error reading Claude CLI stdout: ${e.message}", e)
                    }
                }
            }
            
            // 启动 stderr 监听
            scope.launch {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (line.isNotBlank()) {
                                logger.warn("🔴 [$requestId] Claude CLI stderr: $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("Stream closed") != true) {
                        logger.error("🔴 [$requestId] Error reading Claude CLI stderr: ${e.message}", e)
                    }
                }
            }
            
            try {
                // 等待进程完成
                val exitCode = process.waitFor()
                logger.info("🔵 [$requestId] Claude CLI 进程退出，退出码: $exitCode")
                
                // 优先使用从 CLI 返回中检测到的 sessionId，其次使用用户指定的
                val finalSessionId = detectedSessionId ?: options.sessionId ?: options.resume
                
                QueryResult(
                    sessionId = finalSessionId,
                    processId = process.pid(),
                    success = exitCode == 0,
                    errorMessage = if (exitCode != 0) "Claude CLI 退出码: $exitCode" else null,
                    assistantMessage = if (exitCode == 0 && assistantResponseBuilder.isNotEmpty()) assistantResponseBuilder.toString() else null
                )
            } catch (e: Exception) {
                logger.error("🔴 [$requestId] Claude CLI 执行失败", e)
                val finalSessionId = detectedSessionId ?: options.sessionId ?: options.resume
                QueryResult(
                    sessionId = finalSessionId,
                    processId = process.pid(),
                    success = false,
                    errorMessage = e.message,
                    assistantMessage = null
                )
            } finally {
                currentProcess.set(null)
                scope.cancel() // 清理监听协程
            }
        }
    }
    
    /**
     * 终止当前进程
     */
    fun terminate() {
        currentProcess.get()?.let { process ->
            logger.info("🔴 正在终止进程，PID: ${process.pid()}")
            try {
                process.destroyForcibly()
                logger.info("🔴 进程已终止")
            } catch (e: Exception) {
                logger.error("🔴 终止进程失败: ${e.message}", e)
            } finally {
                currentProcess.set(null)
            }
        } ?: run {
            logger.info("🔴 没有活动的进程需要终止")
        }
    }
    
    /**
     * 检查进程是否还在运行
     */
    fun isProcessAlive(): Boolean {
        return currentProcess.get()?.isAlive == true
    }
    
    /**
     * 输出行回调接口
     */
    private var outputLineCallback: ((String) -> Unit)? = null
    
    /**
     * 设置输出行回调
     */
    fun setOutputLineCallback(callback: (String) -> Unit) {
        this.outputLineCallback = callback
    }
    
    /**
     * 处理单行输出
     * 可以在此处解析JSONL格式的输出或发送到事件总线
     */
    private fun processOutputLine(line: String) {
        try {
            // 首先调用回调函数（如果设置了）
            outputLineCallback?.invoke(line)
            
            // 尝试解析JSONL格式
            if (line.trim().startsWith("{") && line.trim().endsWith("}")) {
                val json = Json.parseToJsonElement(line.trim())
                if (json is JsonObject) {
                    val type = json["type"]?.jsonPrimitive?.content
                    logger.info("📡 解析到消息类型: $type")
                }
            }
        } catch (e: Exception) {
            logger.debug("解析输出行失败（这是正常的，因为不是所有行都是JSON）: ${e.message}")
        }
    }
    
    /**
     * 检查 Claude CLI 是否可用
     * 验证 claude 命令是否正常工作
     */
    suspend fun isClaudeCodeSdkAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查 claude 命令
            if (!isClaudeAvailable()) {
                logger.warn("Claude CLI 不可用")
                return@withContext false
            }
            
            // 获取 claude 命令路径
            val claudePath = getClaudeCommandPath()
            logger.info("找到 claude 命令: $claudePath")
            
            // 测试运行简单的 claude 命令（--help 或 --version）
            val testCommand = listOf(claudePath, "--version")
            val processBuilder = ProcessBuilder(testCommand)
            processBuilder.directory(java.io.File(System.getProperty("user.dir")))
            val process = processBuilder.start()
            
            // 等待短时间或直到进程结束
            val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                logger.warn("Claude CLI 测试超时")
                return@withContext false
            }
            
            val exitCode = process.exitValue()
            logger.info("Claude CLI 测试结果: 退出码 $exitCode")
            
            // 只要能正常启动就认为可用（退出码0表示成功）
            exitCode == 0
            
        } catch (e: Exception) {
            logger.warn("Claude CLI 不可用: ${e.message}")
            false
        }
    }
}