package com.claudecodeplus.ui.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

data class Project(
    val id: String, // 通常是项目路径
    val path: String,
    val name: String = path.substringAfterLast("/")
)

data class ProjectSession(
    val id: String, // 会话ID
    val projectId: String,
    val name: String,
    val createdAt: String
)
