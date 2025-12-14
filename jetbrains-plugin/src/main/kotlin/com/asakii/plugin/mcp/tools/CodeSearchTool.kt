package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfo2UsageAdapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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

    fun getInputSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "搜索文本或正则表达式"
            ),
            "isRegex" to mapOf(
                "type" to "boolean",
                "description" to "是否使用正则表达式",
                "default" to false
            ),
            "caseSensitive" to mapOf(
                "type" to "boolean",
                "description" to "是否区分大小写",
                "default" to false
            ),
            "wholeWords" to mapOf(
                "type" to "boolean",
                "description" to "是否全词匹配",
                "default" to false
            ),
            "fileMask" to mapOf(
                "type" to "string",
                "description" to "文件类型过滤（如 \"*.kt,*.java\" 或 \"*.{kt,java}\"）"
            ),
            "scope" to mapOf(
                "type" to "string",
                "enum" to listOf("Project", "Module", "Directory", "Scope"),
                "description" to "搜索范围：Project（整个项目）、Module（指定模块）、Directory（指定目录）、Scope（指定 Scope）",
                "default" to "Project"
            ),
            "scopeArg" to mapOf(
                "type" to "string",
                "description" to "范围参数：模块名（Module）、相对目录路径（Directory）或 Scope 名称（Scope）"
            ),
            "maxResults" to mapOf(
                "type" to "integer",
                "description" to "最大结果数",
                "default" to 100,
                "minimum" to 1,
                "maximum" to 500
            ),
            "offset" to mapOf(
                "type" to "integer",
                "description" to "分页偏移量",
                "default" to 0,
                "minimum" to 0
            ),
            "includeContext" to mapOf(
                "type" to "boolean",
                "description" to "是否包含上下文行",
                "default" to false
            )
        ),
        "required" to listOf("query")
    )

    suspend fun execute(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String
            ?: return ToolResult.error("缺少必需参数: query")
        val isRegex = arguments["isRegex"] as? Boolean ?: false
        val caseSensitive = arguments["caseSensitive"] as? Boolean ?: false
        val wholeWords = arguments["wholeWords"] as? Boolean ?: false
        val fileMask = arguments["fileMask"] as? String
        val scopeStr = arguments["scope"] as? String ?: "Project"
        val scopeArg = arguments["scopeArg"] as? String
        val maxResults = ((arguments["maxResults"] as? Number)?.toInt() ?: 100).coerceIn(1, 500)
        val offset = ((arguments["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val includeContext = arguments["includeContext"] as? Boolean ?: false

        val scope = try {
            SearchScope.valueOf(scopeStr)
        } catch (e: Exception) {
            return ToolResult.error("无效的搜索范围: $scopeStr")
        }

        if (query.isBlank()) {
            return ToolResult.error("搜索内容不能为空")
        }

        // 验证正则表达式
        if (isRegex) {
            try {
                Regex(query)
            } catch (e: Exception) {
                return ToolResult.error("无效的正则表达式: ${e.message}")
            }
        }

        val matches = mutableListOf<CodeSearchMatch>()
        var totalMatches = 0
        val filesWithMatches = mutableSetOf<String>()

        try {
            ReadAction.run<Exception> {
                val findManager = FindManager.getInstance(project)
                val findModel = FindModel().apply {
                    stringToFind = query
                    this.isRegularExpressions = isRegex
                    this.isCaseSensitive = caseSensitive
                    this.isWholeWordsOnly = wholeWords
                    this.isProjectScope = (scope == SearchScope.Project)
                    
                    // 设置文件过滤
                    if (!fileMask.isNullOrBlank()) {
                        this.fileFilter = fileMask
                        this.isCustomScope = true
                    }
                }

                // 根据 scope 设置搜索范围
                val searchScope = when (scope) {
                    SearchScope.Project -> GlobalSearchScope.projectScope(project)
                    SearchScope.Module -> {
                        if (scopeArg.isNullOrBlank()) {
                            return@run // 需要模块名
                        }
                        val module = ModuleManager.getInstance(project).findModuleByName(scopeArg)
                        if (module != null) {
                            GlobalSearchScope.moduleScope(module)
                        } else {
                            return@run // 模块不存在
                        }
                    }
                    SearchScope.Directory -> {
                        if (scopeArg.isNullOrBlank()) {
                            return@run // 需要目录路径
                        }
                        val projectPath = project.basePath ?: return@run
                        val dirPath = File(projectPath, scopeArg).canonicalPath
                        val dir = LocalFileSystem.getInstance().findFileByPath(dirPath)
                        if (dir != null && dir.isDirectory) {
                            GlobalSearchScope.projectScope(project).restrictToDirectory(dir, true)
                        } else {
                            return@run // 目录不存在
                        }
                    }
                    SearchScope.Scope -> {
                        // 使用命名 Scope（简化实现）
                        GlobalSearchScope.projectScope(project)
                    }
                }

                // 执行搜索
                val usages = mutableListOf<UsageInfo2UsageAdapter>()
                val presentation = FindInProjectUtil.setupViewPresentation(findModel)
                val processPresentation = FindInProjectUtil.setupProcessPresentation(presentation)
                FindInProjectUtil.findUsages(
                    findModel,
                    project,
                    { usage ->
                        if (usage is UsageInfo2UsageAdapter) {
                            usages.add(usage)
                        }
                        usages.size < (offset + maxResults + 100) // 多取一些以计算 hasMore
                    },
                    processPresentation
                )

                totalMatches = usages.size
                
                // 转换结果
                usages.drop(offset).take(maxResults).forEach { usage ->
                    val usageInfo = usage.usageInfo
                    val file = usageInfo.virtualFile ?: return@forEach
                    val document = usageInfo.element?.let {
                        PsiDocumentManager.getInstance(project).getDocument(it.containingFile)
                    }
                    
                    val projectPath = project.basePath ?: ""
                    val relativePath = file.path.removePrefix(projectPath).removePrefix("/")
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
        } catch (e: Exception) {
            return ToolResult.error("搜索时出错: ${e.message}")
        }

        val result = CodeSearchResult(
            query = query,
            isRegex = isRegex,
            caseSensitive = caseSensitive,
            scope = scope,
            matches = matches,
            totalMatches = totalMatches,
            filesWithMatches = filesWithMatches.size,
            hasMore = offset + matches.size < totalMatches,
            offset = offset,
            limit = maxResults
        )

        return Json.encodeToString(result)
    }
    
    // 扩展函数：限制搜索范围到目录下
    private fun GlobalSearchScope.restrictToDirectory(dir: VirtualFile, recursive: Boolean): GlobalSearchScope {
        return object : GlobalSearchScope(project) {
            override fun contains(file: VirtualFile): Boolean {
                if (recursive) {
                    var parent = file.parent
                    while (parent != null) {
                        if (parent == dir) return true
                        parent = parent.parent
                    }
                    return false
                } else {
                    return file.parent == dir
                }
            }
            override fun isSearchInModuleContent(aModule: Module) = true
            override fun isSearchInLibraries() = false
        }
    }
}
