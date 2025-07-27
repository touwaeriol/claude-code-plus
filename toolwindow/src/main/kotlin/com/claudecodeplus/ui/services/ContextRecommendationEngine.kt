package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 智能上下文推荐引擎
 */
class ContextRecommendationEngine(
    private val fileIndexService: FileIndexService
) {
    
    // 文件关联规则
    private val fileAssociations = mapOf(
        // 构建文件
        "build.gradle" to listOf("settings.gradle", "gradle.properties", "gradlew", "gradle/wrapper/gradle-wrapper.properties"),
        "build.gradle.kts" to listOf("settings.gradle.kts", "gradle.properties", "gradlew", "gradle/wrapper/gradle-wrapper.properties"),
        "pom.xml" to listOf("settings.xml", ".mvn/wrapper/maven-wrapper.properties"),
        
        // 包管理
        "package.json" to listOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml", ".npmrc", "tsconfig.json", ".eslintrc.js"),
        "Cargo.toml" to listOf("Cargo.lock", "rust-toolchain.toml"),
        "go.mod" to listOf("go.sum"),
        "requirements.txt" to listOf("setup.py", "pyproject.toml", "Pipfile", "Pipfile.lock"),
        
        // 配置文件
        "Dockerfile" to listOf("docker-compose.yml", ".dockerignore", "docker-compose.override.yml"),
        ".gitignore" to listOf(".gitattributes", ".git/config"),
        "tsconfig.json" to listOf("tsconfig.*.json", "jest.config.js", ".eslintrc.js"),
        
        // 测试文件
        "*.test.kt" to listOf("src/main/kotlin/**/*.kt"),
        "*.spec.ts" to listOf("src/**/*.ts"),
        "*Test.java" to listOf("src/main/java/**/*.java")
    )
    
    // 技术栈识别模式
    private val techStackPatterns = mapOf(
        "Spring Boot" to listOf("@SpringBootApplication", "@RestController", "application.properties", "application.yml"),
        "React" to listOf("import React", "ReactDOM.render", "useState", "useEffect"),
        "Vue" to listOf("new Vue", "<template>", "export default {", "v-model"),
        "Angular" to listOf("@Component", "@NgModule", "angular.json"),
        "Django" to listOf("from django", "settings.py", "urls.py", "models.py"),
        "FastAPI" to listOf("from fastapi", "@app.get", "@app.post")
    )
    
    /**
     * 推荐上下文
     */
    suspend fun recommendContext(
        currentContext: List<ContextItem>,
        recentMessages: List<EnhancedMessage>,
        projectPath: String,
        limit: Int = 10
    ): List<ContextSuggestion> = withContext(Dispatchers.Default) {
        val suggestions = mutableListOf<ContextSuggestion>()
        
        // 1. 基于当前文件的关联推荐
        val fileBasedSuggestions = recommendBasedOnFiles(currentContext, projectPath)
        suggestions.addAll(fileBasedSuggestions)
        
        // 2. 基于对话内容的推荐
        val conversationBasedSuggestions = recommendBasedOnConversation(recentMessages, projectPath)
        suggestions.addAll(conversationBasedSuggestions)
        
        // 3. 基于技术栈的推荐
        val techStackSuggestions = recommendBasedOnTechStack(currentContext, projectPath)
        suggestions.addAll(techStackSuggestions)
        
        // 4. 基于文件修改时间的推荐
        val recentlyModifiedSuggestions = recommendRecentlyModified(projectPath, currentContext)
        suggestions.addAll(recentlyModifiedSuggestions)
        
        // 5. 基于导入/依赖的推荐
        val dependencySuggestions = recommendBasedOnDependencies(currentContext, projectPath)
        suggestions.addAll(dependencySuggestions)
        
        // 去重并排序
        suggestions
            .distinctBy { getContextItemKey(it.item) }
            .sortedByDescending { it.confidence }
            .take(limit)
    }
    
    /**
     * 基于当前文件推荐相关文件
     */
    private suspend fun recommendBasedOnFiles(
        currentContext: List<ContextItem>,
        projectPath: String
    ): List<ContextSuggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<ContextSuggestion>()
        
        currentContext.filterIsInstance<ContextItem.File>().forEach { contextFile ->
            val file = File(contextFile.path)
            val fileName = file.name
            
            // 检查文件关联规则
            fileAssociations.forEach { (pattern, relatedFiles) ->
                if (matchesPattern(fileName, pattern)) {
                    relatedFiles.forEach { relatedPattern ->
                        val relatedFile = findRelatedFile(file.parentFile, relatedPattern, projectPath)
                        if (relatedFile != null && !isInContext(relatedFile, currentContext)) {
                            suggestions.add(
                                ContextSuggestion(
                                    item = ContextItem.File(path = relatedFile.absolutePath),
                                    reason = "与 $fileName 相关",
                                    confidence = 0.8f,
                                    source = ContextSuggestion.SuggestionSource.FILE_ASSOCIATION
                                )
                            )
                        }
                    }
                }
            }
            
            // 推荐同目录下的相关文件
            file.parentFile?.listFiles()?.forEach { sibling ->
                if (sibling.isFile && sibling != file && isRelatedFile(file, sibling)) {
                    if (!isInContext(sibling, currentContext)) {
                        suggestions.add(
                            ContextSuggestion(
                                item = ContextItem.File(path = sibling.absolutePath),
                                reason = "同目录相关文件",
                                confidence = 0.6f,
                                source = ContextSuggestion.SuggestionSource.PROJECT_STRUCTURE
                            )
                        )
                    }
                }
            }
        }
        
        suggestions
    }
    
    /**
     * 基于对话内容推荐
     */
    private suspend fun recommendBasedOnConversation(
        recentMessages: List<EnhancedMessage>,
        projectPath: String
    ): List<ContextSuggestion> = withContext(Dispatchers.Default) {
        val suggestions = mutableListOf<ContextSuggestion>()
        
        // 提取关键词
        val keywords = extractKeywords(recentMessages)
        
        // 搜索相关文件
        keywords.forEach { keyword ->
            val searchResults = fileIndexService.searchFiles(keyword, maxResults = 5)
            searchResults.forEach { result ->
                suggestions.add(
                    ContextSuggestion(
                        item = ContextItem.File(path = result.absolutePath),
                        reason = "对话中提到 \"$keyword\"",
                        confidence = 0.7f,
                        source = ContextSuggestion.SuggestionSource.CONVERSATION_ANALYSIS
                    )
                )
            }
        }
        
        // 识别代码片段中的文件引用
        recentMessages.forEach { message ->
            val fileReferences = extractFileReferences(message.content)
            fileReferences.forEach { ref ->
                val file = File(projectPath, ref)
                if (file.exists() && file.isFile) {
                    suggestions.add(
                        ContextSuggestion(
                            item = ContextItem.File(path = file.absolutePath),
                            reason = "对话中引用了此文件",
                            confidence = 0.9f,
                            source = ContextSuggestion.SuggestionSource.CONVERSATION_ANALYSIS
                        )
                    )
                }
            }
        }
        
        suggestions
    }
    
    /**
     * 基于技术栈推荐
     */
    private suspend fun recommendBasedOnTechStack(
        currentContext: List<ContextItem>,
        projectPath: String
    ): List<ContextSuggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<ContextSuggestion>()
        val detectedTechStacks = mutableSetOf<String>()
        
        // 检测技术栈
        currentContext.filterIsInstance<ContextItem.File>().forEach { contextFile ->
            val content = try {
                File(contextFile.path).readText()
            } catch (e: Exception) {
                return@forEach
            }
            
            techStackPatterns.forEach { (tech, patterns) ->
                if (patterns.any { pattern -> content.contains(pattern) }) {
                    detectedTechStacks.add(tech)
                }
            }
        }
        
        // 基于检测到的技术栈推荐文件
        detectedTechStacks.forEach { tech ->
            when (tech) {
                "Spring Boot" -> {
                    suggestFiles(projectPath, listOf(
                        "src/main/resources/application.properties",
                        "src/main/resources/application.yml",
                        "src/main/java/**/Application.java"
                    ), "Spring Boot 配置文件", suggestions)
                }
                "React" -> {
                    suggestFiles(projectPath, listOf(
                        "src/App.js",
                        "src/App.tsx",
                        "src/index.js",
                        "public/index.html"
                    ), "React 核心文件", suggestions)
                }
                "Django" -> {
                    suggestFiles(projectPath, listOf(
                        "manage.py",
                        "*/settings.py",
                        "*/urls.py",
                        "*/wsgi.py"
                    ), "Django 核心文件", suggestions)
                }
            }
        }
        
        suggestions
    }
    
    /**
     * 推荐最近修改的文件
     */
    private suspend fun recommendRecentlyModified(
        projectPath: String,
        currentContext: List<ContextItem>
    ): List<ContextSuggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<ContextSuggestion>()
        
        // 获取最近修改的文件
        val recentFiles = fileIndexService.getRecentlyModifiedFiles(projectPath, limit = 20)
        
        recentFiles
            .filter { !isInContext(File(it.relativePath), currentContext) }
            .take(5)
            .forEach { fileInfo ->
                suggestions.add(
                    ContextSuggestion(
                        item = ContextItem.File(path = fileInfo.relativePath),
                        reason = "最近修改的文件",
                        confidence = 0.5f,
                        source = ContextSuggestion.SuggestionSource.USAGE_PATTERN
                    )
                )
            }
        
        suggestions
    }
    
    /**
     * 基于依赖关系推荐
     */
    private suspend fun recommendBasedOnDependencies(
        currentContext: List<ContextItem>,
        projectPath: String
    ): List<ContextSuggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<ContextSuggestion>()
        
        currentContext.filterIsInstance<ContextItem.File>().forEach { contextFile ->
            val file = File(contextFile.path)
            if (file.extension in listOf("kt", "java", "ts", "js", "py")) {
                val imports = extractImports(file)
                
                imports.forEach { import ->
                    val relatedFile = resolveImportToFile(import, file, projectPath)
                    if (relatedFile != null && !isInContext(relatedFile, currentContext)) {
                        suggestions.add(
                            ContextSuggestion(
                                item = ContextItem.File(path = relatedFile.absolutePath),
                                reason = "被 ${file.name} 导入",
                                confidence = 0.85f,
                                source = ContextSuggestion.SuggestionSource.FILE_ASSOCIATION
                            )
                        )
                    }
                }
            }
        }
        
        suggestions
    }
    
    // 辅助方法
    
    private fun matchesPattern(fileName: String, pattern: String): Boolean {
        return if (pattern.contains("*")) {
            val regex = pattern.replace("*", ".*").toRegex()
            regex.matches(fileName)
        } else {
            fileName == pattern
        }
    }
    
    private fun findRelatedFile(directory: File, pattern: String, projectPath: String): File? {
        // 首先在当前目录查找
        directory.listFiles()?.forEach { file ->
            if (matchesPattern(file.name, pattern)) {
                return file
            }
        }
        
        // 然后在项目根目录查找
        val projectDir = File(projectPath)
        projectDir.walkTopDown().forEach { file ->
            if (file.isFile && matchesPattern(file.name, pattern)) {
                return file
            }
        }
        
        return null
    }
    
    private fun isRelatedFile(file1: File, file2: File): Boolean {
        val name1 = file1.nameWithoutExtension
        val name2 = file2.nameWithoutExtension
        
        // 检查常见的相关文件模式
        return when {
            // 测试文件
            name2 == "${name1}Test" || name2 == "${name1}Spec" -> true
            name2 == "${name1}.test" || name2 == "${name1}.spec" -> true
            
            // 接口和实现
            name2 == "${name1}Impl" || name2 == "${name1}Implementation" -> true
            name2.startsWith(name1) && name2.endsWith("Service") -> true
            
            // 配置文件
            name1 == name2 && file1.extension != file2.extension -> true
            
            else -> false
        }
    }
    
    private fun isInContext(file: File, context: List<ContextItem>): Boolean {
        return context.any { item ->
            when (item) {
                is ContextItem.File -> File(item.path) == file
                is ContextItem.Folder -> file.absolutePath.startsWith(item.path)
                else -> false
            }
        }
    }
    
    private fun extractKeywords(messages: List<EnhancedMessage>): List<String> {
        val keywords = mutableSetOf<String>()
        val stopWords = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for")
        
        messages.forEach { message ->
            // 提取驼峰命名和下划线命名的标识符
            val identifiers = Regex("[A-Z][a-z]+|[a-z]+(?=[A-Z])|[a-zA-Z]+_[a-zA-Z]+|[a-zA-Z]{3,}")
                .findAll(message.content)
                .map { it.value }
                .filter { it.length > 3 && it.lowercase() !in stopWords }
            
            keywords.addAll(identifiers)
        }
        
        return keywords.toList()
    }
    
    private fun extractFileReferences(content: String): List<String> {
        val references = mutableListOf<String>()
        
        // 匹配文件路径模式
        val patterns = listOf(
            Regex("([\\w/]+\\.[a-zA-Z]+)"), // 基本文件路径
            Regex("\"([^\"]+\\.[a-zA-Z]+)\""), // 引号中的文件路径
            Regex("'([^']+\\.[a-zA-Z]+)'"), // 单引号中的文件路径
            Regex("`([^`]+\\.[a-zA-Z]+)`") // 反引号中的文件路径
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                val path = match.groupValues.last()
                if (path.isNotBlank() && !path.startsWith("http")) {
                    references.add(path)
                }
            }
        }
        
        return references.distinct()
    }
    
    private fun suggestFiles(
        projectPath: String,
        patterns: List<String>,
        reason: String,
        suggestions: MutableList<ContextSuggestion>
    ) {
        patterns.forEach { pattern ->
            val file = if (pattern.contains("*")) {
                // 处理通配符
                File(projectPath).walkTopDown()
                    .filter { it.isFile }
                    .firstOrNull { matchesPattern(it.absolutePath, pattern) }
            } else {
                File(projectPath, pattern).takeIf { it.exists() && it.isFile }
            }
            
            file?.let {
                suggestions.add(
                    ContextSuggestion(
                        item = ContextItem.File(path = it.absolutePath),
                        reason = reason,
                        confidence = 0.7f,
                        source = ContextSuggestion.SuggestionSource.PROJECT_STRUCTURE
                    )
                )
            }
        }
    }
    
    private fun extractImports(file: File): List<String> {
        val imports = mutableListOf<String>()
        
        try {
            val content = file.readText()
            
            when (file.extension) {
                "kt", "java" -> {
                    Regex("import\\s+([\\w.]+)").findAll(content).forEach {
                        imports.add(it.groupValues[1])
                    }
                }
                "ts", "js" -> {
                    Regex("import\\s+.*\\s+from\\s+['\"]([^'\"]+)['\"]").findAll(content).forEach {
                        imports.add(it.groupValues[1])
                    }
                }
                "py" -> {
                    Regex("(?:from\\s+([\\w.]+)\\s+)?import\\s+([\\w.]+)").findAll(content).forEach {
                        val module = it.groupValues[1].ifEmpty { it.groupValues[2] }
                        imports.add(module)
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略读取错误
        }
        
        return imports
    }
    
    private fun resolveImportToFile(import: String, fromFile: File, projectPath: String): File? {
        // 简化的导入解析逻辑
        val projectDir = File(projectPath)
        
        return when (fromFile.extension) {
            "kt", "java" -> {
                val path = import.replace('.', '/')
                projectDir.walkTopDown()
                    .filter { it.isFile }
                    .firstOrNull { 
                        it.absolutePath.contains(path) && 
                        it.extension in listOf("kt", "java")
                    }
            }
            "ts", "js" -> {
                if (import.startsWith(".")) {
                    // 相对导入
                    val resolved = File(fromFile.parentFile, import)
                    listOf("$resolved.ts", "$resolved.js", "$resolved/index.ts", "$resolved/index.js")
                        .map { File(it) }
                        .firstOrNull { it.exists() }
                } else {
                    // 模块导入
                    null
                }
            }
            "py" -> {
                val path = import.replace('.', '/')
                listOf("$path.py", "$path/__init__.py")
                    .map { File(projectDir, it) }
                    .firstOrNull { it.exists() }
            }
            else -> null
        }
    }
    
    private fun getContextItemKey(item: ContextItem): String {
        return when (item) {
            is ContextItem.File -> "file:${item.path}"
            is ContextItem.Folder -> "folder:${item.path}"
            is ContextItem.CodeBlock -> "code:${item.content.hashCode()}"
        }
    }
}