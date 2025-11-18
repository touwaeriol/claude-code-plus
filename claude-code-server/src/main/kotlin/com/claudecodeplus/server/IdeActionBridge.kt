package com.claudecodeplus.server

import com.claudecodeplus.bridge.FrontendRequest
import com.claudecodeplus.bridge.FrontendResponse
import com.claudecodeplus.bridge.IdeTheme
import kotlinx.serialization.json.JsonPrimitive

/**
 * An interface to abstract away IDE-specific actions, allowing the server
 * to run in a standalone mode for testing.
 */
interface IdeActionBridge {
    fun getTheme(): IdeTheme
    fun getProjectPath(): String
    fun openFile(request: FrontendRequest): FrontendResponse
    fun showDiff(request: FrontendRequest): FrontendResponse
    fun searchFiles(query: String, maxResults: Int): List<Any>
    fun getRecentFiles(maxResults: Int): List<Any>

    /**
     * A mock implementation for standalone testing.
     */
    class Mock : IdeActionBridge {
        override fun getTheme(): IdeTheme = IdeTheme(isDark = true)
        override fun getProjectPath(): String = System.getProperty("user.dir")
        override fun openFile(request: FrontendRequest): FrontendResponse {
            println("[Mock] Opening file: ${request.data}")
            return FrontendResponse(true, data = mapOf("message" to JsonPrimitive("Mock openFile success")))
        }
        override fun showDiff(request: FrontendRequest): FrontendResponse {
            println("[Mock] Showing diff: ${request.data}")
            return FrontendResponse(true, data = mapOf("message" to JsonPrimitive("Mock showDiff success")))
        }
        override fun searchFiles(query: String, maxResults: Int): List<String> {
            println("[Mock] Searching files for: '$query'")
            return listOf("/mock/file1.kt", "/mock/file2.java")
        }
        override fun getRecentFiles(maxResults: Int): List<String> {
            println("[Mock] Getting recent files")
            return listOf("/mock/recent1.kt", "/mock/recent2.java")
        }
    }
}

