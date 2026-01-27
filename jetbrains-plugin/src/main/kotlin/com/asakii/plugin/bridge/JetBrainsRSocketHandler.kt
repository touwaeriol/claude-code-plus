package com.asakii.plugin.bridge

import com.asakii.plugin.mcp.tools.FileChangeLabelCache
import com.asakii.rpc.api.*
import com.asakii.rpc.proto.IdeThemeProto
import com.asakii.rpc.proto.GetIdeSettingsResponse
import com.asakii.rpc.proto.IdeSettings
import com.asakii.rpc.proto.OptionConfig as OptionConfigProto
import com.asakii.settings.OptionConfig
import com.asakii.rpc.proto.JetBrainsGetLocaleResponse
import com.asakii.rpc.proto.JetBrainsGetProjectPathResponse
import com.asakii.rpc.proto.JetBrainsGetThemeResponse
import com.asakii.rpc.proto.JetBrainsOperationResponse
import com.asakii.settings.AgentSettingsService
import com.asakii.server.JetBrainsRSocketHandlerProvider
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import com.asakii.logging.*
import java.util.concurrent.ConcurrentHashMap
import com.asakii.rpc.proto.JetBrainsOpenFileRequest as ProtoOpenFileRequest
import com.asakii.rpc.proto.JetBrainsShowDiffRequest as ProtoShowDiffRequest
import com.asakii.rpc.proto.JetBrainsShowMultiEditDiffRequest as ProtoShowMultiEditDiffRequest
import com.asakii.rpc.proto.JetBrainsShowEditPreviewRequest as ProtoShowEditPreviewRequest
import com.asakii.rpc.proto.JetBrainsShowMarkdownRequest as ProtoShowMarkdownRequest
import com.asakii.rpc.proto.JetBrainsShowEditFullDiffRequest as ProtoShowEditFullDiffRequest
import com.asakii.rpc.proto.JetBrainsSetLocaleRequest as ProtoSetLocaleRequest
import com.asakii.rpc.proto.JetBrainsSessionState as ProtoSessionState
import com.asakii.rpc.proto.JetBrainsSessionCommand as ProtoSessionCommand
import com.asakii.rpc.proto.JetBrainsBatchRollbackRequest as ProtoBatchRollbackRequest
import com.asakii.rpc.proto.JetBrainsBatchRollbackEvent
import com.asakii.rpc.proto.RollbackStatus
import com.asakii.rpc.proto.JetBrainsTerminalBackgroundRequest as ProtoTerminalBackgroundRequest
import com.asakii.rpc.proto.JetBrainsTerminalBackgroundEvent
import com.asakii.rpc.proto.TerminalBackgroundStatus
import com.asakii.rpc.proto.JetBrainsGetBackgroundableTerminalsRequest as ProtoGetBackgroundableTerminalsRequest
import com.asakii.rpc.proto.JetBrainsGetBackgroundableTerminalsResponse
import com.asakii.rpc.proto.JetBrainsBackgroundableTerminal
import com.asakii.rpc.proto.ActiveFileChangedNotify
import com.asakii.server.mcp.TerminalMcpServerProvider
import com.asakii.plugin.mcp.TerminalMcpServerImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * JetBrains IDE 集成 RSocket 处理器
 *
 * 职责：
 * 1. 处理前端调用（openFile, showDiff, getTheme 等）
 * 2. 支持反向调用（pushThemeChange, pushSessionCommand 等）
 *
 * 路由表（前端 → 后端）：
 * - ide.openFile: 打开文件
 * - ide.showDiff: 显示 Diff
 * - ide.showMultiEditDiff: 显示多编辑 Diff
 * - ide.getTheme: 获取主题
 * - ide.getActiveFile: 获取当前活跃文件
 * - ide.getSettings: 获取 IDE 设置
 * - ide.getLocale: 获取语言
 * - ide.setLocale: 设置语言
 * - ide.getProjectPath: 获取项目路径
 * - ide.reportSessionState: 上报会话状态
 *
 * 反向调用路由（后端 → 前端）：
 * - client.call(method=onThemeChanged): 主题变化
 * - client.call(method=onSessionCommand): 会话命令
 * - client.call(method=onActiveFileChanged): 活跃文件变化
 */
