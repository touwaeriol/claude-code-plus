package com.claudecodeplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                event.key == Key.Enter && !event.isShiftPressed -> {
                                    onSendMessage()
                                    true
                                }
                                event.key == Key.Tab -> {
                                    // TODO: 处理 @ 补全
                                    false
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Text(
                        text = "输入消息，使用 @ 引用文件...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSendMessage() }
                ),
                maxLines = 5,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onSendMessage,
                enabled = value.isNotBlank(),
                modifier = Modifier.align(Alignment.Bottom)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (value.isNotBlank()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // 自动获取焦点
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
