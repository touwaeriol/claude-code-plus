package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    fun getInputSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "相对于项目根目录的路径，默认为项目根目录（\".\"）",
                "default" to "."
            ),
            "maxDepth" to mapOf(
                "type" to "integer",
                "description" to "最大递归深度，默认 3，最大 10",
                "default" to 3,
                "minimum" to 1,
                "maximum" to 10
            ),
            "filesOnly" to mapOf(
                "type" to "boolean",
                "description" to "是否只显示文件而不显示目录",
                "default" to false
            ),
            "includeHidden" to mapOf(
                "type" to "boolean",
                "description" to "是否包含隐藏文件/目录（以 . 开头）",
                "default" to false
            ),
            "pattern" to mapOf(
                "type" to "string",
                "description" to "文件名匹配模式（glob 格式，如 \"*.kt\"、\"*.{kt,java}\"）"
            ),
            "maxEntries" to mapOf(
                "type" to "integer",
                "description" to "最大返回条目数，防止结果过大",
                "default" to 500,
                "maximum" to 2000
            )
        ),
        "required" to emptyList<String>()
    )

    suspend fun execute(arguments: Map<String, Any>): Any {
        val path = (arguments["path"] as? String)?.takeIf { it.isNotBlank() } ?: "."
        val maxDepth = ((arguments["maxDepth"] as? Number)?.toInt() ?: 3).coerceIn(1, 10)
        val filesOnly = arguments["filesOnly"] as? Boolean ?: false
        val includeHidden = arguments["includeHidden"] as? Boolean ?: false
        val pattern = arguments["pattern"] as? String
        val maxEntries = ((arguments["maxEntries"] as? Number)?.toInt() ?: 500).coerceIn(1, 2000)

        val projectPath = project.basePath 
            ?: return ToolResult.error("无法获取项目路径")
        
        // 验证路径安全性（防止目录遍历攻击）
        val targetPath = File(projectPath, path).canonicalPath
        if (!targetPath.startsWith(File(projectPath).canonicalPath)) {
            return ToolResult.error("路径必须在项目目录内")
        }
        
        val targetDir = File(targetPath)
        if (!targetDir.exists()) {
            return ToolResult.error("目录不存在: $path")
        }
        if (!targetDir.isDirectory) {
            return ToolResult.error("指定路径不是目录: $path")
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

        val result = DirectoryTreeResult(
            root = path,
            entries = entries,
            totalFiles = totalFiles,
            totalDirectories = totalDirectories,
            truncated = truncated,
            maxDepthReached = maxDepthReached
        )

        return Json.encodeToString(result)
    }
}
