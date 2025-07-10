package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/**
 * 提示词模板管理器
 */
class PromptTemplateManager {
    private val _templates = MutableStateFlow<List<PromptTemplate>>(emptyList())
    val templates: StateFlow<List<PromptTemplate>> = _templates.asStateFlow()
    
    private val _categories = MutableStateFlow<List<String>>(
        listOf("开发", "文档", "分析", "测试", "重构", "设计", "其他")
    )
    val categories: StateFlow<List<String>> = _categories.asStateFlow()
    
    init {
        loadBuiltInTemplates()
    }
    
    /**
     * 加载内置模板
     */
    private fun loadBuiltInTemplates() {
        val builtInTemplates = listOf(
            // 开发类模板
            PromptTemplate(
                id = "code-review",
                name = "代码审查",
                description = "对代码进行全面的质量审查",
                template = """请对以下代码进行详细审查：

{{code}}

请从以下几个方面进行分析：
1. **代码质量**：是否符合编码规范和最佳实践
2. **潜在问题**：是否存在bug、安全隐患或性能问题
3. **可维护性**：代码是否易于理解和维护
4. **优化建议**：如何改进代码结构和性能

审查重点：{{focus}}

请提供具体的改进建议和示例代码。""",
                variables = listOf(
                    TemplateVariable(
                        name = "code",
                        description = "要审查的代码",
                        type = TemplateVariable.VariableType.MULTILINE_TEXT,
                        required = true
                    ),
                    TemplateVariable(
                        name = "focus",
                        description = "审查重点（可选）",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "通用审查",
                        required = false
                    )
                ),
                category = "开发",
                icon = "🔍",
                isBuiltIn = true
            ),
            
            PromptTemplate(
                id = "refactor-code",
                name = "代码重构",
                description = "重构代码以提高质量和可维护性",
                template = """请帮我重构以下代码：

{{code}}

重构目标：
- {{goal}}

请遵循以下原则：
1. 保持功能不变
2. 提高代码可读性
3. 减少重复代码
4. 优化性能（如果可能）
5. 添加适当的注释

编程语言：{{language}}
设计模式偏好：{{patterns}}""",
                variables = listOf(
                    TemplateVariable(
                        name = "code",
                        description = "要重构的代码",
                        type = TemplateVariable.VariableType.MULTILINE_TEXT,
                        required = true
                    ),
                    TemplateVariable(
                        name = "goal",
                        description = "重构目标",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "提高代码质量",
                        required = true
                    ),
                    TemplateVariable(
                        name = "language",
                        description = "编程语言",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("Java", "Kotlin", "Python", "JavaScript", "TypeScript", "Go", "Rust"),
                        required = true
                    ),
                    TemplateVariable(
                        name = "patterns",
                        description = "设计模式",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "SOLID原则",
                        required = false
                    )
                ),
                category = "重构",
                icon = "🔧",
                isBuiltIn = true
            ),
            
            // 文档类模板
            PromptTemplate(
                id = "generate-docs",
                name = "生成文档",
                description = "为代码生成详细的文档",
                template = """请为以下代码生成{{docType}}文档：

{{code}}

文档要求：
- 语言：{{language}}
- 详细程度：{{detail}}
- 包含示例：{{includeExamples}}

请确保文档：
1. 描述清晰准确
2. 包含所有公共API
3. 说明参数和返回值
4. 包含使用示例（如果需要）
5. 遵循{{docType}}的标准格式""",
                variables = listOf(
                    TemplateVariable(
                        name = "code",
                        description = "需要生成文档的代码",
                        type = TemplateVariable.VariableType.MULTILINE_TEXT,
                        required = true
                    ),
                    TemplateVariable(
                        name = "docType",
                        description = "文档类型",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("JavaDoc", "KDoc", "JSDoc", "Python Docstring", "Markdown", "API文档"),
                        defaultValue = "Markdown",
                        required = true
                    ),
                    TemplateVariable(
                        name = "language",
                        description = "文档语言",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("中文", "English"),
                        defaultValue = "中文",
                        required = true
                    ),
                    TemplateVariable(
                        name = "detail",
                        description = "详细程度",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("简洁", "标准", "详细"),
                        defaultValue = "标准",
                        required = true
                    ),
                    TemplateVariable(
                        name = "includeExamples",
                        description = "包含示例",
                        type = TemplateVariable.VariableType.BOOLEAN,
                        defaultValue = "true",
                        required = false
                    )
                ),
                category = "文档",
                icon = "📝",
                isBuiltIn = true
            ),
            
            // 测试类模板
            PromptTemplate(
                id = "generate-tests",
                name = "生成单元测试",
                description = "为代码生成单元测试",
                template = """请为以下代码生成单元测试：

{{code}}

测试框架：{{framework}}
测试风格：{{style}}
覆盖率目标：{{coverage}}%

请生成：
1. 正常情况的测试用例
2. 边界条件测试
3. 异常情况测试
4. 性能测试（如果适用）

确保测试：
- 独立且可重复
- 有明确的断言
- 测试名称描述清晰
- 覆盖所有公共方法""",
                variables = listOf(
                    TemplateVariable(
                        name = "code",
                        description = "需要测试的代码",
                        type = TemplateVariable.VariableType.MULTILINE_TEXT,
                        required = true
                    ),
                    TemplateVariable(
                        name = "framework",
                        description = "测试框架",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("JUnit", "Mockito", "pytest", "Jest", "Mocha", "Go test"),
                        required = true
                    ),
                    TemplateVariable(
                        name = "style",
                        description = "测试风格",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("AAA (Arrange-Act-Assert)", "BDD (Given-When-Then)", "TDD"),
                        defaultValue = "AAA (Arrange-Act-Assert)",
                        required = false
                    ),
                    TemplateVariable(
                        name = "coverage",
                        description = "目标覆盖率",
                        type = TemplateVariable.VariableType.NUMBER,
                        defaultValue = "80",
                        validation = "^(100|[1-9]?[0-9])$",
                        required = false
                    )
                ),
                category = "测试",
                icon = "🧪",
                isBuiltIn = true
            ),
            
            // 分析类模板
            PromptTemplate(
                id = "analyze-performance",
                name = "性能分析",
                description = "分析代码性能并提供优化建议",
                template = """请对以下代码进行性能分析：

{{code}}

运行环境：{{environment}}
性能目标：{{goal}}

请分析：
1. **时间复杂度**：算法的时间复杂度分析
2. **空间复杂度**：内存使用情况
3. **瓶颈识别**：找出性能瓶颈
4. **优化建议**：具体的优化方案

请提供：
- 详细的复杂度分析
- 性能测试建议
- 优化后的代码示例
- 预期的性能提升""",
                variables = listOf(
                    TemplateVariable(
                        name = "code",
                        description = "要分析的代码",
                        type = TemplateVariable.VariableType.MULTILINE_TEXT,
                        required = true
                    ),
                    TemplateVariable(
                        name = "environment",
                        description = "运行环境",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "生产环境",
                        required = false
                    ),
                    TemplateVariable(
                        name = "goal",
                        description = "性能目标",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "提高响应速度",
                        required = false
                    )
                ),
                category = "分析",
                icon = "📊",
                isBuiltIn = true
            ),
            
            // 设计类模板
            PromptTemplate(
                id = "design-api",
                name = "API 设计",
                description = "设计 RESTful API",
                template = """请帮我设计一个{{apiType}} API：

功能需求：
{{requirements}}

技术栈：{{techStack}}
认证方式：{{auth}}

请提供：
1. **API 端点设计**
   - URL 结构
   - HTTP 方法
   - 请求/响应格式

2. **数据模型**
   - 实体定义
   - 关系设计

3. **错误处理**
   - 错误码定义
   - 错误响应格式

4. **API 文档**
   - OpenAPI/Swagger 规范
   - 使用示例

遵循 RESTful 最佳实践和{{standard}}标准。""",
                variables = listOf(
                    TemplateVariable(
                        name = "apiType",
                        description = "API 类型",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("RESTful", "GraphQL", "gRPC", "WebSocket"),
                        defaultValue = "RESTful",
                        required = true
                    ),
                    TemplateVariable(
                        name = "requirements",
                        description = "功能需求",
                        type = TemplateVariable.VariableType.MULTILINE_TEXT,
                        required = true
                    ),
                    TemplateVariable(
                        name = "techStack",
                        description = "技术栈",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "Spring Boot + MySQL",
                        required = false
                    ),
                    TemplateVariable(
                        name = "auth",
                        description = "认证方式",
                        type = TemplateVariable.VariableType.SELECT,
                        options = listOf("JWT", "OAuth2", "API Key", "Basic Auth", "无"),
                        defaultValue = "JWT",
                        required = false
                    ),
                    TemplateVariable(
                        name = "standard",
                        description = "遵循标准",
                        type = TemplateVariable.VariableType.TEXT,
                        defaultValue = "OpenAPI 3.0",
                        required = false
                    )
                ),
                category = "设计",
                icon = "🏗️",
                isBuiltIn = true
            )
        )
        
        _templates.value = builtInTemplates
    }
    
