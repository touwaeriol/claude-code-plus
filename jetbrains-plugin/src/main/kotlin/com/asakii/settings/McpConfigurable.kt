package com.asakii.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * MCP 配置页面 - 列表形式
 *
 * 使用类似 JetBrains 原生 MCP 配置页面的设计：
 * - 列表展示所有 MCP 服务器（内置 + 自定义）
 * - 双击编辑配置
 * - 在对话框中配置服务器级别（Global/Project）
 *
 * @see docs/MCP_CONFIGURABLE_DESIGN.md
 */
class McpConfigurable(private val project: Project? = null) : SearchableConfigurable {

    private var mainPanel: JPanel? = null
    private var table: JBTable? = null
    private var tableModel: McpServerTableModel? = null

    // 内置服务器配置存储
    private val builtInServers = mutableListOf<McpServerEntry>()
    // 自定义服务器配置存储
    private val customServers = mutableListOf<McpServerEntry>()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override fun getId(): String = "com.asakii.settings.mcp"

    override fun getDisplayName(): String = "MCP"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        // 顶部区域（说明 + 通知）
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 0, 10, 0)
        }

        // 说明
        val descPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            val label = JBLabel("""
                <html>
                Configure MCP (Model Context Protocol) servers.
                <a href="https://modelcontextprotocol.io">Learn more</a>
                </html>
            """.trimIndent())
            add(label, BorderLayout.WEST)
        }
        topPanel.add(descPanel)
        topPanel.add(Box.createVerticalStrut(8))

        // 通知：仅对插件生效
        val noticePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            val noticeLabel = JBLabel("<html><font color='gray'>${ClaudeCodePlusBundle.message("mcp.settings.notice")}</font></html>")
            add(noticeLabel)
        }
        topPanel.add(noticePanel)

        mainPanel!!.add(topPanel, BorderLayout.NORTH)

        // 创建表格
        tableModel = McpServerTableModel()
        table = JBTable(tableModel).apply {
            setShowGrid(false)
            intercellSpacing = Dimension(0, 0)
            rowHeight = 28
            tableHeader.reorderingAllowed = false

            // Status 列渲染器（显示启用状态）
            columnModel.getColumn(0).apply {
                preferredWidth = 50
                maxWidth = 60
                cellRenderer = StatusCellRenderer()
            }

            // Name 列
            columnModel.getColumn(1).apply {
                preferredWidth = 180
            }

            // Configuration 列
            columnModel.getColumn(2).apply {
                preferredWidth = 350
                cellRenderer = ConfigurationCellRenderer()
            }

            // Level 列
            columnModel.getColumn(3).apply {
                preferredWidth = 80
                maxWidth = 100
                cellRenderer = LevelCellRenderer()
            }

            // 双击编辑
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val row = rowAtPoint(e.point)
                        if (row >= 0) {
                            editServer(row)
                        }
                    }
                }
            })
        }

        // 工具栏装饰器
        val decorator = ToolbarDecorator.createDecorator(table!!)
            .setToolbarPosition(ActionToolbarPosition.TOP)
            .setAddAction { addServer() }
            .setRemoveAction { removeServer() }
            .setEditAction { editServer(table!!.selectedRow) }
            .setEditActionUpdater {
                val row = table?.selectedRow ?: -1
                row >= 0 && !isBuiltInServer(row)
            }
            .setRemoveActionUpdater {
                val row = table?.selectedRow ?: -1
                row >= 0 && !isBuiltInServer(row)
            }

        val tablePanel = decorator.createPanel()
        mainPanel!!.add(tablePanel, BorderLayout.CENTER)

        // 底部提示
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(10)
            val warningLabel = JBLabel(
                "<html><font color='#B07800'>⚠ Proceed with caution and only connect to trusted servers.</font></html>"
            )
            add(warningLabel, BorderLayout.WEST)
        }
        mainPanel!!.add(bottomPanel, BorderLayout.SOUTH)

        // 加载配置
        reset()

        return mainPanel!!
    }

    /**
     * 判断是否为内置服务器
     */
    private fun isBuiltInServer(row: Int): Boolean {
        return row < builtInServers.size
    }

    /**
     * 添加新服务器
     */
    private fun addServer() {
        val dialog = McpServerDialog(project, null)
        if (dialog.showAndGet()) {
            val entry = dialog.getServerEntry()
            customServers.add(entry)
            refreshTable()
        }
    }

    /**
     * 编辑服务器
     */
    private fun editServer(row: Int) {
        if (row < 0) return

        if (row < builtInServers.size) {
            // 编辑内置服务器
            val entry = builtInServers[row]
            val dialog = BuiltInMcpServerDialog(project, entry)
            if (dialog.showAndGet()) {
                builtInServers[row] = dialog.getServerEntry()
                refreshTable()
            }
        } else {
            // 编辑自定义服务器
            val customIndex = row - builtInServers.size
            val entry = customServers[customIndex]
            val dialog = McpServerDialog(project, entry)
            if (dialog.showAndGet()) {
                customServers[customIndex] = dialog.getServerEntry()
                refreshTable()
            }
        }
    }

    /**
     * 删除服务器
     */
    private fun removeServer() {
        val row = table?.selectedRow ?: return
        if (row < 0 || row < builtInServers.size) return

        val customIndex = row - builtInServers.size
        val entry = customServers[customIndex]

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove '${entry.name}'?",
            "Remove MCP Server",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            customServers.removeAt(customIndex)
            refreshTable()
        }
    }

    /**
     * 刷新表格
     */
    private fun refreshTable() {
        tableModel?.fireTableDataChanged()
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()

        // 检查内置服务器配置
        val userInteractionEntry = builtInServers.find { it.name == "User Interaction MCP" }
        val jetbrainsEntry = builtInServers.find { it.name == "JetBrains IDE MCP" }
        val context7Entry = builtInServers.find { it.name == "Context7 MCP" }
        val terminalEntry = builtInServers.find { it.name == "Terminal MCP" }
        val gitEntry = builtInServers.find { it.name == "JetBrains Git MCP" }

        if (userInteractionEntry?.enabled != settings.enableUserInteractionMcp ||
            jetbrainsEntry?.enabled != settings.enableJetBrainsMcp ||
            context7Entry?.enabled != settings.enableContext7Mcp ||
            terminalEntry?.enabled != settings.enableTerminalMcp ||
            gitEntry?.enabled != settings.enableGitMcp ||
            context7Entry?.apiKey != settings.context7ApiKey ||
            userInteractionEntry?.instructions != settings.userInteractionInstructions ||
            jetbrainsEntry?.instructions != settings.jetbrainsInstructions ||
            context7Entry?.instructions != settings.context7Instructions ||
            terminalEntry?.instructions != settings.terminalInstructions ||
            gitEntry?.instructions != settings.gitInstructions ||
            terminalEntry?.terminalMaxOutputLines != settings.terminalMaxOutputLines ||
            terminalEntry?.terminalMaxOutputChars != settings.terminalMaxOutputChars ||
            terminalEntry?.terminalReadTimeout != settings.terminalReadTimeout
        ) {
            return true
        }

        // 检查自定义服务器配置
        val savedGlobalServers = parseCustomServers(mcpSettings.getGlobalConfig(), McpServerLevel.GLOBAL)
        val savedProjectServers = parseCustomServers(mcpSettings.getProjectConfig(project), McpServerLevel.PROJECT)
        val savedCustomServers = savedGlobalServers + savedProjectServers

        if (customServers.size != savedCustomServers.size) return true

        // 比较每个自定义服务器
        for (server in customServers) {
            val saved = savedCustomServers.find { it.name == server.name && it.level == server.level }
            if (saved == null ||
                saved.jsonConfig != server.jsonConfig ||
                saved.enabled != server.enabled ||
                saved.instructions != server.instructions
            ) {
                return true
            }
        }

        return false
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()

        // 保存内置服务器配置
        val userInteractionEntry = builtInServers.find { it.name == "User Interaction MCP" }
        val jetbrainsEntry = builtInServers.find { it.name == "JetBrains IDE MCP" }
        val context7Entry = builtInServers.find { it.name == "Context7 MCP" }
        val terminalEntry = builtInServers.find { it.name == "Terminal MCP" }
        val gitEntry = builtInServers.find { it.name == "JetBrains Git MCP" }

        settings.enableUserInteractionMcp = userInteractionEntry?.enabled ?: true
        settings.enableJetBrainsMcp = jetbrainsEntry?.enabled ?: true
        settings.enableContext7Mcp = context7Entry?.enabled ?: false
        settings.enableGitMcp = gitEntry?.enabled ?: false
        settings.gitInstructions = gitEntry?.instructions ?: ""
        settings.enableTerminalMcp = terminalEntry?.enabled ?: false
        settings.context7ApiKey = context7Entry?.apiKey ?: ""
        settings.userInteractionInstructions = userInteractionEntry?.instructions ?: ""
        settings.jetbrainsInstructions = jetbrainsEntry?.instructions ?: ""
        settings.context7Instructions = context7Entry?.instructions ?: ""
        settings.terminalInstructions = terminalEntry?.instructions ?: ""
        settings.terminalMaxOutputLines = terminalEntry?.terminalMaxOutputLines ?: 500
        settings.terminalMaxOutputChars = terminalEntry?.terminalMaxOutputChars ?: 50000
        settings.terminalDefaultShell = terminalEntry?.terminalDefaultShell ?: ""
        settings.terminalAvailableShells = terminalEntry?.terminalAvailableShells ?: ""
        settings.terminalReadTimeout = terminalEntry?.terminalReadTimeout ?: 30

        // 保存自定义服务器配置
        val globalServers = customServers.filter { it.level == McpServerLevel.GLOBAL }
        val projectServers = customServers.filter { it.level == McpServerLevel.PROJECT }

        mcpSettings.setGlobalConfig(buildMcpServersJson(globalServers))
        mcpSettings.setProjectConfig(project, buildMcpServersJson(projectServers))

        // 通知监听器
        settings.notifyChange()
    }

    /**
     * 构建 MCP 服务器 JSON 配置
     *
     * 存储格式：
     * {
     *   "server-name": {
     *     "config": { "command": "...", "args": [...] },  // 纯净的 MCP 配置
     *     "enabled": true,                                 // 我们的元数据
     *     "instructions": "..."                            // 我们的元数据
     *   }
     * }
     */
    private fun buildMcpServersJson(servers: List<McpServerEntry>): String {
        if (servers.isEmpty()) return ""

        val serversMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for (server in servers) {
            try {
                val parsed = json.parseToJsonElement(server.jsonConfig).jsonObject
                // 直接从顶层读取服务器配置
                for ((serverName, serverConfig) in parsed) {
                    // 构建包含 config + 元数据的完整条目
                    val entryMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                    entryMap["config"] = serverConfig  // 纯净的 MCP 配置
                    entryMap["enabled"] = kotlinx.serialization.json.JsonPrimitive(server.enabled)
                    if (server.instructions.isNotBlank()) {
                        entryMap["instructions"] = kotlinx.serialization.json.JsonPrimitive(server.instructions)
                    }
                    serversMap[serverName] = JsonObject(entryMap)
                }
            } catch (_: Exception) {
                // 忽略解析错误
            }
        }

        if (serversMap.isEmpty()) return ""

        return json.encodeToString(JsonObject.serializer(), JsonObject(serversMap))
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()

        // 加载内置服务器
        builtInServers.clear()
        builtInServers.add(McpServerEntry(
            name = "User Interaction MCP",
            enabled = settings.enableUserInteractionMcp,
            level = McpServerLevel.BUILTIN,
            configSummary = "Allows Claude to ask questions",
            isBuiltIn = true,
            instructions = settings.userInteractionInstructions,
            defaultInstructions = McpDefaults.USER_INTERACTION_INSTRUCTIONS
        ))
        builtInServers.add(McpServerEntry(
            name = "JetBrains IDE MCP",
            enabled = settings.enableJetBrainsMcp,
            level = McpServerLevel.BUILTIN,
            configSummary = "Code search, file indexing",
            isBuiltIn = true,
            instructions = settings.jetbrainsInstructions,
            defaultInstructions = McpDefaults.JETBRAINS_INSTRUCTIONS,
            disabledTools = listOf("Glob", "Grep")
        ))
        builtInServers.add(McpServerEntry(
            name = "Context7 MCP",
            enabled = settings.enableContext7Mcp,
            level = McpServerLevel.BUILTIN,
            configSummary = "Library documentation",
            isBuiltIn = true,
            instructions = settings.context7Instructions,
            apiKey = settings.context7ApiKey,
            defaultInstructions = McpDefaults.CONTEXT7_INSTRUCTIONS
        ))
        builtInServers.add(McpServerEntry(
            name = "Terminal MCP",
            enabled = settings.enableTerminalMcp,
            level = McpServerLevel.BUILTIN,
            configSummary = "IDEA integrated terminal",
            isBuiltIn = true,
            instructions = settings.terminalInstructions,
            defaultInstructions = McpDefaults.TERMINAL_INSTRUCTIONS,
            disabledTools = if (settings.terminalDisableBuiltinBash) listOf("Bash") else emptyList(),
            hasDisableToolsToggle = true,
            terminalMaxOutputLines = settings.terminalMaxOutputLines,
            terminalMaxOutputChars = settings.terminalMaxOutputChars,
            terminalDefaultShell = settings.terminalDefaultShell,
            terminalAvailableShells = settings.terminalAvailableShells,
            terminalReadTimeout = settings.terminalReadTimeout
        ))
        builtInServers.add(McpServerEntry(
            name = "JetBrains Git MCP",
            enabled = settings.enableGitMcp,
            level = McpServerLevel.BUILTIN,
            configSummary = "VCS integration and commit message generation",
            isBuiltIn = true,
            instructions = settings.gitInstructions,
            defaultInstructions = McpDefaults.GIT_INSTRUCTIONS
        ))

        // 加载自定义服务器
        customServers.clear()
        customServers.addAll(parseCustomServers(mcpSettings.getGlobalConfig(), McpServerLevel.GLOBAL))
        customServers.addAll(parseCustomServers(mcpSettings.getProjectConfig(project), McpServerLevel.PROJECT))

        refreshTable()
    }

    /**
     * 解析自定义服务器配置
     *
     * 存储格式：
     * {
     *   "server-name": {
     *     "config": { "command": "...", "args": [...] },  // 纯净的 MCP 配置
     *     "enabled": true,                                 // 我们的元数据
     *     "instructions": "..."                            // 我们的元数据
     *   }
     * }
     */
    private fun parseCustomServers(jsonStr: String, level: McpServerLevel): List<McpServerEntry> {
        if (jsonStr.isBlank()) return emptyList()

        return try {
            val parsed = json.parseToJsonElement(jsonStr).jsonObject

            // 直接从顶层读取（每个 key 是服务器名称）
            parsed.entries.map { (name, entry) ->
                val entryObj = entry.jsonObject

                // 读取纯净的 MCP 配置
                val mcpConfig = entryObj["config"]?.jsonObject ?: entryObj  // 兼容旧格式

                val command = mcpConfig["command"]?.toString()?.trim('"') ?: ""
                val url = mcpConfig["url"]?.toString()?.trim('"') ?: ""
                val serverType = mcpConfig["type"]?.toString()?.trim('"')

                // 读取我们的元数据
                val enabled = entryObj["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
                val instructions = entryObj["instructions"]?.toString()?.trim('"') ?: ""

                // 生成配置摘要
                val summary = if (serverType == "http" || url.isNotBlank()) {
                    "http: $url"
                } else {
                    "command: $command"
                }

                // jsonConfig 只保存纯净的 MCP 配置（用于编辑对话框显示）
                val pureConfig = json.encodeToString(JsonObject.serializer(), mcpConfig)

                McpServerEntry(
                    name = name,
                    enabled = enabled,
                    level = level,
                    configSummary = summary,
                    isBuiltIn = false,
                    jsonConfig = """{"$name": $pureConfig}""",
                    instructions = instructions
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun disposeUIResources() {
        table = null
        tableModel = null
        mainPanel = null
        builtInServers.clear()
        customServers.clear()
    }

    /**
     * MCP 服务器表格模型
     */
    inner class McpServerTableModel : AbstractTableModel() {
        private val columns = arrayOf("Status", "Name", "Configuration", "Level")

        override fun getRowCount(): Int = builtInServers.size + customServers.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entry = if (rowIndex < builtInServers.size) {
                builtInServers[rowIndex]
            } else {
                customServers[rowIndex - builtInServers.size]
            }

            return when (columnIndex) {
                0 -> entry.enabled
                1 -> entry.name
                2 -> entry.configSummary
                3 -> entry.level
                else -> ""
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> java.lang.Boolean::class.java
                3 -> McpServerLevel::class.java
                else -> String::class.java
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            // 只允许编辑 Status 列
            return columnIndex == 0
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && aValue is Boolean) {
                if (rowIndex < builtInServers.size) {
                    builtInServers[rowIndex] = builtInServers[rowIndex].copy(enabled = aValue)
                } else {
                    val customIndex = rowIndex - builtInServers.size
                    customServers[customIndex] = customServers[customIndex].copy(enabled = aValue)
                }
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }
    }

    /**
     * 状态列渲染器
     */
    inner class StatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val panel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
            panel.isOpaque = true
            panel.background = if (isSelected) table?.selectionBackground else table?.background

            val checkbox = JCheckBox().apply {
                this.isSelected = value as? Boolean ?: false
                this.isOpaque = false
                addActionListener {
                    tableModel?.setValueAt(this.isSelected, row, column)
                }
            }
            panel.add(checkbox)

            return panel
        }
    }

    /**
     * 配置列渲染器
     */
    inner class ConfigurationCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (component is JLabel) {
                component.foreground = if (isSelected) table?.selectionForeground else JBColor.GRAY
            }
            return component
        }
    }

    /**
     * Level 列渲染器
     */
    inner class LevelCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val level = value as? McpServerLevel ?: McpServerLevel.GLOBAL
            val text = when (level) {
                McpServerLevel.BUILTIN -> "Built-in"
                McpServerLevel.GLOBAL -> "Global"
                McpServerLevel.PROJECT -> "Project"
            }
            return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
        }
    }
}

