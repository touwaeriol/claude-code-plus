package com.claudecodeplus.ui.jewel.components.context

/**
 * 上下文类型
 */
sealed class ContextType {
    object File : ContextType()
    object Web : ContextType()
    
    // 未来扩展类型
    object Terminal : ContextType()
    object Git : ContextType()
    object Symbol : ContextType()
    object Selection : ContextType()
    object Workspace : ContextType()
}

/**
 * 文件上下文项
 */
data class FileContextItem(
    val name: String,                // 文件名
    val relativePath: String,        // 相对路径
    val absolutePath: String,        // 绝对路径
    val isDirectory: Boolean,        // 是否为目录
    val fileType: String,           // 文件扩展名
    val size: Long = 0L,            // 文件大小（字节）
    val lastModified: Long = 0L     // 最后修改时间
) {
    /**
     * 获取文件类型图标
     */
    fun getIcon(): String {
        return if (isDirectory) {
            "📁"
        } else {
            when (fileType.lowercase()) {
                "kt" -> "🔷"
                "java" -> "☕"
                "js", "ts" -> "💛"
                "py" -> "🐍"
                "md" -> "📝"
                "json" -> "📋"
                "xml" -> "🔖"
                "yml", "yaml" -> "⚙️"
                "html", "htm" -> "🌐"
                "css" -> "🎨"
                "png", "jpg", "jpeg", "gif" -> "🖼️"
                "pdf" -> "📕"
                "txt" -> "📄"
                "gradle" -> "🔧"
                "properties" -> "⚙️"
                else -> "📄"
            }
        }
    }
    
    /**
     * 获取显示名称（带图标）
     */
    fun getDisplayName(): String = "${getIcon()} $name"
    
    /**
     * 获取相对路径显示（灰色小字）
     */
    fun getPathDisplay(): String = relativePath.ifEmpty { "/" }
}

/**
 * Web上下文项
 */
data class WebContextItem(
    val url: String,                 // URL地址
    val title: String? = null,       // 网页标题
    val description: String? = null, // 网页描述
    val favicon: String? = null      // 网站图标
) {
    /**
     * 验证URL格式是否正确
     */
    fun isValidUrl(): Boolean {
        return try {
            val urlPattern = Regex(
                "^(https?|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
            )
            url.matches(urlPattern)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取显示名称
     */
    fun getDisplayName(): String = title ?: url
    
    /**
     * 获取URL显示（灰色小字）
     */
    fun getUrlDisplay(): String = url
    
    /**
     * 获取图标
     */
    fun getIcon(): String = "🌐"
}

/**
 * 上下文选择状态
 */
sealed class ContextSelectionState {
    object Hidden : ContextSelectionState()                           // 隐藏状态
    object SelectingType : ContextSelectionState()                    // 选择类型阶段
    data class SelectingFile(val query: String = "") : ContextSelectionState()  // 选择文件阶段
    data class SelectingWeb(val url: String = "") : ContextSelectionState()     // 选择Web阶段
}

/**
 * 上下文选择结果
 */
sealed class ContextSelectionResult {
    data class FileSelected(val item: FileContextItem) : ContextSelectionResult()
    data class WebSelected(val item: WebContextItem) : ContextSelectionResult()
    object Cancelled : ContextSelectionResult()
}

/**
 * 搜索权重配置
 */
data class SearchWeight(
    val exactMatch: Int = 100,      // 完全匹配
    val prefixMatch: Int = 80,      // 前缀匹配
    val containsMatch: Int = 60,    // 包含匹配
    val pathMatch: Int = 40         // 路径匹配
)

/**
 * 文件搜索结果（带权重）
 */
data class FileSearchResult(
    val item: FileContextItem,
    val weight: Int,
    val matchType: MatchType
) {
    enum class MatchType {
        EXACT_NAME,     // 文件名完全匹配
        PREFIX_NAME,    // 文件名前缀匹配
        CONTAINS_NAME,  // 文件名包含匹配
        PATH_MATCH      // 路径匹配
    }
}

/**
 * 上下文选择配置
 */
data class ContextSelectorConfig(
    val maxResults: Int = 50,                   // 最大搜索结果数
    val searchDelayMs: Long = 200,              // 搜索延迟（去抖动）
    val popupWidth: Int = 300,                  // 弹出器宽度
    val popupMaxHeight: Int = 400,              // 弹出器最大高度
    val itemHeight: Int = 32,                   // 列表项高度
    val animationDurationMs: Int = 150,         // 动画持续时间
    val enableAnimations: Boolean = true,       // 是否启用动画
    val searchWeight: SearchWeight = SearchWeight()  // 搜索权重配置
) 