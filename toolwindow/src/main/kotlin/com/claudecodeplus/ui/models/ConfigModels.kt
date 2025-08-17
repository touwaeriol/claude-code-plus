package com.claudecodeplus.ui.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 主配置文件的数据模型，映射 ~/.claude.json 的完整结构。
 */
@Serializable
data class ClaudeConfig(
    @SerialName("numStartups")
    val numStartups: Int = 0,
    val installMethod: String? = null,
    val autoUpdates: Boolean = true,
    val theme: String? = null,
    val tipsHistory: Map<String, Int> = emptyMap(),
    val promptQueueUseCount: Int = 0,
    val firstStartTime: String? = null,
    val userID: String? = null,
    val mcpServers: Map<String, McpConfig> = emptyMap(),
    val projects: Map<String, ProjectConfig> = emptyMap(),
    val oauthAccount: OAuthAccount? = null,
    val isQualifiedForDataSharing: Boolean = false,
    val hasCompletedOnboarding: Boolean = true,
    val lastOnboardingVersion: String? = null,
    val subscriptionNoticeCount: Int = 0,
    val hasAvailableSubscription: Boolean = false,
    val cachedChangelog: String? = null,
    val changelogLastFetched: Long? = null,
    val fallbackAvailableWarningThreshold: Double = 0.5,
    val bypassPermissionsModeAccepted: Boolean = true,
    val lastReleaseNotesSeen: String? = null,
    val hasIdeOnboardingBeenShown: Map<String, Boolean> = emptyMap()
)

/**
 * 单个项目的配置信息。
 */
@Serializable
data class ProjectConfig(
    val history: List<HistoryItem> = emptyList(),
    @SerialName("interaction_log")
    val interactionLog: List<InteractionLog> = emptyList(),
    @SerialName("allowed_tools")
    val allowedTools: List<String> = emptyList(),
    @SerialName("mcp_context_uris")
    val mcpContextUris: List<String> = emptyList(),
    @SerialName("mcp_servers")
    val mcpServers: Map<String, McpConfig> = emptyMap(),
    @SerialName("enabled_mcpjson_servers")
    val enabledMcpjsonServers: List<String> = emptyList(),
    @SerialName("disabled_mcpjson_servers")
    val disabledMcpjsonServers: List<String> = emptyList(),
    @SerialName("has_trust_dialog_accepted")
    val hasTrustDialogAccepted: Boolean = false,
    @SerialName("project_onboarding_seen_count")
    val projectOnboardingSeenCount: Int = 0,
    @SerialName("has_claude_md_external_includes_approved")
    val hasClaudeMdExternalIncludesApproved: Boolean = false,
    @SerialName("has_claude_md_external_includes_warning_shown")
    val hasClaudeMdExternalIncludesWarningShown: Boolean = false,
    @SerialName("has_completed_project_onboarding")
    val hasCompletedProjectOnboarding: Boolean = true,
    @SerialName("last_total_web_search_requests")
    val lastTotalWebSearchRequests: Int = 0,
    @SerialName("usage_count")
    val usageCount: Int = 0
)

/**
 * 历史记录项
 */
@Serializable
data class HistoryItem(
    val display: String,
    val pastedContents: Map<String, PastedContent> = emptyMap()
)

/**
 * 粘贴的内容
 */
@Serializable
data class PastedContent(
    val id: Int,
    val type: String,
    val content: String? = null
)

/**
 * 用户交互日志。
 */
@Serializable
data class InteractionLog(
    val timestamp: String,
    val type: String,
    val content: String
)

/**
 * 用户信息。
 */
@Serializable
data class UserConfig(
    val email: String,
    val org: String? = null,
    val role: String? = null
)

/**
 * OAuth 配置。
 */
@Serializable
data class OAuthConfig(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    val expiry: Long? = null
)

/**
 * OAuth 账户信息
 */
@Serializable
data class OAuthAccount(
    val accountUuid: String,
    val emailAddress: String,
    val organizationUuid: String,
    val organizationRole: String,
    val workspaceRole: String? = null,
    val organizationName: String
)

/**
 * MCP (Micro-service Communication Protocol) 服务器配置。
 */
