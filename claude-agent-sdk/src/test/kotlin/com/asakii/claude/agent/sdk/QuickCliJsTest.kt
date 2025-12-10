package com.asakii.claude.agent.sdk

import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 快速验证 cli.js 方案的基础测试
 */
class QuickCliJsTest {

    @Test
    fun `verify Node_js is available`() {
        println("\n========== 测试 Node.js ==========")

        val nodeExe = findNodeExecutable()
        println("Node.js 路径: $nodeExe")

        assertNotNull(nodeExe, "❌ 未找到 Node.js")

        // 获取版本
        val version = getNodeVersion()
        println("Node.js 版本: $version")

        assertNotNull(version, "无法获取 Node.js 版本")

        println("✅ Node.js 测试通过")
    }

    @Test
    fun `verify cli_js file exists in resources`() {
        println("\n========== 测试 cli.js 资源 ==========")

        // 读取版本
        val versionProps = Properties()
        val versionResource = this::class.java.classLoader.getResourceAsStream("cli-version.properties")
        assertNotNull(versionResource, "❌ cli-version.properties 未找到")

        versionProps.load(versionResource)
        val cliVersion = versionProps.getProperty("cli.version")

        println("CLI 版本: $cliVersion")
        assertNotNull(cliVersion, "CLI 版本未定义")

        //检查资源
        val cliJsName = "claude-cli-$cliVersion.js"
        val resourcePath = "bundled/$cliJsName"

        println("资源路径: $resourcePath")

        val resource = this::class.java.classLoader.getResource(resourcePath)
        println("资源 URL: $resource")

        assertNotNull(resource, "❌ $resourcePath 未找到")

        println("✅ cli.js 资源测试通过")
    }

    @Test
    fun `verify cli_js can be extracted from resources`() {
        println("\n========== 测试 cli.js 提取 ==========")

        val cliJsPath = findBundledCliJs()
        println("提取的 cli.js 路径: $cliJsPath")

        assertNotNull(cliJsPath, "❌ 无法提取 cli.js")

        val cliJsFile = File(cliJsPath)
        assertTrue(cliJsFile.exists(), "cli.js 文件不存在")

        val sizeMB = cliJsFile.length() / (1024.0 * 1024.0)
        println("文件大小: ${String.format("%.2f", sizeMB)} MB")

        assertTrue(sizeMB > 5.0, "cli.js 文件太小 (${String.format("%.2f", sizeMB)} MB)")

        println("✅ cli.js 提取测试通过")
    }

    // ========== 辅助函数 ==========

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
            println("查找 Node.js 失败: ${e.message}")
            null
        }
    }

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
            println("获取 Node.js 版本失败: ${e.message}")
            null
        }
    }

    private fun findBundledCliJs(): String? {
        return try {
            // 读取 CLI 版本
            val versionProps = Properties()
            this::class.java.classLoader.getResourceAsStream("cli-version.properties")?.use {
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
            e.printStackTrace()
            null
        }
    }
}
