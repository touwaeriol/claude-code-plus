package com.claudecodeplus.plugin.ui.components

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 输入历史管理器
 * 
 * 跟踪每个会话的输入历史，支持上/下箭头键导航
 */
@State(
    name = "ClaudeCodePlusInputHistory",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
@Service(Service.Level.PROJECT)
class InputHistoryManager : PersistentStateComponent<InputHistoryManager.State> {
    
    @Serializable
    data class State(
        var sessionHistories: MutableMap<String, MutableList<String>> = mutableMapOf()
    )
    
    private var state = State()
    private val maxHistorySize = 50
    
    // 当前历史导航位置（每个会话）
    private val currentPositions = mutableMapOf<String, Int>()
    
    /**
     * 添加输入到历史
     */
    fun addToHistory(sessionId: String, input: String) {
        if (input.isBlank()) return
        
        val history = state.sessionHistories.getOrPut(sessionId) { mutableListOf() }
        
        // 如果最后一条是相同的，不重复添加
        if (history.lastOrNull() == input) return
        
        history.add(input)
        
        // 限制历史记录数量
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
        
        // 重置导航位置
        currentPositions[sessionId] = history.size
    }
    
    /**
     * 获取前一条历史记录（上箭头）
     */
    fun getPrevious(sessionId: String): String? {
        val history = state.sessionHistories[sessionId] ?: return null
        if (history.isEmpty()) return null
        
        val currentPos = currentPositions[sessionId] ?: history.size
        
        // 如果已经在最前面，返回null
        if (currentPos <= 0) return null
        
        val newPos = currentPos - 1
        currentPositions[sessionId] = newPos
        
        return history[newPos]
    }
    
    /**
     * 获取后一条历史记录（下箭头）
     */
    fun getNext(sessionId: String): String? {
        val history = state.sessionHistories[sessionId] ?: return null
        if (history.isEmpty()) return null
        
        val currentPos = currentPositions[sessionId] ?: history.size
        
        // 如果已经在最后，返回空字符串（清空输入框）
        if (currentPos >= history.size - 1) {
            currentPositions[sessionId] = history.size
            return ""
        }
        
        val newPos = currentPos + 1
        currentPositions[sessionId] = newPos
        
        return history[newPos]
    }
    
    /**
     * 重置导航位置
     */
    fun resetPosition(sessionId: String) {
        val history = state.sessionHistories[sessionId] ?: return
        currentPositions[sessionId] = history.size
    }
    
    /**
     * 获取当前导航位置
     */
    fun getCurrentPosition(sessionId: String): Int {
        val history = state.sessionHistories[sessionId] ?: return 0
        return currentPositions[sessionId] ?: history.size
    }
    
    /**
     * 获取所有历史记录
     */
    fun getHistory(sessionId: String): List<String> {
        return state.sessionHistories[sessionId]?.toList() ?: emptyList()
    }
    
    /**
     * 清空会话历史
     */
    fun clearHistory(sessionId: String) {
        state.sessionHistories.remove(sessionId)
        currentPositions.remove(sessionId)
    }
    
    /**
     * 清空所有历史
     */
    fun clearAllHistories() {
        state.sessionHistories.clear()
        currentPositions.clear()
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    companion object {
        /**
         * 获取项目的输入历史管理器实例
         */
        fun getInstance(project: Project): InputHistoryManager {
            return project.getService(InputHistoryManager::class.java)
        }
    }
}


