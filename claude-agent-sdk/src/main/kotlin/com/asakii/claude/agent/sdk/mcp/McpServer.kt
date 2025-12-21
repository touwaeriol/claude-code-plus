package com.asakii.claude.agent.sdk.mcp

import kotlinx.serialization.json.*

/**
 * MCP Server接口 - 所有MCP服务器的基础接口
 *
 * 提供了创建自定义MCP工具服务器的标准接口，支持工具列表查询和工具调用执行。
 */
interface McpServer {
    /**
     * 服务器名称，用作标识符
     */
    val name: String

    /**
     * 服务器版本
     */
    val version: String

    /**
     * 服务器描述
     */
    val description: String

    /**
     * 工具调用超时时间（毫秒）
     * - null 或 0 或负数: 无限超时（适用于需要用户交互的工具，如 AskUserQuestion）
     * - 正数: 指定超时时间
     */
    val timeout: Long?
        get() = null  // 默认无限超时

    /**
     * 是否在收到进度报告时重置超时计时器
     *
     * 当设置为 true 时，只要 MCP 工具持续报告进度，超时计时器就会重置。
     * 这允许长时间运行的操作（如需要用户交互）不会因为超时而失败。
     *
     * 参考: https://github.com/anthropics/claude-code/issues/470
     */
    val resetTimeoutOnProgress: Boolean
        get() = false

    /**
     * 是否启用进度报告
     *
     * 当设置为 true 时，MCP 服务器应该在执行长时间操作时定期报告进度。
     * 配合 resetTimeoutOnProgress 使用可以防止超时。
     */
    val progressReportingEnabled: Boolean
        get() = false

    /**
     * 获取该 MCP 服务器的系统提示词追加内容
     *
     * MCP 服务器可以通过此方法提供额外的系统提示词，这些提示词将被追加到主系统提示词后面，
     * 用于指导 AI 如何正确使用该服务器提供的工具。
     *
     * @return 系统提示词追加内容，返回 null 表示无额外提示词
     */
    fun getSystemPromptAppendix(): String? = null

    /**
     * 获取该 MCP 服务器需要自动允许的工具列表
     *
     * 返回的工具名称将被添加到 Claude CLI 的 --allowed-tools 参数中，
     * 这些工具将自动获得权限批准，无需用户手动确认。
     *
     * 注意：返回的是简短工具名（如 "AskUserQuestion"），
     * 系统会自动添加 mcp__{serverName}__ 前缀。
     *
     * @return 需要自动允许的工具名列表，默认返回空列表
     */
    fun getAllowedTools(): List<String> = emptyList()

    /**
     * 列出所有可用工具
     */
    suspend fun listTools(): List<ToolDefinition>

    /**
     * 调用指定的工具（Map 参数版本，兼容旧代码）
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): ToolResult

    /**
     * 调用指定的工具（JsonObject 参数版本，推荐使用）
     * 直接传递原始 JSON，避免类型转换问题
     */
    suspend fun callToolJson(toolName: String, arguments: JsonObject): ToolResult {
        // 默认实现：递归转换 JsonObject 为 Map，调用旧方法
        val map = JsonConverter.jsonObjectToMap(arguments)
        return callTool(toolName, map)
    }
}

/**
 * JSON 转换工具
 */
object JsonConverter {
    /**
     * 递归将 JsonElement 转换为普通类型
     */
    fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
            is JsonObject -> jsonObjectToMap(element)
            is JsonArray -> element.map { jsonElementToAny(it) }
        }
    }

    /**
     * 将 JsonObject 转换为 Map<String, Any>
     */
    fun jsonObjectToMap(obj: JsonObject): Map<String, Any> {
        return obj.entries
            .mapNotNull { (k, v) -> jsonElementToAny(v)?.let { k to it } }
            .toMap()
    }
}

/**
 * 工具定义
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * 创建简单的工具定义
         */
        fun simple(name: String, description: String) = ToolDefinition(
            name = name,
            description = description,
            inputSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>()
            )
        )
        
        /**
         * 从参数类型创建工具定义（兼容旧版本）
         */
        fun withParameters(
            name: String, 
            description: String, 
            parameters: Map<String, ParameterType>
        ) = withParameterInfo(
            name = name,
            description = description,
            parameters = parameters.mapValues { (_, type) ->
                ParameterInfo(type = type)
            }
        )
        
        /**
         * 从参数信息创建工具定义（支持描述）
         */
        fun withParameterInfo(
            name: String, 
            description: String, 
            parameters: Map<String, ParameterInfo>
        ) = ToolDefinition(
            name = name,
            description = description,
            inputSchema = mapOf(
                "type" to "object",
                "properties" to parameters.mapValues { (_, paramInfo) ->
                    val schema = mutableMapOf<String, Any>()
                    
                    // 设置基础类型
                    when (paramInfo.type) {
                        ParameterType.STRING -> schema["type"] = "string"
                        ParameterType.NUMBER -> schema["type"] = "number" 
                        ParameterType.BOOLEAN -> schema["type"] = "boolean"
                        ParameterType.ARRAY -> schema["type"] = "array"
                        ParameterType.OBJECT -> schema["type"] = "object"
                    }
                    
                    // 添加描述信息
                    if (paramInfo.description.isNotEmpty()) {
                        schema["description"] = paramInfo.description
                    }
                    
                    schema.toMap()
                },
                "required" to parameters.keys.toList() // 所有参数都是必需的，除非有默认值
            )
        )
    }
}

