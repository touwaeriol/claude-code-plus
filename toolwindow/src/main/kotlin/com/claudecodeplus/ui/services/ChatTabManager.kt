package com.claudecodeplus.ui.services

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * 多标签对话管理器 - 会话标签管理核心组件
 * 
 * 负责管理所有会话标签的生命周期，包括创建、切换、关闭、分组等操作。
 * 虽然在桌面应用中标签栏被隐藏，但该组件仍在后台管理所有会话状态。
 * 
 * 主要功能：
 * - 标签生命周期管理（创建、激活、关闭）
 * - 会话 ID 映射（将 Claude 会话 ID 与标签关联）
 * - 标签分组管理（组织相关对话）
 * - 最近使用记录（快速切换）
 * - 事件通知（通过 StateFlow 发布标签事件）
 * 
 * 与其他组件的关系：
 * - ProjectManager: 标签可以关联到特定项目
 * - SessionLoader: 加载会话时需要找到对应的标签
 * - ChatView: UI 通过该管理器获取当前活动标签
 */
class ChatTabManager {
    private val _tabs = mutableStateListOf<ChatTab>()
    val tabs: List<ChatTab> = _tabs
    
    private val _activeTabId = mutableStateOf<String?>(null)
    val activeTabId: String? get() = _activeTabId.value
    
    private val _groups = mutableStateListOf<ChatGroup>()
    val groups: List<ChatGroup> = _groups
    
    private val _recentTabs = mutableListOf<String>()
    
    // 事件流
    private val _events = MutableStateFlow<TabEvent?>(null)
    val events: StateFlow<TabEvent?> = _events.asStateFlow()
    
    init {
        // 创建默认分组
        _groups.add(
            ChatGroup(
                id = "default",
                name = "默认",
                color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
            )
        )
    }
    
    /**
     * 创建新标签
     * 
     * 创建一个新的聊天标签。如果提供了项目信息，会在标签标题中显示项目简称。
     * 
     * 标题格式：
     * - 有项目: [项目简称] 标题
     * - 无项目: 标题
     * 
     * @param title 标签标题，默认为 "新对话 N"
     * @param groupId 所属分组 ID，默认为 "default"
     * @param sessionId Claude 会话 ID，新建会话时为 null
     * @param project 关联的项目信息
     * @return 新创建标签的 ID
     */
    fun createNewTab(
        title: String = "新对话 ${_tabs.size + 1}",
        groupId: String? = "default",
        sessionId: String? = null,
        project: com.claudecodeplus.ui.models.Project? = null
    ): String {
        println("\n=== ChatTabManager.createNewTab 调试 ===")
        println("  - 输入 title: '$title'")
        println("  - 输入 project: ${project?.name} (${project?.id})")
        println("  - 输入 sessionId: $sessionId")
        println("  - 当前标签数量: ${_tabs.size}")
        
        val projectShortName = project?.name?.let { name ->
            // 生成项目简称：取前4个字符或使用首字母缩写
            if (name.length <= 4) name
            else name.split(" ", "-", "_").map { it.firstOrNull()?.uppercaseChar() ?: "" }.joinToString("")
                .ifEmpty { name.take(4) }
        }
        
        println("  - 生成的项目简称: $projectShortName")
        
        val tabTitle = if (projectShortName != null) {
            "[$projectShortName] $title"
        } else {
            title
        }
        
        println("  - 最终标签标题: '$tabTitle'")
        
        // 为新会话预先生成 sessionId（如果没有提供的话）
        val finalSessionId = sessionId ?: UUID.randomUUID().toString()
        
        val newTab = ChatTab(
            title = tabTitle,
            groupId = groupId,
            sessionId = finalSessionId,
            projectId = project?.id,
            projectName = project?.name,
            projectPath = project?.path
        )
        
        println("  - 创建的 ChatTab 对象:")
        println("    - id: ${newTab.id}")
        println("    - title: '${newTab.title}'")
        println("    - projectId: ${newTab.projectId}")
        println("    - projectName: ${newTab.projectName}")
        
        _tabs.add(newTab)
        println("  - 添加到 _tabs 后的数量: ${_tabs.size}")
        
        // 验证添加是否成功
        val addedTab = _tabs.find { it.id == newTab.id }
        if (addedTab != null) {
            println("  - ✅ 验证：标签已成功添加")
            println("  - 存储的标题: '${addedTab.title}'")
        } else {
            println("  - ❌ 错误：标签添加失败！")
        }
        println("=====================================\n")
        
        setActiveTab(newTab.id)
        _events.value = TabEvent.TabCreated(newTab.id)
        return newTab.id
    }
    
