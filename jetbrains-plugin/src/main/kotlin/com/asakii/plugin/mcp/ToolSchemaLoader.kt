package com.asakii.plugin.mcp

import com.asakii.plugin.utils.ResourceLoader
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 工具 Schema 加载器
 *
 * 从 resources/mcp/schemas/tools.json 加载工具的 JSON Schema 定义
 */
object ToolSchemaLoader {

    private const val SCHEMA_PATH = "mcp/schemas/tools.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedSchemas: Map<String, Map<String, Any>>? = null

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

        val schemas = loadSchemas()
        cachedSchemas = schemas
        return schemas
    }

    /**
     * 刷新缓存
     */
    fun invalidateCache() {
        cachedSchemas = null
    }

    private fun loadSchemas(): Map<String, Map<String, Any>> {
        return try {
            val content = ResourceLoader.loadText(SCHEMA_PATH)
            if (content == null) {
                logger.warn { "Schema file not found: $SCHEMA_PATH" }
                return emptyMap()
            }

            val jsonObject = json.parseToJsonElement(content).jsonObject
            jsonObject.mapValues { (_, value) ->
                jsonElementToMap(value.jsonObject)
            }.also {
                logger.info { "Loaded ${it.size} tool schemas" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load tool schemas" }
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
            else -> element.toString()
        }
    }
}