/**
 * MCP 服务器级别
 */
enum class McpServerLevel {
    BUILTIN,  // 内置
    GLOBAL,   // 全局
    PROJECT   // 项目
}

/**
 * MCP 服务器条目
 */
data class McpServerEntry(
    val name: String,
    val enabled: Boolean = true,
    val level: McpServerLevel = McpServerLevel.GLOBAL,
    val configSummary: String = "",
    val isBuiltIn: Boolean = false,
    val jsonConfig: String = "",
    val instructions: String = "",
    val apiKey: String = "",
    /** 启用此 MCP 时禁用的内置工具列表 */
    val disabledTools: List<String> = emptyList(),
    /** 默认系统提示词（内置 MCP 使用，只读） */
    val defaultInstructions: String = "",
    /** 是否有关联的禁用工具开关（如 Terminal MCP 的 terminalDisableBuiltinBash） */
    val hasDisableToolsToggle: Boolean = false,
    /** Terminal MCP: 输出最大行数 */
    val terminalMaxOutputLines: Int = 500,
    /** Terminal MCP: 输出最大字符数 */
    val terminalMaxOutputChars: Int = 50000,
    /** Terminal MCP: 默认 shell（空字符串表示使用系统默认） */
    val terminalDefaultShell: String = "",
    /** Terminal MCP: 可用 shell 列表（逗号分隔） */
    val terminalAvailableShells: String = "",
    /** Terminal MCP: TerminalRead 默认超时时间（秒） */
    val terminalReadTimeout: Int = 30
)

