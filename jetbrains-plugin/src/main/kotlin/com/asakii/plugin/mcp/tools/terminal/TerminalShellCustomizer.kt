package com.asakii.plugin.mcp.tools.terminal

import com.intellij.openapi.project.Project
import mu.KotlinLogging
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer

private val logger = KotlinLogging.logger {}

/**
 * Shell 命令覆盖管理器
 *
 * 使用 ThreadLocal 在创建终端时临时指定 shell 命令，
 * 避免修改全局设置带来的并发问题。
 */
object ShellCommandOverride {
    private val shellCommandOverride = ThreadLocal<List<String>>()

    /**
     * 设置当前线程的 shell 命令覆盖
     */
    fun set(shellCommand: List<String>) {
        logger.debug { "Setting shell command override: $shellCommand" }
        shellCommandOverride.set(shellCommand)
    }

    /**
     * 获取并清除当前线程的 shell 命令覆盖
     */
    fun getAndClear(): List<String>? {
        val command = shellCommandOverride.get()
        if (command != null) {
            logger.debug { "Getting and clearing shell command override: $command" }
            shellCommandOverride.remove()
        }
        return command
    }

    /**
     * 清除当前线程的 shell 命令覆盖
     */
    fun clear() {
        shellCommandOverride.remove()
    }
}

/**
 * 终端 Shell 自定义器
 *
 * 通过 LocalTerminalCustomizer 扩展点，在终端创建时替换 shell 命令。
 * 这是比修改全局 shellPath 设置更优雅的方案。
 */
class TerminalShellCustomizer : LocalTerminalCustomizer() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun customizeCommandAndEnvironment(
        project: Project,
        workingDirectory: String?,
        command: Array<String>,
        envs: MutableMap<String, String>
    ): Array<String> {
        val override = ShellCommandOverride.getAndClear()

        return if (override != null && override.isNotEmpty()) {
            logger.info { "Customizing shell command: ${command.toList()} -> $override" }
            override.toTypedArray()
        } else {
            command
        }
    }
}
