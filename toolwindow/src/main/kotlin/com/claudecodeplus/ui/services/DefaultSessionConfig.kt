package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences

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
    
    private val prefs: Preferences = Preferences.userRoot().node(PREF_NODE)
    
    /**
     * 默认 AI 模型
     */
    var defaultModel by mutableStateOf(AiModel.OPUS)
        private set
    
    /**
     * 默认权限模式
     */
    var defaultPermissionMode by mutableStateOf(PermissionMode.BYPASS)
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
            // 加载默认模型
            val modelName = prefs.get(KEY_DEFAULT_MODEL, AiModel.OPUS.name)
            defaultModel = AiModel.values().find { it.name == modelName } ?: AiModel.OPUS
            
            // 加载默认权限模式
            val permissionModeName = prefs.get(KEY_DEFAULT_PERMISSION_MODE, PermissionMode.BYPASS.name)
            defaultPermissionMode = PermissionMode.values().find { it.name == permissionModeName } 
                ?: PermissionMode.BYPASS
            
            // 加载是否跳过权限
            defaultSkipPermissions = prefs.getBoolean(KEY_DEFAULT_SKIP_PERMISSIONS, true)
            
            println("[DefaultSessionConfig] 加载默认配置:")
            println("  - 默认模型: $defaultModel")
            println("  - 默认权限模式: $defaultPermissionMode")
            println("  - 跳过权限确认: $defaultSkipPermissions")
        } catch (e: Exception) {
            println("[DefaultSessionConfig] 加载配置失败: ${e.message}")
            e.printStackTrace()
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
            
            println("[DefaultSessionConfig] 保存默认配置成功")
        } catch (e: Exception) {
            println("[DefaultSessionConfig] 保存配置失败: ${e.message}")
            e.printStackTrace()
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
        defaultModel = AiModel.OPUS
        defaultPermissionMode = PermissionMode.BYPASS
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