package com.claudecodeplus.ui.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.claudecodeplus.sdk.ClaudeCliWrapper
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
    cliWrapper: ClaudeCliWrapper,
    workingDirectory: String,
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    sessionManager: ClaudeSessionManager = ClaudeSessionManager(),
    initialMessages: List<EnhancedMessage>? = null,
    sessionId: String? = null,
    modifier: Modifier = Modifier
) {
    // 为简单用例创建默认的 SessionManager 和 tabId
    val sessionObjectManager = remember { SessionManager() }
    val defaultTabId = remember { "default-${UUID.randomUUID()}" }
    
    ChatView(
        cliWrapper = cliWrapper,
        workingDirectory = workingDirectory,
        fileIndexService = fileIndexService,
        projectService = projectService,
        sessionManager = sessionManager,
        sessionObjectManager = sessionObjectManager,
        tabId = defaultTabId,
        initialMessages = initialMessages,
        sessionId = sessionId,
        tabManager = null,
        currentTabId = null,
        currentProject = null,
        projectManager = null,
        modifier = modifier
    )
}