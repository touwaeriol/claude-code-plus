package com.claudecodeplus.core.types

/**
 * 统一的结果包装类
 * 提供类型安全的错误处理机制
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    
    /**
     * 成功时执行指定操作
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * 失败时执行指定操作
     */
    inline fun onFailure(action: (AppError) -> Unit): Result<T> {
        if (this is Failure) action(error)
        return this
    }
    
    /**
     * 获取数据，失败时返回null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
    
    /**
     * 获取数据，失败时返回默认值
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): @UnsafeVariance T = when (this) {
        is Success -> data
        is Failure -> defaultValue
    }
    
    /**
     * 转换成功时的数据类型
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }
    
    /**
     * 链式调用，成功时执行下一个操作
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }
}

/**
 * 从可能抛出异常的操作创建Result
 */
inline fun <T> resultOf(action: () -> T): Result<T> {
    return try {
        Result.Success(action())
    } catch (e: Exception) {
        Result.Failure(AppError.fromException(e))
    }
}

/**
 * 从挂起函数创建Result
 */
suspend inline fun <T> suspendResultOf(crossinline action: suspend () -> T): Result<T> {
    return try {
        Result.Success(action())
    } catch (e: Exception) {
        Result.Failure(AppError.fromException(e))
    }
}