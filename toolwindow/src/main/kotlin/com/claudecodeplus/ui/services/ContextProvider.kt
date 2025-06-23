package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*

/**
 * 上下文提供者服务接口
 * 提供文件、符号、终端输出等上下文信息
 */
interface ContextProvider {
    /**
     * 搜索文件
     */
    suspend fun searchFiles(query: String): List<FileContext>
    
    /**
     * 搜索符号（类、函数、变量等）
     */
    suspend fun searchSymbols(query: String): List<SymbolContext>
    
    /**
     * 获取最近使用的文件
     */
    suspend fun getRecentFiles(limit: Int = 10): List<FileContext>
    
    /**
     * 获取终端输出
     */
    suspend fun getTerminalOutput(lines: Int = 50): TerminalContext
    
    /**
     * 获取问题列表
     */
    suspend fun getProblems(filter: ProblemSeverity? = null): List<Problem>
    
    /**
     * 获取 Git 信息
     */
    suspend fun getGitInfo(type: GitContextType): GitContext
    
    /**
     * 获取文件夹信息
     */
    suspend fun getFolderInfo(path: String): FolderContext
    
    /**
     * 读取文件内容
     */
    suspend fun readFileContent(path: String, lines: IntRange? = null): String
    
    /**
     * 获取符号定义
     */
    suspend fun getSymbolDefinition(symbol: String): SymbolContext?
}