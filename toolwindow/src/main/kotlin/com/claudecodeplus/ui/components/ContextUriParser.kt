/*
 * ContextUriParser.kt
 * 
 * 解析和生成各种类型的上下文 URI
 */

package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.ui.models.ContextReference
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Claude 上下文 URI 协议
 */
const val CLAUDE_CONTEXT_SCHEME = "claude-context"

/**
 * 上下文类型枚举
 */
enum class ContextType(val value: String) {
    FILE("file"),
    WEB("web"),
    FOLDER("folder"),
    SYMBOL("symbol"),
    TERMINAL("terminal"),
    PROBLEMS("problems"),
    GIT("git"),
    IMAGE("image"),
    SELECTION("selection"),
    WORKSPACE("workspace");
    
    companion object {
        fun fromValue(value: String): ContextType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * 上下文 URI 数据类
 */
sealed class ContextUri {
    abstract fun toUriString(): String
    abstract fun toDisplayName(): String
    
    data class FileUri(
        val path: String,
        val line: Int? = null,
        val column: Int? = null
    ) : ContextUri() {
        override fun toUriString(): String {
            val encodedPath = URLEncoder.encode(path, "UTF-8")
            val base = "$CLAUDE_CONTEXT_SCHEME://${ContextType.FILE.value}/$encodedPath"
            return when {
                line != null && column != null -> "$base#L$line:$column"
                line != null -> "$base#L$line"
                else -> base
            }
        }
        
        override fun toDisplayName(): String {
            val fileName = path.substringAfterLast('/')
            return when {
                line != null -> "$fileName:$line"
                else -> fileName
            }
        }
    }
    
    data class WebUri(
        val url: String,
        val title: String? = null
    ) : ContextUri() {
        override fun toUriString(): String {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val base = "$CLAUDE_CONTEXT_SCHEME://${ContextType.WEB.value}/$encodedUrl"
            return if (title != null) {
                "$base?title=${URLEncoder.encode(title, "UTF-8")}"
            } else {
                base
            }
        }
        
        override fun toDisplayName(): String {
            return title ?: url.substringAfter("://")
                .substringBefore("/")
                .removePrefix("www.")
        }
    }
    
    data class FolderUri(val path: String) : ContextUri() {
        override fun toUriString(): String {
            val encodedPath = URLEncoder.encode(path, "UTF-8")
            return "$CLAUDE_CONTEXT_SCHEME://${ContextType.FOLDER.value}/$encodedPath"
        }
        
        override fun toDisplayName(): String {
            return path.substringAfterLast('/')
        }
    }
    
    data class SymbolUri(
        val name: String,
        val qualifiedName: String,
        val file: String? = null
    ) : ContextUri() {
        override fun toUriString(): String {
            val encodedName = URLEncoder.encode(qualifiedName, "UTF-8")
            val base = "$CLAUDE_CONTEXT_SCHEME://${ContextType.SYMBOL.value}/$encodedName"
            return if (file != null) {
                "$base?file=${URLEncoder.encode(file, "UTF-8")}"
            } else {
                base
            }
        }
        
        override fun toDisplayName(): String = name
    }
    
    data class ImageUri(
        val path: String,
        val mimeType: String? = null
    ) : ContextUri() {
        override fun toUriString(): String {
            val encodedPath = URLEncoder.encode(path, "UTF-8")
            val base = "$CLAUDE_CONTEXT_SCHEME://${ContextType.IMAGE.value}/$encodedPath"
            return if (mimeType != null) {
                "$base?mime=${URLEncoder.encode(mimeType, "UTF-8")}"
            } else {
                base
            }
        }
        
        override fun toDisplayName(): String {
            return path.substringAfterLast('/')
        }
    }
    
    object TerminalUri : ContextUri() {
        override fun toUriString() = "$CLAUDE_CONTEXT_SCHEME://${ContextType.TERMINAL.value}"
        override fun toDisplayName() = "Terminal"
    }
    
    object ProblemsUri : ContextUri() {
        override fun toUriString() = "$CLAUDE_CONTEXT_SCHEME://${ContextType.PROBLEMS.value}"
        override fun toDisplayName() = "Problems"
    }
    
    object GitUri : ContextUri() {
        override fun toUriString() = "$CLAUDE_CONTEXT_SCHEME://${ContextType.GIT.value}"
        override fun toDisplayName() = "Git"
    }
    
    object SelectionUri : ContextUri() {
        override fun toUriString() = "$CLAUDE_CONTEXT_SCHEME://${ContextType.SELECTION.value}"
        override fun toDisplayName() = "Selection"
    }
    
    object WorkspaceUri : ContextUri() {
        override fun toUriString() = "$CLAUDE_CONTEXT_SCHEME://${ContextType.WORKSPACE.value}"
        override fun toDisplayName() = "Workspace"
    }
}

/**
 * 解析 URI 字符串为 ContextUri 对象
 */
fun parseContextUri(uriString: String): ContextUri? {
    // 处理旧格式的 file:// URI
    if (uriString.startsWith("file://")) {
        val path = uriString.removePrefix("file://")
        return ContextUri.FileUri(path)
    }
    
    // 处理标准 HTTP(S) URI
    if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
        return ContextUri.WebUri(uriString)
    }
    
    // 解析 claude-context:// URI
    if (!uriString.startsWith("$CLAUDE_CONTEXT_SCHEME://")) {
        return null
    }
    
    val uri = try {
        java.net.URI(uriString)
    } catch (e: Exception) {
        return null
    }
    
    val pathParts = uri.path?.removePrefix("/")?.split("/", limit = 2) ?: return null
    if (pathParts.isEmpty()) return null
    
    val contextType = ContextType.fromValue(pathParts[0]) ?: return null
    
    return when (contextType) {
        ContextType.FILE -> {
            if (pathParts.size < 2) return null
            val path = URLDecoder.decode(pathParts[1], "UTF-8")
            val fragment = uri.fragment
            
            val (line, column) = if (fragment != null && fragment.startsWith("L")) {
                val lineCol = fragment.removePrefix("L").split(":")
                val line = lineCol.getOrNull(0)?.toIntOrNull()
                val column = lineCol.getOrNull(1)?.toIntOrNull()
                line to column
            } else {
                null to null
            }
            
            ContextUri.FileUri(path, line, column)
        }
        
        ContextType.WEB -> {
            if (pathParts.size < 2) return null
            val url = URLDecoder.decode(pathParts[1], "UTF-8")
            val title = uri.query?.let { query ->
                query.split("&")
                    .find { it.startsWith("title=") }
                    ?.removePrefix("title=")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
            }
            ContextUri.WebUri(url, title)
        }
        
        ContextType.FOLDER -> {
            if (pathParts.size < 2) return null
            val path = URLDecoder.decode(pathParts[1], "UTF-8")
            ContextUri.FolderUri(path)
        }
        
        ContextType.SYMBOL -> {
            if (pathParts.size < 2) return null
            val qualifiedName = URLDecoder.decode(pathParts[1], "UTF-8")
            val file = uri.query?.let { query ->
                query.split("&")
                    .find { it.startsWith("file=") }
                    ?.removePrefix("file=")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
            }
            val name = qualifiedName.substringAfterLast('.')
            ContextUri.SymbolUri(name, qualifiedName, file)
        }
        
        ContextType.IMAGE -> {
            if (pathParts.size < 2) return null
            val path = URLDecoder.decode(pathParts[1], "UTF-8")
            val mimeType = uri.query?.let { query ->
                query.split("&")
                    .find { it.startsWith("mime=") }
                    ?.removePrefix("mime=")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
            }
            ContextUri.ImageUri(path, mimeType)
        }
        
        ContextType.TERMINAL -> ContextUri.TerminalUri
        ContextType.PROBLEMS -> ContextUri.ProblemsUri
        ContextType.GIT -> ContextUri.GitUri
        ContextType.SELECTION -> ContextUri.SelectionUri
        ContextType.WORKSPACE -> ContextUri.WorkspaceUri
    }
}

/**
 * 将 ContextReference 转换为 ContextUri
 */
fun ContextReference.toContextUri(): ContextUri {
    return when (this) {
        is ContextReference.FileReference -> ContextUri.FileUri(path)
        is ContextReference.WebReference -> ContextUri.WebUri(url, title)
        is ContextReference.FolderReference -> ContextUri.FolderUri(path)
        is ContextReference.SymbolReference -> ContextUri.SymbolUri(name, name, file)
        is ContextReference.ImageReference -> ContextUri.ImageUri(path, mimeType)
        is ContextReference.TerminalReference -> ContextUri.TerminalUri
        is ContextReference.ProblemsReference -> ContextUri.ProblemsUri
        is ContextReference.GitReference -> ContextUri.GitUri
        is ContextReference.SelectionReference -> ContextUri.SelectionUri
        is ContextReference.WorkspaceReference -> ContextUri.WorkspaceUri
    }
}

/**
 * 生成 Markdown 格式的上下文链接
 */
fun ContextReference.toMarkdownLink(): String {
    val uri = toContextUri()
    return "[@${uri.toDisplayName()}](${uri.toUriString()})"
}