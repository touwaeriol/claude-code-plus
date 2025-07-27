package com.claudecodeplus.ui.utils

/**
 * 默认配置
 * 集中管理应用中的默认值
 */
object DefaultConfigs {
    
    /**
     * 会话相关默认配置
     */
    object Session {
        const val MAX_MESSAGES = 50
        const val MAX_DAYS_OLD = 7
        const val DEFAULT_SESSION_ID_LENGTH = 36
        const val SESSION_CACHE_SIZE = 10
    }
    
    /**
     * 搜索相关默认配置
     */
    object Search {
        const val MAX_SEARCH_RESULTS = 50
        const val DEFAULT_SEARCH_LIMIT = 20
        const val RECENT_FILES_LIMIT = 10
        const val MAX_FILE_TYPES = 10
    }
    
    /**
     * 模型相关默认配置
     */
    object Model {
        const val DEFAULT_MODEL = "opus"
        const val DEFAULT_PERMISSION_MODE = "bypass-permissions"
        const val SKIP_PERMISSIONS = true
    }
    
    /**
     * UI相关默认配置
     */
    object UI {
        const val DEFAULT_TAB_WIDTH = 200
        const val DEFAULT_INPUT_HEIGHT = 100
        const val MIN_INPUT_HEIGHT = 50
        const val MAX_INPUT_HEIGHT = 300
        const val DEFAULT_SIDEBAR_WIDTH = 300
        const val DEFAULT_MESSAGE_GAP = 12
    }
    
    /**
     * 文件索引默认配置
     */
    object FileIndex {
        const val INDEX_REFRESH_INTERVAL = 300000L // 5分钟
        const val INDEX_BATCH_SIZE = 100
        const val MAX_INDEXED_FILES = 10000
        val SUPPORTED_FILE_EXTENSIONS = listOf(
            "kt", "java", "js", "ts", "py", "cpp", "c", "h", 
            "cs", "go", "rs", "swift", "rb", "php", "dart",
            "json", "xml", "yaml", "yml", "properties", "gradle"
        )
    }
    
    /**
     * 网络相关默认配置
     */
    object Network {
        const val CONNECTION_TIMEOUT = 30000 // 30秒
        const val READ_TIMEOUT = 60000 // 60秒
        const val MAX_RETRIES = 3
        const val RETRY_DELAY = 1000L // 1秒
    }
    
    /**
     * 消息ID生成配置
     */
    object MessageId {
        const val PREFIX = "msg"
        const val TIMESTAMP_ENABLED = true
        const val RANDOM_SUFFIX_MAX = 999
    }
}