class JetBrainsRSocketHandler(
    private val jetbrainsApi: JetBrainsApi,
    private val terminalMcpServerProvider: TerminalMcpServerProvider? = null
) : JetBrainsRSocketHandlerProvider {
    private val logger = getLogger("JetBrainsRSocketHandler")

    // 客户端 requester（用于反向调用）
    private var clientRequester: RSocket? = null

    // 连接的客户端集合（支持多客户端）
    private val connectedClients = ConcurrentHashMap<String, RSocket>()

    // 推送处理器（委托处理所有推送操作）
    private val pushHandlers = JetBrainsPushHandlers(connectedClients)

    /**
     * 创建 RSocket 请求处理器
     */
    override fun createHandler(): RSocket {
        logger.info { "🔌 [JetBrains RSocket] 创建请求处理器" }

        return RSocketRequestHandler {
            requestResponse { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                logger.info { "📨 [JetBrains RSocket] ← $route" }

                when (route) {
                    "ide.openFile" -> handleOpenFile(dataBytes)
                    "ide.showDiff" -> handleShowDiff(dataBytes)
                    "ide.showMultiEditDiff" -> handleShowMultiEditDiff(dataBytes)
                    "ide.showEditPreviewDiff" -> handleShowEditPreviewDiff(dataBytes)
                    "ide.showEditFullDiff" -> handleShowEditFullDiff(dataBytes)
                    "ide.showMarkdown" -> handleShowMarkdown(dataBytes)
                    "ide.getTheme" -> handleGetTheme()
                    "ide.getActiveFile" -> handleGetActiveFile()
                    "ide.getSettings" -> handleGetSettings()
                    "ide.getLocale" -> handleGetLocale()
                    "ide.setLocale" -> handleSetLocale(dataBytes)
                    "ide.getProjectPath" -> handleGetProjectPath()
                    "ide.reportSessionState" -> handleReportSessionState(dataBytes)
                    "ide.getOriginalContent" -> handleGetOriginalContent(dataBytes)
                    "ide.getFileHistoryContent" -> handleGetFileHistoryContent(dataBytes)
                    "ide.rollbackFile" -> handleRollbackFile(dataBytes)
                    "ide.getBackgroundableTerminals" -> handleGetBackgroundableTerminals(dataBytes)
                    else -> {
                        logger.warn { "⚠️ [JetBrains RSocket] Unknown route: $route" }
                        buildErrorResponse("Unknown route: $route")
                    }
                }
            }

            // 流式请求处理
            requestStream { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                logger.info { "📡 [JetBrains RSocket] ← Stream: $route" }

                when (route) {
                    "ide.batchRollback" -> handleBatchRollback(dataBytes)
                    "ide.terminalBackground" -> handleTerminalBackground(dataBytes)
                    else -> {
                        logger.warn { "⚠️ [JetBrains RSocket] Unknown stream route: $route" }
                        flow { throw IllegalArgumentException("Unknown stream route: $route") }
                    }
                }
            }
        }
    }

    /**
     * 设置客户端 requester（用于反向调用）
     */
    override fun setClientRequester(clientId: String, requester: RSocket) {
        this.clientRequester = requester
        connectedClients[clientId] = requester
        logger.info { "🔗 [JetBrains RSocket] 客户端已连接: $clientId" }
    }

    /**
     * 移除客户端
     */
    override fun removeClient(clientId: String) {
        connectedClients.remove(clientId)
        if (connectedClients.isEmpty()) {
            clientRequester = null
        }
        logger.info { "🔌 [JetBrains RSocket] 客户端已断开: $clientId" }
    }

    // ==================== 前端调用处理 ====================

    private fun handleOpenFile(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoOpenFileRequest.parseFrom(dataBytes)
            logger.info { "📂 [JetBrains] openFile: ${req.filePath}" }

            val request = com.asakii.rpc.api.JetBrainsOpenFileRequest(
                filePath = req.filePath,
                line = if (req.hasLine()) req.line else null,
                column = if (req.hasColumn()) req.column else null,
                startOffset = if (req.hasStartOffset()) req.startOffset else null,
                endOffset = if (req.hasEndOffset()) req.endOffset else null
            )

            val result = jetbrainsApi.file.openFile(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] openFile failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowDiffRequest.parseFrom(dataBytes)
            logger.info { "📝 [JetBrains] showDiff: ${req.filePath}" }

            val request = com.asakii.rpc.api.JetBrainsShowDiffRequest(
                filePath = req.filePath,
                oldContent = req.oldContent,
                newContent = req.newContent,
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowMultiEditDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowMultiEditDiffRequest.parseFrom(dataBytes)
            logger.info { "📝 [JetBrains] showMultiEditDiff: ${req.filePath} (${req.editsCount} edits)" }

            val request = com.asakii.rpc.api.JetBrainsShowMultiEditDiffRequest(
                filePath = req.filePath,
                edits = req.editsList.map { edit ->
                    com.asakii.rpc.api.JetBrainsEditOperation(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                },
                currentContent = if (req.hasCurrentContent()) req.currentContent else null
            )

            val result = jetbrainsApi.file.showMultiEditDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showMultiEditDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowEditPreviewDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowEditPreviewRequest.parseFrom(dataBytes)
            logger.info { "👀 [JetBrains] showEditPreviewDiff: ${req.filePath} (${req.editsCount} edits)" }

            val request = com.asakii.rpc.api.JetBrainsShowEditPreviewRequest(
                filePath = req.filePath,
                edits = req.editsList.map { edit ->
                    com.asakii.rpc.api.JetBrainsEditOperation(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                },
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showEditPreviewDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showEditPreviewDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowMarkdown(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowMarkdownRequest.parseFrom(dataBytes)
            logger.info("📄 [JetBrains] showMarkdown: ${req.title ?: "Plan Preview"}")

            val request = com.asakii.rpc.api.JetBrainsShowMarkdownRequest(
                content = req.content,
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showMarkdown(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showMarkdown failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowEditFullDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowEditFullDiffRequest.parseFrom(dataBytes)
            logger.info { "📝 [JetBrains] showEditFullDiff: ${req.filePath}" }

            val request = com.asakii.rpc.api.JetBrainsShowEditFullDiffRequest(
                filePath = req.filePath,
                oldString = req.oldString,
                newString = req.newString,
                replaceAll = req.replaceAll,
                title = if (req.hasTitle()) req.title else null,
                originalContent = if (req.hasOriginalContent()) req.originalContent else null
            )

            val result = jetbrainsApi.file.showEditFullDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showEditFullDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetSettings(): Payload {
        return try {
            val settings = AgentSettingsService.getInstance()
            logger.info { "⚙️ [JetBrains] getSettings" }

            // 转换思考级别列表为 Proto 格式
            val thinkingLevelsProto = settings.getAllThinkingLevels().map { level: com.asakii.settings.ThinkingLevelConfig ->
                com.asakii.rpc.proto.ThinkingLevelConfig.newBuilder()
                    .setId(level.id)
                    .setName(level.name)
                    .setTokens(level.tokens)
                    .setIsCustom(level.isCustom)
                    .build()
            }

            // 转换配置选项列表为 Proto 格式
            val codexEffortOptionsProto = settings.getCodexReasoningEffortOptions().map { it.toProto() }
            val codexSummaryOptionsProto = settings.getCodexReasoningSummaryOptions().map { it.toProto() }
            val codexSandboxOptionsProto = settings.getCodexSandboxModeOptions().map { it.toProto() }
            val permissionModeOptionsProto = settings.getPermissionModeOptions().map { it.toProto() }

            val defaultModelInfo = settings.getModelById(settings.defaultModel)
            val defaultModelName = defaultModelInfo?.displayName ?: settings.defaultModel
            val ideSettings = IdeSettings.newBuilder()
                .setDefaultModelId(settings.defaultModel)
                .setDefaultModelName(defaultModelName)
                .setDefaultBypassPermissions(settings.defaultBypassPermissions)
                .setClaudeDefaultAutoCleanupContexts(settings.claudeDefaultAutoCleanupContexts)
                .setCodexDefaultAutoCleanupContexts(settings.codexDefaultAutoCleanupContexts)
                .setEnableUserInteractionMcp(settings.enableUserInteractionMcp)
                .setEnableJetbrainsMcp(settings.enableJetBrainsMcp)
                .setIncludePartialMessages(settings.includePartialMessages)
                .setDefaultThinkingLevel(settings.defaultThinkingLevel)
                .setDefaultThinkingTokens(settings.defaultThinkingTokens)
                .setDefaultThinkingLevelId(settings.defaultThinkingLevelId)
                .addAllThinkingLevels(thinkingLevelsProto)
                .setPermissionMode(settings.permissionMode)
                .setCodexDefaultModelId(settings.codexDefaultModelId)
                .setCodexDefaultReasoningEffort(settings.codexDefaultReasoningEffort)
                .setCodexDefaultReasoningSummary(settings.codexDefaultReasoningSummary)
                .setCodexDefaultSandboxMode(settings.codexDefaultSandboxMode)
                // 配置选项列表
                .addAllCodexReasoningEffortOptions(codexEffortOptionsProto)
                .addAllCodexReasoningSummaryOptions(codexSummaryOptionsProto)
                .addAllCodexSandboxModeOptions(codexSandboxOptionsProto)
                .addAllPermissionModeOptions(permissionModeOptionsProto)
                .build()

            val response = GetIdeSettingsResponse.newBuilder()
                .setSettings(ideSettings)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getSettings failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetActiveFile(): Payload {
        return try {
            val activeFile = jetbrainsApi.file.getActiveFile()
            logger.info("📂 [JetBrains] getActiveFile: ${activeFile?.relativePath ?: "null"}")

            val notifyBuilder = ActiveFileChangedNotify.newBuilder()
                .setHasActiveFile(activeFile != null)

            if (activeFile != null) {
                notifyBuilder.setPath(activeFile.path)
                notifyBuilder.setRelativePath(activeFile.relativePath)
                notifyBuilder.setName(activeFile.name)
                activeFile.line?.let { line: Int -> notifyBuilder.setLine(line) }
                activeFile.column?.let { col: Int -> notifyBuilder.setColumn(col) }
                notifyBuilder.setHasSelection(activeFile.hasSelection)
                activeFile.startLine?.let { line: Int -> notifyBuilder.setStartLine(line) }
                activeFile.startColumn?.let { col: Int -> notifyBuilder.setStartColumn(col) }
                activeFile.endLine?.let { line: Int -> notifyBuilder.setEndLine(line) }
                activeFile.endColumn?.let { col: Int -> notifyBuilder.setEndColumn(col) }
                activeFile.selectedContent?.let { content: String -> notifyBuilder.setSelectedContent(content) }
            }

            buildPayload { data(notifyBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getActiveFile failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetTheme(): Payload {
        return try {
            val theme = jetbrainsApi.theme.get()
                ?: return buildErrorResponse("Theme not available")
            logger.info { "🎨 [JetBrains] getTheme" }

            val protoTheme = IdeThemeProto.newBuilder()
                .setBackground(theme.background)
                .setForeground(theme.foreground)
                .setBorderColor(theme.borderColor)
                .setPanelBackground(theme.panelBackground)
                .setTextFieldBackground(theme.textFieldBackground)
                .setSelectionBackground(theme.selectionBackground)
                .setSelectionForeground(theme.selectionForeground)
                .setLinkColor(theme.linkColor)
                .setErrorColor(theme.errorColor)
                .setWarningColor(theme.warningColor)
                .setSuccessColor(theme.successColor)
                .setSeparatorColor(theme.separatorColor)
                .setHoverBackground(theme.hoverBackground)
                .setAccentColor(theme.accentColor)
                .setInfoBackground(theme.infoBackground)
                .setCodeBackground(theme.codeBackground)
                .setSecondaryForeground(theme.secondaryForeground)
                .setFontFamily(theme.fontFamily)
                .setFontSize(theme.fontSize)
                .setEditorFontFamily(theme.editorFontFamily)
                .setEditorFontSize(theme.editorFontSize)
                .build()

            val response = JetBrainsGetThemeResponse.newBuilder()
                .setTheme(protoTheme)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getTheme failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetLocale(): Payload {
        return try {
            val locale = jetbrainsApi.locale.get()
            logger.info { "🌐 [JetBrains] getLocale: $locale" }

            val response = JetBrainsGetLocaleResponse.newBuilder()
                .setLocale(locale)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getLocale failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleSetLocale(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoSetLocaleRequest.parseFrom(dataBytes)
            logger.info { "🌐 [JetBrains] setLocale: ${req.locale}" }

            val result = jetbrainsApi.locale.set(req.locale)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] setLocale failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetProjectPath(): Payload {
        return try {
            val projectPath = jetbrainsApi.project.getPath()
            logger.info { "📁 [JetBrains] getProjectPath: $projectPath" }

            val response = JetBrainsGetProjectPathResponse.newBuilder()
                .setProjectPath(projectPath)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getProjectPath failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleReportSessionState(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoSessionState.parseFrom(dataBytes)
            logger.info { "📊 [JetBrains] reportSessionState: ${req.sessionsCount} sessions" }

            val state = JetBrainsSessionState(
                sessions = req.sessionsList.map { session ->
                    JetBrainsSessionSummary(
                        id = session.id,
                        title = session.title,
                        sessionId = if (session.hasSessionId()) session.sessionId else null,
                        isGenerating = session.isGenerating,
                        isConnected = session.isConnected,
                        isConnecting = session.isConnecting
                    )
                },
                activeSessionId = if (req.hasActiveSessionId()) req.activeSessionId else null
            )

            jetbrainsApi.session.receiveState(state)
            buildOperationResponse(true, null)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] reportSessionState failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    /**
     * 获取文件修改前的原始内容
     * 使用 LocalHistory Label 缓存
     */
    private fun handleGetOriginalContent(dataBytes: ByteArray): Payload {
        return try {
            // dataBytes 直接是 toolUseId 字符串
            val toolUseId = String(dataBytes, Charsets.UTF_8)
            logger.info { "📄 [JetBrains] getOriginalContent: toolUseId=$toolUseId" }

            val content = FileChangeLabelCache.getOriginalContent(toolUseId)

            // 构建响应：success + content（可能为 null）
            val responseBuilder = com.asakii.rpc.proto.JetBrainsGetOriginalContentResponse.newBuilder()
                .setSuccess(true)
                .setFound(content != null)

            if (content != null) {
                responseBuilder.setContent(content)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getOriginalContent failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    /**
     * 获取文件历史内容（基于时间戳查询 LocalHistory）
     * 用于历史会话加载时的 Diff 显示
     */
    private fun handleGetFileHistoryContent(dataBytes: ByteArray): Payload {
        return try {
            val req = com.asakii.rpc.proto.JetBrainsGetFileHistoryContentRequest.parseFrom(dataBytes)
            logger.info { "📄 [JetBrains] getFileHistoryContent: ${req.filePath} (before: ${req.beforeTimestamp})" }

            // 将相对路径转换为绝对路径
            val absolutePath = resolvePath(req.filePath)
            val content = com.asakii.plugin.services.FileHistoryService.getContentBefore(
                absolutePath,
                req.beforeTimestamp
            )

            val responseBuilder = com.asakii.rpc.proto.JetBrainsGetFileHistoryContentResponse.newBuilder()
                .setSuccess(true)
                .setFound(content != null)

            if (content != null) {
                responseBuilder.setContent(content)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getFileHistoryContent failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    /**
     * 回滚文件到指定时间戳之前的版本
     * 用于前端文件回滚功能
     */
    private fun handleRollbackFile(dataBytes: ByteArray): Payload {
        return try {
            val req = com.asakii.rpc.proto.JetBrainsRollbackFileRequest.parseFrom(dataBytes)
            logger.info { "↩️ [JetBrains] rollbackFile: ${req.filePath} (before: ${req.beforeTimestamp})" }

            // 将相对路径转换为绝对路径
            val absolutePath = resolvePath(req.filePath)
            
            // beforeTimestamp == 0 表示新建文件的回滚，需要删除文件
            val result = if (req.beforeTimestamp == 0L) {
                logger.info { "↩️ [JetBrains] deleteFile (rollback new file): $absolutePath" }
                com.asakii.plugin.services.FileHistoryService.deleteFile(absolutePath)
            } else {
                com.asakii.plugin.services.FileHistoryService.rollbackToTimestamp(
                    absolutePath,
                    req.beforeTimestamp
                )
            }

            val responseBuilder = com.asakii.rpc.proto.JetBrainsRollbackFileResponse.newBuilder()
                .setSuccess(result.success)

            if (result.error != null) {
                responseBuilder.setError(result.error)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] rollbackFile failed: ${e.message}" }
            val response = com.asakii.rpc.proto.JetBrainsRollbackFileResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
            buildPayload { data(response.toByteArray()) }
        }
    }

    /**
     * 批量回滚文件（流式返回结果）
     * 用于前端"回滚所有"功能
     */
    private fun handleBatchRollback(dataBytes: ByteArray): Flow<Payload> = flow {
        val req = ProtoBatchRollbackRequest.parseFrom(dataBytes)
        logger.info { "↩️ [JetBrains] batchRollback: ${req.itemsCount} items" }

        for (item in req.itemsList) {
            val filePath = item.filePath
            val beforeTimestamp = item.beforeTimestamp
            val toolUseId = item.toolUseId

            // 发送"开始回滚"事件
            emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_STARTED, null))

            try {
                // 将相对路径转换为绝对路径
                val absolutePath = resolvePath(filePath)

                // beforeTimestamp == 0 表示新建文件的回滚，需要删除文件
                val result = if (beforeTimestamp == 0L) {
                    logger.info { "↩️ [JetBrains] deleteFile (rollback new file): $absolutePath" }
                    com.asakii.plugin.services.FileHistoryService.deleteFile(absolutePath)
                } else {
                    com.asakii.plugin.services.FileHistoryService.rollbackToTimestamp(
                        absolutePath,
                        beforeTimestamp
                    )
                }

                // 发送结果事件
                if (result.success) {
                    logger.info { "✅ [JetBrains] rollback success: $filePath ($toolUseId)" }
                    emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_SUCCESS, null))
                } else {
                    logger.warn { "❌ [JetBrains] rollback failed: $filePath - ${result.error}" }
                    emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_FAILED, result.error))
                }
            } catch (e: Exception) {
                logger.error { "❌ [JetBrains] rollback exception: $filePath - ${e.message}" }
                emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_FAILED, e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * 构建回滚事件 Payload
     */
    private fun buildRollbackEvent(
        filePath: String,
        toolUseId: String,
        status: RollbackStatus,
        error: String?
    ): Payload {
        val builder = JetBrainsBatchRollbackEvent.newBuilder()
            .setFilePath(filePath)
            .setToolUseId(toolUseId)
            .setStatus(status)

        if (error != null) {
            builder.setError(error)
        }

        return buildPayload { data(builder.build().toByteArray()) }
    }

    // ==================== Terminal 后台执行 ====================

    /**
     * 获取可后台化的终端任务
     */
    private fun handleGetBackgroundableTerminals(dataBytes: ByteArray): Payload {
        return try {
            val terminalServer = terminalMcpServerProvider?.getServer() as? TerminalMcpServerImpl
            if (terminalServer == null) {
                logger.warn { "⚠️ [JetBrains] Terminal MCP Server not available" }
                return buildPayload {
                    data(JetBrainsGetBackgroundableTerminalsResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Terminal MCP Server not available")
                        .build().toByteArray())
                }
            }

            val tasks = terminalServer.sessionManager.getBackgroundableTasks()
            logger.info { "📋 [JetBrains] getBackgroundableTerminals: returning ${tasks.size} tasks to frontend" }

            val response = JetBrainsGetBackgroundableTerminalsResponse.newBuilder()
                .setSuccess(true)
                .addAllTerminals(tasks.map { task ->
                    JetBrainsBackgroundableTerminal.newBuilder()
                        .setSessionId(task.sessionId)
                        .setToolUseId(task.toolUseId)
                        .setCommand(task.command)
                        .setStartTime(task.startTime)
                        .setElapsedMs(task.getElapsedMs())
                        .build()
                })
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getBackgroundableTerminals failed: ${e.message}" }
            buildPayload {
                data(JetBrainsGetBackgroundableTerminalsResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.message ?: "Unknown error")
                    .build().toByteArray())
            }
        }
    }

    /**
     * 批量后台终端任务（流式返回结果）
     */
    private fun handleTerminalBackground(dataBytes: ByteArray): Flow<Payload> = flow {
        val req = ProtoTerminalBackgroundRequest.parseFrom(dataBytes)
        logger.info { "⏸️ [JetBrains] terminalBackground: ${req.itemsCount} items" }

        val terminalServer = terminalMcpServerProvider?.getServer() as? TerminalMcpServerImpl
        if (terminalServer == null) {
            logger.warn { "⚠️ [JetBrains] Terminal MCP Server not available" }
            emit(buildTerminalBackgroundEvent("", "", TerminalBackgroundStatus.TERMINAL_BG_FAILED, "Terminal MCP Server not available"))
            return@flow
        }

        for (item in req.itemsList) {
            val sessionId = item.sessionId
            val toolUseId = item.toolUseId

            // 发送"开始后台化"事件
            emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_STARTED, null))

            try {
                val success = terminalServer.sessionManager.markTaskAsBackground(toolUseId)
                
                if (success) {
                    logger.info { "✅ [JetBrains] terminal background success: $toolUseId" }
                    emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_SUCCESS, null))
                } else {
                    logger.warn { "❌ [JetBrains] terminal background failed: $toolUseId - Task not found" }
                    emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_FAILED, "Task not found"))
                }
            } catch (e: Exception) {
                logger.error { "❌ [JetBrains] terminal background exception: $toolUseId - ${e.message}" }
                emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_FAILED, e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * 构建终端后台事件 Payload
     */
    private fun buildTerminalBackgroundEvent(
        sessionId: String,
        toolUseId: String,
        status: TerminalBackgroundStatus,
        error: String?
    ): Payload {
        val builder = JetBrainsTerminalBackgroundEvent.newBuilder()
            .setSessionId(sessionId)
            .setToolUseId(toolUseId)
            .setStatus(status)

        if (error != null) {
            builder.setError(error)
        }

        return buildPayload { data(builder.build().toByteArray()) }
    }

    // ==================== 反向调用（后端 → 前端）====================

    /**
     * 推送主题变化到前端
     */
    suspend fun pushThemeChanged(theme: JetBrainsIdeTheme) = pushHandlers.pushThemeChanged(theme)

    /**
     * 推送设置变更到前端
     */
    suspend fun pushSettingsChanged(settings: AgentSettingsService) = pushHandlers.pushSettingsChanged(settings)

    /**
     * 推送会话命令到前端
     */
    suspend fun pushSessionCommand(command: JetBrainsSessionCommand) = pushHandlers.pushSessionCommand(command)

    /**
     * 推送终端任务更新到前端
     */
    suspend fun pushTerminalTaskUpdate(
        toolUseId: String,
        sessionId: String,
        action: String,
        command: String,
        isBackground: Boolean,
        startTime: Long,
        elapsedMs: Long? = null
    ) = pushHandlers.pushTerminalTaskUpdate(toolUseId, sessionId, action, command, isBackground, startTime, elapsedMs)

    /**
     * 推送活跃文件变更到前端
     */
    suspend fun pushActiveFileChanged(activeFile: ActiveFileInfo?) = pushHandlers.pushActiveFileChanged(activeFile)

    // ==================== 辅助方法 ====================

    private fun extractRoute(payload: Payload): String {
        val metadata = payload.metadata ?: throw IllegalArgumentException("Missing metadata")
        val metadataBytes = metadata.readByteArray()
        if (metadataBytes.isEmpty()) {
            throw IllegalArgumentException("Empty metadata")
        }

        val length = metadataBytes[0].toInt() and 0xFF
        return String(metadataBytes, 1, length, Charsets.UTF_8)
    }

    private fun buildOperationResponse(success: Boolean, error: String?): Payload {
        val response = JetBrainsOperationResponse.newBuilder().apply {
            this.success = success
            error?.let { this.error = it }
        }.build()

        return buildPayload { data(response.toByteArray()) }
    }

    private fun buildErrorResponse(error: String): Payload {
        return buildOperationResponse(false, error)
    }

    /**
     * 解析文件路径，将相对路径转换为绝对路径
     */
    private fun resolvePath(path: String): String {
        val projectPath = jetbrainsApi.project.getPath()
        return com.asakii.plugin.util.PathResolver.resolve(path, projectPath)
    }
}

/**
 * 将 OptionConfig 转换为 Proto 格式
 */
private fun OptionConfig.toProto(): OptionConfigProto {
    return OptionConfigProto.newBuilder()
        .setId(this.id)
        .setLabel(this.label)
        .setDescription(this.description)
        .setIsDefault(this.isDefault)
        .build()
}
