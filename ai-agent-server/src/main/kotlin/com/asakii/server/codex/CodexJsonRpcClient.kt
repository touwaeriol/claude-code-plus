package com.asakii.server.codex

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * JSON-RPC 2.0 客户端，用于通过 stdin/stdout 与 codex-app-server 通信
 *
 * 功能：
 * - request(method, params) - 发送请求并等待响应
 * - notify(method, params) - 发送通知（无响应）
 * - onNotification(callback) - 注册通知处理器
 * - onRequest(callback) - 注册服务端请求处理器（用于 approval）
 */
class CodexJsonRpcClient(
    private val process: Process,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
    private val reader: BufferedReader = BufferedReader(InputStreamReader(process.inputStream))

    private val requestIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()

    private val writeMutex = Mutex()
    private val messageBuffer = StringBuilder()

    // 通知处理器
    private val notificationHandlers = mutableListOf<suspend (String, JsonElement?) -> Unit>()

    // 服务端请求处理器（用于 approval）
    private val requestHandlers = mutableListOf<suspend (String, JsonElement?, Int) -> JsonElement?>()

    @Volatile
    private var isRunning = true

    init {
        // 启动消息读取协程
        scope.launch {
            startMessageLoop()
        }
    }

    /**
     * 发送 JSON-RPC 请求并等待响应
     */
    suspend fun request(method: String, params: JsonElement? = null): JsonElement {
        val id = requestIdCounter.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pendingRequests[id] = deferred

        try {
            val message = JsonRpcRequest(
                jsonrpc = "2.0",
                method = method,
                params = params,
                id = id
            )

            sendMessage(message)

            // 等待响应，设置超时
            return withTimeout(30000) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(id)
            throw JsonRpcException("Request timeout: $method", -32603)
        } catch (e: Exception) {
            pendingRequests.remove(id)
            throw e
        }
    }

    /**
     * 发送 JSON-RPC 通知（无需响应）
     */
    suspend fun notify(method: String, params: JsonElement? = null) {
        val message = JsonRpcNotification(
            jsonrpc = "2.0",
            method = method,
            params = params
        )
        sendMessage(message)
    }

    /**
     * 注册通知处理器
     */
    fun onNotification(handler: suspend (method: String, params: JsonElement?) -> Unit) {
        notificationHandlers.add(handler)
    }

    /**
     * 注册服务端请求处理器（用于 approval 等场景）
     */
    fun onRequest(handler: suspend (method: String, params: JsonElement?, id: Int) -> JsonElement?) {
        requestHandlers.add(handler)
    }

    /**
     * 发送响应给服务端（响应服务端的请求）
     */
    suspend fun sendResponse(id: Int, result: JsonElement?) {
        val message = JsonRpcResponse(
            jsonrpc = "2.0",
            result = result,
            id = id
        )
        sendMessage(message)
    }

    /**
     * 发送错误响应给服务端
     */
    suspend fun sendErrorResponse(id: Int, code: Int, message: String, data: JsonElement? = null) {
        val response = JsonRpcErrorResponse(
            jsonrpc = "2.0",
            error = JsonRpcError(code = code, message = message, data = data),
            id = id
        )
        sendMessage(response)
    }

    /**
     * 发送消息到 stdout
     */
    private suspend fun sendMessage(message: Any) {
        writeMutex.withLock {
            val jsonString = json.encodeToString(JsonElement.serializer(), json.encodeToJsonElement(message))
            writer.write(jsonString)
            writer.newLine()
            writer.flush()
        }
    }

    /**
     * 启动消息读取循环
     */
    private suspend fun startMessageLoop() {
        try {
            while (isRunning && !scope.isActive.not()) {
                val line = withContext(Dispatchers.IO) {
                    reader.readLine()
                } ?: break

                if (line.isBlank()) continue

                try {
                    processMessage(line)
                } catch (e: Exception) {
                    println("Error processing message: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                println("Message loop error: ${e.message}")
                e.printStackTrace()
            }
        } finally {
            cleanup()
        }
    }

    /**
     * 处理接收到的消息
     */
    private suspend fun processMessage(line: String) {
        val jsonElement = json.parseToJsonElement(line)
        val jsonObject = jsonElement.jsonObject

        val version = jsonObject["jsonrpc"]?.jsonPrimitive?.content
        if (version != "2.0") {
            println("Invalid JSON-RPC version: $version")
            return
        }

        when {
            // 响应消息（有 result 或 error，有 id）
            jsonObject.containsKey("result") || jsonObject.containsKey("error") -> {
                handleResponse(jsonObject)
            }
            // 请求消息（有 method 和 id）
            jsonObject.containsKey("method") && jsonObject.containsKey("id") -> {
                handleRequest(jsonObject)
            }
            // 通知消息（有 method，无 id）
            jsonObject.containsKey("method") -> {
                handleNotification(jsonObject)
            }
            else -> {
                println("Unknown message type: $line")
            }
        }
    }

    /**
     * 处理响应消息
     */
    private fun handleResponse(jsonObject: JsonObject) {
        val id = jsonObject["id"]?.jsonPrimitive?.intOrNull ?: return
        val deferred = pendingRequests.remove(id) ?: return

        when {
            jsonObject.containsKey("result") -> {
                deferred.complete(jsonObject["result"] ?: JsonNull)
            }
            jsonObject.containsKey("error") -> {
                val error = jsonObject["error"]?.jsonObject
                val code = error?.get("code")?.jsonPrimitive?.intOrNull ?: -32603
                val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                deferred.completeExceptionally(JsonRpcException(message, code))
            }
            else -> {
                deferred.completeExceptionally(JsonRpcException("Invalid response format", -32600))
            }
        }
    }

    /**
     * 处理服务端请求（如 approval）
     */
    private suspend fun handleRequest(jsonObject: JsonObject) {
        val method = jsonObject["method"]?.jsonPrimitive?.content ?: return
        val params = jsonObject["params"]
        val id = jsonObject["id"]?.jsonPrimitive?.intOrNull ?: return

        try {
            var handled = false
            for (handler in requestHandlers) {
                val result = handler(method, params, id)
                if (result != null || handled) {
                    handled = true
                    sendResponse(id, result)
                    break
                }
            }

            if (!handled) {
                sendErrorResponse(id, -32601, "Method not found: $method")
            }
        } catch (e: Exception) {
            sendErrorResponse(id, -32603, "Internal error: ${e.message}")
        }
    }

    /**
     * 处理通知消息
     */
    private suspend fun handleNotification(jsonObject: JsonObject) {
        val method = jsonObject["method"]?.jsonPrimitive?.content ?: return
        val params = jsonObject["params"]

        for (handler in notificationHandlers) {
            try {
                handler(method, params)
            } catch (e: Exception) {
                println("Notification handler error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        isRunning = false

        // 取消所有待处理的请求
        pendingRequests.values.forEach { deferred ->
            deferred.completeExceptionally(JsonRpcException("Client closed", -32000))
        }
        pendingRequests.clear()

        try {
            writer.close()
        } catch (e: Exception) {
            // Ignore
        }

        try {
            reader.close()
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * 关闭客户端
     */
    fun close() {
        isRunning = false
        scope.cancel()
        cleanup()
    }
}

/**
 * JSON-RPC 请求
 */
@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String,
    val method: String,
    val params: JsonElement? = null,
    val id: Int
)

/**
 * JSON-RPC 通知
 */
@Serializable
private data class JsonRpcNotification(
    val jsonrpc: String,
    val method: String,
    val params: JsonElement? = null
)

/**
 * JSON-RPC 响应
 */
@Serializable
private data class JsonRpcResponse(
    val jsonrpc: String,
    val result: JsonElement? = null,
    val id: Int
)

/**
 * JSON-RPC 错误响应
 */
@Serializable
private data class JsonRpcErrorResponse(
    val jsonrpc: String,
    val error: JsonRpcError,
    val id: Int
)

/**
 * JSON-RPC 错误对象
 */
@Serializable
private data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * JSON-RPC 异常
 */
class JsonRpcException(
    message: String,
    val code: Int
) : Exception(message)
