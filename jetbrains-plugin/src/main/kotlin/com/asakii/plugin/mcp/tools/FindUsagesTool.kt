package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.server.mcp.schema.SchemaValidator
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.asakii.server.mcp.schema.ValidationError
import com.asakii.server.mcp.schema.ValidationResult
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import java.io.File

/**
 * 使用类型 - 针对不同符号类型的细粒度过滤
 */
@Serializable
enum class UsageType {
    // 通用
    All,            // 所有使用

    // 类相关
    Inheritance,    // 继承/实现 (extends/implements)
    Instantiation,  // 实例化 (new)
    TypeReference,  // 类型引用 (变量声明、泛型参数、类型转换等)
    Import,         // 导入语句

    // 方法相关
    Override,       // 覆盖 (子类重写)
    Call,           // 调用
    MethodReference,// 方法引用 (::method)

    // 字段相关
    Read,           // 读取
    Write           // 写入
}

@Serializable
data class UsageLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val context: String,       // 引用所在代码行
    val usageType: String      // 使用类型
)

@Serializable
data class FindUsagesResult(
    val symbolName: String,
    val symbolType: String,
    val definitionFile: String?,
    val definitionLine: Int?,
    val usages: List<UsageLocation>,
    val totalFound: Int,
    val hasMore: Boolean,
    val offset: Int,
    val limit: Int
)

/**
 * Find Usages 工具
 *
 * 查找符号（类、方法、字段、变量等）在项目中的所有引用位置。
 * 类似于 IDEA 的 "Find Usages" (Alt+F7) 功能。
 *
 * 支持细粒度的使用类型过滤：
 * - 类: 继承、实例化、类型引用、导入
 * - 方法: 覆盖、调用、方法引用
 * - 字段/变量: 读取、写入
 */
class FindUsagesTool(private val project: Project) {

    fun getInputSchema(): Map<String, Any> = ToolSchemaLoader.getSchema("FindUsages")

