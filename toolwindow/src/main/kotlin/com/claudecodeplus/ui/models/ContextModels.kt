package com.claudecodeplus.ui.models

/**
 * 上下文引用系统的数据模型
 * 支持 @文件、@符号、@终端 等多种上下文类型
 */

// 上下文引用类型
sealed class ContextReference {
    data class File(
        val path: String,
        val lines: IntRange? = null,
        val preview: String? = null
    ) : ContextReference()
    
    data class Symbol(
        val name: String,
        val type: SymbolType,
        val file: String,
        val line: Int,
        val preview: String? = null
    ) : ContextReference()
    
    data class Folder(
        val path: String,
        val fileCount: Int,
        val totalSize: Long
    ) : ContextReference()
    
    data class Terminal(
        val content: String,
        val timestamp: Long,
        val isError: Boolean = false
    ) : ContextReference()
    
    data class Problems(
        val problems: List<Problem>,
        val filter: ProblemSeverity? = null
    ) : ContextReference()
    
    data class Git(
        val type: GitContextType,
        val content: String
    ) : ContextReference()
}

// 符号类型
enum class SymbolType {
    CLASS,
    INTERFACE,
    FUNCTION,
    PROPERTY,
    VARIABLE,
    CONSTANT,
    ENUM,
    OBJECT
}

// 问题严重程度
enum class ProblemSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT
}

// Git 上下文类型
enum class GitContextType {
    DIFF,           // 未提交的更改
    STAGED,         // 已暂存的更改
    COMMITS,        // 提交历史
    BRANCHES,       // 分支信息
    STATUS          // 状态信息
}

// 问题信息
data class Problem(
    val severity: ProblemSeverity,
    val message: String,
    val file: String,
    val line: Int,
    val column: Int? = null
)

// 上下文提供者接口
interface ContextProvider {
    suspend fun searchFiles(query: String): List<FileContext>
    suspend fun searchSymbols(query: String): List<SymbolContext>
    suspend fun getRecentFiles(limit: Int = 10): List<FileContext>
    suspend fun getTerminalOutput(lines: Int = 50): TerminalContext
    suspend fun getProblems(filter: ProblemSeverity? = null): List<Problem>
    suspend fun getGitInfo(type: GitContextType): GitContext
    suspend fun getFolderInfo(path: String): FolderContext
}

// 文件上下文
data class FileContext(
    val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val preview: String? = null
)

// 符号上下文
data class SymbolContext(
    val name: String,
    val type: SymbolType,
    val file: String,
    val line: Int,
    val signature: String? = null,
    val documentation: String? = null
)

// 终端上下文
data class TerminalContext(
    val output: String,
    val timestamp: Long,
    val hasErrors: Boolean,
    val command: String? = null
)

// Git 上下文
data class GitContext(
    val type: GitContextType,
    val content: String,
    val files: List<String> = emptyList(),
    val stats: GitStats? = null
)

// Git 统计信息
data class GitStats(
    val additions: Int,
    val deletions: Int,
    val filesChanged: Int
)

// 文件夹上下文
data class FolderContext(
    val path: String,
    val fileCount: Int,
    val folderCount: Int,
    val totalSize: Long,
    val files: List<FileContext>
)