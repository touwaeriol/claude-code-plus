package com.claudecodeplus.plugin.ui.markdown

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * 代码高亮器
 * 
 * 使用 IntelliJ 的语法高亮引擎为代码块提供高亮
 */
class CodeHighlighter(private val project: Project? = null) {
    
    private val colorScheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme
    
    /**
     * 对代码进行语法高亮
     * 
     * @param code 代码文本
     * @param language 语言标识（如 "kotlin", "java", "python"）
     * @return 高亮后的样式化文本组件数据
     */
    fun highlight(code: String, language: String?): List<HighlightedSegment> {
        if (language.isNullOrBlank()) {
            return listOf(HighlightedSegment(code, getDefaultCodeColor(), Font.PLAIN))
        }
        
        // 尝试获取文件类型
        val fileType = getFileTypeFromLanguage(language)
        if (fileType == null) {
            return listOf(HighlightedSegment(code, getDefaultCodeColor(), Font.PLAIN))
        }
        
        // 简化版：使用基础的语法高亮模式
        return highlightBasic(code, language)
    }
    
    /**
     * 基础语法高亮（针对常见语言）
     */
    private fun highlightBasic(code: String, language: String): List<HighlightedSegment> {
        val segments = mutableListOf<HighlightedSegment>()
        
        when (language.lowercase()) {
            "kotlin", "kt" -> highlightKotlin(code, segments)
            "java" -> highlightJava(code, segments)
            "python", "py" -> highlightPython(code, segments)
            "javascript", "js", "typescript", "ts" -> highlightJavaScript(code, segments)
            "json" -> highlightJson(code, segments)
            "xml", "html" -> highlightXml(code, segments)
            "sql" -> highlightSql(code, segments)
            "bash", "sh", "shell" -> highlightBash(code, segments)
            else -> segments.add(HighlightedSegment(code, getDefaultCodeColor(), Font.PLAIN))
        }
        
        return segments
    }
    
    private fun highlightKotlin(code: String, segments: MutableList<HighlightedSegment>) {
        val keywords = setOf(
            "package", "import", "class", "interface", "fun", "val", "var", "if", "else", 
            "when", "for", "while", "do", "return", "break", "continue", "object", "companion",
            "data", "sealed", "enum", "annotation", "public", "private", "protected", "internal",
            "override", "open", "abstract", "final", "suspend", "inline", "operator", "infix"
        )
        highlightWithKeywords(code, keywords, segments)
    }
    
    private fun highlightJava(code: String, segments: MutableList<HighlightedSegment>) {
        val keywords = setOf(
            "package", "import", "class", "interface", "extends", "implements", "public", 
            "private", "protected", "static", "final", "abstract", "synchronized", "volatile",
            "if", "else", "for", "while", "do", "switch", "case", "return", "break", "continue",
            "new", "this", "super", "try", "catch", "finally", "throw", "throws"
        )
        highlightWithKeywords(code, keywords, segments)
    }
    
