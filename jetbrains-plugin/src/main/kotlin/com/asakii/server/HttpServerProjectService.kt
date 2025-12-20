package com.asakii.server

import com.asakii.plugin.bridge.JetBrainsApiImpl
import com.asakii.plugin.bridge.JetBrainsRSocketHandler
import com.asakii.plugin.hooks.IdeaFileSyncHooks
import com.asakii.plugin.mcp.JetBrainsMcpServerProviderImpl
import com.asakii.plugin.mcp.TerminalMcpServerProviderImpl
import com.asakii.plugin.mcp.GitMcpServerProviderImpl
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
import kotlinx.serialization.json.Json
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
import java.util.logging.Logger

/**
 * HTTP 服务器项目级服务
 * 在项目打开时自动启动 HTTP API 服务器
 */
@Service(Service.Level.PROJECT)
class HttpServerProjectService(private val project: Project) : Disposable {
    private val logger = Logger.getLogger(javaClass.name)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var httpServer: HttpApiServer? = null
    private var extractedFrontendDir: Path? = null
    private var _jetbrainsApi: JetBrainsApi? = null

    var serverUrl: String? = null
        private set

    /** 获取 JetBrains API 实例（用于 title actions 等组件） */
    val jetbrainsApi: JetBrainsApi?
        get() = _jetbrainsApi

    init {
        // 首先配置日志系统
        configureLogging()

        logger.info("🚀 Initializing HTTP Server Project Service")
        startServer()
    }

    /**
     * 配置日志系统
     * 将日志输出到项目的 .log 目录，支持滚动备份
     */
    private fun configureLogging() {
        try {
            val projectBasePath = project.basePath
            if (projectBasePath != null) {
                StandaloneLogging.configure(java.io.File(projectBasePath))
                logger.info("📝 Logging configured to: $projectBasePath/.log/")
            } else {
                logger.warning("⚠️ Project base path is null, logging to .log directory skipped")
            }
        } catch (e: Exception) {
            logger.warning("⚠️ Failed to configure logging: ${e.message}")
        }
    }

