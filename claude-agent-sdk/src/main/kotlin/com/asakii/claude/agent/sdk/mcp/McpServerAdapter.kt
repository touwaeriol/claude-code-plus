package com.asakii.claude.agent.sdk.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * 适配器：将官方 MCP Kotlin SDK 的 Server 转换为我们的 McpServer 接口
 *
 * 使用示例：
 * ```kotlin
 * val adapter = McpServerAdapter.create("my-server", "1.0.0") {
 *     addTool(
 *         name = "greet",
 *         description = "Say hello"
 *     ) { request ->
 *         CallToolResult.success("Hello!")
 *     }
 * }
 * // 现在可以将 adapter 作为 McpServer 使用
 * ```
 */
class McpServerAdapter(
    private val server: Server,
    override val name: String,
    override val version: String
) : McpServer {

    private val logger = Logger.getLogger(McpServerAdapter::class.java.name)

    override val description: String = ""

    override suspend fun listTools(): List<ToolDefinition> {
        return try {
            server.tools.values.map { registeredTool ->
                val tool = registeredTool.tool
                ToolDefinition(
                    name = tool.name,
                    description = tool.description ?: "",
                    inputSchema = convertToolSchemaToMap(tool.inputSchema)
                )
            }
        } catch (e: Exception) {
            logger.warning("获取工具列表失败: ${e.message}")
            emptyList()
        }
    }

    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): ToolResult {
        return try {
            logger.info("🔧 调用官方 SDK 工具: $toolName, 参数: $arguments")

            // 查找注册的工具
            val registeredTool = server.tools[toolName]
                ?: return ToolResult.error("Tool '$toolName' not found")

            // 构建 CallToolRequest
            val jsonArgs = buildJsonObject {
                arguments.forEach { (key, value) ->
                    put(key, anyToJsonElement(value))
                }
            }

            // 创建 CallToolRequest 并调用处理器
            val request = CallToolRequest(
                CallToolRequestParams(
                    name = toolName,
                    arguments = jsonArgs
                )
            )

            val result = registeredTool.handler(request)

            // 转换结果
            val content = result.content.map { item ->
                when (item) {
                    is TextContent -> ContentItem.text(item.text)
                    else -> ContentItem.text(item.toString())
                }
            }

            if (result.isError == true) {
                ToolResult.Error(
                    error = content.firstOrNull()?.let {
                        (it as? ContentItem.Text)?.text
                    } ?: "Unknown error",
                    content = content
                )
            } else {
                ToolResult.Success(content = content)
            }
        } catch (e: Exception) {
            logger.severe("❌ 工具调用失败: ${e.message}")
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    private fun convertToolSchemaToMap(schema: ToolSchema?): Map<String, Any> {
        if (schema == null) return mapOf("type" to "object", "properties" to emptyMap<String, Any>())

        return mapOf(
            "type" to schema.type,
            "properties" to (schema.properties?.let { jsonObjectToMap(it) } ?: emptyMap()),
            "required" to (schema.required ?: emptyList())
        )
    }

    private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any?> {
        return jsonObject.entries.associate { (key, value) ->
            key to jsonElementToAny(value)
        }
    }

    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> buildJsonObject {
                value.forEach { (k, v) ->
                    if (k is String) {
                        put(k, anyToJsonElement(v))
                    }
                }
            }
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            is JsonElement -> value
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonObject -> jsonObjectToMap(element)
            is JsonArray -> element.map { jsonElementToAny(it) }
        }
    }

    companion object {
        /**
         * 创建一个使用官方 MCP SDK 的服务器适配器
         *
         * @param name 服务器名称
         * @param version 服务器版本
         * @param configure 配置回调，可以在这里添加工具
         */
        fun create(
            name: String,
            version: String = "1.0.0",
            configure: Server.() -> Unit = {}
        ): McpServerAdapter {
            val server = Server(
                serverInfo = Implementation(name = name, version = version),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools(listChanged = true)
                    )
                ),
                block = configure
            )
            return McpServerAdapter(server, name, version)
        }
    }
}

/**
 * 扩展函数：将官方 SDK Server 转换为 McpServer
 */
fun Server.toMcpServer(name: String, version: String = "1.0.0"): McpServer {
    return McpServerAdapter(this, name, version)
}
