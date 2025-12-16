package com.asakii.rpc.api

import com.asakii.claude.agent.sdk.types.AgentDefinition
import kotlinx.serialization.Serializable

/**
 * 统一的 IDE 操作工具接口
 *
 * 这个接口抽象了所有 IDE 相关的操作，使得：
 * - ai-agent-server 模块提供默认实现（IdeToolsDefault）
 * - jetbrains-plugin 模块提供 IDEA 实现（IdeToolsImpl）
 * - HTTP API 服务器统一使用这个接口
 */
interface IdeTools {
    /**
     * 打开文件并跳转到指定位置
     * @param path 文件路径
     * @param line 行号（从1开始，0表示不跳转）
     * @param column 列号（从1开始，0表示不跳转）
     * @return Result<Unit> 成功或失败
     */
    fun openFile(path: String, line: Int = 0, column: Int = 0): Result<Unit>

    /**
     * 显示文件差异对比
     * @param request 差异请求参数
     * @return Result<Unit> 成功或失败
     */
    fun showDiff(request: DiffRequest): Result<Unit>

    /**
     * 搜索文件
     * @param query 搜索查询
     * @param maxResults 最大结果数
     * @return Result<List<FileInfo>> 文件信息列表
     */
    fun searchFiles(query: String, maxResults: Int = 50): Result<List<FileInfo>>

    /**
     * 获取文件内容
     * @param path 文件路径
     * @param lineStart 起始行号（可选，从1开始）
     * @param lineEnd 结束行号（可选，从1开始）
     * @return Result<String> 文件内容
     */
    fun getFileContent(path: String, lineStart: Int? = null, lineEnd: Int? = null): Result<String>

    /**
     * 获取最近打开的文件
     * @param maxResults 最大结果数
     * @return Result<List<FileInfo>> 文件信息列表
     */
    fun getRecentFiles(maxResults: Int = 10): Result<List<FileInfo>>

    /**
     * 获取 IDE 主题信息
     * @return IdeTheme 主题信息
     */
    fun getTheme(): IdeTheme

    /**
     * 获取项目路径
     * @return String 项目根目录路径
     */
    fun getProjectPath(): String

    /**
     * 获取当前语言环境
     * @return String 语言代码（如 "en-US", "zh-CN"）
     */
    fun getLocale(): String

    /**
     * 设置语言环境
     * @param locale 语言代码
     * @return Result<Unit> 成功或失败
     */
    fun setLocale(locale: String): Result<Unit>

    /**
     * 获取子代理定义
     *
     * 从资源文件加载自定义子代理（如 JetBrains 专用的代码探索代理）
     * @return 代理名称到定义的映射，如果没有自定义代理则返回空 Map
     */
    fun getAgentDefinitions(): Map<String, AgentDefinition> = emptyMap()

    /**
     * 检测 Node.js 安装信息
     * @return NodeDetectionResult 检测结果（包含路径、版本等）
     */
    fun detectNode(): NodeDetectionResult

    /**
     * 获取当前活动编辑器中的文件信息
     * @return ActiveFileInfo? 活动文件信息，如果没有活动文件则返回 null
     */
    fun getActiveEditorFile(): ActiveFileInfo? = null
}

/**
 * 差异请求参数
 */
data class DiffRequest(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val title: String? = null,
    val rebuildFromFile: Boolean = false,
    val edits: List<EditOperation>? = null
)

/**
 * 编辑操作
 */
data class EditOperation(
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
)

/**
 * 文件信息
 */
data class FileInfo(
    val path: String,
    val name: String? = null
) {
    constructor(path: String) : this(path, null)

    val fileName: String
        get() = name ?: java.io.File(path).name
}

/**
 * IDE 主题信息
 */
@Serializable
data class IdeTheme(
    val background: String = "#2b2b2b",
    val foreground: String = "#a9b7c6",
    val borderColor: String = "#3c3f41",
    val panelBackground: String = "#3c3f41",
    val textFieldBackground: String = "#45494a",
    val selectionBackground: String = "#214283",
    val selectionForeground: String = "#ffffff",
    val linkColor: String = "#4e9a06",
    val errorColor: String = "#cc0000",
    val warningColor: String = "#f57900",
    val successColor: String = "#4e9a06",
    val separatorColor: String = "#515658",
    val hoverBackground: String = "#4e5254",
    val accentColor: String = "#4e9a06",
    val infoBackground: String = "#3c3f41",
    val codeBackground: String = "#2b2b2b",
    val secondaryForeground: String = "#808080",
    // 字体设置
    val fontFamily: String = "JetBrains Mono, Consolas, monospace",
    val fontSize: Int = 13,
    val editorFontFamily: String = "JetBrains Mono, Consolas, monospace",
    val editorFontSize: Int = 13
)

/**
 * Node.js 检测结果
 */
@Serializable
data class NodeDetectionResult(
    val found: Boolean,              // 是否找到 Node.js
    val path: String? = null,        // Node.js 路径（如果找到）
    val version: String? = null,     // Node.js 版本（如果找到）
    val error: String? = null        // 错误信息（如果检测失败）
)

/**
 * 活动文件信息
 */
@Serializable
data class ActiveFileInfo(
    val path: String,                     // 文件绝对路径
    val relativePath: String,             // 相对项目根目录的路径
    val name: String,                     // 文件名
    val line: Int? = null,                // 光标所在行（1-based）
    val column: Int? = null,              // 光标所在列（1-based）
    val hasSelection: Boolean = false,    // 是否有选区
    val startLine: Int? = null,           // 选区开始行（1-based）
    val startColumn: Int? = null,         // 选区开始列（1-based）
    val endLine: Int? = null,             // 选区结束行（1-based）
    val endColumn: Int? = null,           // 选区结束列（1-based）
    val selectedContent: String? = null,  // 选中的内容
    // 文件类型相关字段
    val fileType: String = "text",        // 文件类型: "text", "diff", "image", "binary"
    // Diff 视图专用字段
    val diffOldContent: String? = null,   // Diff 旧内容（左侧）
    val diffNewContent: String? = null,   // Diff 新内容（右侧）
    val diffTitle: String? = null         // Diff 标题
)
