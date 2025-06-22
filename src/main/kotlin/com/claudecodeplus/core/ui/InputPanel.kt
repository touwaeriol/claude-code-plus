package com.claudecodeplus.core.ui

import com.claudecodeplus.core.interfaces.FileSearchService
import com.claudecodeplus.core.interfaces.ProjectService
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.util.logging.Logger

/**
 * 独立的输入面板组件，不依赖IDEA平台
 */
class InputPanel(
    private val projectService: ProjectService,
    private val fileSearchService: FileSearchService,
    private val onSendMessage: (String) -> Unit
) : JPanel(BorderLayout()) {
    
    companion object {
        private val logger = Logger.getLogger(InputPanel::class.java.name)
    }
    
    private val inputArea = JTextArea(1, 0)
    private val fileListModel = DefaultListModel<String>()
    private val fileList = JList(fileListModel)
    private val filePopup = JPopupMenu()
    private var currentSearchJob: Job? = null
    
    init {
        setupUI()
        setupEventHandlers()
    }
    
    private fun setupUI() {
        // 输入容器
        val inputContainer = JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Panel.background")
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            )
        }
        
        // 配置输入区域
        inputArea.apply {
            font = Font("Dialog", Font.PLAIN, 14)
            lineWrap = true
            wrapStyleWord = true
            background = inputContainer.background
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }
        
        val inputScrollPane = JScrollPane(inputArea).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            background = inputContainer.background
        }
        
        // 文件列表配置
        fileList.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 8
            cellRenderer = FileListRenderer(projectService)
        }
        
        val fileListScrollPane = JScrollPane(fileList).apply {
            preferredSize = Dimension(400, 200)
        }
        filePopup.add(fileListScrollPane)
        
        // 底部控制栏
        val controlBar = createControlBar()
        
        // 组装
        inputContainer.add(inputScrollPane, BorderLayout.CENTER)
        inputContainer.add(controlBar, BorderLayout.SOUTH)
        add(inputContainer, BorderLayout.CENTER)
    }
    
    private fun createControlBar(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Panel.background")
            preferredSize = Dimension(0, 35)
            border = BorderFactory.createEmptyBorder(8, 4, 4, 4)
            
            // 左侧：Add Context 按钮
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                
                val addContextBtn = JButton("@ Add Context").apply {
                    preferredSize = Dimension(110, 28)
                    font = Font("Dialog", Font.PLAIN, 12)
                    cursor = Cursor(Cursor.HAND_CURSOR)
                    toolTipText = "添加文件引用 (@)"
                    isFocusPainted = false
                    
                    addActionListener {
                        inputArea.insert("@", inputArea.caretPosition)
                        inputArea.requestFocusInWindow()
                        checkForFileReference()
                    }
                }
                add(addContextBtn)
            }
            
            // 右侧：发送按钮
            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                isOpaque = false
                
                val sendButton = JButton("Send").apply {
                    preferredSize = Dimension(80, 28)
                    font = Font("Dialog", Font.PLAIN, 12)
                    
                    addActionListener {
                        sendMessage()
                    }
                }
                add(sendButton)
            }
            
            add(leftPanel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.EAST)
        }
    }
    
    private fun setupEventHandlers() {
        // 文档监听器
        inputArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                SwingUtilities.invokeLater {
                    adjustInputAreaHeight()
                    checkForFileReference()
                }
            }
            
            override fun removeUpdate(e: DocumentEvent) {
                SwingUtilities.invokeLater {
                    adjustInputAreaHeight()
                    checkForFileReference()
                }
            }
            
            override fun changedUpdate(e: DocumentEvent) {
                SwingUtilities.invokeLater {
                    adjustInputAreaHeight()
                }
            }
        })
        
        // 光标监听器
        inputArea.addCaretListener {
            SwingUtilities.invokeLater {
                checkForFileReference()
            }
        }
        
        // 键盘监听器
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                handleKeyPress(e)
            }
            
            override fun keyReleased(e: KeyEvent) {
                if (!filePopup.isVisible && e.keyChar == '@') {
                    SwingUtilities.invokeLater {
                        checkForFileReference()
                    }
                }
            }
        })
        
        // 文件列表双击
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedFile = fileList.selectedValue
                    if (selectedFile != null) {
                        insertFileReference(selectedFile)
                        filePopup.isVisible = false
                    }
                }
            }
        })
    }
    
    private fun handleKeyPress(e: KeyEvent) {
        if (filePopup.isVisible) {
            when (e.keyCode) {
                KeyEvent.VK_ENTER -> {
                    val selectedFile = fileList.selectedValue
                    if (selectedFile != null) {
                        e.consume()
                        filePopup.isVisible = false
                        insertFileReference(selectedFile)
                        inputArea.requestFocusInWindow()
                    }
                }
                
                KeyEvent.VK_DOWN -> {
                    if (fileListModel.size() > 0) {
                        e.consume()
                        val currentIndex = fileList.selectedIndex
                        fileList.selectedIndex = if (currentIndex < fileListModel.size() - 1) {
                            currentIndex + 1
                        } else {
                            0
                        }
                        fileList.ensureIndexIsVisible(fileList.selectedIndex)
                    }
                }
                
                KeyEvent.VK_UP -> {
                    if (fileListModel.size() > 0) {
                        e.consume()
                        val currentIndex = fileList.selectedIndex
                        fileList.selectedIndex = if (currentIndex > 0) {
                            currentIndex - 1
                        } else {
                            fileListModel.size() - 1
                        }
                        fileList.ensureIndexIsVisible(fileList.selectedIndex)
                    }
                }
                
                KeyEvent.VK_ESCAPE -> {
                    e.consume()
                    filePopup.isVisible = false
                    inputArea.requestFocusInWindow()
                }
            }
        } else {
            when {
                e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown -> {
                    e.consume()
                    sendMessage()
                }
            }
        }
    }
    
    private fun checkForFileReference() {
        val text = inputArea.text
        val caretPos = inputArea.caretPosition
        
        var atPos = -1
        var endPos = caretPos
        
        // 查找 @ 符号
        if (text.isNotEmpty() && caretPos >= 0) {
            val searchStart = minOf(caretPos, text.length - 1)
            for (i in searchStart downTo 0) {
                if (i >= 0 && i < text.length && text[i] == '@') {
                    if (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '\n') {
                        atPos = i
                        break
                    }
                } else if (i >= 0 && i < text.length && (text[i].isWhitespace() || text[i] == '\n')) {
                    break
                }
            }
            
            if (atPos >= 0) {
                endPos = atPos + 1
                while (endPos < text.length && !text[endPos].isWhitespace() && text[endPos] != '\n') {
                    endPos++
                }
            }
        }
        
        if (atPos >= 0 && caretPos >= atPos && caretPos <= endPos) {
            val searchText = if (endPos > atPos + 1) {
                text.substring(atPos + 1, endPos).trim()
            } else {
                ""
            }
            
            // 搜索文件
            currentSearchJob?.cancel()
            currentSearchJob = GlobalScope.launch(Dispatchers.IO) {
                delay(100) // 防抖动
                
                if (!isActive) return@launch
                
                val files = fileSearchService.searchFiles(searchText)
                
                if (!isActive) return@launch
                
                SwingUtilities.invokeLater {
                    fileListModel.clear()
                    files.forEach { fileListModel.addElement(it) }
                    
                    if (fileListModel.size() > 0) {
                        fileList.selectedIndex = 0
                        fileList.ensureIndexIsVisible(0)
                        
                        try {
                            // 简化的位置计算
                            val rect = inputArea.modelToView(atPos)
                            filePopup.show(inputArea, rect.x, rect.y - filePopup.preferredSize.height - 5)
                        } catch (ex: Exception) {
                            logger.warning("Error showing file popup: ${ex.message}")
                        }
                    } else {
                        filePopup.isVisible = false
                    }
                }
            }
        } else {
            filePopup.isVisible = false
        }
    }
    
    private fun insertFileReference(filePath: String) {
        val text = inputArea.text
        val caretPos = inputArea.caretPosition
        
        // 找到 @ 符号位置
        var atPos = -1
        var endPos = caretPos
        
        if (caretPos > 0 && text.isNotEmpty()) {
            for (i in caretPos - 1 downTo 0) {
                if (i < text.length && text[i] == '@') {
                    atPos = i
                    break
                }
            }
        }
        
        if (atPos >= 0) {
            for (i in caretPos until text.length) {
                if (text[i].isWhitespace() || text[i] == '\n') {
                    break
                }
                endPos = i + 1
            }
            
            val beforeAt = if (atPos > 0) text.substring(0, atPos) else ""
            val afterEnd = if (endPos < text.length) text.substring(endPos) else ""
            val relativePath = projectService.getRelativePath(filePath)
            val newText = beforeAt + "@" + relativePath + " " + afterEnd
            
            inputArea.text = newText
            val newCaretPos = atPos + relativePath.length + 2
            inputArea.caretPosition = minOf(newCaretPos, newText.length)
        }
    }
    
    private fun sendMessage() {
        val message = inputArea.text.trim()
        if (message.isNotEmpty()) {
            onSendMessage(message)
            inputArea.text = ""
        }
    }
    
    private fun adjustInputAreaHeight() {
        val text = inputArea.text
        val lines = if (text.isEmpty()) 1 else {
            text.count { it == '\n' } + 1
        }
        
        val targetLines = lines.coerceIn(1, 10)
        inputArea.rows = targetLines
        
        val scrollPane = inputArea.parent.parent as? JScrollPane
        scrollPane?.let {
            val fontMetrics = inputArea.getFontMetrics(inputArea.font)
            val lineHeight = fontMetrics.height
            val textHeight = targetLines * lineHeight
            val insets = inputArea.insets
            val scrollPaneHeight = textHeight + insets.top + insets.bottom + 8
            
            it.preferredSize = Dimension(it.width, scrollPaneHeight)
            it.minimumSize = Dimension(100, scrollPaneHeight)
            it.revalidate()
        }
    }
    
    /**
     * 文件列表渲染器
     */
    private class FileListRenderer(
        private val projectService: ProjectService
    ) : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (component is JLabel && value is String) {
                val fileName = value.substringAfterLast('/')
                val dirPath = value.substringBeforeLast('/', "")
                
                component.text = if (dirPath.isNotEmpty()) {
                    "<html><b>$fileName</b> <font color='gray'>- $dirPath</font></html>"
                } else {
                    fileName
                }
                component.toolTipText = value
            }
            
            return component
        }
    }
}