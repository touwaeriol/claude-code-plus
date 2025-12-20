package com.asakii.plugin.compat

import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.fileEditor.FileEditor

/**
 * Diff Editor 兼容层 - 适用于 2024.1 ~ 2024.2 (241-242)
 *
 * 使用 DiffRequestProcessorEditor.getProcessor().getActiveRequest() 直接 API 调用
 */
object DiffEditorCompat {

    /**
     * 从 FileEditor 获取 DiffRequest
     *
     * @param diffEditor Diff 编辑器
     * @return DiffRequest，如果无法获取则返回 null
     */
    fun getActiveRequest(diffEditor: FileEditor): DiffRequest? {
        return if (diffEditor is DiffRequestProcessorEditor) {
            diffEditor.processor.activeRequest
        } else {
            null
        }
    }
}
