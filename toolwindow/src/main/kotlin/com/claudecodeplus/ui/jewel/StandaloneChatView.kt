package com.claudecodeplus.ui.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.claudecodeplus.ui.services.UnifiedSessionService
import com.claudecodeplus.session.ClaudeSessionManager
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.core.interfaces.ProjectService
import com.claudecodeplus.ui.services.SessionManager

/**
 * 独立会话的 ChatView 包装器
 * 
 * 为单一会话场景（如插件环境）提供简化的接口，
 * 自动管理 SessionManager 和会话对象，无需外部传递这些参数
 */
@Composable
fun StandaloneChatView(
    unifiedSessionService: UnifiedSessionService,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = ClaudeSessionManager(),
    initialMessages: List<EnhancedMessage>? = null,
    sessionId: String? = null,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,  // 新增：IDE 集成
    backgroundService: Any? = null,  // 新增：后台服务
    sessionStateSync: Any? = null,   // 新增：状态同步器
    onNewSessionRequest: (() -> Unit)? = null,  // 新增：新会话请求回调
    modifier: Modifier = Modifier
) {
    // 为简单用例创建默认的 tabId 和临时 Project
    val defaultTabId = remember { "main" }
    
    // 使用全局 ProjectManager 获取稳定的临时项目实例
    // 基于 workingDirectory 生成确定性的项目ID，确保同一目录总是返回相同的项目实例
    val tempProject = remember(workingDirectory) {
        com.claudecodeplus.ui.services.ProjectManager.getTemporaryProject(workingDirectory)
    }
    
    ChatViewNew(
        unifiedSessionService = unifiedSessionService,
        workingDirectory = workingDirectory,
        fileIndexService = fileIndexService,
        projectService = projectService,
        sessionManager = sessionManager,
        tabId = defaultTabId,
        initialMessages = initialMessages,
        sessionId = sessionId,
        tabManager = null,
        currentTabId = null,
        currentProject = tempProject,
        projectManager = null,
        ideIntegration = ideIntegration,  // 传递 IDE 集成
        backgroundService = backgroundService,
        sessionStateSync = sessionStateSync,
        onNewSessionRequest = onNewSessionRequest,
        modifier = modifier
    )
}