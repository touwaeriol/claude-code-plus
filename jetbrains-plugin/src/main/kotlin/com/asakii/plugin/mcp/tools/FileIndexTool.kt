package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.psi.codeStyle.NameUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class SearchIndexType {
    All, Classes, Files, Symbols, Actions, Text
}

@Serializable
enum class SearchIndexScope {
    Project,            // 仅项目文件
    All,                // 所有地方（包括库）
    ProductionFiles,    // 仅生产代码（不含测试）
    TestFiles,          // 仅测试代码
    Scratches           // Scratches and Consoles
}

@Serializable
data class IndexSearchResult(
    val name: String,
    val path: String? = null,
    val type: String,           // 结果类型：File, Class, Method, Field, Action 等
    val description: String? = null,
    val line: Int? = null       // 符号所在行
)

@Serializable
data class FileIndexSearchResult(
    val query: String,
    val searchType: SearchIndexType,
    val results: List<IndexSearchResult>,
    val totalFound: Int,
    val hasMore: Boolean,
    val offset: Int,
    val limit: Int
)

/**
 * 文件索引工具
 * 
 * 通过关键词在 IDEA 索引中搜索文件、类、符号、动作或文本
 */
class FileIndexTool(private val project: Project) {

    fun getInputSchema(): Map<String, Any> = ToolSchemaLoader.getSchema("FileIndex")