    /**
     * 启动 HTTP 服务器
     */
    private fun startServer() {
        try {
            // 准备前端资源目录
            val frontendDir = prepareFrontendResources()
            logger.info("📂 Frontend directory: $frontendDir")

            // 创建 IdeTools 和 JetBrainsApi 的实现
            val ideTools = IdeToolsImpl(project)
            val jetbrainsApi = JetBrainsApiImpl(project)
            _jetbrainsApi = jetbrainsApi  // 保存引用供 title actions 使用
            val jetbrainsRSocketHandler = JetBrainsRSocketHandler(jetbrainsApi)

            // 监听主题变化，通过 RSocket 推送给前端（非阻塞）
            jetbrainsApi.theme.addChangeListener { theme ->
                scope.launch {
                    try {
                        withTimeout(5000) {
                            jetbrainsRSocketHandler.pushThemeChanged(theme)
                        }
                    } catch (e: Exception) {
                        logger.warning("⚠️ Failed to push theme change: ${e.message}")
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
                        logger.warning("⚠️ Failed to push session command: ${e.message}")
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
                        logger.warning("⚠️ Failed to push settings change: ${e.message}")
                    }
                }
            }

            // 监听文件编辑器切换，通过 RSocket 推送给前端
            setupFileEditorListener(ideTools, jetbrainsRSocketHandler)

            // 创建 MCP Server Providers
            val jetBrainsMcpServerProvider = JetBrainsMcpServerProviderImpl(project)
            val terminalMcpServerProvider = TerminalMcpServerProviderImpl(project)
            val gitMcpServerProvider = GitMcpServerProviderImpl(project)

            // 创建服务配置提供者（每次 connect 时调用，获取最新的用户设置）
            val serviceConfigProvider: () -> AiAgentServiceConfig = {
                val settings = AgentSettingsService.getInstance()
                val thinkingLevelName = settings.getThinkingLevelById(settings.defaultThinkingLevelId)?.name ?: "Ultra"
                logger.info("📦 Loading agent settings: nodePath=${settings.nodePath.ifBlank { "(system PATH)" }}, model=${settings.defaultModelEnum.displayName}, thinkingLevel=$thinkingLevelName (${settings.defaultThinkingTokens} tokens), permissionMode=${settings.permissionMode}, userInteractionMcp=${settings.enableUserInteractionMcp}, jetbrainsMcp=${settings.enableJetBrainsMcp}, defaultBypass=${settings.defaultBypassPermissions}")

                // 创建 IDEA 文件同步 hooks
                val fileSyncHooks = IdeaFileSyncHooks.create(project)

                AiAgentServiceConfig(
                    defaultModel = settings.defaultModel,  // 使用模型 ID（内置或自定义）
                    claude = ClaudeDefaults(
                        nodePath = settings.nodePath.takeIf { it.isNotBlank() },
                        permissionMode = settings.permissionMode.takeIf { it.isNotBlank() && it != "default" },
                        includePartialMessages = settings.includePartialMessages,
                        enableUserInteractionMcp = settings.enableUserInteractionMcp,
                        enableJetBrainsMcp = settings.enableJetBrainsMcp,
                        enableContext7Mcp = settings.enableContext7Mcp,
                        context7ApiKey = settings.context7ApiKey.takeIf { it.isNotBlank() },
                        enableTerminalMcp = settings.enableTerminalMcp,
                        enableGitMcp = settings.enableGitMcp,
                        mcpServersConfig = loadMcpServersConfig(settings),
                        mcpInstructions = loadMcpInstructions(settings),
                        dangerouslySkipPermissions = settings.defaultBypassPermissions,
                        defaultThinkingLevel = settings.defaultThinkingLevel,
                        defaultThinkingTokens = settings.defaultThinkingTokens,
                        ideaFileSyncHooks = fileSyncHooks
                    ),
                    codex = CodexDefaults(),  // Codex 配置已移除，使用默认值
                    customModels = settings.getCustomModels().map { model ->
                        CustomModelInfo(
                            id = model.id,
                            displayName = model.displayName,
                            modelId = model.modelId
                        )
                    }
                )
            }

            // 启动 Ktor HTTP 服务器
            // 开发模式：使用环境变量指定端口（默认 8765）
            // 生产模式：随机端口（支持多项目）
            val server = HttpApiServer(ideTools, scope, frontendDir, jetbrainsApi, jetbrainsRSocketHandler, jetBrainsMcpServerProvider, terminalMcpServerProvider, gitMcpServerProvider, serviceConfigProvider)
            val devPort = System.getenv("CLAUDE_DEV_PORT")?.toIntOrNull()
            val url = server.start(preferredPort = devPort)
            httpServer = server
            serverUrl = url
            logger.info("🚀 HTTP Server started at: $url")
            logger.info("✅ HTTP Server Project Service initialized successfully")
        } catch (e: Exception) {
            logger.severe("❌ Failed to start HTTP server: ${e.message}")
            e.printStackTrace()
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
            logger.info("✅ Reusing extracted frontend directory: $existing")
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
                logger.info("📦 Extracting frontend resources to: $tempDir")

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
                logger.info("✅ Frontend extracted successfully")
                tempDir
            }
            "file" -> {
                // 开发模式：直接使用文件系统路径
                val file = Path.of(htmlUrl.toURI()).parent
                logger.info("✅ Using filesystem frontend directory: $file")
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
        logger.info("🔄 Restarting HTTP Server...")

        // 1. 停止当前服务器
        httpServer?.stop()
        httpServer = null

        // 2. 清除前端资源缓存（这样会重新从 JAR 解压最新资源）
        extractedFrontendDir?.toFile()?.deleteRecursively()
        extractedFrontendDir = null

        // 3. 重新启动服务器
        startServer()

        logger.info("✅ HTTP Server restarted at: $serverUrl")
        return serverUrl
    }

    override fun dispose() {
        logger.info("🛑 Disposing HTTP Server Project Service")
        httpServer?.stop()
        httpServer = null

        // 清理临时目录
        extractedFrontendDir?.toFile()?.deleteRecursively()
        extractedFrontendDir = null

        scope.cancel()
        logger.info("✅ HTTP Server Project Service disposed")
    }

    /**
     * 加载 MCP 系统提示词
     * 根据启用的 MCP 服务器加载对应的指令（从设置中获取，支持自定义）
     */
    private fun loadMcpInstructions(settings: AgentSettingsService): String? {
        val instructions = mutableListOf<String>()

        // Context7 MCP 指令
        if (settings.enableContext7Mcp) {
            instructions.add(settings.effectiveContext7Instructions)
            logger.info("📝 Loaded Context7 MCP instructions")
        }

        return instructions.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
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
                description = McpDefaults.Context7Server.DESCRIPTION
            ))
            logger.info("✅ Loaded MCP Server config: context7 (type=http)")
        } else {
            logger.info("⏭️ MCP Server 'context7' is disabled")
        }

        // 加载自定义 MCP 服务器
        val mcpSettings = service<McpSettingsService>()
        val customConfigs = loadCustomMcpServers(mcpSettings)
        configs.addAll(customConfigs)
        if (customConfigs.isNotEmpty()) {
            logger.info("✅ Loaded ${customConfigs.size} custom MCP server(s)")
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
                        logger.info("⏭️ Custom MCP Server '$serverName' is disabled")
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

                    configs.add(McpServerConfig(
                        name = serverName,
                        type = serverType,
                        enabled = true,
                        command = command,
                        args = args,
                        env = env,
                        url = url,
                        headers = headers,
                        instructions = instructions
                    ))
                    logger.info("✅ Loaded custom MCP Server: $serverName (type=$serverType)")
                }
            } catch (e: Exception) {
                logger.warning("⚠️ Failed to parse custom MCP config: ${e.message}")
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
                val listener = object : SelectionListener {
                    override fun selectionChanged(e: SelectionEvent) {
                        // 直接推送，不做防抖
                        pushActiveFileUpdate(ideTools, jetbrainsRSocketHandler)
                    }
                }
                selectionListener = listener
                ed.selectionModel.addSelectionListener(listener, this)
                logger.info("📡 Selection listener registered for: ${ed.document}")
            }
        }

        // 监听文件切换事件
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    // 当切换到新文件时，重新注册选区监听器并推送
                    registerSelectionListener()
                    pushActiveFileUpdate(ideTools, jetbrainsRSocketHandler)
                }

                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    // 打开新文件时，重新注册选区监听器并推送
                    registerSelectionListener()
                    pushActiveFileUpdate(ideTools, jetbrainsRSocketHandler)
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    // 关闭文件时推送（可能活跃文件变化了）
                    pushActiveFileUpdate(ideTools, jetbrainsRSocketHandler)
                }
            } as FileEditorManagerListener
        )

        // 初始注册选区监听器
        registerSelectionListener()

        logger.info("📡 File editor listener registered")
    }

    /**
     * 推送活跃文件更新（非阻塞）
     */
    private fun pushActiveFileUpdate(
        ideTools: IdeToolsImpl,
        jetbrainsRSocketHandler: JetBrainsRSocketHandler
    ) {
        scope.launch {
            try {
                val activeFile = ideTools.getActiveEditorFile()
                withTimeout(5000) {
                    jetbrainsRSocketHandler.pushActiveFileChanged(activeFile)
                }
            } catch (e: Exception) {
                logger.warning("⚠️ Failed to push active file update: ${e.message}")
            }
        }
    }

    companion object {
        fun getInstance(project: Project): HttpServerProjectService {
            return project.getService(HttpServerProjectService::class.java)
        }
    }
}
