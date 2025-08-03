package com.claudecodeplus.sdk.session

import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

/**
 * 文件系统错误处理器
 * 
 * 提供统一的文件系统错误处理和重试机制
 */
class FileSystemErrorHandler {
    private val logger = KotlinLogging.logger {}
    
    // 重试配置
    private val retryDelays = listOf(100L, 500L, 1000L, 2000L)
    private val maxRetries = retryDelays.size
    
    /**
     * 执行带重试的文件操作
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        block: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        
        for (attempt in 0..maxRetries) {
            try {
                return Result.success(block())
            } catch (e: Exception) {
                lastException = e
                
                val errorType = classifyError(e)
                logger.debug { "$operation failed (attempt ${attempt + 1}): ${errorType.description}" }
                
                // 对于不可重试的错误，直接返回
                if (!errorType.canRetry || attempt == maxRetries) {
                    break
                }
                
                // 等待后重试
                val delayMs = retryDelays.getOrElse(attempt) { 2000L }
                logger.trace { "Retrying $operation in ${delayMs}ms" }
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("Unknown error in $operation"))
    }
    
    /**
     * 分类错误类型
     */
    private fun classifyError(exception: Exception): FileError {
        return when {
            exception is NoSuchFileException || 
            exception.message?.contains("No such file") == true -> FileError.FileNotFound
            
            exception is AccessDeniedException || 
            exception.message?.contains("Access is denied") == true -> FileError.FileAccessDenied
            
            exception.message?.contains("being used by another process") == true ||
            exception.message?.contains("file is locked") == true -> FileError.FileLocked
            
            exception is IOException && 
            exception.message?.contains("Permission denied") == true -> FileError.FileAccessDenied
            
            exception is IOException -> FileError.IOError(exception.message ?: "Unknown IO error")
            
            else -> FileError.UnknownError(exception.message ?: "Unknown error")
        }
    }
    
    /**
     * 文件错误类型
     */
    sealed class FileError(val description: String, val canRetry: Boolean) {
        object FileNotFound : FileError("文件不存在", false)
        object FileAccessDenied : FileError("文件访问被拒绝", false)
        object FileLocked : FileError("文件被锁定", true)
        data class IOError(val message: String) : FileError("IO错误: $message", true)
        data class UnknownError(val message: String) : FileError("未知错误: $message", true)
    }
}