/*
 * ContextProcessor.kt
 * 
 * 上下文处理工具类 - 负责上下文内容格式化和前缀生成
 */

package com.claudecodeplus.ui.services

import com.claudecodeplus.core.logging.*
import com.claudecodeplus.ui.models.ContextReference
import java.io.File
import kotlin.text.Charsets

/**
 * 上下文处理器
 * 提供上下文内容格式化和前缀生成功能
 */
object ContextProcessor {
    
    /**
     * 为提示构建包含上下文文件内容的前缀
     * @param contexts 上下文引用列表
     * @param projectCwd 项目工作目录
     * @param originalPrompt 原始用户提示
     * @return 包含上下文内容的完整提示
     */
    fun buildPromptWithContextFiles(
        contexts: List<ContextReference>,
        projectCwd: String,
        originalPrompt: String
    ): String {
        if (contexts.isEmpty()) return originalPrompt
        
        val contextFilesContent = buildContextFilesXml(contexts, projectCwd)
        return if (contextFilesContent.isNotBlank()) {
            contextFilesContent + originalPrompt
        } else {
            originalPrompt
        }
    }
    
    /**
     * 构建上下文文件的 XML 格式内容
     * @param contexts 上下文引用列表
     * @param projectCwd 项目工作目录
     * @return XML 格式的上下文内容字符串
     */
    private fun buildContextFilesXml(contexts: List<ContextReference>, projectCwd: String): String {
        val validContexts = mutableListOf<Pair<ContextReference.FileReference, String>>()
        
        contexts.forEach { context ->
            when (context) {
                is ContextReference.FileReference -> {
                    val content = readFileContent(context, projectCwd)
                    if (content != null) {
                        validContexts.add(context to content)
                    }
                }
                // 其他类型的上下文可以在这里扩展
                else -> {
                    // 暂不处理其他类型的上下文
                }
            }
        }
        
        if (validContexts.isEmpty()) return ""
        
        return buildString {
            append("<context_files>\n")
            validContexts.forEach { (context, content) ->
                append("<file path=\"${context.path}\">\n")
                append(content)
                append("\n</file>\n")
            }
            append("</context_files>\n\n")
        }
    }
    
    /**
     * 读取文件内容
     * @param context 文件上下文引用
     * @param projectCwd 项目工作目录
     * @return 文件内容，如果读取失败返回 null
     */
    private fun readFileContent(context: ContextReference.FileReference, projectCwd: String): String? {
        return try {
            // 构建文件完整路径
            val filePath = if (context.path.startsWith("/")) {
                context.path // 绝对路径
            } else {
                File(projectCwd, context.path).absolutePath // 相对路径
            }
            
            val file = File(filePath)
            if (file.exists() && file.isFile()) {
                file.readText(Charsets.UTF_8)
            } else {
    //                 logD("[ContextProcessor] 文件不存在或不是文件: $filePath")
                null
            }
        } catch (e: Exception) {
    //             logD("[ContextProcessor] 读取文件失败: ${context.path}, 错误: ${e.message}")
            null
        }
    }
    
    /**
     * 生成前缀信息格式
     * @param contexts 上下文引用列表
     * @return 前缀信息格式的字符串
     */
    fun generateFrontMatter(contexts: List<ContextReference>): String {
        if (contexts.isEmpty()) return ""
        
        return buildString {
            appendLine("---")
            appendLine("contexts:")
            contexts.forEach { context ->
                when (context) {
                    is ContextReference.FileReference -> {
                        appendLine("  - file:@${context.path}")
                    }
                    is ContextReference.WebReference -> {
                        appendLine("  - web:${context.url}")
                    }
                    is ContextReference.FolderReference -> {
                        appendLine("  - folder:@${context.path}")
                    }
                    is ContextReference.SymbolReference -> {
                        appendLine("  - symbol:${context.name}")
                    }
                    is ContextReference.ImageReference -> {
                        appendLine("  - image:@${context.path}")
                    }
                    is ContextReference.GitReference -> {
                        appendLine("  - git:${context.type.name.lowercase()}")
                    }
                    is ContextReference.ProblemsReference -> {
                        appendLine("  - problems:${context.severity?.name?.lowercase() ?: "all"}")
                    }
                    is ContextReference.TerminalReference -> {
                        appendLine("  - terminal:${context.lines}")
                    }
                    ContextReference.SelectionReference -> {
                        appendLine("  - selection:current")
                    }
                    ContextReference.WorkspaceReference -> {
                        appendLine("  - workspace:current")
                    }
                }
            }
            appendLine("---")
        }
    }
    
