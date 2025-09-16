package com.claudecodeplus.sdk.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Abstract transport interface for communicating with Claude CLI.
 */
interface Transport {
    /**
     * Connect to the Claude CLI process.
     */
    suspend fun connect()
    
    /**
     * Write data to the CLI stdin.
     */
    suspend fun write(data: String)
    
    /**
     * Read messages from CLI stdout as a flow of JSON objects.
     */
    fun readMessages(): Flow<JsonElement>
    
    /**
     * Check if the transport is ready for communication.
     */
    fun isReady(): Boolean
    
    /**
     * End the input stream to the CLI.
     */
    suspend fun endInput()
    
    /**
     * Close the transport and cleanup resources.
     */
    suspend fun close()
    
    /**
     * Check if the transport is connected.
     */
    fun isConnected(): Boolean
}