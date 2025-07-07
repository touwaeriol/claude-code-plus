package com.claudecodeplus.idea

import com.claudecodeplus.core.interfaces.FileIndexService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 使用IDEA平台索引的实现
 */
class IdeaFileIndexService(
    private val project: Project
) : FileIndexService {
    
    override suspend fun searchByName(query: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        ApplicationManager.getApplication().runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            
            // 精确匹配文件名
            val exactMatches = FilenameIndex.getFilesByName(project, query, scope)
                .mapNotNull { it.virtualFile?.path }
                .map { getRelativePath(it) }
            
            results.addAll(exactMatches)
            
            // 如果精确匹配不够，搜索包含关键词的文件
            if (results.size < limit) {
                FilenameIndex.getAllFilenames(project).forEach { filename ->
                    if (results.size >= limit) return@forEach
                    
                    if (filename.contains(query, ignoreCase = true) && filename != query) {
                        val files = FilenameIndex.getFilesByName(project, filename, scope)
                        files.forEach { psiFile ->
                            if (results.size < limit) {
                                psiFile.virtualFile?.path?.let { path ->
                                    val relativePath = getRelativePath(path)
                                    if (relativePath !in results) {
                                        results.add(relativePath)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        results
    }
    
    override suspend fun searchByContent(query: String, filePattern: String?, limit: Int): List<String> {
        // IDEA平台的内容搜索比较复杂，这里简化处理
        // 实际项目中可以使用 FindManager 或 UsageViewManager
        return emptyList()
    }
    
    override suspend fun getRecentFiles(limit: Int): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        ApplicationManager.getApplication().runReadAction {
            val editorManager = FileEditorManager.getInstance(project)
            val openFiles = editorManager.openFiles
            
            openFiles.take(limit).forEach { file ->
                results.add(getRelativePath(file.path))
            }
            
            // 如果打开的文件不够，添加一些常见文件
            if (results.size < limit) {
                val scope = GlobalSearchScope.projectScope(project)
                val commonFiles = listOf("README.md", "build.gradle.kts", "pom.xml", "package.json")
                
                commonFiles.forEach { fileName ->
                    if (results.size >= limit) return@forEach
                    
                    val files = FilenameIndex.getFilesByName(project, fileName, scope)
                    files.firstOrNull()?.virtualFile?.path?.let { path ->
                        val relativePath = getRelativePath(path)
                        if (relativePath !in results) {
                            results.add(relativePath)
                        }
                    }
                }
            }
        }
        
        results
    }
    
    override suspend fun refreshIndex(path: String?) {
        // IDEA平台会自动管理索引，这里不需要手动刷新
    }
    
    override fun isIndexReady(): Boolean {
        // IDEA索引通常总是准备好的
        return true
    }
    
    private fun getRelativePath(absolutePath: String): String {
        val basePath = project.basePath ?: return absolutePath
        return if (absolutePath.startsWith(basePath)) {
            absolutePath.substring(basePath.length + 1)
        } else {
            absolutePath
        }
    }
}