    /**
     * 解析前缀信息格式
     * @param frontMatter 前缀信息字符串
     * @return 解析出的上下文引用列表
     */
    fun parseFrontMatter(frontMatter: String): List<ContextReference> {
        val contexts = mutableListOf<ContextReference>()
        val lines = frontMatter.lines()
        
        var insideContexts = false
        for (line in lines) {
            val trimmedLine = line.trim()
            
            if (trimmedLine == "---") {
                continue
            } else if (trimmedLine == "contexts:") {
                insideContexts = true
                continue
            } else if (insideContexts && trimmedLine.startsWith("- ")) {
                val contextLine = trimmedLine.substring(2).trim()
                
                when {
                    contextLine.startsWith("file:") -> {
                        val rawPath = contextLine.substring(5).trim()
                        // 处理 file:@path 格式，移除 @ 符号
                        val path = if (rawPath.startsWith("@")) rawPath.substring(1) else rawPath
                        contexts.add(ContextReference.FileReference(path = path))
                    }
                    contextLine.startsWith("web:") -> {
                        val url = contextLine.substring(4).trim()
                        contexts.add(ContextReference.WebReference(url = url, title = null))
                    }
                    contextLine.startsWith("folder:") -> {
                        val rawPath = contextLine.substring(7).trim()
                        val path = if (rawPath.startsWith("@")) rawPath.substring(1) else rawPath
                        contexts.add(ContextReference.FolderReference(path = path))
                    }
                    contextLine.startsWith("symbol:") -> {
                        val name = contextLine.substring(7).trim()
                        contexts.add(ContextReference.SymbolReference(
                            name = name,
                            type = com.claudecodeplus.ui.models.SymbolType.FUNCTION, // 默认类型
                            file = "", // 从前端格式无法获取文件信息
                            line = 0   // 从前端格式无法获取行号信息
                        ))
                    }
                    contextLine.startsWith("image:") -> {
                        val rawPath = contextLine.substring(6).trim()
                        val path = if (rawPath.startsWith("@")) rawPath.substring(1) else rawPath
                        val filename = path.substringAfterLast('/')
                        contexts.add(ContextReference.ImageReference(path = path, filename = filename))
                    }
                    contextLine.startsWith("git:") -> {
                        val type = contextLine.substring(4).trim()
                        contexts.add(ContextReference.GitReference(
                            type = com.claudecodeplus.ui.models.GitRefType.valueOf(type.uppercase()),
                            content = ""
                        ))
                    }
                    contextLine.startsWith("problems:") -> {
                        val severity = contextLine.substring(9).trim()
                        val problemSeverity = if (severity != "all") {
                            com.claudecodeplus.ui.models.ProblemSeverity.valueOf(severity.uppercase())
                        } else null
                        contexts.add(ContextReference.ProblemsReference(
                            problems = emptyList(),
                            severity = problemSeverity
                        ))
                    }
                    contextLine.startsWith("terminal:") -> {
                        val lines = contextLine.substring(9).trim().toIntOrNull() ?: 50
                        contexts.add(ContextReference.TerminalReference(
                            content = "",
                            lines = lines
                        ))
                    }
                    contextLine == "selection:current" -> {
                        contexts.add(ContextReference.SelectionReference)
                    }
                    contextLine == "workspace:current" -> {
                        contexts.add(ContextReference.WorkspaceReference)
                    }
                }
            }
        }
        
        return contexts
    }
    
