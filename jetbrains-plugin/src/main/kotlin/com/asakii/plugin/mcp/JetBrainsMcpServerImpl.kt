package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolDefinition
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.*
import com.asakii.plugin.utils.ResourceLoader
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.intellij.openapi.project.Project
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val MCP_INSTRUCTIONS_PATH = "prompts/jetbrains-mcp-instructions.md"

/**
 * JetBrains MCP 服务器实现
 * 
 * 提供 IDEA 平台相关的工具，如目录树、文件问题检测、文件索引搜索、代码搜索等。
 * 这些工具利用 IDEA 的强大索引和分析能力，提供比纯文件系统操作更丰富的功能。
 */
@McpServerConfig(
    name = "jetbrains",
    version = "1.0.0",
    description = "JetBrains IDE integration tool server, providing directory browsing, file problem detection, index search, code search and other features"
)
class JetBrainsMcpServerImpl(private val project: Project) : McpServerBase() {
    
    // 工具实例
    private lateinit var directoryTreeTool: DirectoryTreeTool
    private lateinit var fileProblemsTool: FileProblemsTool
    private lateinit var fileIndexTool: FileIndexTool
    private lateinit var codeSearchTool: CodeSearchTool
    private lateinit var findUsagesTool: FindUsagesTool
    private lateinit var renameTool: RenameTool

    override fun getSystemPromptAppendix(): String {
        return ResourceLoader.loadTextOrDefault(
            MCP_INSTRUCTIONS_PATH,
            DEFAULT_MCP_INSTRUCTIONS
        )
    }

    /**
     * 获取需要自动允许的工具列表
     * JetBrains MCP 的所有工具都应该自动允许，因为它们只是读取 IDE 信息
     */
    override fun getAllowedTools(): List<String> = listOf(
        "DirectoryTree",
        "FileProblems",
        "FileIndex",
        "CodeSearch",
        "FindUsages",
        "Rename"
    )

    companion object {
        private const val DEFAULT_MCP_INSTRUCTIONS = """You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:

- `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
- `mcp__jetbrains__FileProblems`: Get static analysis results (errors, warnings) for a file
- `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index
- `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)

These tools are faster and more accurate than file system operations because they use IDE's pre-built indexes.

IMPORTANT: After completing code modifications, you MUST use `mcp__jetbrains__FileProblems` to perform static analysis validation on the modified files to minimize syntax errors.

IMPORTANT: When a project build/compile fails or a file is known to have syntax errors, use `mcp__jetbrains__FileProblems` with `includeWarnings: true` to quickly retrieve static analysis results and pinpoint issues. This is much faster than re-running the full build command."""
    }

    override suspend fun onInitialize() {
        logger.info { "🔧 Initializing JetBrains MCP Server for project: ${project.name}" }

        // 注册 JetBrains 插件的 Schema 来源
        ToolSchemaLoader.registerSchemaSource(JetBrainsSchemaSource)

        // 初始化工具实例
        directoryTreeTool = DirectoryTreeTool(project)
        fileProblemsTool = FileProblemsTool(project)
        fileIndexTool = FileIndexTool(project)
        codeSearchTool = CodeSearchTool(project)
        findUsagesTool = FindUsagesTool(project)
        renameTool = RenameTool(project)

        // 注册目录树工具
        registerToolFromSchema("DirectoryTree", directoryTreeTool.getInputSchema()) { arguments ->
            directoryTreeTool.execute(arguments)
        }

        // 注册文件问题检测工具
        registerToolFromSchema("FileProblems", fileProblemsTool.getInputSchema()) { arguments ->
            fileProblemsTool.execute(arguments)
        }

        // 注册文件索引搜索工具
        registerToolFromSchema("FileIndex", fileIndexTool.getInputSchema()) { arguments ->
            fileIndexTool.execute(arguments)
        }

        // 注册代码搜索工具
        registerToolFromSchema("CodeSearch", codeSearchTool.getInputSchema()) { arguments ->
            codeSearchTool.execute(arguments)
        }

        // 注册查找引用工具
        registerToolFromSchema("FindUsages", findUsagesTool.getInputSchema()) { arguments ->
            findUsagesTool.execute(arguments)
        }

        // 注册重命名工具
        registerToolFromSchema("Rename", renameTool.getInputSchema()) { arguments ->
            renameTool.execute(arguments)
        }

        logger.info { "✅ JetBrains MCP Server initialized, registered 6 tools" }
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

/**
 * JetBrains 插件的 Schema 来源
 *
 * 从 jetbrains-plugin 的 resources/mcp/schemas/tools.json 加载工具 Schema
 */
private object JetBrainsSchemaSource : ToolSchemaLoader.SchemaSource {
    private const val SCHEMA_PATH = "/mcp/schemas/tools.json"

    override fun loadSchemas(): Map<String, Map<String, Any>> {
        return ToolSchemaLoader.loadFromClasspath(
            JetBrainsMcpServerImpl::class.java,
            SCHEMA_PATH
        )
    }
}