    fun execute(arguments: Map<String, Any>): Any {
        // ===== 使用 SchemaValidator 进行参数校验 =====
        val validationResult = SchemaValidator.validate(
            toolName = "FindUsages",
            arguments = arguments,
            customValidators = listOf(
                // 自定义校验：symbolName 或 line 至少提供一个
                SchemaValidator.requireAtLeastOne(
                    "symbolName", "line",
                    message = "Must provide either 'symbolName' or 'line' to locate the symbol"
                ),
                // 自定义校验：Module/Directory 需要 scopeArg
                SchemaValidator.requireIfPresent(
                    trigger = "searchScope",
                    triggerValues = listOf("Module", "Directory"),
                    required = "scopeArg"
                ),
                // 自定义校验：文件路径有效性
                { args -> validateFilePath(args) }
            )
        )

        if (validationResult is ValidationResult.Invalid) {
            return ToolResult.error(validationResult.formatMessage())
        }

        // ===== 提取参数 =====
        val filePath = arguments["filePath"] as String
        val symbolName = arguments["symbolName"] as? String
        val line = (arguments["line"] as? Number)?.toInt()
        val column = (arguments["column"] as? Number)?.toInt()
        val symbolTypeStr = arguments["symbolType"] as? String ?: "Auto"
        val searchScopeStr = arguments["searchScope"] as? String ?: "Project"
        val scopeArg = arguments["scopeArg"] as? String
        val maxResults = ((arguments["maxResults"] as? Number)?.toInt() ?: 20).coerceAtLeast(1)
        val offset = ((arguments["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

        // 解析枚举值 (已通过 Schema 校验，直接转换)
        val symbolType = SymbolType.valueOf(symbolTypeStr)
        val usageTypes = parseUsageTypes(arguments["usageTypes"])

        // ===== 构建路径 =====
        val absolutePath = if (File(filePath).isAbsolute) {
            filePath
        } else {
            project.basePath?.let { File(it, filePath).absolutePath } ?: filePath
        }

        val usages = mutableListOf<UsageLocation>()
        var foundElement: PsiElement? = null
        var foundElementType = "Unknown"
        var definitionFile: String? = null
        var definitionLine: Int? = null
        var totalFound = 0

        try {
            ReadAction.run<Exception> {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)
                if (virtualFile == null) {
                    throw IllegalArgumentException("""
                        |Cannot access file via IDE: $filePath
                        |
                        |The file exists on disk but cannot be accessed by IDE.
                        |This may happen if the file is outside the project or not indexed yet.
                        |Try refreshing the project or reopening the file.
                    """.trimMargin())
                }

                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile == null) {
                    throw IllegalArgumentException("""
                        |Cannot parse file: $filePath
                        |
                        |The IDE cannot parse this file. Possible reasons:
                        |  1. The file type is not supported
                        |  2. The file is binary or corrupted
                        |  3. The language plugin is not installed
                    """.trimMargin())
                }

                // 查找目标元素
                foundElement = findTargetElement(psiFile, symbolName, line, column, symbolType)
                if (foundElement == null) {
                    throw IllegalArgumentException(buildNotFoundMessage(symbolName, line, column, symbolType))
                }

                foundElementType = getElementTypeDescription(foundElement!!)

                // 获取定义位置
                foundElement?.containingFile?.virtualFile?.let { file ->
                    definitionFile = project.basePath?.let {
                        file.path.removePrefix(it).removePrefix("/")
                    } ?: file.path
                }

                foundElement?.let { elem ->
                    val document = PsiDocumentManager.getInstance(project).getDocument(elem.containingFile)
                    document?.let { doc ->
                        definitionLine = doc.getLineNumber(elem.textOffset) + 1
                    }
                }

                // 确定搜索范围
                val scope = when (searchScopeStr) {
                    "Module" -> {
                        val moduleName = scopeArg!!
                        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)
                        val module = moduleManager.findModuleByName(moduleName)
                        if (module == null) {
                            val availableModules = moduleManager.modules.map { it.name }
                            throw IllegalArgumentException("""
                                |Module not found: '$moduleName'
                                |
                                |Available modules in this project:
                                |  ${availableModules.joinToString("\n  ")}
                            """.trimMargin())
                        }
                        GlobalSearchScope.moduleScope(module)
                    }
                    "Directory" -> {
                        val dirPath = scopeArg!!
                        val dirAbsPath = if (File(dirPath).isAbsolute) dirPath
                                         else project.basePath?.let { File(it, dirPath).absolutePath } ?: dirPath
                        val dirFile = LocalFileSystem.getInstance().findFileByPath(dirAbsPath)
                        if (dirFile == null || !dirFile.isDirectory) {
                            throw IllegalArgumentException("""
                                |Directory not found: '$dirPath'
                                |
                                |Resolved path: $dirAbsPath
                                |
                                |Please provide a valid directory path relative to project root.
                            """.trimMargin())
                        }
                        GlobalSearchScopes.directoryScope(project, dirFile, true)
                    }
                    else -> GlobalSearchScope.projectScope(project)
                }

                // 根据符号类型和使用类型过滤进行搜索
                val allUsages = mutableListOf<Pair<PsiElement, String>>() // element to usage type

                val elem = foundElement!!

                // 类相关的特殊搜索（使用反射，支持 Java 插件可选）
                if (JavaPluginHelper.isPsiClass(elem)) {
                    // 查找继承者
                    if (usageTypes.contains(UsageType.All) || usageTypes.contains(UsageType.Inheritance)) {
                        JavaPluginHelper.searchClassInheritors(elem, scope, true).forEach { inheritor ->
                            allUsages.add(inheritor to "Inheritance")
                        }
                    }
                }

                // 方法相关的特殊搜索（使用反射，支持 Java 插件可选）
                if (JavaPluginHelper.isPsiMethod(elem)) {
                    // 查找覆盖方法
                    if (usageTypes.contains(UsageType.All) || usageTypes.contains(UsageType.Override)) {
                        JavaPluginHelper.searchOverridingMethods(elem, scope, true).forEach { overrider ->
                            allUsages.add(overrider to "Override")
                        }
                    }
                }

                // 通用引用搜索
                val references = ReferencesSearch.search(elem, scope, false).findAll()

                references.forEach { reference ->
                    val refElement = reference.element
                    val usageType = classifyUsage(refElement, elem)

                    // 过滤使用类型
                    if (usageTypes.contains(UsageType.All) || usageTypes.contains(usageType)) {
                        allUsages.add(refElement to usageType.name)
                    }
                }

                totalFound = allUsages.size

                allUsages
                    .drop(offset)
                    .take(maxResults)
                    .forEach { (refElement, usageTypeStr) ->
                        val refFile = refElement.containingFile?.virtualFile ?: return@forEach
                        val document = PsiDocumentManager.getInstance(project)
                            .getDocument(refElement.containingFile) ?: return@forEach

                        val lineNumber = document.getLineNumber(refElement.textOffset) + 1
                        val columnNumber = refElement.textOffset - document.getLineStartOffset(lineNumber - 1) + 1

                        // 获取引用所在行的代码作为上下文
                        val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
                        val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
                        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
                            .trim()
                            .take(200) // 限制长度

                        val relativePath = project.basePath?.let {
                            refFile.path.removePrefix(it).removePrefix("/")
                        } ?: refFile.path

                        usages.add(UsageLocation(
                            filePath = relativePath,
                            line = lineNumber,
                            column = columnNumber,
                            context = lineText,
                            usageType = usageTypeStr
                        ))
                    }
            }
        } catch (e: IllegalArgumentException) {
            return ToolResult.error(e.message ?: "Unknown error")
        } catch (e: Exception) {
            return ToolResult.error("Search error: ${e.message}")
        }

        // 构建输出
        val sb = StringBuilder()
        val actualName = (foundElement as? PsiNamedElement)?.name ?: symbolName ?: "element"
        sb.appendLine("## Find Usages: `$actualName` ($foundElementType)")
        sb.appendLine()

        if (definitionFile != null) {
            sb.appendLine("**Definition:** `$definitionFile:$definitionLine`")
        }

        if (usageTypes.isNotEmpty() && !usageTypes.contains(UsageType.All)) {
            sb.appendLine("**Filter:** ${usageTypes.joinToString(", ")}")
        }
        sb.appendLine()

        if (usages.isEmpty()) {
            sb.appendLine("*No usages found in project scope*")
        } else {
            // 按使用类型分组显示
            val groupedUsages = usages.groupBy { it.usageType }
            groupedUsages.forEach { (type, typeUsages) ->
                sb.appendLine("### $type (${typeUsages.size})")
                sb.appendLine()
                sb.appendLine("| # | Location | Context |")
                sb.appendLine("|---|----------|---------|")
                typeUsages.forEachIndexed { index, usage ->
                    val escapedContext = usage.context.replace("|", "\\|").replace("`", "\\`")
                    sb.appendLine("| ${index + 1} | `${usage.filePath}:${usage.line}:${usage.column}` | `$escapedContext` |")
                }
                sb.appendLine()
            }
        }

        sb.appendLine("---")
        sb.append("**Summary:** $totalFound usages")
        if (offset + usages.size < totalFound) {
            sb.append(" *(showing ${offset + 1}-${offset + usages.size}, more available)*")
        }

        return sb.toString()
    }

