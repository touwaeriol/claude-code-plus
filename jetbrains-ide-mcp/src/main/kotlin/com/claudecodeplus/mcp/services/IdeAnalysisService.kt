package com.claudecodeplus.mcp.services

import com.claudecodeplus.mcp.models.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * IDE 分析服务 - 基于基础文件系统和文本分析
 * 
 * ⚠️ 重要说明：
 * - 语言检测：基于文件扩展名 (File.extension) - 不是 IntelliJ API
 * - 静态分析：基于文本模式匹配 - 不是 PSI 或 IntelliJ 检查工具
 * - 复杂度计算：基于关键字计数 - 不是真正的 AST 分析
 * 
 * 此实现提供基础功能，但不使用 IntelliJ Platform 的高级 API。
 * 要获得真正的 IDE 级别分析，需要使用 IntelliJNativeAnalysisService。
 */
class IdeAnalysisService(private val project: Project) {

    /**
     * 检查文件错误
     */
    fun checkFileErrors(filePath: String, level: String): Future<FileErrorCheckResult> {
        return CompletableFuture.supplyAsync {
            val file = File(filePath)
            if (!file.exists()) {
                return@supplyAsync FileErrorCheckResult(
                    filePath = filePath,
                    fileExists = false,
                    summary = ErrorSummary(
                        totalIssues = 1,
                        errorCount = 1,
                        warningCount = 0,
                        infoCount = 0,
                        categories = mapOf("System" to 1)
                    ),
                    issues = listOf(
                        CodeIssue(
                            severity = "ERROR",
                            message = "File not found: $filePath",
                            category = "System",
                            line = 0,
                            column = 0,
                            source = "IDE"
                        )
                    )
                )
            }

            // 尝试获取 PSI 文件
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            val psiFile = virtualFile?.let { PsiManager.getInstance(project).findFile(it) }

            val issues = mutableListOf<CodeIssue>()

            if (psiFile != null) {
                // 基础文件分析
                val fileText = psiFile.text
                val lines = fileText.lines()

                // 简单的静态检查
                lines.forEachIndexed { index, line ->
                    // 检查常见问题
                    if (line.contains("TODO") || line.contains("FIXME")) {
                        issues.add(
                            CodeIssue(
                                severity = "WARNING",
                                message = "Todo comment found",
                                category = "Code Quality",
                                line = index + 1,
                                column = line.indexOf("TODO").coerceAtLeast(line.indexOf("FIXME")).coerceAtLeast(1),
                                source = "IDE"
                            )
                        )
                    }

                    if (line.trim().isEmpty()) {
                        // 跳过空行
                    } else if (line.contains("System.out.println") || line.contains("console.log")) {
                        issues.add(
                            CodeIssue(
                                severity = "INFO",
                                message = "Debug output statement detected",
                                category = "Code Quality",
                                line = index + 1,
                                column = 1,
                                source = "IDE"
                            )
                        )
                    }
                }
            } else {
                // 无法解析为 PSI，进行文本分析
                val fileText = file.readText()
                if (fileText.contains("ERROR") || fileText.contains("Exception")) {
                    issues.add(
                        CodeIssue(
                            severity = "ERROR",
                            message = "Potential error pattern detected",
                            category = "Syntax",
                            line = 1,
                            column = 1,
                            source = "IDE"
                        )
                    )
                }
            }

            val errors = issues.filter { it.severity == "ERROR" }
            val warnings = issues.filter { it.severity == "WARNING" }

            FileErrorCheckResult(
                filePath = filePath,
                fileExists = true,
                fileSize = file.length(),
                lineCount = file.readText().lines().size,
                language = detectLanguage(filePath),
                summary = ErrorSummary(
                    totalIssues = issues.size,
                    errorCount = errors.size,
                    warningCount = warnings.size,
                    infoCount = issues.filter { it.severity == "INFO" }.size,
                    categories = issues.groupingBy { it.category }.eachCount()
                ),
                issues = issues
            )
        }
    }

    /**
     * 分析代码质量
     */
    fun analyzeCodeQuality(filePath: String, metrics: List<String>): Future<CodeQualityResult> {
        return CompletableFuture.supplyAsync {
            val file = File(filePath)
            if (!file.exists()) {
                throw RuntimeException("File not found: $filePath")
            }

            val fileText = file.readText()
            val lines = fileText.lines()
            val codeLines = lines.filter { it.trim().isNotEmpty() && !it.trim().startsWith("//") }

            // 基础指标计算
            val fileMetrics = FileMetrics(
                totalLines = lines.size,
                codeLines = codeLines.size,
                commentLines = lines.size - codeLines.size,
                blankLines = lines.count { it.trim().isEmpty() },
                fileSize = file.length(),
                characterCount = fileText.length
            )

            var complexityMetrics: ComplexityMetrics? = null
            if (metrics.contains("complexity")) {
                val methodCount = countMethods(fileText)
                val classCount = countClasses(fileText)
                val cyclomaticComplexity = calculateCyclomaticComplexity(fileText)

                complexityMetrics = ComplexityMetrics(
                    cyclomaticComplexity = cyclomaticComplexity,
                    complexityScore = if (methodCount > 0) cyclomaticComplexity.toDouble() / methodCount else 0.0,
                    methodCount = methodCount,
                    classCount = classCount,
                    averageMethodLength = if (methodCount > 0) codeLines.size.toDouble() / methodCount else 0.0,
                    maxMethodComplexity = cyclomaticComplexity / methodCount.coerceAtLeast(1)
                )
            }

            val overallScore = calculateQualityScore(fileMetrics, complexityMetrics)

            CodeQualityResult(
                filePath = filePath,
                language = detectLanguage(filePath),
                summary = QualitySummary(
                    overallScore = overallScore,
                    grade = getGradeFromScore(overallScore),
                    status = getStatusFromScore(overallScore),
                    riskLevel = getRiskLevelFromScore(overallScore)
                ),
                metrics = QualityMetrics(
                    fileMetrics = fileMetrics,
                    complexityMetrics = complexityMetrics
                )
            )
        }
    }

