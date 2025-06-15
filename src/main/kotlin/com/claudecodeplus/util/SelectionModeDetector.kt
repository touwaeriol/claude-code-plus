package com.claudecodeplus.util

import com.claudecodeplus.model.SelectionOption

/**
 * 选择模式检测器，用于检测 Claude 输出中的选择框
 */
object SelectionModeDetector {
    
    // 选择框的模式
    private val SELECTION_START_PATTERN = """^(?:Please select|Choose|Select).*:?\s*$""".toRegex(RegexOption.IGNORE_CASE)
    private val OPTION_PATTERN = """^\s*\[?\s*(\d+)\s*\]?\s*[.)\-:]\s*(.+)$""".toRegex()
    private val CHECKBOX_PATTERN = """^\s*\[([xX ])\]\s*(.+)$""".toRegex()
    
    /**
     * 检测文本是否包含选择模式
     */
    fun detectSelectionMode(text: String): SelectionDetectionResult {
        val lines = text.lines()
        
        // 查找选择提示
        var promptIndex = -1
        for (i in lines.indices) {
            if (SELECTION_START_PATTERN.matches(lines[i].trim())) {
                promptIndex = i
                break
            }
        }
        
        if (promptIndex == -1) {
            return SelectionDetectionResult(false, emptyList())
        }
        
        // 解析选项
        val options = mutableListOf<SelectionOption>()
        var optionId = 1
        
        for (i in (promptIndex + 1) until lines.size) {
            val line = lines[i]
            
            // 空行可能表示选项结束
            if (line.isBlank() && options.isNotEmpty()) {
                break
            }
            
            // 尝试匹配编号选项
            val optionMatch = OPTION_PATTERN.matchEntire(line)
            if (optionMatch != null) {
                val number = optionMatch.groupValues[1]
                val label = optionMatch.groupValues[2].trim()
                options.add(SelectionOption(
                    id = number,
                    label = label,
                    value = number
                ))
                continue
            }
            
            // 尝试匹配复选框选项
            val checkboxMatch = CHECKBOX_PATTERN.matchEntire(line)
            if (checkboxMatch != null) {
                val checked = checkboxMatch.groupValues[1].lowercase() == "x"
                val label = checkboxMatch.groupValues[2].trim()
                options.add(SelectionOption(
                    id = optionId.toString(),
                    label = label,
                    value = optionId.toString(),
                    isDefault = checked
                ))
                optionId++
                continue
            }
            
            // 如果是缩进的文本，可能是选项的一部分
            if (line.startsWith("  ") && line.trim().isNotEmpty()) {
                options.add(SelectionOption(
                    id = optionId.toString(),
                    label = line.trim(),
                    value = optionId.toString()
                ))
                optionId++
            }
        }
        
        return SelectionDetectionResult(
            isSelectionMode = options.isNotEmpty(),
            options = options
        )
    }
    
    /**
     * 检测结果
     */
    data class SelectionDetectionResult(
        val isSelectionMode: Boolean,
        val options: List<SelectionOption>
    )
}