/**
 * 参数类型枚举
 */
enum class ParameterType {
    STRING, NUMBER, BOOLEAN, ARRAY, OBJECT
}

/**
 * 参数信息类 - 只包含类型和描述信息
 */
data class ParameterInfo(
    val type: ParameterType,
    val description: String = ""
)

/**
 * 工具执行结果
 */
sealed class ToolResult {
    abstract val content: List<ContentItem>
    abstract val isError: Boolean
    
    /**
     * 成功结果
     */
    data class Success(
        override val content: List<ContentItem>,
        val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val isError = false
    }
    
    /**
     * 错误结果  
     */
    data class Error(
        val error: String,
        val code: Int = -1,
        override val content: List<ContentItem> = emptyList()
    ) : ToolResult() {
        override val isError = true
    }
    
    companion object {
        /**
         * 创建成功结果（文本内容）
         */
        fun success(text: String, metadata: Map<String, Any> = emptyMap()) = Success(
            content = listOf(ContentItem.text(text)),
            metadata = metadata
        )
        
        /**
         * 创建成功结果（结构化数据）
         */
        fun success(data: Any, metadata: Map<String, Any> = emptyMap()) = Success(
            content = listOf(ContentItem.json(data)),
            metadata = metadata
        )
        
        /**
         * 创建错误结果
         */
        fun error(message: String, code: Int = -1) = Error(
            error = message,
            code = code,
            content = listOf(ContentItem.text("错误: $message"))
        )
    }
}

/**
 * 内容项
 */
sealed class ContentItem {
    /**
     * 文本内容
     */
    data class Text(val text: String) : ContentItem()
    
    /**
     * JSON数据内容
     */
    data class Json(val data: JsonElement) : ContentItem()
    
    /**
     * 二进制内容
     */
    data class Binary(val data: ByteArray, val mimeType: String) : ContentItem()
    
    companion object {
        fun text(content: String) = Text(content)
        fun json(data: Any): Json = Json(anyToJsonElement(data))
        fun binary(data: ByteArray, mimeType: String) = Binary(data, mimeType)

        /**
         * 递归将任意对象转换为 JsonElement
         */
        private fun anyToJsonElement(value: Any?): JsonElement {
            return when (value) {
                null -> JsonNull
                is JsonElement -> value
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Map<*, *> -> buildJsonObject {
                    value.forEach { (k, v) ->
                        put(k.toString(), anyToJsonElement(v))
                    }
                }
                is List<*> -> buildJsonArray {
                    value.forEach { add(anyToJsonElement(it)) }
                }
                is Array<*> -> buildJsonArray {
                    value.forEach { add(anyToJsonElement(it)) }
                }
                else -> JsonPrimitive(value.toString())
            }
        }
    }
}

/**
 * 内部工具处理器接口
 */
internal interface ToolHandlerBase {
    val name: String
    val description: String
    val handler: suspend (Map<String, Any>) -> Any
    fun toDefinition(): ToolDefinition
}

/**
 * 内部工具处理器（使用 ParameterInfo）
 */
internal data class ToolHandler(
    override val name: String,
    override val description: String,
    val parameterSchema: Map<String, ParameterInfo>? = null,
    override val handler: suspend (Map<String, Any>) -> Any
) : ToolHandlerBase {
    override fun toDefinition(): ToolDefinition {
        return if (parameterSchema != null) {
            ToolDefinition.withParameterInfo(name, description, parameterSchema)
        } else {
            ToolDefinition.simple(name, description)
        }
    }
}

/**
 * 内部工具处理器（使用完整 JSON Schema）
 */
internal data class ToolHandlerWithSchema(
    override val name: String,
    override val description: String,
    val inputSchema: Map<String, Any>,
    override val handler: suspend (Map<String, Any>) -> Any
) : ToolHandlerBase {
    override fun toDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema
        )
    }
}