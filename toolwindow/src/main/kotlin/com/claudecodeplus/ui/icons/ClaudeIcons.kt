/*
 * ClaudeIcons.kt
 * 
 * Claude Code Plus 图标定义
 */

package com.claudecodeplus.ui.icons

import org.jetbrains.jewel.ui.icon.PathIconKey

/**
 * Claude Code Plus 项目图标定义
 * 使用 PathIconKey 加载项目资源中的 SVG 图标
 */
object ClaudeIcons {
    /**
     * 发送消息图标 - 向上箭头
     */
    val send: PathIconKey = PathIconKey("icons/send.svg", ClaudeIcons::class.java)
    
    /**
     * 停止生成图标 - 方形停止按钮
     */
    val stop: PathIconKey = PathIconKey("icons/stop.svg", ClaudeIcons::class.java)
    
    /**
     * 图片选择图标 - 相机图标
     */
    val image: PathIconKey = PathIconKey("icons/image.svg", ClaudeIcons::class.java)
    
    /**
     * 操作相关图标分组
     */
    object Actions {
        val send: PathIconKey = ClaudeIcons.send
        val stop: PathIconKey = ClaudeIcons.stop
        val selectImage: PathIconKey = ClaudeIcons.image
    }
}