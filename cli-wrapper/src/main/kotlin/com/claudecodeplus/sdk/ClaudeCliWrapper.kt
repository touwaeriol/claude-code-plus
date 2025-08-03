package com.claudecodeplus.sdk

import com.claudecodeplus.core.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.*

/**
 * Claude CLI 包装器 - 简化版本
 * 
 * 这是与 Claude CLI 交互的核心类，专注于CLI执行。
 * 不再解析CLI输出，而是由统一的会话管理API处理会话文件监听。
 * 
 * 主要功能：
 * - 自动查找和调用 Claude CLI 命令
 * - 管理会话（新建、继续、恢复）
 * - 支持中断响应（通过 terminate() 方法）
 * - 返回会话ID供文件监听使用
 * 
 * 工作原理：
 * 1. 通过 ProcessBuilder 直接调用系统中的 claude CLI 命令
 * 2. 启动 Claude CLI 进程并返回会话ID
 * 3. 会话消息通过文件监听服务获取，不再解析CLI输出
 * 4. 通过环境变量 CLAUDE_CODE_ENTRYPOINT 标识调用来源
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
        /**
         * 构建跨平台的 Claude 命令
         * 
         * @param args Claude 命令的参数
         * @return 完整的命令列表，包含平台特定的包装器
         */
        private fun buildClaudeCommand(args: List<String>): List<String> {
            val osName = System.getProperty("os.name").lowercase()
            return if (osName.contains("windows")) {
                // Windows: 通过 cmd /c 执行，这样可以正确找到 .cmd 文件
                listOf("cmd", "/c", "claude") + args
            } else {
                // Unix/Linux/macOS: 直接执行
                listOf("claude") + args
            }
        }
        
        /**
         * 检查 Claude 命令是否可用
         * 
         * 通过尝试执行 "claude --version" 来验证命令是否存在且可执行
         * 
         * @return 如果命令可用返回 true，否则返回 false
         */
        private fun isClaudeCommandAvailable(): Boolean {
            return try {
                val commandList = buildClaudeCommand(listOf("--version"))
                val process = ProcessBuilder(commandList).start()
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
        // 工作目录（AI 执行命令和文件操作的基础路径）
        val cwd: String? = null,
        
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
        val errorMessage: String? = null
    )
    
    /**
     * 执行查询，返回简化的结果
     * 只返回进程状态和会话ID，不再解析输出流
     */
    suspend fun query(prompt: String, options: QueryOptions = QueryOptions()): QueryResult {
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
        
        return withContext(Dispatchers.IO) {
            
            // 检查协程状态
            coroutineContext.ensureActive()
            
            // 构建命令行参数
            val args = mutableListOf<String>()
            
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
            
            // 新会话ID指定
            if (options.sessionId != null && options.sessionId.isNotBlank()) {
                args.addAll(listOf("--session-id", options.sessionId))
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
            
            // 输出和格式控制
            if (options.print) {
                args.add("--print")
            }
            options.outputFormat?.let { args.addAll(listOf("--output-format", it)) }
            options.inputFormat?.let { args.addAll(listOf("--input-format", it)) }
            options.verbose?.let { 
                if (it) args.add("--verbose")
            }
            
            // 工具权限控制
            options.allowedTools?.let { tools ->
                if (tools.isNotEmpty()) {
                    args.addAll(listOf("--allowedTools", tools.joinToString(" ")))
                }
            }
            options.disallowedTools?.let { tools ->
                if (tools.isNotEmpty()) {
                    args.addAll(listOf("--disallowedTools", tools.joinToString(" ")))
                }
            }
            
            // 高级会话控制
            if (options.continueRecent) {
                args.add("--continue")
            }
            options.settingsFile?.let { args.addAll(listOf("--settings", it)) }
            options.additionalDirectories?.let { dirs ->
                if (dirs.isNotEmpty()) {
                    args.addAll(listOf("--add-dir") + dirs)
                }
            }
            if (options.autoConnectIde) {
                args.add("--ide")
            }
            if (options.strictMcpConfig) {
                args.add("--strict-mcp-config")
            }
            
            // 调试选项
            if (options.debug) args.add("--debug")
            if (options.showStats) args.add("--show-stats")
            
            // 添加用户提示
            args.add(prompt)
            
            logger.info("🔵 [$requestId] 构建参数: ${args.joinToString(" ")}")
            
            // 构建跨平台的完整命令
            val finalCommand = buildClaudeCommand(args)
            
            logger.info("🔵 [$requestId] 完整命令行: ${finalCommand.joinToString(" ")}")
            
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
            logger.info("🔵 [$requestId] 进程已启动，PID: ${process.pid()}")
            
            // 简化版本：只启动进程，不解析输出
            // 会话消息通过文件监听获取
            logger.info("🔵 [$requestId] Claude CLI 进程已启动，会话消息将通过文件监听获取")
            
            // 提取会话ID（优先使用新指定的 sessionId，其次是恢复的会话ID）
            val sessionId = options.sessionId ?: options.resume
            
            try {
                // 等待进程完成
                val exitCode = process.waitFor()
                logger.info("🔵 [$requestId] 进程退出，退出码: $exitCode")
                
                QueryResult(
                    sessionId = sessionId,
                    processId = process.pid(),
                    success = exitCode == 0,
                    errorMessage = if (exitCode != 0) "Claude CLI 退出码: $exitCode" else null
                )
            } catch (e: Exception) {
                logger.error("🔴 [$requestId] 进程执行失败", e)
                QueryResult(
                    sessionId = sessionId,
                    processId = process.pid(),
                    success = false,
                    errorMessage = e.message
                )
            } finally {
                currentProcess.set(null)
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
     * 检查 Claude CLI 是否可用
     */
    suspend fun isClaudeCliAvailable(customCommand: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val commandList = buildClaudeCommand(listOf("--version"))
            val process = ProcessBuilder(commandList).start()
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