    /**
     * 创建自定义模板
     */
    fun createTemplate(
        name: String,
        description: String,
        template: String,
        variables: List<TemplateVariable>,
        category: String,
        icon: String? = null
    ): PromptTemplate {
        val newTemplate = PromptTemplate(
            name = name,
            description = description,
            template = template,
            variables = variables,
            category = category,
            icon = icon,
            isBuiltIn = false
        )
        
        _templates.value = _templates.value + newTemplate
        saveTemplates()
        
        return newTemplate
    }
    
    /**
     * 更新模板
     */
    fun updateTemplate(templateId: String, update: (PromptTemplate) -> PromptTemplate) {
        _templates.value = _templates.value.map { template ->
            if (template.id == templateId && !template.isBuiltIn) {
                update(template)
            } else {
                template
            }
        }
        saveTemplates()
    }
    
    /**
     * 删除模板
     */
    fun deleteTemplate(templateId: String) {
        _templates.value = _templates.value.filter { 
            it.id != templateId || it.isBuiltIn 
        }
        saveTemplates()
    }
    
    /**
     * 应用模板
     */
    fun applyTemplate(template: PromptTemplate, values: Map<String, String>): String {
        var result = template.template
        
        // 验证必需的变量
        template.variables.filter { it.required }.forEach { variable ->
            if (!values.containsKey(variable.name) || values[variable.name].isNullOrBlank()) {
                throw IllegalArgumentException("缺少必需的变量: ${variable.name}")
            }
        }
        
        // 替换变量
        template.variables.forEach { variable ->
            val value = values[variable.name] ?: variable.defaultValue ?: ""
            
            // 验证变量值
            variable.validation?.let { pattern ->
                if (!Regex(pattern).matches(value)) {
                    throw IllegalArgumentException("变量 ${variable.name} 的值不符合格式要求")
                }
            }
            
            result = result.replace("{{${variable.name}}}", value)
        }
        
        // 更新使用统计
        updateTemplateUsage(template.id)
        
        return result
    }
    
