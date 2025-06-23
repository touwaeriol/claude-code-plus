package com.claudecodeplus.test

import com.claudecodeplus.core.interfaces.FileSearchService
import kotlinx.coroutines.delay
import java.io.File

/**
 * 测试用的文件搜索服务实现
 * 使用预定义的文件列表来模拟搜索功能
 */
class MockFileSearchService(
    private val projectPath: String = "/Users/erio/codes/idea/claude-code-plus"
) : FileSearchService {
    
    // 预定义的测试文件列表（模拟项目中的文件）
    private val mockFiles = listOf(
        // Kotlin源文件
        "src/main/kotlin/com/claudecodeplus/toolwindow/ClaudeCodePlusToolWindowFactory.kt",
        "src/main/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapper.kt",
        "src/main/kotlin/com/claudecodeplus/sdk/DataClasses.kt",
        "src/main/kotlin/com/claudecodeplus/sdk/ClaudeSessionManager.kt",
        "src/main/kotlin/com/claudecodeplus/sdk/OptimizedSessionManager.kt",
        "src/main/kotlin/com/claudecodeplus/sdk/SessionCache.kt",
        "src/main/kotlin/com/claudecodeplus/ui/MessageRenderer.kt",
        "src/main/kotlin/com/claudecodeplus/ui/CustomMarkdownStyles.kt",
        "src/main/kotlin/com/claudecodeplus/settings/McpConfigurable.kt",
        "src/main/kotlin/com/claudecodeplus/settings/McpSettingsService.kt",
        
        // 配置文件
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        
        // 文档
        "README.md",
        "docs/ARCHITECTURE.md",
        "docs/REQUIREMENTS.md",
        "docs/进度文档.md",
        "docs/CLAUDE_MESSAGE_TYPES.md",
        "CLAUDE.md",
        
        // 资源文件
        "src/main/resources/META-INF/plugin.xml",
        "src/main/resources/messages/MyBundle.properties",
        
        // 测试文件
        "src/test/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapperTest.kt",
        
        // Node相关（如果需要）
        "claude-sdk-wrapper/package.json",
        "claude-sdk-wrapper/src/index.ts",
        "claude-sdk-wrapper/src/services/claudeService.ts",
        
        // 其他常见文件
        ".gitignore",
        "LICENSE"
    )
    
    // 最近文件列表（模拟最近打开的文件）
    private val recentFiles = listOf(
        "src/main/kotlin/com/claudecodeplus/toolwindow/ClaudeCodePlusToolWindowFactory.kt",
        "src/main/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapper.kt",
        "build.gradle.kts",
        "README.md",
        "src/main/resources/META-INF/plugin.xml"
    )
    
    override suspend fun searchFiles(searchText: String): List<String> {
        // 模拟搜索延迟
        delay(50)
        
        if (searchText.isBlank()) {
            return recentFiles
        }
        
        // 简单的文件名和路径匹配
        return mockFiles.filter { path ->
            val fileName = path.substringAfterLast('/')
            val lowerSearch = searchText.lowercase()
            
            // 匹配文件名或路径
            fileName.lowercase().contains(lowerSearch) ||
            path.lowercase().contains(lowerSearch) ||
            // 支持简单的模糊匹配（如 "ctw" 匹配 "ClaudeToolWindow"）
            matchesFuzzy(fileName, searchText)
        }.take(20) // 限制返回结果数量
    }
    
    override suspend fun getRecentFiles(limit: Int): List<String> {
        return recentFiles.take(limit)
    }
    
    override fun fileExists(path: String): Boolean {
        // 检查是否在mock文件列表中
        return mockFiles.contains(path) || 
               File("$projectPath/$path").exists()
    }
    
    override fun readFileContent(path: String): String? {
        return try {
            val fullPath = if (path.startsWith("/")) path else "$projectPath/$path"
            File(fullPath).readText()
        } catch (e: Exception) {
            // 返回模拟内容
            when {
                path.endsWith(".kt") -> "// Mock Kotlin file content\nclass MockClass {\n    fun mockFunction() {}\n}"
                path.endsWith(".md") -> "# Mock Markdown Content\n\nThis is a mock file for testing."
                path.endsWith(".properties") -> "mock.property=value\ntest.enabled=true"
                else -> "Mock file content for: $path"
            }
        }
    }
    
    /**
     * 简单的模糊匹配算法
     * 例如 "ctw" 可以匹配 "ClaudeToolWindow"
     */
    private fun matchesFuzzy(fileName: String, searchText: String): Boolean {
        if (searchText.length > fileName.length) return false
        
        var searchIndex = 0
        val lowerFileName = fileName.lowercase()
        val lowerSearch = searchText.lowercase()
        
        for (char in lowerFileName) {
            if (searchIndex < lowerSearch.length && char == lowerSearch[searchIndex]) {
                searchIndex++
            }
        }
        
        return searchIndex == lowerSearch.length
    }
}