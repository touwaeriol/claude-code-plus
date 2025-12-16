package com.asakii.claude.agent.sdk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.test.Ignore

/**
 * 测试 Shell 命令执行
 * 验证通过 login shell 执行命令是否能正确加载环境变量
 */
class ShellCommandTest {

    @Test
    fun `test shell command execution with environment variables`() = runBlocking {
        println("\n========== 测试 Shell 命令执行和环境变量 ==========")

        // 检测用户的默认 shell
        val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
        println("🐚 检测到默认 shell: $defaultShell")

        // 构建一个简单的测试命令：检查 node 和 ANTHROPIC_API_KEY
        val testCommand = """
            echo "=== Environment Test ==="
            echo "Node path: $(which node)"
            echo "Node version: $(node --version 2>&1 || echo 'not found')"
            echo "Claude path: $(which claude)"
            if [ -n "${'$'}ANTHROPIC_API_KEY" ]; then
                echo "API Key: Found (${'$'}{ANTHROPIC_API_KEY:0:15}...)"
            else
                echo "API Key: NOT FOUND"
            fi
            echo "PATH: ${'$'}PATH"
        """.trimIndent()

        val processBuilder = ProcessBuilder(
            defaultShell,
            "-l",  // login shell
            "-c",
            testCommand
        )

        println("\n📋 执行命令:")
        println("   $defaultShell -l -c '$testCommand'")
        println()

        val process = processBuilder.start()

        // 读取输出
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readText()
        }

        // 读取错误输出
        val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
            reader.readText()
        }

        val exitCode = process.waitFor()

        println("📤 输出:")
        println(output)

        if (errorOutput.isNotEmpty()) {
            println("⚠️ 错误输出:")
            println(errorOutput)
        }

        println("✅ 退出码: $exitCode")

        // 验证
        assertEquals(0, exitCode, "Shell 命令应该成功执行")
        assertTrue(output.contains("Environment Test"), "应该包含测试标记")

        // 检查是否找到了关键环境变量
        val hasNode = output.contains("node") && !output.contains("not found")
        val hasApiKey = output.contains("API Key: Found")

        println("\n🔍 检查结果:")
        println("   Node: ${if (hasNode) "✅ 找到" else "❌ 未找到"}")
        println("   API Key: ${if (hasApiKey) "✅ 找到" else "❌ 未找到"}")

        assertTrue(hasNode, "应该能找到 node 命令（说明 PATH 已正确加载）")
        assertTrue(hasApiKey, "应该能找到 ANTHROPIC_API_KEY（说明环境变量已正确加载）")

        println("\n✅ Shell 环境变量测试通过")
    }

    @Test
    fun `test node command directly`() = runBlocking {
        println("\n========== 测试直接执行 node 命令 ==========")

        val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
        val testCommand = "node --version"

        val processBuilder = ProcessBuilder(
            defaultShell,
            "-l",
            "-c",
            testCommand
        )

        println("📋 执行命令: $defaultShell -l -c '$testCommand'")

        val process = processBuilder.start()

        val output = BufferedReader(InputStreamReader(process.inputStream)).use {
            it.readText().trim()
        }

        val exitCode = process.waitFor()

        println("📤 输出: $output")
        println("✅ 退出码: $exitCode")

        assertEquals(0, exitCode, "node --version 应该成功执行")
        assertTrue(output.startsWith("v"), "输出应该是版本号（如 v24.2.0）")

        println("✅ Node 命令测试通过")
    }

    @Test
    @Ignore("仅用于手动调试")
    fun `manual test - check claude cli startup`() = runBlocking {
        println("\n========== 手动测试：检查 Claude CLI 启动 ==========")

        val defaultShell = System.getenv("SHELL") ?: "/bin/bash"

        // 简化的 Claude CLI 命令（只测试启动，不需要完整参数）
        val testCommand = "claude --help"

        val processBuilder = ProcessBuilder(
            defaultShell,
            "-l",
            "-c",
            testCommand
        )

        println("📋 执行命令: $defaultShell -l -c '$testCommand'")

        val process = processBuilder.start()

        // 读取输出（设置超时）
        val outputThread = Thread {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText()
            }
            println("📤 输出:")
            println(output)
        }

        val errorThread = Thread {
            val error = BufferedReader(InputStreamReader(process.errorStream)).use {
                it.readText()
            }
            if (error.isNotEmpty()) {
                println("⚠️ 错误输出:")
                println(error)
            }
        }

        outputThread.start()
        errorThread.start()

        // 等待进程完成（最多 10 秒）
        val completed = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)

        if (!completed) {
            println("❌ 进程超时（10秒），强制终止")
            process.destroyForcibly()
            fail("Claude CLI 启动超时")
        } else {
            val exitCode = process.exitValue()
            println("✅ 退出码: $exitCode")

            outputThread.join()
            errorThread.join()
        }

        println("✅ Claude CLI 测试完成")
    }

    @Test
    fun `test complex shell command with JSON escaping`() = runBlocking {
        println("\n========== 测试复杂 Shell 命令（JSON 转义）==========")

        val defaultShell = System.getenv("SHELL") ?: "/bin/bash"

        // 模拟包含 JSON 的复杂命令
        val jsonData = """{"name":"test","value":"hello world"}"""
        val testCommand = """echo '$jsonData' | node -e "const data = JSON.parse(require('fs').readFileSync(0, 'utf-8')); console.log(data.name);""""

        val processBuilder = ProcessBuilder(
            defaultShell,
            "-l",
            "-c",
            testCommand
        )

        println("📋 执行命令:")
        println("   $testCommand")

        val process = processBuilder.start()

        val output = BufferedReader(InputStreamReader(process.inputStream)).use {
            it.readText().trim()
        }

        val exitCode = process.waitFor()

        println("📤 输出: $output")
        println("✅ 退出码: $exitCode")

        assertEquals(0, exitCode, "复杂命令应该成功执行")
        assertEquals("test", output, "应该正确解析 JSON 并输出 'test'")

        println("✅ JSON 转义测试通过")
    }
}
