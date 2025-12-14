package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class SearchIndexType {
    All, Classes, Files, Symbols, Actions, Text
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

    fun getInputSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "搜索关键词"
            ),
            "searchType" to mapOf(
                "type" to "string",
                "enum" to listOf("All", "Classes", "Files", "Symbols", "Actions", "Text"),
                "description" to "搜索类型：All（全部）、Classes（类）、Files（文件）、Symbols（符号）、Actions（动作）、Text（文本）",
                "default" to "All"
            ),
            "maxResults" to mapOf(
                "type" to "integer",
                "description" to "最大结果数",
                "default" to 50,
                "minimum" to 1,
                "maximum" to 200
            ),
            "offset" to mapOf(
                "type" to "integer",
                "description" to "分页偏移量",
                "default" to 0,
                "minimum" to 0
            )
        ),
        "required" to listOf("query")
    )

    suspend fun execute(arguments: Map<String, Any>): Any {
        val query = arguments["query"] as? String
            ?: return ToolResult.error("缺少必需参数: query")
        val searchTypeStr = arguments["searchType"] as? String ?: "All"
        val maxResults = ((arguments["maxResults"] as? Number)?.toInt() ?: 50).coerceIn(1, 200)
        val offset = ((arguments["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

        val searchType = try {
            SearchIndexType.valueOf(searchTypeStr)
        } catch (e: Exception) {
            return ToolResult.error("无效的搜索类型: $searchTypeStr")
        }

        if (query.isBlank()) {
            return ToolResult.error("搜索关键词不能为空")
        }

        val results = mutableListOf<IndexSearchResult>()
        var totalFound = 0

        try {
            ReadAction.run<Exception> {
                val projectScope = GlobalSearchScope.projectScope(project)
                val queryLower = query.lowercase()

                when (searchType) {
                    SearchIndexType.Files, SearchIndexType.All -> {
                        // 文件搜索
                        val allFileNames = FilenameIndex.getAllFilenames(project)
                        val matchingFiles = allFileNames
                            .filter { it.lowercase().contains(queryLower) }
                            .flatMap { fileName ->
                                FilenameIndex.getVirtualFilesByName(fileName, projectScope).map { file ->
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
                        // 类搜索 - 使用 GotoClassModel2
                        val model = GotoClassModel2(project)
                        val names = model.getNames(false) // false = 不包含库中的类
                        val matchingNames = names.filter { it.lowercase().contains(queryLower) }
                        
                        totalFound = matchingNames.size
                        matchingNames.drop(offset).take(maxResults).forEach { name ->
                            val elements = model.getElementsByName(name, false, name)
                            (elements.firstOrNull() as? PsiElement)?.let { psiElement ->
                                val file = psiElement.containingFile?.virtualFile

                                results.add(IndexSearchResult(
                                    name = name,
                                    path = file?.path?.removePrefix(project.basePath ?: "")?.removePrefix("/"),
                                    type = "Class",
                                    description = getElementDescription(psiElement)
                                ))
                            }
                        }
                    }

                    SearchIndexType.Symbols -> {
                        // 符号搜索 - 使用 GotoSymbolModel2
                        val model = GotoSymbolModel2(project)
                        val names = model.getNames(false)
                        val matchingNames = names.filter { it.lowercase().contains(queryLower) }

                        totalFound = matchingNames.size
                        matchingNames.drop(offset).take(maxResults).forEach { name ->
                            val elements = model.getElementsByName(name, false, name)
                            (elements.firstOrNull() as? PsiElement)?.let { psiElement ->
                                val file = psiElement.containingFile?.virtualFile

                                results.add(IndexSearchResult(
                                    name = name,
                                    path = file?.path?.removePrefix(project.basePath ?: "")?.removePrefix("/"),
                                    type = getSymbolType(psiElement),
                                    description = getElementDescription(psiElement),
                                    line = getElementLine(psiElement)
                                ))
                            }
                        }
                    }
                    
                    SearchIndexType.Actions -> {
                        // 动作搜索 - 简化实现
                        results.add(IndexSearchResult(
                            name = "Actions search",
                            type = "Info",
                            description = "动作搜索功能需要 UI 上下文，建议使用 IDEA 的 Search Everywhere (Shift+Shift)"
                        ))
                        totalFound = 1
                    }
                    
                    SearchIndexType.Text -> {
                        // 文本搜索 - 建议使用 FindInFiles 工具
                        results.add(IndexSearchResult(
                            name = "Text search",
                            type = "Info",
                            description = "文本内容搜索请使用 FindInFiles 工具，支持更多搜索选项"
                        ))
                        totalFound = 1
                    }
                }
            }
        } catch (e: Exception) {
            return ToolResult.error("搜索时出错: ${e.message}")
        }

        val result = FileIndexSearchResult(
            query = query,
            searchType = searchType,
            results = results,
            totalFound = totalFound,
            hasMore = offset + results.size < totalFound,
            offset = offset,
            limit = maxResults
        )

        return Json.encodeToString(result)
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
}