    /**
     * 分类使用类型
     */
    private fun classifyUsage(refElement: PsiElement, targetElement: PsiElement): UsageType {
        val parent = refElement.parent
        val grandParent = parent?.parent
        val parentClass = parent?.javaClass?.simpleName ?: ""
        val grandParentClass = grandParent?.javaClass?.simpleName ?: ""

        // 导入语句
        if (parentClass.contains("Import") || grandParentClass.contains("Import")) {
            return UsageType.Import
        }

        // 类相关（使用反射检查，支持 Java 插件可选）
        if (JavaPluginHelper.isPsiClass(targetElement)) {
            // 继承/实现
            if (parentClass.contains("Extends") || parentClass.contains("Implements") ||
                parentClass.contains("SuperType") || parentClass.contains("ReferenceList")) {
                return UsageType.Inheritance
            }

            // 实例化 (new)
            if (parentClass.contains("NewExpression") || parentClass.contains("CallExpression") ||
                grandParentClass.contains("NewExpression")) {
                return UsageType.Instantiation
            }

            // 默认为类型引用
            return UsageType.TypeReference
        }

        // 方法相关（使用反射检查，支持 Java 插件可选）
        if (JavaPluginHelper.isPsiMethod(targetElement)) {
            // 方法引用 (::method)
            if (parentClass.contains("MethodReference") || parentClass.contains("CallableReference")) {
                return UsageType.MethodReference
            }

            // 默认为调用
            return UsageType.Call
        }

        // 字段/变量相关
        if (isWriteAccess(refElement)) {
            return UsageType.Write
        }

        return UsageType.Read
    }