    /**
     * 验证上下文引用是否有效
     * @param context 上下文引用
     * @param projectCwd 项目工作目录
     * @return 是否有效
     */
    fun validateContext(context: ContextReference, projectCwd: String): Boolean {
        return when (context) {
            is ContextReference.FileReference -> {
                val filePath = if (context.path.startsWith("/")) {
                    context.path
                } else {
                    File(projectCwd, context.path).absolutePath
                }
                File(filePath).exists()
            }
            is ContextReference.WebReference -> {
                // 简单的 URL 格式验证
                context.url.matches(Regex("^https?://.*"))
            }
            is ContextReference.FolderReference -> {
                val folderPath = if (context.path.startsWith("/")) {
                    context.path
                } else {
                    File(projectCwd, context.path).absolutePath
                }
                File(folderPath).isDirectory()
            }
            is ContextReference.SymbolReference -> {
                // 符号引用暂时认为总是有效
                true
            }
            is ContextReference.ImageReference -> {
                val imagePath = if (context.path.startsWith("/")) {
                    context.path
                } else {
                    File(projectCwd, context.path).absolutePath
                }
                val file = File(imagePath)
                file.exists() && file.isFile() && isImageFile(file)
            }
            is ContextReference.GitReference -> {
                // Git 引用暂时认为总是有效（依赖外部 Git 状态）
                true
            }
            is ContextReference.ProblemsReference -> {
                // 问题引用暂时认为总是有效（依赖外部问题状态）
                true
            }
            is ContextReference.TerminalReference -> {
                // 终端引用暂时认为总是有效（依赖终端状态）
                true
            }
            ContextReference.SelectionReference -> {
                // 选择引用暂时认为总是有效
                true
            }
            ContextReference.WorkspaceReference -> {
                // 工作区引用暂时认为总是有效
                true
            }
        }
    }
    
    /**
     * 检查文件是否为图片文件
     * @param file 文件对象
     * @return 是否为图片文件
     */
    private fun isImageFile(file: File): Boolean {
        val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "svg", "webp")
        val extension = file.extension.lowercase()
        return extension in imageExtensions
    }
    
    /**
     * 获取上下文统计信息
     * @param contexts 上下文引用列表
     * @param projectCwd 项目工作目录
     * @return 统计信息对象
     */
    fun getContextStats(contexts: List<ContextReference>, projectCwd: String): ContextStats {
        var totalFiles = 0
        var validFiles = 0
        var totalSize = 0L
        var fileTypeCount = mutableMapOf<String, Int>()
        
        contexts.forEach { context ->
            when (context) {
                is ContextReference.FileReference -> {
                    totalFiles++
                    val filePath = if (context.path.startsWith("/")) {
                        context.path
                    } else {
                        File(projectCwd, context.path).absolutePath
                    }
                    val file = File(filePath)
                    if (file.exists() && file.isFile()) {
                        validFiles++
                        totalSize += file.length()
                        val extension = file.extension.lowercase().ifEmpty { "no-ext" }
                        fileTypeCount[extension] = fileTypeCount.getOrDefault(extension, 0) + 1
                    }
                }
                is ContextReference.WebReference,
                is ContextReference.FolderReference,
                is ContextReference.SymbolReference,
                is ContextReference.ImageReference,
                is ContextReference.GitReference,
                is ContextReference.ProblemsReference,
                is ContextReference.TerminalReference,
                ContextReference.SelectionReference,
                ContextReference.WorkspaceReference -> {
                    // 其他类型的上下文不计入文件统计
                }
            }
        }
        
        return ContextStats(
            totalContexts = contexts.size,
            totalFiles = totalFiles,
            validFiles = validFiles,
            totalSizeBytes = totalSize,
            fileTypeCount = fileTypeCount
        )
    }
}

/**
 * 上下文统计信息
 */
data class ContextStats(
    val totalContexts: Int,
    val totalFiles: Int,
    val validFiles: Int,
    val totalSizeBytes: Long,
    val fileTypeCount: Map<String, Int>
) {
    /**
     * 获取总大小的可读格式
     */
    fun getTotalSizeFormatted(): String {
        return when {
            totalSizeBytes < 1024 -> "${totalSizeBytes}B"
            totalSizeBytes < 1024 * 1024 -> "${totalSizeBytes / 1024}KB"
            totalSizeBytes < 1024 * 1024 * 1024 -> "${totalSizeBytes / (1024 * 1024)}MB"
            else -> "${totalSizeBytes / (1024 * 1024 * 1024)}GB"
        }
    }
}
