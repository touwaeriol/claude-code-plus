package com.asakii.server.logging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 延迟格式化的日志消息包装器
 *
 * 用于将消息格式化延迟到日志线程中执行，避免阻塞工作线程。
 * 配合 Logback AsyncAppender 使用，toString() 会在异步日志线程中被调用。
 *
 * 使用方式：
 * ```kotlin
 * logger.info { LazyLogMessage { formatMyMessage(message) } }
 * ```
 */
class LazyLogMessage(private val supplier: () -> String) {
    override fun toString(): String = supplier()
}

/**
 * 延迟 JSON 序列化的日志消息包装器
 *
 * 自动将对象序列化为 JSON 字符串，格式化在日志线程中执行。
 *
 * 使用方式：
 * ```kotlin
 * logger.info { LazyJsonMessage(myObject) }
 * ```
 */
class LazyJsonMessage<T>(
    private val value: T,
    private val serializer: (T) -> String
) {
    override fun toString(): String = serializer(value)

    companion object {
        @PublishedApi
        internal val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * 使用默认 JSON 序列化器
         */
        inline fun <reified T> of(value: T): LazyJsonMessage<T> {
            return LazyJsonMessage(value) { json.encodeToString(it) }
        }
    }
}

/**
 * 延迟格式化的日志消息构建器
 *
 * 提供更灵活的日志消息构建方式
 */
class LazyLogBuilder {
    private val parts = mutableListOf<() -> String>()

    fun append(value: String): LazyLogBuilder {
        parts.add { value }
        return this
    }

    fun append(supplier: () -> String): LazyLogBuilder {
        parts.add(supplier)
        return this
    }

    fun appendJson(value: Any?): LazyLogBuilder {
        parts.add { value?.toString() ?: "null" }
        return this
    }

    override fun toString(): String = parts.joinToString("") { it() }
}

/**
 * 创建延迟格式化的日志消息
 */
fun lazyLog(supplier: () -> String): LazyLogMessage = LazyLogMessage(supplier)
