package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.plugin.mcp.ToolSchemaLoader
import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.usageView.UsageInfo
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

@Serializable
enum class SearchScope {
    Project, Module, Directory, Scope
}

@Serializable
data class CodeSearchMatch(
    val filePath: String,       // 相对路径
    val line: Int,              // 1-based
    val column: Int,            // 1-based
    val lineContent: String,    // 匹配所在行的内容
    val matchText: String,      // 匹配的文本
    val contextBefore: String? = null,  // 上下文（前）
    val contextAfter: String? = null    // 上下文（后）
)

@Serializable
data class CodeSearchResult(
    val query: String,
    val isRegex: Boolean,
    val caseSensitive: Boolean,
    val scope: SearchScope,
    val matches: List<CodeSearchMatch>,
    val totalMatches: Int,
    val filesWithMatches: Int,
    val hasMore: Boolean,
    val offset: Int,
    val limit: Int
)

/**
 * 代码搜索工具
 * 
 * 在项目文件中搜索代码或文本内容（类似 IDEA 的 Find in Files 功能）
 */
class CodeSearchTool(private val project: Project) {

    fun getInputSchema(): Map<String, Any> = ToolSchemaLoader.getSchema("CodeSearch")

    suspend fun execute(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String
            ?: return ToolResult.error("Missing required parameter: query")
        val isRegex = arguments["isRegex"] as? Boolean ?: false
        val caseSensitive = arguments["caseSensitive"] as? Boolean ?: false
        val wholeWords = arguments["wholeWords"] as? Boolean ?: false
        val fileMask = arguments["fileMask"] as? String
        val scopeStr = arguments["scope"] as? String ?: "Project"
        val scopeArg = arguments["scopeArg"] as? String
        val maxResults = ((arguments["maxResults"] as? Number)?.toInt() ?: 30).coerceAtLeast(1)
        val offset = ((arguments["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val includeContext = arguments["includeContext"] as? Boolean ?: false

        val scope = try {
            SearchScope.valueOf(scopeStr)
        } catch (e: Exception) {
            return ToolResult.error("Invalid search scope: $scopeStr")
        }

        if (query.isBlank()) {
            return ToolResult.error("Search query cannot be empty")
        }

        if (isRegex) {
            try {
                Regex(query)
            } catch (e: Exception) {
                return ToolResult.error("Invalid regex: ${e.message}")
            }
        }

        val matches = mutableListOf<CodeSearchMatch>()
        var totalMatches = 0
        val filesWithMatches = mutableSetOf<String>()

        val projectPath = project.basePath ?: return ToolResult.error("Project base path not found")

        try {
            // 1. 配置 FindModel
            val findModel = FindModel().apply {
                stringToFind = query
                isRegularExpressions = isRegex
                isCaseSensitive = caseSensitive
                isWholeWordsOnly = wholeWords

                // 关键配置：启用全局搜索模式
                isGlobal = true
                isFindAll = true
                isMultipleFiles = true
                isWithSubdirectories = true

                // 设置文件过滤
                if (!fileMask.isNullOrBlank()) {
                    fileFilter = fileMask
                }

                // 设置搜索范围
                when (scope) {
                    SearchScope.Project -> {
                        isProjectScope = true
                    }
                    SearchScope.Module -> {
                        if (scopeArg.isNullOrBlank()) {
                            throw IllegalArgumentException("Module scope requires scopeArg (module name)")
                        }
                        val module = ReadAction.compute<com.intellij.openapi.module.Module?, Exception> {
                            ModuleManager.getInstance(project).findModuleByName(scopeArg)
                        } ?: throw IllegalArgumentException("Module not found: $scopeArg")
                        moduleName = scopeArg
                        isProjectScope = false
                    }
                    SearchScope.Directory -> {
                        if (scopeArg.isNullOrBlank()) {
                            throw IllegalArgumentException("Directory scope requires scopeArg (directory path)")
                        }
                        val dirPath = File(projectPath, scopeArg).canonicalPath
                        val dir = LocalFileSystem.getInstance().findFileByPath(dirPath)
                        if (dir == null || !dir.isDirectory) {
                            throw IllegalArgumentException("Directory not found: $scopeArg")
                        }
                        directoryName = dirPath
                        isProjectScope = false
                    }
                    SearchScope.Scope -> {
                        isProjectScope = false
                        isCustomScope = true
                    }
                }
            }

            // 2. 执行搜索 - 需要在 ProgressIndicator 上下文中
            val usages = ConcurrentLinkedQueue<UsageInfo>()
            val maxToCollect = offset + maxResults + 100 // 多取一些以计算 hasMore

            val indicator = EmptyProgressIndicator()
            ProgressManager.getInstance().runProcess({
                val presentation = FindInProjectUtil.setupViewPresentation(findModel)
                val processPresentation = FindInProjectUtil.setupProcessPresentation(presentation)

                FindInProjectUtil.findUsages(
                    findModel,
                    project,
                    { usage ->
                        usages.add(usage)
                        usages.size < maxToCollect
                    },
                    processPresentation
                )
            }, indicator)

            totalMatches = usages.size

            // 3. 在 ReadAction 中转换结果
            val usageList = usages.toList()
            ReadAction.run<Exception> {
                usageList.drop(offset).take(maxResults).forEach { usageInfo ->
                    val file = usageInfo.virtualFile ?: return@forEach
                    val document = FileDocumentManager.getInstance().getDocument(file)

                    val relativePath = file.path.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
                    filesWithMatches.add(relativePath)

                    val startOffset = usageInfo.navigationOffset
                    val line = document?.getLineNumber(startOffset)?.plus(1) ?: 1
                    val lineStart = document?.getLineStartOffset(line - 1) ?: 0
                    val column = startOffset - lineStart + 1

                    val lineContent = document?.let { doc ->
                        val lineEnd = doc.getLineEndOffset(line - 1)
                        doc.getText(TextRange(lineStart, lineEnd))
                    } ?: ""

                    matches.add(CodeSearchMatch(
                        filePath = relativePath,
                        line = line,
                        column = column,
                        lineContent = lineContent.trim(),
                        matchText = usageInfo.element?.text?.take(100) ?: query,
                        contextBefore = if (includeContext && document != null && line > 1) {
                            val prevLineStart = document.getLineStartOffset(line - 2)
                            val prevLineEnd = document.getLineEndOffset(line - 2)
                            document.getText(TextRange(prevLineStart, prevLineEnd)).trim()
                        } else null,
                        contextAfter = if (includeContext && document != null && line < (document.lineCount)) {
                            val nextLineStart = document.getLineStartOffset(line)
                            val nextLineEnd = document.getLineEndOffset(line)
                            document.getText(TextRange(nextLineStart, nextLineEnd)).trim()
                        } else null
                    ))
                }
            }
        } catch (e: IllegalArgumentException) {
            return ToolResult.error(e.message ?: "Invalid argument")
        } catch (e: Exception) {
            return ToolResult.error("Search error: ${e.message}")
        }

        val sb = StringBuilder()
        val regexFlag = if (isRegex) " (regex)" else ""
        val caseFlag = if (caseSensitive) " (case sensitive)" else ""
        sb.appendLine("🔍 Search: \"$query\"$regexFlag$caseFlag")
        sb.appendLine("📁 Scope: $scope${scopeArg?.let { " ($it)" } ?: ""}")
        sb.appendLine()

        if (matches.isEmpty()) {
            sb.appendLine("No results found")
        } else {
            val groupedByFile = matches.groupBy { it.filePath }
            groupedByFile.forEach { (filePath, fileMatches) ->
                sb.appendLine("📄 $filePath")
                fileMatches.forEach { match ->
                    match.contextBefore?.let { sb.appendLine("   ${match.line - 1}│ $it") }
                    sb.appendLine("   ${match.line}│ ${match.lineContent}  ← match")
                    match.contextAfter?.let { sb.appendLine("   ${match.line + 1}│ $it") }
                    if (fileMatches.size > 1 && match != fileMatches.last()) {
                        sb.appendLine("   ...")
                    }
                }
                sb.appendLine()
            }
        }

        sb.append("📊 Found $totalMatches matches in ${filesWithMatches.size} files")
        if (offset + matches.size < totalMatches) {
            sb.append(" (showing ${offset + 1}-${offset + matches.size})")
        }

        return sb.toString()
    }
}
