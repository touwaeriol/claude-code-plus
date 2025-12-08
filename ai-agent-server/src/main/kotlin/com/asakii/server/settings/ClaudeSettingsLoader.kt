package com.asakii.server.settings

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Claude settings 加载与合并工具。
 */
private val logger = KotlinLogging.logger {}

object ClaudeSettingsLoader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * 简单缓存，避免频繁 IO。
     * key = (userHome, projectPath 或 null) 的字符串表示。
     */
    private val cache = ConcurrentHashMap<String, ClaudeSettings>()

    fun loadMergedSettings(projectPath: Path?): ClaudeSettings {
        val userHome = System.getProperty("user.home") ?: ""
        val cacheKey = "$userHome|${projectPath?.toAbsolutePath()?.normalize()}"

        return cache.computeIfAbsent(cacheKey) {
            var settings = ClaudeSettings()

            // 1. 用户级
            ClaudeSettingsPaths.userSettingsPath()
                ?.takeIf { Files.isRegularFile(it) }
                ?.let { path -> loadSettingsFile(path)?.also { settings = merge(settings, it) } }

            if (projectPath != null) {
                // 2. 项目级共享
                val projectSettings = ClaudeSettingsPaths.projectSettingsPath(projectPath)
                if (Files.isRegularFile(projectSettings)) {
                    loadSettingsFile(projectSettings)?.also { settings = merge(settings, it) }
                }

                // 3. 项目级本地
                val projectLocalSettings = ClaudeSettingsPaths.projectLocalSettingsPath(projectPath)
                if (Files.isRegularFile(projectLocalSettings)) {
                    loadSettingsFile(projectLocalSettings)?.also { settings = merge(settings, it) }
                }
            }

            settings
        }
    }

    private fun loadSettingsFile(path: Path): ClaudeSettings? {
        return try {
            val text = Files.readString(path)
            json.decodeFromString(ClaudeSettings.serializer(), text)
        } catch (e: Exception) {
            logger.warn { "Failed to load Claude settings from $path: ${e.message}" }
            null
        }
    }

    /**
     * 合并两个设置对象，override 的优先级更高。
     *
     * - 标量: override 非默认/非空则覆盖 base
     * - env: map 合并, 同名 key 由 override 覆盖
     * - list: 叠加并去重 (base + override)
     * - 嵌套对象: 递归/字段级合并
     */
    private fun merge(base: ClaudeSettings, override: ClaudeSettings): ClaudeSettings {
        val defaults = ClaudeSettings()
        return ClaudeSettings(
            apiKeyHelper = override.apiKeyHelper ?: base.apiKeyHelper,
            cleanupPeriodDays = if (override.cleanupPeriodDays != defaults.cleanupPeriodDays)
                override.cleanupPeriodDays else base.cleanupPeriodDays,
            companyAnnouncements = (base.companyAnnouncements + override.companyAnnouncements).distinct(),
            env = base.env + override.env,
            includeCoAuthoredBy = if (override.includeCoAuthoredBy != defaults.includeCoAuthoredBy)
                override.includeCoAuthoredBy else base.includeCoAuthoredBy,
            permissions = mergePermissions(base.permissions, override.permissions),
            hooks = if (override.hooks.isNotEmpty()) override.hooks else base.hooks,
            disableAllHooks = if (override.disableAllHooks != defaults.disableAllHooks)
                override.disableAllHooks else base.disableAllHooks,
            model = override.model ?: base.model,
            statusLine = override.statusLine ?: base.statusLine,
            outputStyle = override.outputStyle ?: base.outputStyle,
            forceLoginMethod = override.forceLoginMethod ?: base.forceLoginMethod,
            forceLoginOrgUUID = override.forceLoginOrgUUID ?: base.forceLoginOrgUUID,
            enableAllProjectMcpServers = override.enableAllProjectMcpServers ?: base.enableAllProjectMcpServers,
            enabledMcpjsonServers = (base.enabledMcpjsonServers + override.enabledMcpjsonServers).distinct(),
            disabledMcpjsonServers = (base.disabledMcpjsonServers + override.disabledMcpjsonServers).distinct(),
            allowedMcpServers = mergeMcpRules(base.allowedMcpServers, override.allowedMcpServers),
            deniedMcpServers = mergeMcpRules(base.deniedMcpServers, override.deniedMcpServers),
            awsAuthRefresh = override.awsAuthRefresh ?: base.awsAuthRefresh,
            awsCredentialExport = override.awsCredentialExport ?: base.awsCredentialExport,
            sandbox = mergeSandbox(base.sandbox, override.sandbox)
        )
    }

    private fun mergePermissions(
        base: PermissionsConfig?,
        override: PermissionsConfig?
    ): PermissionsConfig? {
        if (base == null && override == null) return null
        if (base == null) return override
        if (override == null) return base

        return PermissionsConfig(
            allow = (base.allow + override.allow).distinct(),
            ask = (base.ask + override.ask).distinct(),
            deny = (base.deny + override.deny).distinct(),
            additionalDirectories = (base.additionalDirectories + override.additionalDirectories).distinct(),
            defaultMode = override.defaultMode ?: base.defaultMode,
            disableBypassPermissionsMode = override.disableBypassPermissionsMode
                ?: base.disableBypassPermissionsMode
        )
    }

    private fun mergeMcpRules(
        base: List<McpServerRule>,
        override: List<McpServerRule>
    ): List<McpServerRule> {
        if (override.isEmpty()) return base
        if (base.isEmpty()) return override

        val combined = base + override
        // 依据 serverName 去重，保持顺序：低优先级在前，高优先级在后
        val seen = mutableSetOf<String?>()
        val result = mutableListOf<McpServerRule>()
        for (rule in combined) {
            if (seen.add(rule.serverName)) {
                result.add(rule)
            }
        }
        return result
    }

    private fun mergeSandbox(
        base: SandboxSettings,
        override: SandboxSettings
    ): SandboxSettings {
        val defaults = SandboxSettings()
        return SandboxSettings(
            enabled = if (override.enabled != defaults.enabled) override.enabled else base.enabled,
            autoAllowBashIfSandboxed =
                if (override.autoAllowBashIfSandboxed != defaults.autoAllowBashIfSandboxed)
                    override.autoAllowBashIfSandboxed else base.autoAllowBashIfSandboxed,
            excludedCommands = (base.excludedCommands + override.excludedCommands).distinct(),
            allowUnsandboxedCommands =
                if (override.allowUnsandboxedCommands != defaults.allowUnsandboxedCommands)
                    override.allowUnsandboxedCommands else base.allowUnsandboxedCommands,
            networkAllowUnixSockets =
                (base.networkAllowUnixSockets + override.networkAllowUnixSockets).distinct(),
            networkAllowLocalBinding =
                if (override.networkAllowLocalBinding != defaults.networkAllowLocalBinding)
                    override.networkAllowLocalBinding else base.networkAllowLocalBinding,
            networkHttpProxyPort =
                override.networkHttpProxyPort ?: base.networkHttpProxyPort
        )
    }

    /**
     * 基于合并后的 ClaudeSettings 解析 MAX_THINKING_TOKENS。
     */
    fun resolveMaxThinkingTokens(settings: ClaudeSettings, thinkingEnabled: Boolean): Int {
        if (!thinkingEnabled) return 0

        val raw = settings.env["MAX_THINKING_TOKENS"] ?: return 1024

        return raw.toIntOrNull() ?: run {
            logger.warn { "Invalid MAX_THINKING_TOKENS value: '$raw', fallback to default 1024" }
            1024
        }
    }
}


