package com.asakii.plugin.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable

/**
 * Claude 设置服务
 * 
 * 管理 Claude 相关的配置（API Key、模型等）
 */
@State(
    name = "ClaudeCodePlusSettings",
    storages = [Storage("claudeCodePlus.xml")]
)
@Service(Service.Level.PROJECT)
class ClaudeSettingsService : PersistentStateComponent<ClaudeSettingsService.State> {
    
    @Serializable
    data class State(
        var apiKey: String? = null,
        var model: String = "claude-sonnet-4-5-20250929",
        var maxTokens: Int = 4096,
        var temperature: Double = 0.7,
        var maxTurns: Int = 10,
        var autoApproveTools: Boolean = false,
        var useDarkTheme: Boolean = true
    )
    
    data class Settings(
        val apiKey: String?,
        val model: String,
        val maxTokens: Int,
        val temperature: Double,
        val maxTurns: Int,
        val autoApproveTools: Boolean,
        val useDarkTheme: Boolean
    )
    
    private var state = State()
    
    /**
     * 获取当前设置
     */
    fun getSettings(): Settings {
        return Settings(
            apiKey = state.apiKey,
            model = state.model,
            maxTokens = state.maxTokens,
            temperature = state.temperature,
            maxTurns = state.maxTurns,
            autoApproveTools = state.autoApproveTools,
            useDarkTheme = state.useDarkTheme
        )
    }
    
    /**
     * 更新设置
     */
    fun updateSettings(settings: Settings) {
        state.apiKey = settings.apiKey
        state.model = settings.model
        state.maxTokens = settings.maxTokens
        state.temperature = settings.temperature
        state.maxTurns = settings.maxTurns
        state.autoApproveTools = settings.autoApproveTools
        state.useDarkTheme = settings.useDarkTheme
    }
    
    /**
     * 重置为默认值
     */
    fun resetToDefaults() {
        state = State()
    }
    
    /**
     * 获取 API Key
     */
    fun getApiKey(): String? {
        return state.apiKey
    }
    
    /**
     * 设置 API Key
     */
    fun setApiKey(apiKey: String?) {
        state.apiKey = apiKey
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    companion object {
        fun getInstance(project: Project): ClaudeSettingsService {
            return project.getService(ClaudeSettingsService::class.java)
        }
    }
}


