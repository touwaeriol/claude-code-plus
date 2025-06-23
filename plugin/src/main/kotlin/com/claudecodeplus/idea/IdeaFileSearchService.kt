package com.claudecodeplus.idea

import com.claudecodeplus.core.interfaces.FileIndexService
import com.claudecodeplus.core.interfaces.FileSearchService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * IDEA平台的文件搜索服务实现
 */
class IdeaFileSearchService(
    private val project: Project
) : FileSearchService {
    
    private val indexService = IdeaFileIndexService(project)
    
    override suspend fun searchFiles(searchText: String): List<String> = withContext(Dispatchers.IO) {
        if (searchText.isBlank()) {
            getRecentFiles()
        } else {
            indexService.searchByName(searchText)
        }
    }
    
    override suspend fun getRecentFiles(limit: Int): List<String> = withContext(Dispatchers.IO) {
        indexService.getRecentFiles(limit)
    }
    
    override fun fileExists(path: String): Boolean {
        val basePath = project.basePath ?: return false
        val fullPath = if (path.startsWith("/")) path else "$basePath/$path"
        return File(fullPath).exists()
    }
    
    override fun readFileContent(path: String): String? {
        return try {
            val basePath = project.basePath ?: return null
            val fullPath = if (path.startsWith("/")) path else "$basePath/$path"
            File(fullPath).readText()
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getIndexService(): FileIndexService = indexService
    
    suspend fun getFileContent(filePath: String): String? {
        return readFileContent(filePath)
    }
    
    data class FileResult(
        val fileName: String,
        val filePath: String,
        val relativePath: String
    )
    
    suspend fun searchFiles(query: String, limit: Int): List<FileResult> = withContext(Dispatchers.IO) {
        val files = searchFiles(query).take(limit)
        val basePath = project.basePath ?: ""
        
        files.map { filePath ->
            FileResult(
                fileName = File(filePath).name,
                filePath = filePath,
                relativePath = filePath.replace("$basePath/", "")
            )
        }
    }
}