@Serializable
data class McpConfig(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

// UI层使用的简化模型

/**
 * 项目数据模型
 * 
 * 重要说明：
 * - id: Claude 项目目录名（如 "-Users-erio-codes-webstorm-analysis-claude-code"），
 *       对应 ~/.claude/projects/ 下的实际目录名，是项目的唯一标识符
 * - path: 项目的实际文件系统路径（如 "/Users/erio/codes/webstorm/analysis_claude_code"），
 *       用于显示和文件操作
 * - name: 项目显示名称，通常是路径的最后一段目录名
 * 
 * 设计理念：
 * - 项目ID以 Claude CLI 的目录结构为准，确保会话文件能正确定位
 * - 项目路径保持原始路径，便于用户理解和文件操作
 * - 两者分离设计，避免路径转换的复杂性和错误
 * - 项目直接管理自己的Session对象生命周期
 */
class Project(
    val id: String, // Claude 项目目录名（经过路径编码后的名称）
    val path: String, // 项目的实际文件系统路径
    val name: String = path.substringAfterLast("/"),
    val lastAccessedAt: String? = null // 最后访问时间
) {
    // 使用全局会话管理器，而不是项目独立的会话存储
    companion object {
        private val globalSessionManager = com.claudecodeplus.ui.services.SessionManager()
    }
    
    val sessions: Map<String, SessionObject> 
        get() = globalSessionManager.getAllSessionsForProject(this.id)
    
    /**
     * 获取Session对象
     */
    fun getSession(tabId: String): SessionObject? {
        return globalSessionManager.getSession(this.id, tabId)
    }
    
    /**
     * 获取或创建Session对象
     * 如果是恢复已存在的会话，会主动加载历史消息和状态
     */
    fun getOrCreateSession(
        tabId: String,
        initialSessionId: String? = null,
        initialMessages: List<EnhancedMessage> = emptyList(),
        initialModel: AiModel? = null,
        initialPermissionMode: PermissionMode? = null,
        initialSkipPermissions: Boolean? = null
    ): SessionObject {
        // 使用全局会话管理器
        val sessionObject = globalSessionManager.getOrCreateSession(
            projectId = this.id,
            tabId = tabId,
            initialSessionId = initialSessionId,
            initialMessages = initialMessages,
            initialModel = initialModel,
            initialPermissionMode = initialPermissionMode,
            initialSkipPermissions = initialSkipPermissions,
            project = this
        )
        
        // 如果有sessionId，说明是恢复会话，需要加载历史消息
        if (initialSessionId != null && initialMessages.isEmpty() && sessionObject.messages.isEmpty()) {
            println("[Project] 检测到会话恢复，准备加载历史消息: sessionId=$initialSessionId")
            // 启动协程加载历史消息
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    sessionObject.loadNewMessages(forceFullReload = true)
                    println("[Project] 会话历史加载完成: sessionId=$initialSessionId, messages=${sessionObject.messages.size}")
                } catch (e: Exception) {
                    println("[Project] 会话历史加载失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        return sessionObject
    }
    
    /**
     * 移除Session对象
     */
    fun removeSession(tabId: String): SessionObject? {
        val session = globalSessionManager.getSession(this.id, tabId)
        globalSessionManager.removeSession("$id:$tabId") 
        return session
    }
    
    /**
     * 获取所有Session对象
     */
    fun getAllSessions(): Collection<SessionObject> {
        return globalSessionManager.getAllSessionsForProject(this.id).values
    }
    
    /**
     * 清空该项目的所有Session对象
     */
    fun clearAllSessions() {
        val projectSessions = globalSessionManager.getAllSessionsForProject(this.id)
        projectSessions.keys.forEach { tabId ->
            globalSessionManager.removeSession("$id:$tabId")
        }
    }
}

data class ProjectSession(
    val id: String, // 会话ID，现在在创建时就直接生成UUID
    val projectId: String,
    val name: String,
    val createdAt: String,
    val lastModified: Long = System.currentTimeMillis(), // 最后修改时间（毫秒时间戳）
    val cwd: String // 会话的工作目录，从会话文件中解析得到，现在为必需字段
)
