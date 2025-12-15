package com.asakii.claude.agent.sdk.mcp

import com.asakii.claude.agent.sdk.mcp.annotations.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * MCP Server 抽象基类
 * 
 * 提供基于注解的自动工具注册和手动工具注册两种方式。
 * 用户可以继承此类，使用 @McpTool 注解标记工具方法，或者手动注册工具。
 * 
 * 使用示例：
 * ```kotlin
 * @McpServerConfig(
 *     name = "calculator", 
 *     version = "1.0.0",
 *     description = "数学计算工具服务器"
 * )
 * class CalculatorServer : McpServerBase() {
 *     @McpTool(description = "计算两个数的和")
 *     suspend fun add(
 *         @ToolParam("第一个数") a: Double,
 *         @ToolParam("第二个数") b: Double
 *     ): Double {
 *         return a + b
 *     }
 * }
 * ```
 */
abstract class McpServerBase : McpServer {
    private val logger = KotlinLogging.logger {}
    private val registeredTools = mutableMapOf<String, ToolHandlerBase>()
    private var initialized = false
    
    // 从注解中获取服务器配置
    private val serverConfig: McpServerConfig? = this::class.findAnnotation<McpServerConfig>()
    
    // 实现 McpServer 接口
    override val name: String = run {
        val configName = serverConfig?.name
        when {
            configName.isNullOrEmpty() -> this::class.simpleName ?: "unknown"
            else -> configName
        }
    }
    override val version: String = serverConfig?.version ?: "1.0.0" 
    override val description: String = serverConfig?.description ?: ""
    
    /**
     * 初始化服务器，扫描注解并注册工具
     */
    private suspend fun ensureInitialized() {
        if (!initialized) {
            logger.info("🔧 初始化 MCP Server: $name")
            
            // 扫描并注册注解工具
            scanAndRegisterAnnotatedTools()
            
            // 调用用户自定义初始化
            onInitialize()
            
            initialized = true
            logger.info("✅ MCP Server '$name' 初始化完成，已注册 ${registeredTools.size} 个工具")
        }
    }
    
    /**
     * 用户可重写的初始化方法
     */
    protected open suspend fun onInitialize() {
        // 默认空实现，子类可重写进行自定义初始化
    }
    
    /**
     * 扫描并注册所有带 @McpTool 注解的方法
     */
    private suspend fun scanAndRegisterAnnotatedTools() {
        val kClass = this::class
        
        kClass.memberFunctions.forEach { function ->
            val mcpTool = function.findAnnotation<McpTool>()
            if (mcpTool != null) {
                registerAnnotatedTool(function, mcpTool)
            }
        }
        
        logger.info("📋 从注解扫描到 ${registeredTools.size} 个工具")
    }
    
    /**
     * 注册带注解的工具方法
     */
    private suspend fun registerAnnotatedTool(function: KFunction<*>, mcpTool: McpTool) {
        val toolName = function.name
        val description = mcpTool.value
        
        // 构建参数Schema
        val parameterSchema = buildParameterSchema(function)
        
        // 创建工具处理器
        val handler = ToolHandler(
            name = toolName,
            description = description,
            parameterSchema = parameterSchema,
            handler = { arguments ->
                invokeAnnotatedFunction(function, arguments)
            }
        )
        
        registeredTools[toolName] = handler
        logger.info("🔧 注册工具: $toolName - $description")
    }
    
    /**
     * 从函数参数构建参数Schema
     */
    private fun buildParameterSchema(function: KFunction<*>): Map<String, ParameterInfo>? {
        val parameters = function.parameters.drop(1) // 跳过 this 参数
        if (parameters.isEmpty()) return null
        
        return parameters.associate { param ->
            val toolParam = param.findAnnotation<ToolParam>()
            val paramName = param.name ?: "param${param.index}"
            val paramType = mapKotlinTypeToParameterType(param.type)
            
            paramName to ParameterInfo(
                type = paramType,
                description = toolParam?.value ?: ""
            )
        }
    }
    
