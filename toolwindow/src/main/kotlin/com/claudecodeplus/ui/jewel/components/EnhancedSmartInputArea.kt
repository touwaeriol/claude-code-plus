package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ContextReference
import com.claudecodeplus.ui.models.SymbolType
import com.claudecodeplus.ui.models.GitRefType
import com.claudecodeplus.ui.models.Problem
import com.claudecodeplus.ui.models.ProblemSeverity
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import kotlinx.coroutines.launch

/**
 * 增强的智能输入区域组件
 * 支持多行输入、@引用（内联显示）、快捷键等功能
 */
@Composable
fun EnhancedSmartInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var showSuggestions by remember { mutableStateOf(false) }
    var suggestionQuery by remember { mutableStateOf("") }
    var atPosition by remember { mutableStateOf(-1) }
    val focusRequester = remember { FocusRequester() }
    
    // 内联的上下文引用
    val inlineContexts = remember { mutableStateMapOf<String, ContextReference>() }
    
    // 处理文本变化，检测@符号
    fun handleTextChange(newValue: TextFieldValue) {
        val oldText = textFieldValue.text
        val newText = newValue.text
        val cursorPosition = newValue.selection.start
        
        textFieldValue = newValue
        onTextChange(newText)
        
        // 检测是否删除了上下文引用
        if (newText.length < oldText.length) {
            // 检查是否删除了内联引用
            val deletedRange = oldText.substring(
                cursorPosition, 
                cursorPosition + (oldText.length - newText.length)
            )
            
            // 查找并删除相关的内联引用
            inlineContexts.keys.filter { key ->
                oldText.contains(key) && !newText.contains(key)
            }.forEach { key ->
                inlineContexts.remove(key)
            }
        }
        
        // 检测新输入的@符号
        if (newText.length > oldText.length && cursorPosition > 0) {
            val insertedChar = newText[cursorPosition - 1]
            if (insertedChar == '@') {
                // 检查@前后是否都没有字符（或只有空白字符）
                val hasCharBefore = cursorPosition > 1 && !newText[cursorPosition - 2].isWhitespace()
                val hasCharAfter = cursorPosition < newText.length && !newText[cursorPosition].isWhitespace()
                
                if (!hasCharBefore && !hasCharAfter) {
                    showSuggestions = true
                    suggestionQuery = ""
                    atPosition = cursorPosition - 1
                }
            }
        }
        
        // 更新建议查询
        if (showSuggestions && atPosition >= 0) {
            val queryStart = atPosition + 1
            val queryEnd = newText.indexOf(' ', queryStart).let { 
                if (it == -1) newText.length else it 
            }
            
            if (queryStart <= newText.length) {
                val query = newText.substring(queryStart, queryEnd)
                // 检查是否已经是完整的引用格式
                val referencePattern = """^(file|folder|symbol|terminal|problems|git|code|https?|selection|workspace)://.*""".toRegex()
                if (query.matches(referencePattern)) {
                    showSuggestions = false
                } else if (query.contains("://")) {
                    // 包含 :// 但不是已知类型，也关闭建议
                    showSuggestions = false
                } else {
                    suggestionQuery = query
                }
            }
        }
    }
    
    // 选择上下文引用
    fun selectContext(reference: ContextReference) {
        if (atPosition >= 0) {
            val beforeAt = textFieldValue.text.substring(0, atPosition)
            val afterAt = textFieldValue.text.substring(atPosition + 1 + suggestionQuery.length)
            
            val referenceText = when (reference) {
                is ContextReference.FileReference -> "@file://${reference.path}"
                is ContextReference.FolderReference -> "@folder://${reference.path}"
                is ContextReference.SymbolReference -> "@symbol://${reference.name}"
                is ContextReference.TerminalReference -> "@terminal://"
                is ContextReference.ProblemsReference -> "@problems://"
                is ContextReference.GitReference -> "@git://${reference.type.name.lowercase()}"
                ContextReference.SelectionReference -> "@selection://"
                ContextReference.WorkspaceReference -> "@workspace://"
            }
            
            val newText = beforeAt + referenceText + " " + afterAt
            val newCursorPosition = beforeAt.length + referenceText.length + 1
            
            textFieldValue = TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPosition)
            )
            onTextChange(newText)
            
            // 保存内联引用
            inlineContexts[referenceText] = reference
            onContextAdd(reference)
            
            showSuggestions = false
            atPosition = -1
            suggestionQuery = ""
        }
    }
    
    Column(
        modifier = modifier
            .background(Color(0xFF2B2B2B))
            .padding(16.dp)
    ) {
        // 输入框和按钮区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 输入框
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // 富文本输入框
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = ::handleTextChange,
                    enabled = enabled && !isGenerating,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 120.dp)
                        .background(Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF5C5C5C), RoundedCornerShape(4.dp))
                        .padding(12.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                // Enter 发送，Shift+Enter 换行
                                event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                                    if (!event.isShiftPressed && textFieldValue.text.isNotBlank()) {
                                        onSend()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                // Esc 停止生成或关闭建议
                                event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                                    if (showSuggestions) {
                                        showSuggestions = false
                                        true
                                    } else if (isGenerating) {
                                        onStop?.invoke()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                // 方向键在建议列表中导航
                                showSuggestions && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                                    // TODO: 实现建议列表导航
                                    true
                                }
                                else -> false
                            }
                        },
                    decorationBox = { innerTextField ->
                        Box {
                            // 占位符
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    "输入消息... 使用 @ 引用上下文",
                                    color = Color(0xFF7F7F7F),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                
                // 建议菜单
                if (showSuggestions) {
                    SuggestionMenu(
                        query = suggestionQuery,
                        onDismiss = { showSuggestions = false },
                        onSelect = ::selectContext
                    )
                }
            }
            
            // 操作按钮
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 附件按钮
                IconButton(
                    onClick = { /* TODO: 实现文件选择 */ },
                    enabled = enabled && !isGenerating
                ) {
                    Text("📎")
                }
                
                // 发送/停止按钮
                if (isGenerating) {
                    DefaultButton(
                        onClick = { onStop?.invoke() },
                        enabled = true
                    ) {
                        Text("⏹")
                    }
                } else {
                    DefaultButton(
                        onClick = onSend,
                        enabled = enabled && textFieldValue.text.isNotBlank()
                    ) {
                        Text("🚀")
                    }
                }
            }
        }
    }
    
    // 自动聚焦
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // 同步外部文本变化
    LaunchedEffect(text) {
        if (text != textFieldValue.text) {
            textFieldValue = TextFieldValue(text)
        }
    }
}