    private fun highlightPython(code: String, segments: MutableList<HighlightedSegment>) {
        val keywords = setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from",
            "as", "try", "except", "finally", "with", "lambda", "yield", "async", "await",
            "pass", "break", "continue", "global", "nonlocal", "True", "False", "None"
        )
        highlightWithKeywords(code, keywords, segments)
    }
    
    private fun highlightJavaScript(code: String, segments: MutableList<HighlightedSegment>) {
        val keywords = setOf(
            "function", "const", "let", "var", "if", "else", "for", "while", "return", "break",
            "continue", "switch", "case", "default", "try", "catch", "finally", "throw",
            "async", "await", "class", "extends", "import", "export", "from", "new", "this"
        )
        highlightWithKeywords(code, keywords, segments)
    }
    
    private fun highlightJson(code: String, segments: MutableList<HighlightedSegment>) {
        segments.add(HighlightedSegment(code, getStringColor(), Font.PLAIN))
    }
    
    private fun highlightXml(code: String, segments: MutableList<HighlightedSegment>) {
        segments.add(HighlightedSegment(code, getDefaultCodeColor(), Font.PLAIN))
    }
    
    private fun highlightSql(code: String, segments: MutableList<HighlightedSegment>) {
        val keywords = setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER",
            "DROP", "TABLE", "INDEX", "VIEW", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
            "ON", "AND", "OR", "NOT", "ORDER", "BY", "GROUP", "HAVING", "LIMIT"
        )
        highlightWithKeywords(code, keywords, segments)
    }
    
    private fun highlightBash(code: String, segments: MutableList<HighlightedSegment>) {
        val keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
            "esac", "function", "return", "exit", "echo", "cd", "ls", "cat", "grep"
        )
        highlightWithKeywords(code, keywords, segments)
    }
    
    /**
     * 使用关键字进行高亮
     */
    private fun highlightWithKeywords(
        code: String, 
        keywords: Set<String>, 
        segments: MutableList<HighlightedSegment>
    ) {
        val lines = code.lines()
        val keywordColor = getKeywordColor()
        val stringColor = getStringColor()
        val commentColor = getCommentColor()
        val defaultColor = getDefaultCodeColor()
        
        for ((lineIndex, line) in lines.withIndex()) {
            var pos = 0
            
            // 检查是否是注释行
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                segments.add(HighlightedSegment(line, commentColor, Font.ITALIC))
                if (lineIndex < lines.size - 1) {
                    segments.add(HighlightedSegment("\n", defaultColor, Font.PLAIN))
                }
                continue
            }
            
            // 简单的词法分析
            val tokens = tokenize(line)
            for (token in tokens) {
                val color = when {
                    keywords.contains(token) -> keywordColor
                    token.matches(Regex("\".*\"")) || token.matches(Regex("'.*'")) -> stringColor
                    else -> defaultColor
                }
                segments.add(HighlightedSegment(token, color, Font.PLAIN))
            }
            
            if (lineIndex < lines.size - 1) {
                segments.add(HighlightedSegment("\n", defaultColor, Font.PLAIN))
            }
        }
    }
    
    /**
     * 简单的词法分析
     */
    private fun tokenize(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inString = false
        var stringChar = ' '
        
        for (char in line) {
            when {
                !inString && (char == '"' || char == '\'') -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                    inString = true
                    stringChar = char
                    current.append(char)
                }
                inString && char == stringChar -> {
                    current.append(char)
                    tokens.add(current.toString())
                    current = StringBuilder()
                    inString = false
                }
                inString -> {
                    current.append(char)
                }
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                    tokens.add(char.toString())
                }
                char in "(){}[].,;:+-*/<>=!&|" -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                    tokens.add(char.toString())
                }
                else -> {
                    current.append(char)
                }
            }
        }
        
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        
        return tokens
    }
    
    private fun getFileTypeFromLanguage(language: String): FileType? {
        val normalizedLang = language.lowercase()
        val extension = when (normalizedLang) {
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "json" -> "json"
            "xml" -> "xml"
            "html" -> "html"
            "sql" -> "sql"
            "bash", "sh", "shell" -> "sh"
            else -> normalizedLang
        }
        
        return try {
            FileTypeManager.getInstance().getFileTypeByExtension(extension)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getKeywordColor(): Color {
        return JBColor(Color(0xCC7832), Color(0xCC7832))
    }
    
    private fun getStringColor(): Color {
        return JBColor(Color(0x6A8759), Color(0x6A8759))
    }
    
    private fun getCommentColor(): Color {
        return JBColor(Color(0x808080), Color(0x808080))
    }
    
    private fun getDefaultCodeColor(): Color {
        return JBColor(Color(0xE6E6E6), Color(0xE6E6E6))
    }
}

/**
 * 高亮片段
 */
data class HighlightedSegment(
    val text: String,
    val color: Color,
    val fontStyle: Int
)


