package com.claudecodeplus.plugin.services

import com.claudecodeplus.plugin.ui.ChatViewModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 聊天会话服务
 * 
 * 单例服务，用于管理当前活动的聊天会话
 * 允许其他组件（如 FloatingToolbar）与主聊天面板交互
 */
@Service(Service.Level.PROJECT)
class ChatSessionService(private val project: Project) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 当前活动的 ChatViewModel
    private var activeChatViewModel: ChatViewModel? = null
    
    /**
     * 注册当前活动的 ChatViewModel
     */
    fun registerChatViewModel(viewModel: ChatViewModel) {
        activeChatViewModel = viewModel
    }
    
    /**
     * 取消注册 ChatViewModel
     */
    fun unregisterChatViewModel(viewModel: ChatViewModel) {
        if (activeChatViewModel == viewModel) {
            activeChatViewModel = null
        }
    }
    
    /**
     * 发送消息到当前会话
     */
    fun sendMessageToActiveSession(message: String) {
        val viewModel = activeChatViewModel
        if (viewModel == null) {
            throw IllegalStateException("No active chat session")
        }
        
        scope.launch {
            viewModel.sendMessage(message)
        }
    }
    
    /**
     * 检查是否有活动会话
     */
    fun hasActiveSession(): Boolean {
        return activeChatViewModel != null
    }
    
    /**
     * 获取当前活动的 ChatViewModel
     */
    fun getActiveChatViewModel(): ChatViewModel? {
        return activeChatViewModel
    }
    
    companion object {
        /**
         * 获取项目的 ChatSessionService 实例
         */
        fun getInstance(project: Project): ChatSessionService {
            return project.getService(ChatSessionService::class.java)
        }
    }
}


