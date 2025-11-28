package com.asakii.server.logging

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.ConsoleHandler
import java.util.logging.FileHandler
import java.util.logging.Filter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.logging.LogRecord

/**
 * 仅供 StandaloneServer 使用的日志配置。
 *
 * - 所有日志输出到 <project>/.log/server.log
 * - WebSocket 相关日志额外输出到 <project>/.log/ws.log，便于排查 SDK <-> WebSocket 交互
 * - Claude Agent SDK 的日志写入 <project>/.log/claude-agent-sdk.log
 */
object StandaloneLogging {
  private class LoggerPrefixFilter(
    private val prefix: String
  ) : Filter {
    override fun isLoggable(record: LogRecord?): Boolean {
      val loggerName = record?.loggerName ?: return false
      return loggerName.startsWith(prefix)
    }
  }

  fun configure(projectRoot: File) {
    val logDir = projectRoot.toPath().resolve(".log")
    Files.createDirectories(logDir)

    val logManager = LogManager.getLogManager()
    logManager.reset()

    val formatter = SimpleFormatter()

    fun Handler.configure(level: Level = Level.INFO): Handler = apply {
      this.level = level
      this.formatter = formatter
      this.encoding = "UTF-8"
    }

    fun createFileHandler(path: Path, level: Level = Level.INFO): FileHandler =
      FileHandler(path.toAbsolutePath().toString(), true).configure(level) as FileHandler

    val consoleHandler = ConsoleHandler().configure(Level.INFO)
    val fileHandler = createFileHandler(logDir.resolve("server.log"))

    val rootLogger = Logger.getLogger("")
    rootLogger.level = Level.INFO
    rootLogger.addHandler(consoleHandler)
    rootLogger.addHandler(fileHandler)

    // 专用于 WebSocket 流日志
    val wsFileHandler = createFileHandler(logDir.resolve("ws.log")).apply {
      filter = LoggerPrefixFilter("com.asakii.server.WebSocketHandler")
    }
    rootLogger.addHandler(wsFileHandler)
    rootLogger.info("WebSocket logging redirected to ${logDir.resolve("ws.log")}")

    // Claude Agent SDK 日志
    val sdkFileHandler = createFileHandler(logDir.resolve("claude-agent-sdk.log")).apply {
      filter = LoggerPrefixFilter("com.asakii.claude.agent")
    }
    rootLogger.addHandler(sdkFileHandler)
    rootLogger.info("Claude Agent SDK logging redirected to ${logDir.resolve("claude-agent-sdk.log")}")

    println("📝 Logging configured. Server log: ${logDir.resolve("server.log")}")
    println("📝 WebSocket log: ${logDir.resolve("ws.log")}")
    println("📝 Claude Agent SDK log: ${logDir.resolve("claude-agent-sdk.log")}")
  }
}

