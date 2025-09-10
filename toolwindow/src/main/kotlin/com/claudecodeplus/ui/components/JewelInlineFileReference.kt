/*
 * JewelInlineFileReference.kt
 * 
 * 使用 Jewel EditableComboBox 实现的文件引用组件
 * 解决了定位和焦点管理问题
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.services.IndexedFileInfo
import com.claudecodeplus.ui.services.FileIndexService
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd

// 注意：isInAtQuery 函数已在 SimpleInlineFileReference.kt 中定义，这里直接使用

/**
 * 使用 Jewel EditableComboBox 实现的文件引用处理器
 * 
 * 优势：
 * - 自动处理弹窗位置（不会覆盖输入框）
 * - 内置键盘导航和焦点管理
 * - 与 Jewel 主题完全一致
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun JewelInlineFileReferenceHandler(
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    fileIndexService: FileIndexService?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var searchResults by remember { mutableStateOf<List<IndexedFileInfo>>(emptyList()) }
    var atPosition by remember { mutableStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }
    var showFileSelector by remember { mutableStateOf(false) }
    
    // 创建 TextFieldState 用于 EditableComboBox
    val comboBoxState = rememberTextFieldState("")
    
    // 实时检测光标位置是否在 @ 字符串中
    LaunchedEffect(textFieldValue.text, textFieldValue.selection.start) {
        if (!enabled || fileIndexService == null) {
            showFileSelector = false
            return@LaunchedEffect
        }
        
        val cursorPos = textFieldValue.selection.start
        val atResult = isInAtQuery(textFieldValue.text, cursorPos)
        
        if (atResult != null) {
            val (atPos, query) = atResult
            atPosition = atPos
            searchQuery = query
            
            // 更新 ComboBox 的状态
            comboBoxState.setTextAndPlaceCursorAtEnd(query)
            
            // 搜索文件
            try {
                val results = if (query.isEmpty()) {
                    fileIndexService.getRecentFiles(10)
                } else {
                    fileIndexService.searchFiles(query, 10)
                }
                searchResults = results
                showFileSelector = results.isNotEmpty()
            } catch (e: Exception) {
                searchResults = emptyList()
                showFileSelector = false
            }
        } else {
            showFileSelector = false
            searchResults = emptyList()
        }
    }
    
    Box(modifier = modifier) {
        // 当检测到 @ 查询时，显示 EditableComboBox
        if (showFileSelector && searchResults.isNotEmpty()) {
            EditableComboBox(
                textFieldState = comboBoxState,
                modifier = Modifier.fillMaxWidth(),
                popupContent = {
                    Column(
                        modifier = Modifier
                            .width(400.dp)  // 设置合适的宽度
                            .heightIn(max = 300.dp)  // 限制最大高度
                    ) {
                        searchResults.forEach { file ->
                            JewelFileItem(
                                file = file,
                                searchQuery = searchQuery,
                                onClick = {
                                    // 替换 @ 查询为文件引用
                                    val currentText = textFieldValue.text
                                    val fileName = file.name
                                    val baseReplacement = "@$fileName"
                                    
                                    val replaceStart = atPosition
                                    val replaceEnd = atPosition + 1 + searchQuery.length
                                    
                                    // 检查替换后的位置是否需要添加空格
                                    val needsSpace = replaceEnd >= currentText.length || 
                                                    (replaceEnd < currentText.length && currentText[replaceEnd] !in " \n\t")
                                    
                                    val replacement = if (needsSpace) "$baseReplacement " else baseReplacement
                                    val newText = currentText.replaceRange(replaceStart, replaceEnd, replacement)
                                    val newPosition = replaceStart + replacement.length
                                    
                                    onTextChange(TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(newPosition)
                                    ))
                                    
                                    // 关闭选择器
                                    showFileSelector = false
                                    searchResults = emptyList()
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * 获取文件图标 - 使用简单的表情符号代替复杂的图标路径
 */
private fun getFileIcon(fileName: String): String {
    return when (fileName.substringAfterLast('.', "")) {
        "kt" -> "🟢"
        "java" -> "☕"
        "js", "ts", "tsx", "jsx" -> "🟨"
        "py" -> "🐍"
        "md" -> "📝"
        "json" -> "📋"
        "xml", "html", "htm" -> "📄"
        "gradle", "kts" -> "🐘"
        "properties", "yml", "yaml" -> "⚙️"
        "css", "scss", "sass" -> "🎨"
        "png", "jpg", "jpeg", "gif", "svg" -> "🖼️"
        "pdf" -> "📕"
        "txt" -> "📄"
        "sh", "bat", "cmd" -> "⚡"
        else -> "📄"
    }
}

/**
 * 构建带高亮的文本 - 简化版本
 */
@Composable
private fun buildHighlightedText(fileName: String, searchQuery: String): androidx.compose.ui.text.AnnotatedString {
    if (searchQuery.isEmpty()) {
        return androidx.compose.ui.text.AnnotatedString(fileName)
    }
    
    return buildAnnotatedString {
        val index = fileName.indexOf(searchQuery, ignoreCase = true)
        if (index >= 0) {
            append(fileName.substring(0, index))
            withStyle(SpanStyle(
                background = JewelTheme.globalColors.borders.focused.copy(alpha = 0.3f),
                fontWeight = FontWeight.Bold
            )) {
                append(fileName.substring(index, index + searchQuery.length))
            }
            append(fileName.substring(index + searchQuery.length))
        } else {
            append(fileName)
        }
    }
}

/**
 * Jewel 风格的文件项组件 - 使用标准SimpleListItem的简化版本
 */
@Composable
private fun JewelFileItem(
    file: IndexedFileInfo,
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用简化的Row布局代替复杂的SimpleListItem
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 文件图标（使用表情符号）
        Text(
            text = if (file.isDirectory) "📁" else getFileIcon(file.name),
            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
        )
        
        // 文件信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 文件名（带搜索高亮）
            Text(
                text = buildHighlightedText(file.name, searchQuery),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // 路径信息
            if (file.relativePath.isNotEmpty()) {
                val displayPath = file.relativePath.removeSuffix("/${file.name}").removeSuffix(file.name)
                if (displayPath.isNotEmpty()) {
                    Text(
                        text = displayPath,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.disabled
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}