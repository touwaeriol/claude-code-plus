package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.scratch.ScratchUtil
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

        val results = mutableListOf<IndexSearchResult>()
        var totalFound = 0

        try {
            ReadAction.run<Exception> {
                val baseScope = createSearchScope(searchScope)
                val queryLower = query.lowercase()

                // 是否包含库（用于 Classes/Symbols 搜索）
                val includeLibraries = searchScope == SearchIndexScope.All

                when (searchType) {
                    SearchIndexType.Files, SearchIndexType.All -> {
                        // 文件搜索
                        val allFileNames = FilenameIndex.getAllFilenames(project)
                        val matchingFiles = allFileNames
                            .filter { it.lowercase().contains(queryLower) }
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
                        // 类搜索 - 使用 PsiShortNamesCache
                        val cache = PsiShortNamesCache.getInstance(project)
                        val allClassNames = cache.allClassNames
                        val matchingNames = allClassNames.filter { it.lowercase().contains(queryLower) }

                        // 过滤并收集结果
                        val searchScope = if (includeLibraries) GlobalSearchScope.allScope(project) else baseScope
                        val filteredResults = matchingNames.flatMap { name ->
                            cache.getClassesByName(name, searchScope).mapNotNull { psiClass ->
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

                    SearchIndexType.Symbols -> {
                        // 符号搜索 - 使用 PsiShortNamesCache 搜索方法和字段
                        val cache = PsiShortNamesCache.getInstance(project)
                        val symbolSearchScope = if (includeLibraries) GlobalSearchScope.allScope(project) else baseScope

                        // 搜索方法
                        val allMethodNames = cache.allMethodNames
                        val matchingMethodNames = allMethodNames.filter { it.lowercase().contains(queryLower) }

                        val methodResults = matchingMethodNames.flatMap { name ->
                            cache.getMethodsByName(name, symbolSearchScope).mapNotNull { method ->
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

                        // 搜索字段
                        val allFieldNames = cache.allFieldNames
                        val matchingFieldNames = allFieldNames.filter { it.lowercase().contains(queryLower) }

                        val fieldResults = matchingFieldNames.flatMap { name ->
                            cache.getFieldsByName(name, symbolSearchScope).mapNotNull { field ->
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

                        val filteredResults = (methodResults + fieldResults).distinctBy { "${it.name}:${it.path}:${it.line}" }
                        totalFound = filteredResults.size
                        results.addAll(filteredResults.drop(offset).take(maxResults))
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
        sb.appendLine("🔍 Search: \"$query\" (type: $searchType, scope: $searchScope)")
        sb.appendLine()

        if (results.isEmpty()) {
            sb.appendLine("No results found")
        } else {
            results.forEachIndexed { index, item ->
                val icon = when (item.type) {
                    "File" -> "📄"
                    "Class" -> "📦"
                    "Method" -> "🔧"
                    "Field" -> "📌"
                    "Symbol" -> "🔹"
                    else -> "•"
                }
                val lineInfo = item.line?.let { ":$it" } ?: ""
                val pathInfo = item.path?.let { " → $it$lineInfo" } ?: ""

                sb.appendLine("${index + offset + 1}. $icon ${item.name}$pathInfo")
                item.description?.let { desc ->
                    if (desc != item.name) sb.appendLine("   └─ $desc")
                }
            }
        }

        sb.appendLine()
        sb.append("📊 Found $totalFound results")
        if (offset + results.size < totalFound) {
            sb.append(" (showing ${offset + 1}-${offset + results.size}, more available)")
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
