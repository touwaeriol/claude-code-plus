package com.claudecodeplus.desktop.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 新建项目对话框
 */
@Composable
fun NewProjectDialog(
    onConfirm: (projectName: String, projectPath: String) -> Unit,
    onDismiss: () -> Unit
) {
    var projectPath by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "新建项目"
    ) {
        Column(
            modifier = Modifier
                .size(500.dp, 250.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 项目选择
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择项目目录", style = JewelTheme.defaultTextStyle)
                
                DefaultButton(
                    onClick = {
                        // 使用 Swing 的文件选择器
                        val fileChooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "选择项目目录"
                            currentDirectory = File(System.getProperty("user.home"))
                        }
                        
                        val result = fileChooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            projectPath = fileChooser.selectedFile.absolutePath
                            projectName = fileChooser.selectedFile.name
                            errorMessage = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (projectPath.isEmpty()) {
                        Text("点击选择项目目录...")
                    } else {
                        Column {
                            Text(projectName, style = JewelTheme.defaultTextStyle)
                            Text(
                                projectPath,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = JewelTheme.defaultTextStyle.fontSize * 0.85f,
                                    color = JewelTheme.globalColors.text.disabled
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // 错误信息显示
            errorMessage?.let { error ->
                Text(
                    text = error,
                    style = JewelTheme.defaultTextStyle,
                    color = JewelTheme.globalColors.outlines.error
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 说明文本
            Text(
                text = "提示：新建项目将创建一个独立的会话空间，不依赖 Claude CLI 的项目配置。",
                style = JewelTheme.defaultTextStyle,
                color = JewelTheme.globalColors.text.disabled
            )
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                DefaultButton(
                    onClick = {
                        when {
                            projectPath.isBlank() -> {
                                errorMessage = "请选择项目目录"
                            }
                            else -> {
                                // 验证路径是否存在
                                val dir = File(projectPath)
                                if (!dir.exists()) {
                                    errorMessage = "指定的路径不存在"
                                } else if (!dir.isDirectory) {
                                    errorMessage = "请选择一个目录"
                                } else {
                                    onConfirm(projectName, projectPath)
                                }
                            }
                        }
                    },
                    enabled = projectPath.isNotBlank()
                ) {
                    Text("创建")
                }
            }
        }
    }
}