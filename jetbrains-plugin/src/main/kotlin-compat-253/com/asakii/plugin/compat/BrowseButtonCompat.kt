package com.asakii.plugin.compat

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton

/**
 * 文件浏览按钮兼容层 - 适用于 2025.3+
 *
 * 使用新的 2 参数 API，通过 FileChooserDescriptor 设置标题和描述
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
        // 2025.3+ 使用新的 2 参数 API，通过 descriptor 设置标题和描述
        val configuredDescriptor = descriptor
            .withTitle(title)
            .withDescription(description)
        textField.addBrowseFolderListener(project, configuredDescriptor)
    }
}
