package com.asakii.server.mcp

import com.asakii.server.util.JsonTools
import com.asakii.claude.agent.sdk.mcp.ContentItem
import com.asakii.claude.agent.sdk.mcp.McpServer as SdkMcpServer
import com.asakii.claude.agent.sdk.mcp.ToolResult
import io.modelcontextprotocol.server.McpServer as OfficialMcpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.asakii.logging.*
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector

import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private val logger = getLogger("McpHttpGateway")

/**
 * MCP HTTP 网关
 *
 * 项目级实例设计：
 * - 每个 IDEA 项目拥有独立的 McpHttpGateway 实例
 * - 每个实例有独立的 Jetty 服务器和端口
 * - 解决多项目同时打开时 MCP 路由错误的问题
 *
 * 实例内复用设计：
 * - 在同一个网关实例中，每个 serverName 对应一个 Transport
 * - MCP SDK 自己管理 Transport 和 Session
 * - 我们只负责启动 HTTP 服务和路由请求
 * - connectId 通过 MCP SDK 的 TransportContext 传递给 callHandler，再转为协程上下文
 */
class McpHttpGateway {
    
    companion object {
        /** MCP 路由 header 名称 */
        const val HEADER_CONNECT_ID = "X-MCP-Connect-Id"
        
        /**
         * Jetty 默认 idleTimeout 只有 30s，SSE 长连接在空闲时会被断开，导致 session 频繁失效。
         * 这里显式调大 idleTimeout，同时把 MCP keep-alive 调整为更短间隔，尽量避免中间层/容器超时。
         * 
         * 注意：Claude CLI 使用的 MCP 客户端在会话丢失时不会自动重连（不符合 MCP 规范），
         * 所以必须确保会话尽可能不丢失。
         * 
         * 2025-01: 将 idleTimeout 从 30s 提高到 120s，keep-alive 从 15s 降到 10s，
         * 以减少 "Streamable HTTP error" 频繁出现的问题。
         * 
         * @see <a href="https://spec.modelcontextprotocol.io/specification/2025-03-26/basic/transports/">MCP Transport Spec</a>
         */
        private val JETTY_CONNECTOR_IDLE_TIMEOUT_MS = Duration.ofSeconds(120).toMillis()  // 120 秒
        private val MCP_KEEP_ALIVE_INTERVAL = Duration.ofSeconds(10)  // 10 秒心跳
    }
    
    private data class Endpoint(
        val path: String,
        val server: McpSyncServer,
        val transport: HttpServletStreamableServerTransportProvider
    )

    private val json = JsonTools.kotlinJson
    private val jsonMapper = JsonTools.mcpJsonMapper

    /** MCP 端点：key = serverName，实例内共享 */
    private val endpoints = ConcurrentHashMap<String, Endpoint>()
    private val lifecycleLock = Any()
    private var jettyServer: Server? = null
    private var actualPort: Int = 0

