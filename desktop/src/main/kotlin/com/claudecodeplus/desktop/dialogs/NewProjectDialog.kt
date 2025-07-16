package com.claudecodeplus.desktop.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import java.awt.FileDialog
import java.io.File

/**
 * 新建项目对话框
 */
@Composable
fun FrameWindowScope.NewProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, path: String) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var projectPath by remember { mutableStateOf(System.getProperty("user.home")) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(width = 600.dp, height = 400.dp),
        title = "新建项目"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ... (项目名称输入部分保持不变)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "项目名称",
                    style = JewelTheme.defaultTextStyle
                )
                BasicTextField(
                    value = projectName,
                    onValueChange = { newValue ->
                        projectName = newValue
                        showError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            JewelTheme.globalColors.borders.normal,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp),
                    textStyle = TextStyle(
                        color = JewelTheme.globalColors.text.normal,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                    decorationBox = { innerTextField ->
                        Box {
                            if (projectName.isEmpty()) {
                                Text(
                                    "请输入项目名称",
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // 项目路径选择
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "项目路径",
                    style = JewelTheme.defaultTextStyle
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = projectPath,
                        onValueChange = { newValue ->
                            projectPath = newValue
                            showError = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                JewelTheme.globalColors.panelBackground,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                JewelTheme.globalColors.borders.normal,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp),
                        textStyle = TextStyle(
                            color = JewelTheme.globalColors.text.normal,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(JewelTheme.globalColors.text.normal)
                    )

                    DefaultButton(
                        onClick = {
                            val fileDialog = FileDialog(window, "选择项目根目录", FileDialog.LOAD)
                            // For macOS, we need to set a specific property to allow directory selection
                            System.setProperty("apple.awt.fileDialogForDirectories", "true")
                            fileDialog.isVisible = true
                            System.setProperty("apple.awt.fileDialogForDirectories", "false")

                            if (fileDialog.directory != null && fileDialog.file != null) {
                                projectPath = File(fileDialog.directory, fileDialog.file).absolutePath
                            }
                        }
                    ) {
                        Text("浏览...")
                    }
                }
            }

            // ... (剩余部分保持不变)
            if (projectName.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "项目将创建在：",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                    Text(
                        text = File(projectPath, projectName).absolutePath,
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.normal
                        )
                    )
                }
            }

            if (showError) {
                Text(
                    text = errorMessage,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.error
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Divider(orientation = Orientation.Horizontal)

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
                            projectName.isBlank() -> {
                                showError = true
                                errorMessage = "请输入项目名称"
                            }
                            projectPath.isBlank() -> {
                                showError = true
                                errorMessage = "请选择项目路径"
                            }
                            else -> {
                                val fullPath = File(projectPath, projectName).absolutePath
                                if (File(fullPath).exists()) {
                                    showError = true
                                    errorMessage = "该路径下已存在同名项目"
                                } else {
                                    onConfirm(projectName, fullPath)
                                }
                            }
                        }
                    },
                    enabled = projectName.isNotBlank() && projectPath.isNotBlank()
                ) {
                    Text("创建")
                }
            }
        }
    }
}
