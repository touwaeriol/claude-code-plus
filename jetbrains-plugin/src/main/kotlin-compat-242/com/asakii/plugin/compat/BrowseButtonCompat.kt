package com.asakii.plugin.compat

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton

/**
 * 文件浏览按钮兼容层 - 适用于 2024.1 ~ 2024.2
 *
 * 在这些版本中，使用 4 参数的 addBrowseFolderListener API
 */
object BrowseButtonCompat {

    /**
     * 添加文件夹浏览监听器
     *
     * @param textField 带浏览按钮的文本框
     * @param title 对话框标题
     * @param description 对话框描述
     * @param project 项目（可选）
     * @param descriptor 文件选择器描述符
     */
    fun addBrowseFolderListener(
        textField: TextFieldWithBrowseButton,
        title: String,
        description: String,
        project: Project?,
        descriptor: FileChooserDescriptor
    ) {
        // 2024.2 及更早版本使用 4 参数 API
        @Suppress("DEPRECATION")
        textField.addBrowseFolderListener(title, description, project, descriptor)
    }
}
