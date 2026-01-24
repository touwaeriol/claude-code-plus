package com.asakii.server

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.plugin.bridge.JetBrainsApiImpl
import com.asakii.plugin.bridge.JetBrainsRSocketHandler
import com.asakii.plugin.hooks.IdeaFileSyncHooks
import com.asakii.plugin.mcp.JetBrainsMcpServerProviderImpl
import com.asakii.plugin.mcp.JetBrainsFileMcpServerProviderImpl
import com.asakii.plugin.mcp.TerminalMcpServerProviderImpl
import com.asakii.plugin.mcp.GitMcpServerProviderImpl
import com.asakii.server.mcp.McpHttpGateway
import com.asakii.server.mcp.McpProviders
import com.asakii.server.config.AiAgentServiceConfig
import com.asakii.server.config.ClaudeDefaults
import com.asakii.server.config.CodexDefaults
import com.asakii.server.config.CustomModelInfo
import com.asakii.server.config.McpServerConfig
import com.asakii.server.logging.StandaloneLogging
import com.asakii.plugin.tools.IdeToolsImpl
import com.asakii.rpc.api.JetBrainsApi
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import com.asakii.settings.McpSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * HTTP 服务器项目级服务
 * 在项目打开时自动启动 HTTP API 服务器
 */
@Service(Service.Level.PROJECT)
class HttpServerProjectService(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(HttpServerProjectService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var httpServer: HttpApiServer? = null
    private var mcpHttpGateway: McpHttpGateway? = null  // 项目级 MCP HTTP 网关（仅 Codex 模式使用）
    private var extractedFrontendDir: Path? = null
    private var _jetbrainsApi: JetBrainsApi? = null

    var serverUrl: String? = null
        private set

    /** 获取 JetBrains API 实例（用于 title actions 等组件） */
    val jetbrainsApi: JetBrainsApi?
        get() = _jetbrainsApi
    
    /** 获取项目级 MCP HTTP 网关（用于 GenerateCommitMessageService 等组件） */
    fun getMcpHttpGateway(): McpHttpGateway? = mcpHttpGateway

    init {
        // 首先配置日志系统
        configureLogging()

        logger.info { "🚀 Initializing HTTP Server Project Service" }
        startServer()
    }

    /**
     * 配置日志系统
     * - 插件模式：Logback 被排除，SLF4J 自动使用 IDEA 内置实现，日志写入 idea.log
     * - 开发模式 (runIde)：Logback 可用，配置日志到项目 .log 目录
     */
    private fun configureLogging() {
        try {
            // 检测是否在 IDEA 插件环境中（Logback 被排除）
            if (StandaloneLogging.isIdeaPluginEnvironment()) {
                logger.info { "📝 Using IDEA's built-in logging system, logs will be written to idea.log" }
                // Ensure java.util.logging (JUL) is bridged to SLF4J so Codex SDK logs are visible in idea.log
                val projectBasePath = project.basePath
                val logDir = if (projectBasePath != null) {
                    java.nio.file.Path.of(projectBasePath, ".log")
                } else {
                    java.nio.file.Path.of(System.getProperty("user.dir"), ".log")
                }
                StandaloneLogging.configureWithDir(logDir)
                return
            }

            // 开发模式 (runIde)：Logback 可用，配置日志到项目目录
            val projectBasePath = project.basePath
            if (projectBasePath != null) {
                val logDir = java.nio.file.Path.of(projectBasePath, ".log")
                StandaloneLogging.configureWithDir(logDir)
                logger.info { "📝 Development mode: Logging configured to: $logDir" }
            } else {
                logger.warn { "⚠️ Project base path is null, logging configuration skipped" }
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to configure logging: ${e.message}", e)
        }
    }

    /**
     * 启动 HTTP 服务器
     */
    private fun startServer() {
        try {
            // 准备前端资源目录
            val frontendDir = prepareFrontendResources()
            logger.info { "📂 Frontend directory: $frontendDir" }

            // 创建 IdeTools 和 JetBrainsApi 的实现
            val ideTools = IdeToolsImpl(project)
            val jetbrainsApi = JetBrainsApiImpl(project)
            _jetbrainsApi = jetbrainsApi  // 保存引用供 title actions 使用

            // 创建 MCP Server Providers（封装到 McpProviders）
            // Terminal Provider 需要先创建，因为 JetBrainsRSocketHandler 需要它
            val terminalMcpServerProvider = TerminalMcpServerProviderImpl(project)
            val mcpProviders = McpProviders(
                jetBrains = JetBrainsMcpServerProviderImpl(project),
                jetBrainsFile = JetBrainsFileMcpServerProviderImpl(project),
                terminal = terminalMcpServerProvider,
                git = GitMcpServerProviderImpl(project)
            )

            // 创建 JetBrains RSocket Handler（传入 Terminal Provider 以支持后台执行功能）
            val jetbrainsRSocketHandler = JetBrainsRSocketHandler(jetbrainsApi, terminalMcpServerProvider)


            // 注册终端任务更新监听器，通过 RSocket 推送给前端
            (terminalMcpServerProvider.getServer() as? com.asakii.plugin.mcp.TerminalMcpServerImpl)?.sessionManager?.setTaskUpdateListener { 
                toolUseId, sessionId, action, command, isBackground, startTime, elapsedMs ->
                jetbrainsRSocketHandler.pushTerminalTaskUpdate(
                    toolUseId, sessionId, action, command, isBackground, startTime, elapsedMs
                )
            }
            // 监听主题变化，通过 RSocket 推送给前端（非阻塞）
            jetbrainsApi.theme.addChangeListener { theme ->
                scope.launch {
                    try {
                        withTimeout(5000) {
                            jetbrainsRSocketHandler.pushThemeChanged(theme)
                        }
                    } catch (e: Exception) {
                        logger.warn("⚠️ Failed to push theme change: ${e.message}", e)
                    }
                }
            }

            // 监听会话命令，通过 RSocket 推送给前端（非阻塞）
            jetbrainsApi.session.addCommandListener { command ->
                scope.launch {
                    try {
                        withTimeout(5000) {
                            jetbrainsRSocketHandler.pushSessionCommand(command)
                        }
                    } catch (e: Exception) {
                        logger.warn("⚠️ Failed to push session command: ${e.message}", e)
                    }
                }
            }

            // 监听设置变化，通过 RSocket 推送给前端（非阻塞）
            AgentSettingsService.getInstance().addChangeListener { settings ->
                scope.launch {
                    try {
                        withTimeout(5000) {
                            jetbrainsRSocketHandler.pushSettingsChanged(settings)
                        }
                    } catch (e: Exception) {
                        logger.warn("⚠️ Failed to push settings change: ${e.message}", e)
                    }
                }
            }

            // 监听文件编辑器切换，通过 RSocket 推送给前端
            setupFileEditorListener(ideTools, jetbrainsRSocketHandler)

            // 创建服务配置提供者（每次 connect 时调用，获取最新的用户设置）
            val serviceConfigProvider: () -> AiAgentServiceConfig = {
                val settings = AgentSettingsService.getInstance()
                val defaultProvider = settings.getDefaultBackendProvider()
                val thinkingLevelName = settings.getThinkingLevelById(settings.defaultThinkingLevelId)?.name ?: "Ultra"
                val userCodexPath = settings.codexPath.trim()
                val autoCodexPath = if (userCodexPath.isBlank()) {
                    AgentSettingsService.detectCodexPath().trim()
                } else {
                    ""
                }
                val codexPath = userCodexPath
                    .ifBlank { autoCodexPath }
                    .takeIf { it.isNotBlank() }
                val codexPathSource = when {
                    userCodexPath.isNotBlank() -> "user"
                    autoCodexPath.isNotBlank() -> "auto"
                    else -> "none"
                }
                val codexPathExists = codexPath?.let { path ->
                    runCatching { Path.of(path) }
                        .map { it.toFile().exists() }
                        .getOrElse { false }
                } ?: false
                val userInteractionBackends = settings.getUserInteractionMcpProviders()
                val jetbrainsBackends = settings.getJetbrainsMcpProviders()
                val jetbrainsFileBackends = settings.getJetbrainsFileMcpProviders()
                val context7Backends = settings.getContext7McpProviders()
                val terminalBackends = settings.getTerminalMcpProviders()
                val gitBackends = settings.getGitMcpProviders()
                val mcpEnabledBackends = settings.getMcpEnabledProviders()
                val defaultModelInfo = settings.getModelById(settings.defaultModel)
                val defaultModelLabel = defaultModelInfo?.displayName ?: settings.defaultModel
                logger.info(
                    "📦 Loading agent settings: nodePath=${settings.nodePath.ifBlank { "(system PATH)" }}, " +
                        "model=$defaultModelLabel, thinkingLevel=$thinkingLevelName, defaultBackend=${defaultProvider.name.lowercase()} " +
                        "(${settings.defaultThinkingTokens} tokens), permissionMode=${settings.permissionMode}, " +
                        "codexPath=${codexPath ?: "(auto)"}, codexPathSource=$codexPathSource, codexPathExists=$codexPathExists, " +
                        "userInteractionMcp=${settings.enableUserInteractionMcp}(${userInteractionBackends.joinToString()}), " +
                        "jetbrainsMcp=${settings.enableJetBrainsMcp}(${jetbrainsBackends.joinToString()}), " +
                        "jetbrainsFileMcp=${settings.enableJetBrainsFileMcp}(${jetbrainsFileBackends.joinToString()}), " +
                        "context7Mcp=${settings.enableContext7Mcp}(${context7Backends.joinToString()}), " +
                        "terminalMcp=${settings.enableTerminalMcp}(${terminalBackends.joinToString()}), " +
                        "gitMcp=${settings.enableGitMcp}(${gitBackends.joinToString()}), " +
                        "defaultBypass=${settings.defaultBypassPermissions}, " +
                        "claudeAutoCleanup=${settings.claudeDefaultAutoCleanupContexts}, " +
                        "codexAutoCleanup=${settings.codexDefaultAutoCleanupContexts}"
                )

                // 创建 IDEA 文件同步 hooks
                val fileSyncHooks = IdeaFileSyncHooks.create(project)

                AiAgentServiceConfig(
                    defaultProvider = defaultProvider,
                    defaultModel = settings.defaultModel,  // 使用模型 ID（内置或自定义）
                    claude = ClaudeDefaults(
                        nodePath = settings.nodePath.takeIf { it.isNotBlank() },
                        permissionMode = settings.permissionMode.takeIf { it.isNotBlank() && it != "default" },
                        includePartialMessages = settings.includePartialMessages,
                        enableUserInteractionMcp = settings.enableUserInteractionMcp,
                        enableJetBrainsMcp = settings.enableJetBrainsMcp,
                        enableJetBrainsFileMcp = settings.enableJetBrainsFileMcp,
                        enableContext7Mcp = settings.enableContext7Mcp,
                        context7ApiKey = settings.context7ApiKey.takeIf { it.isNotBlank() },
                        enableTerminalMcp = settings.enableTerminalMcp,
                        enableGitMcp = settings.enableGitMcp,
                        userInteractionMcpBackends = userInteractionBackends,
                        jetbrainsMcpBackends = jetbrainsBackends,
                        jetbrainsFileMcpBackends = jetbrainsFileBackends,
                        context7McpBackends = context7Backends,
                        terminalMcpBackends = terminalBackends,
                        gitMcpBackends = gitBackends,
                        userInteractionInstructionsByBackend = settings.getUserInteractionInstructionsByBackend(),
                        userInteractionMcpTimeoutSec = settings.userInteractionMcpTimeout,
                        mcpServersConfig = loadMcpServersConfig(settings),
                        mcpInstructions = loadMcpInstructions(settings),
                        dangerouslySkipPermissions = settings.defaultBypassPermissions,
                        defaultAutoCleanupContexts = settings.claudeDefaultAutoCleanupContexts,
                        defaultThinkingLevel = settings.defaultThinkingLevel,
                        defaultThinkingTokens = settings.defaultThinkingTokens,
                        ideaFileSyncHooks = fileSyncHooks
                    ),
                    codex = CodexDefaults(
                        binaryPath = codexPath,
                        webSearchEnabled = settings.codexWebSearchEnabled,
                        defaultModelId = settings.codexDefaultModelId,
                        sandboxMode = settings.codexDefaultSandboxMode,
                        defaultReasoningEffort = settings.codexDefaultReasoningEffort,
                        defaultReasoningSummary = settings.codexDefaultReasoningSummary,
                        defaultAutoCleanupContexts = settings.codexDefaultAutoCleanupContexts
                    ),
                    mcpEnabledBackends = mcpEnabledBackends,
                    customModels = settings.getCustomModels().map { model ->
                        CustomModelInfo(
                            displayName = model.displayName,
                            modelId = model.modelId
                        )
                    },
                    codexCustomModels = settings.getCodexCustomModels().map { model ->
                        CustomModelInfo(
                            displayName = model.displayName,
                            modelId = model.modelId
                        )
                    }
                )
            }

            // 创建项目级 MCP HTTP 网关（仅 Codex 模式使用）
            val gateway = McpHttpGateway()
            mcpHttpGateway = gateway
            
            // 启动 Ktor HTTP 服务器
            // 开发模式：使用环境变量指定端口（默认 8765）
            // 生产模式：随机端口（支持多项目）
            val server = HttpApiServer(ideTools, scope, frontendDir, jetbrainsApi, jetbrainsRSocketHandler, gateway, mcpProviders, serviceConfigProvider)
            val devPort = System.getenv("CLAUDE_DEV_PORT")?.toIntOrNull()
            val url = server.start(preferredPort = devPort)
            httpServer = server
            serverUrl = url
            logger.info { "🚀 HTTP Server started at: $url" }
            logger.info { "✅ HTTP Server Project Service initialized successfully" }
        } catch (e: Exception) {
            logger.error("❌ Failed to start HTTP server: ${e.message}", e)
        }
    }

    /**
     * 准备前端资源
     * 从 JAR 解压到临时目录
     */
    private fun prepareFrontendResources(): Path {
        // 复用已解压的目录
        val existing = extractedFrontendDir
        if (existing != null && Files.exists(existing.resolve("index.html"))) {
            logger.info { "✅ Reusing extracted frontend directory: $existing" }
            return existing
        }

        val htmlUrl = javaClass.getResource("/frontend/index.html")
            ?: throw IllegalStateException("""
                ❌ Frontend resources not found in JAR!

                Solution:
                1. Run: ./gradlew :jetbrains-plugin:buildFrontend
                2. Or rebuild the project
            """.trimIndent())

        return when (htmlUrl.protocol) {
            "jar" -> {
                val connection = htmlUrl.openConnection() as JarURLConnection
                val tempDir = Files.createTempDirectory("claude-frontend-")
                logger.info { "📦 Extracting frontend resources to: $tempDir" }

                connection.jarFile.use { jarFile ->
                    jarFile.stream().use { entries ->
                        entries
                            .filter { !it.isDirectory && it.name.startsWith("frontend/") }
                            .forEach { entry ->
                                val relative = entry.name.removePrefix("frontend/")
                                val target = tempDir.resolve(relative)
                                target.parent?.let { Files.createDirectories(it) }
                                jarFile.getInputStream(entry).use { input ->
                                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                    }
                }

                extractedFrontendDir = tempDir
                logger.info { "✅ Frontend extracted successfully" }
                tempDir
            }
            "file" -> {
                // 开发模式：直接使用文件系统路径
                val file = Path.of(htmlUrl.toURI()).parent
                logger.info { "✅ Using filesystem frontend directory: $file" }
                file
            }
            else -> throw IllegalStateException("Unsupported protocol: ${htmlUrl.protocol}")
        }
    }

    /**
     * 获取 HTTP 服务器实例
     */
    fun getServer(): HttpApiServer? = httpServer

    /**
     * 重启 HTTP 服务器
     * 会清除前端资源缓存，重新解压并启动服务器
     * @return 新的服务器 URL，如果重启失败则返回 null
     */
    fun restart(): String? {
        logger.info { "🔄 Restarting HTTP Server..." }

        // 1. 停止当前服务器
        httpServer?.stop()
        httpServer = null

        // 2. 清除前端资源缓存（这样会重新从 JAR 解压最新资源）
        extractedFrontendDir?.toFile()?.deleteRecursively()
        extractedFrontendDir = null

        // 3. 重新启动服务器
        startServer()

        logger.info { "✅ HTTP Server restarted at: $serverUrl" }
        return serverUrl
    }

    override fun dispose() {
        logger.info { "🛑 Disposing HTTP Server Project Service" }
        httpServer?.stop()
        httpServer = null
        
        // 关闭 MCP HTTP 网关
        mcpHttpGateway?.shutdown()
        mcpHttpGateway = null

        // 清理临时目录
        extractedFrontendDir?.toFile()?.deleteRecursively()
        extractedFrontendDir = null

        scope.cancel()
        logger.info { "✅ HTTP Server Project Service disposed" }
    }

    /**
     * 加载 MCP 系统提示词
     * 根据启用的 MCP 服务器加载对应的指令（从设置中获取，支持自定义）
     */
    private fun loadMcpInstructions(settings: AgentSettingsService): String? {
        // MCP 指令改为按服务器维度追加（见 loadMcpServersConfig / prepareMcpSession）
        return null
    }

    /**
     * 加载 MCP 服务器配置
     * 根据用户设置决定启用哪些 MCP，使用 McpDefaults 中的配置
     */
    private fun loadMcpServersConfig(settings: AgentSettingsService): List<McpServerConfig> {
        val configs = mutableListOf<McpServerConfig>()

        // Context7 MCP
        if (settings.enableContext7Mcp) {
            val headers = settings.context7ApiKey.takeIf { it.isNotBlank() }?.let {
                mapOf(McpDefaults.Context7Server.API_KEY_HEADER to it)
            }

            configs.add(McpServerConfig(
                name = "context7",
                type = "http",
                enabled = true,
                url = McpDefaults.Context7Server.URL,
                headers = headers,
                description = McpDefaults.Context7Server.DESCRIPTION,
                instructions = settings.effectiveContext7Instructions,
                instructionsByBackend = settings.getContext7InstructionsByBackend(),
                enabledBackends = settings.getContext7McpProviders()
            ))
            logger.info { "✅ Loaded MCP Server config: context7 (type=http)" }
        } else {
            logger.info { "⏭️ MCP Server 'context7' is disabled" }
        }

        // 加载自定义 MCP 服务器
        val mcpSettings = service<McpSettingsService>()
        val customConfigs = loadCustomMcpServers(mcpSettings)
        configs.addAll(customConfigs)
        if (customConfigs.isNotEmpty()) {
            logger.info { "✅ Loaded ${customConfigs.size} custom MCP server(s)" }
        }

        return configs
    }

    /**
     * 加载自定义 MCP 服务器配置
     * 从两个级别读取配置：Global、Project
     *
     * 存储格式：
     * {
     *   "server-name": {
     *     "config": { "command": "...", "args": [...] },  // 纯净的 MCP 配置
     *     "enabled": true,                                 // 元数据
     *     "instructions": "..."                            // 元数据
     *   }
     * }
     */
    private fun loadCustomMcpServers(mcpSettings: McpSettingsService): List<McpServerConfig> {
        val configs = mutableListOf<McpServerConfig>()
        val json = Json { ignoreUnknownKeys = true }
        val settings = AgentSettingsService.getInstance()

        fun parseBackendKeys(raw: String): Set<String> {
            if (raw.isBlank()) return emptySet()
            val parsed = try {
                json.decodeFromString<List<String>>(raw).toSet()
            } catch (_: Exception) {
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
            return parsed
        }

        fun parseEnabledBackends(entryObj: kotlinx.serialization.json.JsonObject): Set<AiAgentProvider>? {
            val element = entryObj["enabledBackends"] ?: return null
            val rawKeys = when (element) {
                is kotlinx.serialization.json.JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                is kotlinx.serialization.json.JsonPrimitive -> parseBackendKeys(element.contentOrNull ?: "")
                else -> return null
            }
            return settings.toProviders(rawKeys)
        }

        // 合并两个级别的配置（Global、Project）
        val allConfigs = listOf(
            mcpSettings.getGlobalConfig(),
            mcpSettings.getProjectConfig(project)
        )

        for (jsonStr in allConfigs) {
            if (jsonStr.isBlank()) continue

            try {
                val parsed = json.parseToJsonElement(jsonStr).jsonObject

                for ((serverName, entry) in parsed.entries) {
                    val entryObj = entry.jsonObject

                    // 读取启用状态
                    val enabled = entryObj["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                    if (!enabled) {
                        logger.info { "⏭️ Custom MCP Server '$serverName' is disabled" }
                        continue
                    }

                    // 读取纯净的 MCP 配置
                    val mcpConfig = entryObj["config"]?.jsonObject ?: entryObj  // 兼容旧格式

                    // 读取配置字段
                    val serverType = mcpConfig["type"]?.jsonPrimitive?.content ?: "stdio"
                    val command = mcpConfig["command"]?.jsonPrimitive?.content
                    val url = mcpConfig["url"]?.jsonPrimitive?.content
                    val args = mcpConfig["args"]?.jsonArray?.map { it.jsonPrimitive.content }
                    val env = mcpConfig["env"]?.jsonObject?.entries?.associate {
                        it.key to it.value.jsonPrimitive.content
                    }
                    val headers = mcpConfig["headers"]?.jsonObject?.entries?.associate {
                        it.key to it.value.jsonPrimitive.content
                    }

                    // 读取元数据
                    val instructions = entryObj["instructions"]?.jsonPrimitive?.content
                    val instructionsByBackend = entryObj["instructionsByBackend"]?.jsonObject?.entries
                        ?.mapNotNull { (key, value) ->
                            value.jsonPrimitive.contentOrNull?.let { key to it }
                        }
                        ?.toMap()
                    val enabledBackends = parseEnabledBackends(entryObj)

                    configs.add(McpServerConfig(
                        name = serverName,
                        type = serverType,
                        enabled = true,
                        command = command,
                        args = args,
                        env = env,
                        url = url,
                        headers = headers,
                        instructions = instructions,
                        instructionsByBackend = instructionsByBackend,
                        enabledBackends = enabledBackends
                    ))
                    logger.info { "✅ Loaded custom MCP Server: $serverName (type=$serverType)" }
                }
            } catch (e: Exception) {
                logger.warn("⚠️ Failed to parse custom MCP config: ${e.message}", e)
            }
        }

        return configs
    }

    /**
     * 设置文件编辑器监听器
     * 监听文件切换和选区变化，推送给前端
     */
    private fun setupFileEditorListener(
        ideTools: IdeToolsImpl,
        jetbrainsRSocketHandler: JetBrainsRSocketHandler
    ) {
        // 用于存储当前监听的编辑器，避免重复注册
        var currentEditor: com.intellij.openapi.editor.Editor? = null
        var selectionListener: SelectionListener? = null
        
        // 防抖机制：用于取消之前的推送任务
        var pendingPushJob: kotlinx.coroutines.Job? = null
        val debounceDelayMs = 150L  // 防抖延迟 150ms

        // 带防抖的推送函数
        fun debouncedPush() {
            pendingPushJob?.cancel()
            pendingPushJob = scope.launch {
                kotlinx.coroutines.delay(debounceDelayMs)
                pushActiveFileUpdate(ideTools, jetbrainsRSocketHandler)
            }
        }
        
        // 立即推送函数（用于文件切换等重要事件）
        fun immediatePush() {
            pendingPushJob?.cancel()
            pendingPushJob = scope.launch {
                pushActiveFileUpdate(ideTools, jetbrainsRSocketHandler)
            }
        }

        // 注册选区监听器的函数
        fun registerSelectionListener() {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val editor = fileEditorManager.selectedTextEditor

            // 如果编辑器没有变化，不需要重新注册
            if (editor == currentEditor) return

            // 移除旧的监听器
            selectionListener?.let { listener ->
                currentEditor?.selectionModel?.removeSelectionListener(listener)
            }

            currentEditor = editor

            // 为新编辑器注册选区监听器
            editor?.let { ed ->
                // 记录上一次的选区状态，用于判断是否需要推送
                var lastHasSelection = ed.selectionModel.hasSelection()
                
                val listener = object : SelectionListener {
                    override fun selectionChanged(e: SelectionEvent) {
                        val currentHasSelection = ed.selectionModel.hasSelection()
                        
                        // 只在选区状态变化时推送：
                        // 1. 从无选区变为有选区
                        // 2. 从有选区变为无选区
                        // 3. 选区内容变化（都有选区的情况）
                        if (currentHasSelection || lastHasSelection != currentHasSelection) {
                            debouncedPush()
                        }
                        lastHasSelection = currentHasSelection
                    }
                }
                selectionListener = listener
                ed.selectionModel.addSelectionListener(listener, this)
            }
        }

        // 监听文件切换事件
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    // 当切换到新文件时，重新注册选区监听器并立即推送
                    registerSelectionListener()
                    immediatePush()
                }

                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    // 打开新文件时，重新注册选区监听器并立即推送
                    registerSelectionListener()
                    immediatePush()
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    // 关闭文件时立即推送（可能活跃文件变化了）
                    immediatePush()
                }
            } as FileEditorManagerListener
        )

        // 初始注册选区监听器
        registerSelectionListener()

        logger.info { "📡 File editor listener registered" }
    }

    /**
     * 推送活跃文件更新（非阻塞）
     */
    private fun pushActiveFileUpdate(
        ideTools: IdeToolsImpl,
        jetbrainsRSocketHandler: JetBrainsRSocketHandler
    ) {
        try {
            val activeFile = ideTools.getActiveEditorFile()
            scope.launch {
                withTimeout(5000) {
                    jetbrainsRSocketHandler.pushActiveFileChanged(activeFile)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 防抖取消，忽略
        } catch (e: Exception) {
            logger.debug { "⚠️ Failed to push active file update: ${e.message}" }
        }
    }

    companion object {
        fun getInstance(project: Project): HttpServerProjectService {
            return project.getService(HttpServerProjectService::class.java)
        }
    }
}
