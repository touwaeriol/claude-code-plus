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
 * Claude Code SDK 包装器 - 基于 Node.js SDK
 * 
 * 这是与 Claude Code SDK 交互的核心类，通过 Node.js 桥接层使用官方 @anthropic-ai/claude-code SDK。
 * 相比直接调用 CLI，SDK 提供了更好的类型安全和错误处理。
 * 
 * 主要功能：
 * - 通过 Node.js 脚本调用 Claude Code SDK
 * - 管理会话（新建、继续、恢复）
 * - 支持中断响应（通过 terminate() 方法）
 * - 实时监听进程输出并发送事件
 * 
 * 工作原理：
 * 1. 启动 Node.js 脚本 claude-sdk-wrapper.js
 * 2. 通过 JSON 传递参数给 Node.js 脚本
 * 3. Node.js 脚本使用 @anthropic-ai/claude-code SDK 执行查询
 * 4. 监听 Node.js 进程的 stdout，解析返回的 JSON 消息
 * 5. UI 组件监听事件来更新消息显示
 */
class ClaudeCliWrapper {
    companion object {
        private val logger = LoggerFactory.getLogger(ClaudeCliWrapper::class.java)
        
        /**
         * 获取 Node.js 脚本的完整路径
         * 使用多层路径解析策略，支持各种部署场景
         * 
         * @return Node.js 脚本的绝对路径
         */
        private fun getNodeScriptPath(): String {
            // 优先检查是否为打包模式
            if (isPackagedMode()) {
                return getScriptPathForPackagedMode()
            }
            
            return getScriptPathForDevelopmentMode()
        }
        
        /**
         * 检查是否为打包模式
         */
        private fun isPackagedMode(): Boolean {
            return try {
                val codeSource = ClaudeCliWrapper::class.java.protectionDomain?.codeSource
                val location = codeSource?.location?.toString()
                // 如果是从 JAR 文件运行且无法找到开发环境的特征文件，则认为是打包模式
                location?.endsWith(".jar") == true && !findDevelopmentModeScript().exists()
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * 打包模式下获取脚本路径
         */
        private fun getScriptPathForPackagedMode(): String {
            logger.info("检测到打包模式，使用打包部署策略")
            
            try {
                val (scriptFile, workingDir) = PackagedDeploymentStrategy.ensureNodejsRuntime()
                logger.info("打包模式脚本路径: ${scriptFile.absolutePath}")
                logger.info("打包模式工作目录: ${workingDir.absolutePath}")
                return scriptFile.absolutePath
            } catch (e: Exception) {
                logger.error("打包模式脚本初始化失败", e)
                throw IllegalStateException("打包模式下无法初始化 Node.js 运行时环境: ${e.message}", e)
            }
        }
        
        /**
         * 开发模式下获取脚本路径
         */
        private fun getScriptPathForDevelopmentMode(): String {
            logger.debug("使用开发模式脚本查找策略")
            
            // 策略 1: 基于类加载器位置推断项目根目录
            val projectRoot = findProjectRoot()
            if (projectRoot != null) {
                val scriptInProject = java.io.File(projectRoot, "cli-wrapper/claude-sdk-wrapper.js")
                if (scriptInProject.exists()) {
                    logger.debug("通过项目根目录找到脚本: ${scriptInProject.absolutePath}")
                    return scriptInProject.absolutePath
                }
            }
            
            // 策略 2: 基于当前工作目录向上查找
            val scriptByTraversal = findScriptByDirectoryTraversal()
            if (scriptByTraversal != null) {
                logger.debug("通过目录遍历找到脚本: ${scriptByTraversal.absolutePath}")
                return scriptByTraversal.absolutePath
            }
            
            // 策略 3: 在常见位置查找
            val commonLocations = listOf(
                // 当前目录及其子目录
                java.io.File(System.getProperty("user.dir"), "cli-wrapper/claude-sdk-wrapper.js"),
                java.io.File(System.getProperty("user.dir"), "claude-sdk-wrapper.js"),
                // JAR 同级目录
                getScriptFromJarLocation()
            ).filterNotNull()
            
            val existingScript = commonLocations.firstOrNull { it.exists() }
            if (existingScript != null) {
                logger.debug("在常见位置找到脚本: ${existingScript.absolutePath}")
                return existingScript.absolutePath
            }
            
            // 所有策略失败，抛出详细错误
            val attemptedPaths = (listOfNotNull(projectRoot?.let { java.io.File(it, "cli-wrapper/claude-sdk-wrapper.js") }) +
                                 listOfNotNull(scriptByTraversal) +
                                 commonLocations).map { it.absolutePath }
                                 
            throw IllegalStateException(
                "Node.js 脚本未找到。\n" +
                "当前工作目录: ${System.getProperty("user.dir")}\n" +
                "项目根目录: $projectRoot\n" +
                "尝试位置: $attemptedPaths\n" +
                "请确保 claude-sdk-wrapper.js 存在于 cli-wrapper 目录中并运行 'npm install'"
            )
        }
        
        /**
         * 查找项目根目录
         * 通过类加载器位置或特征文件推断
         */
        private fun findProjectRoot(): java.io.File? {
            // 方法 1: 从类加载器位置推断
            try {
                val codeSource = ClaudeCliWrapper::class.java.protectionDomain?.codeSource
                val location = codeSource?.location?.toURI()?.path
                if (location != null) {
                    val file = java.io.File(location)
                    var dir = if (file.isFile) file.parentFile else file
                    
                    // 向上查找包含 cli-wrapper 的目录
                    repeat(5) { // 最多向上查找5层
                        if (dir != null && java.io.File(dir, "cli-wrapper").exists()) {
                            return dir
                        }
                        dir = dir?.parentFile
                    }
                }
            } catch (e: Exception) {
                logger.debug("从类加载器位置推断项目根目录失败: ${e.message}")
            }
            
            // 方法 2: 从当前目录向上查找特征文件
            var currentDir: java.io.File? = java.io.File(System.getProperty("user.dir"))
            repeat(10) { // 最多向上查找10层
                if (currentDir == null) return@repeat
                // 查找项目特征: cli-wrapper 目录 + build.gradle.kts/settings.gradle.kts
                if (java.io.File(currentDir, "cli-wrapper").exists() && 
                    (java.io.File(currentDir, "build.gradle.kts").exists() || 
                     java.io.File(currentDir, "settings.gradle.kts").exists())) {
                    return currentDir
                }
                currentDir = currentDir.parentFile
            }
            
            return null
        }
        
        /**
         * 通过目录遍历查找脚本
         */
        private fun findScriptByDirectoryTraversal(): java.io.File? {
            var currentDir: java.io.File? = java.io.File(System.getProperty("user.dir"))
            
            // 向上查找，直到找到 cli-wrapper/claude-sdk-wrapper.js
            repeat(10) {
                if (currentDir == null) return@repeat
                val scriptFile = java.io.File(currentDir, "cli-wrapper/claude-sdk-wrapper.js")
                if (scriptFile.exists()) {
                    return scriptFile
                }
                currentDir = currentDir.parentFile
            }
            
            return null
        }
        
        /**
         * 从资源文件中获取脚本路径
         * 不再使用临时文件，因为会导致 node_modules 路径问题
         */
        private fun getScriptFromResources(): java.io.File? {
            // 不使用临时文件，因为会导致 node_modules 路径问题
            logger.debug("跳过从资源加载脚本，因为会导致 node_modules 路径问题")
            return null
        }
        
        /**
         * 查找开发模式下的脚本文件
         */
        private fun findDevelopmentModeScript(): java.io.File {
            val possiblePaths = listOf(
                java.io.File(System.getProperty("user.dir"), "cli-wrapper/claude-sdk-wrapper.js"),
                java.io.File(System.getProperty("user.dir"), "claude-sdk-wrapper.js")
            )
            
            return possiblePaths.firstOrNull { it.exists() } 
                ?: java.io.File("nonexistent") // 返回不存在的文件作为标识
        }
        
        /**
         * 从 jar 位置获取脚本路径
         */
        private fun getScriptFromJarLocation(): java.io.File? {
            return try {
                val codeSource = ClaudeCliWrapper::class.java.protectionDomain?.codeSource
                val jarLocation = codeSource?.location?.toURI()?.path
                if (jarLocation != null) {
                    val jarDir = java.io.File(jarLocation).parentFile
                    val scriptFile = java.io.File(jarDir, "nodejs/claude-sdk-wrapper.js")
                    if (scriptFile.exists()) scriptFile else null
                } else null
            } catch (e: Exception) {
                logger.debug("从 jar 位置加载脚本失败: ${e.message}")
                null
            }
        }
        
        /**
         * 构建 Node.js 命令和工作目录
         * 确保脚本在正确的目录中运行，以便访问 node_modules
         * 
         * @param scriptPath Node.js 脚本路径
         * @param jsonInput JSON 输入参数
         * @return Pair(完整的命令列表, 工作目录)
         */
        private fun buildNodeCommand(scriptPath: String, jsonInput: String): Pair<List<String>, java.io.File> {
            val scriptFile = java.io.File(scriptPath)
            
            // 工作目录必须是 cli-wrapper 目录，这样才能访问 node_modules 和 package.json
            val workingDir = if (isPackagedMode()) {
                // 打包模式：使用打包部署策略提供的工作目录
                PackagedDeploymentStrategy.getResourceDirectory().resolve("cli-wrapper")
            } else if (scriptFile.parentFile?.name == "cli-wrapper") {
                scriptFile.parentFile
            } else {
                // 如果脚本不在 cli-wrapper 目录，尝试查找附近的 cli-wrapper 目录
                findCliWrapperDirectory(scriptFile) 
                    ?: throw IllegalStateException("无法找到 cli-wrapper 目录，脚本位置: $scriptPath")
            }
            
            // 检查必要的文件
            val packageJson = java.io.File(workingDir, "package.json")
            val nodeModules = java.io.File(workingDir, "node_modules")
            
            if (!packageJson.exists()) {
                throw IllegalStateException("package.json 未找到: ${packageJson.absolutePath}")
            }
            
            if (!nodeModules.exists()) {
                throw IllegalStateException(
                    "node_modules 未找到: ${nodeModules.absolutePath}\n" +
                    "请在 ${workingDir.absolutePath} 目录下运行 'npm install'"
                )
            }
            
            // 使用相对路径，让 Node.js 在正确目录中运行
            val relativeScriptPath = scriptFile.relativeTo(workingDir).path
            val command = listOf("node", relativeScriptPath, jsonInput)
            
            return Pair(command, workingDir)
        }
        
        /**
         * 查找 cli-wrapper 目录
         */
        private fun findCliWrapperDirectory(scriptFile: java.io.File): java.io.File? {
            var dir = scriptFile.parentFile
            
            // 向上查找 cli-wrapper 目录
            repeat(5) {
                if (dir?.name == "cli-wrapper" && java.io.File(dir, "package.json").exists()) {
                    return dir
                }
                val cliWrapperSubdir = java.io.File(dir, "cli-wrapper")
                if (cliWrapperSubdir.exists() && java.io.File(cliWrapperSubdir, "package.json").exists()) {
                    return cliWrapperSubdir
                }
                dir = dir?.parentFile
            }
            
            return null
        }
        
        /**
         * 检查 Node.js 是否可用
         * 
         * @return 如果 Node.js 可用返回 true，否则返回 false
         */
        private fun isNodeAvailable(): Boolean {
            return try {
                val process = ProcessBuilder(listOf("node", "--version")).start()
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
     * 实际执行 Node.js SDK 调用的核心逻辑
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
        logger.info("🔵 [$requestId] 开始查询 (SDK - $sessionTypeStr): ${prompt.take(100)}...")
        
        return withContext(Dispatchers.IO) {
            
            // 检查协程状态
            coroutineContext.ensureActive()
            
            // 检查 Node.js 是否可用
            if (!isNodeAvailable()) {
                throw IllegalStateException("Node.js 不可用。请确保已安装 Node.js 18+ 并在 PATH 中。")
            }
            
            // 获取 Node.js 脚本路径
            val scriptPath = getNodeScriptPath()
            val scriptFile = java.io.File(scriptPath)
            if (!scriptFile.exists()) {
                throw IllegalStateException("Node.js 脚本不存在: $scriptPath")
            }
            
            // 构建 JSON 输入参数
            val jsonInput = buildJsonObject {
                put("prompt", prompt)
                put("options", buildJsonObject {
                    // 模型配置
                    options.model?.let { put("model", it) }
                    options.fallbackModel?.let { put("fallbackModel", it) }
                    
                    // 会话管理
                    options.resume?.let { if (it.isNotBlank()) put("resume", it) }
                    options.sessionId?.let { if (it.isNotBlank()) put("sessionId", it) }
                    
                    // 对话控制
                    options.maxTurns?.let { put("maxTurns", it) }
                    options.customSystemPrompt?.let { put("customSystemPrompt", it) }
                    
                    // 工作目录
                    put("cwd", options.cwd)
                    
                    // 权限配置
                    if (options.skipPermissions || options.permissionMode == "bypassPermissions") {
                        put("skipPermissions", true)
                    }
                    put("permissionMode", options.permissionMode)
                    
                    // 工具权限
                    options.allowedTools?.let { tools ->
                        if (tools.isNotEmpty()) {
                            put("allowedTools", buildJsonArray {
                                tools.forEach { add(it) }
                            })
                        }
                    }
                    options.disallowedTools?.let { tools ->
                        if (tools.isNotEmpty()) {
                            put("disallowedTools", buildJsonArray {
                                tools.forEach { add(it) }
                            })
                        }
                    }
                    
                    // MCP 配置
                    options.mcpServers?.let { servers ->
                        put("mcpServers", buildJsonObject {
                            servers.forEach { (name, config) ->
                                put(name, Json.encodeToJsonElement(config))
                            }
                        })
                    }
                    
                    // 高级选项
                    options.debug?.let { if (it) put("debug", true) }
                    options.verbose?.let { if (it) put("verbose", true) }
                    options.showStats?.let { if (it) put("showStats", true) }
                    options.continueRecent?.let { if (it) put("continueRecent", true) }
                    options.settingsFile?.let { put("settingsFile", it) }
                    options.autoConnectIde?.let { if (it) put("autoConnectIde", true) }
                    
                    options.additionalDirectories?.let { dirs ->
                        if (dirs.isNotEmpty()) {
                            put("additionalDirectories", buildJsonArray {
                                dirs.forEach { add(it) }
                            })
                        }
                    }
                })
            }.toString()
            
            logger.info("🔵 [$requestId] JSON 输入: ${jsonInput.take(500)}...")
            
            // 构建 Node.js 命令
            val (nodeCommand, nodeWorkingDir) = buildNodeCommand(scriptPath, jsonInput)
            logger.info("🔵 [$requestId] Node.js 命令: ${nodeCommand.take(2).joinToString(" ")} [JSON_INPUT]")
            logger.info("🔵 [$requestId] Node.js 工作目录: ${nodeWorkingDir.absolutePath}")
            
            val processBuilder = ProcessBuilder(nodeCommand)
            processBuilder.directory(nodeWorkingDir)
            
            // Node.js 进程不需要设置用户指定的 cwd，因为 cwd 会通过 JSON 参数传递给 SDK
            logger.info("🔵 [$requestId] 用户指定的 cwd 将通过 JSON 参数传递给 SDK: ${options.cwd}")
            
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
            
            // 启动输出监听协程
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            // 监听变量，用于跟踪 SDK 返回的会话ID和助手回复
            var detectedSessionId: String? = null
            val assistantResponseBuilder = StringBuilder()
            
            // 启动 stdout 监听 - 处理 Node.js SDK 返回的 JSON 消息
            scope.launch {
                try {
                    logger.info("🔵 [$requestId] 开始监听 Node.js SDK stdout...")
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { currentLine ->
                                if (currentLine.isNotBlank()) {
                                    try {
                                        // 解析 Node.js 脚本返回的 JSON 消息
                                        val jsonMsg = Json.parseToJsonElement(currentLine.trim())
                                        if (jsonMsg is JsonObject) {
                                            val msgType = jsonMsg["type"]?.jsonPrimitive?.content
                                            logger.info("🔵 [$requestId] SDK 消息类型: $msgType")
                                            
                                            when (msgType) {
                                                "start" -> {
                                                    val sessionId = jsonMsg["sessionId"]?.jsonPrimitive?.contentOrNull
                                                    sessionId?.let { detectedSessionId = it }
                                                    logger.info("🔵 [$requestId] SDK 开始查询，会话ID: $sessionId")
                                                }
                                                "message" -> {
                                                    // 转发 Claude 消息给回调函数并收集内容
                                                    val data = jsonMsg["data"]
                                                    if (data != null) {
                                                        val content = data.toString()
                                                        processOutputLine(content)
                                                        // 收集助手回复内容
                                                        assistantResponseBuilder.append(content)
                                                        // 调用流式回调（实时更新UI）
                                                        onStreamingMessage?.invoke(assistantResponseBuilder.toString())
                                                    }
                                                }
                                                "complete" -> {
                                                    val sessionId = jsonMsg["sessionId"]?.jsonPrimitive?.contentOrNull
                                                    val messageCount = jsonMsg["messageCount"]?.jsonPrimitive?.intOrNull
                                                    val duration = jsonMsg["duration"]?.jsonPrimitive?.longOrNull
                                                    sessionId?.let { detectedSessionId = it }
                                                    logger.info("🔵 [$requestId] SDK 查询完成，会话ID: $sessionId, 消息数: $messageCount, 耗时: ${duration}ms")
                                                }
                                                "error" -> {
                                                    val error = jsonMsg["error"]?.jsonPrimitive?.contentOrNull
                                                    logger.warn("🔴 [$requestId] SDK 错误: $error")
                                                }
                                                "terminated" -> {
                                                    val reason = jsonMsg["reason"]?.jsonPrimitive?.contentOrNull
                                                    logger.info("🔴 [$requestId] SDK 进程被终止: $reason")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 如果不是 JSON，就直接输出日志
                                        logger.info("🔵 [$requestId] SDK 输出: $currentLine")
                                        processOutputLine(currentLine)
                                    }
                                }
                            }
                        }
                    }
                    logger.info("🔵 [$requestId] SDK stdout 流结束")
                } catch (e: Exception) {
                    if (e.message?.contains("Stream closed") != true) {
                        logger.error("🔴 [$requestId] Error reading SDK stdout: ${e.message}", e)
                    }
                }
            }
            
            // 启动 stderr 监听
            scope.launch {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (line.isNotBlank()) {
                                logger.warn("🔴 [$requestId] SDK stderr: $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("Stream closed") != true) {
                        logger.error("🔴 [$requestId] Error reading SDK stderr: ${e.message}", e)
                    }
                }
            }
            
            try {
                // 等待进程完成
                val exitCode = process.waitFor()
                logger.info("🔵 [$requestId] Node.js SDK 进程退出，退出码: $exitCode")
                
                // 优先使用从 SDK 返回中检测到的 sessionId，其次使用用户指定的
                val finalSessionId = detectedSessionId ?: options.sessionId ?: options.resume
                
                QueryResult(
                    sessionId = finalSessionId,
                    processId = process.pid(),
                    success = exitCode == 0,
                    errorMessage = if (exitCode != 0) "Node.js SDK 退出码: $exitCode" else null,
                    assistantMessage = if (exitCode == 0 && assistantResponseBuilder.isNotEmpty()) assistantResponseBuilder.toString() else null
                )
            } catch (e: Exception) {
                logger.error("🔴 [$requestId] Node.js SDK 执行失败", e)
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
     * 检查 Claude Code SDK 是否可用
     * 验证 Node.js 和 SDK 脚本是否正常工作
     */
    suspend fun isClaudeCodeSdkAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查 Node.js
            if (!isNodeAvailable()) {
                logger.warn("Node.js 不可用")
                return@withContext false
            }
            
            // 检查脚本文件
            val scriptPath = getNodeScriptPath()
            val scriptFile = java.io.File(scriptPath)
            if (!scriptFile.exists()) {
                logger.warn("Node.js SDK 脚本不存在: $scriptPath")
                return@withContext false
            }
            
            // 测试运行简单查询
            val testInput = buildJsonObject {
                put("prompt", "test")
                put("options", buildJsonObject {
                    put("cwd", System.getProperty("user.dir"))
                    put("maxTurns", 1)
                })
            }.toString()
            
            val (testCommand, testWorkingDir) = buildNodeCommand(scriptPath, testInput)
            val processBuilder = ProcessBuilder(testCommand)
            processBuilder.directory(testWorkingDir)
            val process = processBuilder.start()
            
            // 等待短时间或直到进程结束
            val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                logger.warn("SDK 测试超时")
                return@withContext false
            }
            
            val exitCode = process.exitValue()
            logger.info("SDK 测试结果: 退出码 $exitCode")
            
            // 只要能正常启动就认为可用（可能会因为缺少 API key 等原因失败）
            true
            
        } catch (e: Exception) {
            logger.warn("Claude Code SDK 不可用: ${e.message}")
            false
        }
    }
}