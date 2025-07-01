package com.claudecodeplus.test

import com.claudecodeplus.ui.jewel.components.context.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.net.URL

/**
 * 测试环境的模拟上下文搜索服务
 * 用于toolwindow-test环境测试上下文选择功能
 */
class MockContextSearchService(
    private val projectRoot: String = System.getProperty("user.dir")
) : ContextSearchService {
    
    private val mockFiles = listOf(
        // Kotlin文件
        FileContextItem(
            name = "Main.kt",
            relativePath = "src/main/kotlin/Main.kt",
            absolutePath = "$projectRoot/src/main/kotlin/Main.kt",
            isDirectory = false,
            fileType = "kt"
        ),
        FileContextItem(
            name = "ContextModels.kt",
            relativePath = "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/context/ContextModels.kt",
            absolutePath = "$projectRoot/toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/context/ContextModels.kt",
            isDirectory = false,
            fileType = "kt"
        ),
        FileContextItem(
            name = "JewelChatApp.kt",
            relativePath = "toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/JewelChatApp.kt",
            absolutePath = "$projectRoot/toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/JewelChatApp.kt",
            isDirectory = false,
            fileType = "kt"
        ),
        
        // Java文件
        FileContextItem(
            name = "ClaudeCliWrapper.java",
            relativePath = "cli-wrapper/src/main/java/com/claudecodeplus/sdk/ClaudeCliWrapper.java",
            absolutePath = "$projectRoot/cli-wrapper/src/main/java/com/claudecodeplus/sdk/ClaudeCliWrapper.java",
            isDirectory = false,
            fileType = "java"
        ),
        
        // 配置文件
        FileContextItem(
            name = "build.gradle.kts",
            relativePath = "build.gradle.kts",
            absolutePath = "$projectRoot/build.gradle.kts",
            isDirectory = false,
            fileType = "gradle"
        ),
        FileContextItem(
            name = "settings.gradle.kts",
            relativePath = "settings.gradle.kts",
            absolutePath = "$projectRoot/settings.gradle.kts",
            isDirectory = false,
            fileType = "gradle"
        ),
        
        // 文档文件
        FileContextItem(
            name = "README.md",
            relativePath = "README.md",
            absolutePath = "$projectRoot/README.md",
            isDirectory = false,
            fileType = "md"
        ),
        FileContextItem(
            name = "CONTEXT_SELECTION_REQUIREMENTS.md",
            relativePath = "docs/CONTEXT_SELECTION_REQUIREMENTS.md",
            absolutePath = "$projectRoot/docs/CONTEXT_SELECTION_REQUIREMENTS.md",
            isDirectory = false,
            fileType = "md"
        ),
        
        // 目录
        FileContextItem(
            name = "toolwindow",
            relativePath = "toolwindow/",
            absolutePath = "$projectRoot/toolwindow",
            isDirectory = true,
            fileType = ""
        ),
        FileContextItem(
            name = "cli-wrapper",
            relativePath = "cli-wrapper/",
            absolutePath = "$projectRoot/cli-wrapper",
            isDirectory = true,
            fileType = ""
        ),
        FileContextItem(
            name = "docs",
            relativePath = "docs/",
            absolutePath = "$projectRoot/docs",
            isDirectory = true,
            fileType = ""
        ),
        
        // TypeScript/JavaScript文件
        FileContextItem(
            name = "server.ts",
            relativePath = "claude-sdk-wrapper/src/server.ts",
            absolutePath = "$projectRoot/claude-sdk-wrapper/src/server.ts",
            isDirectory = false,
            fileType = "ts"
        ),
        FileContextItem(
            name = "package.json",
            relativePath = "claude-sdk-wrapper/package.json",
            absolutePath = "$projectRoot/claude-sdk-wrapper/package.json",
            isDirectory = false,
            fileType = "json"
        )
    )
    
    override suspend fun searchFiles(query: String, maxResults: Int): List<FileSearchResult> {
        // 模拟搜索延迟
        delay(100)
        
        if (query.isEmpty()) {
            return getRootFiles(maxResults).map { 
                FileSearchResult(it, 100, FileSearchResult.MatchType.EXACT_NAME) 
            }
        }
        
        val results = mutableListOf<FileSearchResult>()
        val config = SearchWeight()
        
        for (file in mockFiles) {
            val searchResult = calculateSearchWeight(
                fileName = file.name,
                filePath = file.relativePath,
                query = query,
                config = config
            )
            
            if (searchResult != null) {
                // 使用真实的文件信息替换虚拟信息
                results.add(searchResult.copy(item = file))
            }
        }
        
        // 按权重排序并限制结果数量
        return results
            .sortedByDescending { it.weight }
            .take(maxResults)
    }
    
    override fun searchFilesFlow(query: String, maxResults: Int): Flow<List<FileSearchResult>> {
        return flow {
            emit(searchFiles(query, maxResults))
        }
    }
    
    override suspend fun getRootFiles(maxResults: Int): List<FileContextItem> {
        // 模拟根目录文件获取延迟
        delay(50)
        
        return mockFiles
            .filter { !it.relativePath.contains("/") || it.isDirectory }
            .take(maxResults)
    }
    
    override fun validateUrl(url: String): Boolean {
        return try {
            val urlPattern = Regex(
                "^(https?|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
            )
            url.matches(urlPattern)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getWebInfo(url: String): WebContextItem? {
        // 模拟网络请求延迟
        delay(300)
        
        return when {
            url.contains("github.com") -> WebContextItem(
                url = url,
                title = "GitHub Repository",
                description = "Code repository hosting service"
            )
            url.contains("kotlinlang.org") -> WebContextItem(
                url = url,
                title = "Kotlin Programming Language",
                description = "Official Kotlin documentation and resources"
            )
            url.contains("docs.oracle.com") -> WebContextItem(
                url = url,
                title = "Oracle Documentation",
                description = "Official Oracle Java documentation"
            )
            url.contains("stackoverflow.com") -> WebContextItem(
                url = url,
                title = "Stack Overflow",
                description = "Question and answer site for programmers"
            )
            else -> WebContextItem(
                url = url,
                title = extractDomainName(url),
                description = "Web page"
            )
        }
    }
    
    override suspend fun getFileInfo(relativePath: String): FileContextItem? {
        return mockFiles.find { it.relativePath == relativePath }
    }
    
    /**
     * 从URL中提取域名
     */
    private fun extractDomainName(url: String): String {
        return try {
            val urlObj = URL(url)
            urlObj.host
        } catch (e: Exception) {
            "Unknown Site"
        }
    }
    
    /**
     * 添加更多测试文件
     */
    fun addMockFile(file: FileContextItem) {
        (mockFiles as MutableList).add(file)
    }
    
    /**
     * 清除所有模拟文件
     */
    fun clearMockFiles() {
        (mockFiles as MutableList).clear()
    }
    
    /**
     * 获取所有模拟文件
     */
    fun getAllMockFiles(): List<FileContextItem> = mockFiles
} 