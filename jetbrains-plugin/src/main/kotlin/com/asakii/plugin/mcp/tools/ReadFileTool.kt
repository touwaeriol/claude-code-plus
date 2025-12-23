package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 读取文件工具
 *
 * 利用 IDEA 的 VFS 能够读取任何文件：
 * 1. 项目内的普通文件
 * 2. JAR/ZIP 包内的文件（如依赖库中的源码）
 * 3. JDK 源码（src.zip 中的 .java 文件）
 * 4. .class 文件（自动反编译为可读源码）
 *
 * 支持的路径格式：
 * - 普通路径: C:/path/to/file.java
 * - JAR 内路径: C:/path/to/file.jar!/com/example/MyClass.class
 * - jar:// URL: jar://C:/path/to/file.jar!/com/example/MyClass.class
 * - JDK 源码: C:/jdk/lib/src.zip!/java.base/java/lang/String.java
 */
class ReadFileTool(private val project: Project) {

    fun execute(arguments: Map<String, Any>): Any {
        val filePath = arguments["filePath"] as? String
            ?: return ToolResult.error("Missing required parameter: filePath")

        val maxLines = ((arguments["maxLines"] as? Number)?.toInt() ?: 500).coerceIn(1, 5000)
        val offset = ((arguments["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

        logger.info { "ReadFile: path=$filePath, maxLines=$maxLines, offset=$offset" }

        // 等待索引完成
        DumbService.getInstance(project).waitForSmartMode()

        return try {
            ReadAction.compute<Any, Exception> {
                val virtualFile = resolveVirtualFile(filePath)
                    ?: return@compute ToolResult.error("File not found: $filePath")

                val content = getFileContent(virtualFile)
                    ?: return@compute ToolResult.error("Cannot read file content: $filePath (possibly binary or unsupported format)")

                formatOutput(filePath, virtualFile, content, offset, maxLines)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error reading file: $filePath" }
            ToolResult.error("Error reading file: ${e.message}")
        }
    }

    /**
     * 解析文件路径为 VirtualFile
     *
     * 支持多种路径格式：
     * - 普通文件路径
     * - jar://...!/... (IDEA 标准格式)
     * - C:/path.jar!/... (简化格式)
     * - jrt://module/... (JDK 9+ 模块)
     */
    private fun resolveVirtualFile(path: String): VirtualFile? {
        // 1. 尝试直接作为 URL 解析
        VirtualFileManager.getInstance().findFileByUrl(path)?.let { return it }

        // 2. 处理包含 !/ 的 JAR 路径
        if (path.contains("!/")) {
            // 转换为标准 jar:// URL 格式
            val jarUrl = if (path.startsWith("jar://")) {
                path
            } else {
                // C:/path/to.jar!/inner/path -> jar://C:/path/to.jar!/inner/path
                "jar://${path.replace("\\", "/")}"
            }

            VirtualFileManager.getInstance().findFileByUrl(jarUrl)?.let { return it }

            // 尝试使用 JarFileSystem
            val parts = path.split("!/", limit = 2)
            if (parts.size == 2) {
                val jarPath = parts[0].removePrefix("jar://").replace("\\", "/")
                val innerPath = parts[1]

                val jarFile = VirtualFileManager.getInstance().findFileByUrl("file://$jarPath")
                    ?: VirtualFileManager.getInstance().findFileByUrl(jarPath)

                if (jarFile != null) {
                    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile)
                    jarRoot?.findFileByRelativePath(innerPath)?.let { return it }
                }
            }
        }

        // 3. 尝试 jrt:// (Java Runtime) 格式
        if (path.startsWith("jrt://") || path.contains("/lib/src.zip!/")) {
            VirtualFileManager.getInstance().findFileByUrl(path)?.let { return it }
        }

        // 4. 尝试作为普通文件路径
        val normalizedPath = path.replace("\\", "/")
        VirtualFileManager.getInstance().findFileByUrl("file://$normalizedPath")?.let { return it }

        return null
    }

    /**
     * 获取文件内容
     *
     * 对于 .class 文件，尝试获取反编译后的源码
     */
    private fun getFileContent(file: VirtualFile): String? {
        // 对于 .class 文件，尝试反编译
        if (file.extension == "class") {
            return getDecompiledContent(file)
        }

        // 对于源码文件，直接读取
        return try {
            // 检查是否为文本文件
            if (file.fileType.isBinary && file.extension != "class") {
                return null
            }
            String(file.contentsToByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read file content: ${file.path}" }
            null
        }
    }

    /**
     * 获取 .class 文件的反编译内容
     *
     * 利用 IDEA 内置的反编译器（通常是 Fernflower）
     */
    private fun getDecompiledContent(classFile: VirtualFile): String? {
        return try {
            // 使用 PsiManager 获取 PSI 表示（会自动触发反编译）
            val psiFile = PsiManager.getInstance(project).findFile(classFile)

            if (psiFile != null) {
                // 获取反编译后的文本
                psiFile.text
            } else {
                logger.warn { "Cannot create PSI for class file: ${classFile.path}" }
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to decompile class file: ${classFile.path}" }
            null
        }
    }

    /**
     * 格式化输出
     */
    @Suppress("UNUSED_PARAMETER")
    private fun formatOutput(
        originalPath: String,  // 保留以便将来使用
        file: VirtualFile,
        content: String,
        offset: Int,
        maxLines: Int
    ): String {
        val lines = content.lines()
        val totalLines = lines.size

        // 应用分页
        val startLine = offset.coerceIn(0, maxOf(0, totalLines - 1))
        val endLine = (startLine + maxLines).coerceAtMost(totalLines)
        val selectedLines = lines.subList(startLine, endLine)

        val sb = StringBuilder()

        // 头部信息
        sb.appendLine("## File: `${file.name}`")
        sb.appendLine()
        sb.appendLine("**Path:** `${file.path}`")
        sb.appendLine("**Type:** ${getFileTypeDescription(file)}")
        sb.appendLine("**Total Lines:** $totalLines")

        if (offset > 0 || endLine < totalLines) {
            sb.appendLine("**Showing:** lines ${startLine + 1}-$endLine of $totalLines")
        }

        sb.appendLine()

        // 确定语言用于代码块
        val language = getLanguageForFile(file)
        sb.appendLine("```$language")

        // 带行号的内容
        selectedLines.forEachIndexed { index, line ->
            val lineNumber = startLine + index + 1
            val lineNumStr = lineNumber.toString().padStart(5)
            // 截断过长的行
            val truncatedLine = if (line.length > 500) {
                line.take(500) + "... (truncated)"
            } else {
                line
            }
            sb.appendLine("$lineNumStr | $truncatedLine")
        }

        sb.appendLine("```")

        // 分页提示
        if (endLine < totalLines) {
            sb.appendLine()
            sb.appendLine("*More content available. Use `offset=${endLine}` to continue reading.*")
        }

        return sb.toString()
    }

    /**
     * 获取文件类型描述
     */
    private fun getFileTypeDescription(file: VirtualFile): String {
        return when {
            file.extension == "class" -> "Java Class (decompiled)"
            file.path.contains(".jar!/") -> "JAR entry (${file.extension ?: "unknown"})"
            file.path.contains("src.zip!/") -> "JDK source"
            file.path.contains("/jrt:/") -> "JDK module"
            else -> file.fileType.name
        }
    }

    /**
     * 获取文件对应的语言标识（用于代码高亮）
     */
    private fun getLanguageForFile(file: VirtualFile): String {
        return when (file.extension?.lowercase()) {
            "java", "class" -> "java"
            "kt", "kts" -> "kotlin"
            "groovy", "gradle" -> "groovy"
            "scala" -> "scala"
            "xml", "xsd", "xsl" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "properties" -> "properties"
            "md" -> "markdown"
            "html", "htm" -> "html"
            "css" -> "css"
            "js" -> "javascript"
            "ts" -> "typescript"
            "py" -> "python"
            "rb" -> "ruby"
            "rs" -> "rust"
            "go" -> "go"
            "c", "h" -> "c"
            "cpp", "hpp", "cc" -> "cpp"
            "cs" -> "csharp"
            "sql" -> "sql"
            "sh", "bash" -> "bash"
            "ps1" -> "powershell"
            "txt" -> "text"
            else -> ""
        }
    }
}
