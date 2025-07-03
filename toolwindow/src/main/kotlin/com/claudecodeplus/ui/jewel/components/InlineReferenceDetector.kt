/*
 * InlineReferenceDetector.kt
 * 
 * 内联引用检测器 - 可扩展的引用格式检测系统
 */

package com.claudecodeplus.ui.jewel.components

/**
 * 内联引用类型定义
 */
enum class InlineReferenceScheme(
    val prefix: String,
    val displayName: String,
    val description: String
) {
    FILE("file://", "文件", "本地文件引用"),
    HTTP("http://", "网页", "HTTP网页链接"),
    HTTPS("https://", "网页", "HTTPS网页链接"),
    GIT("git://", "Git", "Git仓库引用"),
    SYMBOL("symbol://", "符号", "代码符号引用"),
    TERMINAL("terminal://", "终端", "终端命令引用"),
    WORKSPACE("workspace://", "工作区", "工作区引用");
    
    /**
     * 创建匹配此类型引用的完整正则表达式
     * 匹配格式：@prefix + path
     */
    fun createFullPattern(): Regex {
        val escapedPrefix = Regex.escape(prefix)
        return Regex("@$escapedPrefix([^\\s]+)")
    }

    companion object {
        /**
         * 获取所有支持的引用前缀
         */
        fun getAllPrefixes(): List<String> = values().map { it.prefix }
        
        /**
         * 根据前缀查找引用类型
         */
        fun fromPrefix(prefix: String): InlineReferenceScheme? = 
            values().find { prefix.startsWith(it.prefix) }
        
        /**
         * 生成用于检测现有引用的正则表达式
         */
        fun generateExistingReferencePattern(): Regex {
            val prefixes = getAllPrefixes().joinToString("|") { Regex.escape(it) }
            return Regex("^($prefixes)\\w+")
        }
        
        /**
         * 生成用于解析所有引用的正则表达式
         */
        fun generateParsePattern(): Regex {
            val prefixes = getAllPrefixes().joinToString("|") { Regex.escape(it) }
            return Regex("@($prefixes[^\\s]+)")
        }
    }
}

/**
 * 内联引用检测器
 */
object InlineReferenceDetector {
    
    /**
     * 检测@符号输入是否应该触发上下文选择
     * 优化版本：支持可扩展的引用格式检测
     */
    fun shouldTriggerContextSelector(text: String, cursorPosition: Int): Boolean {
        if (cursorPosition == 0) return false
        
        // 检查光标前是否是@符号
        val beforeCursor = text.substring(0, cursorPosition)
        if (beforeCursor.isEmpty() || beforeCursor.last() != '@') return false
        
        // 检查@符号前面的字符（如果存在）是否为空格或换行
        val validPrefix = beforeCursor.length == 1 || beforeCursor[beforeCursor.length - 2].let { 
            it == ' ' || it == '\n' 
        }
        if (!validPrefix) return false
        
        // 检查@符号后面是否已经有现有的引用格式
        val afterCursor = text.substring(cursorPosition)
        val existingReferencePattern = InlineReferenceScheme.generateExistingReferencePattern()
        
        return !existingReferencePattern.containsMatchIn(afterCursor)
    }
    
    /**
     * 检测文本中是否包含内联引用
     */
    fun hasInlineReferences(text: String): Boolean {
        val pattern = InlineReferenceScheme.generateParsePattern()
        return pattern.containsMatchIn(text)
    }
    
    /**
     * 提取文本中的所有内联引用
     */
    fun extractReferences(text: String): List<ExtractedReference> {
        val pattern = InlineReferenceScheme.generateParsePattern()
        val references = mutableListOf<ExtractedReference>()
        
        pattern.findAll(text).forEach { match ->
            val fullMatch = match.value // @file://path
            val urlPart = match.groupValues[1] // file://path
            val scheme = InlineReferenceScheme.fromPrefix(urlPart)
            
            if (scheme != null) {
                references.add(
                    ExtractedReference(
                        fullText = fullMatch,
                        scheme = scheme,
                        path = urlPart.removePrefix(scheme.prefix),
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1
                    )
                )
            }
        }
        
        return references
    }
}

/**
 * 提取的引用信息
 */
data class ExtractedReference(
    val fullText: String,           // @file://path
    val scheme: InlineReferenceScheme,
    val path: String,               // path部分
    val startIndex: Int,
    val endIndex: Int
) {
    /**
     * 获取显示文本（通常是文件名或最后一段）
     */
    fun getDisplayText(): String = when (scheme) {
        InlineReferenceScheme.FILE -> {
            val fileName = path.substringAfterLast('/')
            "@$fileName"
        }
        InlineReferenceScheme.HTTP, InlineReferenceScheme.HTTPS -> {
            val domain = path.substringBefore('/')
            "@$domain"
        }
        InlineReferenceScheme.GIT -> {
            val repoName = path.substringAfterLast('/').removeSuffix(".git")
            "@$repoName"
        }
        InlineReferenceScheme.SYMBOL -> {
            val symbolName = path.substringAfterLast('/')
            "@$symbolName"
        }
        InlineReferenceScheme.TERMINAL -> "@terminal"
        InlineReferenceScheme.WORKSPACE -> "@workspace"
    }
    
    /**
     * 获取工具提示文本
     */
    fun getTooltipText(): String = when (scheme) {
        InlineReferenceScheme.FILE -> "文件: $path"
        InlineReferenceScheme.HTTP, InlineReferenceScheme.HTTPS -> "网页: ${scheme.prefix}$path"
        InlineReferenceScheme.GIT -> "Git仓库: $path"
        InlineReferenceScheme.SYMBOL -> "代码符号: $path"
        InlineReferenceScheme.TERMINAL -> "终端命令"
        InlineReferenceScheme.WORKSPACE -> "工作区"
    }
}

/**
 * 引用格式验证器
 */
object ReferenceValidator {
    
    /**
     * 验证文件路径格式
     */
    fun validateFilePath(path: String): Boolean {
        return path.isNotBlank() && !path.contains("..") && path.matches(Regex("[a-zA-Z0-9._/-]+"))
    }
    
    /**
     * 验证URL格式
     */
    fun validateUrl(url: String): Boolean {
        return try {
            val urlPattern = Regex("^[a-zA-Z0-9+&@#/%?=~_|!:,.;-]*[a-zA-Z0-9+&@#/%=~_|-]")
            url.matches(urlPattern)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 根据引用类型验证路径
     */
    fun validateReference(scheme: InlineReferenceScheme, path: String): Boolean {
        return when (scheme) {
            InlineReferenceScheme.FILE -> validateFilePath(path)
            InlineReferenceScheme.HTTP, InlineReferenceScheme.HTTPS -> validateUrl(path)
            InlineReferenceScheme.GIT -> validateUrl(path)
            else -> path.isNotBlank()
        }
    }
}