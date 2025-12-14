package com.asakii.claude.agent.sdk.transport

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.TimeUnit

/**
 * 测试 ProcessBuilder 直接执行 node 命令
 */
class NodeProcessBuilderTest {

    @Test
    fun `ProcessBuilder can execute node command`() {
        val process = ProcessBuilder("node", "--version")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        println("Node version: $output")
        println("Exit code: $exitCode")

        assertEquals(0, exitCode, "node --version should exit with code 0")
        assertTrue(output.startsWith("v"), "Node version should start with 'v'")
    }

    @Test
    fun `ProcessBuilder can execute node with eval`() {
        val process = ProcessBuilder("node", "-e", "console.log('Hello from Node.js')")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        println("Output: $output")
        println("Exit code: $exitCode")

        assertEquals(0, exitCode)
        assertEquals("Hello from Node.js", output)
    }

    @Test
    fun `ProcessBuilder can execute node script file`() {
        // 创建临时 JS 文件
        val tempFile = kotlin.io.path.createTempFile("test-", ".js").toFile()
        tempFile.writeText("""
            const args = process.argv.slice(2);
            console.log(JSON.stringify({ args: args, pid: process.pid }));
        """.trimIndent())
        tempFile.deleteOnExit()

        val process = ProcessBuilder("node", tempFile.absolutePath, "--test-arg", "value")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        println("Output: $output")
        println("Exit code: $exitCode")

        assertEquals(0, exitCode)
        assertTrue(output.contains("--test-arg"))
        assertTrue(output.contains("value"))
    }

    @Test
    fun `ProcessBuilder inherits PATH environment`() {
        val process = ProcessBuilder("node", "-e", "console.log(process.env.PATH)")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        println("PATH: $output")
        assertEquals(0, exitCode)
        assertTrue(output.isNotEmpty(), "PATH should not be empty")
    }

    @Test
    fun `ProcessBuilder can set working directory`() {
        val tempDir = kotlin.io.path.createTempDirectory("node-test-").toFile()
        tempDir.deleteOnExit()

        val process = ProcessBuilder("node", "-e", "console.log(process.cwd())")
            .directory(tempDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        println("CWD: $output")
        assertEquals(0, exitCode)
        assertTrue(output.contains(tempDir.name), "Should be in temp directory")
    }

    @Test
    fun `ProcessBuilder can handle stdin and stdout`() {
        val process = ProcessBuilder("node", "-e", """
            process.stdin.on('data', (data) => {
                const input = JSON.parse(data.toString());
                console.log(JSON.stringify({ received: input, echo: true }));
                process.exit(0);
            });
        """.trimIndent())
            .start()

        // 写入 stdin
        process.outputStream.bufferedWriter().use { writer ->
            writer.write("""{"message": "hello"}""")
            writer.newLine()
            writer.flush()
        }

        val completed = process.waitFor(5, TimeUnit.SECONDS)
        assertTrue(completed, "Process should complete within 5 seconds")

        val output = process.inputStream.bufferedReader().readText().trim()
        println("Output: $output")

        assertTrue(output.contains("received"))
        assertTrue(output.contains("hello"))
    }
}