    /**
     * 从会话创建或切换到标签（包含项目信息）
     */
    fun createOrSwitchToSessionTab(
        session: ProjectSession, 
        messages: List<EnhancedMessage>,
        project: com.claudecodeplus.ui.models.Project? = null
    ): String {
        // 查找是否已有该会话的标签（仅当session.id不为null时）
        val existingTab = session.id?.let { id ->
            _tabs.find { it.sessionId == id }
        }
        
        if (existingTab != null) {
            // 如果标签已存在，更新消息并切换到该标签
            updateTab(existingTab.id) { tab ->
                tab.copy(
                    messages = messages,  // 直接使用 EnhancedMessage
                    status = ChatTab.TabStatus.ACTIVE,
                    lastModified = Instant.now()
                )
            }
            setActiveTab(existingTab.id)
            return existingTab.id
        } else {
            // 创建新标签，包含项目信息
            val projectShortName = project?.name?.let { name ->
                // 生成项目简称：取前4个字符或使用首字母缩写
                if (name.length <= 4) name
                else name.split(" ", "-", "_").map { it.firstOrNull()?.uppercaseChar() ?: "" }.joinToString("")
                    .ifEmpty { name.take(4) }
            }
            
            println("DEBUG: 创建标签 - session.id: ${session.id}")
            println("DEBUG: 创建标签 - session.name: '${session.name}'")
            println("DEBUG: 创建标签 - session.projectId: ${session.projectId}")
            println("DEBUG: 创建标签 - 项目: ${project?.name}")
            
            val tabTitle = if (projectShortName != null) {
                "[$projectShortName] ${session.name}"
            } else {
                session.name
            }
            println("DEBUG: 最终标签标题: '$tabTitle'")
            
            val newTab = ChatTab(
                title = tabTitle,
                groupId = "default",
                sessionId = session.id,
                projectId = project?.id,
                projectName = project?.name,
                projectPath = project?.path,
                messages = messages,  // 直接使用 EnhancedMessage
                status = ChatTab.TabStatus.ACTIVE
            )
            _tabs.add(newTab)
            setActiveTab(newTab.id)
            _events.value = TabEvent.TabCreated(newTab.id)
            
            // 调试：创建标签后再次检查
            println("DEBUG: 标签创建完成，最终状态:")
            println("  - newTab.id: ${newTab.id}")
            println("  - newTab.title: '${newTab.title}'")
            println("  - newTab.sessionId: ${newTab.sessionId}")
            println("  - _tabs中的标签数量: ${_tabs.size}")
            
            // 检查 _tabs 中实际存储的标签
            val addedTab = _tabs.find { it.id == newTab.id }
            if (addedTab != null) {
                println("  - 实际存储的标签标题: '${addedTab.title}'")
                if (addedTab.title != newTab.title) {
                    println("  - 警告：存储的标题与创建的标题不同！")
                }
            }
            
            return newTab.id
        }
    }
    
