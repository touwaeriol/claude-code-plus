package com.claudecodeplus.plugin

import com.claudecodeplus.core.SimpleLogger
import com.intellij.openapi.diagnostic.Logger

/**
 * 使用 IntelliJ Logger 的实现
 */
class IdeaLogger(private val logger: Logger) : SimpleLogger {
    override fun debug(message: String) {
        logger.debug(message)
    }
    
    override fun info(message: String) {
        logger.info(message)
    }
    
    override fun warn(message: String) {
        logger.warn(message)
    }
    
    override fun warn(message: String, throwable: Throwable) {
        logger.warn(message, throwable)
    }
    
    override fun error(message: String, throwable: Throwable) {
        logger.error(message, throwable)
    }
}

/**
 * IntelliJ 平台的 Logger 工厂扩展
 */
object IdeaLoggerFactory {
    fun getLogger(clazz: Class<*>): SimpleLogger {
        return IdeaLogger(Logger.getInstance(clazz))
    }
    
    fun getLogger(name: String): SimpleLogger {
        return IdeaLogger(Logger.getInstance(name))
    }
}