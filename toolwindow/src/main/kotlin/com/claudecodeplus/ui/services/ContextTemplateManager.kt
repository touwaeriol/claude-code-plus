package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant

/**
 * 上下文模板管理器
 */
class ContextTemplateManager {
    private val _templates = MutableStateFlow<List<ContextTemplate>>(emptyList())
    val templates: StateFlow<List<ContextTemplate>> = _templates.asStateFlow()
    
    init {
        loadBuiltInTemplates()
    }
    
    /**
     * 加载内置模板
     */
    private fun loadBuiltInTemplates() {
        val builtInTemplates = listOf(
            ContextTemplate(
                id = "web-project",
                name = "Web 项目",
                description = "适用于前端 Web 项目的标准文件结构",
                icon = "🌐",
                category = "Web",
                items = listOf(
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "package.json",
                        description = "项目配置文件"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "tsconfig.json",
                        description = "TypeScript 配置",
                        optional = true
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FOLDER,
                        value = "src",
                        description = "源代码目录"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.PATTERN,
                        value = "*.config.js",
                        description = "配置文件"
                    )
                ),
                tags = listOf("frontend", "javascript", "typescript"),
                isBuiltIn = true
            ),
            
            ContextTemplate(
                id = "kotlin-gradle",
                name = "Kotlin Gradle 项目",
                description = "Kotlin + Gradle 项目的标准结构",
                icon = "🅺",
                category = "JVM",
                items = listOf(
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "build.gradle.kts",
                        description = "构建配置"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "settings.gradle.kts",
                        description = "项目设置"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FOLDER,
                        value = "src/main/kotlin",
                        description = "Kotlin 源代码"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "gradle.properties",
                        optional = true
                    )
                ),
                tags = listOf("kotlin", "gradle", "jvm"),
                isBuiltIn = true
            ),
            
