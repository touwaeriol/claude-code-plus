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
import java.util.UUID

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
    backgroundService: Any? = null,  // 新增：后台服务
    sessionStateSync: Any? = null,   // 新增：状态同步器
    modifier: Modifier = Modifier
) {
    // 为简单用例创建默认的 tabId 和临时 Project
    val defaultTabId = remember { "default-${UUID.randomUUID()}" }
    val tempProject = remember {
        com.claudecodeplus.ui.models.Project(
            id = "temp-${UUID.randomUUID()}",
            name = "临时项目",
            path = workingDirectory
        )
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
        backgroundService = backgroundService,
        sessionStateSync = sessionStateSync,
        modifier = modifier
    )
}