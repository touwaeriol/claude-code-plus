package com.claudecodeplus.sdk.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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
     * 列出所有可用工具
     */
    suspend fun listTools(): List<ToolDefinition>
    
    /**
     * 调用指定的工具
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): ToolResult
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
        fun json(data: Any) = Json(JsonPrimitive(data.toString()))
        fun binary(data: ByteArray, mimeType: String) = Binary(data, mimeType)
    }
}

/**
 * 内部工具处理器
 */
internal data class ToolHandler(
    val name: String,
    val description: String,
    val parameterSchema: Map<String, ParameterInfo>? = null,
    val handler: suspend (Map<String, Any>) -> Any
) {
    fun toDefinition(): ToolDefinition {
        return if (parameterSchema != null) {
            ToolDefinition.withParameterInfo(name, description, parameterSchema)
        } else {
            ToolDefinition.simple(name, description)
        }
    }
}