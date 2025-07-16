package com.claudecodeplus.ui.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 主配置文件的数据模型，映射 ~/.claude.json 的完整结构。
 */
@Serializable
data class ClaudeConfig(
    @SerialName("startup_count")
    val startupCount: Int = 0,
    val theme: String? = null,
    @SerialName("auto_update_enabled")
    val autoUpdateEnabled: Boolean = true,
    val projects: Map<String, ProjectConfig> = emptyMap(),
    val user: UserConfig? = null,
    val oauth: OAuthConfig? = null,
    val mcp: Map<String, McpConfig> = emptyMap(),
    @SerialName("changelog_cache")
    val changelogCache: Map<String, String> = emptyMap(),
    @SerialName("tips_usage")
    val tipsUsage: Map<String, Int> = emptyMap(),
    @SerialName("prompt_queue_use_count")
    val promptQueueUseCount: Int = 0
)

/**
 * 单个项目的配置信息。
 */
@Serializable
data class ProjectConfig(
    val history: List<String> = emptyList(),
    @SerialName("interaction_log")
    val interactionLog: List<InteractionLog> = emptyList(),
    @SerialName("allowed_tools")
    val allowedTools: List<String> = emptyList(),
    @SerialName("usage_count")
    val usageCount: Int = 0
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
