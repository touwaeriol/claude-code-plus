package com.asakii.settings

import java.io.File
import java.nio.file.Paths

/**
 * Claude CLI 检测结果
 */
data class ClaudeCliInfo(
    val path: String,
    val version: String? = null
)

/**
 * Node.js 检测结果
 */
data class NodeInfo(
    val path: String,
    val version: String? = null
)

/**
 * Codex 检测结果
 */
data class CodexInfo(
    val path: String,
    val version: String? = null
)

/**
 * 环境检测工具类
 *
 * 用于检测系统中的 Claude CLI、Node.js 和 Codex 安装路径及版本
 */
object EnvironmentDetection {

    /**
     * 检测 Claude CLI 路径和版本
     * @return ClaudeCliInfo 包含路径和版本，未找到返回 null
     */
    fun detectClaudeInfo(): ClaudeCliInfo? {
        val path = detectClaudePath()
        if (path.isEmpty()) return null

        val version = detectClaudeVersion(path)
        return ClaudeCliInfo(path, version)
    }

    /**
     * 自动检测系统中的 Claude CLI 路径
     * @return Claude CLI 可执行文件路径，未找到返回空字符串
     */
    fun detectClaudePath(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        // 1. 通过 login shell 查找
        try {
            val command = if (isWindows) {
                // Windows: 查找 claude.cmd（npm 安装的可执行包装脚本）
                arrayOf("cmd", "/c", "where", "claude.cmd")
            } else {
                val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                arrayOf(defaultShell, "-l", "-c", "which claude")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank()) {
                return result
            }
        } catch (_: Exception) {
            // 忽略错误，继续尝试常见路径
        }

        // 2. 检查常见安装路径
        val commonPaths = if (isWindows) {
            listOf(
                System.getenv("APPDATA")?.let { "$it\\npm\\claude.cmd" },
                System.getenv("LOCALAPPDATA")?.let { "$it\\npm\\claude.cmd" },
                System.getenv("USERPROFILE")?.let { "$it\\.npm-global\\claude.cmd" },
                "C:\\Program Files\\nodejs\\claude.cmd"
            )
        } else {
            listOf(
                "/usr/local/bin/claude",
                "/usr/bin/claude",
                "/opt/homebrew/bin/claude",
                System.getenv("HOME")?.let { "$it/.npm-global/bin/claude" },
                System.getenv("HOME")?.let { "$it/.local/bin/claude" }
            )
        }

        for (path in commonPaths) {
            if (path != null && File(path).exists()) {
                return path
            }
        }

        return ""
    }

    /**
     * 检测 Claude CLI 版本
     */
    private fun detectClaudeVersion(claudePath: String): String? {
        try {
            // Windows 上 .cmd 文件需要通过 cmd /c 启动；Unix 直接执行
            val command = if (claudePath.lowercase().endsWith(".cmd")) {
                arrayOf("cmd", "/c", claudePath, "--version")
            } else {
                arrayOf(claudePath, "--version")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank()) {
                return result
            }
        } catch (_: Exception) {
            // 忽略错误
        }

        return null
    }

    /**
     * 检测 Node.js 路径和版本
     * @return NodeInfo 包含路径和版本，未找到返回 null
     */
    fun detectNodeInfo(): NodeInfo? {
        val path = detectNodePath()
        if (path.isEmpty()) return null

        val version = detectNodeVersion(path)
        return NodeInfo(path, version)
    }

    /**
     * 检测 Codex 路径和版本
     * @return CodexInfo 包含路径和版本，未找到返回 null
     */
    fun detectCodexInfo(): CodexInfo? {
        val path = detectCodexPath()
        if (path.isEmpty()) return null

        val version = detectCodexVersion(path)
        return CodexInfo(path, version)
    }

    /**
     * 自动检测系统中的 Node.js 路径
     * 使用 login shell 执行，以正确加载用户的环境变量（PATH 等）
     * @return Node.js 可执行文件路径，未找到返回空字符串
     */
    fun detectNodePath(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        // 1. 尝试通过 login shell 查找（与运行时逻辑一致）
        try {
            val command = if (isWindows) {
                // Windows: 使用 cmd /c
                arrayOf("cmd", "/c", "where", "node")
            } else {
                // macOS/Linux: 使用 login shell 执行 which node
                val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                arrayOf(defaultShell, "-l", "-c", "which node")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank() && File(result).exists()) {
                return result
            }
        } catch (_: Exception) {
            // 忽略错误，继续尝试其他方式
        }

        // 2. 检查常见安装路径
        val commonPaths = if (isWindows) {
            listOf(
                "C:\\Program Files\\nodejs\\node.exe",
                "C:\\Program Files (x86)\\nodejs\\node.exe",
                System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\node\\node.exe" },
                System.getenv("APPDATA")?.let { "$it\\nvm\\current\\node.exe" },
                System.getenv("NVM_HOME")?.let { "$it\\current\\node.exe" }
            )
        } else {
            listOf(
                "/usr/local/bin/node",
                "/usr/bin/node",
                "/opt/homebrew/bin/node",
                System.getenv("HOME")?.let { "$it/.nvm/current/bin/node" },
                System.getenv("HOME")?.let { "$it/.local/bin/node" }
            )
        }

        for (path in commonPaths) {
            if (path != null && File(path).exists()) {
                return path
            }
        }

        return ""
    }

