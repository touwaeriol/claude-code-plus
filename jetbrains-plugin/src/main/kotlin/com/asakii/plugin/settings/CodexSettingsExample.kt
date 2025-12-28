package com.asakii.plugin.settings

import com.intellij.openapi.project.Project
import java.io.File

/**
 * Codex 设置使用示例
 *
 * 演示如何在代码中读取和使用 Codex 设置
 */
class CodexSettingsExample {

    /**
     * 示例 1: 检查 Codex 是否可用
     */
    fun isCodexAvailable(project: Project): Boolean {
        val settings = CodexSettings.getInstance(project)
        return settings.isValid() && settings.enabled
    }

    /**
     * 示例 2: 启动 Codex 进程
     */
    fun startCodexProcess(project: Project): Process? {
        val settings = CodexSettings.getInstance(project)

        if (!settings.isValid()) {
            println("Codex 配置无效: ${settings.binaryPath}")
            return null
        }

        return try {
            ProcessBuilder(settings.binaryPath, "--help")
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            println("启动 Codex 失败: ${e.message}")
            null
        }
    }

    /**
     * 示例 3: 根据模型提供者配置环境变量
     */
    fun getCodexEnvironment(project: Project): Map<String, String> {
        val settings = CodexSettings.getInstance(project)
        val env = mutableMapOf<String, String>()

        when (settings.getModelProviderEnum()) {
            CodexConfigurable.ModelProvider.OPENAI -> {
                // 配置 OpenAI 环境变量
                env["CODEX_MODEL_PROVIDER"] = "openai"
                env["OPENAI_API_KEY"] = System.getenv("OPENAI_API_KEY") ?: ""
            }
            CodexConfigurable.ModelProvider.OLLAMA -> {
                // 配置 Ollama 环境变量
                env["CODEX_MODEL_PROVIDER"] = "ollama"
                env["OLLAMA_HOST"] = System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
            }
            CodexConfigurable.ModelProvider.ANTHROPIC -> {
                // 配置 Anthropic 环境变量
                env["CODEX_MODEL_PROVIDER"] = "anthropic"
                env["ANTHROPIC_API_KEY"] = System.getenv("ANTHROPIC_API_KEY") ?: ""
            }
            CodexConfigurable.ModelProvider.CUSTOM -> {
                // 自定义提供者配置
                env["CODEX_MODEL_PROVIDER"] = "custom"
            }
        }

        return env
    }

    /**
     * 示例 4: 根据沙箱模式配置 Codex 参数
     */
    fun getCodexSandboxArgs(project: Project): List<String> {
        val settings = CodexSettings.getInstance(project)
        val args = mutableListOf<String>()

        when (settings.getSandboxModeEnum()) {
            CodexConfigurable.SandboxMode.READ_ONLY -> {
                args.add("--sandbox-mode=readonly")
            }
            CodexConfigurable.SandboxMode.WORKSPACE_WRITE -> {
                args.add("--sandbox-mode=workspace")
                args.add("--workspace=${project.basePath}")
            }
            CodexConfigurable.SandboxMode.FULL_ACCESS -> {
                args.add("--sandbox-mode=full")
            }
        }

        return args
    }

    /**
     * 示例 5: 完整的 Codex 进程启动
     */
    fun launchCodexSession(project: Project, threadId: String? = null): Process? {
        val settings = CodexSettings.getInstance(project)

        if (!settings.isValid()) {
            println("Codex 未正确配置")
            return null
        }

        if (!settings.enabled) {
            println("Codex 未启用")
            return null
        }

        // 构建命令行参数
        val command = mutableListOf(settings.binaryPath)

        // 添加沙箱参数
        command.addAll(getCodexSandboxArgs(project))

        // 如果有现有线程，则恢复会话
        if (threadId != null) {
            command.add("--resume=$threadId")
        }

        // 构建环境变量
        val env = getCodexEnvironment(project)

        return try {
            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(false)

            // 设置环境变量
            processBuilder.environment().putAll(env)

            // 设置工作目录为项目根目录
            processBuilder.directory(File(project.basePath ?: "."))

            processBuilder.start()
        } catch (e: Exception) {
            println("启动 Codex 会话失败: ${e.message}")
            null
        }
    }

    /**
     * 示例 6: 检查并提示用户配置 Codex
     */
    fun checkAndPromptConfig(project: Project): Boolean {
        val settings = CodexSettings.getInstance(project)

        if (settings.binaryPath.isEmpty()) {
            // 提示用户配置 Codex 路径
            println("请在 Settings > Tools > Codex Backend 中配置 Codex 二进制路径")
            return false
        }

        if (!settings.isValid()) {
            println("Codex 二进制文件无效或不可执行: ${settings.binaryPath}")
            return false
        }

        return true
    }

    /**
     * 示例 7: 获取设置摘要用于日志记录
     */
    fun logCodexSettings(project: Project) {
        val settings = CodexSettings.getInstance(project)
        println("=== Codex Settings ===")
        println(settings.getSummary())
        println("======================")
    }

    /**
     * 示例 8: 更新设置（通常在测试后）
     */
    fun updateCodexSettings(
        project: Project,
        binaryPath: String? = null,
        modelProvider: CodexConfigurable.ModelProvider? = null,
        sandboxMode: CodexConfigurable.SandboxMode? = null,
        enabled: Boolean? = null
    ) {
        val settings = CodexSettings.getInstance(project)

        if (binaryPath != null) {
            settings.binaryPath = binaryPath
        }

        if (modelProvider != null) {
            settings.setModelProvider(modelProvider)
        }

        if (sandboxMode != null) {
            settings.setSandboxMode(sandboxMode)
        }

        if (enabled != null) {
            settings.enabled = enabled
        }

        // 设置会自动持久化
        println("Codex 设置已更新")
    }

    /**
     * 示例 9: 测试 Codex 连接并保存结果
     */
    fun testAndSaveConnection(project: Project): Boolean {
        val settings = CodexSettings.getInstance(project)

        if (settings.binaryPath.isEmpty()) {
            settings.lastTestResult = "未配置二进制路径"
            return false
        }

        return try {
            val process = ProcessBuilder(settings.binaryPath, "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                settings.lastTestResult = "成功: ${output.trim()}"
                true
            } else {
                settings.lastTestResult = "失败 (退出码: $exitCode): ${output.trim()}"
                false
            }
        } catch (e: Exception) {
            settings.lastTestResult = "异常: ${e.message}"
            false
        }
    }
}