            ContextTemplate(
                id = "python-project",
                name = "Python 项目",
                description = "Python 项目的标准结构",
                icon = "🐍",
                category = "Python",
                items = listOf(
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "requirements.txt",
                        description = "依赖列表"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "setup.py",
                        optional = true
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "pyproject.toml",
                        optional = true
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.PATTERN,
                        value = "*.py",
                        description = "Python 源文件"
                    )
                ),
                tags = listOf("python", "pip"),
                isBuiltIn = true
            ),
            
            ContextTemplate(
                id = "docker-compose",
                name = "Docker Compose 项目",
                description = "使用 Docker Compose 的项目",
                icon = "🐳",
                category = "DevOps",
                items = listOf(
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "docker-compose.yml",
                        description = "Compose 配置"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "Dockerfile",
                        description = "Docker 镜像定义"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = ".dockerignore",
                        optional = true
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = ".env",
                        description = "环境变量",
                        optional = true
                    )
                ),
                tags = listOf("docker", "container", "devops"),
                isBuiltIn = true
            ),
            
            ContextTemplate(
                id = "react-app",
                name = "React 应用",
                description = "React 单页应用的标准结构",
                icon = "⚛️",
                category = "Web",
                items = listOf(
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "package.json"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FOLDER,
                        value = "src",
                        parameters = mapOf(
                            "includePattern" to "\\.(jsx?|tsx?)$",
                            "excludePattern" to "\\.(test|spec)\\."
                        )
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FOLDER,
                        value = "public"
                    ),
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = "tsconfig.json",
                        optional = true
                    )
                ),
                tags = listOf("react", "frontend", "spa"),
                isBuiltIn = true
            )
        )
        
        _templates.value = builtInTemplates
    }
    
    /**
     * 应用模板到项目路径
     */
    fun applyTemplate(
        template: ContextTemplate,
        projectPath: String
    ): List<ContextItem> {
        val contextItems = mutableListOf<ContextItem>()
        
        template.items.forEach { item ->
            val resolvedPath = File(projectPath, item.value).absolutePath
            
            when (item.type) {
                ContextTemplateItem.TemplateItemType.FILE -> {
                    val file = File(resolvedPath)
                    if (file.exists() || !item.optional) {
                        contextItems.add(ContextItem.File(path = resolvedPath))
                    }
                }
                
                ContextTemplateItem.TemplateItemType.FOLDER -> {
                    val folder = File(resolvedPath)
                    if (folder.exists() && folder.isDirectory) {
                        contextItems.add(
                            ContextItem.Folder(
                                path = resolvedPath,
                                includePattern = item.parameters["includePattern"],
                                excludePattern = item.parameters["excludePattern"]
                            )
                        )
                    } else if (!item.optional) {
                        // 如果文件夹不存在但不是可选的，仍然添加（可能会在验证时报错）
                        contextItems.add(ContextItem.Folder(path = resolvedPath))
                    }
                }
                
                ContextTemplateItem.TemplateItemType.PATTERN -> {
                    // 将模式转换为文件夹上下文，使用项目根目录
                    contextItems.add(
                        ContextItem.Folder(
                            path = projectPath,
                            includePattern = item.value
                        )
                    )
                }
                
                ContextTemplateItem.TemplateItemType.GLOB -> {
                    // Glob 模式也转换为文件夹上下文
                    val globPattern = item.value.replace("**", ".*").replace("*", "[^/]*")
                    contextItems.add(
                        ContextItem.Folder(
                            path = projectPath,
                            includePattern = globPattern
                        )
                    )
                }
            }
        }
        
        // 更新使用统计
        updateTemplateUsage(template.id)
        
        return contextItems
    }
    
    /**
     * 创建自定义模板
     */
    fun createTemplate(
        name: String,
        description: String,
        items: List<ContextTemplateItem>,
        category: String,
        icon: String? = null,
        tags: List<String> = emptyList()
    ): ContextTemplate {
        val template = ContextTemplate(
            name = name,
            description = description,
            icon = icon,
            items = items,
            category = category,
            tags = tags,
            isBuiltIn = false
        )
        
        _templates.value = _templates.value + template
        saveTemplates()
        
        return template
    }
    
    /**
     * 从当前上下文创建模板
     */
    fun createTemplateFromContext(
        name: String,
        description: String,
        context: List<ContextItem>,
        projectPath: String,
        category: String = "自定义"
    ): ContextTemplate {
        val items = context.map { contextItem ->
            when (contextItem) {
                is ContextItem.File -> {
                    val relativePath = try {
                        File(projectPath).toPath().relativize(File(contextItem.path).toPath()).toString()
                    } catch (e: Exception) {
                        contextItem.path
                    }
                    
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FILE,
                        value = relativePath,
                        description = File(contextItem.path).name
                    )
                }
                
                is ContextItem.Folder -> {
                    val relativePath = try {
                        File(projectPath).toPath().relativize(File(contextItem.path).toPath()).toString()
                    } catch (e: Exception) {
                        contextItem.path
                    }
                    
                    ContextTemplateItem(
                        type = ContextTemplateItem.TemplateItemType.FOLDER,
                        value = relativePath,
                        description = File(contextItem.path).name,
                        parameters = buildMap {
                            contextItem.includePattern?.let { put("includePattern", it) }
                            contextItem.excludePattern?.let { put("excludePattern", it) }
                        }
                    )
                }
                
                is ContextItem.CodeBlock -> {
                    // 代码块不适合作为模板项
                    null
                }
            }
        }.filterNotNull()
        
        return createTemplate(
            name = name,
            description = description,
            items = items,
            category = category
        )
    }
    
    /**
     * 删除模板
     */
    fun deleteTemplate(templateId: String) {
        val template = _templates.value.find { it.id == templateId }
        if (template != null && !template.isBuiltIn) {
            _templates.value = _templates.value.filter { it.id != templateId }
            saveTemplates()
        }
    }
    
    /**
     * 更新模板
     */
    fun updateTemplate(
        templateId: String,
        update: (ContextTemplate) -> ContextTemplate
    ) {
        val templates = _templates.value.toMutableList()
        val index = templates.indexOfFirst { it.id == templateId }
        
        if (index != -1 && !templates[index].isBuiltIn) {
            templates[index] = update(templates[index])
            _templates.value = templates
            saveTemplates()
        }
    }
    
    /**
     * 获取模板分类
     */
    fun getCategories(): List<String> {
        return _templates.value
            .map { it.category }
            .distinct()
            .sorted()
    }
    
    /**
     * 搜索模板
     */
    fun searchTemplates(query: String): List<ContextTemplate> {
        if (query.isBlank()) return _templates.value
        
        val lowerQuery = query.lowercase()
        return _templates.value.filter { template ->
            template.name.lowercase().contains(lowerQuery) ||
            template.description.lowercase().contains(lowerQuery) ||
            template.tags.any { it.lowercase().contains(lowerQuery) } ||
            template.category.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * 获取推荐模板
     */
    fun getRecommendedTemplates(
        projectPath: String,
        limit: Int = 5
    ): List<ContextTemplate> {
        // 基于项目文件推测项目类型
        val projectFiles = File(projectPath).listFiles()?.map { it.name } ?: emptyList()
        
        val scores = _templates.value.map { template ->
            var score = 0
            
            // 检查模板项是否匹配
            template.items.forEach { item ->
                if (item.type == ContextTemplateItem.TemplateItemType.FILE) {
                    if (projectFiles.contains(item.value)) {
                        score += if (item.optional) 1 else 2
                    }
                }
            }
            
            // 基于使用频率
            score += template.usageCount / 10
            
            template to score
        }
        
        return scores
            .sortedByDescending { it.second }
            .take(limit)
            .filter { it.second > 0 }
            .map { it.first }
    }
    
    /**
     * 导入模板
     */
    fun importTemplate(templateJson: String): ContextTemplate? {
        // TODO: 实现 JSON 导入
        return null
    }
    
    /**
     * 导出模板
     */
    fun exportTemplate(templateId: String): String? {
        val template = _templates.value.find { it.id == templateId }
        // TODO: 实现 JSON 导出
        return null
    }
    
    private fun updateTemplateUsage(templateId: String) {
        updateTemplate(templateId) { template ->
            template.copy(
                usageCount = template.usageCount + 1,
                lastUsed = Instant.now()
            )
        }
    }
    
    private fun saveTemplates() {
        // TODO: 保存到持久化存储
    }
    
    private fun loadTemplates() {
        // TODO: 从持久化存储加载
    }
}