    /**
     * 验证语法
     */
    fun validateSyntax(filePath: String): Future<SyntaxValidationResult> {
        return CompletableFuture.supplyAsync {
            val file = File(filePath)
            if (!file.exists()) {
                return@supplyAsync SyntaxValidationResult(
                    filePath = filePath,
                    isValid = false,
                    language = "unknown",
                    summary = SyntaxSummary(
                        totalErrors = 1,
                        errorTypes = listOf("FILE_NOT_FOUND"),
                        severity = "critical",
                        parseSuccess = false
                    ),
                    errors = listOf(
                        SyntaxError(
                            message = "File not found",
                            line = 0,
                            column = 0,
                            errorCode = "FILE_NOT_FOUND",
                            errorType = "SYSTEM"
                        )
                    )
                )
            }

            val fileText = file.readText()
            val language = detectLanguage(filePath)
            val errors = mutableListOf<SyntaxError>()

            // 基础语法检查
            when (language.lowercase()) {
                "java", "kotlin" -> {
                    if (!hasMatchingBraces(fileText)) {
                        errors.add(SyntaxError(
                            message = "Mismatched braces",
                            line = 1,
                            column = 1,
                            errorCode = "MISMATCHED_BRACES",
                            errorType = "SYNTAX"
                        ))
                    }
                }
                "python" -> {
                    // Python specific checks
                    if (fileText.contains("IndentationError")) {
                        errors.add(SyntaxError(
                            message = "Indentation error detected",
                            line = 1,
                            column = 1,
                            errorCode = "INDENTATION_ERROR",
                            errorType = "SYNTAX"
                        ))
                    }
                }
            }

            SyntaxValidationResult(
                filePath = filePath,
                isValid = errors.isEmpty(),
                language = language,
                summary = SyntaxSummary(
                    totalErrors = errors.size,
                    errorTypes = errors.map { it.errorCode }.distinct(),
                    severity = when {
                        errors.isEmpty() -> "none"
                        errors.size < 3 -> "minor"
                        errors.size < 10 -> "major"
                        else -> "critical"
                    },
                    parseSuccess = errors.isEmpty()
                ),
                errors = errors
            )
        }
    }

    // 辅助方法
    /**
     * ⚠️ 简化的语言检测 - 基于文件扩展名
     * 
     * 这不是 IntelliJ API 实现！真正的 IntelliJ API 方式是：
     * val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
     * val language = psiFile.language.displayName
     */
    private fun detectLanguage(filePath: String): String {
        return when (File(filePath).extension.lowercase()) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "py" -> "Python"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "go" -> "Go"
            "rs" -> "Rust"
            else -> "Unknown"
        }
    }

    private fun countMethods(text: String): Int {
        return listOf("fun ", "def ", "function ", "func ").sumOf { keyword ->
            text.split(keyword).size - 1
        }
    }

    private fun countClasses(text: String): Int {
        return text.split("class ").size - 1
    }

    private fun calculateCyclomaticComplexity(text: String): Int {
        val complexityKeywords = listOf("if", "else", "while", "for", "case", "catch", "&&", "||", "?:")
        return complexityKeywords.sumOf { keyword ->
            text.split(keyword).size - 1
        } + 1
    }

    private fun hasMatchingBraces(text: String): Boolean {
        var braceCount = 0
        text.forEach { char ->
            when (char) {
                '{' -> braceCount++
                '}' -> braceCount--
            }
        }
        return braceCount == 0
    }

    private fun calculateQualityScore(fileMetrics: FileMetrics, complexityMetrics: ComplexityMetrics?): Double {
        var score = 10.0

        // 文件大小惩罚
        if (fileMetrics.totalLines > 500) score -= 1.0
        if (fileMetrics.totalLines > 1000) score -= 2.0

        // 复杂度惩罚
        complexityMetrics?.let { complexity ->
            if (complexity.complexityScore > 5.0) score -= 1.0
            if (complexity.complexityScore > 10.0) score -= 2.0
            if (complexity.averageMethodLength > 30.0) score -= 1.0
        }

        return score.coerceAtLeast(0.0).coerceAtMost(10.0)
    }

    private fun getGradeFromScore(score: Double): String = when {
        score >= 9.0 -> "A+"
        score >= 8.0 -> "A"
        score >= 7.0 -> "B+"
        score >= 6.0 -> "B"
        score >= 5.0 -> "C+"
        score >= 4.0 -> "C"
        else -> "D"
    }

    private fun getStatusFromScore(score: Double): String = when {
        score >= 8.0 -> "excellent"
        score >= 6.0 -> "good"
        score >= 4.0 -> "fair"
        else -> "poor"
    }

    private fun getRiskLevelFromScore(score: Double): String = when {
        score >= 7.0 -> "low"
        score >= 5.0 -> "medium"
        score >= 3.0 -> "high"
        else -> "critical"
    }
}