package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.plugin.types.LegacyToolCall
import com.intellij.openapi.project.Project

/**
 * 工具点击处理器接口
 * 用于处理不同工具的点击事件，提供与 IDEA 平台的深度集成
 */
interface ToolClickHandler {
    
    /**
     * 处理工具点击事件
     * 
     * @param toolCall 工具调用信息
     * @param project IntelliJ 项目实例，可能为 null（桌面应用模式）
     * @param config 点击处理配置
     * @return true 表示已处理，false 表示使用默认展开行为
     */
    fun handleToolClick(
        toolCall: LegacyToolCall,
        project: Project?,
        config: ToolClickConfig = ToolClickConfig()
    ): Boolean
    
    /**
     * 检查是否支持处理指定的工具
     */
    fun canHandle(toolCall: LegacyToolCall): Boolean
}

/**
 * 工具点击配置
 */
data class ToolClickConfig(
    /** 优先使用 IDE 集成（默认 true） */
    val preferIdeIntegration: Boolean = true,
    
    /** 总是展开（覆盖 IDE 集成，用于调试或用户偏好） */
    val alwaysExpand: Boolean = false,
    
    /** IDE 集成失败时的回退行为 */
    val fallbackBehavior: FallbackBehavior = FallbackBehavior.EXPAND,
    
    /** 显示操作成功的通知 */
    val showNotifications: Boolean = true
)

/**
 * 回退行为枚举
 */
enum class FallbackBehavior {
    /** 展开显示工具详情 */
    EXPAND,
    
    /** 静默失败（不执行任何操作） */
    SILENT,
    
    /** 显示错误消息 */
    SHOW_ERROR
}

/**
 * 展开行为配置
 */
enum class ExpandBehavior {
    /** 从不展开 */
    NEVER,
    
    /** 总是展开 */
    ALWAYS,
    
    /** IDE 集成失败时展开 */
    IDE_FALLBACK
}