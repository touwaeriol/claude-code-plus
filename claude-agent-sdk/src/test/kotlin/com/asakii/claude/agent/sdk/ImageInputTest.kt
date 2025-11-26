package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 测试图片输入的序列化格式。
 */
class ImageInputTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Test
    fun `test TextInput serialization`() {
        val textInput = TextInput("Hello, Claude!")
        val serialized = json.encodeToString<UserInputContent>(textInput)
        
        println("TextInput serialized:")
        println(serialized)
        
        assertTrue(serialized.contains(""""type": "text""""))
        assertTrue(serialized.contains(""""text": "Hello, Claude!""""))
    }

    @Test
    fun `test ImageInput serialization`() {
        // 使用一个简单的 1x1 像素 PNG 作为测试数据
        val testBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

        // 使用新的 Anthropic API 格式
        val imageInput = ImageInput.fromBase64(
            data = testBase64,
            mimeType = "image/png"
        )
        val serialized = json.encodeToString<UserInputContent>(imageInput)

        println("ImageInput serialized:")
        println(serialized)

        // 验证 Anthropic API 格式: {"type": "image", "source": {"type": "base64", "media_type": "...", "data": "..."}}
        assertTrue(serialized.contains(""""type": "image""""))
        assertTrue(serialized.contains(""""source":"""))
        assertTrue(serialized.contains(""""type": "base64""""))
        assertTrue(serialized.contains(""""media_type": "image/png""""))
        assertTrue(serialized.contains(""""data": "$testBase64""""))
    }

    @Test
    fun `test StreamJsonUserMessage with text and image`() {
        val testBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

        val content = listOf(
            TextInput("这是一张图片，请描述它："),
            ImageInput.fromBase64(data = testBase64, mimeType = "image/png")
        )
        
        val message = StreamJsonUserMessage(
            message = UserMessagePayload(content = content),
            sessionId = "test-session"
        )
        
        val serialized = json.encodeToString(message)
        
        println("Full StreamJsonUserMessage serialized:")
        println(serialized)
        
        // 验证结构
        assertTrue(serialized.contains(""""type": "user""""))
        assertTrue(serialized.contains(""""role": "user""""))
        assertTrue(serialized.contains(""""session_id": "test-session""""))
        assertTrue(serialized.contains(""""content":"""))
        
        // 验证内容块
        assertTrue(serialized.contains(""""type": "text""""))
        assertTrue(serialized.contains(""""type": "image""""))
    }

    @Test
    fun `test message format matches Python SDK`() {
        // Python SDK 格式:
        // {
        //   "type": "user",
        //   "message": {"role": "user", "content": [...]},
        //   "session_id": "default",
        //   "parent_tool_use_id": null
        // }
        
        val message = StreamJsonUserMessage(
            message = UserMessagePayload("Hello!"),
            sessionId = "default"
        )
        
        val serialized = json.encodeToString(message)
        
        println("Python SDK compatible format:")
        println(serialized)
        
        // 验证关键字段
        assertTrue(serialized.contains(""""type": "user""""))
        assertTrue(serialized.contains(""""message":"""))
        assertTrue(serialized.contains(""""role": "user""""))
        assertTrue(serialized.contains(""""content":"""))
        assertTrue(serialized.contains(""""session_id": "default""""))
        assertTrue(serialized.contains(""""parent_tool_use_id": null"""))
    }
}

