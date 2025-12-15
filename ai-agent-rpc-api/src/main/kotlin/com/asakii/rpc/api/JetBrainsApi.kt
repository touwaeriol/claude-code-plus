package com.asakii.rpc.api

import kotlinx.serialization.Serializable

/**
 * JetBrains IDE 集成 API 接口
 *
 * 使用组合模式按功能分组：
 * - jetbrainsApi.capabilities.isSupported()
 * - jetbrainsApi.file.openFile(...)
 * - jetbrainsApi.theme.get()
 * - jetbrainsApi.session.getState()
 * - jetbrainsApi.locale.get()
 */
interface JetBrainsApi {
    val capabilities: JetBrainsCapabilitiesApi
    val file: JetBrainsFileApi
    val theme: JetBrainsThemeApi
    val session: JetBrainsSessionApi
    val locale: JetBrainsLocaleApi
    val project: JetBrainsProjectApi
}

// ========== 能力检测 API ==========

interface JetBrainsCapabilitiesApi {
    fun isSupported(): Boolean
    fun get(): JetBrainsCapabilities
}

// ========== 文件操作 API ==========

interface JetBrainsFileApi {
    fun openFile(request: JetBrainsOpenFileRequest): Result<Unit>
    fun showDiff(request: JetBrainsShowDiffRequest): Result<Unit>
    fun showMultiEditDiff(request: JetBrainsShowMultiEditDiffRequest): Result<Unit>

    /** 展示编辑预览 diff（从文件读取当前内容，应用编辑后显示 diff） */
    fun showEditPreviewDiff(request: JetBrainsShowEditPreviewRequest): Result<Unit>

    /** 展示 Markdown 内容（用于计划预览） */
    fun showMarkdown(request: JetBrainsShowMarkdownRequest): Result<Unit>
}

// ========== 主题 API ==========

interface JetBrainsThemeApi {
    fun get(): JetBrainsIdeTheme?
    fun addChangeListener(listener: (JetBrainsIdeTheme) -> Unit): () -> Unit
}

// ========== 会话管理 API ==========

interface JetBrainsSessionApi {
    /** 接收前端上报的会话状态 */
    fun receiveState(state: JetBrainsSessionState)

    /** 获取当前会话状态 */
    fun getState(): JetBrainsSessionState?

    /** 注册会话状态监听器 */
    fun addStateListener(listener: (JetBrainsSessionState) -> Unit): () -> Unit

    /** 发送会话命令到前端 */
    fun sendCommand(command: JetBrainsSessionCommand)

    /** 注册会话命令监听器（前端使用） */
    fun addCommandListener(listener: (JetBrainsSessionCommand) -> Unit): () -> Unit
}

// ========== 语言设置 API ==========

interface JetBrainsLocaleApi {
    fun get(): String
    fun set(locale: String): Result<Unit>
}

// ========== 项目信息 API ==========

interface JetBrainsProjectApi {
    fun getPath(): String
}

// ========== 默认实现（不支持 JetBrains 集成时使用）==========

object DefaultJetBrainsApi : JetBrainsApi {
    override val capabilities = object : JetBrainsCapabilitiesApi {
        override fun isSupported() = false
        override fun get() = JetBrainsCapabilities(supported = false)
    }

    override val file = object : JetBrainsFileApi {
        private val error = UnsupportedOperationException("JetBrains integration not available")
        override fun openFile(request: JetBrainsOpenFileRequest) = Result.failure<Unit>(error)
        override fun showDiff(request: JetBrainsShowDiffRequest) = Result.failure<Unit>(error)
        override fun showMultiEditDiff(request: JetBrainsShowMultiEditDiffRequest) = Result.failure<Unit>(error)
        override fun showEditPreviewDiff(request: JetBrainsShowEditPreviewRequest) = Result.failure<Unit>(error)
        override fun showMarkdown(request: JetBrainsShowMarkdownRequest) = Result.failure<Unit>(error)
    }

    override val theme = object : JetBrainsThemeApi {
        override fun get(): JetBrainsIdeTheme? = null
        override fun addChangeListener(listener: (JetBrainsIdeTheme) -> Unit) = { }
    }

    override val session = object : JetBrainsSessionApi {
        override fun receiveState(state: JetBrainsSessionState) {}
        override fun getState(): JetBrainsSessionState? = null
        override fun addStateListener(listener: (JetBrainsSessionState) -> Unit) = { }
        override fun sendCommand(command: JetBrainsSessionCommand) {}
        override fun addCommandListener(listener: (JetBrainsSessionCommand) -> Unit) = { }
    }

    override val locale = object : JetBrainsLocaleApi {
        override fun get() = "en-US"
        override fun set(locale: String) = Result.failure<Unit>(
            UnsupportedOperationException("JetBrains integration not available")
        )
    }

    override val project = object : JetBrainsProjectApi {
        override fun getPath() = ""
    }
}

// ========== 数据模型 ==========

data class JetBrainsCapabilities(
    val supported: Boolean,
    val version: String = "1.0"
)

data class JetBrainsOpenFileRequest(
    val filePath: String,
    val line: Int? = null,
    val column: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null
)

data class JetBrainsShowDiffRequest(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val title: String? = null
)

data class JetBrainsEditOperation(
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
)

data class JetBrainsShowMultiEditDiffRequest(
    val filePath: String,
    val edits: List<JetBrainsEditOperation>,
    val currentContent: String? = null
)

/** 编辑预览请求（从文件读取当前内容，应用编辑后显示 diff） */
data class JetBrainsShowEditPreviewRequest(
    val filePath: String,
    val edits: List<JetBrainsEditOperation>,
    val title: String? = null
)

/** Markdown 内容显示请求 */
data class JetBrainsShowMarkdownRequest(
    val content: String,
    val title: String? = null
)

@Serializable
data class JetBrainsIdeTheme(
    val background: String,
    val foreground: String,
    val borderColor: String,
    val panelBackground: String,
    val textFieldBackground: String,
    val selectionBackground: String,
    val selectionForeground: String,
    val linkColor: String,
    val errorColor: String,
    val warningColor: String,
    val successColor: String,
    val separatorColor: String,
    val hoverBackground: String,
    val accentColor: String,
    val infoBackground: String,
    val codeBackground: String,
    val secondaryForeground: String,
    val fontFamily: String,
    val fontSize: Int,
    val editorFontFamily: String,
    val editorFontSize: Int
)

data class JetBrainsSessionSummary(
    val id: String,
    val title: String,
    val sessionId: String? = null,
    val isGenerating: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false
)

data class JetBrainsSessionState(
    val sessions: List<JetBrainsSessionSummary>,
    val activeSessionId: String? = null
)

enum class JetBrainsSessionCommandType {
    UNSPECIFIED,
    SWITCH,
    CREATE,
    CLOSE,
    RENAME,
    TOGGLE_HISTORY,
    SET_LOCALE,
    DELETE,  // 删除历史会话（直接删除文件，不需要同步）
    RESET    // 重置/清空当前会话（不新建 Tab）
}

data class JetBrainsSessionCommand(
    val type: JetBrainsSessionCommandType,
    val sessionId: String? = null,
    val newName: String? = null,
    val locale: String? = null
)
