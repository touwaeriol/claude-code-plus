package com.claudecodeplus.ui.services

import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 文件系统错误处理器
 * 
 * 提供统一的文件系统操作错误处理机制，包括重试策略和错误分类
 */
class FileSystemErrorHandler {
    private val logger = KotlinLogging.logger {}
    
    /**
     * 文件系统错误分类
     */
    sealed class FileError : Exception() {
        object FileNotFound : FileError()
        object FileAccessDenied : FileError()
        object FileLocked : FileError()
        object DirectoryNotFound : FileError()
        data class IOError(override val cause: Throwable) : FileError()
        data class UnknownError(override val cause: Throwable) : FileError()
        
        override fun toString(): String = when (this) {
            is FileNotFound -> "File not found"
            is FileAccessDenied -> "File access denied"
            is FileLocked -> "File is locked by another process"
            is DirectoryNotFound -> "Directory not found"
            is IOError -> "IO error: ${cause.message}"
            is UnknownError -> "Unknown error: ${cause.message}"
        }
    }
    
    /**
     * 重试策略配置
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelay: Duration = 100.milliseconds,
        val maxDelay: Duration = 2.seconds,
        val backoffFactor: Double = 2.0
    )
    
    /**
     * 文件操作结果
     */
    sealed class OperationResult<out T> {
        data class Success<T>(val value: T) : OperationResult<T>()
        data class Failure(val error: FileError, val attempts: Int) : OperationResult<Nothing>()
    }
    
    /**
     * 安全执行文件操作，带错误处理
     */
    suspend fun <T> safeFileOperation(
        operation: suspend () -> T,
        onError: (FileError) -> T? = { null }
    ): T? {
        return try {
            operation()
        } catch (e: Exception) {
            val fileError = classifyError(e)
            logger.debug { "File operation error: $fileError" }
            onError(fileError)
        }
    }
    
    /**
     * 带重试的文件操作
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig(),
        shouldRetry: (FileError) -> Boolean = ::defaultShouldRetry,
        operation: suspend () -> T
    ): OperationResult<T> {
        var lastError: FileError? = null
        var currentDelay = config.initialDelay
        
        repeat(config.maxAttempts) { attempt ->
            try {
                val result = operation()
                return OperationResult.Success(result)
            } catch (e: Exception) {
                lastError = classifyError(e)
                
                logger.debug { 
                    "Operation failed (attempt ${attempt + 1}/${config.maxAttempts}): $lastError" 
                }
                
                if (attempt < config.maxAttempts - 1 && shouldRetry(lastError!!)) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * config.backoffFactor)
                        .coerceAtMost(config.maxDelay)
                }
            }
        }
        
        return OperationResult.Failure(
            error = lastError ?: FileError.UnknownError(Exception("No error captured")),
            attempts = config.maxAttempts
        )
    }
    
    /**
     * 处理文件锁定的专门方法
     */
    suspend fun <T> handleFileLocked(
        maxWaitTime: Duration = 5.seconds,
        checkInterval: Duration = 100.milliseconds,
        operation: suspend () -> T
    ): T? {
        val startTime = System.currentTimeMillis()
        val maxWaitMillis = maxWaitTime.inWholeMilliseconds
        
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            try {
                return operation()
            } catch (e: Exception) {
                if (classifyError(e) == FileError.FileLocked) {
                    logger.trace { "File locked, waiting..." }
                    delay(checkInterval)
                } else {
                    throw e
                }
            }
        }
        
        logger.warn { "File remained locked after ${maxWaitTime}" }
        return null
    }
    
    /**
     * 错误分类
     */
    fun classifyError(throwable: Throwable): FileError {
        return when (throwable) {
            is FileNotFoundException,
            is NoSuchFileException -> FileError.FileNotFound
            
            is AccessDeniedException -> FileError.FileAccessDenied
            
            is IOException -> {
                when {
                    throwable.message?.contains("being used by another process") == true ||
                    throwable.message?.contains("locked") == true ->
                        FileError.FileLocked
                    
                    throwable.message?.contains("directory") == true ->
                        FileError.DirectoryNotFound
                    
                    else -> FileError.IOError(throwable)
                }
            }
            
            else -> FileError.UnknownError(throwable)
        }
    }
    
    /**
     * 默认的重试判断逻辑
     */
    private fun defaultShouldRetry(error: FileError): Boolean {
        return when (error) {
            is FileError.FileLocked -> true
            is FileError.IOError -> true
            is FileError.FileNotFound -> false
            is FileError.FileAccessDenied -> false
            is FileError.DirectoryNotFound -> false
            is FileError.UnknownError -> false
        }
    }
    
    /**
     * 创建带上下文的错误消息
     */
    fun createErrorMessage(
        error: FileError,
        path: Path? = null,
        operation: String? = null
    ): String {
        val parts = mutableListOf<String>()
        
        operation?.let { parts.add("Failed to $it") }
        path?.let { parts.add("Path: $it") }
        parts.add("Error: $error")
        
        return parts.joinToString(". ")
    }
    
    /**
     * 检查路径是否可访问
     */
    fun checkPathAccessible(path: Path): FileError? {
        return try {
            when {
                !path.toFile().exists() -> FileError.FileNotFound
                !path.toFile().canRead() -> FileError.FileAccessDenied
                else -> null
            }
        } catch (e: Exception) {
            classifyError(e)
        }
    }
    
    companion object {
        /**
         * 快速重试辅助方法
         */
        suspend fun <T> quickRetry(
            times: Int = 3,
            delay: Duration = 100.milliseconds,
            operation: suspend () -> T
        ): T? {
            val handler = FileSystemErrorHandler()
            return when (val result = handler.withRetry(
                RetryConfig(maxAttempts = times, initialDelay = delay),
                operation = operation
            )) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> null
            }
        }
    }
}