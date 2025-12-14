package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolDefinition
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.*
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.intellij.openapi.project.Project
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * JetBrains MCP 服务器实现
 * 
 * 提供 IDEA 平台相关的工具，如目录树、文件问题检测、文件索引搜索、代码搜索等。
 * 这些工具利用 IDEA 的强大索引和分析能力，提供比纯文件系统操作更丰富的功能。
 */
@McpServerConfig(
    name = "jetbrains",
    version = "1.0.0",
    description = "JetBrains IDE 集成工具服务器，提供目录浏览、文件问题检测、索引搜索、代码搜索等功能"
)
class JetBrainsMcpServerImpl(private val project: Project) : McpServerBase() {
    
    // 工具实例
    private lateinit var directoryTreeTool: DirectoryTreeTool
    private lateinit var fileProblemsTool: FileProblemsTool
    private lateinit var fileIndexTool: FileIndexTool
    private lateinit var codeSearchTool: CodeSearchTool

    override fun getSystemPromptAppendix(): String = """
        You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:
        
        - `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
        - `mcp__jetbrains__FileProblems`: Get static analysis results (errors, warnings) for a file
        - `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index
        - `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)
        
        These tools are faster and more accurate than file system operations because they use IDE's pre-built indexes.
    """.trimIndent()

    override suspend fun onInitialize() {
        logger.info { "🔧 初始化 JetBrains MCP Server for project: ${project.name}" }
        
        // 初始化工具实例
        directoryTreeTool = DirectoryTreeTool(project)
        fileProblemsTool = FileProblemsTool(project)
        fileIndexTool = FileIndexTool(project)
        codeSearchTool = CodeSearchTool(project)
        
        // 注册目录树工具
        registerToolWithSchema(
            name = "DirectoryTree",
            description = "获取项目目录的树形结构。支持深度限制、文件过滤、隐藏文件等选项。",
            inputSchema = directoryTreeTool.getInputSchema()
        ) { arguments ->
            directoryTreeTool.execute(arguments)
        }
        
        // 注册文件问题检测工具
        registerToolWithSchema(
            name = "FileProblems",
            description = "获取指定文件的静态分析结果，包括编译错误、警告和代码检查问题。使用 IDE 的实时分析能力。",
            inputSchema = fileProblemsTool.getInputSchema()
        ) { arguments ->
            fileProblemsTool.execute(arguments)
        }
        
        // 注册文件索引搜索工具
        registerToolWithSchema(
            name = "FileIndex",
            description = "通过关键词在 IDE 索引中搜索文件、类、符号。比文件系统搜索更快，支持模糊匹配。",
            inputSchema = fileIndexTool.getInputSchema()
        ) { arguments ->
            fileIndexTool.execute(arguments)
        }
        
        // 注册代码搜索工具
        registerToolWithSchema(
            name = "CodeSearch",
            description = "在项目文件中搜索代码或文本内容（类似 IDE 的 Find in Files 功能）。支持正则表达式、大小写敏感、全词匹配等选项。",
            inputSchema = codeSearchTool.getInputSchema()
        ) { arguments ->
            codeSearchTool.execute(arguments)
        }
        
        logger.info { "✅ JetBrains MCP Server 初始化完成，已注册 4 个工具" }
    }
}

/**
 * JetBrains MCP 服务器提供者实现
 * 
 * 在 jetbrains-plugin 模块中实现，提供对 IDEA Platform API 的访问。
 */
class JetBrainsMcpServerProviderImpl(private val project: Project) : JetBrainsMcpServerProvider {
    
    private val _server: McpServer by lazy {
        JetBrainsMcpServerImpl(project)
    }
    
    override fun getServer(): McpServer = _server
}