/**
 * 建议菜单
 */
@Composable
private fun SuggestionMenu(
    query: String,
    onDismiss: () -> Unit,
    onSelect: (ContextReference) -> Unit
) {
    val suggestions = listOf(
        "文件" to "📄",
        "文件夹" to "📁",
        "符号" to "🔤",
        "终端" to "💻",
        "问题" to "⚠️",
        "Git" to "🔀",
        "选中内容" to "✂️",
        "工作空间" to "🗂️"
    ).filter { (name, _) ->
        query.isEmpty() || name.contains(query, ignoreCase = true)
    }
    
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        suggestions.forEach { (name, emoji) ->
            DropdownMenuItem(
                onClick = {
                    when (name) {
                        "文件" -> {
                            // TODO: 打开文件选择对话框
                            onSelect(ContextReference.FileReference("/example/file.kt", null, null))
                        }
                        "文件夹" -> {
                            // TODO: 打开文件夹选择对话框
                            onSelect(ContextReference.FolderReference("/example/folder", 0, 0))
                        }
                        "符号" -> {
                            // TODO: 打开符号搜索对话框
                            onSelect(ContextReference.SymbolReference("ExampleClass", SymbolType.CLASS, "/example/file.kt", 1, null))
                        }
                        "终端" -> onSelect(ContextReference.TerminalReference("", 50, System.currentTimeMillis(), false))
                        "问题" -> onSelect(ContextReference.ProblemsReference(emptyList(), null))
                        "Git" -> onSelect(ContextReference.GitReference(GitRefType.STATUS, "status content"))
                        "选中内容" -> onSelect(ContextReference.SelectionReference)
                        "工作空间" -> onSelect(ContextReference.WorkspaceReference)
                    }
                }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 16.sp)
                    Text(name, fontSize = 14.sp)
                }
            }
        }
    }
}