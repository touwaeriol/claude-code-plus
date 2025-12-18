package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.ContentItem
import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolDefinition
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.*
import com.asakii.plugin.utils.ResourceLoader
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.asakii.server.mcp.McpToolDefinition
import com.asakii.server.mcp.McpToolProvider
import com.asakii.server.mcp.McpToolResult
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.fasterxml.jackson.databind.ObjectMapper
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

    private val _server: JetBrainsMcpServerImpl by lazy {
        JetBrainsMcpServerImpl(project)
    }

    override fun getToolProvider(): McpToolProvider = JetBrainsMcpToolProvider(_server, project)
}

/**
 * JetBrains MCP 工具提供者适配器
 *
 * 将 JetBrainsMcpServerImpl 的工具适配为 McpToolProvider 接口
 */
private class JetBrainsMcpToolProvider(
    private val server: JetBrainsMcpServerImpl,
    private val project: Project
) : McpToolProvider {

    private val directoryTreeTool by lazy { DirectoryTreeTool(project) }
    private val fileProblemsTool by lazy { FileProblemsTool(project) }
    private val fileIndexTool by lazy { FileIndexTool(project) }
    private val codeSearchTool by lazy { CodeSearchTool(project) }
    private val findUsagesTool by lazy { FindUsagesTool(project) }
    private val renameTool by lazy { RenameTool(project) }

    private val objectMapper = ObjectMapper()

    override fun getTools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "DirectoryTree",
            description = "Browse project directory structure with filtering options",
            inputSchema = objectMapper.writeValueAsString(directoryTreeTool.getInputSchema())
        ),
        McpToolDefinition(
            name = "FileProblems",
            description = "Get static analysis results for a file (syntax errors, code errors, warnings, suggestions)",
            inputSchema = objectMapper.writeValueAsString(fileProblemsTool.getInputSchema())
        ),
        McpToolDefinition(
            name = "FileIndex",
            description = "Search files, classes, and symbols using IDE index (supports scope filtering)",
            inputSchema = objectMapper.writeValueAsString(fileIndexTool.getInputSchema())
        ),
        McpToolDefinition(
            name = "CodeSearch",
            description = "Search code content across project files (like Find in Files)",
            inputSchema = objectMapper.writeValueAsString(codeSearchTool.getInputSchema())
        ),
        McpToolDefinition(
            name = "FindUsages",
            description = "Find all references/usages of a symbol (class, method, field, variable) in the project",
            inputSchema = objectMapper.writeValueAsString(findUsagesTool.getInputSchema())
        ),
        McpToolDefinition(
            name = "Rename",
            description = "Safely rename a symbol and automatically update all references (like Refactor > Rename)",
            inputSchema = objectMapper.writeValueAsString(renameTool.getInputSchema())
        )
    )

    override suspend fun callTool(name: String, arguments: Map<String, Any>): McpToolResult {
        return try {
            val result = when (name) {
                "DirectoryTree" -> directoryTreeTool.execute(arguments)
                "FileProblems" -> fileProblemsTool.execute(arguments)
                "FileIndex" -> fileIndexTool.execute(arguments)
                "CodeSearch" -> codeSearchTool.execute(arguments)
                "FindUsages" -> findUsagesTool.execute(arguments)
                "Rename" -> renameTool.execute(arguments)
                else -> ToolResult.error("Unknown tool: $name")
            }
            when (result) {
                is ToolResult.Success -> {
                    val content = result.content.joinToString("\n") { item ->
                        when (item) {
                            is ContentItem.Text -> item.text
                            is ContentItem.Json -> objectMapper.writeValueAsString(item.data)
                            else -> item.toString()
                        }
                    }
                    McpToolResult(content = content, isError = false)
                }
                is ToolResult.Error -> McpToolResult(content = result.error, isError = true)
                is ToolResult -> McpToolResult(content = result.toString(), isError = result.isError)
                else -> McpToolResult(content = result.toString(), isError = false)
            }
        } catch (e: Exception) {
            logger.error(e) { "Tool execution failed: $name" }
            McpToolResult(content = "Error: ${e.message}", isError = true)
        }
    }
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
