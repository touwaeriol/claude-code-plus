package com.claudecodeplus.core

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Logback 配置器
 * 
 * 支持加载用户自定义的 logback 配置文件。
 * 如果用户在 ~/.claude-code-plus/logback.xml 有自定义配置，
 * 则使用用户配置替代默认配置。
 */
object LogbackConfigurator {
    
    private val logger = mu.KotlinLogging.logger {}
    private val USER_CONFIG_PATH: Path = Paths.get(System.getProperty("user.home"), ".claude-code-plus", "logback.xml")
    
    /**
     * 初始化日志配置
     * 
     * 检查是否存在用户自定义配置文件，如果存在则加载。
     * 这个方法应该在应用启动时尽早调用。
     */
    fun initialize() {
        if (Files.exists(USER_CONFIG_PATH)) {
            try {
                loadUserConfig()
                logger.info { "已加载用户自定义日志配置: $USER_CONFIG_PATH" }
            } catch (e: Exception) {
                logger.error(e) { "加载用户日志配置失败，使用默认配置" }
            }
        } else {
            // 使用默认配置（classpath 中的 logback.xml）
            logger.info { "使用默认日志配置" }
            // 确保日志目录存在
            ensureLogDirectoryExists()
        }
    }
    
    /**
     * 加载用户自定义配置
     */
    private fun loadUserConfig() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        
        try {
            val configurator = JoranConfigurator()
            configurator.context = context
            // 重置现有配置
            context.reset()
            // 加载新配置
            configurator.doConfigure(USER_CONFIG_PATH.toFile())
        } catch (je: JoranException) {
            throw RuntimeException("加载日志配置失败", je)
        }
    }
    
    /**
     * 确保日志目录存在
     */
    private fun ensureLogDirectoryExists() {
        val logDir = Paths.get(System.getProperty("user.home"), ".claude-code-plus", "logs")
        if (!Files.exists(logDir)) {
            try {
                Files.createDirectories(logDir)
                logger.debug { "创建日志目录: $logDir" }
            } catch (e: Exception) {
                logger.error(e) { "创建日志目录失败: $logDir" }
            }
        }
    }
    
    /**
     * 动态设置日志级别
     * 
     * @param loggerName 日志器名称，如 "com.claudecodeplus"
     * @param level 日志级别，如 "DEBUG", "INFO", "WARN", "ERROR"
     */
    fun setLogLevel(loggerName: String, level: String) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger(loggerName)
        
        val logLevel = when (level.uppercase()) {
            "TRACE" -> ch.qos.logback.classic.Level.TRACE
            "DEBUG" -> ch.qos.logback.classic.Level.DEBUG
            "INFO" -> ch.qos.logback.classic.Level.INFO
            "WARN", "WARNING" -> ch.qos.logback.classic.Level.WARN
            "ERROR" -> ch.qos.logback.classic.Level.ERROR
            else -> ch.qos.logback.classic.Level.INFO
        }
        
        logger.level = logLevel
        this.logger.info { "设置日志级别: $loggerName = $level" }
    }
    
    /**
     * 创建示例配置文件
     * 
     * 为用户生成一个示例 logback.xml 配置文件
     */
    fun createSampleConfig() {
        val sampleConfig = """<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Claude Code Plus 日志配置示例 -->
    <!-- 您可以修改此文件来自定义日志行为 -->
    
    <!-- 日志文件路径 -->
    <property name="LOG_HOME" value="${'$'}{user.home}/.claude-code-plus/logs" />
    
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 文件输出 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${'$'}{LOG_HOME}/claude-code-plus.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${'$'}{LOG_HOME}/claude-code-plus.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 根日志级别 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </root>
    
    <!-- 特定包的日志级别 -->
    <logger name="com.claudecodeplus" level="DEBUG" />
</configuration>
"""
        
        try {
            Files.createDirectories(USER_CONFIG_PATH.parent)
            Files.write(USER_CONFIG_PATH, sampleConfig.toByteArray())
            logger.info { "已创建示例日志配置文件: $USER_CONFIG_PATH" }
        } catch (e: Exception) {
            logger.error(e) { "创建示例配置文件失败" }
        }
    }
}