package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.plugin.services.*
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * 简单的 FileIndexService 实现
 * 提供基本的文件搜索功能
 */
class SimpleFileIndexService(
    private val project: Project
) : FileIndexService {
    
    companion object {
        private val logger = Logger.getInstance(SimpleFileIndexService::class.java)
    }
    
    /**
     * 检查项目索引是否就绪
     */
    private fun isProjectIndexing(): Boolean {
        return DumbService.getInstance(project).isDumb
    }
    
    /**
     * 当索引正在进行时使用的简单文件搜索
     * 不依赖IntelliJ的索引系统，直接遍历文件系统
     */
    private suspend fun searchFilesWithoutIndex(
        query: String,
        maxResults: Int,
        fileTypes: List<String>
    ): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<IndexedFileInfo>()
            val queryLower = query.lowercase()
            val projectBasePath = project.basePath ?: return@withContext emptyList()
            
            val projectDir = File(projectBasePath)
            
            // 首先查找根目录下的直接匹配文件（优先级最高）
            projectDir.listFiles()?.filter { file ->
                !file.isDirectory && 
                file.name.lowercase().contains(queryLower) &&
                (fileTypes.isEmpty() || fileTypes.contains(file.extension?.lowercase() ?: ""))
            }?.forEach { file ->
                results.add(createFileInfoFromFile(file, projectDir))
            }
            
            // 然后递归搜索子目录（如果还需要更多结果）
            if (results.size < maxResults) {
                projectDir.walkTopDown()
                    .filter { !it.isDirectory && it.parent != projectBasePath } // 排除根目录文件（已处理）
                    .filter { file ->
                        file.name.lowercase().contains(queryLower) &&
                        (fileTypes.isEmpty() || fileTypes.contains(file.extension?.lowercase() ?: ""))
                    }
                    .take(maxResults - results.size)
                    .forEach { file ->
                        results.add(createFileInfoFromFile(file, projectDir))
                    }
            }
            
            // 按相关性和路径深度排序
            results.sortedWith(compareBy(
                // 精确匹配优先
                { !it.name.equals(query, ignoreCase = true) },
                // 根目录文件优先（路径深度最小）
                { it.relativePath.count { it == '/' || it == File.separatorChar } },
                // 前缀匹配次之
                { !it.name.startsWith(query, ignoreCase = true) },
                // 文件名字母排序
                { it.name.lowercase() }
            )).take(maxResults)
        } catch (e: Exception) {
            logger.warn("Fallback search failed: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 从 File 对象创建 IndexedFileInfo
     */
    private fun createFileInfoFromFile(file: File, projectDir: File): IndexedFileInfo {
        val relativePath = file.relativeTo(projectDir).path
        return IndexedFileInfo(
            name = file.name,
            relativePath = relativePath,
            absolutePath = file.absolutePath,
            fileType = file.extension ?: "",
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = false,
            language = detectLanguage(file.extension ?: ""),
            encoding = "UTF-8"
        )
    }
    
    override suspend fun initialize(rootPath: String) {
        // IntelliJ 自动管理索引，不需要手动初始化
    }
    
    override suspend fun indexPath(path: String, recursive: Boolean) {
        // IntelliJ 自动管理索引
    }
    
    override suspend fun searchFiles(
        query: String, 
        maxResults: Int,
        fileTypes: List<String>
    ): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        // 如果索引正在进行，提供有限的搜索功能
        if (isProjectIndexing()) {
            logger.info("Project is indexing, providing limited file search")
            return@withContext searchFilesWithoutIndex(query, maxResults, fileTypes)
        }
        
        try {
            ReadAction.compute<List<IndexedFileInfo>, Exception> {
                val results = mutableListOf<IndexedFileInfo>()
                val queryLower = query.lowercase()
                
                // 获取项目的所有源文件根目录
                val contentRoots = ProjectRootManager.getInstance(project).contentRoots
                
                for (root in contentRoots) {
                    VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                        if (!virtualFile.isDirectory && 
                            virtualFile.name.lowercase().contains(queryLower) &&
                            results.size < maxResults) {
                            
                            createFileInfo(virtualFile)?.let { fileInfo ->
                                if (fileTypes.isEmpty() || fileTypes.contains(fileInfo.fileType.lowercase())) {
                                    results.add(fileInfo)
                                }
                            }
                        }
                        results.size < maxResults // 继续迭代直到达到最大结果数
                    }
                }
                
                // 按相关性排序：精确匹配 > 前缀匹配 > 包含匹配
                results.sortedBy { file ->
                    when {
                        file.name.equals(query, ignoreCase = true) -> 0
                        file.name.startsWith(query, ignoreCase = true) -> 1
                        file.name.contains(query, ignoreCase = true) -> 2
                        else -> 3
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Search failed: ${e.message}")
            emptyList()
        }
    }
    
    override suspend fun findFilesByName(fileName: String, maxResults: Int): List<IndexedFileInfo> {
        return searchFiles(fileName, maxResults)
    }
    
    override suspend fun searchSymbols(
        query: String,
        symbolTypes: List<SymbolType>,
        maxResults: Int
    ): List<IndexedSymbolInfo> {
        // 暂不实现符号搜索
        return emptyList()
    }
    
    override suspend fun getRecentFiles(maxResults: Int): List<IndexedFileInfo> = withContext(Dispatchers.IO) {
        try {
            println("[SimpleFileIndexService] getRecentFiles 被调用，maxResults=$maxResults")
            
            ReadAction.compute<List<IndexedFileInfo>, Exception> {
                val results = mutableListOf<IndexedFileInfo>()
                
                // 获取项目的所有源文件根目录
                val contentRoots = ProjectRootManager.getInstance(project).contentRoots
                println("[SimpleFileIndexService] 找到 ${contentRoots.size} 个内容根目录")
                
                for (root in contentRoots) {
                    println("[SimpleFileIndexService] 遍历根目录: ${root.path}")
                    
                    VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                        if (!virtualFile.isDirectory && results.size < maxResults) {
                            // 过滤常用文件类型
                            val extension = virtualFile.extension?.lowercase()
                            val isCommonFile = extension in listOf(
                                "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
                                "md", "txt", "json", "yaml", "yml", "xml", "html", "css", "scss", "less",
                                "gradle", "properties", "kts"
                            )
                            
                            if (isCommonFile) {
                                createFileInfo(virtualFile)?.let { fileInfo ->
                                    results.add(fileInfo)
                                    println("[SimpleFileIndexService] 添加文件: ${fileInfo.name}")
                                }
                            }
                        }
                        results.size < maxResults // 继续迭代直到达到最大结果数
                    }
                }
                
                // 按文件名排序，优先显示常用文件
                val sortedResults = results.sortedWith(compareBy<IndexedFileInfo> { fileInfo ->
                    // 优先级：配置文件 > Kotlin/Java文件 > 其他
                    when (fileInfo.fileType.lowercase()) {
                        "gradle", "kts", "properties", "json", "yaml", "yml" -> 0
                        "kt", "java" -> 1
                        else -> 2
                    }
                }.thenBy { it.name.lowercase() })
                
                println("[SimpleFileIndexService] 找到 ${sortedResults.size} 个文件")
                sortedResults.take(maxResults)
            }
        } catch (e: Exception) {
            logger.warn("获取最近文件失败: ${e.message}")
            println("[SimpleFileIndexService] 获取最近文件异常: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    override suspend fun getRecentlyModifiedFiles(projectPath: String, limit: Int): List<IndexedFileInfo> {
        // 暂不实现最近修改文件功能
        return emptyList()
    }
    
    override suspend fun getFileContent(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            File(filePath).readText()
        } catch (e: Exception) {
            logger.warn("Failed to read file: $filePath", e)
            null
        }
    }
    
    override suspend fun getFileSymbols(filePath: String): List<IndexedSymbolInfo> {
        // 暂不实现
        return emptyList()
    }
    
    override fun isIndexReady(): Boolean = !isProjectIndexing()
    
    override suspend fun getIndexStats(): IndexStats {
        return IndexStats(
            totalFiles = 0,
            indexedFiles = 0,
            totalSymbols = 0,
            lastIndexTime = System.currentTimeMillis(),
            indexSizeBytes = 0,
            supportedFileTypes = listOf("kt", "java", "js", "ts", "py", "md", "json", "xml")
        )
    }
    
    override suspend fun refreshIndex() {
        // IntelliJ 自动管理
    }
    
    override suspend fun cleanup() {
        // 无需清理
    }
    
    private fun createFileInfo(virtualFile: VirtualFile): IndexedFileInfo? {
        return try {
            val projectPath = project.basePath ?: return null
            val relativePath = virtualFile.path.removePrefix(projectPath).removePrefix("/")
            
            IndexedFileInfo(
                name = virtualFile.name,
                relativePath = relativePath,
                absolutePath = virtualFile.path,
                fileType = virtualFile.extension ?: "",
                size = virtualFile.length,
                lastModified = virtualFile.modificationStamp,
                isDirectory = virtualFile.isDirectory,
                language = detectLanguage(virtualFile.extension ?: ""),
                encoding = virtualFile.charset.name()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun detectLanguage(extension: String): String? {
        return when (extension.lowercase()) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "py" -> "Python"
            "md" -> "Markdown"
            "json" -> "JSON"
            "xml" -> "XML"
            else -> null
        }
    }
}