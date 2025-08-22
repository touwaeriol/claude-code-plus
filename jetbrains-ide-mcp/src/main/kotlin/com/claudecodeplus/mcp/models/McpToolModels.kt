package com.claudecodeplus.mcp.models

import kotlinx.serialization.Serializable

/**
 * MCP 工具请求和响应的数据模型定义
 * 定义了所有 MCP 工具的标准参数格式和返回格式
 */

// ================================
// 工具请求参数模型
// ================================

/**
 * check_file_errors 工具请求参数
 */
@Serializable
data class CheckFileErrorsRequest(
    val filePath: String,
    val checkLevel: String = "all", // "error", "warning", "all"
    val includeInspections: Boolean = true,
    val maxErrors: Int = 100,
    val timeout: Long = 30000 // 30秒超时
)

/**
 * analyze_code_quality 工具请求参数
 */
@Serializable
data class AnalyzeCodeQualityRequest(
    val filePath: String,
    val metrics: List<String> = listOf("complexity", "duplicates", "maintainability"),
    val includeDetails: Boolean = true,
    val threshold: CodeQualityThreshold = CodeQualityThreshold()
)

/**
 * 代码质量阈值配置
 */
@Serializable
data class CodeQualityThreshold(
    val maxComplexity: Int = 10,
    val maxMethodLines: Int = 50,
    val maxDuplicatePercentage: Int = 20,
    val minMaintainabilityIndex: Int = 60
)

/**
 * validate_syntax 工具请求参数
 */
@Serializable
data class ValidateSyntaxRequest(
    val filePath: String,
    val strict: Boolean = false, // 严格模式会检查更多语法规则
    val includeWarnings: Boolean = true
)

/**
 * get_inspection_results 工具请求参数 (新增)
 */
@Serializable
data class GetInspectionResultsRequest(
    val filePath: String,
    val inspectionNames: List<String> = emptyList(), // 空列表表示所有检查
    val severity: String = "all", // "error", "warning", "info", "all"
    val includeDescription: Boolean = true
)

/**
 * batch_analyze_files 工具请求参数 (批量分析)
 */
@Serializable
data class BatchAnalyzeFilesRequest(
    val filePaths: List<String>,
    val operations: List<String> = listOf("errors", "quality"), // "errors", "quality", "syntax"
    val parallel: Boolean = true,
    val maxConcurrency: Int = 5
)

// ================================
// 工具响应结果模型
// ================================

/**
 * 标准 MCP 工具响应格式
 */
@Serializable
data class McpToolResponse<T>(
    val tool: String,
    val success: Boolean,
    val result: T? = null,
    val error: McpError? = null,
    val metadata: McpMetadata = McpMetadata()
)

/**
 * MCP 错误信息
 */
@Serializable
data class McpError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

/**
 * MCP 响应元数据
 */
@Serializable
data class McpMetadata(
    val timestamp: Long = System.currentTimeMillis(),
    val executionTime: Long = 0,
    val version: String = "1.0.0"
)

/**
 * 文件错误检查结果
 */
@Serializable
data class FileErrorCheckResult(
    val filePath: String,
    val fileExists: Boolean = true,
    val fileSize: Long = 0,
    val lineCount: Int = 0,
    val language: String = "",
    val summary: ErrorSummary,
    val issues: List<CodeIssue>,
    val inspectionResults: List<InspectionResult> = emptyList()
)

/**
 * 错误汇总
 */
@Serializable
data class ErrorSummary(
    val totalIssues: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val categories: Map<String, Int> = emptyMap()
)

/**
 * 代码问题
 */
@Serializable
data class CodeIssue(
    val severity: String, // "ERROR", "WARNING", "INFO"
    val message: String,
    val description: String = "",
    val category: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column,
    val quickFixes: List<String> = emptyList(),
    val ruleId: String = "",
    val source: String = "IDE" // "IDE", "INSPECTION", "SYNTAX"
)

/**
 * 检查结果
 */
@Serializable
data class InspectionResult(
    val inspectionName: String,
    val displayName: String,
    val enabled: Boolean,
    val severity: String,
    val problemCount: Int,
    val problems: List<InspectionProblem> = emptyList()
)

/**
 * 检查问题
 */
@Serializable
data class InspectionProblem(
    val message: String,
    val line: Int,
    val column: Int,
    val highlightType: String,
    val fixes: List<String> = emptyList()
)

/**
 * 代码质量分析结果
 */
@Serializable
data class CodeQualityResult(
    val filePath: String,
    val language: String = "",
    val summary: QualitySummary,
    val metrics: QualityMetrics,
    val recommendations: List<QualityRecommendation> = emptyList()
)

/**
 * 质量汇总
 */
@Serializable
data class QualitySummary(
    val overallScore: Double, // 0-10 总体评分
    val grade: String, // A+, A, B+, B, C+, C, D, F
    val status: String, // "excellent", "good", "fair", "poor"
    val riskLevel: String // "low", "medium", "high", "critical"
)

