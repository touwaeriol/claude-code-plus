package com.asakii.server.settings

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Claude settings 配置文件路径工具。
 *
 * 当前支持三个层级（从低到高）：
 * 1. 用户级: ~/.claude/settings.json
 * 2. 项目级共享: <project>/.claude/settings.json
 * 3. 项目级本地: <project>/.claude/settings.local.json
 */
object ClaudeSettingsPaths {

    fun userSettingsPath(): Path? {
        val home = System.getProperty("user.home") ?: return null
        return Paths.get(home, ".claude", "settings.json")
    }

    fun projectSettingsPath(projectPath: Path): Path {
        return projectPath.resolve(".claude").resolve("settings.json")
    }

    fun projectLocalSettingsPath(projectPath: Path): Path {
        return projectPath.resolve(".claude").resolve("settings.local.json")
    }
}





