    private fun getUsageTypeIcon(usageType: String): String {
        return when (usageType) {
            "Inheritance" -> "🔗"
            "Instantiation" -> "🆕"
            "TypeReference" -> "📝"
            "Import" -> "📥"
            "Override" -> "🔄"
            "Call" -> "📞"
            "MethodReference" -> "🔗"
            "Read" -> "📖"
            "Write" -> "✏️"
            else -> "•"
        }
    }

    private fun findTargetElement(
        psiFile: PsiFile,
        symbolName: String?,
        line: Int?,
        column: Int?,
        symbolType: SymbolType
    ): PsiElement? {
        // 优先通过行列号定位
        if (line != null) {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            if (document != null && line > 0 && line <= document.lineCount) {
                val lineStartOffset = document.getLineStartOffset(line - 1)
                val targetOffset = if (column != null && column > 0) {
                    lineStartOffset + column - 1
                } else {
                    lineStartOffset
                }

                val elementAtOffset = psiFile.findElementAt(targetOffset.coerceIn(0, psiFile.textLength - 1))
                val namedElement = PsiTreeUtil.getParentOfType(
                    elementAtOffset,
                    PsiNamedElement::class.java,
                    false
                )

                if (namedElement != null) {
                    // 如果指定了符号名，验证是否匹配
                    if (symbolName != null && (namedElement as? PsiNamedElement)?.name != symbolName) {
                        // 继续向上查找匹配的元素
                        var parentElem = namedElement.parent
                        while (parentElem != null) {
                            if (parentElem is PsiNamedElement && parentElem.name == symbolName) {
                                return resolveToDeclaration(parentElem)
                            }
                            parentElem = parentElem.parent
                        }
                    }
                    return resolveToDeclaration(namedElement)
                }
            }
        }

        // 通过符号名查找
        if (symbolName != null) {
            return findByName(psiFile, symbolName, symbolType)
        }

        return null
    }

