package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * 测试 canUseTool 回调参数
 */
class CanUseToolCallbackTest {

    @Test
    fun `test canUseTool callback parameters`() = runBlocking {
        val workDir = Path.of(System.getProperty("user.dir"))

        val canUseToolCallback: CanUseTool = { toolName, input, toolUseId, context ->
            println("=" .repeat(60))
            println("🔐 canUseTool 回调被调用!")
            println("=" .repeat(60))
            println("📌 toolName: $toolName")
            println("📌 toolUseId: $toolUseId")
            println("📌 input: $input")
            println("📌 input.keys: ${input.keys}")
            println("📌 context: $context")

            // 打印 input 的所有字段
            input.forEach { (key, value) ->
                println("   input[$key] = $value")
            }

            println("=" .repeat(60))

            // 允许执行
            PermissionResultAllow(updatedInput = input)
        }

        val options = ClaudeAgentOptions(
            cwd = workDir,
            permissionMode = PermissionMode.DEFAULT,
            maxTurns = 2,
            canUseTool = canUseToolCallback
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()
            client.query("在当前目录创建一个名为 test_permission.txt 的文件，内容为 hello")

            client.receiveResponse().collect { message ->
                if (message is ResultMessage) {
                    println("✅ 完成")
                }
            }
        } finally {
            client.disconnect()
        }
    }
}