/**
 * 质量指标
 */
@Serializable
data class QualityMetrics(
    val fileMetrics: FileMetrics,
    val complexityMetrics: ComplexityMetrics? = null,
    val duplicateMetrics: DuplicateMetrics? = null,
    val maintainabilityMetrics: MaintainabilityMetrics? = null,
    val customMetrics: Map<String, String> = emptyMap()
)

/**
 * 文件指标
 */
@Serializable
data class FileMetrics(
    val totalLines: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val fileSize: Long,
    val characterCount: Int
)

/**
 * 复杂度指标
 */
@Serializable
data class ComplexityMetrics(
    val cyclomaticComplexity: Int,
    val complexityScore: Double,
    val methodCount: Int,
    val classCount: Int,
    val averageMethodLength: Double,
    val maxMethodComplexity: Int,
    val complexMethods: List<String> = emptyList()
)

/**
 * 重复代码指标
 */
@Serializable
data class DuplicateMetrics(
    val duplicateLines: Int,
    val duplicatePercentage: Double,
    val duplicateBlocks: Int,
    val duplicateTokens: Int,
    val duplicatePatterns: List<String> = emptyList()
)

/**
 * 可维护性指标
 */
@Serializable
data class MaintainabilityMetrics(
    val maintainabilityIndex: Int, // 0-100
    val commentRatio: Double,
    val averageLineLength: Double,
    val codeSmells: List<String>,
    val codeSmellCount: Int,
    val technicalDebt: String = "", // 估算的技术债务时间
    val refactoringPriority: String = "low" // "low", "medium", "high"
)

/**
 * 质量改进建议
 */
@Serializable
data class QualityRecommendation(
    val type: String, // "refactor", "optimize", "document", "test"
    val priority: String, // "low", "medium", "high", "critical"
    val category: String,
    val message: String,
    val location: CodeLocation? = null,
    val estimatedEffort: String = "", // "5m", "30m", "2h", "1d"
    val impact: String = "medium" // "low", "medium", "high"
)

/**
 * 代码位置
 */
@Serializable
data class CodeLocation(
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column,
    val method: String = "",
    val class_: String = ""
)

/**
 * 语法验证结果
 */
@Serializable
data class SyntaxValidationResult(
    val filePath: String,
    val isValid: Boolean,
    val language: String = "",
    val summary: SyntaxSummary,
    val errors: List<SyntaxError>,
    val parseInfo: ParseInfo? = null
)

/**
 * 语法汇总
 */
@Serializable
data class SyntaxSummary(
    val totalErrors: Int,
    val errorTypes: List<String>,
    val severity: String, // "none", "minor", "major", "critical"
    val parseSuccess: Boolean
)

/**
 * 语法错误
 */
@Serializable
data class SyntaxError(
    val message: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column,
    val errorCode: String,
    val errorType: String = "SYNTAX", // "SYNTAX", "PARSE", "SEMANTIC"
    val suggestion: String = "",
    val context: String = "" // 错误上下文代码片段
)

/**
 * 解析信息
 */
@Serializable
data class ParseInfo(
    val parseTime: Long, // 解析耗时(ms)
    val astNodeCount: Int,
    val maxDepth: Int,
    val warnings: List<String> = emptyList()
)

/**
 * 批量分析结果
 */
@Serializable
data class BatchAnalysisResult(
    val totalFiles: Int,
    val successfulFiles: Int,
    val failedFiles: Int,
    val executionTime: Long,
    val results: List<SingleFileResult>,
    val summary: BatchSummary
)

/**
 * 单文件结果
 */
@Serializable
data class SingleFileResult(
    val filePath: String,
    val success: Boolean,
    val operations: Map<String, String>, // 各种操作的结果
    val error: String? = null
)

/**
 * 批量汇总
 */
@Serializable
data class BatchSummary(
    val totalErrors: Int,
    val totalWarnings: Int,
    val averageQualityScore: Double,
    val riskFiles: List<String>, // 高风险文件列表
    val recommendations: List<String>
)

// ================================
// MCP 工具定义模型
// ================================

/**
 * MCP 工具定义
 */
@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String,
    val parameters: McpToolParameters,
    val examples: List<McpToolExample> = emptyList()
)

/**
 * 工具参数定义
 */
@Serializable
data class McpToolParameters(
    val type: String = "object",
    val properties: Map<String, ParameterProperty>,
    val required: List<String> = emptyList()
)

/**
 * 参数属性
 */
@Serializable
data class ParameterProperty(
    val type: String,
    val description: String,
    val default: String? = null,
    val enum: List<String>? = null,
    val minimum: Int? = null,
    val maximum: Int? = null
)

/**
 * 工具使用示例
 */
@Serializable
data class McpToolExample(
    val description: String,
    val request: Map<String, String>,
    val expectedResponse: String
)