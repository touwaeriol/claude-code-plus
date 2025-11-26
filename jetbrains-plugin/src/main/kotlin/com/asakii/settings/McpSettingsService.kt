package com.asakii.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * MCP 配置持久化服务
 */
@State(
    name = "ClaudeCodePlusMcpSettings",
    storages = [Storage("claude-code-plus-mcp.xml")]
)
@Service
class McpSettingsService : PersistentStateComponent<McpSettingsService.State> {
    
    data class State(
        var userConfig: String = "",
        var localConfig: String = ""
    )
    
    private var state = State()
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }
    
    fun getUserConfig(): String = state.userConfig
    
    fun setUserConfig(config: String) {
        state.userConfig = config
    }
    
    fun getLocalConfig(): String = state.localConfig
    
    fun setLocalConfig(config: String) {
        state.localConfig = config
    }
    
    fun getProjectConfig(project: Project?): String {
        return project?.let {
            it.service<ProjectMcpSettingsService>().getConfig()
        } ?: ""
    }
    
    fun setProjectConfig(project: Project?, config: String) {
        project?.let {
            it.service<ProjectMcpSettingsService>().setConfig(config)
        }
    }
    
    /**
     * 获取合并后的 MCP 配置
     * 优先级：Project > Local > User
     */
    fun getMergedConfig(project: Project?): String {
        val projectConfig = getProjectConfig(project).takeIf { it.isNotBlank() }
        val localConfig = getLocalConfig().takeIf { it.isNotBlank() }
        val userConfig = getUserConfig().takeIf { it.isNotBlank() }
        
        // 如果项目级别有配置，直接使用项目配置
        if (projectConfig != null) return projectConfig
        
        // 如果本地级别有配置，直接使用本地配置
        if (localConfig != null) return localConfig
        
        // 使用用户级别配置
        return userConfig ?: "{}"
    }
}

/**
 * 项目级别的 MCP 配置服务
 */
@State(
    name = "ClaudeCodePlusProjectMcpSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
@Service(Service.Level.PROJECT)
class ProjectMcpSettingsService : PersistentStateComponent<ProjectMcpSettingsService.State> {
    
    data class State(
        var config: String = ""
    )
    
    private var state = State()
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }
    
    fun getConfig(): String = state.config
    
    fun setConfig(config: String) {
        state.config = config
    }
}