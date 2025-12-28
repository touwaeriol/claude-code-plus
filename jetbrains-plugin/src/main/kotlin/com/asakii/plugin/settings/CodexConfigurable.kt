package com.asakii.plugin.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.io.File
import javax.swing.*

/**
 * Codex 设置界面
 *
 * 提供 Codex 后端的配置选项，包括：
 * - Codex 二进制文件路径
 * - 自动检测二进制文件
 * - 模型提供者选择
 * - 默认沙箱模式
 * - 测试连接功能
 */
class CodexConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private lateinit var binaryPathField: TextFieldWithBrowseButton
    private lateinit var autoDetectButton: JButton
    private lateinit var modelProviderComboBox: ComboBox<ModelProvider>
    private lateinit var sandboxModeComboBox: ComboBox<SandboxMode>
    private lateinit var testConnectionButton: JButton
    private lateinit var statusLabel: JLabel

    // 模型提供者枚举
    enum class ModelProvider(val displayName: String) {
        OPENAI("OpenAI"),
        OLLAMA("Ollama"),
        ANTHROPIC("Anthropic"),
        CUSTOM("Custom")
    }

    // 沙箱模式枚举
    enum class SandboxMode(val displayName: String, val description: String) {
        READ_ONLY("ReadOnly", "只读模式 - 仅允许读取文件"),
        WORKSPACE_WRITE("WorkspaceWrite", "工作区写入 - 允许在工作区内修改文件"),
        FULL_ACCESS("FullAccess", "完全访问 - 允许所有文件操作")
    }

    override fun getDisplayName(): String = "Codex Backend"

    override fun createComponent(): JComponent {
        // 初始化组件
        initializeComponents()

        // 构建表单
        val formBuilder = FormBuilder.createFormBuilder()
            .setFormLeftIndent(10)
            .addLabeledComponent(
                JBLabel("Codex 二进制路径:"),
                createBinaryPathPanel(),
                1,
                false
            )
            .addComponent(createAutoDetectPanel(), 1)
            .addSeparator(5)
            .addLabeledComponent(
                JBLabel("模型提供者:"),
                modelProviderComboBox,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel("默认沙箱模式:"),
                createSandboxModePanel(),
                1,
                false
            )
            .addSeparator(5)
            .addComponent(createTestConnectionPanel(), 1)
            .addComponentFillVertically(JPanel(), 0)

        mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(formBuilder.panel, BorderLayout.NORTH)
        }

        return mainPanel!!
    }

    /**
     * 初始化所有 UI 组件
     */
    private fun initializeComponents() {
        // Codex 二进制路径字段
        binaryPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "选择 Codex 二进制文件",
                "请选择 Codex 可执行文件",
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        }

        // 自动检测按钮
        autoDetectButton = JButton("自动检测").apply {
            addActionListener { autoDetectCodexBinary() }
        }

        // 模型提供者下拉框
        modelProviderComboBox = ComboBox(ModelProvider.values()).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ModelProvider) {
                        text = value.displayName
                    }
                    return component
                }
            }
        }

        // 沙箱模式下拉框
        sandboxModeComboBox = ComboBox(SandboxMode.values()).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is SandboxMode) {
                        text = value.displayName
                    }
                    return component
                }
            }
        }

        // 测试连接按钮
        testConnectionButton = JButton("测试连接").apply {
            addActionListener { testCodexConnection() }
        }

        // 状态标签
        statusLabel = JLabel(" ").apply {
            foreground = java.awt.Color.GRAY
        }
    }

    /**
     * 创建二进制路径面板（包含路径字段和浏览按钮）
     */
    private fun createBinaryPathPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(binaryPathField, BorderLayout.CENTER)
        }
    }

    /**
     * 创建自动检测面板
     */
    private fun createAutoDetectPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(autoDetectButton, BorderLayout.WEST)
            border = JBUI.Borders.emptyLeft(10)
        }
    }

    /**
     * 创建沙箱模式面板（包含下拉框和说明）
     */
    private fun createSandboxModePanel(): JPanel {
        val descriptionLabel = JBLabel().apply {
            foreground = java.awt.Color.GRAY
            font = font.deriveFont(font.size - 1f)
        }

        // 监听下拉框选择变化，更新说明文本
        sandboxModeComboBox.addActionListener {
            val selectedMode = sandboxModeComboBox.selectedItem as? SandboxMode
            descriptionLabel.text = selectedMode?.description ?: ""
        }

        // 初始化说明文本
        val initialMode = sandboxModeComboBox.selectedItem as? SandboxMode
        descriptionLabel.text = initialMode?.description ?: ""

        return JPanel(BorderLayout()).apply {
            add(sandboxModeComboBox, BorderLayout.NORTH)
            add(descriptionLabel, BorderLayout.SOUTH)
        }
    }

    /**
     * 创建测试连接面板
     */
    private fun createTestConnectionPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            val buttonPanel = JPanel(BorderLayout()).apply {
                add(testConnectionButton, BorderLayout.WEST)
            }
            add(buttonPanel, BorderLayout.NORTH)
            add(statusLabel, BorderLayout.SOUTH)
            border = JBUI.Borders.emptyTop(5)
        }
    }

    /**
     * 自动检测 Codex 二进制文件
     */
    private fun autoDetectCodexBinary() {
        statusLabel.text = "正在检测 Codex 二进制文件..."
        statusLabel.foreground = java.awt.Color.BLUE

        // 在后台线程中执行检测
        SwingUtilities.invokeLater {
            val detectedPath = detectCodexBinary()
            if (detectedPath != null) {
                binaryPathField.text = detectedPath
                statusLabel.text = "✓ 已自动检测到 Codex: $detectedPath"
                statusLabel.foreground = java.awt.Color(0, 128, 0)
            } else {
                statusLabel.text = "✗ 未检测到 Codex 二进制文件，请手动选择"
                statusLabel.foreground = java.awt.Color.RED
            }
        }
    }

    /**
     * 检测 Codex 二进制文件路径
     *
     * @return 检测到的路径，如果未检测到则返回 null
     */
    private fun detectCodexBinary(): String? {
        val osName = System.getProperty("os.name").lowercase()
        val possiblePaths = when {
            osName.contains("windows") -> listOf(
                "C:\\Program Files\\Codex\\codex.exe",
                "C:\\Program Files (x86)\\Codex\\codex.exe",
                "${System.getenv("LOCALAPPDATA")}\\Codex\\codex.exe",
                "${System.getenv("USERPROFILE")}\\.codex\\codex.exe"
            )
            osName.contains("mac") -> listOf(
                "/usr/local/bin/codex",
                "/opt/homebrew/bin/codex",
                "${System.getProperty("user.home")}/.codex/codex",
                "/Applications/Codex.app/Contents/MacOS/codex"
            )
            else -> listOf( // Linux
                "/usr/local/bin/codex",
                "/usr/bin/codex",
                "${System.getProperty("user.home")}/.local/bin/codex",
                "${System.getProperty("user.home")}/.codex/codex"
            )
        }

        // 检查可能的路径
        return possiblePaths.firstOrNull { path ->
            val file = File(path)
            file.exists() && file.canExecute()
        }
    }

    /**
     * 测试 Codex 连接
     */
    private fun testCodexConnection() {
        val binaryPath = binaryPathField.text.trim()

        if (binaryPath.isEmpty()) {
            statusLabel.text = "✗ 请先指定 Codex 二进制文件路径"
            statusLabel.foreground = java.awt.Color.RED
            return
        }

        val file = File(binaryPath)
        if (!file.exists()) {
            statusLabel.text = "✗ 文件不存在: $binaryPath"
            statusLabel.foreground = java.awt.Color.RED
            return
        }

        if (!file.canExecute()) {
            statusLabel.text = "✗ 文件不可执行: $binaryPath"
            statusLabel.foreground = java.awt.Color.RED
            return
        }

        statusLabel.text = "正在测试连接..."
        statusLabel.foreground = java.awt.Color.BLUE

        // 在后台线程中执行测试
        SwingUtilities.invokeLater {
            try {
                val process = ProcessBuilder(binaryPath, "--version")
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    statusLabel.text = "✓ 连接成功: ${output.trim()}"
                    statusLabel.foreground = java.awt.Color(0, 128, 0)
                } else {
                    statusLabel.text = "✗ 连接失败 (退出码: $exitCode): ${output.trim()}"
                    statusLabel.foreground = java.awt.Color.RED
                }
            } catch (e: Exception) {
                statusLabel.text = "✗ 测试失败: ${e.message}"
                statusLabel.foreground = java.awt.Color.RED
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = CodexSettings.getInstance(project)

        val currentBinaryPath = binaryPathField.text.trim()
        val currentModelProvider = modelProviderComboBox.selectedItem as? ModelProvider
        val currentSandboxMode = sandboxModeComboBox.selectedItem as? SandboxMode

        return currentBinaryPath != settings.binaryPath ||
                currentModelProvider != settings.getModelProviderEnum() ||
                currentSandboxMode != settings.getSandboxModeEnum()
    }

    override fun apply() {
        val settings = CodexSettings.getInstance(project)
        settings.binaryPath = binaryPathField.text.trim()

        val modelProvider = modelProviderComboBox.selectedItem as? ModelProvider
        if (modelProvider != null) {
            settings.setModelProvider(modelProvider)
        }

        val sandboxMode = sandboxModeComboBox.selectedItem as? SandboxMode
        if (sandboxMode != null) {
            settings.setSandboxMode(sandboxMode)
        }

        // 应用设置后清除状态信息
        statusLabel.text = " "
        statusLabel.foreground = java.awt.Color.GRAY
    }

    override fun reset() {
        val settings = CodexSettings.getInstance(project)
        binaryPathField.text = settings.binaryPath
        modelProviderComboBox.selectedItem = settings.getModelProviderEnum()
        sandboxModeComboBox.selectedItem = settings.getSandboxModeEnum()
        statusLabel.text = " "
        statusLabel.foreground = java.awt.Color.GRAY
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}
