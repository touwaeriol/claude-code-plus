package com.claudecodeplus.core.types

import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * 应用程序统一错误类型
 * 提供结构化的错误分类和处理
 */
sealed class AppError(
    message: String, 
    cause: Throwable? = null,
    val errorCode: String = ""
) : Exception(message, cause) {
    
    /**
     * 网络相关错误
     */
    class NetworkError(
        message: String, 
        cause: Throwable? = null,
        val statusCode: Int? = null
    ) : AppError(message, cause, "NETWORK_ERROR")
    
    /**
     * 数据解析错误
     */
    class ParseError(
        message: String, 
        cause: Throwable? = null,
        val rawData: String? = null
    ) : AppError(message, cause, "PARSE_ERROR")
    
    /**
     * 配置相关错误
     */
    class ConfigError(
        message: String, 
        cause: Throwable? = null
    ) : AppError(message, cause, "CONFIG_ERROR")
    
    /**
     * 会话管理错误
     */
    class SessionError(
        message: String, 
        cause: Throwable? = null,
        val sessionId: String? = null
    ) : AppError(message, cause, "SESSION_ERROR")
    
    /**
     * 文件操作错误
     */
    class FileError(
        message: String, 
        cause: Throwable? = null,
        val filePath: String? = null
    ) : AppError(message, cause, "FILE_ERROR")
    
    /**
     * CLI执行错误
     */
    class CliError(
        message: String, 
        cause: Throwable? = null,
        val command: String? = null,
        val exitCode: Int? = null
    ) : AppError(message, cause, "CLI_ERROR")
    
    /**
     * 验证错误
     */
    class ValidationError(
        message: String, 
        val field: String? = null
    ) : AppError(message, null, "VALIDATION_ERROR")
    
    /**
     * 未知错误
     */
    class UnknownError(
        message: String, 
        cause: Throwable? = null
    ) : AppError(message, cause, "UNKNOWN_ERROR")
    
    companion object {
        /**
         * 从异常创建对应的AppError
         */
        fun fromException(exception: Throwable): AppError {
            return when (exception) {
                is AppError -> exception
                is SerializationException -> ParseError("JSON序列化失败: ${exception.message}", exception)
                is IOException -> when {
                    exception.message?.contains("network", ignoreCase = true) == true -> 
                        NetworkError("网络连接失败: ${exception.message}", exception)
                    exception.message?.contains("file", ignoreCase = true) == true -> 
                        FileError("文件操作失败: ${exception.message}", exception)
                    else -> FileError("IO操作失败: ${exception.message}", exception)
                }
                is IllegalArgumentException -> ValidationError("参数验证失败: ${exception.message}")
                is IllegalStateException -> SessionError("状态异常: ${exception.message}", exception)
                else -> UnknownError("未知错误: ${exception.message}", exception)
            }
        }
    }
    
    /**
     * 获取用户友好的错误消息
     */
    fun getUserMessage(): String {
        return when (this) {
            is NetworkError -> "网络连接失败，请检查网络设置"
            is ParseError -> "数据解析失败，请重试"
            is ConfigError -> "配置错误，请检查设置"
            is SessionError -> "会话异常，请重新启动会话"
            is FileError -> "文件操作失败，请检查文件权限"
            is CliError -> "命令执行失败，请检查Claude CLI安装"
            is ValidationError -> "输入验证失败：$message"
            is UnknownError -> "发生未知错误，请联系开发者"
        }
    }
}