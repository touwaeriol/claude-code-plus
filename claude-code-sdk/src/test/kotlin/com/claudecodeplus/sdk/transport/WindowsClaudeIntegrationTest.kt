package com.claudecodeplus.sdk.transport

import com.claudecodeplus.sdk.types.ClaudeCodeOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

/**
 * 集成测试：验证Windows平台下Claude CLI的连接
 */
class WindowsClaudeIntegrationTest {

    @BeforeEach
    fun checkPlatform() {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        Assumptions.assumeTrue(isWindows, "此测试仅在Windows平台运行")

        // 检查claude命令是否可用
        val claudeAvailable = try {
            ProcessBuilder("cmd", "/c", "claude", "--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
        Assumptions.assumeTrue(claudeAvailable, "Claude CLI未安装或不在PATH中")
    }

    @Test
    fun testSubprocessTransportConnectsOnWindows() {
        println("========== Windows平台Claude CLI连接测试 ==========")

        val options = ClaudeCodeOptions(
            cwd = Paths.get(System.getProperty("user.dir"))
        )

        val transport = SubprocessTransport(options, streamingMode = true)

        runBlocking {
            try {
                // 设置超时，防止测试挂起
                withTimeout(10.seconds) {
                    println("正在连接Claude CLI...")
                    transport.connect()

                    // 验证连接状态
                    val isConnected = transport.isConnected()
                    println("连接状态: $isConnected")

                    assert(isConnected) { "SubprocessTransport应该成功连接到Claude CLI" }

                    println("✅ 测试通过：Windows平台成功连接Claude CLI")
                }
            } catch (e: Exception) {
                println("❌ 测试失败: ${e.message}")
                throw e
            } finally {
                // 清理资源
                try {
                    transport.close()
                } catch (e: Exception) {
                    println("关闭transport时出错: ${e.message}")
                }
            }
        }
    }

    @Test
    fun testDirectClaudeExecutionFails() {
        println("========== 验证直接执行claude会失败 ==========")

        try {
            // 直接执行claude（不通过cmd）
            val process = ProcessBuilder("claude", "--version").start()
            process.waitFor()

            println("⚠️ 直接执行claude竟然成功了，这不应该在Windows上发生")
            assert(false) { "预期直接执行claude会失败" }
        } catch (e: java.io.IOException) {
            println("✅ 预期的失败：${e.message}")
            assert(e.message?.contains("CreateProcess error") == true ||
                   e.message?.contains("cannot find") == true ||
                   e.message?.contains("系统找不到") == true) {
                "错误信息应该表明找不到文件"
            }
        }
    }

    @Test
    fun testCmdClaudeExecutionSucceeds() {
        println("========== 验证通过cmd执行claude会成功 ==========")

        val process = ProcessBuilder("cmd", "/c", "claude", "--version").start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        println("退出码: $exitCode")
        println("输出: $output")

        assert(exitCode == 0) { "通过cmd执行claude应该返回0" }
        assert(output.contains("Claude Code") || output.contains("1.0")) {
            "输出应该包含版本信息"
        }

        println("✅ 测试通过：通过cmd成功执行claude")
    }
}