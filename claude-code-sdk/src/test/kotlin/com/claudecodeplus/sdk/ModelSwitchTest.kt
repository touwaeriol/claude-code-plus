package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.*

class ModelSwitchTest {
    
    @Test
    fun `test model switch with slash command`() = runTest {
        val mockTransport = MockTransport()
        val options = ClaudeCodeOptions(model = "claude-3-5-sonnet")
        val client = ClaudeCodeSdkClient(options, transport = mockTransport)
        
        // Connect first
        val connectJob = launch { client.connect() }
        delay(50)
        mockTransport.sendMessage("""
        {
            "type": "control_response",
            "response": {
                "subtype": "success",
                "request_id": "req_1",
                "response": {}
            }
        }
        """.trimIndent())
        connectJob.join()
        
        // Clear previous data
        mockTransport.clearWrittenData()
        
        // 1. Ask what model is currently being used
        client.query("What model are you?")
        
        // Simulate AI response saying it's sonnet
        mockTransport.sendMessage("""
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "I am Claude 3.5 Sonnet."
                    }
                ],
                "model": "claude-3-5-sonnet"
            }
        }
        """.trimIndent())
        
        mockTransport.sendMessage("""
        {
            "type": "result",
            "subtype": "success",
            "duration_ms": 1000,
            "duration_api_ms": 800,
            "is_error": false,
            "num_turns": 1,
            "session_id": "default"
        }
        """.trimIndent())
        
        delay(100)
        mockTransport.clearWrittenData()
        
        // 2. Send model switch command
        client.query("/model opus")
        
        // Check if the slash command was sent correctly
        val writtenData = mockTransport.getWrittenData()
        assertTrue(writtenData.isNotEmpty(), "Model switch command should be sent")
        
        val json = Json { ignoreUnknownKeys = true }
        val sentMessage = json.parseToJsonElement(writtenData.last()).jsonObject
        val messageContent = sentMessage["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
        
        assertEquals("/model opus", messageContent, "Slash command should be sent as user message")
        
        // Simulate successful model switch response (this depends on Claude Code CLI handling)
        mockTransport.sendMessage("""
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Switched to Claude 3 Opus."
                    }
                ],
                "model": "claude-3-opus-20240229"
            }
        }
        """.trimIndent())
        
        mockTransport.sendMessage("""
        {
            "type": "result",
            "subtype": "success",
            "duration_ms": 500,
            "duration_api_ms": 300,
            "is_error": false,
            "num_turns": 2,
            "session_id": "default"
        }
        """.trimIndent())
        
        delay(100)
        mockTransport.clearWrittenData()
        
        // 3. Ask what model again to verify switch
        client.query("What model are you now?")
        
        // Verify the query was sent
        val writtenData2 = mockTransport.getWrittenData()
        assertTrue(writtenData2.isNotEmpty(), "Follow-up query should be sent")
        
        // Simulate response with new model
        mockTransport.sendMessage("""
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "I am now Claude 3 Opus."
                    }
                ],
                "model": "claude-3-opus-20240229"
            }
        }
        """.trimIndent())
        
        mockTransport.sendMessage("""
        {
            "type": "result",
            "subtype": "success",
            "duration_ms": 1200,
            "duration_api_ms": 900,
            "is_error": false,
            "num_turns": 3,
            "session_id": "default"
        }
        """.trimIndent())
        
        delay(100)
        
        client.disconnect()
    }
    
    @Test
    fun `test unknown slash command handling`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        // Connect first
        val connectJob = launch { client.connect() }
        delay(50)
        mockTransport.sendMessage("""
        {
            "type": "control_response",
            "response": {
                "subtype": "success",
                "request_id": "req_1",
                "response": {}
            }
        }
        """.trimIndent())
        connectJob.join()
        
        mockTransport.clearWrittenData()
        
        // Send unknown slash command
        client.query("/mode sonnet")  // This should be /model, not /mode
        
        // Verify it's sent as a regular user message
        val writtenData = mockTransport.getWrittenData()
        assertTrue(writtenData.isNotEmpty())
        
        val json = Json { ignoreUnknownKeys = true }
        val sentMessage = json.parseToJsonElement(writtenData.last()).jsonObject
        val messageContent = sentMessage["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
        
        assertEquals("/mode sonnet", messageContent, "Unknown slash command should be sent as regular message")
        
        // Claude Code CLI should handle unknown commands and possibly return an error
        mockTransport.sendMessage("""
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Unknown slash command: mode"
                    }
                ],
                "model": "claude-3-5-sonnet"
            }
        }
        """.trimIndent())
        
        client.disconnect()
    }
}