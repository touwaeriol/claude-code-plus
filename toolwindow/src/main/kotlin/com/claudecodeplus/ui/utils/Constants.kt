package com.claudecodeplus.ui.utils

/**
 * 常量定义类 - 全局常量集中管理
 * 
 * 集中管理应用中使用的所有常量，避免硬编码和魔法数字。
 * 通过对象嵌套的方式组织不同类别的常量，提高代码可读性。
 * 
 * 组织结构：
 * - Messages: 消息相关常量
 * - UI: 用户界面相关常量
 * - Files: 文件处理相关常量
 * - Theme: 主题相关常量
 * - Errors: 错误消息常量
 * - Debug: 调试相关常量
 * 
 * 使用优势：
 * - 便于统一修改和维护
 * - 避免拼写错误
 * - 提供 IDE 自动补全支持
 * - 便于国际化和本地化
 */
object Constants {
    
    /**
     * 消息相关常量
     * 
     * 用于 Claude CLI 消息解析和识别。
     * 这些前缀和标记用于区分不同类型的消息。
     */
    object Messages {
        // 用户消息前缀 - 用于标识历史会话中的用户消息
        const val USER_MESSAGE_PREFIX = "USER_MESSAGE:"
        
        // 助手消息前缀 - 用于标识历史会话中的 AI 消息
        const val ASSISTANT_MESSAGE_PREFIX = "ASSISTANT_MESSAGE:"
        
        // 压缩摘要前缀 - 用于标识会话压缩后的摘要信息
        const val COMPACT_SUMMARY_PREFIX = "COMPACT_SUMMARY:"
        
        // 压缩完成标记 - Claude 完成会话压缩后返回的特定标记
        const val COMPACT_COMPLETE_MARKER = "<local-command-stdout>Compacted. ctrl+r to see full summary</local-command-stdout>"
    }
    
    /**
     * UI 相关常量
     * 
     * 定义用户界面的布局、动画和延迟参数。
     * 统一的 UI 常量有助于保持界面一致性。
     */
    object UI {
        // 默认内边距 - 用于大部分 UI 组件（单位：dp）
        const val DEFAULT_PADDING = 16
        
        // 小内边距 - 用于紧凑的 UI 元素（单位：dp）
        const val SMALL_PADDING = 8
        
        // 消息动画时长 - 消息出现/消失的动画时间（单位：ms）
        const val MESSAGE_ANIMATION_DURATION = 300
        
        // 工具调用动画时长 - 工具调用显示的动画时间（单位：ms）
        const val TOOL_CALL_ANIMATION_DURATION = 500
        
        // 压缩完成显示延迟 - 用户看到压缩完成消息的时间（单位：ms）
        const val COMPACT_DISPLAY_DELAY = 2000L
    }
    
    /**
     * 文件相关常量
     * 
     * 定义文件处理的参数和限制。
     */
    object Files {
        // 会话文件扩展名 - Claude CLI 使用的 JSONL 格式
        const val SESSION_FILE_EXTENSION = ".jsonl"
        
        // 预览文件最大大小 - 超过此大小不显示完整内容（单位：字节）
        const val MAX_FILE_SIZE_FOR_PREVIEW = 1024 * 1024 // 1MB
        
        // 默认文件编码 - 用于读写文本文件
        const val DEFAULT_ENCODING = "UTF-8"
    }
    
    /**
     * 主题相关常量
     * 
     * 定义各种主题的显示名称。
     * 这些名称用于 UI 显示和用户选择。
     */
    object Theme {
        const val LIGHT_THEME_NAME = "亮色"
        const val DARK_THEME_NAME = "暗色"
        const val SYSTEM_THEME_NAME = "系统"
        const val HIGH_CONTRAST_LIGHT_NAME = "高对比度亮色"
        const val HIGH_CONTRAST_DARK_NAME = "高对比度暗色"
    }
    
    /**
     * 错误消息常量
     * 
     * 集中管理所有错误消息文本，
     * 便于统一修改和国际化。
     */
    object Errors {
        const val LOADING_SESSION_ERROR = "加载会话失败"
        const val SENDING_MESSAGE_ERROR = "发送消息时出错"
        const val UNKNOWN_ERROR = "Unknown error"
        const val FILE_NOT_FOUND = "文件不存在"
        const val UNSUPPORTED_OPERATION = "不支持的操作"
    }
    
    /**
     * 调试标记常量
     * 
     * 用于日志输出和调试信息的前缀标记。
     * 统一的标记有助于日志过滤和分析。
     */
    object Debug {
        // 全局日志前缀 - 所有日志的统一前缀
        const val LOG_PREFIX = "[Claude Code Plus]"
        
        // 会话加载器标记 - SessionLoader 组件的日志标记
        const val SESSION_LOADER_TAG = "[SessionLoader]"
        
        // 消息处理器标记 - MessageProcessor 组件的日志标记
        const val MESSAGE_PROCESSOR_TAG = "[MessageProcessor]"
        
        // 工具显示标记 - 工具调用显示组件的日志标记
        const val TOOL_DISPLAY_TAG = "[ToolDisplay]"
    }
}