    /**
     * 搜索模板
     */
    fun searchTemplates(query: String): List<PromptTemplate> {
        if (query.isBlank()) return _templates.value
        
        val lowerQuery = query.lowercase()
        return _templates.value.filter { template ->
            template.name.lowercase().contains(lowerQuery) ||
            template.description.lowercase().contains(lowerQuery) ||
            template.category.lowercase().contains(lowerQuery) ||
            template.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * 获取分类
     */
    fun getCategories(): List<String> {
        return _categories.value
    }
    
    /**
     * 添加分类
     */
    fun addCategory(category: String) {
        if (category !in _categories.value) {
            _categories.value = _categories.value + category
        }
    }
    
    /**
     * 获取收藏的模板
     */
    fun getFavoriteTemplates(): List<PromptTemplate> {
        return _templates.value.filter { it.isFavorite }
    }
    
    /**
     * 切换收藏状态
     */
    fun toggleFavorite(templateId: String) {
        updateTemplate(templateId) { template ->
            template.copy(isFavorite = !template.isFavorite)
        }
    }
    
    /**
     * 获取最近使用的模板
     */
    fun getRecentTemplates(limit: Int = 5): List<PromptTemplate> {
        return _templates.value
            .filter { it.lastUsed != null }
            .sortedByDescending { it.lastUsed }
            .take(limit)
    }
    
    /**
     * 获取流行的模板
     */
    fun getPopularTemplates(limit: Int = 5): List<PromptTemplate> {
        return _templates.value
            .sortedByDescending { it.usageCount }
            .take(limit)
    }
    
    /**
     * 导出模板
     */
    fun exportTemplate(templateId: String): String {
        val template = _templates.value.find { it.id == templateId }
            ?: throw IllegalArgumentException("模板不存在")
        
        // TODO: 实现 JSON 序列化
        return ""
    }
    
    /**
     * 导入模板
     */
    fun importTemplate(json: String): PromptTemplate {
        // TODO: 实现 JSON 反序列化
        throw NotImplementedError()
    }
    
    /**
     * 验证模板
     */
    fun validateTemplate(template: String): List<String> {
        val errors = mutableListOf<String>()
        
        // 查找所有变量
        val variablePattern = Regex("\\{\\{(\\w+)\\}\\}")
        val foundVariables = variablePattern.findAll(template)
            .map { it.groupValues[1] }
            .toSet()
        
        // 检查是否有未闭合的变量
        if (template.count { it == '{' } != template.count { it == '}' }) {
            errors.add("模板中存在未闭合的变量标记")
        }
        
        return errors
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