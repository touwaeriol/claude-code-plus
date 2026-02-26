package com.asakii.claude.agent.sdk.examples

import com.asakii.claude.agent.sdk.ClaudeCodeSdkClient
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.claude.agent.sdk.types.McpServerSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

fun main() = runBlocking {
    val serverUrl = "http://127.0.0.1:52244" // Port from the running server
    println("--- Joint Test Client ---")
    println("Connecting to server at: $serverUrl")

    val client = ClaudeCodeSdkClient(
        ClaudeAgentOptions(
            model = "claude-sonnet-4-6",
            mcpServers = mapOf<String, McpServerSpec>(
                "default" to com.asakii.claude.agent.sdk.types.McpHttpServerConfig(url = serverUrl)
            )
        )
    )

    try {
        client.connect()
        println("✅ Client connected successfully.")

        val sessionId = "joint-test-${UUID.randomUUID()}"
        println("🔹 Using Session ID: $sessionId")

        // Launch a coroutine to listen for all incoming messages
        val job = launch {
            println("🎧 Listening for messages from server...")
            client.receiveResponse().collect { message ->
                println("\n<<< Received from server:")
                println(message)
            }
        }

        // Give the listener a moment to start up
        kotlinx.coroutines.delay(500)

        println("\n>>> Sending query: 'Hello'...")
        client.query("Hello", sessionId)

        // Wait for a while to see all messages
        println("\n⏳ Waiting for 10 seconds to observe all messages...")
        kotlinx.coroutines.delay(10000)

        job.cancel() // Stop listening

    } catch (e: Exception) {
        println("\n❌ An error occurred: ${e.message}")
        e.printStackTrace()
    } finally {
        if (client.isConnected()) {
            client.disconnect()
            println("🔌 Client disconnected.")
        }
    }
    println("--- Test Finished ---")
}
