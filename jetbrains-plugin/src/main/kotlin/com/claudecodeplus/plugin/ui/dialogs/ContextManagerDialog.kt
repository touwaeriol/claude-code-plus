package com.claudecodeplus.plugin.ui.dialogs

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*

/**
 * 上下文管理对话框
 * 
 * 管理当前对话上下文中的文件
 */
class ContextManagerDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    private val contextFiles = mutableListOf<String>()
    private val fileListModel = DefaultListModel<String>()
    private val fileList = JBList(fileListModel)
    
    init {
        title = "上下文管理"
        setOKButtonText("关闭")
        init()
        loadContextFiles()
    }
    
    private fun loadContextFiles() {
        // TODO: 从实际的上下文服务加载文件
        // 这里使用模拟数据
        contextFiles.clear()
        contextFiles.addAll(listOf(
            "src/main/kotlin/Main.kt",
            "src/main/kotlin/Service.kt",
            "README.md"
        ))
        
        refreshFileList()
    }
    
    private fun refreshFileList() {
        fileListModel.clear()
        contextFiles.forEach { file ->
            val tokenCount = estimateTokenCount(file)
            fileListModel.addElement("$file ($tokenCount tokens)")
        }
    }
    
    private fun estimateTokenCount(filePath: String): Int {
        // 简单估算：每个文件大约 1000 tokens
        // 实际应该读取文件内容并计算
        return 1000
    }
    
    private fun getTotalTokenCount(): Int {
        return contextFiles.size * 1000
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(600, 400)
        mainPanel.border = JBUI.Borders.empty(10)
        
        // 顶部信息面板
        val infoPanel = createInfoPanel()
        mainPanel.add(infoPanel, BorderLayout.NORTH)
        
        // 文件列表
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        val scrollPane = JBScrollPane(fileList)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        // 底部按钮面板
        val buttonPanel = createButtonPanel()
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }
    
    private fun createInfoPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 0, 10, 0)
        
        val infoLabel = JLabel("<html><b>当前上下文文件</b> (总计 ${contextFiles.size} 个文件, 约 ${getTotalTokenCount()} tokens)</html>")
        panel.add(infoLabel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createButtonPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.border = JBUI.Borders.empty(10, 0, 0, 0)
        
        val addButton = JButton("添加文件")
        addButton.addActionListener { addFile() }
        
        val removeButton = JButton("移除")
        removeButton.addActionListener { removeSelectedFile() }
        
        val clearButton = JButton("清空全部")
        clearButton.addActionListener { clearAllFiles() }
        
        panel.add(addButton)
        panel.add(Box.createRigidArea(Dimension(5, 0)))
        panel.add(removeButton)
        panel.add(Box.createRigidArea(Dimension(5, 0)))
        panel.add(clearButton)
        panel.add(Box.createHorizontalGlue())
        
        return panel
    }
    
    private fun addFile() {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
        descriptor.title = "选择要添加到上下文的文件"
        
        val files = FileChooser.chooseFiles(descriptor, project, null)
        files.forEach { virtualFile ->
            val path = virtualFile.path
            if (!contextFiles.contains(path)) {
                contextFiles.add(path)
            }
        }
        
        refreshFileList()
    }
    
    private fun removeSelectedFile() {
        val selectedIndex = fileList.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < contextFiles.size) {
            contextFiles.removeAt(selectedIndex)
            refreshFileList()
        }
    }
    
    private fun clearAllFiles() {
        val result = JOptionPane.showConfirmDialog(
            contentPane,
            "确定要清空所有上下文文件吗？",
            "确认清空",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        
        if (result == JOptionPane.YES_OPTION) {
            contextFiles.clear()
            refreshFileList()
        }
    }
    
    companion object {
        /**
         * 显示上下文管理对话框
         */
        fun show(project: Project?) {
            val dialog = ContextManagerDialog(project)
            dialog.show()
        }
    }
}


