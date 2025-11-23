package com.claudecodeplus.plugin.ui.dialogs

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Diff 预览对话框
 * 
 * 在应用修改前显示 diff 预览
 */
class DiffPreviewDialog(
    private val project: Project,
    private val filePath: String,
    private val oldContent: String,
    private val newContent: String
) : DialogWrapper(project) {
    
    private var accepted = false
    
    init {
        title = "预览更改: $filePath"
        setOKButtonText("应用")
        setCancelButtonText("取消")
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(800, 600)
        
        // 创建 Diff 视图
        val diffPanel = createDiffPanel()
        panel.add(diffPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createDiffPanel(): JComponent {
        val contentFactory = DiffContentFactory.getInstance()
        
        val oldContentDoc = contentFactory.create(project, oldContent)
        val newContentDoc = contentFactory.create(project, newContent)
        
        val diffRequest = SimpleDiffRequest(
            "文件更改预览",
            oldContentDoc,
            newContentDoc,
            "原始内容",
            "修改后"
        )
        
        val diffPanel = DiffManager.getInstance().createRequestPanel(project, this.disposable, null)
        diffPanel.setRequest(diffRequest)
        
        return diffPanel.component
    }
    
    override fun doOKAction() {
        accepted = true
        super.doOKAction()
    }
    
    fun isAccepted(): Boolean = accepted
}


