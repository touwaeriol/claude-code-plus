package com.asakii.plugin.config

import java.io.File

/**
 * 插件配置
 */
object PluginConfig {

    /**
     * 检查 claude 命令是否可用
     * @return Pair<是否可用, 详细信息>
     */
    fun checkClaudeCommand(): Pair<Boolean, String> {
        return try {
            val osName = System.getProperty("os.name").lowercase()
            val command = if (osName.contains("windows")) {
                listOf("cmd", "/c", "claude", "--version")
            } else {
                listOf("/bin/bash", "-c", "claude --version")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Pair(true, "Claude CLI 可用: ${output.trim()}")
            } else {
                Pair(false, "Claude CLI 执行失败 (退出码: $exitCode): ${output.trim()}")
            }
        } catch (e: Exception) {
            Pair(false, "无法执行 claude 命令: ${e.message}")
        }
    }
    
    /**
     * 查找项目根目录
     */
    private fun findProjectRoot(): String? {
        // 从当前类的位置向上查找
        val classLocation = PluginConfig::class.java.protectionDomain.codeSource?.location?.path
        if (classLocation != null) {
            var currentDir = File(classLocation).parentFile
            repeat(10) {
                if (currentDir == null) return@repeat
                
                // 检查是否包含 settings.gradle.kts
                if (File(currentDir, "settings.gradle.kts").exists() ||
                    File(currentDir, "settings.gradle").exists()) {
                    return currentDir.absolutePath
                }
                
                // 检查是否包含 cli-wrapper 目录
                if (File(currentDir, "cli-wrapper").isDirectory) {
                    return currentDir.absolutePath
                }
                
                currentDir = currentDir.parentFile
            }
        }
        
        return null
    }
    
    /**
     * 设置环境并检查 claude 命令可用性
     */
    fun setupEnvironment() {
        // 检查 claude 命令是否可用
        val (isAvailable, message) = checkClaudeCommand()

        // 设置系统属性，供 ClaudeCliWrapper 使用
        System.setProperty("claude.command.available", isAvailable.toString())
        System.setProperty("claude.command.check.result", message)

        println("[PluginConfig] Claude 命令检查: $message")

        // 开发模式下，尝试设置项目根目录
        val projectRoot = findProjectRoot()
        if (projectRoot != null) {
            System.setProperty("claude.project.root", projectRoot)
            println("[PluginConfig] 项目根目录: $projectRoot")
        }
    }
}