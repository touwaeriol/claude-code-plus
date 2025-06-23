package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ContextReference
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import kotlinx.coroutines.launch

/**
 * 智能输入区域组件
 * 支持多行输入、@引用、快捷键等功能
 */
@Composable
fun SmartInputArea(
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
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .background(Color(0xFF2B2B2B))
            .padding(16.dp)
    ) {
        // 上下文引用列表
        if (contexts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                contexts.forEach { context ->
                    ContextChip(
                        context = context,
                        onRemove = { onContextRemove(context) }
                    )
                }
            }
        }
        
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
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        onTextChange(newText)
                        // 检测 @ 符号
                        if (newText.lastOrNull() == '@') {
                            showContextMenu = true
                            contextMenuPosition = newText.length - 1
                        }
                    },
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
                                    if (!event.isShiftPressed && text.isNotBlank()) {
                                        onSend()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                // Esc 停止生成
                                event.key == Key.Escape && event.type == KeyEventType.KeyDown && isGenerating -> {
                                    onStop?.invoke()
                                    true
                                }
                                else -> false
                            }
                        }
                )
                
                // 占位符
                if (text.isEmpty()) {
                    Text(
                        "输入消息... 使用 @ 引用上下文",
                        color = Color(0xFF7F7F7F),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                // 上下文菜单
                if (showContextMenu) {
                    ContextMenu(
                        onDismiss = { showContextMenu = false },
                        onSelect = { reference ->
                            onContextAdd(reference)
                            showContextMenu = false
                            // 移除 @ 符号
                            val newText = text.substring(0, contextMenuPosition) + 
                                         text.substring(contextMenuPosition + 1)
                            onTextChange(newText)
                        }
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
                        enabled = enabled && text.isNotBlank()
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
}

/**
 * 上下文引用标签
 */
@Composable
private fun ContextChip(
    context: ContextReference,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF5C5C5C), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = when (context) {
                is ContextReference.FileReference -> "📄 ${context.path.substringAfterLast('/')}"
                is ContextReference.FolderReference -> "📁 ${context.path}"
                is ContextReference.SymbolReference -> "🔤 ${context.name}"
                is ContextReference.TerminalReference -> "💻 终端"
                is ContextReference.ProblemsReference -> "⚠️ 问题"
                is ContextReference.GitReference -> "🔀 Git"
                ContextReference.SelectionReference -> "✂️ 选中内容"
                ContextReference.WorkspaceReference -> "🗂️ 工作空间"
            },
            fontSize = 12.sp
        )
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(16.dp)
        ) {
            Text("×", fontSize = 12.sp)
        }
    }
}

/**
 * 上下文菜单
 */
@Composable
private fun ContextMenu(
    onDismiss: () -> Unit,
    onSelect: (ContextReference) -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        DropdownMenuItem(onClick = { /* TODO: 实现文件选择 */ }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📄")
                Column {
                    Text("文件")
                    Text(
                        "引用项目中的文件",
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * 0.8f,
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
        
        DropdownMenuItem(onClick = { /* TODO: 实现符号搜索 */ }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔤")
                Column {
                    Text("符号")
                    Text(
                        "引用类、函数或变量",
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * 0.8f,
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
        
        DropdownMenuItem(onClick = { onSelect(ContextReference.TerminalReference()) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💻")
                Column {
                    Text("终端")
                    Text(
                        "引用终端输出",
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * 0.8f,
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
        
        DropdownMenuItem(onClick = { onSelect(ContextReference.SelectionReference) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✂️")
                Column {
                    Text("选中内容")
                    Text(
                        "引用编辑器中的选中内容",
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * 0.8f,
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }
}