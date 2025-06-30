package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ContextReference
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 智能输入区域组件 - 简化版本
 * 支持多行输入和基本功能
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
    val focusRequester = remember { FocusRequester() }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 上下文标签
        if (contexts.isNotEmpty()) {
            HorizontallyScrollableContainer {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    contexts.forEach { context ->
                        ContextChip(
                            context = context,
                            onRemove = { onContextRemove(context) }
                        )
                    }
                }
            }
        }
        
        // 输入区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 主输入框
            Box(
                modifier = Modifier.weight(1f)
            ) {
                TextArea(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = enabled && !isGenerating,
                    placeholder = "输入消息...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 120.dp)
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
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(4.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(4.dp))
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
            fontSize = 12.sp,
            style = JewelTheme.defaultTextStyle
        )
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(16.dp)
        ) {
            Text("×", fontSize = 12.sp)
        }
    }
}