    private fun findByName(psiFile: PsiFile, name: String, symbolType: SymbolType): PsiElement? {
        var foundElement: PsiElement? = null

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (foundElement != null) return

                if (element is PsiNamedElement && element.name == name) {
                    val matches = when (symbolType) {
                        SymbolType.Auto -> true
                        SymbolType.Class, SymbolType.Interface, SymbolType.Enum, SymbolType.Object -> isClassLike(element)
                        SymbolType.Method, SymbolType.Function -> isMethodLike(element)
                        SymbolType.Field, SymbolType.Property -> isFieldLike(element)
                        SymbolType.Variable -> isVariableLike(element)
                        SymbolType.Parameter -> isParameter(element)
                        SymbolType.File -> false // 文件引用不在这里处理
                    }

                    if (matches) {
                        foundElement = resolveToDeclaration(element)
                        return
                    }
                }
                super.visitElement(element)
            }
        })

        return foundElement
    }

    private fun resolveToDeclaration(element: PsiElement): PsiElement {
        // 如果是引用，解析到声明
        if (element is PsiReference) {
            return element.resolve() ?: element
        }

        // 对于 Kotlin/Java 的标识符，获取其父元素（声明）
        val parent = element.parent
        if (parent is PsiNamedElement) {
            return parent
        }

        return element
    }

    private fun isClassLike(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName
        return className.contains("Class") ||
               className.contains("Interface") ||
               className.contains("Enum") ||
               className.contains("Object") // Kotlin object
    }

    private fun isMethodLike(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName
        return className.contains("Method") ||
               className.contains("Function") ||
               className.contains("Constructor")
    }

    private fun isFieldLike(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName
        return className.contains("Field") ||
               className.contains("Property")
    }

    private fun isVariableLike(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName
        return className.contains("Variable") ||
               className.contains("LocalVariable")
    }

    private fun isParameter(element: PsiElement): Boolean {
        val className = element.javaClass.simpleName
        return className.contains("Parameter")
    }

    private fun isWriteAccess(element: PsiElement): Boolean {
        // 检查父元素是否是赋值表达式的左侧
        val parent = element.parent
        val parentClass = parent?.javaClass?.simpleName ?: ""

        return parentClass.contains("Assignment") ||
               (parentClass.contains("BinaryExpression") && element == parent?.firstChild) ||
               parentClass.contains("PostfixExpression") ||
               parentClass.contains("PrefixExpression") ||
               parentClass.contains("++") ||
               parentClass.contains("--")
    }

    private fun getElementTypeDescription(element: PsiElement): String {
        val className = element.javaClass.simpleName
        return when {
            className.contains("Class") -> "Class"
            className.contains("Interface") -> "Interface"
            className.contains("Enum") -> "Enum"
            className.contains("Object") -> "Object"
            className.contains("Method") -> "Method"
            className.contains("Function") -> "Function"
            className.contains("Constructor") -> "Constructor"
            className.contains("Field") -> "Field"
            className.contains("Property") -> "Property"
            className.contains("Variable") -> "Variable"
            className.contains("Parameter") -> "Parameter"
            else -> className.replace("Kt", "").replace("Psi", "").replace("Impl", "")
        }
    }

    private fun buildNotFoundMessage(symbolName: String?, line: Int?, column: Int?, symbolType: SymbolType): String {
        return buildString {
            append("Cannot find symbol")
            symbolName?.let { append(" '$it'") }
            if (line != null) {
                append(" at line $line")
                column?.let { append(", column $it") }
            }
            if (symbolType != SymbolType.Auto) {
                append(" (type: $symbolType)")
            }
            append(". Make sure the file is indexed and the position is correct.")
        }
    }

    /**
     * 校验文件路径有效性
     */
    private fun validateFilePath(arguments: Map<String, Any>): ValidationError? {
        val filePath = arguments["filePath"] as? String ?: return null

        val absolutePath = if (File(filePath).isAbsolute) {
            filePath
        } else {
            project.basePath?.let { File(it, filePath).absolutePath } ?: filePath
        }

        val file = File(absolutePath)

        if (!file.exists()) {
            return ValidationError(
                parameter = "filePath",
                message = "File not found: $filePath",
                hint = """Resolved path: $absolutePath
Please check:
  1. The file path is correct
  2. The file exists in the project
  3. Use relative path from project root or absolute path"""
            )
        }

        if (!file.isFile) {
            return ValidationError(
                parameter = "filePath",
                message = "Path is not a file: $filePath",
                hint = "Please provide a file path, not a directory"
            )
        }

        return null
    }

    /**
     * 解析 usageTypes 参数
     */
    private fun parseUsageTypes(raw: Any?): Set<UsageType> {
        return when (raw) {
            is List<*> -> raw.mapNotNull { item ->
                try { UsageType.valueOf(item.toString()) } catch (_: Exception) { null }
            }.toSet().ifEmpty { setOf(UsageType.All) }
            is String -> try {
                setOf(UsageType.valueOf(raw))
            } catch (_: Exception) {
                setOf(UsageType.All)
            }
            else -> setOf(UsageType.All)
        }
    }
}
