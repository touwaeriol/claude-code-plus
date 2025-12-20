package com.asakii.plugin.compat

import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.fileEditor.FileEditor

/**
 * Diff Editor 兼容层 - 适用于 2024.3+ (243+)
 *
 * 使用 DiffEditorViewerFileEditor.editorViewer 获取 DiffRequestProcessor，
 * 再通过 getActiveRequest() 直接 API 调用获取 DiffRequest
 */
object DiffEditorCompat {

    /**
     * 从 FileEditor 获取 DiffRequest
     *
     * @param diffEditor Diff 编辑器
     * @return DiffRequest，如果无法获取则返回 null
     */
    fun getActiveRequest(diffEditor: FileEditor): DiffRequest? {
        if (diffEditor is DiffEditorViewerFileEditor) {
            val viewer = diffEditor.editorViewer
            if (viewer is DiffRequestProcessor) {
                return viewer.activeRequest
            }
        }
        return null
    }
}
