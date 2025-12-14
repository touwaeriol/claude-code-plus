package com.asakii.server

import com.asakii.bridge.FrontendRequest
import com.asakii.bridge.FrontendResponse
import com.asakii.claude.agent.sdk.types.AgentDefinition
import com.asakii.rpc.api.IdeTheme
import com.asakii.server.agents.AgentDefinitionsProvider
import com.asakii.server.agents.DefaultAgentDefinitionsProvider
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * An interface to abstract away IDE-specific actions, allowing the server
 * to run in a standalone mode for testing.
 */
interface IdeActionBridge {
    fun getTheme(): IdeTheme
    fun getProjectPath(): String
    fun getLocale(): String
    fun setLocale(locale: String): Boolean
    fun openFile(request: FrontendRequest): FrontendResponse
    fun showDiff(request: FrontendRequest): FrontendResponse
    fun searchFiles(query: String, maxResults: Int): List<Any>
    fun getRecentFiles(maxResults: Int): List<Any>

    /**
     * 获取子代理定义
     * @return 代理名称到定义的映射
     */
    fun getAgentDefinitions(): Map<String, AgentDefinition> = DefaultAgentDefinitionsProvider.getAgentDefinitions()

    /**
     * A mock implementation for standalone testing.
     * @param projectPath 项目根目录路径（可选，默认使用当前工作目录）
     */
    class Mock(private val projectPath: String? = null) : IdeActionBridge {
        private var mockLocale: String? = null

        override fun getTheme(): IdeTheme = IdeTheme()
        override fun getProjectPath(): String = projectPath ?: System.getProperty("user.dir")
        
        override fun getLocale(): String {
            if (mockLocale != null) return mockLocale!!
            val locale = Locale.getDefault()
            return "${locale.language}-${locale.country}"
        }

        override fun setLocale(locale: String): Boolean {
            mockLocale = locale
            println("[Mock] Locale set to: $locale")
            return true
        }

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
