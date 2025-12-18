package com.asakii.server.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import mu.KotlinLogging
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import com.asakii.server.rpc.ClientCaller
import java.net.ServerSocket

private val logger = KotlinLogging.logger {}

/**
 * 统一的 MCP HTTP 服务器
 *
 * 在一个端口上提供两个 MCP 端点：
 * - /mcp/user_interaction - 用户交互工具
 * - /mcp/jetbrains - JetBrains IDE 工具
 */
class McpHttpServer(
    private val clientCaller: ClientCaller,
    private val jetBrainsToolProvider: McpToolProvider? = null
) {
    private var jettyServer: Server? = null
    private var actualPort: Int = 0
    private var userInteractionMcpServer: McpSyncServer? = null
    private var jetBrainsMcpServer: McpSyncServer? = null

    fun start(): Int {
        val port = findAvailablePort()

        // 创建 Jetty Server
        jettyServer = Server().apply {
            val connector = ServerConnector(this)
            connector.port = port
            connector.host = "127.0.0.1"
            addConnector(connector)

            val context = ServletContextHandler(ServletContextHandler.SESSIONS)
            context.contextPath = "/"

            // 注册 UserInteraction MCP 端点
            val userInteractionTransport = HttpServletStreamableServerTransportProvider.builder()
                .build()
            userInteractionMcpServer = McpServer.sync(userInteractionTransport)
                .serverInfo("user_interaction", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build()
            registerUserInteractionTools(userInteractionMcpServer!!)
            context.addServlet(ServletHolder(userInteractionTransport), "/mcp/user_interaction/*")
            logger.info { "✅ [MCP] 注册端点: /mcp/user_interaction" }

            // 注册 JetBrains MCP 端点（如果有 toolProvider）
            if (jetBrainsToolProvider != null) {
                val jetBrainsTransport = HttpServletStreamableServerTransportProvider.builder()
                    .build()
                jetBrainsMcpServer = McpServer.sync(jetBrainsTransport)
                    .serverInfo("jetbrains", "1.0.0")
                    .capabilities(ServerCapabilities.builder().tools(true).build())
                    .build()
                registerJetBrainsTools(jetBrainsMcpServer!!, jetBrainsToolProvider)
                context.addServlet(ServletHolder(jetBrainsTransport), "/mcp/jetbrains/*")
                logger.info { "✅ [MCP] 注册端点: /mcp/jetbrains" }
            }

            handler = context
        }

        jettyServer?.start()
        actualPort = port

        logger.info { "✅ [MCP] Server started at http://127.0.0.1:$actualPort/mcp" }
        return actualPort
    }

    fun stop() {
        try {
            userInteractionMcpServer?.close()
            jetBrainsMcpServer?.close()
            jettyServer?.stop()
            logger.info { "🛑 [MCP] Server stopped" }
        } catch (e: Exception) {
            logger.error { "❌ [MCP] Failed to stop: ${e.message}" }
        }
    }

    fun getPort(): Int = actualPort
    fun getUserInteractionUrl(): String = "http://127.0.0.1:$actualPort/mcp/user_interaction"
    fun getJetBrainsUrl(): String = "http://127.0.0.1:$actualPort/mcp/jetbrains"
    fun hasJetBrains(): Boolean = jetBrainsToolProvider != null

    private fun registerUserInteractionTools(server: McpSyncServer) {
        val jsonMapper = McpJsonMapper.getDefault()
        val tool = McpSchema.Tool.builder()
            .name("AskUserQuestion")
            .description("向用户询问问题并获取选择。使用此工具在需要用户输入或确认时与用户交互。")
            .inputSchema(jsonMapper, createAskUserQuestionSchema())
            .build()
        val spec = McpServerFeatures.SyncToolSpecification(tool) { _, arguments ->
            handleAskUserQuestion(arguments)
        }
        server.addTool(spec)
        logger.info { "✅ [MCP] 注册工具: user_interaction/AskUserQuestion" }
    }

    private fun registerJetBrainsTools(server: McpSyncServer, toolProvider: McpToolProvider) {
        val jsonMapper = McpJsonMapper.getDefault()
        toolProvider.getTools().forEach { toolDef ->
            val tool = McpSchema.Tool.builder()
                .name(toolDef.name)
                .description(toolDef.description)
                .inputSchema(jsonMapper, toolDef.inputSchema)
                .build()
            val spec = McpServerFeatures.SyncToolSpecification(tool) { _, arguments ->
                handleJetBrainsTool(toolDef.name, arguments, toolProvider)
            }
            server.addTool(spec)
            logger.info { "✅ [MCP] 注册工具: jetbrains/${toolDef.name}" }
        }
    }

    private fun createAskUserQuestionSchema(): String = """
    {
        "type": "object",
        "properties": {
            "questions": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "question": { "type": "string", "description": "问题内容" },
                        "header": { "type": "string", "description": "问题标题/分类标签" },
                        "options": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "label": { "type": "string", "description": "选项显示文本" },
                                    "description": { "type": "string", "description": "选项描述(可选)" }
                                },
                                "required": ["label"]
                            }
                        },
                        "multiSelect": { "type": "boolean", "description": "是否允许多选,默认 false" }
                    },
                    "required": ["question", "header", "options"]
                }
            }
        },
        "required": ["questions"]
    }
    """.trimIndent()

    private fun handleAskUserQuestion(arguments: Map<String, Any>): McpSchema.CallToolResult {
        return try {
            val questionsRaw = arguments["questions"] as? List<*> ?: emptyList<Any>()

            val protoRequest = com.asakii.rpc.proto.AskUserQuestionRequest.newBuilder().apply {
                questionsRaw.forEach { q ->
                    if (q is Map<*, *>) {
                        addQuestions(com.asakii.rpc.proto.QuestionItem.newBuilder().apply {
                            question = q["question"]?.toString() ?: ""
                            (q["header"] as? String)?.let { header = it }
                            (q["options"] as? List<*>)?.forEach { opt ->
                                if (opt is Map<*, *>) {
                                    addOptions(com.asakii.rpc.proto.QuestionOption.newBuilder().apply {
                                        label = opt["label"]?.toString() ?: ""
                                        (opt["description"] as? String)?.let { description = it }
                                    }.build())
                                }
                            }
                            multiSelect = q["multiSelect"] as? Boolean ?: false
                        }.build())
                    }
                }
            }.build()

            val response = kotlinx.coroutines.runBlocking { clientCaller.callAskUserQuestion(protoRequest) }
            val answersMap = response.answersList.associate { it.question to it.answer }
            val content = ObjectMapper().writeValueAsString(answersMap)

            logger.info { "✅ [AskUserQuestion] 完成" }
            McpSchema.CallToolResult(listOf(McpSchema.TextContent(content)), false)
        } catch (e: Exception) {
            logger.error { "❌ [AskUserQuestion] 失败: ${e.message}" }
            McpSchema.CallToolResult(listOf(McpSchema.TextContent("错误: ${e.message}")), true)
        }
    }

    private fun handleJetBrainsTool(name: String, arguments: Map<String, Any>, toolProvider: McpToolProvider): McpSchema.CallToolResult {
        return try {
            val result = kotlinx.coroutines.runBlocking { toolProvider.callTool(name, arguments) }
            McpSchema.CallToolResult(listOf(McpSchema.TextContent(result.content)), result.isError)
        } catch (e: Exception) {
            logger.error { "❌ [$name] 失败: ${e.message}" }
            McpSchema.CallToolResult(listOf(McpSchema.TextContent("错误: ${e.message}")), true)
        }
    }

    private fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }

    companion object {
        /**
         * 获取 UserInteraction MCP 提示词
         */
        fun getUserInteractionInstructions(): String {
            return try {
                val stream = McpHttpServer::class.java.getResourceAsStream("/prompts/user-interaction-mcp-instructions.md")
                stream?.bufferedReader()?.readText() ?: DEFAULT_USER_INTERACTION_INSTRUCTIONS
            } catch (e: Exception) {
                logger.warn { "⚠️ [MCP] 加载 UserInteraction 提示词失败: ${e.message}" }
                DEFAULT_USER_INTERACTION_INSTRUCTIONS
            }
        }

        /**
         * 获取 JetBrains MCP 提示词
         */
        fun getJetBrainsInstructions(): String {
            return try {
                val stream = McpHttpServer::class.java.getResourceAsStream("/prompts/jetbrains-mcp-instructions.md")
                stream?.bufferedReader()?.readText() ?: DEFAULT_JETBRAINS_INSTRUCTIONS
            } catch (e: Exception) {
                logger.warn { "⚠️ [MCP] 加载 JetBrains 提示词失败: ${e.message}" }
                DEFAULT_JETBRAINS_INSTRUCTIONS
            }
        }

        private const val DEFAULT_USER_INTERACTION_INSTRUCTIONS = """When you need clarification from the user, especially when presenting multiple options or choices, use the `mcp__user_interaction__AskUserQuestion` tool to ask questions. The user's response will be returned to you through this tool."""

        private const val DEFAULT_JETBRAINS_INSTRUCTIONS = """### MCP Tools

You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:

- `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
- `mcp__jetbrains__FileProblems`: Get static analysis results for a file (syntax errors, code errors, warnings, suggestions)
- `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index (supports scope filtering)
- `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)
- `mcp__jetbrains__FindUsages`: Find all references/usages of a symbol (class, method, field, variable) in the project
- `mcp__jetbrains__Rename`: Safely rename a symbol and automatically update all references (like Refactor > Rename)

IMPORTANT: Prefer JetBrains tools over file system tools (faster and more reliable due to IDE's pre-built indexes):
- Use `mcp__jetbrains__CodeSearch` instead of `Grep` for searching code content
- Use `mcp__jetbrains__FileIndex` instead of `Glob` for finding files, classes, and symbols

IMPORTANT: After completing code modifications, you MUST use `mcp__jetbrains__FileProblems` to perform static analysis validation on the modified files to minimize syntax errors."""
    }
}
