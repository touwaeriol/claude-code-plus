package com.claudecodeplus.mcp

import com.claudecodeplus.mcp.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 真实 API 演示程序
 * 展示基于 IntelliJ Platform API 的 MCP 工具实现
 */
object RealApiDemo {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("🔧 IntelliJ Platform MCP 服务器演示")
        println("=".repeat(50))
        
        println("\n📋 基于真实 IntelliJ Platform API 的功能特性：")
        println("• PSI (Program Structure Interface) 代码分析")
        println("• Code Inspections 集成")
        println("• 语法错误检测") 
        println("• 代码质量度量")
        println("• 线程安全的 ReadAction 执行")
        println("• 非阻塞后台处理")
        
        println("\n🏗 技术实现要点：")
        println("1. IntelliJAnalysisService - 核心分析服务")
        println("   • 使用 ReadAction.nonBlocking() 确保线程安全")
        println("   • PSI 错误检测和语法分析")
        println("   • 代码复杂度计算和质量度量")
        
        println("\n2. SimpleMcpServer - HTTP MCP 服务器")
        println("   • Ktor Netty 服务器实现")
        println("   • 标准化的 JSON API 接口")
        println("   • 完整的错误处理和日志记录")
        
        println("\n3. 线程模型合规性")
        println("   • 遵循 IntelliJ Platform 线程规则")
        println("   • EDT/BGT 线程分离")
        println("   • 异步 Future 基础 API")
        
        // 演示参数格式
        demonstrateRealApiFormats()
        
        println("\n" + "=".repeat(50))
        println("✅ 真实 IntelliJ Platform API 集成演示完成！")
        println("💡 现在可以在真实的 IntelliJ 插件环境中运行此 MCP 服务器")
        println("🔗 通过 http://localhost:8001 访问 MCP 工具")
    }
    
    private fun demonstrateRealApiFormats() {
        println("\n📊 真实 API 调用示例：")
        
        val json = Json { prettyPrint = true }
        
        // 文件错误检查请求
        println("\n🔍 check_file_errors 调用：")
        val errorRequest = CheckFileErrorsRequest(
            filePath = "/Users/project/src/main/kotlin/Main.kt",
            checkLevel = "all",
            includeInspections = true,
            maxErrors = 50
        )
        println("请求: ${json.encodeToString(errorRequest)}")
        
        // 代码质量分析请求
        println("\n📈 analyze_code_quality 调用：")
        val qualityRequest = AnalyzeCodeQualityRequest(
            filePath = "/Users/project/src/main/kotlin/ComplexClass.kt",
            metrics = listOf("complexity", "maintainability"),
            includeDetails = true
        )
        println("请求: ${json.encodeToString(qualityRequest)}")
        
        // 语法验证请求
        println("\n✅ validate_syntax 调用：")
        val syntaxRequest = ValidateSyntaxRequest(
            filePath = "/Users/project/src/main/kotlin/TestFile.kt",
            strict = false,
            includeWarnings = true
        )
        println("请求: ${json.encodeToString(syntaxRequest)}")
        
        println("\n💻 启动 MCP 服务器的方式：")
        println("```kotlin")
        println("val server = SimpleMcpServer(project, port = 8001)")
        println("server.start()")
        println("```")
        
        println("\n📡 Claude CLI 配置：")
        println("```json")
        println("{")
        println("  \"mcpServers\": {")
        println("    \"jetbrains-ide\": {")
        println("      \"command\": \"http://localhost:8001\",")
        println("      \"transport\": \"http\"")
        println("    }")
        println("  }")
        println("}")
        println("```")
    }
}

/**
 * MCP 工具能力说明
 */
object McpCapabilitiesDemo {
    
    fun printCapabilities() {
        println("\n🌟 MCP 工具能力对比：")
        println("┌─────────────────────┬──────────┬──────────────┐")
        println("│ 功能特性            │ Mock版本 │ 真实API版本  │")
        println("├─────────────────────┼──────────┼──────────────┤")
        println("│ 文件存在性检查      │ ✅       │ ✅           │")
        println("│ PSI 语法错误检测    │ ❌       │ ✅           │")
        println("│ IntelliJ 检查工具   │ ❌       │ ✅(基础版)   │")
        println("│ 代码复杂度分析      │ 模拟数据 │ ✅           │")
        println("│ 文件质量度量        │ 模拟数据 │ ✅           │")
        println("│ 多语言支持          │ ❌       │ ✅           │")
        println("│ 线程安全执行        │ ❌       │ ✅           │")
        println("│ IDE 环境依赖        │ 无       │ 需要         │")
        println("└─────────────────────┴──────────┴──────────────┘")
        
        println("\n🎯 使用场景建议：")
        println("• 开发测试阶段：使用 Mock 版本进行参数格式验证")
        println("• 生产环境：使用真实 API 版本获得完整 IDE 分析能力")
        println("• 独立部署：真实 API 版本需要在 IntelliJ 插件环境中运行")
    }
}