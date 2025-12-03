package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.server.rpc.ClientCaller
import com.asakii.server.rpc.callTyped
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

/**
 * 授权响应数据类
 */
@Serializable
data class PermissionResponse(
    val approved: Boolean
)

/**
 * 工具授权 MCP Server
 *
 * 提供 RequestPermission 工具，用于请求用户授权执行敏感工具操作。
 * 当 Claude 需要执行 Bash、Write、Edit 等工具时，会先调用此工具请求授权。
 */
@McpServerConfig(
    name = "permission",
    version = "1.0.0",
    description = "工具授权服务器，用于请求用户批准工具执行"
)
class PermissionMcpServer : McpServerBase() {

    private val logger = Logger.getLogger(javaClass.name)
    private var clientCaller: ClientCaller? = null

    companion object {
        /** RequestPermission 工具的 JSON Schema 定义 */
        val REQUEST_PERMISSION_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "tool_name" to mapOf(
                    "type" to "string",
                    "description" to "需要授权的工具名称，如 Bash、Write、Edit 等"
                ),
                "tool_input" to mapOf(
                    "type" to "object",
                    "description" to "工具的输入参数"
                )
            ),
            "required" to listOf("tool_name", "tool_input")
        )
    }

    /**
     * 设置客户端调用器
     */
    fun setClientCaller(caller: ClientCaller) {
        this.clientCaller = caller
        logger.info("✅ [PermissionMcpServer] ClientCaller 已设置")
    }

    override suspend fun onInitialize() {
        // 注册 RequestPermission 工具
        registerToolWithSchema(
            name = "RequestPermission",
            description = "请求用户授权执行工具操作。在执行敏感操作（如执行命令、写入文件）前调用此工具获取用户许可。返回 { approved: true } 表示用户批准，返回 { approved: false } 表示用户跳过。",
            inputSchema = REQUEST_PERMISSION_SCHEMA
        ) { arguments ->
            handleRequestPermission(arguments)
        }

        logger.info("✅ [PermissionMcpServer] 初始化完成，已注册 RequestPermission 工具")
    }

    /**
     * 处理 RequestPermission 工具调用
     */
    private suspend fun handleRequestPermission(arguments: Map<String, Any>): ToolResult {
        val caller = clientCaller
            ?: return ToolResult.error("ClientCaller 未设置，无法与前端通信")

        logger.info("📩 [RequestPermission] 收到授权请求，参数: $arguments")

        try {
            // 获取参数
            val toolName = arguments["tool_name"] as? String
                ?: return ToolResult.error("缺少 tool_name 参数")
            @Suppress("UNCHECKED_CAST")
            val toolInput = arguments["tool_input"] as? Map<String, Any>
                ?: return ToolResult.error("缺少 tool_input 参数")

            logger.info("📤 [RequestPermission] 请求授权工具: $toolName")

            // 调用前端方法，获取用户响应
            val response: PermissionResponse = caller.callTyped(
                method = "RequestPermission",
                params = mapOf(
                    "tool_name" to toolName,
                    "tool_input" to toolInput
                )
            )

            logger.info("📥 [RequestPermission] 收到用户响应: approved=${response.approved}")

            // 返回结果给 Claude
            val result = Json.encodeToString(mapOf("approved" to response.approved))

            logger.info("✅ [RequestPermission] 完成，返回: $result")
            return ToolResult.success(result)

        } catch (e: Exception) {
            logger.severe("❌ [RequestPermission] 处理失败: ${e.message}")
            e.printStackTrace()
            return ToolResult.error("授权请求处理失败: ${e.message}")
        }
    }
}