    fun execute(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String
            ?: return ToolResult.error("Missing required parameter: query")
        val searchTypeStr = arguments["searchType"] as? String ?: "All"
        val scopeStr = arguments["scope"] as? String ?: "Project"
        val maxResults = ((arguments["maxResults"] as? Number)?.toInt() ?: 20).coerceAtLeast(1)
        val offset = ((arguments["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

        val searchType = try {
            SearchIndexType.valueOf(searchTypeStr)
        } catch (_: Exception) {
            return ToolResult.error("Invalid search type: $searchTypeStr")
        }

        val searchScope = try {
            SearchIndexScope.valueOf(scopeStr)
        } catch (_: Exception) {
            return ToolResult.error("Invalid scope: $scopeStr. Valid values: Project, All, ProductionFiles, TestFiles, Scratches")
        }

        if (query.isBlank()) {
            return ToolResult.error("Search keyword cannot be empty")
        }

        // 等待索引完成，确保能搜索到最新的文件内容
        DumbService.getInstance(project).waitForSmartMode()

        val results = mutableListOf<IndexSearchResult>()
        var totalFound = 0

        try {
            ReadAction.run<Exception> {
                val baseScope = createSearchScope(searchScope)

                // 是否包含库（用于 Classes/Symbols 搜索）
                val includeLibraries = searchScope == SearchIndexScope.All

                when (searchType) {
                    SearchIndexType.Files, SearchIndexType.All -> {
                        // 文件搜索 - 使用 IDEA 原生匹配器排序
                        val matcher = NameUtil.buildMatcher("*$query").build()
                        val allFileNames = FilenameIndex.getAllFilenames(project)

                        // 过滤并按匹配度排序
                        val sortedFileNames = allFileNames
                            .filter { matcher.matches(it) }
                            .sortedByDescending { matcher.matchingDegree(it) }

                        val matchingFiles = sortedFileNames
                            .flatMap { fileName ->
                                FilenameIndex.getVirtualFilesByName(fileName, baseScope).map { file ->
                                    val relativePath = project.basePath?.let {
                                        file.path.removePrefix(it).removePrefix("/")
                                    } ?: file.path

                                    IndexSearchResult(
                                        name = file.name,
                                        path = relativePath,
                                        type = "File",
                                        description = file.extension?.let { ".$it file" }
                                    )
                                }
                            }
                            .distinctBy { it.path }

                        totalFound += matchingFiles.size
                        results.addAll(matchingFiles.drop(offset).take(maxResults))
                    }
                    
                    SearchIndexType.Classes -> {
                        // 类搜索 - 需要 Java 插件支持
                        val cache = JavaPluginHelper.getShortNamesCache(project)
                        if (cache == null) {
                            results.add(IndexSearchResult(
                                name = "Classes search",
                                type = "Info",
                                description = "Classes search requires Java plugin. Not available in WebStorm/PyCharm."
                            ))
                            totalFound = 1
                        } else {
                            val matcher = NameUtil.buildMatcher("*$query").build()
                            val allClassNames = JavaPluginHelper.getAllClassNames(cache)

                            // 过滤并按匹配度排序
                            val sortedNames = allClassNames
                                .filter { matcher.matches(it) }
                                .sortedByDescending { matcher.matchingDegree(it) }

                            // 收集结果（按排序后的顺序）
                            val classSearchScope = if (includeLibraries) GlobalSearchScope.allScope(project) else baseScope
                            val filteredResults = sortedNames.flatMap { name ->
                                JavaPluginHelper.getClassesByName(cache, name, classSearchScope).mapNotNull { psiClass ->
                                    val file = psiClass.containingFile?.virtualFile
                                    if (file != null && baseScope.contains(file)) {
                                        IndexSearchResult(
                                            name = name,
                                            path = file.path.removePrefix(project.basePath ?: "").removePrefix("/"),
                                            type = "Class",
                                            description = getElementDescription(psiClass)
                                        )
                                    } else null
                                }
                            }.distinctBy { "${it.name}:${it.path}" }
                            totalFound = filteredResults.size
                            results.addAll(filteredResults.drop(offset).take(maxResults))
                        }
                    }

                    SearchIndexType.Symbols -> {
                        // 符号搜索 - 需要 Java 插件支持
                        val cache = JavaPluginHelper.getShortNamesCache(project)
                        if (cache == null) {
                            results.add(IndexSearchResult(
                                name = "Symbols search",
                                type = "Info",
                                description = "Symbols search requires Java plugin. Not available in WebStorm/PyCharm."
                            ))
                            totalFound = 1
                        } else {
                            val matcher = NameUtil.buildMatcher("*$query").build()
                            val symbolSearchScope = if (includeLibraries) GlobalSearchScope.allScope(project) else baseScope

                            // 搜索方法 - 按匹配度排序
                            val allMethodNames = JavaPluginHelper.getAllMethodNames(cache)
                            val sortedMethodNames = allMethodNames
                                .filter { matcher.matches(it) }
                                .sortedByDescending { matcher.matchingDegree(it) }

                            val methodResults = sortedMethodNames.flatMap { name ->
                                JavaPluginHelper.getMethodsByName(cache, name, symbolSearchScope).mapNotNull { method ->
                                    val file = method.containingFile?.virtualFile
                                    if (file != null && baseScope.contains(file)) {
                                        IndexSearchResult(
                                            name = name,
                                            path = file.path.removePrefix(project.basePath ?: "").removePrefix("/"),
                                            type = "Method",
                                            description = getElementDescription(method),
                                            line = getElementLine(method)
                                        )
                                    } else null
                                }
                            }

                            // 搜索字段 - 按匹配度排序
                            val allFieldNames = JavaPluginHelper.getAllFieldNames(cache)
                            val sortedFieldNames = allFieldNames
                                .filter { matcher.matches(it) }
                                .sortedByDescending { matcher.matchingDegree(it) }

                            val fieldResults = sortedFieldNames.flatMap { name ->
                                JavaPluginHelper.getFieldsByName(cache, name, symbolSearchScope).mapNotNull { field ->
                                    val file = field.containingFile?.virtualFile
                                    if (file != null && baseScope.contains(file)) {
                                        IndexSearchResult(
                                            name = name,
                                            path = file.path.removePrefix(project.basePath ?: "").removePrefix("/"),
                                            type = "Field",
                                            description = getElementDescription(field),
                                            line = getElementLine(field)
                                        )
                                    } else null
                                }
                            }

                            // 合并结果并按匹配度重新排序
                            val allSymbolResults = (methodResults + fieldResults)
                            val sortedResults = allSymbolResults
                                .sortedByDescending { matcher.matchingDegree(it.name) }
                                .distinctBy { "${it.name}:${it.path}:${it.line}" }
                            totalFound = sortedResults.size
                            results.addAll(sortedResults.drop(offset).take(maxResults))
                        }
                    }
                    
                    SearchIndexType.Actions -> {
                        // 动作搜索 - 简化实现
                        results.add(IndexSearchResult(
                            name = "Actions search",
                            type = "Info",
                            description = "Actions search requires UI context. Use IDEA's Search Everywhere (Shift+Shift)"
                        ))
                        totalFound = 1
                    }
                    
                    SearchIndexType.Text -> {
                        // 文本搜索 - 建议使用 FindInFiles 工具
                        results.add(IndexSearchResult(
                            name = "Text search",
                            type = "Info",
                            description = "For text content search, use CodeSearch tool which supports more options"
                        ))
                        totalFound = 1
                    }
                }
            }
        } catch (e: Exception) {
            return ToolResult.error("Search error: ${e.message}")
        }

        val sb = StringBuilder()
        sb.appendLine("## Index Search: `$query`")
        sb.appendLine()
        sb.appendLine("**Type:** $searchType | **Scope:** $searchScope")
        sb.appendLine()

        if (results.isEmpty()) {
            sb.appendLine("*No results found*")
        } else {
            sb.appendLine("| # | Type | Name | Path | Line |")
            sb.appendLine("|---|------|------|------|------|")
            results.forEachIndexed { index, item ->
                val lineInfo = item.line?.toString() ?: "-"
                val pathInfo = item.path ?: "-"
                sb.appendLine("| ${index + offset + 1} | ${item.type} | `${item.name}` | `$pathInfo` | $lineInfo |")
            }
        }

        sb.appendLine()
        sb.appendLine("---")
        sb.append("**Summary:** $totalFound results")
        if (offset + results.size < totalFound) {
            sb.append(" *(showing ${offset + 1}-${offset + results.size}, more available)*")
        }

        return sb.toString()
    }

    private fun getSymbolType(element: PsiElement?): String {
        if (element == null) return "Symbol"
        return when (element.javaClass.simpleName) {
            "KtClass", "PsiClass" -> "Class"
            "KtFunction", "PsiMethod" -> "Method"
            "KtProperty", "PsiField" -> "Field"
            "KtParameter" -> "Parameter"
            else -> "Symbol"
        }
    }

    private fun getElementDescription(element: PsiElement): String? {
        return when (element) {
            is PsiNamedElement -> element.name
            else -> null
        }
    }

    private fun getElementLine(element: PsiElement): Int? {
        val file = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(element.project)
            .getDocument(file) ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    /**
     * 根据 SearchIndexScope 创建对应的 GlobalSearchScope
     */
    private fun createSearchScope(scope: SearchIndexScope): GlobalSearchScope {
        return when (scope) {
            SearchIndexScope.Project -> GlobalSearchScope.projectScope(project)
            SearchIndexScope.All -> GlobalSearchScope.allScope(project)
            SearchIndexScope.ProductionFiles -> {
                // 仅生产代码（排除测试目录）
                val thisProject = project // 捕获非空的 project 引用
                GlobalSearchScope.projectScope(thisProject).let { projectScope ->
                    object : GlobalSearchScope(thisProject) {
                        override fun contains(file: VirtualFile): Boolean {
                            return projectScope.contains(file) && !TestSourcesFilter.isTestSources(file, thisProject)
                        }
                        override fun isSearchInModuleContent(aModule: com.intellij.openapi.module.Module) = true
                        override fun isSearchInLibraries() = false
                    }
                }
            }
            SearchIndexScope.TestFiles -> {
                // 仅测试代码
                val thisProject = project // 捕获非空的 project 引用
                GlobalSearchScope.projectScope(thisProject).let { projectScope ->
                    object : GlobalSearchScope(thisProject) {
                        override fun contains(file: VirtualFile): Boolean {
                            return projectScope.contains(file) && TestSourcesFilter.isTestSources(file, thisProject)
                        }
                        override fun isSearchInModuleContent(aModule: com.intellij.openapi.module.Module) = true
                        override fun isSearchInLibraries() = false
                    }
                }
            }
            SearchIndexScope.Scratches -> {
                // Scratches and Consoles
                object : GlobalSearchScope(project) {
                    override fun contains(file: VirtualFile): Boolean {
                        return ScratchUtil.isScratch(file)
                    }
                    override fun isSearchInModuleContent(aModule: com.intellij.openapi.module.Module) = false
                    override fun isSearchInLibraries() = false
                }
            }
        }
    }
}
