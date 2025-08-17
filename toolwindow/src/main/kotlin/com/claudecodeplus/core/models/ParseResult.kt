package com.claudecodeplus.core.models

/**
 * 消息解析结果
 */
sealed class ParseResult<out T> {
    /**
     * 解析成功
     */
    data class Success<T>(val data: T) : ParseResult<T>()
    
    /**
     * 解析被忽略（非错误，如过滤掉的消息类型）
     */
    data class Ignored(val reason: String) : ParseResult<Nothing>()
    
    /**
     * 解析失败
     */
    data class Error(val message: String, val cause: Throwable? = null) : ParseResult<Nothing>()
    
    /**
     * 是否解析成功
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * 是否被忽略
     */
    val isIgnored: Boolean get() = this is Ignored
    
    /**
     * 是否失败
     */
    val isError: Boolean get() = this is Error
    
    /**
     * 获取数据，失败时返回null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * 成功时执行操作
     */
    inline fun onSuccess(action: (T) -> Unit): ParseResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * 失败时执行操作
     */
    inline fun onError(action: (String, Throwable?) -> Unit): ParseResult<T> {
        if (this is Error) action(message, cause)
        return this
    }
    
    /**
     * 被忽略时执行操作
     */
    inline fun onIgnored(action: (String) -> Unit): ParseResult<T> {
        if (this is Ignored) action(reason)
        return this
    }
}

/**
 * 从可能抛出异常的解析操作创建ParseResult
 */
inline fun <T> parseResultOf(action: () -> T): ParseResult<T> {
    return try {
        ParseResult.Success(action())
    } catch (e: Exception) {
        ParseResult.Error("解析失败: ${e.message}", e)
    }
}