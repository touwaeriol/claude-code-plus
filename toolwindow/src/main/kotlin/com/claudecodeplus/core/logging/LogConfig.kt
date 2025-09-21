package com.claudecodeplus.core.logging

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 日志配置数据类
 */
data class LogConfigState(
    var enableDebugLog: Boolean = false,
    var enableConsoleOutput: Boolean = false,
    var logLevel: LogLevel = LogLevel.INFO,
    var enabledCategories: MutableSet<String> = mutableSetOf()
)

/**
 * 日志级别枚举
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * 日志配置服务
 * 提供全局的日志配置管理
 */
@State(
    name = "ClaudeCodePlusLogConfig",
    storages = [Storage("claude-code-plus-log.xml")]
)
@Service
class LogConfigService : PersistentStateComponent<LogConfigState> {
    private var state = LogConfigState()

    override fun getState(): LogConfigState = state

    override fun loadState(state: LogConfigState) {
        this.state = state
    }

    fun isDebugEnabled(): Boolean = state.enableDebugLog

    fun setDebugEnabled(enabled: Boolean) {
        state.enableDebugLog = enabled
        // 更新 Logger 配置
        if (LoggerProvider.logger is ConsoleLogger) {
            (LoggerProvider.logger as ConsoleLogger).debugEnabled = enabled
        }
    }

    fun getLogLevel(): LogLevel = state.logLevel

    fun setLogLevel(level: LogLevel) {
        state.logLevel = level
    }

    fun isCategoryEnabled(category: String): Boolean {
        return state.enabledCategories.contains(category)
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        if (enabled) {
            state.enabledCategories.add(category)
        } else {
            state.enabledCategories.remove(category)
        }
    }

    fun isConsoleOutputEnabled(): Boolean = state.enableConsoleOutput

    fun setConsoleOutputEnabled(enabled: Boolean) {
        state.enableConsoleOutput = enabled
    }

    companion object {
        fun getInstance(): LogConfigService {
            return ApplicationManager.getApplication().getService(LogConfigService::class.java)
        }
    }
}

/**
 * 日志分类常量
 */
object LogCategory {
    const val UI = "UI"
    const val SDK = "SDK"
    const val MESSAGE = "Message"
    const val SESSION = "Session"
    const val TOOL = "Tool"
    const val CONFIG = "Config"
    const val DEBUG = "Debug"
}