package com.asakii.server.mcp.schema

import com.asakii.server.mcp.UserInteractionMcpServer
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 工具 Schema 加载器
 *
 * 提供工具的 JSON Schema 定义。
 * 支持通过 registerSchemaSource 注册额外的 Schema 来源。
 */
object ToolSchemaLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedSchemas: MutableMap<String, Map<String, Any>>? = null
    private val additionalSources = mutableListOf<SchemaSource>()

    /**
     * Schema 来源接口
     */
    interface SchemaSource {
        fun loadSchemas(): Map<String, Map<String, Any>>
    }

    /**
     * 注册额外的 Schema 来源
     */
    fun registerSchemaSource(source: SchemaSource) {
        additionalSources.add(source)
        invalidateCache()
    }

    /**
     * 获取指定工具的 Schema
     */
    fun getSchema(toolName: String): Map<String, Any> {
        return getAllSchemas()[toolName] ?: run {
            logger.warn { "Schema not found for tool: $toolName" }
            emptyMap()
        }
    }

    /**
     * 获取所有工具的 Schema
     */
    fun getAllSchemas(): Map<String, Map<String, Any>> {
        cachedSchemas?.let { return it }

        val schemas = mutableMapOf<String, Map<String, Any>>()

        // 1. 加载内置 Schema
        schemas.putAll(loadBuiltInSchemas())

        // 2. 加载额外来源的 Schema
        for (source in additionalSources) {
            try {
                schemas.putAll(source.loadSchemas())
            } catch (e: Exception) {
                logger.error(e) { "Failed to load schemas from additional source" }
            }
        }

        cachedSchemas = schemas
        logger.info { "Loaded ${schemas.size} tool schemas" }
        return schemas
    }

    /**
     * 刷新缓存
     */
    fun invalidateCache() {
        cachedSchemas = null
    }

    /**
     * 加载内置工具 Schema
     */
    private fun loadBuiltInSchemas(): Map<String, Map<String, Any>> {
        return mapOf(
            "AskUserQuestion" to UserInteractionMcpServer.ASK_USER_QUESTION_SCHEMA
        )
    }

    /**
     * 从 JSON 字符串解析 Schema
     */
    fun parseSchemaJson(content: String): Map<String, Map<String, Any>> {
        return try {
            val jsonObject = json.parseToJsonElement(content).jsonObject
            jsonObject.mapValues { (_, value) ->
                jsonElementToMap(value.jsonObject)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse schema JSON" }
            emptyMap()
        }
    }

    private fun jsonElementToMap(obj: JsonObject): Map<String, Any> {
        return obj.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    private fun jsonElementToAny(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonObject -> jsonElementToMap(element)
            is JsonNull -> ""
        }
    }
}
