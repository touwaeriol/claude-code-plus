package com.claudecodeplus.completion

import com.claudecodeplus.debug.CompletionDebugger
import com.claudecodeplus.ui.FileSearchUtil
import com.claudecodeplus.ui.createFileLookupElement
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext

/**
 * 文件引用自动完成贡献者
 * 在输入 @ 后提供项目文件的自动完成
 */
class FileReferenceCompletionContributor : CompletionContributor() {
    
    init {
        // 注册完成提供者，在所有位置都可以使用
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            FileReferenceCompletionProvider()
        )
    }
}

/**
 * 文件引用完成提供者
 */
private class FileReferenceCompletionProvider : CompletionProvider<CompletionParameters>() {
    
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        // 添加调试信息（已禁用）
        // CompletionDebugger.debugCompletionContext(parameters, result, "FileReferenceCompletionProvider")
        
        val position = parameters.position
        val document = parameters.editor.document
        val text = document.text
        val offset = parameters.offset
        
        // 查找最近的 @ 符号
        val atInfo = findAtSymbol(text, offset)
        if (atInfo == null) {
            // 如果没有找到 @ 符号，不提供补全
            return
        }
        
        // 如果 @ 符号太远（超过100个字符），不提供补全
        if (offset - atInfo.offset > 100) return
        
        // 获取查询字符串
        val query = atInfo.query
        
        // 搜索文件
        val project = parameters.position.project
        val searchResults = FileSearchUtil.searchProjectFiles(project, query, 30)
        
        // 创建带前缀的结果集
        val prefixedResult = result.withPrefixMatcher(query)
        
        // 添加查找元素
        searchResults.forEach { searchResult ->
            val lookupElement = createFileLookupElement(searchResult)
            prefixedResult.addElement(lookupElement)
        }
        
        // 如果有结果，停止其他贡献者
        if (searchResults.isNotEmpty()) {
            result.stopHere()
        }
    }
    
    /**
     * 查找 @ 符号和查询字符串
     */
    private fun findAtSymbol(text: String, offset: Int): AtSymbolInfo? {
        // 从当前位置向前搜索 @
        for (i in offset - 1 downTo maxOf(0, offset - 100)) {
            if (i < text.length && text[i] == '@') {
                // 检查 @ 前面必须是空格、换行或者是文本开头
                val hasSpaceBefore = i == 0 || text[i - 1] in " \n\t"
                if (!hasSpaceBefore) continue
                
                // 检查 @ 后面的内容
                val afterAtIndex = i + 1
                
                // 如果 @ 是最后一个字符，或者后面紧跟空格，认为是有效的引用符号
                if (afterAtIndex >= text.length || afterAtIndex >= offset) {
                    return AtSymbolInfo(i, "")
                }
                
                // 提取 @ 后面到当前光标位置的内容
                val afterContent = text.substring(afterAtIndex, offset)
                
                // 如果包含空格或换行，说明已经不是文件引用了
                if (afterContent.contains(' ') || afterContent.contains('\n')) {
                    continue
                }
                
                return AtSymbolInfo(i, afterContent.trim())
            }
        }
        return null
    }
    
    /**
     * @ 符号信息
     */
    private data class AtSymbolInfo(
        val offset: Int,    // @ 符号的位置
        val query: String   // @ 后面的查询字符串
    )
}