package com.claudecodeplus.mcp

import com.claudecodeplus.mcp.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * MCP 工具参数格式演示
 * 展示所有 MCP 工具的标准化参数格式和响应格式
 */
object ParameterFormatDemo {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("📋 IntelliJ IDE MCP 工具参数格式规范")
        println("=".repeat(60))
        
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        
        // 1. 文件错误检查工具
        demonstrateCheckFileErrors(json)
        
        // 2. 代码质量分析工具
        demonstrateCodeQuality(json)
        
        // 3. 语法验证工具
        demonstrateSyntaxValidation(json)
        
        // 4. 检查结果获取工具
        demonstrateInspectionResults(json)
        
        // 5. 批量分析工具
        demonstrateBatchAnalysis(json)
        
        println("\n" + "=".repeat(60))
        println("✅ 所有 MCP 工具参数格式演示完成！")
        println("💡 这些格式定义了与 IntelliJ IDEA API 交互的标准接口")
    }
    
    private fun demonstrateCheckFileErrors(json: Json) {
        println("\n🔍 1. check_file_errors - 文件错误检查工具")
        println("-".repeat(40))
        
        val request = CheckFileErrorsRequest(
            filePath = "/project/src/main/kotlin/com/example/MyClass.kt",
            checkLevel = "all",
            includeInspections = true,
            maxErrors = 50,
            timeout = 30000
        )
        
        println("📤 请求参数格式:")
        println(json.encodeToString(request))
        
        val response = FileErrorCheckResult(
            filePath = request.filePath,
            fileExists = true,
            fileSize = 2048,
            lineCount = 45,
            language = "Kotlin",
            summary = ErrorSummary(
                totalIssues = 3,
                errorCount = 1,
                warningCount = 2,
                infoCount = 0,
                categories = mapOf(
                    "Syntax" to 1,
                    "Unused" to 1,
                    "Nullability" to 1
                )
            ),
            issues = listOf(
                CodeIssue(
                    severity = "ERROR",
                    message = "Unresolved reference: unknownFunction",
                    description = "Cannot resolve symbol 'unknownFunction'",
                    category = "Syntax",
                    line = 12,
                    column = 5,
                    source = "IDE",
                    quickFixes = listOf("Import function", "Create function")
                ),
                CodeIssue(
                    severity = "WARNING", 
                    message = "Unused variable 'temp'",
                    description = "Variable 'temp' is never used",
                    category = "Unused",
                    line = 8,
                    column = 9,
                    source = "IDE"
                )
            )
        )
        
        println("\n📥 响应格式示例:")
        println(json.encodeToString(response))
        
        println("\n💡 调用方式:")
        println("POST /tools/check_file_errors")
        println("Content-Type: application/json")
        println("支持的 checkLevel: error, warning, all")
    }
    
    private fun demonstrateCodeQuality(json: Json) {
        println("\n📊 2. analyze_code_quality - 代码质量分析工具")
        println("-".repeat(40))
        
        val request = AnalyzeCodeQualityRequest(
            filePath = "/project/src/main/kotlin/com/example/ComplexClass.kt",
            metrics = listOf("complexity", "duplicates", "maintainability"),
            includeDetails = true,
            threshold = CodeQualityThreshold(
                maxComplexity = 8,
                maxMethodLines = 40,
                maxDuplicatePercentage = 15,
                minMaintainabilityIndex = 65
            )
        )
        
        println("📤 请求参数格式:")
        println(json.encodeToString(request))
        
        val response = CodeQualityResult(
            filePath = request.filePath,
            language = "Kotlin",
            summary = QualitySummary(
                overallScore = 7.2,
                grade = "B+",
                status = "good",
                riskLevel = "low"
            ),
            metrics = QualityMetrics(
                fileMetrics = FileMetrics(
                    totalLines = 156,
                    codeLines = 120,
                    commentLines = 25,
                    blankLines = 11,
                    fileSize = 4096,
                    characterCount = 3892
                ),
                complexityMetrics = ComplexityMetrics(
                    cyclomaticComplexity = 12,
                    complexityScore = 6.8,
                    methodCount = 8,
                    classCount = 2,
                    averageMethodLength = 15.0,
                    maxMethodComplexity = 8,
                    complexMethods = listOf("processData", "validateInput")
                ),
                maintainabilityMetrics = MaintainabilityMetrics(
                    maintainabilityIndex = 72,
                    commentRatio = 16.0,
                    averageLineLength = 32.4,
                    codeSmells = listOf("LongMethod", "ComplexCondition"),
                    codeSmellCount = 2,
                    technicalDebt = "2h",
                    refactoringPriority = "medium"
                )
            ),
            recommendations = listOf(
                QualityRecommendation(
                    type = "refactor",
                    priority = "medium",
                    category = "complexity",
                    message = "Consider splitting the 'processData' method to reduce complexity",
                    location = CodeLocation(line = 25, column = 5, method = "processData", class_ = "ComplexClass"),
                    estimatedEffort = "30m",
                    impact = "high"
                )
            )
        )
        
        println("\n📥 响应格式示例:")
        println(json.encodeToString(response))
        
        println("\n💡 调用方式:")
        println("POST /tools/analyze_code_quality")
        println("支持的 metrics: complexity, duplicates, maintainability")
    }
    
    private fun demonstrateSyntaxValidation(json: Json) {
        println("\n🔍 3. validate_syntax - 语法验证工具")
        println("-".repeat(40))
        
        val request = ValidateSyntaxRequest(
            filePath = "/project/src/main/kotlin/com/example/TestClass.kt",
            strict = false,
            includeWarnings = true
        )
        
        println("📤 请求参数格式:")
        println(json.encodeToString(request))
        
        val response = SyntaxValidationResult(
            filePath = request.filePath,
            isValid = false,
            language = "Kotlin",
            summary = SyntaxSummary(
                totalErrors = 2,
                errorTypes = listOf("MISSING_SEMICOLON", "UNEXPECTED_TOKEN"),
                severity = "minor",
                parseSuccess = false
            ),
            errors = listOf(
                SyntaxError(
                    message = "Expecting ';'",
                    line = 15,
                    column = 23,
                    errorCode = "MISSING_SEMICOLON",
                    errorType = "SYNTAX",
                    suggestion = "Add semicolon at the end of statement",
                    context = "val result = processData()"
                ),
                SyntaxError(
                    message = "Unexpected token '}'",
                    line = 20,
                    column = 1,
                    errorCode = "UNEXPECTED_TOKEN",
                    errorType = "SYNTAX",
                    suggestion = "Check for missing opening brace",
                    context = "}"
                )
            ),
            parseInfo = ParseInfo(
                parseTime = 45,
                astNodeCount = 156,
                maxDepth = 8,
                warnings = listOf("Deprecated syntax used")
            )
        )
        
        println("\n📥 响应格式示例:")
        println(json.encodeToString(response))
        
        println("\n💡 调用方式:")
        println("POST /tools/validate_syntax")
        println("strict=true 启用更严格的语法检查")
    }
    
    private fun demonstrateInspectionResults(json: Json) {
        println("\n🔍 4. get_inspection_results - 检查结果获取工具")  
        println("-".repeat(40))
        
        val request = GetInspectionResultsRequest(
            filePath = "/project/src/main/kotlin/com/example/MyClass.kt",
            inspectionNames = listOf("UnusedDeclaration", "NullableProblems", "RedundantSemicolon"),
            severity = "warning",
            includeDescription = true
        )
        
        println("📤 请求参数格式:")
        println(json.encodeToString(request))
        
        val response = listOf(
            InspectionResult(
                inspectionName = "UnusedDeclaration",
                displayName = "未使用的声明",
                enabled = true,
                severity = "WARNING",
                problemCount = 2,
                problems = listOf(
                    InspectionProblem(
                        message = "Function 'helperMethod' is never used",
                        line = 25,
                        column = 5,
                        highlightType = "WARNING",
                        fixes = listOf("Remove unused function", "Make private")
                    )
                )
            ),
            InspectionResult(
                inspectionName = "NullableProblems",
                displayName = "空值安全问题",
                enabled = true,
                severity = "ERROR", 
                problemCount = 1,
                problems = listOf(
                    InspectionProblem(
                        message = "Smart cast to 'String' is impossible",
                        line = 18,
                        column = 12,
                        highlightType = "ERROR",
                        fixes = listOf("Add null check", "Use safe call operator")
                    )
                )
            )
        )
        
        println("\n📥 响应格式示例:")
        println(json.encodeToString(response))
        
        println("\n💡 调用方式:")
        println("POST /tools/get_inspection_results")
        println("inspectionNames 为空时返回所有检查结果")
    }
    
    private fun demonstrateBatchAnalysis(json: Json) {
        println("\n🔄 5. batch_analyze_files - 批量分析工具")
        println("-".repeat(40))
        
        val request = BatchAnalyzeFilesRequest(
            filePaths = listOf(
                "/project/src/main/kotlin/com/example/ClassA.kt",
                "/project/src/main/kotlin/com/example/ClassB.kt", 
                "/project/src/main/kotlin/com/example/ClassC.kt"
            ),
            operations = listOf("errors", "quality", "syntax"),
            parallel = true,
            maxConcurrency = 3
        )
        
        println("📤 请求参数格式:")
        println(json.encodeToString(request))
        
        val response = BatchAnalysisResult(
            totalFiles = 3,
            successfulFiles = 2,
            failedFiles = 1,
            executionTime = 1250,
            results = listOf(
                SingleFileResult(
                    filePath = "/project/src/main/kotlin/com/example/ClassA.kt",
                    success = true,
                    operations = mapOf(
                        "errors" to "errorCount: 0, warningCount: 1",
                        "quality" to "score: 8.5, grade: A-",
                        "syntax" to "isValid: true"
                    )
                ),
                SingleFileResult(
                    filePath = "/project/src/main/kotlin/com/example/ClassB.kt",
                    success = true,
                    operations = mapOf(
                        "errors" to "errorCount: 2, warningCount: 3",
                        "quality" to "score: 6.2, grade: C+",
                        "syntax" to "isValid: false"
                    )
                ),
                SingleFileResult(
                    filePath = "/project/src/main/kotlin/com/example/ClassC.kt",
                    success = false,
                    operations = emptyMap<String, String>(),
                    error = "File not found"
                )
            ),
            summary = BatchSummary(
                totalErrors = 2,
                totalWarnings = 4,
                averageQualityScore = 7.35,
                riskFiles = listOf("/project/src/main/kotlin/com/example/ClassB.kt"),
                recommendations = listOf("Review ClassB.kt for quality improvements")
            )
        )
        
        println("\n📥 响应格式示例:")
        println(json.encodeToString(response))
        
        println("\n💡 调用方式:")  
        println("POST /tools/batch_analyze_files")
        println("支持的 operations: errors, quality, syntax")
        println("parallel=true 启用并发分析")
    }
    
    private operator fun String.times(n: Int) = this.repeat(n)
}

/**
 * 工具定义汇总
 */
object McpToolsSummary {
    
    fun printToolDefinitions() {
        println("\n📋 MCP 工具定义汇总")
        println("=".repeat(60))
        
        val tools = listOf(
            Triple("check_file_errors", "文件错误检查", "检查语法错误、类型错误、代码检查问题"),
            Triple("analyze_code_quality", "代码质量分析", "分析复杂度、重复代码、可维护性指标"),  
            Triple("validate_syntax", "语法验证", "快速验证文件语法正确性"),
            Triple("get_inspection_results", "检查结果获取", "获取特定 IntelliJ 检查工具结果"),
            Triple("batch_analyze_files", "批量文件分析", "并发分析多个文件的各项指标")
        )
        
        tools.forEach { (name, displayName, description) ->
            println("🔧 $name")
            println("   名称: $displayName")
            println("   描述: $description")
            println("   端点: POST /tools/$name")
            println()
        }
        
        println("🌟 特点:")
        println("• 基于真实的 IntelliJ Platform API")
        println("• 标准化的 JSON 请求/响应格式")
        println("• 详细的错误信息和位置")
        println("• 丰富的代码质量指标")
        println("• 支持批量和并发处理")
        println("• 完整的检查工具集成")
    }
}