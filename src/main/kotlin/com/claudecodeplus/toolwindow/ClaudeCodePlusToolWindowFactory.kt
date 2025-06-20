package com.claudecodeplus.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import javax.swing.JPanel
import javax.swing.JTextField
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.BorderFactory
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.Font
import java.awt.Cursor
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.SwingUtilities
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.Box
import javax.swing.BoxLayout
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.vfs.VirtualFileWrapper
import java.io.File
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.options.ShowSettingsUtil
import java.text.SimpleDateFormat
import java.util.Date
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.ui.components.JBList
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import javax.swing.DefaultListModel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import java.awt.Point
import javax.swing.SwingConstants
import javax.swing.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths
import javax.swing.Timer
import com.claudecodeplus.sdk.ClaudeAPIClient
import com.claudecodeplus.sdk.ClaudeOptions
import com.claudecodeplus.sdk.NodeResourceExtractor
import com.claudecodeplus.sdk.HealthStatus
import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import com.intellij.openapi.application.ApplicationManager
import kotlin.concurrent.thread
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.claudecodeplus.settings.McpConfigurable
import com.claudecodeplus.settings.McpSettingsService
import com.intellij.openapi.components.service

/**
 * Claude Code Plus 工具窗口工厂实现
 */
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    
    companion object {
        private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(ClaudeCodePlusToolWindowFactory::class.java)
        private var apiClient: ClaudeAPIClient? = null
        private var initJob: Job? = null
        
        @JvmStatic
        fun stopServices() {
            logger.info("Stopping Claude Code Plus services...")
            apiClient?.stop()
            apiClient = null
            initJob?.cancel()
            initJob = null
        }
        
        private fun initializeApiClient(project: Project) {
            if (apiClient != null) return
            
            initJob = GlobalScope.launch {
                try {
                    // 获取 Node 服务路径
                    val pluginPath = File(PathManager.getPluginsPath(), "claude-code-plus/claude-node")
                    val nodeServicePath = when {
                        // 生产环境：插件目录中的静态文件
                        pluginPath.exists() -> {
                            logger.info("Using Node service from plugin directory: $pluginPath")
                            pluginPath.absolutePath
                        }
                        // 开发环境
                        else -> {
                            // 尝试多个可能的开发路径
                            val possiblePaths = listOf(
                                // 开发时的沙箱路径
                                File(PathManager.getPluginsPath()).parentFile.resolve("claude-code-plus/claude-node"),
                                // 项目根目录下的资源
                                File(System.getProperty("user.dir"), "src/main/resources/claude-node"),
                                // 如果当前项目就是插件项目
                                File(project.basePath ?: "", "src/main/resources/claude-node"),
                                // 临时目录（作为后备方案）
                                File(System.getProperty("java.io.tmpdir"), "claude-code-plus-node")
                            )
                            
                            var foundPath: File? = null
                            for (path in possiblePaths) {
                                logger.info("Checking for Node service at: ${path.absolutePath}")
                                if (path.exists() && path.isDirectory && File(path, "start.js").exists()) {
                                    foundPath = path
                                    break
                                }
                            }
                            
                            if (foundPath != null) {
                                logger.info("Using Node service from development path: ${foundPath.absolutePath}")
                                foundPath.absolutePath
                            } else {
                                // 最后的尝试：从资源中提取
                                val tempDir = File(System.getProperty("java.io.tmpdir"), "claude-code-plus-node")
                                val extractor = NodeResourceExtractor()
                                if (extractor.extractServerResources(tempDir)) {
                                    logger.info("Extracted Node service to: ${tempDir.absolutePath}")
                                    tempDir.absolutePath
                                } else {
                                    throw RuntimeException("Failed to find or extract Node service files")
                                }
                            }
                        }
                    }
                    
                    logger.info("Initializing API client with Node service at: $nodeServicePath")
                    
                    // 创建并初始化客户端
                    val client = ClaudeAPIClient(nodeServicePath)
                    if (client.initialize()) {
                        apiClient = client
                        logger.info("API client initialized successfully")
                    } else {
                        logger.error("Failed to initialize API client")
                    }
                } catch (e: Exception) {
                    logger.error("Error initializing API client", e)
                }
            }
        }
    }
    
    private var shouldStartNewSession = false
    private var currentStreamJob: kotlinx.coroutines.Job? = null
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 初始化 API 客户端
        if (apiClient == null) {
            initializeApiClient(project)
        }
        
        val contentFactory = ContentFactory.getInstance()
        val panel = createChatPanel(project)
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // 添加标题栏按钮
        val titleActions = mutableListOf<AnAction>()
        val newChatAction = object : AnAction("+ New Chat", "开始新会话", null) {
            init {
                // 强制显示文字而不是图标
                templatePresentation.text = "+ New Chat"
                templatePresentation.icon = null
            }
            
            override fun actionPerformed(e: AnActionEvent) {
                // 清空对话内容
                val editor = panel.getClientProperty("editor") as? EditorEx
                val conversationContent = panel.getClientProperty("conversationContent") as? StringBuilder
                
                editor?.let { ed ->
                    conversationContent?.let { content ->
                        content.clear()
                        val projectPath = project.basePath ?: "Unknown"
                        val projectName = project.name
                        val welcomeMessage = """
欢迎使用 Claude Code Plus！

**当前项目**: $projectName  
**项目路径**: `$projectPath`

您可以：
- 输入消息与 Claude 对话
- 使用 `@` 引用项目中的文件
- 使用 `Shift+Enter` 换行，`Enter` 发送消息

---

""".trimIndent()
                        content.append(welcomeMessage)
                        updateEditorContent(ed, content.toString())
                        
                        // 设置标志，下次请求时开启新会话
                        shouldStartNewSession = true
                    }
                }
            }
        }
        titleActions.add(newChatAction)
        toolWindow.setTitleActions(titleActions)
        
        // 添加齿轮菜单项（设置）
        val gearActions = DefaultActionGroup()
        gearActions.add(object : AnAction("Settings", "打开 Claude Code Plus 设置", null) {
            override fun actionPerformed(e: AnActionEvent) {
                // 打开到 Tools > Claude Code Plus 设置组
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "com.claudecodeplus.settings")
            }
        })
        toolWindow.setAdditionalGearActions(gearActions)
        
        // 检查服务是否在运行
        thread {
            try {
                Thread.sleep(1000) // 等待界面初始化
                val isHealthy = kotlinx.coroutines.runBlocking { apiClient?.checkHealth() ?: false }
                if (isHealthy == false) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showWarningDialog(
                            project,
                            "无法连接到 Claude SDK 服务。请确保服务正在运行。\n\n" +
                            "手动启动方法：\n" +
                            "1. 进入目录: src/main/resources/claude-node\n" +
                            "2. 运行命令: node server-esm-wrapper.mjs --port 18080\n\n" +
                            "默认地址: http://127.0.0.1:18080",
                            "连接提示"
                        )
                    }
                }
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }
    
    
    private fun createChatPanel(project: Project): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 创建 Markdown 编辑器用于显示消息
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument("")
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("md")
        val editor = editorFactory.createEditor(document, project, fileType, true) as EditorEx
        
        // 设置编辑器为只读
        editor.isViewer = true
        editor.settings.isLineNumbersShown = false
        editor.settings.isLineMarkerAreaShown = false
        editor.settings.isFoldingOutlineShown = false
        editor.settings.isIndentGuidesShown = false
        editor.settings.isCaretRowShown = false
        editor.settings.isUseSoftWraps = true
        editor.settings.additionalLinesCount = 2
        
        // 设置编辑器边距
        editor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_RIGHT)
        editor.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15))
        
        // 存储对话内容的 StringBuilder
        val conversationContent = StringBuilder()
        
        // 创建工具栏
        val toolbar = createToolbar(project, editor, conversationContent)
        panel.add(toolbar, BorderLayout.NORTH)
        
        // 显示欢迎消息和项目路径
        val projectPath = project.basePath ?: "Unknown"
        val projectName = project.name
        val welcomeMessage = """
欢迎使用 Claude Code Plus！

**当前项目**: $projectName  
**项目路径**: `$projectPath`

您可以：
- 输入消息与 Claude 对话
- 使用 `@` 引用项目中的文件
- 使用 `Shift+Enter` 换行，`Enter` 发送消息

---

""".trimIndent()
        
        conversationContent.append(welcomeMessage)
        updateEditorContent(editor, conversationContent.toString())
        
        // 创建滚动面板
        val scrollPane = JBScrollPane(editor.component)
        scrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        
        // 创建输入区域
        val inputPanel = createInputPanel(project, editor, conversationContent)
        
        // 添加组件到面板
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(inputPanel, BorderLayout.SOUTH)
        
        // 存储引用以供工具窗口按钮使用
        panel.putClientProperty("editor", editor)
        panel.putClientProperty("conversationContent", conversationContent)
        
        return panel
    }
    
    private fun updateEditorContent(editor: EditorEx, content: String) {
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            editor.document.setReadOnly(false)
            editor.document.setText(content)
            editor.document.setReadOnly(true)
        }
    }
    
    private fun createToolbar(project: Project, editor: EditorEx, conversationContent: StringBuilder): JPanel {
        val toolbarPanel = JPanel()
        toolbarPanel.layout = BoxLayout(toolbarPanel, BoxLayout.X_AXIS)
        toolbarPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        // 清空按钮
        val clearBtn = JButton("清空")
        clearBtn.addActionListener {
            conversationContent.clear()
            updateEditorContent(editor, "")
        }
        
        // 日志按钮
        val logBtn = JButton("日志")
        logBtn.addActionListener {
            val logDir = File(System.getProperty("user.home"), ".claudecodeplus/logs")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd")
            val logFile = File(logDir, "session-${dateFormat.format(Date())}.log")
            
            Messages.showMessageDialog(
                project,
                "日志文件位置：\n${logFile.absolutePath}",
                "会话日志",
                Messages.getInformationIcon()
            )
        }
        
        // 导出按钮
        val exportBtn = JButton("导出")
        exportBtn.addActionListener {
            val descriptor = FileSaverDescriptor(
                "导出对话",
                "将对话导出为 Markdown 文件",
                "md"
            )
            
            val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd-HHmmss")
            val defaultName = "claude-chat-${dateFormat.format(Date())}.md"
            
            val saveResult = dialog.save(project.basePath?.let { Paths.get(it) }, defaultName)
            saveResult?.let { virtualFileWrapper ->
                try {
                    val file = virtualFileWrapper.file
                    file.writeText(conversationContent.toString())
                    Messages.showMessageDialog(
                        project,
                        "对话已导出到：\n${file.absolutePath}",
                        "导出成功",
                        Messages.getInformationIcon()
                    )
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "导出失败：${e.message}",
                        "错误"
                    )
                }
            }
        }
        
        // 添加按钮到工具栏
        toolbarPanel.add(clearBtn)
        toolbarPanel.add(Box.createHorizontalStrut(10))
        toolbarPanel.add(logBtn)
        toolbarPanel.add(Box.createHorizontalStrut(10))
        toolbarPanel.add(exportBtn)
        
        return toolbarPanel
    }
    
    private fun createInputPanel(project: Project, editor: EditorEx, conversationContent: StringBuilder): JPanel {
        val inputPanel = JPanel(BorderLayout())
        inputPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        // 创建输入容器（带圆角边框效果）
        val inputContainer = JPanel(BorderLayout())
        inputContainer.background = UIUtil.getPanelBackground()
        inputContainer.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1, true),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        )
        
        // 创建多行输入框
        val inputArea = javax.swing.JTextArea(3, 0)
        inputArea.font = Font("Dialog", Font.PLAIN, 14)
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.background = inputContainer.background
        inputArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        
        val inputScrollPane = JScrollPane(inputArea)
        inputScrollPane.border = null
        inputScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        inputScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        inputScrollPane.background = inputContainer.background
        
        // 文件引用自动补全
        val filePopup = JPopupMenu()
        val fileListModel = DefaultListModel<String>()
        val fileList = JBList(fileListModel)
        val fileListScrollPane = JScrollPane(fileList)
        fileListScrollPane.preferredSize = java.awt.Dimension(400, 200)
        filePopup.add(fileListScrollPane)
        
        // 监听输入内容变化
        inputArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                checkForFileReference(inputArea, project, filePopup, fileListModel, fileList)
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                filePopup.isVisible = false
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
        })
        
        // 自定义文件列表渲染器，显示文件图标和路径
        fileList.cellRenderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (component is JLabel && value is String) {
                    // 显示文件名和路径
                    val fileName = value.substringAfterLast('/')
                    val dirPath = value.substringBeforeLast('/', "")
                    component.text = if (dirPath.isNotEmpty()) {
                        "<html><b>$fileName</b> <font color='gray'>- $dirPath</font></html>"
                    } else {
                        fileName
                    }
                    component.toolTipText = value // 悬停显示完整路径
                    
                    // 设置文件图标
                    val file = findFile(project, value)
                    file?.let { vFile ->
                        val fileType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFile(vFile)
                        component.icon = fileType.icon
                    }
                }
                return component
            }
        }
        
        // 处理文件选择 - 双击或回车确认
        fileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedFile = fileList.selectedValue
                if (selectedFile != null) {
                    // 只在双击时插入，单击只是选中
                }
            }
        }
        
        // 双击插入文件引用
        fileList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedFile = fileList.selectedValue
                    if (selectedFile != null) {
                        insertFileReference(inputArea, selectedFile)
                        filePopup.isVisible = false
                    }
                }
            }
        })
        
        // 底部控制栏
        val controlBar = JPanel(BorderLayout())
        controlBar.background = inputContainer.background
        controlBar.preferredSize = java.awt.Dimension(0, 35)
        controlBar.border = BorderFactory.createEmptyBorder(8, 4, 4, 4)
        
        // 左侧：Add Context 按钮
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        leftPanel.isOpaque = false
        
        val addContextBtn = JButton("@ Add Context")
        addContextBtn.preferredSize = java.awt.Dimension(110, 28)
        addContextBtn.font = Font("Dialog", Font.PLAIN, 12)
        addContextBtn.cursor = Cursor(Cursor.HAND_CURSOR)
        addContextBtn.toolTipText = "添加文件引用 (@)"
        addContextBtn.isFocusPainted = false
        addContextBtn.putClientProperty("JButton.buttonType", "textured")
        addContextBtn.addActionListener {
            inputArea.insert("@", inputArea.caretPosition)
            inputArea.requestFocusInWindow()
            // 触发文件搜索
            checkForFileReference(inputArea, project, filePopup, fileListModel, fileList)
        }
        
        leftPanel.add(addContextBtn)
        
        // 右侧：模型选择器
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        rightPanel.isOpaque = false
        
        val agentIcon = JLabel("👤")
        agentIcon.font = Font("Dialog", Font.PLAIN, 14)
        
        val agentLabel = JLabel("Agent")
        agentLabel.foreground = UIUtil.getLabelDisabledForeground()
        agentLabel.font = Font("Dialog", Font.PLAIN, 11)
        
        val modelOptions = arrayOf(
            "claude-4-opus",
            "claude-4-sonnet"
        )
        
        val modelCombo = JComboBox(modelOptions)
        modelCombo.preferredSize = java.awt.Dimension(140, 28)
        modelCombo.font = Font("Dialog", Font.PLAIN, 12)
        modelCombo.selectedIndex = 0  // 默认选择 claude-4-opus
        modelCombo.toolTipText = "选择AI模型"
        modelCombo.putClientProperty("JComboBox.isTableCellEditor", true)
        
        // 存储模型选择器的引用，以便在事件处理器中访问
        inputArea.putClientProperty("modelCombo", modelCombo)
        
        rightPanel.add(agentIcon)
        rightPanel.add(agentLabel)
        rightPanel.add(modelCombo)
        
        // 中间：停止按钮（默认隐藏）
        val centerPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
        centerPanel.isOpaque = false
        
        val stopButton = JButton("🛑 停止生成")
        stopButton.preferredSize = java.awt.Dimension(100, 28)
        stopButton.font = Font("Dialog", Font.PLAIN, 12)
        stopButton.cursor = Cursor(Cursor.HAND_CURSOR)
        stopButton.toolTipText = "停止 AI 响应"
        stopButton.isFocusPainted = false
        stopButton.putClientProperty("JButton.buttonType", "textured")
        stopButton.isVisible = false  // 默认隐藏
        stopButton.addActionListener {
            // 取消当前的流式任务
            currentStreamJob?.cancel()
            
            // 同时调用服务端的 abort 接口
            GlobalScope.launch {
                try {
                    val aborted = apiClient?.abortCurrentRequest() ?: false
                    logger.info("Server abort result: $aborted")
                } catch (e: Exception) {
                    logger.error("Failed to abort server request", e)
                }
            }
            
            stopButton.isVisible = false
            inputArea.isEnabled = true
            inputArea.requestFocusInWindow()
        }
        
        // 存储停止按钮的引用
        inputArea.putClientProperty("stopButton", stopButton)
        
        centerPanel.add(stopButton)
        
        controlBar.add(leftPanel, BorderLayout.WEST)
        controlBar.add(centerPanel, BorderLayout.CENTER)
        controlBar.add(rightPanel, BorderLayout.EAST)
        
        // 处理快捷键
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown -> {
                        e.consume()
                        // 检查是否正在生成中（输入框被禁用）
                        if (!inputArea.isEnabled) {
                            // 显示提示信息
                            javax.swing.JOptionPane.showMessageDialog(
                                inputArea,
                                "AI 正在生成响应中...\n请使用 ESC 键或停止按钮来中断生成",
                                "提示",
                                javax.swing.JOptionPane.INFORMATION_MESSAGE
                            )
                            return
                        }
                        val message = inputArea.text.trim()
                        if (message.isNotEmpty()) {
                            val combo = inputArea.getClientProperty("modelCombo") as? JComboBox<*>
                            val selectedModel = when(combo?.selectedItem?.toString()) {
                                "claude-4-opus" -> "opus"
                                "claude-4-sonnet" -> "sonnet"
                                else -> "opus"  // 默认使用 opus
                            }
                            sendMessageWithModel(inputArea, selectedModel, editor, conversationContent, project)
                        }
                    }
                    e.keyCode == KeyEvent.VK_ESCAPE -> {
                        // 如果正在生成响应，ESC 键停止生成
                        if (!inputArea.isEnabled) {
                            currentStreamJob?.cancel()
                            
                            // 同时调用服务端的 abort 接口
                            GlobalScope.launch {
                                try {
                                    val aborted = apiClient?.abortCurrentRequest() ?: false
                                    logger.info("Server abort result: $aborted")
                                } catch (e: Exception) {
                                    logger.error("Failed to abort server request", e)
                                }
                            }
                            
                            inputArea.getClientProperty("stopButton")?.let { btn ->
                                if (btn is JButton) {
                                    btn.isVisible = false
                                }
                            }
                            inputArea.isEnabled = true
                            inputArea.requestFocusInWindow()
                        } else {
                            // 否则关闭文件弹出菜单
                            filePopup.isVisible = false
                        }
                    }
                    e.keyCode == KeyEvent.VK_DOWN && filePopup.isVisible -> {
                        e.consume()
                        val currentIndex = fileList.selectedIndex
                        if (currentIndex < fileListModel.size() - 1) {
                            fileList.selectedIndex = currentIndex + 1
                        }
                    }
                    e.keyCode == KeyEvent.VK_UP && filePopup.isVisible -> {
                        e.consume()
                        val currentIndex = fileList.selectedIndex
                        if (currentIndex > 0) {
                            fileList.selectedIndex = currentIndex - 1
                        }
                    }
                    e.keyCode == KeyEvent.VK_ENTER && filePopup.isVisible -> {
                        e.consume()
                        val selectedFile = fileList.selectedValue
                        if (selectedFile != null) {
                            insertFileReference(inputArea, selectedFile)
                            filePopup.isVisible = false
                        }
                    }
                }
            }
        })
        
        // 组装输入容器
        inputContainer.add(inputScrollPane, BorderLayout.CENTER)
        inputContainer.add(controlBar, BorderLayout.SOUTH)
        
        // 布局
        inputPanel.add(inputContainer, BorderLayout.CENTER)
        
        // 聚焦到输入框
        SwingUtilities.invokeLater {
            inputArea.requestFocusInWindow()
        }
        
        return inputPanel
    }
    
    private fun checkForFileReference(
        inputArea: javax.swing.JTextArea,
        project: Project,
        popup: JPopupMenu,
        listModel: DefaultListModel<String>,
        fileList: JBList<String>
    ) {
        val text = inputArea.text
        val caretPos = inputArea.caretPosition
        
        // 查找最近的 @ 符号
        var atPos = -1
        for (i in caretPos - 1 downTo 0) {
            if (text[i] == '@') {
                // 检查 @ 前面是否是空白或行首
                if (i == 0 || text[i - 1].isWhitespace()) {
                    // 检查 @ 后面是否紧跟着空格或者光标就在@后面
                    if (caretPos == i + 1 || (caretPos > i + 1 && !text[i + 1].isWhitespace())) {
                        atPos = i
                        break
                    }
                }
            } else if (text[i].isWhitespace() || text[i] == '\n') {
                break
            }
        }
        
        if (atPos >= 0) {
            val searchText = if (caretPos > atPos + 1) {
                text.substring(atPos + 1, caretPos).trim()
            } else {
                ""
            }
            
            // 始终搜索文件，即使搜索文本为空
            listModel.clear()
            val files = if (searchText.isEmpty()) {
                // 如果没有搜索文本，显示最近的文件或常用文件
                searchRecentFiles(project)
            } else {
                searchFiles(project, searchText)
            }
            files.forEach { listModel.addElement(it) }
            
            if (listModel.size() > 0) {
                fileList.selectedIndex = 0
                
                // 计算弹出位置
                try {
                    val rect = inputArea.modelToView(atPos)
                    popup.show(inputArea, rect.x, rect.y - popup.preferredSize.height - 5)
                } catch (ex: Exception) {
                    // 忽略位置计算错误
                }
            } else {
                popup.isVisible = false
            }
        } else {
            popup.isVisible = false
        }
    }
    
    private fun searchRecentFiles(project: Project): List<String> {
        val results = mutableListOf<String>()
        
        // 获取最近打开的文件
        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
            val editorManager = FileEditorManager.getInstance(project)
            val openFiles = editorManager.openFiles
            
            // 添加当前打开的文件
            openFiles.take(10).forEach { file ->
                val relativePath = getRelativePath(project, file)
                results.add(relativePath)
            }
            
            // 如果打开的文件不够，添加项目中的一些常用文件
            if (results.size < 10) {
                val scope = GlobalSearchScope.projectScope(project)
                val commonFiles = listOf("README.md", "build.gradle", "pom.xml", "package.json", "settings.gradle")
                
                commonFiles.forEach { fileName ->
                    if (results.size < 10) {
                        val files = FilenameIndex.getFilesByName(project, fileName, scope)
                        files.forEach { psiFile ->
                            psiFile.virtualFile?.let { vFile ->
                                val relativePath = getRelativePath(project, vFile)
                                if (!results.contains(relativePath)) {
                                    results.add(relativePath)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return results
    }
    
    private fun searchFiles(project: Project, searchText: String): List<String> {
        val results = mutableListOf<String>()
        val scope = GlobalSearchScope.projectScope(project)
        
        // 使用 IDEA 的原生搜索功能，支持搜索 Java 类、Kotlin 类和所有文件
        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
            val allFiles = mutableSetOf<VirtualFile>()
            
            // 1. 搜索所有项目文件，不限制扩展名
            // 这样可以搜索到 Java 类、Kotlin 类和其他所有文件类型
            
            // 2. 搜索文件名（使用 IDEA 的文件索引）
            FilenameIndex.getAllFilenames(project).forEach { filename ->
                if (filename.contains(searchText, ignoreCase = true)) {
                    FilenameIndex.getVirtualFilesByName(filename, scope).forEach { file ->
                        allFiles.add(file)
                    }
                }
            }
            
            // 3. 搜索路径中包含关键词的文件
            if (searchText.contains('/')) {
                FilenameIndex.getAllFilenames(project).forEach { filename ->
                    FilenameIndex.getVirtualFilesByName(filename, scope).forEach { file ->
                        val path = getRelativePath(project, file)
                        if (path.contains(searchText, ignoreCase = true)) {
                            allFiles.add(file)
                        }
                    }
                }
            }
            
            // 排序：精确匹配优先，然后按路径长度
            val sortedFiles = allFiles.sortedWith(compareBy(
                { !it.name.equals(searchText, ignoreCase = true) },
                { !it.name.startsWith(searchText, ignoreCase = true) },
                { !it.name.contains(searchText, ignoreCase = true) },
                { it.path.length }
            ))
            
            // 转换为相对路径
            sortedFiles.take(30).forEach { file ->
                val relativePath = getRelativePath(project, file)
                results.add(relativePath)
            }
        }
        
        return results
    }
    
    private fun getRelativePath(project: Project, file: VirtualFile): String {
        val projectPath = project.basePath ?: return file.path
        val filePath = file.path
        
        return if (filePath.startsWith(projectPath)) {
            filePath.substring(projectPath.length + 1)
        } else {
            filePath
        }
    }
    
    private fun formatUserMessage(content: String): String {
        // 使用 > 引用语法来创建背景容器效果
        // IntelliJ 的 Markdown 渲染器会为引用块添加背景色
        val lines = content.lines()
        return lines.joinToString("\n") { line ->
            if (line.isNotEmpty()) "> $line" else ">"
        }
    }
    
    private fun insertFileReference(inputArea: javax.swing.JTextArea, filePath: String) {
        val text = inputArea.text
        val caretPos = inputArea.caretPosition
        
        // 找到 @ 符号位置
        var atPos = -1
        var endPos = caretPos
        for (i in caretPos - 1 downTo 0) {
            if (text[i] == '@') {
                atPos = i
                break
            }
        }
        
        if (atPos >= 0) {
            // 找到当前词的结束位置（用于替换已输入的部分）
            for (i in caretPos until text.length) {
                if (text[i].isWhitespace() || text[i] == '\n') {
                    break
                }
                endPos = i + 1
            }
            
            // 替换从@到光标位置的文本
            val newText = text.substring(0, atPos) + "@" + filePath + " " + text.substring(endPos)
            inputArea.text = newText
            inputArea.caretPosition = atPos + filePath.length + 2
        }
    }
    
    private fun sendMessage(
        inputArea: javax.swing.JTextArea,
        editor: EditorEx,
        conversationContent: StringBuilder,
        project: Project
    ) {
        sendMessageWithModel(inputArea, "opus", editor, conversationContent, project)
    }
    
    private fun sendMessageWithModel(
        inputArea: javax.swing.JTextArea,
        model: String,
        editor: EditorEx,
        conversationContent: StringBuilder,
        project: Project
    ) {
        val input = inputArea.text.trim()
        if (input.isNotEmpty()) {
            // 禁用输入区域
            inputArea.isEnabled = false
            
            // 处理文件引用以显示
            val displayInput = processFileReferences(input, project)
            
            // 准备发送给 API 的消息（包含文件内容）
            val apiMessage = prepareMessageWithFileContents(input, project)
            
            // 添加用户消息到对话内容
            SwingUtilities.invokeLater {
                // 为用户消息添加背景容器的Markdown格式
                conversationContent.append(formatUserMessage(displayInput))
                conversationContent.append("\n\n")
                
                // 更新编辑器内容
                updateEditorContent(editor, conversationContent.toString())
                
                // 滚动到底部
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                        editor.caretModel.moveToOffset(editor.document.textLength)
                        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
                    }
                }
                
                // 清空输入框
                inputArea.text = ""
                
                // 发送消息到 Claude，包含模型参数
                sendToClaudeAPIWithModel(apiMessage, model, editor, conversationContent, project, inputArea)
            }
        }
    }
    
    private fun prepareMessageWithFileContents(input: String, project: Project): String {
        // 将 @文件引用 转换为超链接格式发送给 AI
        return processFileReferences(input, project)
    }
    
    private fun getMcpConfigForProject(project: Project): Map<String, Any>? {
        val mcpSettingsService = service<McpSettingsService>()
        val mergedConfig = mcpSettingsService.getMergedConfig(project)
        
        if (mergedConfig.isBlank() || mergedConfig == "{}") {
            return null
        }
        
        return try {
            // 解析 JSON 配置
            val json = org.json.JSONObject(mergedConfig)
            json.toMap()
        } catch (e: Exception) {
            null
        }
    }
    
    
    private fun sendToClaudeAPIWithModel(
        message: String,
        model: String,
        editor: EditorEx,
        conversationContent: StringBuilder,
        project: Project,
        inputArea: javax.swing.JTextArea
    ) {
        // 添加 Claude 正在输入的指示
        SwingUtilities.invokeLater {
            conversationContent.append("_Generating..._")
            conversationContent.append("\n\n")
            updateEditorContent(editor, conversationContent.toString())
            scrollToBottom(editor)
            
            // 显示停止按钮（需要在输入区域实现）
            inputArea.getClientProperty("stopButton")?.let { btn ->
                if (btn is JButton) {
                    btn.isVisible = true
                    btn.isEnabled = true
                }
            }
        }
        
        // 取消之前的任务（如果有）
        currentStreamJob?.cancel()
        
        // 使用协程发送请求
        currentStreamJob = GlobalScope.launch {
            try {
                // 检查服务状态
                val healthStatus = apiClient?.checkHealth() ?: HealthStatus(false, false, 0)
                if (!healthStatus.isHealthy) {
                    SwingUtilities.invokeLater {
                        // 移除"Generating..."
                        val currentContent = conversationContent.toString()
                        val generatingIndex = currentContent.lastIndexOf("_Generating..._")
                        if (generatingIndex >= 0) {
                            conversationContent.setLength(generatingIndex)
                        }
                        
                        conversationContent.append("> ❌ **错误**: Claude SDK 服务未就绪\n\n")
                        updateEditorContent(editor, conversationContent.toString())
                        scrollToBottom(editor)
                        
                        Messages.showErrorDialog(
                            project,
                            "Claude SDK 服务未就绪，请检查服务是否已启动",
                            "服务错误"
                        )
                        
                        // 重新启用输入框
                        inputArea.isEnabled = true
                        inputArea.requestFocusInWindow()
                        
                        // 隐藏停止按钮
                        inputArea.getClientProperty("stopButton")?.let { btn ->
                            if (btn is JButton) {
                                btn.isVisible = false
                            }
                        }
                    }
                    return@launch
                }
                
                if (healthStatus.isProcessing) {
                    SwingUtilities.invokeLater {
                        // 移除"Generating..."
                        val currentContent = conversationContent.toString()
                        val generatingIndex = currentContent.lastIndexOf("_Generating..._")
                        if (generatingIndex >= 0) {
                            conversationContent.setLength(generatingIndex)
                        }
                        
                        conversationContent.append("> ⚠️ **提示**: 服务正在处理其他请求，请稍后再试或中断当前请求\n\n")
                        updateEditorContent(editor, conversationContent.toString())
                        scrollToBottom(editor)
                        
                        Messages.showWarningDialog(
                            project,
                            "Claude SDK 服务正在处理其他请求\n请稍后再试或使用停止按钮中断当前请求",
                            "服务繁忙"
                        )
                        
                        // 重新启用输入框
                        inputArea.isEnabled = true
                        inputArea.requestFocusInWindow()
                        
                        // 隐藏停止按钮
                        inputArea.getClientProperty("stopButton")?.let { btn ->
                            if (btn is JButton) {
                                btn.isVisible = false
                            }
                        }
                    }
                    return@launch
                }
                
                val responseBuilder = StringBuilder()
                var hasContent = false
                
                // 获取 MCP 配置
                val mcpConfig = getMcpConfigForProject(project)
                
                // 准备选项（使用 ClaudeOptions 数据类）
                val options = ClaudeOptions(
                    model = when (model) {
                        "sonnet" -> "claude-3-5-sonnet-20241022"
                        "opus" -> "claude-3-opus-20240229"
                        else -> "claude-3-5-sonnet-20241022"
                    },
                    mcp = mcpConfig
                )
                
                // 使用流式 API
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        apiClient?.streamMessage(message, options) { chunk ->
                            // 处理每个数据块
                            responseBuilder.append(chunk)
                            hasContent = true
                            
                            SwingUtilities.invokeLater {
                                val currentContent = conversationContent.toString()
                                val generatingIndex = currentContent.lastIndexOf("_Generating..._")
                                if (generatingIndex >= 0) {
                                    conversationContent.setLength(generatingIndex)
                                    conversationContent.append(responseBuilder.toString())
                                    conversationContent.append("_")
                                    updateEditorContent(editor, conversationContent.toString())
                                    scrollToBottom(editor)
                                }
                            }
                        }?.collect()
                        
                        // 流完成后的处理
                        SwingUtilities.invokeLater {
                            val currentContent = conversationContent.toString()
                            val generatingIndex = currentContent.lastIndexOf("_Generating..._")
                            if (generatingIndex >= 0) {
                                conversationContent.setLength(generatingIndex)
                                conversationContent.append(responseBuilder.toString())
                                conversationContent.append("\n\n")
                                updateEditorContent(editor, conversationContent.toString())
                                scrollToBottom(editor)
                            }
                            
                            // 重新启用输入
                            inputArea.isEnabled = true
                            inputArea.requestFocusInWindow()
                            
                            // 隐藏停止按钮
                            inputArea.getClientProperty("stopButton")?.let { btn ->
                                if (btn is JButton) {
                                    btn.isVisible = false
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            val currentContent = conversationContent.toString()
                            val generatingIndex = currentContent.lastIndexOf("_Generating..._")
                            if (generatingIndex >= 0) {
                                conversationContent.setLength(generatingIndex)
                                if (responseBuilder.isNotEmpty()) {
                                    conversationContent.append(responseBuilder.toString())
                                }
                                
                                if (e is CancellationException) {
                                    conversationContent.append("\n\n*（已停止生成）*\n\n")
                                } else {
                                    conversationContent.append("\n\n> ❌ **错误**: ${e.message}\n\n")
                                }
                                
                                updateEditorContent(editor, conversationContent.toString())
                                scrollToBottom(editor)
                            }
                            
                            // 重新启用输入框
                            inputArea.isEnabled = true
                            inputArea.requestFocusInWindow()
                            
                            // 隐藏停止按钮
                            inputArea.getClientProperty("stopButton")?.let { btn ->
                                if (btn is JButton) {
                                    btn.isVisible = false
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 处理外层异常
                SwingUtilities.invokeLater {
                    val currentContent = conversationContent.toString()
                    val generatingIndex = currentContent.lastIndexOf("_Generating..._")
                    if (generatingIndex >= 0) {
                        conversationContent.setLength(generatingIndex)
                        conversationContent.append("> ❌ **错误**: ${e.message}\n\n")
                        updateEditorContent(editor, conversationContent.toString())
                        scrollToBottom(editor)
                    }
                    
                    // 重新启用输入框
                    inputArea.isEnabled = true
                    inputArea.requestFocusInWindow()
                    
                    // 隐藏停止按钮
                    inputArea.getClientProperty("stopButton")?.let { btn ->
                        if (btn is JButton) {
                            btn.isVisible = false
                        }
                    }
                }
            }
        }
    }
    
    private fun scrollToBottom(editor: EditorEx) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                editor.caretModel.moveToOffset(editor.document.textLength)
                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
            }
        }
    }
    
    private fun processFileReferences(input: String, project: Project): String {
        // 查找所有 @文件路径 格式的引用，转换为富文本超链接格式
        val pattern = "@([^\\s]+)".toRegex()
        var result = input
        
        pattern.findAll(input).forEach { matchResult ->
            val filePath = matchResult.groupValues[1]
            val file = findFile(project, filePath)
            
            if (file != null) {
                val relativePath = getRelativePath(project, file)
                // 将文件引用转换为 Markdown 链接格式
                val link = "[@$relativePath](file://${file.path})"
                result = result.replace(matchResult.value, link)
            }
            // 如果找不到文件，保持原样
        }
        
        return result
    }
    
    private fun findFile(project: Project, filePath: String): VirtualFile? {
        val projectPath = project.basePath ?: return null
        
        // 尝试作为相对路径
        var file = LocalFileSystem.getInstance().findFileByPath("$projectPath/$filePath")
        
        // 尝试作为绝对路径
        if (file == null) {
            file = LocalFileSystem.getInstance().findFileByPath(filePath)
        }
        
        // 尝试在项目中搜索文件名
        if (file == null) {
            com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                val files = FilenameIndex.getFilesByName(project, filePath, GlobalSearchScope.projectScope(project))
                if (files.isNotEmpty()) {
                    file = files[0].virtualFile
                }
            }
        }
        
        return file
    }
}