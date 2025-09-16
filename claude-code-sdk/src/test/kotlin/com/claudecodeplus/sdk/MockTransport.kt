package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.transport.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mock transport for testing purposes.
 */
class MockTransport : Transport {
    
    private val json = Json { ignoreUnknownKeys = true }
    private var connected = false
    private val writtenData = CopyOnWriteArrayList<String>()
    private val messageChannel = Channel<JsonElement>(Channel.UNLIMITED)
    
    // Test control methods
    fun getWrittenData(): List<String> = writtenData.toList()
    fun clearWrittenData() = writtenData.clear()
    
    suspend fun sendMessage(jsonString: String) {
        val jsonElement = json.parseToJsonElement(jsonString)
        messageChannel.send(jsonElement)
    }
    
    suspend fun sendMessages(vararg jsonStrings: String) {
        jsonStrings.forEach { sendMessage(it) }
    }
    
    override suspend fun connect() {
        connected = true
    }
    
    override suspend fun write(data: String) {
        if (!connected) throw IllegalStateException("Transport not connected")
        writtenData.add(data)
    }
    
    override fun readMessages(): Flow<JsonElement> = messageChannel.receiveAsFlow()
    
    override fun isReady(): Boolean = connected
    
    override suspend fun endInput() {
        // Mock implementation - do nothing
    }
    
    override suspend fun close() {
        connected = false
        messageChannel.close()
    }
    
    override fun isConnected(): Boolean = connected
}