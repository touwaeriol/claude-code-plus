package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class ProblemSeverity {
    ERROR, WARNING, WEAK_WARNING, INFO
}

@Serializable
data class FileProblem(
    val severity: ProblemSeverity,
    val message: String,
    val line: Int,          // 1-based
    val column: Int,        // 1-based
    val endLine: Int,       // 1-based
    val endColumn: Int,     // 1-based
    val description: String? = null,
    val quickFixHint: String? = null
)

@Serializable
data class FileProblemsResult(
    val filePath: String,
    val problems: List<FileProblem>,
    val errorCount: Int,
    val warningCount: Int,
    val weakWarningCount: Int,
    val hasErrors: Boolean
)

/**
 * 文件静态错误工具
 * 
 * 获取指定文件的静态分析结果，包括编译错误、警告和代码检查问题
 */
class FileProblemsTool(private val project: Project) {

    fun getInputSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "filePath" to mapOf(
                "type" to "string",
                "description" to "相对于项目根目录的文件路径"
            ),
            "includeWarnings" to mapOf(
                "type" to "boolean",
                "description" to "是否包含警告",
                "default" to true
            ),
            "includeWeakWarnings" to mapOf(
                "type" to "boolean",
                "description" to "是否包含弱警告/建议",
                "default" to false
            )
        ),
        "required" to listOf("filePath")
    )

    suspend fun execute(arguments: Map<String, Any>): Any {
        val filePath = arguments["filePath"] as? String
            ?: return ToolResult.error("缺少必需参数: filePath")
        val includeWarnings = arguments["includeWarnings"] as? Boolean ?: true
        val includeWeakWarnings = arguments["includeWeakWarnings"] as? Boolean ?: false

        val projectPath = project.basePath
            ?: return ToolResult.error("无法获取项目路径")

        val absolutePath = File(projectPath, filePath).canonicalPath
        
        // 安全检查
        if (!absolutePath.startsWith(File(projectPath).canonicalPath)) {
            return ToolResult.error("文件路径必须在项目目录内")
        }

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)
            ?: return ToolResult.error("文件不存在: $filePath")

        val problems = mutableListOf<FileProblem>()
        var errorCount = 0
        var warningCount = 0
        var weakWarningCount = 0

        try {
            ReadAction.run<Exception> {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                    ?: return@run

                val document = FileDocumentManager.getInstance().getDocument(virtualFile) 
                    ?: return@run

                // 获取高亮信息
                val highlights = DaemonCodeAnalyzerImpl.getHighlights(
                    document, 
                    null, // 不过滤范围
                    project
                )

                for (info in highlights) {
                    val severity = when {
                        info.severity == HighlightSeverity.ERROR -> {
                            errorCount++
                            ProblemSeverity.ERROR
                        }
                        info.severity == HighlightSeverity.WARNING -> {
                            if (!includeWarnings) return@run
                            warningCount++
                            ProblemSeverity.WARNING
                        }
                        info.severity == HighlightSeverity.WEAK_WARNING -> {
                            if (!includeWeakWarnings) return@run
                            weakWarningCount++
                            ProblemSeverity.WEAK_WARNING
                        }
                        info.severity.myVal >= HighlightSeverity.INFORMATION.myVal -> {
                            ProblemSeverity.INFO
                        }
                        else -> continue
                    }

                    // 计算行列号
                    val startLine = document.getLineNumber(info.startOffset) + 1
                    val startColumn = info.startOffset - document.getLineStartOffset(startLine - 1) + 1
                    val endLine = document.getLineNumber(info.endOffset) + 1
                    val endColumn = info.endOffset - document.getLineStartOffset(endLine - 1) + 1

                    problems.add(FileProblem(
                        severity = severity,
                        message = info.description ?: info.toolTip ?: "Unknown issue",
                        line = startLine,
                        column = startColumn,
                        endLine = endLine,
                        endColumn = endColumn,
                        description = info.toolTip,
                        quickFixHint = info.quickFixActionRanges?.firstOrNull()?.first?.action?.text
                    ))
                }
            }
        } catch (e: Exception) {
            return ToolResult.error("分析文件时出错: ${e.message}")
        }

        val result = FileProblemsResult(
            filePath = filePath,
            problems = problems.sortedWith(
                compareBy({ it.severity.ordinal }, { it.line }, { it.column })
            ),
            errorCount = errorCount,
            warningCount = warningCount,
            weakWarningCount = weakWarningCount,
            hasErrors = errorCount > 0
        )

        return Json.encodeToString(result)
    }
}
