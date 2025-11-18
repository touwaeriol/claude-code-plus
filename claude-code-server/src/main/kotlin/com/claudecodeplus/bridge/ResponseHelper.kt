package com.claudecodeplus.bridge

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * FrontendResponse 辅助工具
 * 提供便捷的成功/错误响应创建方法
 */
object ResponseHelper {
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 创建成功响应（任意数据类型）
     */
    inline fun <reified T> success(data: T): FrontendResponse {
        // 将数据序列化为 JsonElement
        val jsonString = json.encodeToString(data)
        val jsonElement = json.parseToJsonElement(jsonString)

        return FrontendResponse(
            success = true,
            data = if (jsonElement is JsonObject) {
                jsonElement.toMap()
            } else {
                mapOf("value" to jsonElement)
            }
        )
    }

    /**
     * 创建错误响应
     */
    fun <T> error(message: String): FrontendResponse {
        return FrontendResponse(
            success = false,
            error = message
        )
    }
}