    /**
     * 设置活动标签
     */
    fun setActiveTab(tabId: String) {
        val tab = _tabs.find { it.id == tabId }
        if (tab != null) {
            _activeTabId.value = tabId
            updateRecentTabs(tabId)
            _events.value = TabEvent.TabActivated(tabId)
            
            // 如果标签有关联的会话，触发会话切换事件
            tab.sessionId?.let { sessionId ->
                _events.value = TabEvent.SessionSwitchRequested(sessionId)
            }
        }
    }
    
    /**
     * 关闭标签
     */
    fun closeTab(tabId: String, force: Boolean = false) {
        val tab = _tabs.find { it.id == tabId } ?: return
        
        // 检查是否有未保存的内容
        if (!force && tab.status == ChatTab.TabStatus.ACTIVE && tab.messages.isNotEmpty()) {
            _events.value = TabEvent.CloseConfirmationNeeded(tabId)
            return
        }
        
        _tabs.removeAll { it.id == tabId }
        _recentTabs.remove(tabId)
        
        // 如果关闭的是当前标签，切换到最近的标签
        if (_activeTabId.value == tabId) {
            val nextTab = _recentTabs.firstOrNull() ?: _tabs.firstOrNull()?.id
            _activeTabId.value = nextTab
        }
        
        _events.value = TabEvent.TabClosed(tabId)
    }
    