    /**
     * 将Kotlin类型映射为ParameterType
     */
    private fun mapKotlinTypeToParameterType(type: KType): ParameterType {
        return when (type.classifier) {
            String::class -> ParameterType.STRING
            Int::class, Long::class, Float::class, Double::class -> ParameterType.NUMBER
            Boolean::class -> ParameterType.BOOLEAN
            List::class, Array::class -> ParameterType.ARRAY
            Map::class -> ParameterType.OBJECT
            else -> ParameterType.STRING // 默认为字符串
        }
    }
    
    /**
     * 调用带注解的函数
     */
    private suspend fun invokeAnnotatedFunction(function: KFunction<*>, arguments: Map<String, Any>): Any {
        val parameters = function.parameters
        val args = mutableListOf<Any?>()
        
        // 第一个参数是 this
        args.add(this)
        
        // 添加其他参数
        parameters.drop(1).forEach { param ->
            val paramName = param.name ?: "param${param.index}"
            val value = arguments[paramName]
            
            // 类型转换
            val convertedValue = convertParameterValue(value, param.type)
            args.add(convertedValue)
        }
        
        return try {
            if (function.isSuspend) {
                function.callSuspend(*args.toTypedArray())
            } else {
                function.call(*args.toTypedArray())
            } ?: Unit
        } catch (e: Exception) {
            logger.error("❌ 工具调用失败: ${function.name}, 错误: ${e.message}")
            throw e
        }
    }
    
