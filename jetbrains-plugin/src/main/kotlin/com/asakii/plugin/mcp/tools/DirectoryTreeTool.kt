package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.plugin.mcp.ToolSchemaLoader
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * 目录树条目
 */
@Serializable
data class DirectoryEntry(
    val name: String,
    val path: String,           // 相对路径
    val isDirectory: Boolean,
    val size: Long? = null,     // 文件大小（仅文件）
    val children: List<DirectoryEntry>? = null  // 子目录/文件
)

/**
 * 目录树工具返回结果
 */
@Serializable
data class DirectoryTreeResult(
    val root: String,
    val entries: List<DirectoryEntry>,
    val totalFiles: Int,
    val totalDirectories: Int,
    val truncated: Boolean = false,  // 是否因达到限制而截断
    val maxDepthReached: Boolean = false
)

/**
 * 目录树工具
 * 
 * 获取项目目录中指定文件夹的目录树结构
 */
class DirectoryTreeTool(private val project: Project) {

    fun getInputSchema(): Map<String, Any> = ToolSchemaLoader.getSchema("DirectoryTree")

    suspend fun execute(arguments: Map<String, Any>): Any {
        val path = (arguments["path"] as? String)?.takeIf { it.isNotBlank() } ?: "."
        val maxDepth = ((arguments["maxDepth"] as? Number)?.toInt() ?: 3).coerceIn(1, 10)
        val filesOnly = arguments["filesOnly"] as? Boolean ?: false
        val includeHidden = arguments["includeHidden"] as? Boolean ?: false
        val pattern = arguments["pattern"] as? String
        val maxEntries = ((arguments["maxEntries"] as? Number)?.toInt() ?: 100).coerceAtLeast(1)

        val projectPath = project.basePath
            ?: return ToolResult.error("Cannot get project path")

        val targetPath = File(projectPath, path).canonicalPath
        if (!targetPath.startsWith(File(projectPath).canonicalPath)) {
            return ToolResult.error("Path must be within project directory")
        }

        val targetDir = File(targetPath)
        if (!targetDir.exists()) {
            return ToolResult.error("Directory not found: $path")
        }
        if (!targetDir.isDirectory) {
            return ToolResult.error("Path is not a directory: $path")
        }

        // 编译 glob 模式
        val matcher: PathMatcher? = pattern?.let {
            FileSystems.getDefault().getPathMatcher("glob:$it")
        }

        var totalFiles = 0
        var totalDirectories = 0
        var entriesCount = 0
        var truncated = false
        var maxDepthReached = false

        fun buildTree(dir: File, currentDepth: Int, relativePath: String): List<DirectoryEntry> {
            if (entriesCount >= maxEntries) {
                truncated = true
                return emptyList()
            }
            
            if (currentDepth > maxDepth) {
                maxDepthReached = true
                return emptyList()
            }

            val entries = mutableListOf<DirectoryEntry>()
            val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) 
                ?: return entries

            for (file in files) {
                if (entriesCount >= maxEntries) {
                    truncated = true
                    break
                }

                // 过滤隐藏文件
                if (!includeHidden && file.name.startsWith(".")) continue

                val fileRelativePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

                if (file.isDirectory) {
                    if (!filesOnly) {
                        totalDirectories++
                        entriesCount++
                        
                        val children = if (currentDepth < maxDepth) {
                            buildTree(file, currentDepth + 1, fileRelativePath)
                        } else {
                            maxDepthReached = true
                            null
                        }

                        entries.add(DirectoryEntry(
                            name = file.name,
                            path = fileRelativePath,
                            isDirectory = true,
                            children = children
                        ))
                    } else {
                        // filesOnly 模式下仍需递归目录
                        entries.addAll(buildTree(file, currentDepth + 1, fileRelativePath))
                    }
                } else {
                    // 应用文件名模式过滤
                    if (matcher != null && !matcher.matches(java.nio.file.Paths.get(file.name))) {
                        continue
                    }

                    totalFiles++
                    entriesCount++
                    entries.add(DirectoryEntry(
                        name = file.name,
                        path = fileRelativePath,
                        isDirectory = false,
                        size = file.length()
                    ))
                }
            }

            return entries
        }

        val entries = buildTree(targetDir, 1, "")

        // 生成易读的树形文本格式
        val sb = StringBuilder()
        sb.appendLine("📂 $path")

        fun renderTree(items: List<DirectoryEntry>, prefix: String = "") {
            items.forEachIndexed { index, entry ->
                val isLast = index == items.lastIndex
                val connector = if (isLast) "└── " else "├── "
                val icon = if (entry.isDirectory) "📁" else "📄"
                val sizeInfo = entry.size?.let { " (${formatSize(it)})" } ?: ""

                sb.appendLine("$prefix$connector$icon ${entry.name}$sizeInfo")

                entry.children?.let { children ->
                    val newPrefix = prefix + if (isLast) "    " else "│   "
                    renderTree(children, newPrefix)
                }
            }
        }

        renderTree(entries)

        sb.appendLine()
        sb.append("📊 Statistics: $totalFiles files, $totalDirectories directories")
        if (truncated) sb.append(" (truncated, max entries reached)")
        if (maxDepthReached) sb.append(" (max depth reached)")

        return sb.toString()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
}