    /**
     * 自动检测系统中的 Codex 路径
     * @return Codex 可执行文件路径，未找到返回空字符串
     */
    fun detectCodexPath(): String {
        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("win")

        // Search PATH directly
        val pathEnv = System.getenv("PATH") ?: ""
        val pathEntries = pathEnv.split(File.pathSeparatorChar)
        val fileNames = if (isWindows) {
            listOf("codex.exe", "codex.cmd", "codex.bat")
        } else {
            listOf("codex")
        }
        for (dir in pathEntries) {
            if (dir.isBlank()) continue
            for (name in fileNames) {
                val candidate = File(dir, name)
                if (candidate.exists() && candidate.canExecute()) {
                    return candidate.absolutePath
                }
            }
        }

        // Common install paths
        val commonPaths = when {
            isWindows -> listOf(
                "C:\\Program Files\\Codex\\codex.exe",
                "C:\\Program Files (x86)\\Codex\\codex.exe",
                System.getenv("LOCALAPPDATA")?.let { "$it\\Codex\\codex.exe" },
                System.getenv("USERPROFILE")?.let { "$it\\.codex\\codex.exe" }
            )
            os.contains("mac") -> listOf(
                "/usr/local/bin/codex",
                "/opt/homebrew/bin/codex",
                System.getProperty("user.home")?.let { "$it/.codex/codex" },
                "/Applications/Codex.app/Contents/MacOS/codex"
            )
            else -> listOf(
                "/usr/local/bin/codex",
                "/usr/bin/codex",
                System.getProperty("user.home")?.let { "$it/.local/bin/codex" },
                System.getProperty("user.home")?.let { "$it/.codex/codex" }
            )
        }

        for (path in commonPaths) {
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    return file.absolutePath
                }
            }
        }

        // Vendor fallback (dev environment)
        val vendorPath = detectCodexVendorBinary()
        if (vendorPath != null) {
            return vendorPath
        }

        return ""
    }

    /**
     * 检测 Node.js 版本
     * @param nodePath Node.js 可执行文件路径
     * @return 版本号（如 v24.2.0），未检测到返回 null
     */
    private fun detectNodeVersion(nodePath: String): String? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        try {
            val command = if (isWindows) {
                arrayOf("cmd", "/c", nodePath, "--version")
            } else {
                val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                arrayOf(defaultShell, "-l", "-c", "$nodePath --version")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank()) {
                return result
            }
        } catch (_: Exception) {
            // 忽略错误
        }

        return null
    }

    /**
     * 检测 Codex 版本
     * @param codexPath Codex 可执行文件路径
     * @return 版本号，未检测到返回 null
     */
    private fun detectCodexVersion(codexPath: String): String? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        try {
            val command = if (isWindows) {
                arrayOf("cmd", "/c", codexPath, "--version")
            } else {
                arrayOf(codexPath, "--version")
            }

            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLine()?.trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && !result.isNullOrBlank()) {
                return result
            }
        } catch (_: Exception) {
            // ignore
        }

        return null
    }

    /**
     * 检测开发环境中的 Codex vendor 二进制文件
     * @return vendor 路径，未找到返回 null
     */
    private fun detectCodexVendorBinary(): String? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val isWindows = os.contains("win")

        val triple = when {
            isWindows && arch.contains("64") -> "x86_64-pc-windows-msvc"
            os.contains("mac") && arch.contains("aarch64") -> "aarch64-apple-darwin"
            os.contains("mac") && arch.contains("x86") -> "x86_64-apple-darwin"
            os.contains("linux") && arch.contains("aarch64") -> "aarch64-unknown-linux-musl"
            os.contains("linux") && arch.contains("64") -> "x86_64-unknown-linux-musl"
            else -> null
        } ?: return null

        val binaryName = if (isWindows) "codex.exe" else "codex"
        val candidate = Paths.get(
            "external",
            "openai-codex",
            "sdk",
            "vendor",
            triple,
            "codex",
            binaryName
        ).toFile()

        return if (candidate.exists() && candidate.canExecute()) {
            candidate.absolutePath
        } else {
            null
        }
    }
}