    /**
     * 参数值类型转换（简化版本）
     */
    private fun convertParameterValue(value: Any?, targetType: KType): Any? {
        if (value == null) return null
        
        return try {
            when (targetType.classifier) {
                String::class -> value.toString()
                Int::class -> when (value) {
                    is Number -> value.toInt()
                    is String -> value.toInt()
                    else -> throw IllegalArgumentException("无法将 ${value::class.simpleName} 转换为 Int")
                }
                Long::class -> when (value) {
                    is Number -> value.toLong()
                    is String -> value.toLong()
                    else -> throw IllegalArgumentException("无法将 ${value::class.simpleName} 转换为 Long")
                }
                Float::class -> when (value) {
                    is Number -> value.toFloat()
                    is String -> value.toFloat()
                    else -> throw IllegalArgumentException("无法将 ${value::class.simpleName} 转换为 Float")
                }
                Double::class -> when (value) {
                    is Number -> value.toDouble()
                    is String -> value.toDouble()
                    else -> throw IllegalArgumentException("无法将 ${value::class.simpleName} 转换为 Double")
                }
                Boolean::class -> when (value) {
                    is Boolean -> value
                    is String -> value.toBoolean()
                    is Number -> value.toDouble() != 0.0
                    else -> throw IllegalArgumentException("无法将 ${value::class.simpleName} 转换为 Boolean")
                }
                else -> value
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("参数值 '$value' 无法转换为目标类型 ${targetType.classifier}", e)
        }
    }
    
    /**
     * 手动注册工具（用于不使用注解的场景）
     */
    protected fun registerTool(
        name: String,
        description: String,
        parameterSchema: Map<String, ParameterInfo>? = null,
        handler: suspend (Map<String, Any>) -> Any
    ) {
        val toolHandler = ToolHandler(
            name = name,
            description = description,
            parameterSchema = parameterSchema,
            handler = handler
        )
        
        registeredTools[name] = toolHandler
        logger.info("🔧 手动注册工具: $name - $description")
    }
    
    /**
     * 手动注册工具（兼容旧版本 ParameterType）
     */
    protected fun registerToolWithTypes(
        name: String,
        description: String,
        parameterTypes: Map<String, ParameterType>? = null,
        handler: suspend (Map<String, Any>) -> Any
    ) {
        val parameterSchema = parameterTypes?.mapValues { (_, type) ->
            ParameterInfo(type = type)
        }

        registerTool(name, description, parameterSchema, handler)
    }

    /**
     * 手动注册工具（支持完整 JSON Schema）
     *
     * 使用示例：
     * ```kotlin
     * registerToolWithSchema(
     *     name = "AskUserQuestion",
     *     description = "向用户询问问题",
     *     inputSchema = mapOf(
     *         "type" to "object",
     *         "properties" to mapOf(
     *             "questions" to mapOf(
     *                 "type" to "array",
     *                 "description" to "问题列表",
     *                 "items" to mapOf(
     *                     "type" to "object",
     *                     "properties" to mapOf(
     *                         "question" to mapOf("type" to "string"),
     *                         "header" to mapOf("type" to "string"),
     *                         "options" to mapOf("type" to "array")
     *                     ),
     *                     "required" to listOf("question", "header", "options")
     *                 )
     *             )
     *         ),
     *         "required" to listOf("questions")
     *     )
     * ) { arguments -> ... }
     * ```
     */
    protected fun registerToolWithSchema(
        name: String,
        description: String,
        inputSchema: Map<String, Any>,
        handler: suspend (Map<String, Any>) -> Any
    ) {
        val toolHandler = ToolHandlerWithSchema(
            name = name,
            description = description,
            inputSchema = inputSchema,
            handler = handler
        )

        registeredTools[name] = toolHandler
        logger.info("🔧 手动注册工具(完整Schema): $name - $description")
    }

    /**
     * 从 Schema 中注册工具（自动提取 description）
     *
     * Schema 中应包含 "description" 字段，如果没有则使用空字符串。
     * 此方法会自动从 inputSchema 中提取 description，避免重复定义。
     *
     * 使用示例：
     * ```kotlin
     * registerToolFromSchema("CodeSearch", codeSearchTool.getInputSchema()) { arguments ->
     *     codeSearchTool.execute(arguments)
     * }
     * ```
     */
    protected fun registerToolFromSchema(
        name: String,
        inputSchema: Map<String, Any>,
        handler: suspend (Map<String, Any>) -> Any
    ) {
        // 从 schema 中提取 description
        val description = inputSchema["description"] as? String ?: ""

        val toolHandler = ToolHandlerWithSchema(
            name = name,
            description = description,
            inputSchema = inputSchema,
            handler = handler
        )

        registeredTools[name] = toolHandler
        logger.info("🔧 注册工具(从Schema): $name - $description")
    }

    /**
     * 实现 McpServer.listTools()
     */
    override suspend fun listTools(): List<ToolDefinition> {
        ensureInitialized()
        
        return registeredTools.values.map { handler ->
            handler.toDefinition()
        }
    }
    
    /**
     * 实现 McpServer.callTool()
     */
    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): ToolResult {
        ensureInitialized()
        
        val handler = registeredTools[toolName]
            ?: return ToolResult.error("工具 '$toolName' 未找到")
        
        return try {
            logger.info("🎯 调用工具: $toolName, 参数: $arguments")
            val result = handler.handler(arguments)
            
            when (result) {
                is ToolResult -> result
                Unit -> ToolResult.success("操作完成")
                is String -> ToolResult.success(result)  // 显式匹配 String 以调用正确的重载
                else -> ToolResult.success(result)
            }
        } catch (e: Exception) {
            logger.error("❌ 工具 '$toolName' 执行失败: ${e.message}")
            ToolResult.error("工具执行失败: ${e.message}")
        }
    }
    
    /**
     * 获取工具统计信息
     */
    fun getToolsInfo(): Map<String, Any> {
        return mapOf(
            "server_name" to name,
            "server_version" to version,
            "description" to description,
            "tools_count" to registeredTools.size,
            "tools" to registeredTools.keys.toList(),
            "initialized" to initialized
        )
    }
}