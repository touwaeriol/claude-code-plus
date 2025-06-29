package com.claudecodeplus.sdk

import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * ANSI转义序列处理器
 * 用于识别、记录和清理Claude CLI输出中的ANSI编码
 */
object AnsiProcessor {
    private val logger = Logger.getLogger(AnsiProcessor::class.java.name)
    
    // ANSI转义序列的正则表达式模式
    private val ANSI_PATTERNS = listOf(
        // 颜色控制序列 \u001B[...m
        Pattern.compile("\\u001B\\[[;\\d]*m"),
        
        // 窗口标题设置序列 \u001B]0;...BEL
        Pattern.compile("\\u001B\\]0;[^\\u0007]*\\u0007"),
        
        // 其他OSC序列 \u001B]...BEL
        Pattern.compile("\\u001B\\][^\\u0007]*\\u0007"),
        
        // 光标控制序列 \u001B[...H, \u001B[...A 等
        Pattern.compile("\\u001B\\[[\\d;]*[ABCDEFGHJKST]"),
        
        // 清屏序列 \u001B[2J, \u001B[H 等
        Pattern.compile("\\u001B\\[[\\d]*[JK]"),
        
        // 其他控制序列
        Pattern.compile("\\u001B\\[[^a-zA-Z]*[a-zA-Z]")
    )
    
    // 组合的ANSI检测模式
    private val COMBINED_ANSI_PATTERN = Pattern.compile(
        ANSI_PATTERNS.joinToString("|") { it.pattern() }
    )
    
    data class AnsiInfo(
        val sequence: String,
        val type: AnsiType,
        val startIndex: Int,
        val endIndex: Int,
        val description: String
    )
    
    enum class AnsiType {
        COLOR,          // 颜色控制
        WINDOW_TITLE,   // 窗口标题
        CURSOR,         // 光标控制
        CLEAR,          // 清屏
        OSC,            // Operating System Command
        OTHER           // 其他
    }
    
    /**
     * 检测文本中的ANSI序列
     * @param text 要检测的文本
     * @return ANSI序列信息列表
     */
    fun detectAnsiSequences(text: String): List<AnsiInfo> {
        val sequences = mutableListOf<AnsiInfo>()
        val matcher = COMBINED_ANSI_PATTERN.matcher(text)
        
        while (matcher.find()) {
            val sequence = matcher.group()
            val type = determineAnsiType(sequence)
            val description = getAnsiDescription(sequence, type)
            
            sequences.add(AnsiInfo(
                sequence = sequence,
                type = type,
                startIndex = matcher.start(),
                endIndex = matcher.end(),
                description = description
            ))
        }
        
        return sequences
    }
    
    /**
     * 清理文本中的ANSI序列并记录日志
     * @param text 原始文本
     * @return 清理后的文本
     */
    fun cleanAnsiSequences(text: String): String {
        val sequences = detectAnsiSequences(text)
        
        if (sequences.isNotEmpty()) {
            logger.info("检测到 ${sequences.size} 个ANSI序列:")
            sequences.forEach { ansi ->
                logger.info("  - 类型: ${ansi.type}, 序列: ${escapeForLog(ansi.sequence)}, 描述: ${ansi.description}")
            }
            
            // 输出完整的原始文本
            logger.info("原始文本（完整内容）: ${escapeForLog(text)}")
            
            val cleanText = COMBINED_ANSI_PATTERN.matcher(text).replaceAll("")
            
            // 输出完整的清理后文本
            logger.info("清理后文本（完整内容）: ${cleanText}")
            
            return cleanText
        }
        
        return text
    }
    
    /**
     * 判断ANSI序列类型
     */
    private fun determineAnsiType(sequence: String): AnsiType {
        return when {
            sequence.matches(Regex("\\u001B\\[[;\\d]*m")) -> AnsiType.COLOR
            sequence.matches(Regex("\\u001B\\]0;.*\\u0007")) -> AnsiType.WINDOW_TITLE
            sequence.matches(Regex("\\u001B\\[[\\d;]*[ABCDEFGH]")) -> AnsiType.CURSOR
            sequence.matches(Regex("\\u001B\\[[\\d]*[JK]")) -> AnsiType.CLEAR
            sequence.matches(Regex("\\u001B\\].*\\u0007")) -> AnsiType.OSC
            else -> AnsiType.OTHER
        }
    }
    
    /**
     * 获取ANSI序列的描述
     */
    private fun getAnsiDescription(sequence: String, type: AnsiType): String {
        return when (type) {
            AnsiType.COLOR -> "颜色控制序列"
            AnsiType.WINDOW_TITLE -> {
                val titleMatch = Regex("\\u001B\\]0;([^\\u0007]*)\\u0007").find(sequence)
                val title = titleMatch?.groupValues?.get(1) ?: "未知"
                "设置窗口标题为: $title"
            }
            AnsiType.CURSOR -> "光标控制序列"
            AnsiType.CLEAR -> "清屏序列"
            AnsiType.OSC -> "操作系统命令序列"
            AnsiType.OTHER -> "其他控制序列"
        }
    }
    
    /**
     * 转义字符串用于日志输出
     */
    private fun escapeForLog(text: String): String {
        return text
            .replace("\u001B", "\\u001B")
            .replace("\u0007", "\\u0007")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }
    
    /**
     * 检查文本是否包含ANSI序列
     */
    fun containsAnsiSequences(text: String): Boolean {
        return COMBINED_ANSI_PATTERN.matcher(text).find()
    }
}