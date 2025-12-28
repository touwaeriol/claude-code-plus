package com.asakii.plugin.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Codex 设置服务
 *
 * 使用 IntelliJ Platform 的持久化机制保存 Codex 配置
 */
@State(
    name = "CodexSettings",
    storages = [Storage("codex-settings.xml")]
)
@Service(Service.Level.PROJECT)
class CodexSettings : PersistentStateComponent<CodexSettings> {

    /**
     * Codex 二进制文件路径
     */
    var binaryPath: String = ""

    /**
     * 模型提供者
     */
    var modelProvider: String = CodexConfigurable.ModelProvider.OPENAI.name

    /**
     * 默认沙箱模式
     */
    var sandboxMode: String = CodexConfigurable.SandboxMode.WORKSPACE_WRITE.name

    /**
     * Codex 是否已启用
     */
    var enabled: Boolean = false

    /**
     * 最后一次连接测试的结果
     */
    var lastTestResult: String = ""

    override fun getState(): CodexSettings = this

    override fun loadState(state: CodexSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /**
         * 获取项目级别的 Codex 设置实例
         */
        fun getInstance(project: Project): CodexSettings {
            return project.service<CodexSettings>()
        }
    }

    /**
     * 获取模型提供者枚举
     */
    fun getModelProviderEnum(): CodexConfigurable.ModelProvider {
        return try {
            CodexConfigurable.ModelProvider.valueOf(modelProvider)
        } catch (e: IllegalArgumentException) {
            CodexConfigurable.ModelProvider.OPENAI
        }
    }

    /**
     * 设置模型提供者
     */
    fun setModelProvider(provider: CodexConfigurable.ModelProvider) {
        modelProvider = provider.name
    }

    /**
     * 获取沙箱模式枚举
     */
    fun getSandboxModeEnum(): CodexConfigurable.SandboxMode {
        return try {
            CodexConfigurable.SandboxMode.valueOf(sandboxMode)
        } catch (e: IllegalArgumentException) {
            CodexConfigurable.SandboxMode.WORKSPACE_WRITE
        }
    }

    /**
     * 设置沙箱模式
     */
    fun setSandboxMode(mode: CodexConfigurable.SandboxMode) {
        sandboxMode = mode.name
    }

    /**
     * 检查 Codex 配置是否有效
     */
    fun isValid(): Boolean {
        if (binaryPath.isEmpty()) {
            return false
        }

        val file = java.io.File(binaryPath)
        return file.exists() && file.canExecute()
    }

    /**
     * 获取配置摘要信息
     */
    fun getSummary(): String {
        return buildString {
            append("Binary: $binaryPath\n")
            append("Provider: ${getModelProviderEnum().displayName}\n")
            append("Sandbox: ${getSandboxModeEnum().displayName}\n")
            append("Enabled: $enabled\n")
            if (lastTestResult.isNotEmpty()) {
                append("Last Test: $lastTestResult")
            }
        }
    }
}