/**
 * 内置 MCP 服务器编辑对话框
 *
 * 显示：
 * - 启用开关
 * - 默认系统提示词（只读，可折叠）
 * - 禁用工具信息
 * - 自定义追加提示词
 */
class BuiltInMcpServerDialog(
    private val project: Project?,
    private val entry: McpServerEntry
) : DialogWrapper(project) {

    private val enableCheckbox = JBCheckBox("Enable", entry.enabled)
    private val instructionsArea = JBTextArea(
        entry.instructions.ifBlank { entry.defaultInstructions },
        10, 50
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val apiKeyField = JBTextField(entry.apiKey, 25)

    // 禁用工具配置（标签选择）
    private val defaultDisabledTools = entry.disabledTools.toList()
    private val disabledToolsList = entry.disabledTools.toMutableList()
    private var disabledToolsPanel: JPanel? = null
    private val disabledToolInput = JBTextField(20)

    // Terminal MCP 截断配置
    private val maxOutputLinesField = JBTextField(entry.terminalMaxOutputLines.toString(), 8)
    private val maxOutputCharsField = JBTextField(entry.terminalMaxOutputChars.toString(), 8)
    // Terminal MCP 超时配置
    private val readTimeoutField = JBTextField(entry.terminalReadTimeout.toString(), 6)

    // Terminal MCP Shell 配置
    // 动态检测已安装的 shell
    private val allShellTypes = AgentSettingsService.getInstance().detectInstalledShells()
    private val defaultShellCombo = ComboBox<String>()
    private val availableShellCheckboxes = mutableMapOf<String, JBCheckBox>()

    /**
     * 更新 Default Shell 下拉框的选项
     * 只显示在 Available Shells 中勾选的 shell
     */
    private fun updateDefaultShellCombo() {
        val currentSelection = defaultShellCombo.selectedItem as? String
        val enabledShells = availableShellCheckboxes
            .filter { it.value.isSelected }
            .keys
            .toList()

        defaultShellCombo.removeAllItems()
        for (shell in enabledShells) {
            defaultShellCombo.addItem(shell)
        }

        // 恢复之前的选择，如果仍然可用
        if (currentSelection != null && enabledShells.contains(currentSelection)) {
            defaultShellCombo.selectedItem = currentSelection
        } else if (enabledShells.isNotEmpty()) {
            defaultShellCombo.selectedIndex = 0
        }
    }

    init {
        title = "Edit ${entry.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        // 顶部固定内容面板
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // 启用复选框
        val enablePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(enableCheckbox)
        }
        topPanel.add(enablePanel)
        topPanel.add(Box.createVerticalStrut(8))

        // Context7 的 API Key
        if (entry.name == "Context7 MCP") {
            val apiKeyPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("API Key (optional):"))
                add(apiKeyField)
            }
            topPanel.add(apiKeyPanel)
            topPanel.add(Box.createVerticalStrut(8))
        }

        // 禁用工具配置（标签选择界面）
        if (defaultDisabledTools.isNotEmpty()) {
            val disabledToolsLabel = JBLabel("Disables built-in tools when enabled:").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                foreground = JBColor(0x1976D2, 0x6BA3D6)
            }
            topPanel.add(disabledToolsLabel)
            topPanel.add(Box.createVerticalStrut(4))

            // 标签容器
            val toolsContainer = JPanel(BorderLayout()).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }

            // 标签流式布局面板
            disabledToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }

            // 初始化已有的标签
            disabledToolsList.forEach { addDisabledToolTag(it) }

            // 添加工具的输入行
            val addButton = JButton("+").apply {
                preferredSize = Dimension(40, disabledToolInput.preferredSize.height)
                toolTipText = "Add tool to disable"
            }

            val addToolAction = {
                val toolName = disabledToolInput.text.trim()
                if (toolName.isNotEmpty() && !disabledToolsList.contains(toolName)) {
                    addDisabledToolTag(toolName)
                    disabledToolInput.text = ""
                }
            }

            addButton.addActionListener { addToolAction() }
            disabledToolInput.addActionListener { addToolAction() }

            val inputRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(disabledToolInput)
                add(addButton)
                add(JBLabel("<html><font color='gray' size='-1'>Type tool name, then click + or press Enter</font></html>"))
            }

            toolsContainer.add(disabledToolsPanel!!, BorderLayout.CENTER)
            toolsContainer.add(inputRow, BorderLayout.SOUTH)

            val toolsScrollPane = JBScrollPane(toolsContainer).apply {
                preferredSize = Dimension(500, 70)
                alignmentX = JPanel.LEFT_ALIGNMENT
                border = BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            topPanel.add(toolsScrollPane)
            topPanel.add(Box.createVerticalStrut(4))

            // Reset 按钮
            val resetToolsButton = JButton("Reset").apply {
                toolTipText = "Reset to default: ${defaultDisabledTools.joinToString(", ")}"
                addActionListener {
                    disabledToolsList.clear()
                    disabledToolsPanel?.removeAll()
                    defaultDisabledTools.forEach { addDisabledToolTag(it) }
                    disabledToolsPanel?.revalidate()
                    disabledToolsPanel?.repaint()
                }
            }
            val toolsButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(resetToolsButton)
                add(Box.createHorizontalStrut(8))
                add(JBLabel("<html><font color='#666666' size='2'>Leave empty to not disable any built-in tools</font></html>"))
            }
            topPanel.add(toolsButtonPanel)
            topPanel.add(Box.createVerticalStrut(8))
        }

        // Terminal MCP 的 Shell 配置
        if (entry.name == "Terminal MCP") {
            val shellConfigLabel = JBLabel("Shell Configuration:").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            topPanel.add(shellConfigLabel)
            topPanel.add(Box.createVerticalStrut(4))

            // 默认 Shell 下拉框
            val defaultShellPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Default Shell:"))
                add(defaultShellCombo)
            }
            topPanel.add(defaultShellPanel)
            topPanel.add(Box.createVerticalStrut(4))

            // 可用 Shell 复选框
            val availableShellsLabel = JBLabel("Available Shells:").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                foreground = JBColor(0x666666, 0x999999)
                font = font.deriveFont(11f)
            }
            topPanel.add(availableShellsLabel)
            topPanel.add(Box.createVerticalStrut(2))

            // 解析已配置的可用 shells
            val configuredShells = entry.terminalAvailableShells.trim()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val useAllShells = configuredShells.isEmpty()

            val shellsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            for (shellType in allShellTypes) {
                val isChecked = useAllShells || configuredShells.contains(shellType)
                val checkbox = JBCheckBox(shellType, isChecked).apply {
                    // 当 checkbox 状态改变时，更新 Default Shell 下拉框
                    addActionListener { updateDefaultShellCombo() }
                }
                availableShellCheckboxes[shellType] = checkbox
                shellsPanel.add(checkbox)
            }
            topPanel.add(shellsPanel)

            // 初始化 Default Shell 下拉框（基于已勾选的 Available Shells）
            updateDefaultShellCombo()
            // 恢复之前保存的默认 shell 选择，或使用系统推荐的默认值
            val savedDefaultShell = entry.terminalDefaultShell
            val effectiveDefaultShell = if (savedDefaultShell.isNotBlank()) {
                savedDefaultShell
            } else {
                // 使用 getEffectiveDefaultShell() 获取系统推荐的默认 shell
                // Windows 下会优先使用 git-bash（如果已安装）
                AgentSettingsService.getInstance().getEffectiveDefaultShell()
            }
            if ((defaultShellCombo.model as? DefaultComboBoxModel<*>)?.getIndexOf(effectiveDefaultShell) != -1) {
                defaultShellCombo.selectedItem = effectiveDefaultShell
            }
            topPanel.add(Box.createVerticalStrut(8))
        }

        // Terminal MCP 的截断配置
        if (entry.name == "Terminal MCP") {
            val truncateLabel = JBLabel("Output Truncation:").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            topPanel.add(truncateLabel)
            topPanel.add(Box.createVerticalStrut(4))

            val truncatePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Max lines:"))
                add(maxOutputLinesField)
                add(Box.createHorizontalStrut(16))
                add(JBLabel("Max chars:"))
                add(maxOutputCharsField)
            }
            topPanel.add(truncatePanel)
            topPanel.add(Box.createVerticalStrut(8))

            // 超时配置
            val timeoutLabel = JBLabel("Read Timeout:").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            topPanel.add(timeoutLabel)
            topPanel.add(Box.createVerticalStrut(4))

            val timeoutPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Default timeout:"))
                add(readTimeoutField)
                add(JBLabel("seconds"))
                add(Box.createHorizontalStrut(8))
                add(JBLabel("<html><font color='gray' size='-1'>(when using wait=true)</font></html>"))
            }
            topPanel.add(timeoutPanel)
            topPanel.add(Box.createVerticalStrut(8))
        }

        // 系统提示词标签
        val customPromptLabel = JBLabel("Appended System Prompt:").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        topPanel.add(customPromptLabel)
        topPanel.add(Box.createVerticalStrut(4))

        // 中间可伸缩的提示词区域
        val customScrollPane = JBScrollPane(instructionsArea).apply {
            minimumSize = Dimension(500, 100)
            preferredSize = Dimension(500, 150)
        }

        // 底部 Reset 按钮
        val resetButton = JButton("Reset to Default").apply {
            addActionListener { instructionsArea.text = entry.defaultInstructions }
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(resetButton)
        }

        // 使用 BorderLayout 布局：顶部固定，中间伸缩，底部固定
        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(customScrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        panel.preferredSize = Dimension(580, 550)
        return panel
    }

    /**
     * 添加禁用工具标签
     */
    private fun addDisabledToolTag(toolName: String) {
        if (!disabledToolsList.contains(toolName)) {
            disabledToolsList.add(toolName)
        }

        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBUI.CurrentTheme.ActionButton.hoverBorder(), 1, true),
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = JBUI.CurrentTheme.ActionButton.hoverBackground()
            isOpaque = true
        }

        val label = JBLabel(toolName).apply {
            font = font.deriveFont(11f)
        }

        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Remove"
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                disabledToolsList.remove(toolName)
                disabledToolsPanel?.remove(tagPanel)
                disabledToolsPanel?.revalidate()
                disabledToolsPanel?.repaint()
            }
        })

        tagPanel.add(label)
        tagPanel.add(removeBtn)
        disabledToolsPanel?.add(tagPanel)
        disabledToolsPanel?.revalidate()
        disabledToolsPanel?.repaint()
    }

    fun getServerEntry(): McpServerEntry {
        // 如果内容与默认值相同，存储空字符串（表示使用默认值）
        val customInstructions = if (instructionsArea.text.trim() == entry.defaultInstructions.trim()) {
            ""
        } else {
            instructionsArea.text
        }

        // 获取选中的可用 shells
        val selectedShells = if (entry.name == "Terminal MCP") {
            availableShellCheckboxes
                .filter { it.value.isSelected }
                .keys
                .toList()
        } else emptyList()

        // 如果全部选中，存储空字符串（表示使用全部）
        val availableShellsValue = if (entry.name == "Terminal MCP") {
            if (selectedShells.size == allShellTypes.size) "" else selectedShells.joinToString(",")
        } else entry.terminalAvailableShells

        return entry.copy(
            enabled = enableCheckbox.isSelected,
            instructions = customInstructions,
            apiKey = if (entry.name == "Context7 MCP") apiKeyField.text else entry.apiKey,
            disabledTools = if (defaultDisabledTools.isNotEmpty()) disabledToolsList.toList() else entry.disabledTools,
            terminalMaxOutputLines = if (entry.name == "Terminal MCP") {
                maxOutputLinesField.text.toIntOrNull() ?: 500
            } else entry.terminalMaxOutputLines,
            terminalMaxOutputChars = if (entry.name == "Terminal MCP") {
                maxOutputCharsField.text.toIntOrNull() ?: 50000
            } else entry.terminalMaxOutputChars,
            terminalDefaultShell = if (entry.name == "Terminal MCP") {
                defaultShellCombo.selectedItem as? String ?: ""
            } else entry.terminalDefaultShell,
            terminalAvailableShells = availableShellsValue,
            terminalReadTimeout = if (entry.name == "Terminal MCP") {
                readTimeoutField.text.toIntOrNull() ?: 30
            } else entry.terminalReadTimeout
        )
    }
}

