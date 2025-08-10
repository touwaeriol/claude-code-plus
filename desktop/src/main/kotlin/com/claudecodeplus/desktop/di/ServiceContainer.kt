package com.claudecodeplus.desktop.di

import com.claudecodeplus.desktop.DesktopProjectService
import com.claudecodeplus.desktop.SimpleFileIndexService
import com.claudecodeplus.desktop.state.AppUiState
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.session.UnifiedSessionAPI
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.services.*
import com.claudecodeplus.core.interfaces.ProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 一个简单的服务定位器，用于管理应用的单例服务。
 */
object ServiceContainer {
    // 创建一个专用的协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 核心服务
    val cliWrapper: ClaudeCliWrapper by lazy { ClaudeCliWrapper() }
    val sessionManager: ClaudeSessionManager by lazy { ClaudeSessionManager() }
    val unifiedSessionAPI: UnifiedSessionAPI by lazy { UnifiedSessionAPI(applicationScope) }
    val unifiedSessionServiceProvider: UnifiedSessionServiceProvider by lazy { 
        UnifiedSessionServiceProvider(applicationScope)
    }

    // 项目与UI服务 - 使用自动加载模式，传入 UnifiedSessionAPI 用于项目监听
    val projectManager: ProjectManager by lazy { ProjectManager(autoLoad = true, sessionAPI = unifiedSessionAPI) }
    val tabManager: ChatTabManager by lazy { ChatTabManager() }
    val exportService: ChatExportService by lazy { ChatExportService() }
    val templateManager: PromptTemplateManager by lazy { PromptTemplateManager() }
    val contextTemplateManager: ContextTemplateManager by lazy { ContextTemplateManager() }

    // UI 状态
    val appUiState: AppUiState by lazy { AppUiState() }
    
    // 桌面特定服务
    val fileIndexService: SimpleFileIndexService by lazy { SimpleFileIndexService() }
    lateinit var projectService: ProjectService
        private set

    /**
     * 初始化必须在UI组合前完成的服务。
     * @param projectPath 当前项目的路径。
     */
    fun initialize(projectPath: String) {
        // 初始化必须立即完成的服务
        projectService = DesktopProjectService(projectPath)
        
        // 在后台启动文件索引和默认标签创建
        applicationScope.launch {
            fileIndexService.initialize(projectPath)
            // 等待项目管理器加载完成后再创建欢迎标签
            if (tabManager.tabs.isEmpty()) {
                // 暂时不创建欢迎标签，让用户选择项目后再创建
                // tabManager.createNewTab("欢迎使用 Claude Code Plus")
            }
        }
    }
}
