package com.claudecodeplus.ui.services

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/**
 * 多标签对话管理器
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
     */
    fun createNewTab(
        title: String = "新对话 ${_tabs.size + 1}",
        groupId: String? = "default",
        sessionId: String? = null
    ): String {
        val newTab = ChatTab(
            title = title,
            groupId = groupId,
            sessionId = sessionId
        )
        _tabs.add(newTab)
        setActiveTab(newTab.id)
        _events.value = TabEvent.TabCreated(newTab.id)
        return newTab.id
    }
    
    /**
     * 从会话创建或切换到标签
     */
    fun createOrSwitchToSessionTab(session: ProjectSession, messages: List<EnhancedMessage>): String {
        // 查找是否已有该会话的标签
        val existingTab = _tabs.find { it.sessionId == session.id }
        
        // 将 EnhancedMessage 转换为 ChatMessage
        val chatMessages = messages.map { enhancedMsg ->
            ChatMessage(
                id = enhancedMsg.id,
                role = enhancedMsg.role,
                content = enhancedMsg.content,
                timestamp = Instant.ofEpochMilli(enhancedMsg.timestamp)
            )
        }
        
        if (existingTab != null) {
            // 如果标签已存在，更新消息并切换到该标签
            updateTab(existingTab.id) { tab ->
                tab.copy(
                    messages = chatMessages,
                    status = ChatTab.TabStatus.ACTIVE,
                    lastModified = Instant.now()
                )
            }
            setActiveTab(existingTab.id)
            return existingTab.id
        } else {
            // 创建新标签
            val newTab = ChatTab(
                title = session.name,
                groupId = "default",
                sessionId = session.id,
                messages = chatMessages,
                status = ChatTab.TabStatus.ACTIVE
            )
            _tabs.add(newTab)
            setActiveTab(newTab.id)
            _events.value = TabEvent.TabCreated(newTab.id)
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
     * 添加消息到标签
     */
    fun addMessage(tabId: String, message: ChatMessage) {
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
    
    sealed class TabEvent {
        data class TabCreated(val tabId: String) : TabEvent()
        data class TabClosed(val tabId: String) : TabEvent()
        data class TabActivated(val tabId: String) : TabEvent()
        data class TabUpdated(val tabId: String) : TabEvent()
        data class CloseConfirmationNeeded(val tabId: String) : TabEvent()
        data class SessionSwitchRequested(val sessionId: String) : TabEvent()
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