package com.claudecodeplus.ui

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.claudecodeplus.model.SelectionOption
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.DefaultListCellRenderer

/**
 * 选择模式面板
 */
class SelectionPanel {
    private val panel = JPanel(BorderLayout())
    private val listModel = DefaultListModel<SelectionOption>()
    private val selectionList = JBList(listModel)
    private var onSelectionConfirmed: ((Int) -> Unit)? = null
    private var onSelectionCancelled: (() -> Unit)? = null
    
    init {
        setupUI()
        setupListeners()
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        panel.apply {
            background = Color(40, 44, 52)
            border = JBUI.Borders.empty(10)
        }
        
        // 标题
        val titleLabel = JLabel("Select an option").apply {
            font = font.deriveFont(Font.BOLD, 14f)
            foreground = Color(171, 178, 191)
            border = JBUI.Borders.emptyBottom(10)
        }
        
        // 列表设置
        selectionList.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = SelectionPanelCellRenderer()
            background = Color(40, 44, 52)
            foreground = Color(171, 178, 191)
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            fixedCellHeight = 30
            border = JBUI.Borders.empty()
        }
        
        // 滚动面板
        val scrollPane = JBScrollPane(selectionList).apply {
            border = JBUI.Borders.customLine(Color(60, 63, 65), 1)
            background = selectionList.background
            viewport.background = selectionList.background
        }
        
        // 提示文本
        val hintLabel = JLabel("↑↓ Navigate · Enter Confirm · Esc Cancel").apply {
            font = font.deriveFont(11f)
            foreground = Color(124, 124, 124)
            border = JBUI.Borders.emptyTop(10)
        }
        
        // 组装面板
        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(hintLabel, BorderLayout.SOUTH)
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 键盘监听
        selectionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> confirmSelection()
                    KeyEvent.VK_ESCAPE -> cancelSelection()
                    KeyEvent.VK_UP -> navigateUp()
                    KeyEvent.VK_DOWN -> navigateDown()
                }
            }
            
            override fun keyTyped(e: KeyEvent) {
                // 数字快捷键
                val digit = e.keyChar.digitToIntOrNull()
                if (digit != null && digit > 0 && digit <= listModel.size()) {
                    selectionList.selectedIndex = digit - 1
                    confirmSelection()
                }
            }
        })
        
        // 鼠标双击
        selectionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    confirmSelection()
                }
            }
        })
    }
    
    /**
     * 更新选项列表
     */
    fun updateOptions(options: List<SelectionOption>, selectedIndex: Int = 0) {
        SwingUtilities.invokeLater {
            listModel.clear()
            options.forEach { listModel.addElement(it) }
            
            if (options.isNotEmpty() && selectedIndex in 0 until options.size) {
                selectionList.selectedIndex = selectedIndex
                selectionList.ensureIndexIsVisible(selectedIndex)
            }
        }
    }
    
    /**
     * 获取组件
     */
    fun getComponent(): JComponent = panel
    
    /**
     * 设置选择确认回调
     */
    fun setOnSelectionConfirmed(callback: (Int) -> Unit) {
        onSelectionConfirmed = callback
    }
    
    /**
     * 设置取消回调
     */
    fun setOnSelectionCancelled(callback: () -> Unit) {
        onSelectionCancelled = callback
    }
    
    /**
     * 确认选择
     */
    private fun confirmSelection() {
        val selectedIndex = selectionList.selectedIndex
        if (selectedIndex >= 0) {
            onSelectionConfirmed?.invoke(selectedIndex)
        }
    }
    
    /**
     * 取消选择
     */
    private fun cancelSelection() {
        onSelectionCancelled?.invoke()
    }
    
    /**
     * 向上导航
     */
    private fun navigateUp() {
        val currentIndex = selectionList.selectedIndex
        if (currentIndex > 0) {
            selectionList.selectedIndex = currentIndex - 1
            selectionList.ensureIndexIsVisible(currentIndex - 1)
        }
    }
    
    /**
     * 向下导航
     */
    private fun navigateDown() {
        val currentIndex = selectionList.selectedIndex
        if (currentIndex < listModel.size() - 1) {
            selectionList.selectedIndex = currentIndex + 1
            selectionList.ensureIndexIsVisible(currentIndex + 1)
        }
    }
    
    /**
     * 请求焦点
     */
    fun requestFocus() {
        selectionList.requestFocusInWindow()
    }
}

/**
 * 选择列表单元格渲染器
 */
private class SelectionPanelCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        
        if (value is SelectionOption) {
            text = "${index + 1}. ${value.label}"
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            
            if (isSelected) {
                background = Color(45, 125, 200)
                foreground = Color.WHITE
                border = JBUI.Borders.empty(5, 10)
            } else {
                background = Color(40, 44, 52)
                foreground = Color(171, 178, 191)
                border = JBUI.Borders.empty(5, 10)
            }
        }
        
        return this
    }
}