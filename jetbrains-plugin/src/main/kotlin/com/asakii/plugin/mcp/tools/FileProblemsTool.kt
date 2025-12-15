package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

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
    val description: String? = null
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
 * 使用 InspectionEngine API 直接运行检查，无需打开文件
 * 参考: https://plugins.jetbrains.com/docs/intellij/code-inspections.html
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
            // 使用 InspectionEngine 直接运行检查，无需打开文件
            val problemDescriptors = runInspectionsOnFile(virtualFile, includeWarnings, includeWeakWarnings)

            logger.debug { "📊 Found ${problemDescriptors.size} problems for $filePath" }

            for (descriptor in problemDescriptors) {
                if (problems.size >= maxProblems) break

                val severity = when (descriptor.highlightType) {
                    ProblemHighlightType.ERROR,
                    ProblemHighlightType.GENERIC_ERROR,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> {
                        errorCount++
                        ProblemSeverity.ERROR
                    }
                    ProblemHighlightType.WARNING -> {
                        if (!includeWarnings) continue
                        warningCount++
                        ProblemSeverity.WARNING
                    }
                    ProblemHighlightType.WEAK_WARNING,
                    ProblemHighlightType.INFORMATION -> {
                        if (!includeWeakWarnings) continue
                        weakWarningCount++
                        ProblemSeverity.WEAK_WARNING
                    }
                    else -> {
                        if (!includeWeakWarnings) continue
                        ProblemSeverity.INFO
                    }
                }

                // 获取位置信息
                val psiElement = descriptor.psiElement
                val textRange = descriptor.textRangeInElement ?: psiElement?.textRange
                val document = psiElement?.containingFile?.viewProvider?.document

                val (line, column, endLine, endColumn) = if (document != null && textRange != null) {
                    val startLine = document.getLineNumber(textRange.startOffset) + 1
                    val startCol = textRange.startOffset - document.getLineStartOffset(startLine - 1) + 1
                    val endL = document.getLineNumber(textRange.endOffset) + 1
                    val endCol = textRange.endOffset - document.getLineStartOffset(endL - 1) + 1
                    listOf(startLine, startCol, endL, endCol)
                } else {
                    listOf(1, 1, 1, 1)
                }

                problems.add(FileProblem(
                    severity = severity,
                    message = descriptor.descriptionTemplate ?: "Unknown issue",
                    line = line,
                    column = column,
                    endLine = endLine,
                    endColumn = endColumn,
                    description = descriptor.toString()
                ))
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Analysis error for $filePath" }
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

    /**
     * 使用 InspectionEngine 直接在文件上运行检查
     * 无需打开文件，直接通过 PsiFile 运行
     */
    private fun runInspectionsOnFile(
        virtualFile: com.intellij.openapi.vfs.VirtualFile,
        includeWarnings: Boolean,
        includeWeakWarnings: Boolean
    ): List<ProblemDescriptor> {
        return try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<List<ProblemDescriptor>, Exception>(
                {
                    ReadAction.compute<List<ProblemDescriptor>, Exception> {
                        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                            ?: return@compute emptyList()

                        runInspectionsOnPsiFile(psiFile, includeWarnings, includeWeakWarnings)
                    }
                },
                "Analyzing ${virtualFile.name}",
                true,
                project
            ) ?: emptyList()
        } catch (e: Exception) {
            logger.error(e) { "❌ Error running inspections" }
            emptyList()
        }
    }

    /**
     * 在 PsiFile 上运行所有启用的检查
     */
    private fun runInspectionsOnPsiFile(
        psiFile: PsiFile,
        includeWarnings: Boolean,
        includeWeakWarnings: Boolean
    ): List<ProblemDescriptor> {
        val inspectionManager = InspectionManager.getInstance(project)
        val context = inspectionManager.createNewGlobalContext()

        // 获取当前项目的检查配置
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile as? InspectionProfileImpl
            ?: return emptyList()

        val problems = mutableListOf<ProblemDescriptor>()

        // 获取所有启用的检查工具 - getAllEnabledInspectionTools 返回 List<Tools>
        val toolsList = profile.getAllEnabledInspectionTools(project)

        for (tools in toolsList) {
            // 从 Tools 中获取默认的 InspectionToolWrapper
            val toolWrapper = tools.tool

            // 只运行 LocalInspectionTool（文件级别的检查）
            if (toolWrapper !is LocalInspectionToolWrapper) continue

            // 根据严重级别过滤 - 使用 tools 获取默认级别
            val defaultLevel = tools.defaultState.level
            val isWarning = defaultLevel == HighlightDisplayLevel.WARNING
            val isWeakWarning = defaultLevel == HighlightDisplayLevel.WEAK_WARNING ||
                               defaultLevel == HighlightDisplayLevel.DO_NOT_SHOW

            if (!includeWarnings && isWarning) continue
            if (!includeWeakWarnings && isWeakWarning) continue

            try {
                val descriptors = InspectionEngine.runInspectionOnFile(psiFile, toolWrapper, context)
                problems.addAll(descriptors)
            } catch (e: Exception) {
                // 忽略单个检查的错误，继续运行其他检查
                logger.debug { "⚠️ Inspection ${toolWrapper.shortName} failed: ${e.message}" }
            }
        }

        return problems
    }
}
