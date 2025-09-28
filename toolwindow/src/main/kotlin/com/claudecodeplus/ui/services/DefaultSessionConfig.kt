package com.claudecodeplus.ui.services

import com.claudecodeplus.core.logging.*
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Claude全局设置数据类
 */
@Serializable
data class ClaudeSettings(
    val model: String? = null
)

/**
 * 默认会话配置管理器
 *
 * 管理全局的默认会话设置，包括：
 * - 默认 AI 模型
 * - 默认权限模式
 * - 其他会话级别的默认设置
 *
 * 配置会持久化到用户设置中，并在应用启动时加载
 */
object DefaultSessionConfig {
    private const val PREF_NODE = "claude_code_plus/session_defaults"
    private const val KEY_DEFAULT_MODEL = "default_model"
    private const val KEY_DEFAULT_PERMISSION_MODE = "default_permission_mode"
    private const val KEY_DEFAULT_SKIP_PERMISSIONS = "default_skip_permissions"

    // JSON配置用于解析Claude设置文件
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val prefs: Preferences = Preferences.userRoot().node(PREF_NODE)
    
    /**
     * 默认 AI 模型
     */
    var defaultModel by mutableStateOf(AiModel.DEFAULT)
        private set
    
    /**
     * 默认权限模式
     */
    var defaultPermissionMode by mutableStateOf(PermissionMode.DEFAULT)
        private set
    
    /**
     * 默认是否跳过权限确认
     */
    var defaultSkipPermissions by mutableStateOf(true)
        private set
    
    init {
        // 启动时从设置加载
        loadFromSettings()
    }
    
    /**
     * 从用户设置加载配置
     */
    fun loadFromSettings() {
        try {
            // 首先尝试从 Claude 全局配置文件读取
            var modelFromClaude: AiModel? = null
            try {
                modelFromClaude = loadModelFromClaudeSettings()
            } catch (e: Exception) {
    //                 logD("[DefaultSessionConfig] 无法读取 Claude 全局配置: ${e.message}")
            }
            
            // 加载默认模型 - 优先使用 Claude 配置，否则使用插件本地配置
            val modelName = if (modelFromClaude != null) {
    //                 logD("[DefaultSessionConfig] 使用 Claude 全局配置的模型: ${modelFromClaude.displayName}")
                modelFromClaude.name
            } else {
                prefs.get(KEY_DEFAULT_MODEL, AiModel.DEFAULT.name)
            }
            defaultModel = AiModel.values().find { it.name == modelName } ?: modelFromClaude ?: AiModel.DEFAULT
            
            // 加载默认权限模式
            val permissionModeName = prefs.get(KEY_DEFAULT_PERMISSION_MODE, PermissionMode.DEFAULT.name)
            defaultPermissionMode = PermissionMode.values().find { it.name == permissionModeName } 
                ?: PermissionMode.DEFAULT
            
            // 加载是否跳过权限
            defaultSkipPermissions = prefs.getBoolean(KEY_DEFAULT_SKIP_PERMISSIONS, true)
            
    //             logD("[DefaultSessionConfig] 加载默认配置:")
    //             logD("  - 默认模型: $defaultModel")
    //             logD("  - 默认权限模式: $defaultPermissionMode")
    //             logD("  - 跳过权限确认: $defaultSkipPermissions")
        } catch (e: Exception) {
    //             logD("[DefaultSessionConfig] 加载配置失败: ${e.message}")
            logE("Exception caught", e)
        }
    }
    
    /**
     * 从 Claude 全局配置文件读取模型设置
     * 
     * @return 解析出的 AI 模型，如果无法读取则返回 null
     */
    private fun loadModelFromClaudeSettings(): AiModel? {
        val settingsFile = Paths.get(System.getProperty("user.home"), ".claude", "settings.json")
        
        if (!Files.exists(settingsFile)) {
    //             logD("[DefaultSessionConfig] Claude 设置文件不存在: $settingsFile")
            return null
        }
        
        try {
            val content = Files.readString(settingsFile)
            val settings = json.decodeFromString<ClaudeSettings>(content)

            val modelStr = settings.model
            if (modelStr.isNullOrBlank()) {
    //                 logD("[DefaultSessionConfig] Claude 设置文件中没有模型配置")
                return null
            }
            
            // 映射 Claude CLI 模型名称到插件模型枚举
            val aiModel = when (modelStr.lowercase()) {
                "default" -> AiModel.DEFAULT
                "opus" -> AiModel.OPUS
                "sonnet" -> AiModel.SONNET
                "opusplan" -> AiModel.OPUS_PLAN
                "claude-opus-4-20250514" -> AiModel.OPUS_4
                else -> {
    //                     logD("[DefaultSessionConfig] 未知的 Claude 模型: $modelStr, 使用默认模型")
                    null
                }
            }
            
            if (aiModel != null) {
    //                 logD("[DefaultSessionConfig] 从 Claude 设置文件读取到模型: $modelStr -> ${aiModel.displayName}")
            }
            
            return aiModel
        } catch (e: Exception) {
    //             logD("[DefaultSessionConfig] 读取 Claude 设置文件失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 保存配置到用户设置
     */
    fun saveToSettings() {
        try {
            prefs.put(KEY_DEFAULT_MODEL, defaultModel.name)
            prefs.put(KEY_DEFAULT_PERMISSION_MODE, defaultPermissionMode.name)
            prefs.putBoolean(KEY_DEFAULT_SKIP_PERMISSIONS, defaultSkipPermissions)
            prefs.flush()
            
    //             logD("[DefaultSessionConfig] 保存默认配置成功")
        } catch (e: Exception) {
    //             logD("[DefaultSessionConfig] 保存配置失败: ${e.message}")
            logE("Exception caught", e)
        }
    }
    
    /**
     * 更新默认模型
     */
    fun updateDefaultModel(model: AiModel) {
        defaultModel = model
        saveToSettings()
    }
    
    /**
     * 更新默认权限模式
     */
    fun updateDefaultPermissionMode(mode: PermissionMode) {
        defaultPermissionMode = mode
        saveToSettings()
    }
    
    /**
     * 更新是否跳过权限确认
     */
    fun updateDefaultSkipPermissions(skip: Boolean) {
        defaultSkipPermissions = skip
        saveToSettings()
    }
    
    /**
     * 重置为出厂默认值
     */
    fun resetToDefaults() {
        defaultModel = AiModel.DEFAULT
        defaultPermissionMode = PermissionMode.DEFAULT
        defaultSkipPermissions = true
        saveToSettings()
    }
    
    /**
     * 获取当前配置的摘要
     */
    fun getConfigSummary(): String {
        return """
            默认会话配置:
            - AI 模型: ${defaultModel.displayName}
            - 权限模式: ${defaultPermissionMode.displayName}
            - 跳过权限确认: ${if (defaultSkipPermissions) "是" else "否"}
        """.trimIndent()
    }
}
