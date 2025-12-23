package com.asakii.plugin.compat

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton

/**
 * 文件浏览按钮兼容层 - 适用于 2024.2 ~ 2025.2
 *
 * 使用 4 参数的 addBrowseFolderListener API (title, description, project, descriptor)
 * 此 API 在 2025.3 中被标记为 deprecated，但在旧版本中是唯一可用的 API
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
        @Suppress("DEPRECATION")
        textField.addBrowseFolderListener(title, description, project, descriptor)
    }
}
