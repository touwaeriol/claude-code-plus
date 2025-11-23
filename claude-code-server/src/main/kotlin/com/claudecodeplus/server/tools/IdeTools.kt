package com.claudecodeplus.server.tools

import com.claudecodeplus.bridge.IdeTheme

/**
 * 统一的IDE操作工具接口
 * 
 * 这个接口抽象了所有IDE相关的操作，使得：
 * - IDEA插件可以直接调用实现
 * - 浏览器模式可以使用Mock实现
 * - HTTP API服务器可以统一使用这个接口
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
     * 获取IDE主题信息
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

