package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.plugin.mcp.ToolSchemaLoader
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.impl.DocumentMarkupModel
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

    fun getInputSchema(): Map<String, Any> = ToolSchemaLoader.getSchema("FileProblems")

    suspend fun execute(arguments: Map<String, Any>): Any {
        val filePath = arguments["filePath"] as? String
            ?: return ToolResult.error("Missing required parameter: filePath")
        val includeWarnings = arguments["includeWarnings"] as? Boolean ?: true
        val includeWeakWarnings = arguments["includeWeakWarnings"] as? Boolean ?: false
        val maxProblems = ((arguments["maxProblems"] as? Number)?.toInt() ?: 50).coerceAtLeast(1)

        val projectPath = project.basePath
            ?: return ToolResult.error("Cannot get project path")

        val absolutePath = File(projectPath, filePath).canonicalPath

        if (!absolutePath.startsWith(File(projectPath).canonicalPath)) {
            return ToolResult.error("File path must be within project directory")
        }

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)
            ?: return ToolResult.error("File not found: $filePath")

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

                // 获取高亮信息 - 使用公共 API
                val markupModel = DocumentMarkupModel.forDocument(document, project, false)
                    ?: return@run
                val highlighters = markupModel.allHighlighters

                for (highlighter in highlighters) {
                    if (problems.size >= maxProblems) break
                    if (!highlighter.isValid) continue
                    val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: continue
                    val severity = when {
                        info.severity == HighlightSeverity.ERROR -> {
                            errorCount++
                            ProblemSeverity.ERROR
                        }
                        info.severity == HighlightSeverity.WARNING -> {
                            if (!includeWarnings) continue
                            warningCount++
                            ProblemSeverity.WARNING
                        }
                        info.severity == HighlightSeverity.WEAK_WARNING -> {
                            if (!includeWeakWarnings) continue
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

                    // Note: quickFixActionRanges is deprecated, use findRegisteredQuickFix() instead
                    // However, that requires IntentionAction context which is not available here
                    // For now, we try to get quick fix hint via reflection to avoid deprecation warning
                    val quickFixHint = try {
                        @Suppress("DEPRECATION", "removal")
                        info.quickFixActionRanges?.firstOrNull()?.first?.action?.text
                    } catch (e: Exception) {
                        null
                    }

                    problems.add(FileProblem(
                        severity = severity,
                        message = info.description ?: info.toolTip ?: "Unknown issue",
                        line = startLine,
                        column = startColumn,
                        endLine = endLine,
                        endColumn = endColumn,
                        description = info.toolTip,
                        quickFixHint = quickFixHint
                    ))
                }
            }
        } catch (e: Exception) {
            return ToolResult.error("Analysis error: ${e.message}")
        }

        val sortedProblems = problems.sortedWith(
            compareBy({ it.severity.ordinal }, { it.line }, { it.column })
        )

        val sb = StringBuilder()
        sb.appendLine("📄 File: $filePath")
        sb.appendLine()

        if (sortedProblems.isEmpty()) {
            sb.appendLine("✅ No issues found")
        } else {
            sortedProblems.forEach { problem ->
                val icon = when (problem.severity) {
                    ProblemSeverity.ERROR -> "❌"
                    ProblemSeverity.WARNING -> "⚠️"
                    ProblemSeverity.WEAK_WARNING -> "💡"
                    ProblemSeverity.INFO -> "ℹ️"
                }
                val location = "${problem.line}:${problem.column}"
                sb.appendLine("$icon [$location] ${problem.message}")
                problem.quickFixHint?.let {
                    sb.appendLine("   └─ Quick fix: $it")
                }
            }
        }

        sb.appendLine()
        val parts = mutableListOf<String>()
        if (errorCount > 0) parts.add("❌ $errorCount errors")
        if (warningCount > 0) parts.add("⚠️ $warningCount warnings")
        if (weakWarningCount > 0) parts.add("💡 $weakWarningCount suggestions")
        if (parts.isEmpty()) {
            sb.append("📊 No problems")
        } else {
            sb.append("📊 ${parts.joinToString(", ")}")
        }

        return sb.toString()
    }
}
