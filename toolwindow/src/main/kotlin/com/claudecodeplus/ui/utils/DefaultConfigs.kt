package com.claudecodeplus.ui.utils

/**
 * 默认配置管理 - 系统默认参数集中配置
 * 
 * 集中管理应用中的所有默认值和配置参数。
 * 这些配置可以被用户设置覆盖，但提供合理的默认值。
 * 
 * 配置分类：
 * - Session: 会话管理相关
 * - Search: 搜索功能相关
 * - Model: AI 模型相关
 * - UI: 用户界面相关
 * - FileIndex: 文件索引相关
 * - Network: 网络请求相关
 * - MessageId: 消息 ID 生成相关
 * 
 * 使用方式：
 * 直接引用对应的配置值，例如：DefaultConfigs.Session.MAX_MESSAGES
 */
object DefaultConfigs {
    
    /**
     * 会话相关默认配置
     * 
     * 控制会话加载、存储和缓存的参数。
     */
    object Session {
        // 最大消息数 - 从历史会话加载的最大消息数量
        const val MAX_MESSAGES = 50
        
        // 最大天数 - 只加载最近 N 天的会话
        const val MAX_DAYS_OLD = 7
        
        // 默认会话 ID 长度 - UUID 的标准长度
        const val DEFAULT_SESSION_ID_LENGTH = 36
        
        // 会话缓存大小 - 内存中缓存的会话数量
        const val SESSION_CACHE_SIZE = 10
    }
    
    /**
     * 搜索相关默认配置
     * 
     * 控制文件搜索、符号搜索等功能的参数。
     */
    object Search {
        // 最大搜索结果数 - 搜索结果的上限
        const val MAX_SEARCH_RESULTS = 50
        
        // 默认搜索限制 - 默认显示的结果数量
        const val DEFAULT_SEARCH_LIMIT = 20
        
        // 最近文件限制 - 显示最近使用文件的数量
        const val RECENT_FILES_LIMIT = 10
        
        // 最大文件类型数 - 支持的文件类型筛选数量
        const val MAX_FILE_TYPES = 10
    }
    
    /**
     * 模型相关默认配置
     * 
     * AI 模型和权限管理的默认设置。
     */
    object Model {
        // 默认模型 - 默认使用的 AI 模型（Opus 是最强大的模型）
        const val DEFAULT_MODEL = "opus"
        
        // 默认权限模式 - 绕过权限确认，提高效率
        const val DEFAULT_PERMISSION_MODE = "bypass-permissions"
        
        // 跳过权限检查 - 默认为 true，避免频繁确认
        const val SKIP_PERMISSIONS = true
    }
    
    /**
     * UI 相关默认配置
     * 
     * 用户界面布局和尺寸的默认值。
     */
    object UI {
        // 默认标签宽度 - 标签页的默认宽度（单位：dp）
        const val DEFAULT_TAB_WIDTH = 200
        
        // 默认输入框高度 - 消息输入框的初始高度（单位：dp）
        const val DEFAULT_INPUT_HEIGHT = 100
        
        // 最小输入框高度 - 输入框可调整的最小高度（单位：dp）
        const val MIN_INPUT_HEIGHT = 50
        
        // 最大输入框高度 - 输入框可调整的最大高度（单位：dp）
        const val MAX_INPUT_HEIGHT = 300
        
        // 默认侧边栏宽度 - 项目管理侧边栏的宽度（单位：dp）
        const val DEFAULT_SIDEBAR_WIDTH = 300
        
        // 默认消息间距 - 消息之间的间距（单位：dp）
        const val DEFAULT_MESSAGE_GAP = 12
    }
    
    /**
     * 文件索引默认配置
     * 
     * 控制文件索引系统的性能和范围。
     */
    object FileIndex {
        // 索引刷新间隔 - 后台更新文件索引的时间间隔（单位：ms）
        const val INDEX_REFRESH_INTERVAL = 300000L // 5分钟
        
        // 索引批处理大小 - 每次处理的文件数量
        const val INDEX_BATCH_SIZE = 100
        
        // 最大索引文件数 - 索引系统支持的最大文件数
        const val MAX_INDEXED_FILES = 10000
        
        // 支持的文件扩展名 - 索引系统会处理的文件类型
        val SUPPORTED_FILE_EXTENSIONS = listOf(
            "kt", "java", "js", "ts", "py", "cpp", "c", "h", 
            "cs", "go", "rs", "swift", "rb", "php", "dart",
            "json", "xml", "yaml", "yml", "properties", "gradle"
        )
    }
    
    /**
     * 网络相关默认配置
     * 
     * 控制网络请求的超时和重试策略。
     */
    object Network {
        // 连接超时 - 建立网络连接的超时时间（单位：ms）
        const val CONNECTION_TIMEOUT = 30000 // 30秒
        
        // 读取超时 - 等待响应数据的超时时间（单位：ms）
        const val READ_TIMEOUT = 60000 // 60秒
        
        // 最大重试次数 - 网络请求失败后的重试次数
        const val MAX_RETRIES = 3
        
        // 重试延迟 - 重试之间的等待时间（单位：ms）
        const val RETRY_DELAY = 1000L // 1秒
    }
    
    /**
     * 消息 ID 生成配置
     * 
     * 控制消息 ID 的生成规则。
     * 与 IdGenerator 配合使用。
     */
    object MessageId {
        // ID 前缀 - 消息 ID 的固定前缀
        const val PREFIX = "msg"
        
        // 启用时间戳 - 是否在 ID 中包含时间戳
        const val TIMESTAMP_ENABLED = true
        
        // 随机后缀最大值 - 随机后缀的取值范围 (0 到此值)
        const val RANDOM_SUFFIX_MAX = 999
    }
}