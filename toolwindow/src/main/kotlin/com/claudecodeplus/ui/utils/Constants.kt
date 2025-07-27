package com.claudecodeplus.ui.utils

/**
 * 常量定义
 * 集中管理应用中使用的常量
 */
object Constants {
    
    /**
     * 消息相关常量
     */
    object Messages {
        const val USER_MESSAGE_PREFIX = "USER_MESSAGE:"
        const val ASSISTANT_MESSAGE_PREFIX = "ASSISTANT_MESSAGE:"
        const val COMPACT_SUMMARY_PREFIX = "COMPACT_SUMMARY:"
        const val COMPACT_COMPLETE_MARKER = "<local-command-stdout>Compacted. ctrl+r to see full summary</local-command-stdout>"
    }
    
    /**
     * UI相关常量
     */
    object UI {
        const val DEFAULT_PADDING = 16
        const val SMALL_PADDING = 8
        const val MESSAGE_ANIMATION_DURATION = 300
        const val TOOL_CALL_ANIMATION_DURATION = 500
        const val COMPACT_DISPLAY_DELAY = 2000L
    }
    
    /**
     * 文件相关常量
     */
    object Files {
        const val SESSION_FILE_EXTENSION = ".jsonl"
        const val MAX_FILE_SIZE_FOR_PREVIEW = 1024 * 1024 // 1MB
        const val DEFAULT_ENCODING = "UTF-8"
    }
    
    /**
     * 主题相关常量
     */
    object Theme {
        const val LIGHT_THEME_NAME = "亮色"
        const val DARK_THEME_NAME = "暗色"
        const val SYSTEM_THEME_NAME = "系统"
        const val HIGH_CONTRAST_LIGHT_NAME = "高对比度亮色"
        const val HIGH_CONTRAST_DARK_NAME = "高对比度暗色"
    }
    
    /**
     * 错误消息
     */
    object Errors {
        const val LOADING_SESSION_ERROR = "加载会话失败"
        const val SENDING_MESSAGE_ERROR = "发送消息时出错"
        const val UNKNOWN_ERROR = "Unknown error"
        const val FILE_NOT_FOUND = "文件不存在"
        const val UNSUPPORTED_OPERATION = "不支持的操作"
    }
    
    /**
     * 调试标记
     */
    object Debug {
        const val LOG_PREFIX = "[Claude Code Plus]"
        const val SESSION_LOADER_TAG = "[SessionLoader]"
        const val MESSAGE_PROCESSOR_TAG = "[MessageProcessor]"
        const val TOOL_DISPLAY_TAG = "[ToolDisplay]"
    }
}