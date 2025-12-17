package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.server.mcp.schema.SchemaValidator
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.asakii.server.mcp.schema.ValidationError
import com.asakii.server.mcp.schema.ValidationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class RenameResult(
    val success: Boolean,
    val symbolName: String,
    val newName: String,
    val symbolType: String,
    val filesAffected: Int = 0,
    val usagesRenamed: Int = 0,
    val affectedFiles: List<String> = emptyList(),
    val error: String? = null
)

/**
 * 符号类型枚举（共享给 FindUsages 和 Rename 工具）
 */
@Serializable
enum class SymbolType {
    Auto,
    Class,
    Interface,
    Enum,
    Object,
    Method,
    Function,
    Field,
    Property,
    Variable,
    Parameter,
    File
}

/**
 * Rename 工具
 *
 * 安全地重命名符号（类、方法、字段、变量等）并自动更新所有引用。
 * 类似于 IDEA 的 "Refactor > Rename" (Shift+F6) 功能。
 *
 * 支持：
 * - 类、接口、枚举重命名
 * - 方法、函数重命名
 * - 字段、属性重命名
 * - 变量、参数重命名
 * - 文件重命名
 * - 搜索注释和字符串中的引用
 */
class RenameTool(private val project: Project) {

    fun getInputSchema(): Map<String, Any> = ToolSchemaLoader.getSchema("Rename")

    fun execute(arguments: Map<String, Any>): Any {
        // ===== 使用 SchemaValidator 进行参数校验 =====
        val validationResult = SchemaValidator.validate(
            toolName = "Rename",
            arguments = arguments,
            customValidators = listOf(
                // 自定义校验：文件路径有效性
                { args -> validateFilePath(args) },
                // 自定义校验：新名称不能为空
                { args -> validateNewName(args) }
            )
        )

        if (validationResult is ValidationResult.Invalid) {
            return ToolResult.error(validationResult.formatMessage())
        }

        // ===== 提取参数 =====
        val filePath = arguments["filePath"] as String
        val newName = arguments["newName"] as String
        val line = (arguments["line"] as Number).toInt()
        val column = (arguments["column"] as? Number)?.toInt()
        val symbolTypeStr = arguments["symbolType"] as? String ?: "Auto"
        val searchInComments = arguments["searchInComments"] as? Boolean ?: true
        val searchInStrings = arguments["searchInStrings"] as? Boolean ?: false

        // 解析枚举值
        val symbolType = SymbolType.valueOf(symbolTypeStr)

        // ===== 构建路径 =====
        val absolutePath = if (File(filePath).isAbsolute) {
            filePath
        } else {
            project.basePath?.let { File(it, filePath).absolutePath } ?: filePath
        }

        val resultRef = AtomicReference<Any>()

        try {
            ApplicationManager.getApplication().invokeAndWait {
                val result = performRename(
                    absolutePath = absolutePath,
                    newName = newName,
                    line = line,
                    column = column,
                    symbolType = symbolType,
                    searchInComments = searchInComments,
                    searchInStrings = searchInStrings
                )
                resultRef.set(result)
            }
        } catch (e: Exception) {
            return ToolResult.error("Rename failed: ${e.message}")
        }

        return resultRef.get()
    }

    private fun performRename(
        absolutePath: String,
        newName: String,
        line: Int,
        column: Int?,
        symbolType: SymbolType,
        searchInComments: Boolean,
        searchInStrings: Boolean
    ): Any {
        var foundElement: PsiElement? = null
        var foundElementType = "Unknown"
        var originalName = ""
        var usagesCount = 0
        val affectedFilesSet = mutableSetOf<String>()

        try {
            ApplicationManager.getApplication().runReadAction {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)
                if (virtualFile == null) {
                    throw IllegalArgumentException("""
                        |Cannot access file via IDE: $absolutePath
                        |
                        |The file exists on disk but cannot be accessed by IDE.
                        |Try refreshing the project or reopening the file.
                    """.trimMargin())
                }

                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile == null) {
                    throw IllegalArgumentException("""
                        |Cannot parse file: $absolutePath
                        |
                        |The IDE cannot parse this file. Possible reasons:
                        |  1. The file type is not supported
                        |  2. The file is binary or corrupted
                        |  3. The language plugin is not installed
                    """.trimMargin())
                }

                // 查找目标元素
                foundElement = findTargetElement(psiFile, line, column, symbolType)
                if (foundElement == null) {
                    throw IllegalArgumentException(buildNotFoundMessage(line, column, symbolType))
                }

                foundElementType = getElementTypeDescription(foundElement!!)
                originalName = (foundElement as? PsiNamedElement)?.name ?: "element"

                // 统计引用数量和受影响的文件
                val references = ReferencesSearch.search(foundElement!!, GlobalSearchScope.allScope(project)).findAll()
                usagesCount = references.size + 1 // +1 for the definition itself

                affectedFilesSet.add(psiFile.virtualFile.path)
                references.forEach { ref ->
                    ref.element.containingFile?.virtualFile?.path?.let { affectedFilesSet.add(it) }
                }
            }

            // 执行重命名
            val renameSuccessful = WriteCommandAction.runWriteCommandAction<Boolean>(project) {
                try {
                    val processor = RenameProcessor(project, foundElement!!, newName, searchInComments, searchInStrings)
                    processor.run()
                    true
                } catch (e: Exception) {
                    throw IllegalStateException("Rename operation failed: ${e.message}", e)
                }
            }

            if (!renameSuccessful) {
                return ToolResult.error("Rename operation was not successful")
            }

            // 构建结果
            val affectedFilesList = affectedFilesSet.mapNotNull { path ->
                project.basePath?.let { basePath ->
                    path.removePrefix(basePath).removePrefix("/")
                } ?: path
            }.sorted()

            val sb = StringBuilder()
            sb.appendLine("## Rename Successful")
            sb.appendLine()
            sb.appendLine("**Symbol:** `$originalName` → `$newName` ($foundElementType)")
            sb.appendLine("**Files affected:** ${affectedFilesSet.size}")
            sb.appendLine("**Usages renamed:** $usagesCount")
            sb.appendLine()

            if (affectedFilesList.isNotEmpty()) {
                sb.appendLine("### Affected Files")
                sb.appendLine()
                affectedFilesList.forEach { file ->
                    sb.appendLine("- `$file`")
                }
            }

            return sb.toString()

        } catch (e: IllegalArgumentException) {
            return ToolResult.error(e.message ?: "Unknown error")
        } catch (e: Exception) {
            return ToolResult.error("Rename error: ${e.message}")
        }
    }

    private fun findTargetElement(
        psiFile: PsiFile,
        line: Int,
        column: Int?,
        symbolType: SymbolType
    ): PsiElement? {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        if (document == null || line <= 0 || line > document.lineCount) {
            return null
        }

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

        return namedElement
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

    private fun buildNotFoundMessage(line: Int, column: Int?, symbolType: SymbolType): String {
        return buildString {
            append("Cannot find symbol at line $line")
            column?.let { append(", column $it") }
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
     * 校验新名称有效性
     */
    private fun validateNewName(arguments: Map<String, Any>): ValidationError? {
        val newName = arguments["newName"] as? String

        if (newName.isNullOrBlank()) {
            return ValidationError(
                parameter = "newName",
                message = "New name cannot be empty",
                hint = "Please provide a valid new name for the symbol"
            )
        }

        // 检查是否包含非法字符
        if (!newName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
            return ValidationError(
                parameter = "newName",
                message = "Invalid identifier: $newName",
                hint = "Name must start with letter or underscore, followed by letters, digits, or underscores"
            )
        }

        return null
    }
}