/**
 * 自定义 MCP 服务器编辑对话框
 */
class McpServerDialog(
    private val project: Project?,
    private val entry: McpServerEntry?
) : DialogWrapper(project) {

    private val enableCheckbox = JBCheckBox("Enable", entry?.enabled ?: true)
    private val jsonArea = JBTextArea(3, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        text = entry?.jsonConfig ?: ""
    }
    private val instructionsArea = JBTextArea(entry?.instructions ?: "", 2, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val levelGroup = ButtonGroup()
    private val globalRadio = JBRadioButton("Global", entry?.level != McpServerLevel.PROJECT)
    private val projectRadio = JBRadioButton("Project", entry?.level == McpServerLevel.PROJECT)

    init {
        title = if (entry == null) "New MCP Server" else "Edit MCP Server"
        levelGroup.add(globalRadio)
        levelGroup.add(projectRadio)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // Enable checkbox
        val enablePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(enableCheckbox)
        }
        contentPanel.add(enablePanel)
        contentPanel.add(Box.createVerticalStrut(10))

        // JSON 配置区域
        val jsonPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        val topPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(5)
            add(JBLabel("JSON configuration:"), BorderLayout.WEST)

            val formatButton = JButton(AllIcons.Actions.Preview).apply {
                toolTipText = "Format JSON"
                preferredSize = Dimension(28, 28)
                addActionListener { formatJson() }
            }
            val copyButton = JButton(AllIcons.Actions.Copy).apply {
                toolTipText = "Copy to clipboard"
                preferredSize = Dimension(28, 28)
                addActionListener {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(java.awt.datatransfer.StringSelection(jsonArea.text), null)
                }
            }
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
            buttonPanel.add(formatButton)
            buttonPanel.add(copyButton)
            add(buttonPanel, BorderLayout.EAST)
        }
        jsonPanel.add(topPanel, BorderLayout.NORTH)

        // 为 JSON 区域添加 placeholder
        val placeholderText = """{"server-name": {"command": "...", "args": [...]}}"""
        val placeholderColor = JBColor(0x999999, 0x666666)

        // 自定义绘制 placeholder
        val jsonAreaWithPlaceholder = object : JPanel(BorderLayout()) {
            init {
                add(jsonArea, BorderLayout.CENTER)
                jsonArea.isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                if (jsonArea.text.isEmpty() && !jsonArea.isFocusOwner) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g2.color = placeholderColor
                    g2.font = jsonArea.font
                    val fm = g2.fontMetrics
                    g2.drawString(placeholderText, jsonArea.insets.left + 2, fm.ascent + jsonArea.insets.top + 2)
                }
            }
        }
        jsonArea.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) { jsonAreaWithPlaceholder.repaint() }
            override fun focusLost(e: java.awt.event.FocusEvent?) { jsonAreaWithPlaceholder.repaint() }
        })

        val jsonScrollPane = JBScrollPane(jsonAreaWithPlaceholder).apply {
            preferredSize = Dimension(500, 60)
        }
        jsonPanel.add(jsonScrollPane, BorderLayout.CENTER)

        // 简洁的格式提示
        val hintLabel = JBLabel("<html><font color='gray' size='-1'>HTTP: {\"name\": {\"type\": \"http\", \"url\": \"https://...\"}}</font></html>").apply {
            border = JBUI.Borders.emptyTop(4)
        }
        jsonPanel.add(hintLabel, BorderLayout.SOUTH)

        contentPanel.add(jsonPanel)
        contentPanel.add(Box.createVerticalStrut(15))

        // Appended System Prompt
        val promptLabel = JBLabel("Appended System Prompt (optional):").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(promptLabel)
        contentPanel.add(Box.createVerticalStrut(5))

        val instructionsScrollPane = JBScrollPane(instructionsArea).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            preferredSize = Dimension(500, 60)
        }
        contentPanel.add(instructionsScrollPane)
        contentPanel.add(Box.createVerticalStrut(15))

        // Server level
        val levelPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Server level:"))
            add(globalRadio)
            add(projectRadio)
        }
        contentPanel.add(levelPanel)

        val levelHintLabel = JBLabel("<html><font color='gray' size='-1'>Global: all projects | Project: current project only</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyLeft(85)
        }
        contentPanel.add(levelHintLabel)
        contentPanel.add(Box.createVerticalStrut(10))

        // 警告
        val warningPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            background = JBColor(0xFFF3CD, 0x3D3000)
            border = JBUI.Borders.empty(8)
            add(JBLabel("<html><font color='#856404'>⚠ Proceed with caution and only connect to trusted servers.</font></html>"))
        }
        contentPanel.add(warningPanel)

        panel.add(contentPanel, BorderLayout.CENTER)
        panel.preferredSize = Dimension(550, panel.preferredSize.height)

        return panel
    }

    /**
     * 格式化 JSON
     */
    private fun formatJson() {
        try {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
            val parsed = json.parseToJsonElement(jsonArea.text.trim())
            jsonArea.text = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
        } catch (_: Exception) {
            // 忽略格式化错误
        }
    }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        val jsonText = jsonArea.text.trim()
        if (jsonText.isBlank()) {
            return com.intellij.openapi.ui.ValidationInfo("JSON configuration cannot be empty", jsonArea)
        }

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.parseToJsonElement(jsonText).jsonObject

            // 直接验证顶层对象（每个 key 是服务器名称）
            if (parsed.isEmpty()) {
                return com.intellij.openapi.ui.ValidationInfo("Configuration must contain at least one server", jsonArea)
            }

            // 验证每个服务器配置
            for ((serverName, serverConfig) in parsed) {
                if (serverName.isBlank()) {
                    return com.intellij.openapi.ui.ValidationInfo("Server name cannot be empty", jsonArea)
                }

                val config = serverConfig.jsonObject
                val hasCommand = config.containsKey("command")
                val hasUrl = config.containsKey("url")
                val serverType = config["type"]?.toString()?.trim('"')

                // HTTP 类型必须有 url
                if (serverType == "http" && !hasUrl) {
                    return com.intellij.openapi.ui.ValidationInfo("HTTP server '$serverName' must have 'url' field", jsonArea)
                }

                // STDIO 类型（默认）必须有 command
                if (serverType != "http" && !hasCommand) {
                    return com.intellij.openapi.ui.ValidationInfo("Server '$serverName' must have 'command' field (or 'type: http' with 'url')", jsonArea)
                }

                // 验证 command 不为空
                if (hasCommand) {
                    val command = config["command"]?.toString()?.trim('"') ?: ""
                    if (command.isBlank()) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'command' cannot be empty", jsonArea)
                    }
                }

                // 验证 url 不为空
                if (hasUrl) {
                    val url = config["url"]?.toString()?.trim('"') ?: ""
                    if (url.isBlank()) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'url' cannot be empty", jsonArea)
                    }
                }

                // 验证 args 是数组（如果存在）
                if (config.containsKey("args")) {
                    try {
                        config["args"]?.let {
                            if (it !is kotlinx.serialization.json.JsonArray) {
                                return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'args' must be an array", jsonArea)
                            }
                        }
                    } catch (_: Exception) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'args' must be an array", jsonArea)
                    }
                }

                // 验证 env 是对象（如果存在）
                if (config.containsKey("env")) {
                    try {
                        config["env"]?.jsonObject
                    } catch (_: Exception) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'env' must be an object", jsonArea)
                    }
                }
            }

            null
        } catch (e: Exception) {
            com.intellij.openapi.ui.ValidationInfo("Invalid JSON: ${e.message}", jsonArea)
        }
    }

    fun getServerEntry(): McpServerEntry {
        val jsonText = jsonArea.text.trim()
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.parseToJsonElement(jsonText).jsonObject

        // 直接从顶层读取（每个 key 是服务器名称）
        val serverName = parsed.keys.firstOrNull() ?: "unknown"
        val serverConfig = parsed[serverName]?.jsonObject

        // 生成配置摘要
        val serverType = serverConfig?.get("type")?.toString()?.trim('"')
        val summary = if (serverType == "http") {
            val url = serverConfig["url"]?.toString()?.trim('"') ?: ""
            "http: $url"
        } else {
            val command = serverConfig?.get("command")?.toString()?.trim('"') ?: ""
            "command: $command"
        }

        return McpServerEntry(
            name = serverName,
            enabled = enableCheckbox.isSelected,
            level = if (projectRadio.isSelected) McpServerLevel.PROJECT else McpServerLevel.GLOBAL,
            configSummary = summary,
            isBuiltIn = false,
            jsonConfig = jsonText,
            instructions = instructionsArea.text.trim()
        )
    }
}