    /**
     * 更新标签
     */
    fun updateTab(tabId: String, update: (ChatTab) -> ChatTab) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            _tabs[index] = update(_tabs[index]).copy(lastModified = Instant.now())
            _events.value = TabEvent.TabUpdated(tabId)
        }
    }
    
    /**
     * 链接Claude会话
     * 在用户发送第一条消息时调用，关联Claude会话并更新标题
     */
    fun linkClaudeSession(tabId: String, sessionId: String, sessionTitle: String? = null) {
        updateTab(tabId) { tab ->
            tab.copy(
                sessionId = sessionId,
                isClaudeSessionLinked = true,
                title = sessionTitle ?: tab.title
            )
        }
        
        // 触发会话链接事件
        _events.value = TabEvent.SessionLinked(tabId, sessionId)
    }
    
    /**
     * 添加消息到标签
     */
    fun addMessage(tabId: String, message: EnhancedMessage) {
        updateTab(tabId) { tab ->
            tab.copy(messages = tab.messages + message)
        }
    }
    
    /**
     * 更新标签上下文
     */
    fun updateContext(tabId: String, context: List<ContextItem>) {
        updateTab(tabId) { tab ->
            tab.copy(context = context)
        }
    }
    
    /**
     * 移动标签到分组
     */
    fun moveTabToGroup(tabId: String, groupId: String) {
        updateTab(tabId) { tab ->
            tab.copy(groupId = groupId)
        }
    }
    
    /**
     * 添加标签到标签
     */
    fun addTagToTab(tabId: String, tag: ChatTag) {
        updateTab(tabId) { tab ->
            if (tab.tags.none { it.id == tag.id }) {
                tab.copy(tags = tab.tags + tag)
            } else tab
        }
    }
    
    /**
     * 创建分组
     */
    fun createGroup(name: String, color: androidx.compose.ui.graphics.Color): String {
        val group = ChatGroup(
            name = name,
            color = color,
            order = _groups.size
        )
        _groups.add(group)
        return group.id
    }
    
    /**
     * 获取分组中的标签
     */
    fun getTabsInGroup(groupId: String): List<ChatTab> {
        return _tabs.filter { it.groupId == groupId }
    }
    
    /**
     * 复制标签
     */
    fun duplicateTab(tabId: String): String? {
        val tab = _tabs.find { it.id == tabId } ?: return null
        val newTab = tab.copy(
            id = UUID.randomUUID().toString(),
            title = "${tab.title} (副本)",
            sessionId = null, // 新会话
            createdAt = Instant.now(),
            lastModified = Instant.now()
        )
        _tabs.add(newTab)
        return newTab.id
    }
    
    /**
     * 重命名标签
     */
    fun renameTab(tabId: String, newTitle: String) {
        updateTab(tabId) { tab ->
            tab.copy(title = newTitle)
        }
    }
    
    /**
     * 标记标签状态
     */
    fun markTabStatus(tabId: String, status: ChatTab.TabStatus) {
        updateTab(tabId) { tab ->
            tab.copy(status = status)
        }
    }
    
    /**
     * 获取最近使用的标签
     */
    fun getRecentTabs(limit: Int = 5): List<ChatTab> {
        return _recentTabs.take(limit).mapNotNull { id ->
            _tabs.find { it.id == id }
        }
    }
    
    /**
     * 清理已关闭的标签
     */
    fun cleanupClosedTabs() {
        _tabs.removeAll { it.status == ChatTab.TabStatus.ARCHIVED }
    }
    
    /**
     * 保存标签状态（用于持久化）
     */
    fun saveState(): ChatTabsState {
        return ChatTabsState(
            tabs = _tabs.toList(),
            groups = _groups.toList(),
            activeTabId = _activeTabId.value,
            recentTabIds = _recentTabs.toList()
        )
    }
    
    /**
     * 恢复标签状态
     */
    fun restoreState(state: ChatTabsState) {
        _tabs.clear()
        _tabs.addAll(state.tabs)
        _groups.clear()
        _groups.addAll(state.groups)
        _activeTabId.value = state.activeTabId
        _recentTabs.clear()
        _recentTabs.addAll(state.recentTabIds)
    }
    
    private fun updateRecentTabs(tabId: String) {
        _recentTabs.remove(tabId)
        _recentTabs.add(0, tabId)
        if (_recentTabs.size > 10) {
            _recentTabs.removeAt(_recentTabs.size - 1)
        }
    }
    
    /**
     * 根据第一条消息更新标签标题
     */
    fun updateTabTitleFromFirstMessage(tabId: String, messageContent: String, project: com.claudecodeplus.ui.models.Project? = null) {
        println("\n=== ChatTabManager.updateTabTitleFromFirstMessage 调试 ===")
        println("  - tabId: $tabId")
        println("  - messageContent: '${messageContent.take(100)}...'")
        println("  - project: ${project?.name}")
        
        val tab = _tabs.find { it.id == tabId }
        if (tab == null) {
            println("  - ❌ 错误：找不到标签 ID: $tabId")
            println("==========================================\n")
            return
        }
        
        println("  - 找到标签，当前标题: '${tab.title}'")
        
        // 生成会话名称（复用 ProjectManager 的逻辑）
        val sessionName = generateSessionNameFromContent(messageContent)
        println("  - 生成的会话名称: '$sessionName'")
        
        // 生成项目简称
        val projectShortName = project?.name?.let { name ->
            if (name.length <= 4) name
            else name.split(" ", "-", "_").map { it.firstOrNull()?.uppercaseChar() ?: "" }.joinToString("")
                .ifEmpty { name.take(4) }
        }
        println("  - 项目简称: $projectShortName")
        
        // 更新标签标题
        val newTitle = if (projectShortName != null) {
            "[$projectShortName] $sessionName"
        } else {
            sessionName
        }
        
        println("  - 新标题: '$newTitle'")
        
        updateTab(tabId) { t ->
            t.copy(title = newTitle)
        }
        
        // 验证更新后的标题
        val updatedTab = _tabs.find { it.id == tabId }
        println("  - 更新后的标题: '${updatedTab?.title}'")
        println("==========================================\n")
        
        _events.value = TabEvent.TabTitleUpdateRequested(tabId, newTitle)
    }
    
    private fun generateSessionNameFromContent(content: String): String {
        val cleanContent = content.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        return when {
            cleanContent.isEmpty() -> "新对话"
            cleanContent.length <= 30 -> cleanContent
            cleanContent.contains("?") -> {
                val questionPart = cleanContent.substringBefore("?") + "?"
                if (questionPart.length <= 50) questionPart else questionPart.take(47) + "..."
            }
            cleanContent.contains("。") -> {
                val sentencePart = cleanContent.substringBefore("。") + "。"
                if (sentencePart.length <= 50) sentencePart else sentencePart.take(47) + "..."
            }
            else -> cleanContent.take(30) + if (cleanContent.length > 30) "..." else ""
        }
    }
    
    /**
     * 加载历史会话到标签（流式）
     * @param tabId 标签ID
     * @param sessionFile 会话文件
     * @param sessionLoader 会话加载器
     * @return 加载结果流
     */
    fun loadSessionHistoryToTab(
        tabId: String,
        sessionFile: File,
        sessionLoader: SessionLoader
    ): Flow<SessionLoadEvent> = flow {
        val tab = _tabs.find { it.id == tabId }
        if (tab == null) {
            emit(SessionLoadEvent.Error("标签不存在: $tabId"))
            return@flow
        }
        
        // 清空当前消息
        updateTab(tabId) { t ->
            t.copy(
                messages = emptyList(),
                status = ChatTab.TabStatus.LOADING
            )
        }
        
        val loadedMessages = mutableListOf<EnhancedMessage>()
        
        // 使用 SessionLoader 加载历史
        sessionLoader.loadSessionAsMessageFlow(sessionFile)
            .collect { result ->
                when (result) {
                    is SessionLoader.LoadResult.MessageCompleted -> {
                        // 直接使用 EnhancedMessage
                        loadedMessages.add(result.message)
                        
                        // 实时更新标签消息
                        updateTab(tabId) { t ->
                            t.copy(messages = loadedMessages.toList())
                        }
                        
                        emit(SessionLoadEvent.MessageLoaded(result.message))
                    }
                    
                    is SessionLoader.LoadResult.MessageUpdated -> {
                        // 对于流式更新，可以选择性地更新最后一条消息
                        emit(SessionLoadEvent.MessageUpdated(result.message))
                    }
                    
                    is SessionLoader.LoadResult.LoadComplete -> {
                        // 加载完成
                        updateTab(tabId) { t ->
                            t.copy(
                                messages = loadedMessages.toList(),
                                status = ChatTab.TabStatus.ACTIVE
                            )
                        }
                        emit(SessionLoadEvent.LoadComplete(result.messages))
                    }
                    
                    is SessionLoader.LoadResult.Error -> {
                        // 错误处理
                        updateTab(tabId) { t ->
                            t.copy(status = ChatTab.TabStatus.ERROR)
                        }
                        emit(SessionLoadEvent.Error(result.error))
                    }
                }
            }
    }
    
    /**
     * 会话加载事件
     */
    sealed class SessionLoadEvent {
        data class MessageLoaded(val message: EnhancedMessage) : SessionLoadEvent()
        data class MessageUpdated(val message: EnhancedMessage) : SessionLoadEvent()
        data class LoadComplete(val messages: List<EnhancedMessage>) : SessionLoadEvent()
        data class Error(val error: String) : SessionLoadEvent()
    }
    
    sealed class TabEvent {
        data class TabCreated(val tabId: String) : TabEvent()
        data class TabClosed(val tabId: String) : TabEvent()
        data class TabActivated(val tabId: String) : TabEvent()
        data class TabUpdated(val tabId: String) : TabEvent()
        data class CloseConfirmationNeeded(val tabId: String) : TabEvent()
        data class SessionSwitchRequested(val sessionId: String) : TabEvent()
        data class TabTitleUpdateRequested(val tabId: String, val newTitle: String) : TabEvent()
        data class SessionLinked(val tabId: String, val sessionId: String) : TabEvent()
    }
}

/**
 * 标签状态（用于持久化）
 */
data class ChatTabsState(
    val tabs: List<ChatTab>,
    val groups: List<ChatGroup>,
    val activeTabId: String?,
    val recentTabIds: List<String>
)