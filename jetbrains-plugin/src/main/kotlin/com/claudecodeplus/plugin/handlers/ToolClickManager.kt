package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.ui.models.ToolCall
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局工具点击管理器
 * 负责注册和调度各种工具的点击处理器
 */
object ToolClickManager {
    
    private val logger = Logger.getInstance(ToolClickManager::class.java)
    
    /**
     * 注册的工具处理器映射
     * Key: 工具名称（不区分大小写）
     * Value: 对应的处理器
     */
    private val handlers = ConcurrentHashMap<String, ToolClickHandler>()
    
    /**
     * 默认配置
     */
    private val defaultConfig = ToolClickConfig()
    
    init {
        initializeDefaultHandlers()
    }
    
    /**
     * 初始化默认的工具处理器
     */
    private fun initializeDefaultHandlers() {
        try {
            // 注册 Read 工具处理器
            registerHandler("Read", ReadToolHandler())
            logger.info("ToolClickManager: 已注册 Read 工具处理器")
            
            // 注册 Edit 工具处理器
            registerHandler("Edit", EditToolHandler())
            registerHandler("MultiEdit", EditToolHandler())
            logger.info("ToolClickManager: 已注册 Edit/MultiEdit 工具处理器")

            // 注册 Write 工具处理器
            registerHandler("Write", WriteToolHandler())
            logger.info("ToolClickManager: 已注册 Write 工具处理器")

            // 未来可以添加更多处理器
            // registerHandler("Bash", BashToolHandler())
            
            logger.info("ToolClickManager: 默认处理器初始化完成，共注册 ${handlers.size} 个处理器")
        } catch (e: Exception) {
            logger.error("ToolClickManager: 初始化默认处理器失败", e)
        }
    }
    
    /**
     * 注册工具处理器
     * 
     * @param toolName 工具名称（不区分大小写）
     * @param handler 处理器实例
     */
    fun registerHandler(toolName: String, handler: ToolClickHandler) {
        val normalizedName = toolName.lowercase()
        handlers[normalizedName] = handler
        logger.info("ToolClickManager: 已注册处理器 - $toolName -> ${handler::class.simpleName}")
    }
    
    /**
     * 取消注册工具处理器
     */
    fun unregisterHandler(toolName: String): Boolean {
        val normalizedName = toolName.lowercase()
        val removed = handlers.remove(normalizedName)
        if (removed != null) {
            logger.info("ToolClickManager: 已取消注册处理器 - $toolName")
            return true
        }
        return false
    }
    
    /**
     * 处理工具点击事件
     * 
     * @param toolCall 工具调用信息
     * @param project IntelliJ 项目实例
     * @param config 处理配置（可选）
     * @return true 表示已被处理器处理，false 表示使用默认行为（展开）
     */
    fun handleToolClick(
        toolCall: ToolCall,
        project: Project?,
        config: ToolClickConfig = defaultConfig
    ): Boolean {
        return try {
            val normalizedName = toolCall.name.lowercase()
            var handler = handlers[normalizedName]

            if (handler == null) {
                val specificTool = toolCall.specificTool
                if (specificTool != null) {
                    val typeKey = specificTool.toolType.toolName.lowercase()
                    handler = handlers[typeKey]
                    if (handler != null) {
                        logger.debug("ToolClickManager: 使用 specificTool 类型映射处理器 - ${specificTool.toolType.toolName}")
                    }
                }
            }

            if (handler == null) {
                logger.debug("ToolClickManager: 没有找到 ${toolCall.name} 的处理器")
                return false
            }
            
            if (!handler.canHandle(toolCall)) {
                logger.debug("ToolClickManager: 处理器 ${handler::class.simpleName} 无法处理工具调用 ${toolCall.name}")
                return false
            }
            
            logger.info("ToolClickManager: 使用 ${handler::class.simpleName} 处理 ${toolCall.name} 工具点击")
            val handled = handler.handleToolClick(toolCall, project, config)
            
            if (handled) {
                logger.info("ToolClickManager: ${toolCall.name} 工具点击已成功处理")
            } else {
                logger.info("ToolClickManager: ${toolCall.name} 工具点击处理失败，将使用默认行为")
            }
            
            handled
        } catch (e: Exception) {
            logger.error("ToolClickManager: 处理工具点击时出现异常", e)
            false
        }
    }
    
    /**
     * 获取指定工具的处理器
     */
    fun getHandler(toolName: String): ToolClickHandler? {
        val normalizedName = toolName.lowercase()
        return handlers[normalizedName]
    }
    
    /**
     * 检查是否有注册的处理器可以处理指定工具
     */
    fun canHandle(toolCall: ToolCall): Boolean {
        val normalizedName = toolCall.name.lowercase()
        val handler = handlers[normalizedName]
        return handler?.canHandle(toolCall) == true
    }
    
    /**
     * 获取所有已注册的工具名称
     */
    fun getRegisteredToolNames(): Set<String> {
        return handlers.keys.toSet()
    }
    
    /**
     * 获取管理器统计信息
     */
    fun getStats(): ToolClickManagerStats {
        return ToolClickManagerStats(
            registeredHandlers = handlers.size,
            handlerDetails = handlers.mapValues { it.value::class.simpleName ?: "Unknown" }
        )
    }
    
    /**
     * 清除所有处理器（主要用于测试）
     */
    internal fun clearHandlers() {
        handlers.clear()
        logger.warn("ToolClickManager: 所有处理器已清除")
    }
    
    /**
     * 重新初始化默认处理器（主要用于测试）
     */
    internal fun reinitialize() {
        clearHandlers()
        initializeDefaultHandlers()
        logger.info("ToolClickManager: 已重新初始化")
    }
}

/**
 * 工具点击管理器统计信息
 */
data class ToolClickManagerStats(
    val registeredHandlers: Int,
    val handlerDetails: Map<String, String>
) {
    override fun toString(): String {
        return "ToolClickManagerStats(handlers=$registeredHandlers, details=$handlerDetails)"
    }
}