    /**
     * 网关 Servlet
     * 
     * 职责：路由请求到对应的 Transport
     * 
     * 注意：connectId 通过 MCP SDK 的 contextExtractor 从 HTTP header 提取，
     * 然后在 callHandler 中通过 exchange.transportContext() 获取。
     */
    private class GatewayServlet(
        private val resolver: (String) -> HttpServletStreamableServerTransportProvider?
    ) : jakarta.servlet.http.HttpServlet() {
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
            val path = req.requestURI ?: ""
            logger.debug { "[MCP] Incoming request: ${req.method} $path" }

            val transport = resolver(path)
            if (transport == null) {
                logger.warn { "[MCP] No transport found for path: $path" }
                resp.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }

            transport.service(req, resp)
        }
    }

    fun ensureStarted(): Int {
        synchronized(lifecycleLock) {
            if (jettyServer != null) return actualPort

            val server = Server()
            val connector = ServerConnector(server)
            connector.host = "127.0.0.1"
            connector.port = 0
            connector.idleTimeout = JETTY_CONNECTOR_IDLE_TIMEOUT_MS
            server.addConnector(connector)

            val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
            context.contextPath = "/"
            context.addServlet(ServletHolder(GatewayServlet(::resolveTransport)), "/mcp/*")
            server.handler = context

            server.start()
            actualPort = connector.localPort
            jettyServer = server
            logger.info { "[MCP] HTTP gateway started on http://127.0.0.1:$actualPort/mcp" }
            return actualPort
        }
    }
    
    /**
     * 关闭 MCP HTTP 网关
     * 
     * 清理所有资源：
     * - 关闭所有已注册的 MCP 服务器
     * - 停止 Jetty HTTP 服务器
     * - 清空端点映射
     */
    fun shutdown() {
        synchronized(lifecycleLock) {
            logger.info { "[MCP] Shutting down HTTP gateway (port=$actualPort, endpoints=${endpoints.size})" }
            
            // 关闭所有 MCP 服务器
            endpoints.values.forEach { endpoint ->
                try {
                    endpoint.server.close()
                } catch (e: Exception) {
                    logger.warn { "[MCP] Error closing MCP server: ${e.message}" }
                }
            }
            endpoints.clear()
            
            // 停止 Jetty 服务器
            jettyServer?.let { server ->
                try {
                    server.stop()
                    logger.info { "[MCP] Jetty server stopped" }
                } catch (e: Exception) {
                    logger.warn { "[MCP] Error stopping Jetty server: ${e.message}" }
                }
            }
            jettyServer = null
            actualPort = 0
            
            logger.info { "[MCP] HTTP gateway shutdown complete" }
        }
    }

    /**
     * 注册 MCP 服务器
     *
     * 实例内复用设计：
     * - 在同一个网关实例中，每个 serverName 只注册一次
     * - 已存在则复用，让 MCP SDK 管理 session
     *
     * @param serverName MCP 服务器名称
     * @param server MCP 服务器实例
     * @return MCP 端点 URL
     */
    suspend fun registerServer(
        serverName: String,
        server: SdkMcpServer
    ): String {
        ensureStarted()

        // 已存在则复用（在同一个网关实例内）
        endpoints[serverName]?.let { existing ->
            logger.info { "[MCP] Reusing endpoint: $serverName" }
            return buildUrl(existing.path)
        }

        val endpointPath = "/mcp/$serverName"
        val transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint(endpointPath)
            .keepAliveInterval(MCP_KEEP_ALIVE_INTERVAL)
            // 从 HTTP header 提取 connectId，存入 TransportContext
            .contextExtractor { request ->
                val connectId = request.getHeader(HEADER_CONNECT_ID)?.takeIf { it.isNotBlank() }
                McpTransportContext.create(mapOf(HEADER_CONNECT_ID to (connectId ?: "")))
            }
            .build()

        val serverBuilder = OfficialMcpServer.sync(transport)
            .serverInfo(server.name, server.version)
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .jsonMapper(JsonTools.mcpJsonMapper)
            .jsonSchemaValidator(JsonTools.mcpJsonSchemaValidator)

        server.getSystemPromptAppendix()
            ?.takeIf { it.isNotBlank() }
            ?.let { serverBuilder.instructions(it) }

        server.timeout?.takeIf { it > 0 }?.let {
            serverBuilder.requestTimeout(Duration.ofMillis(it))
        }

        val syncServer = serverBuilder.build()
        registerTools(syncServer, server)

        val endpoint = Endpoint(endpointPath, syncServer, transport)
        endpoints[serverName] = endpoint
        logger.info { "[MCP] Registered endpoint: $serverName" }

        return buildUrl(endpointPath)
    }

    /**
     * 根据请求路径解析 Transport
     */
    private fun resolveTransport(path: String): HttpServletStreamableServerTransportProvider? {
        val normalized = path.trimEnd('/')
        val serverName = normalized.removePrefix("/mcp/").takeIf { it.isNotBlank() } ?: return null
        return endpoints[serverName]?.transport
    }

    private suspend fun registerTools(syncServer: McpSyncServer, server: SdkMcpServer) {
        val tools = server.listTools()
        logger.info { "[MCP] Registering ${tools.size} tool(s) for server '${server.name}': ${tools.joinToString { it.name }}" }

        for (toolDef in tools) {
            val schemaJson = json.encodeToString(JsonObject.serializer(), toolDef.inputSchema)
            val tool = McpSchema.Tool.builder()
                .name(toolDef.name)
                .description(toolDef.description)
                .inputSchema(jsonMapper, schemaJson)
                .build()

            val spec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler { exchange, request ->
                    // 从 MCP SDK 的 TransportContext 获取 connectId
                    val connectId = exchange.transportContext()?.get(HEADER_CONNECT_ID) as? String
                        ?: ""
                    
                    runBlocking {
                        // 通过协程上下文传递 connectId，MCP 工具可通过 currentConnectId() 获取
                        withContext(McpCallContext(connectId.takeIf { it.isNotBlank() })) {
                            val jsonArgs = toJsonObject(request.arguments() ?: emptyMap())
                            val progressToken = request.progressToken()
                            val toolUseId = progressToken?.toString()
                                ?: java.util.UUID.randomUUID().toString()
                            
                            logger.info { "[MCP] 🎯 Tool call: ${toolDef.name}, connectId=$connectId, toolUseId=$toolUseId" }
                            
                            server.callToolWithContext(toolDef.name, jsonArgs, toolUseId)
                        }
                    }.let(::toCallToolResult)
                }
                .build()
            syncServer.addTool(spec)
        }
    }

    private fun toJsonObject(arguments: Map<String, Any>): JsonObject {
        if (arguments.isEmpty()) return JsonObject(emptyMap())
        val jsonText = jsonMapper.writeValueAsString(arguments)
        val parsed = json.parseToJsonElement(jsonText)
        return parsed as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun toCallToolResult(result: ToolResult): McpSchema.CallToolResult {
        val contents = result.content.map { item ->
            when (item) {
                is ContentItem.Text -> McpSchema.TextContent(item.text)
                is ContentItem.Json -> {
                    val text = json.encodeToString(JsonElement.serializer(), item.data)
                    McpSchema.TextContent(text)
                }
                is ContentItem.Binary -> {
                    val encoded = Base64.getEncoder().encodeToString(item.data)
                    if (item.mimeType.startsWith("image/")) {
                        McpSchema.ImageContent(null, encoded, item.mimeType)
                    } else {
                        McpSchema.TextContent(encoded)
                    }
                }
            }
        }
        return McpSchema.CallToolResult.builder()
            .content(contents)
            .isError(result.isError)
            .build()
    }

    private fun buildUrl(path: String): String = "http://127.0.0.1:$actualPort$path"

    /**
     * 获取 MCP 网关端口（启动后可用）
     */
    fun getPort(): Int = actualPort

    /**
     * 构建指定 serverName 的 MCP URL
     */
    fun buildServerUrl(serverName: String): String {
        ensureStarted()
        return buildUrl("/mcp/$serverName")
    }
}
