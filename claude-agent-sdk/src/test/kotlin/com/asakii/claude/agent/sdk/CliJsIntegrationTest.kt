package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 测试使用 cli.js (Node.js 方式) 启动 Claude Agent 的集成测试
 *
 * 验证点:
 * 1. SDK 能正确找到 bundled cli.js
 * 2. SDK 能找到系统的 Node.js
 * 3. 能成功启动 Claude 进程 (node cli.js)
 * 4. 能发送查询并接收响应
 * 5. 流式消息工作正常
 */
class CliJsIntegrationTest {

    private lateinit var client: ClaudeCodeSdkClient
    private lateinit var sessionId: String

    @BeforeEach
    fun setUp() {
        sessionId = "test-clijs-${UUID.randomUUID()}"
        client = ClaudeCodeSdkClient(
            ClaudeAgentOptions(
                model = "claude-sonnet-4-20250514",
                maxTurns = 1,
                includePartialMessages = true  // 启用流式输出
            )
        )
        runBlocking { client.connect() }
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            if (this@CliJsIntegrationTest::client.isInitialized) {
                client.disconnect()
            }
        }
    }

    @Test
    fun `test Node_js can be found on system`() {
        val nodeExecutable = findNodeExecutable()
        assertNotNull(nodeExecutable, "❌ 未找到 Node.js！请确保系统已安装 Node.js")

        println("✅ 找到 Node.js: $nodeExecutable")

        // 验证 Node.js 版本
        val version = getNodeVersion()
        assertNotNull(version, "无法获取 Node.js 版本")
        println("   Node.js 版本: $version")

        // 检查版本是否符合要求 (>= 18.0.0)
        val majorVersion = version.split(".").firstOrNull()?.toIntOrNull() ?: 0
        assertTrue(majorVersion >= 18, "Node.js 版本过低，需要 >= 18.0.0，当前: $version")
        println("   ✅ 版本符合要求 (>= 18.0.0)")
    }

    @Test
    fun `test bundled cli_js can be found`() {
        val cliJsPath = findBundledCliJs()
        assertNotNull(cliJsPath, "❌ 未找到 bundled cli.js！请先运行 ./gradlew downloadCli")

        println("✅ 找到 bundled cli.js: $cliJsPath")

        val cliJsFile = File(cliJsPath)
        assertTrue(cliJsFile.exists(), "cli.js 文件不存在")

        val sizeMB = cliJsFile.length() / (1024.0 * 1024.0)
        println("   大小: ${String.format("%.2f", sizeMB)} MB")
        assertTrue(sizeMB > 5.0, "cli.js 文件太小，可能未正确下载 (${String.format("%.2f", sizeMB)} MB)")
    }

    @Test
    fun `test SDK can start Claude with cli_js`() = runBlocking {
        println("\n=== 测试使用 cli.js 启动 Claude ===")

        // 检查前置条件
        val nodeExe = findNodeExecutable()
        val cliJs = findBundledCliJs()

        assertNotNull(nodeExe, "Node.js 未找到")
        assertNotNull(cliJs, "cli.js 未找到")

        println("📦 Node.js: $nodeExe")
        println("📦 CLI.js: $cliJs")

        // 发送简单查询
        println("\n发送查询: '请用一句话解释什么是 Kotlin'")
        client.query(
            prompt = "请用一句话解释什么是 Kotlin",
            sessionId = sessionId
        )

        var assistantMessageReceived = false
        var resultMessageReceived = false

        // 收集响应
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    assistantMessageReceived = true
                    println("✅ 收到 AssistantMessage (CLI 正常工作)")
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("   文本: ${block.text.take(100)}...")
                            is ThinkingBlock -> println("   思考: ${block.thinking.take(50)}...")
                            else -> println("   ${block::class.simpleName}")
                        }
                    }
                }

                is ResultMessage -> {
                    resultMessageReceived = true
                    println("✅ 收到 ResultMessage:")
                    println("   - 状态: ${if (message.isError) "Error" else "Success"}")
                    println("   - Turns: ${message.numTurns}")
                    println("   - 耗时: ${message.durationMs}ms")
                }

                is StreamEvent -> {
                    println("📡 StreamEvent: ${message.event}")
                }

                else -> {}
            }
        }

        // 验证结果
        assertTrue(assistantMessageReceived, "❌ 未收到 AssistantMessage！CLI 可能未正常工作")
        assertTrue(resultMessageReceived, "应该收到 ResultMessage")

        println("\n✅ 测试通过：SDK 成功使用 cli.js 启动 Claude！")
    }

    @Test
    fun `test streaming works with cli_js`() = runBlocking {
        println("\n=== 测试流式消息功能 ===")

        var streamEventCount = 0
        var assistantMessageReceived = false

        client.query(
            prompt = "列举 3 个著名的编程语言",
            sessionId = sessionId
        )

        client.receiveResponse().collect { message ->
            when (message) {
                is StreamEvent -> {
                    streamEventCount++
                    println("📡 StreamEvent #$streamEventCount: ${message.event}")
                }

                is AssistantMessage -> {
                    assistantMessageReceived = true
                    println("✅ 收到完整 AssistantMessage")
                }

                is ResultMessage -> {
                    println("✅ 收到 ResultMessage (耗时: ${message.durationMs}ms)")
                }

                else -> {}
            }
        }

        println("\n=== 测试结果 ===")
        println("StreamEvent 数量: $streamEventCount")
        println("AssistantMessage 收到: $assistantMessageReceived")

        assertTrue(streamEventCount > 0, "应该收到至少一个 StreamEvent")
        assertTrue(assistantMessageReceived, "应该收到 AssistantMessage")

        println("✅ 流式消息测试通过")
    }

    @Test
    fun `test error handling with cli_js`() = runBlocking {
        println("\n=== 测试错误处理 ===")

        // 发送一个可能触发错误的查询（超长输入）
        val longPrompt = "请解释 " + "Kotlin ".repeat(1000)

        try {
            client.query(
                prompt = longPrompt,
                sessionId = sessionId
            )

            var errorReceived = false

            client.receiveResponse().collect { message ->
                when (message) {
                    is ResultMessage -> {
                        if (message.isError) {
                            errorReceived = true
                            println("✅ 正确接收到错误消息")
                            println("   错误信息: ${message.result}")
                        }
                    }

                    else -> {}
                }
            }

            // 注意: 这个测试可能成功也可能失败,取决于 CLI 的行为
            // 主要是验证 SDK 不会崩溃
            println("✅ SDK 正确处理了查询（无崩溃）")

        } catch (e: Exception) {
            println("⚠️ 捕获到异常: ${e.message}")
            // 只要不是连接相关的错误就算通过
            assertTrue(!e.message.orEmpty().contains("Connection"), "不应该是连接错误")
        }
    }

    // ========== 辅助函数 ==========

    /**
     * 查找系统的 Node.js 可执行文件
     */
    private fun findNodeExecutable(): String? {
        return try {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val command = if (isWindows) "where" else "which"

            val process = ProcessBuilder(command, "node").start()
            val result = process.inputStream.bufferedReader().readText().trim()

            if (process.waitFor() == 0 && result.isNotEmpty()) {
                result.lines().first()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 Node.js 版本
     */
    private fun getNodeVersion(): String? {
        return try {
            val process = ProcessBuilder("node", "--version").start()
            val version = process.inputStream.bufferedReader().readText().trim()

            if (process.waitFor() == 0) {
                version.removePrefix("v")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 查找 bundled cli.js 文件
     */
    private fun findBundledCliJs(): String? {
        return try {
            // 读取 CLI 版本
            val versionProps = java.util.Properties()
            this::class.java.classLoader.getResourceAsStream("bundled/../cli-version.properties")?.use {
                versionProps.load(it)
            }
            val cliVersion = versionProps.getProperty("cli.version") ?: return null

            // cli.js 文件名
            val cliJsName = "claude-cli-$cliVersion.js"
            val resourcePath = "bundled/$cliJsName"

            val resource = this::class.java.classLoader.getResource(resourcePath)

            if (resource != null) {
                // 如果资源在 JAR 内，提取到临时文件
                if (resource.protocol == "jar") {
                    val tempFile = kotlin.io.path.createTempFile("claude-cli-test-", ".js").toFile()
                    tempFile.deleteOnExit()

                    resource.openStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    tempFile.absolutePath
                } else {
                    // 资源在文件系统中（开发模式）
                    val file = File(resource.toURI())
                    if (file.exists()) {
                        file.absolutePath
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("查找 cli.js 失败: ${e.message}")
            null
        }
    }
}
