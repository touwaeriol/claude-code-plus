package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.*
import com.claudecodeplus.sdk.mcp.*
import com.claudecodeplus.sdk.mcp.annotations.*
import kotlinx.coroutines.*

/**
 * 增强的 MCP 功能使用示例
 * 演示：
 * 1. 参数描述支持 - @ToolParam 注解提供参数说明
 * 2. 简化的字符串到类型转换 - AI 传递的字符串参数自动转换为正确类型
 * 3. 错误处理 - 转换失败时的明确错误信息
 */

@McpServerConfig(
    name = "file_processor",
    version = "2.1.0", 
    description = "文件处理工具集，支持参数验证和自动类型转换"
)
class FileProcessorServer : McpServerBase() {

    @McpTool("创建指定大小的文件")
    suspend fun createFile(
        @ToolParam("文件路径，例如 /tmp/test.txt") 
        filePath: String,
        
        @ToolParam("文件大小（字节），范围0到1GB，例如1024") 
        sizeBytes: Long,
        
        @ToolParam("是否覆盖已存在的文件，可选，默认false") 
        overwrite: Boolean = false
    ): String {
        // AI 会传递字符串 "1024" 给 sizeBytes，自动转换为 Long
        // AI 会传递字符串 "true" 给 overwrite，自动转换为 Boolean
        
        if (sizeBytes <= 0) {
            throw IllegalArgumentException("文件大小必须大于0")
        }
        
        return "创建文件: $filePath (${sizeBytes}字节, 覆盖: $overwrite)"
    }

    @McpTool("分析文件统计信息")
    suspend fun analyzeFile(
        @ToolParam("文件路径") 
        filePath: String,
        
        @ToolParam("分析深度，可选，默认1，范围1-5") 
        depth: Int = 1,
        
        @ToolParam("包含隐藏文件，可选，默认false")
        includeHidden: Boolean = false,
        
        @ToolParam("输出格式，可选，默认json")
        format: String = "json"
    ): Map<String, Any> {
        return mapOf(
            "path" to filePath,
            "depth" to depth,
            "includeHidden" to includeHidden,
            "format" to format,
            "fileCount" to (10..100).random(),
            "totalSize" to (1024..1048576).random()
        )
    }

    @McpTool("批量重命名文件")
    suspend fun batchRename(
        @ToolParam("源文件夹路径") 
        sourceDir: String,
        
        @ToolParam("文件名模式，例如 file_*") 
        pattern: String,
        
        @ToolParam("新文件名前缀，长度1-50字符") 
        prefix: String,
        
        @ToolParam("起始编号，可选，默认1，范围1-9999")
        startNumber: Int = 1,
        
        @ToolParam("测试模式（不实际重命名），可选，默认true")
        dryRun: Boolean = true
    ): List<Map<String, String>> {
        val fileCount = (3..8).random()
        return (0 until fileCount).map { index ->
            val num = startNumber + index
            mapOf(
                "oldName" to "old_file_$index.txt",
                "newName" to "${prefix}_${num}.txt",
                "action" to if (dryRun) "预览" else "重命名"
            )
        }
    }
}

/**
 * 使用示例函数
 */
suspend fun demonstrateEnhancedMcp() {
    println("=== 🚀 增强 MCP 功能演示 ===\n")
    
    val server = FileProcessorServer()
    
    // 1. 展示参数描述功能
    println("📋 工具定义（包含参数描述）:")
    val tools = server.listTools()
    tools.forEach { tool ->
        println("\n🔧 ${tool.name}: ${tool.description}")
        val schema = tool.inputSchema
        @Suppress("UNCHECKED_CAST")
        val properties = (schema["properties"] as? Map<String, Map<String, Any>>) ?: emptyMap()
        
        properties.forEach { (paramName, paramSchema) ->
            val description = paramSchema["description"] as? String ?: ""
            val type = paramSchema["type"] as? String ?: "unknown"
            val required = ((schema["required"] as? List<String>) ?: emptyList()).contains(paramName)
            val requiredMark = if (required) "*" else ""
            println("  • $paramName$requiredMark ($type): $description")
            
            // 显示约束信息
            paramSchema["example"]?.let { println("    例子: $it") }
            paramSchema["minimum"]?.let { println("    最小值: $it") }
            paramSchema["maximum"]?.let { println("    最大值: $it") }
            paramSchema["minLength"]?.let { println("    最小长度: $it") }
            paramSchema["maxLength"]?.let { println("    最大长度: $it") }
        }
    }
    
    println("\n" + "=".repeat(50))
    
    // 2. 演示字符串到类型的自动转换
    println("\n🔄 字符串类型转换演示:")
    
    println("\n1️⃣ 创建文件 (字符串 -> Long, Boolean)")
    val createResult = server.callTool("createFile", mapOf(
        "filePath" to "/tmp/example.txt",
        "sizeBytes" to "2048",      // 字符串 -> Long
        "overwrite" to "true"       // 字符串 -> Boolean
    ))
    println("   结果: ${(createResult as ToolResult.Success).content.first()}")
    
    println("\n2️⃣ 文件分析 (字符串 -> Int, Boolean)")
    val analyzeResult = server.callTool("analyzeFile", mapOf(
        "filePath" to "/home/user/documents",
        "depth" to "3",                    // 字符串 -> Int
        "includeHidden" to "false",        // 字符串 -> Boolean
        "format" to "detailed"
    ))
    println("   结果: ${(analyzeResult as ToolResult.Success).content.first()}")
    
    println("\n3️⃣ 批量重命名 (混合类型转换)")
    val renameResult = server.callTool("batchRename", mapOf(
        "sourceDir" to "/tmp/photos",
        "pattern" to "IMG_*",
        "prefix" to "vacation",
        "startNumber" to "100",            // 字符串 -> Int
        "dryRun" to "false"                // 字符串 -> Boolean
    ))
    println("   结果: ${(renameResult as ToolResult.Success).content.first()}")
    
    println("\n" + "=".repeat(50))
    
    // 3. 演示错误处理
    println("\n❌ 错误处理演示:")
    
    println("\n🚫 无效数值转换")
    val invalidResult = server.callTool("createFile", mapOf(
        "filePath" to "/tmp/test.txt",
        "sizeBytes" to "not_a_number",     // 无效数值
        "overwrite" to "true"
    ))
    println("   错误: ${(invalidResult as ToolResult.Error).error}")
    
    println("\n🚫 业务逻辑错误")
    val businessErrorResult = server.callTool("createFile", mapOf(
        "filePath" to "/tmp/test.txt",
        "sizeBytes" to "-100",             // 负数，业务逻辑错误
        "overwrite" to "true"
    ))
    println("   错误: ${(businessErrorResult as ToolResult.Error).error}")
    
    println("\n✅ 增强 MCP 功能演示完成!")
}

/**
 * 主函数 - 运行演示
 */
suspend fun main() {
    demonstrateEnhancedMcp()
}