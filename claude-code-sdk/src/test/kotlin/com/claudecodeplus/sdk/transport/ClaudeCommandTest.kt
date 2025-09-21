package com.claudecodeplus.sdk.transport

import org.junit.jupiter.api.Test
import java.io.File

/**
 * 测试Claude命令在不同平台的执行方式
 */
class ClaudeCommandTest {

    @Test
    fun testClaudeCommandExecution() {
        println("========== Claude命令执行测试 ==========")
        println("操作系统: ${System.getProperty("os.name")}")
        println("操作系统版本: ${System.getProperty("os.version")}")
        println("用户目录: ${System.getProperty("user.home")}")
        println()

        // 测试1: 使用where/which查找claude
        testFindClaudeWithWhich()

        // 测试2: 直接执行claude命令
        testDirectClaudeExecution()

        // 测试3: 测试claude --version
        testClaudeVersion()

        // 测试4: 列出npm全局安装目录下的claude相关文件
        listNpmGlobalClaudeFiles()
    }

    private fun testFindClaudeWithWhich() {
        println("--- 测试1: 使用where/which查找claude ---")
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val whichCommand = if (isWindows) "where" else "which"

        try {
            val process = ProcessBuilder(whichCommand, "claude").start()
            val result = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            println("命令: $whichCommand claude")
            println("退出码: $exitCode")
            if (exitCode == 0 && result.isNotEmpty()) {
                println("找到claude路径:")
                result.lines().forEach { line ->
                    println("  - $line")
                    // 检查文件是否存在
                    val file = File(line)
                    if (file.exists()) {
                        println("    文件存在: ✓ (大小: ${file.length()} bytes)")
                    }
                }
            } else {
                println("未找到claude命令")
            }
        } catch (e: Exception) {
            println("执行失败: ${e.message}")
        }
        println()
    }

    private fun testDirectClaudeExecution() {
        println("--- 测试2: 直接执行claude命令 ---")

        // 方案A: 直接使用"claude"
        println("方案A: ProcessBuilder(\"claude\")")
        try {
            val process = ProcessBuilder("claude").start()
            // 立即关闭，只测试是否能启动
            process.destroyForcibly()
            println("  结果: 成功启动 ✓")
        } catch (e: Exception) {
            println("  结果: 启动失败 - ${e.message}")
        }

        // 方案B: Windows下显式使用claude.cmd
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        if (isWindows) {
            println("方案B: ProcessBuilder(\"claude.cmd\") [仅Windows]")
            try {
                val process = ProcessBuilder("claude.cmd").start()
                process.destroyForcibly()
                println("  结果: 成功启动 ✓")
            } catch (e: Exception) {
                println("  结果: 启动失败 - ${e.message}")
            }
        }

        // 方案C: 通过cmd /c执行
        if (isWindows) {
            println("方案C: ProcessBuilder(\"cmd\", \"/c\", \"claude\") [仅Windows]")
            try {
                val process = ProcessBuilder("cmd", "/c", "claude").start()
                process.destroyForcibly()
                println("  结果: 成功启动 ✓")
            } catch (e: Exception) {
                println("  结果: 启动失败 - ${e.message}")
            }
        }
        println()
    }

    private fun testClaudeVersion() {
        println("--- 测试3: 执行claude --version ---")

        try {
            val process = ProcessBuilder("claude", "--version").start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            println("命令: claude --version")
            println("退出码: $exitCode")
            if (output.isNotEmpty()) {
                println("标准输出:")
                println(output.trim())
            }
            if (error.isNotEmpty()) {
                println("错误输出:")
                println(error.trim())
            }
        } catch (e: Exception) {
            println("执行失败: ${e.message}")
            println("错误类型: ${e.javaClass.simpleName}")
        }
        println()
    }

    private fun listNpmGlobalClaudeFiles() {
        println("--- 测试4: npm全局目录中的claude文件 ---")

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val npmDir = if (isWindows) {
            val appData = System.getenv("APPDATA")
            if (appData != null) {
                File(appData, "npm")
            } else {
                File(System.getProperty("user.home"), "AppData\\Roaming\\npm")
            }
        } else {
            File("/usr/local/bin")
        }

        println("npm全局目录: ${npmDir.absolutePath}")

        if (npmDir.exists()) {
            val claudeFiles = npmDir.listFiles { file ->
                file.name.startsWith("claude")
            }

            if (!claudeFiles.isNullOrEmpty()) {
                println("找到以下claude相关文件:")
                claudeFiles.forEach { file ->
                    val type = when {
                        file.isDirectory -> "目录"
                        file.name.endsWith(".cmd") -> "CMD批处理"
                        file.name.endsWith(".ps1") -> "PowerShell脚本"
                        file.canExecute() -> "可执行文件"
                        else -> "文件"
                    }
                    println("  - ${file.name} [$type] (${file.length()} bytes)")
                }
            } else {
                println("未找到claude相关文件")
            }
        } else {
            println("npm目录不存在: ${npmDir.absolutePath}")
        }
        println()
    }

    @Test
    fun testProcessBuilderBehavior() {
        println("========== ProcessBuilder行为测试 ==========")

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        // 测试ProcessBuilder如何处理命令
        println("测试ProcessBuilder在${if (isWindows) "Windows" else "Unix"}系统上的行为:")
        println()

        // 测试已知存在的命令
        val testCommands = if (isWindows) {
            listOf("cmd", "where", "echo")
        } else {
            listOf("sh", "which", "echo")
        }

        testCommands.forEach { cmd ->
            println("测试命令: $cmd")
            try {
                val pb = ProcessBuilder(cmd, "--help")
                pb.redirectErrorStream(true)
                val process = pb.start()
                process.destroyForcibly()
                println("  ✓ 命令可执行")
            } catch (e: Exception) {
                println("  ✗ 执行失败: ${e.message}")
            }
        }
        println()

        // 测试PATH环境变量
        println("PATH环境变量:")
        val path = System.getenv("PATH")
        path.split(File.pathSeparator).forEach { dir ->
            if (dir.contains("npm", ignoreCase = true) ||
                dir.contains("node", ignoreCase = true)) {
                println("  - $dir [Node.js/npm相关]")
                // 检查该目录下是否有claude文件
                val dirFile = File(dir)
                if (dirFile.exists()) {
                    val claudeFiles = dirFile.listFiles { f ->
                        f.name.startsWith("claude", ignoreCase = true)
                    }
                    claudeFiles?.forEach { f ->
                        println("    找到: ${f.name}")
                    }
                }
            }
        }
    }
}