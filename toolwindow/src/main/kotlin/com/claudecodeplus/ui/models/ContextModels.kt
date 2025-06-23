package com.claudecodeplus.ui.models

// Re-export all models from UnifiedModels.kt to maintain backward compatibility
// This file is kept for compatibility but all definitions are in UnifiedModels.kt

// Context provider interface
interface ContextProvider {
    suspend fun searchFiles(query: String): List<FileContext>
    suspend fun searchSymbols(query: String): List<SymbolContext>
    suspend fun getRecentFiles(limit: Int = 10): List<FileContext>
    suspend fun getTerminalOutput(lines: Int = 50): TerminalContext
    suspend fun getProblems(filter: ProblemSeverity? = null): List<Problem>
    suspend fun getGitInfo(type: GitRefType): GitContext
    suspend fun getFolderInfo(path: String): FolderContext
}