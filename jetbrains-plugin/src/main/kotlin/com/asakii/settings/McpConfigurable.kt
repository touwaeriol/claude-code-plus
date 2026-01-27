package com.asakii.settings

import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import com.asakii.ai.agent.sdk.CodexFeatures

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
        initTable()

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

        mainPanel = panel {
            row {
                text("Configure MCP (Model Context Protocol) servers. <a href=\"https://modelcontextprotocol.io\">Learn more</a>")
            }
            row {
                comment(ClaudeCodePlusBundle.message("mcp.settings.notice"))
            }

            row {
                cell(tablePanel)
                    .align(Align.FILL)
            }.resizableRow()

            row {
                label("<html><font color='#B07800'>\u26a0 Proceed with caution and only connect to trusted servers.</font></html>")
            }
        }

        // 加载配置
        reset()

        return mainPanel!!
    }

    private fun initTable() {
        tableModel = McpServerTableModel()
        table = JBTable(tableModel).apply {
            setShowGrid(false)
            intercellSpacing = Dimension(0, 0)
            rowHeight = 28
            tableHeader.reorderingAllowed = false

            // Status 列渲染器（显示启用状态）
            columnModel.getColumn(0).apply {
                cellRenderer = StatusCellRenderer()
            }

            // Configuration 列
            columnModel.getColumn(2).apply {
                cellRenderer = ConfigurationCellRenderer()
            }

            // Backends 列
            columnModel.getColumn(3).apply {
                cellRenderer = BackendCellRenderer()
            }

            // Level 列
            columnModel.getColumn(4).apply {
                cellRenderer = LevelCellRenderer()
            }

            // 鼠标点击处理
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)
                    if (row < 0) return

                    // 单击 Backends 列（第 3 列）时弹出后端选择
                    if (e.clickCount == 1 && col == 3) {
                        editBackends(row, e)
                        return
                    }

                    // 双击编辑完整配置
                    if (e.clickCount == 2) {
                        editServer(row)
                    }
                }
            })
        }
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
     * 快速编辑 Backends（单击 Backends 列时弹出）
     */
    private fun editBackends(row: Int, e: MouseEvent) {
        if (row < 0) return

        val entry = if (row < builtInServers.size) {
            builtInServers[row]
        } else {
            customServers.getOrNull(row - builtInServers.size) ?: return
        }

        // 创建弹出菜单
        val popup = JPopupMenu()
        val currentKeys = entry.enabledBackends

        val allItem = JCheckBoxMenuItem("All").apply {
            isSelected = currentKeys.contains(AgentSettingsService.MCP_BACKEND_ALL)
            addActionListener {
                updateBackends(row, setOf(AgentSettingsService.MCP_BACKEND_ALL))
            }
        }

        val claudeItem = JCheckBoxMenuItem("Claude Code").apply {
            isSelected = !currentKeys.contains(AgentSettingsService.MCP_BACKEND_ALL) &&
                currentKeys.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)
            isEnabled = !allItem.isSelected
            addActionListener {
                val newKeys = mutableSetOf<String>()
                if (isSelected) newKeys.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
                if (popup.components.filterIsInstance<JCheckBoxMenuItem>()
                        .find { it.text == "Codex" }?.isSelected == true) {
                    newKeys.add(AgentSettingsService.MCP_BACKEND_CODEX)
                }
                if (newKeys.isEmpty()) newKeys.add(AgentSettingsService.MCP_BACKEND_ALL)
                updateBackends(row, newKeys)
            }
        }

        val codexItem = JCheckBoxMenuItem("Codex").apply {
            isSelected = !currentKeys.contains(AgentSettingsService.MCP_BACKEND_ALL) &&
                currentKeys.contains(AgentSettingsService.MCP_BACKEND_CODEX)
            isEnabled = !allItem.isSelected
            addActionListener {
                val newKeys = mutableSetOf<String>()
                if (isSelected) newKeys.add(AgentSettingsService.MCP_BACKEND_CODEX)
                if (popup.components.filterIsInstance<JCheckBoxMenuItem>()
                        .find { it.text == "Claude Code" }?.isSelected == true) {
                    newKeys.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
                }
                if (newKeys.isEmpty()) newKeys.add(AgentSettingsService.MCP_BACKEND_ALL)
                updateBackends(row, newKeys)
            }
        }

        // All 选中时禁用其他选项
        allItem.addActionListener {
            if (allItem.isSelected) {
                claudeItem.isSelected = false
                claudeItem.isEnabled = false
                codexItem.isSelected = false
                codexItem.isEnabled = false
            } else {
                claudeItem.isEnabled = true
                codexItem.isEnabled = true
            }
        }

        popup.add(allItem)
        popup.addSeparator()
        popup.add(claudeItem)
        popup.add(codexItem)

        // 在点击位置显示弹出菜单
        popup.show(e.component, e.x, e.y)
    }

    /**
     * 更新服务器的 Backends 配置
     */
    private fun updateBackends(row: Int, newBackends: Set<String>) {
        if (row < builtInServers.size) {
            builtInServers[row] = builtInServers[row].copy(enabledBackends = newBackends)
        } else {
            val customIndex = row - builtInServers.size
            if (customIndex < customServers.size) {
                customServers[customIndex] = customServers[customIndex].copy(enabledBackends = newBackends)
            }
        }
        refreshTable()
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

    private fun normalizeBackendKeys(keys: Set<String>): Set<String> {
        val normalized = keys.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_ALL)) {
            return setOf(AgentSettingsService.MCP_BACKEND_ALL)
        }
        return normalized.intersect(
            setOf(
                AgentSettingsService.MCP_BACKEND_CLAUDE,
                AgentSettingsService.MCP_BACKEND_CODEX
            )
        )
    }

    private fun parseBackendKeys(
        element: kotlinx.serialization.json.JsonElement?,
        fallback: Set<String>
    ): Set<String> {
        if (element == null) return fallback
        return when (element) {
            is kotlinx.serialization.json.JsonArray -> {
                val raw = element.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                normalizeBackendKeys(raw)
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                val raw = element.contentOrNull ?: ""
                val parsed = try {
                    json.decodeFromString<List<String>>(raw).toSet()
                } catch (_: Exception) {
                    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                normalizeBackendKeys(parsed)
            }
            else -> fallback
        }
    }

    private fun formatBackendLabel(keys: Set<String>): String {
        val normalized = normalizeBackendKeys(keys)
        if (normalized.isEmpty()) return "-"
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_ALL)) return "All"
        val labels = mutableListOf<String>()
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)) {
            labels.add("Claude Code")
        }
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_CODEX)) {
            labels.add("Codex")
        }
        return if (labels.isEmpty()) "-" else labels.joinToString("/")
    }


    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()
        val fallbackBackends = settings.getMcpEnabledBackendKeys().ifEmpty {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        }

        // 检查内置服务器配置
        val userInteractionEntry = builtInServers.find { it.name == McpBundle.message("mcp.userInteraction.name") }
        val jetbrainsEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsIde.name") }
        val jetbrainsFileEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsFile.name") }
        val context7Entry = builtInServers.find { it.name == McpBundle.message("mcp.context7.name") }
        val terminalEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsTerminal.name") }
        val gitEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsGit.name") }

        if (userInteractionEntry?.enabled != settings.enableUserInteractionMcp ||
            jetbrainsEntry?.enabled != settings.enableJetBrainsMcp ||
            jetbrainsFileEntry?.enabled != settings.enableJetBrainsFileMcp ||
            context7Entry?.enabled != settings.enableContext7Mcp ||
            terminalEntry?.enabled != settings.enableTerminalMcp ||
            gitEntry?.enabled != settings.enableGitMcp ||
            userInteractionEntry?.enabledBackends != settings.getUserInteractionMcpBackendKeys() ||
            jetbrainsEntry?.enabledBackends != settings.getJetbrainsMcpBackendKeys() ||
            jetbrainsFileEntry?.enabledBackends != settings.getJetbrainsFileMcpBackendKeys() ||
            context7Entry?.enabledBackends != settings.getContext7McpBackendKeys() ||
            terminalEntry?.enabledBackends != settings.getTerminalMcpBackendKeys() ||
            gitEntry?.enabledBackends != settings.getGitMcpBackendKeys() ||
            context7Entry?.apiKey != settings.context7ApiKey ||
            userInteractionEntry?.instructions != settings.userInteractionInstructions ||
            userInteractionEntry?.instructionsClaude != settings.userInteractionInstructionsClaude ||
            userInteractionEntry?.instructionsCodex != settings.userInteractionInstructionsCodex ||
            jetbrainsEntry?.instructions != settings.jetbrainsInstructions ||
            jetbrainsEntry?.instructionsClaude != settings.jetbrainsInstructionsClaude ||
            jetbrainsEntry?.instructionsCodex != settings.jetbrainsInstructionsCodex ||
            jetbrainsFileEntry?.instructions != settings.jetbrainsFileInstructions ||
            jetbrainsFileEntry?.instructionsClaude != settings.jetbrainsFileInstructionsClaude ||
            jetbrainsFileEntry?.instructionsCodex != settings.jetbrainsFileInstructionsCodex ||
            context7Entry?.instructions != settings.context7Instructions ||
            context7Entry?.instructionsClaude != settings.context7InstructionsClaude ||
            context7Entry?.instructionsCodex != settings.context7InstructionsCodex ||
            terminalEntry?.instructions != settings.terminalInstructions ||
            terminalEntry?.instructionsClaude != settings.terminalInstructionsClaude ||
            terminalEntry?.instructionsCodex != settings.terminalInstructionsCodex ||
            gitEntry?.instructions != settings.gitInstructions ||
            gitEntry?.instructionsClaude != settings.gitInstructionsClaude ||
            gitEntry?.instructionsCodex != settings.gitInstructionsCodex ||
            gitEntry?.gitCommitLanguage != settings.gitCommitLanguage ||
            terminalEntry?.terminalMaxOutputLines != settings.terminalMaxOutputLines ||
            terminalEntry?.terminalMaxOutputChars != settings.terminalMaxOutputChars ||
            terminalEntry?.terminalReadTimeout != settings.terminalReadTimeout ||
            // Terminal MCP 禁用工具配置（Bash 对应 Claude Code，shell_tool 对应 Codex）
            ((terminalEntry?.disabledTools?.contains("Bash") == true) ||
                (terminalEntry?.codexDisabledFeatures?.contains(CodexFeatures.SHELL_TOOL) == true)) != settings.terminalDisableBuiltinBash ||
            // JetBrains File MCP 禁用工具配置
            (jetbrainsFileEntry?.disabledTools?.isNotEmpty() ?: false) != settings.jetbrainsFileDisableBuiltinTools ||
            jetbrainsFileEntry?.disabledTools != settings.getJetbrainsFileDisabledToolsList() ||
            // JetBrains File MCP 外部文件配置
            jetbrainsFileEntry?.fileAllowExternal != settings.jetbrainsFileAllowExternal ||
            jetbrainsFileEntry?.fileExternalRules != settings.jetbrainsFileExternalRules
        ) {
            return true
        }

        // 检查自定义服务器配置
        val savedGlobalServers = parseCustomServers(
            mcpSettings.getGlobalConfig(),
            McpServerLevel.GLOBAL,
            fallbackBackends
        )
        val savedProjectServers = parseCustomServers(
            mcpSettings.getProjectConfig(project),
            McpServerLevel.PROJECT,
            fallbackBackends
        )
        val savedCustomServers = savedGlobalServers + savedProjectServers

        if (customServers.size != savedCustomServers.size) return true

        // 比较每个自定义服务器
        for (server in customServers) {
            val saved = savedCustomServers.find { it.name == server.name && it.level == server.level }
            if (saved == null ||
                saved.jsonConfig != server.jsonConfig ||
                saved.enabled != server.enabled ||
                saved.instructions != server.instructions ||
                saved.instructionsClaude != server.instructionsClaude ||
                saved.instructionsCodex != server.instructionsCodex ||
                saved.enabledBackends != server.enabledBackends
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
        val userInteractionEntry = builtInServers.find { it.name == McpBundle.message("mcp.userInteraction.name") }
        val jetbrainsEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsIde.name") }
        val jetbrainsFileEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsFile.name") }
        val context7Entry = builtInServers.find { it.name == McpBundle.message("mcp.context7.name") }
        val terminalEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsTerminal.name") }
        val gitEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsGit.name") }

        settings.enableUserInteractionMcp = userInteractionEntry?.enabled ?: true
        settings.enableJetBrainsMcp = jetbrainsEntry?.enabled ?: true
        settings.enableJetBrainsFileMcp = jetbrainsFileEntry?.enabled ?: true
        settings.enableContext7Mcp = context7Entry?.enabled ?: false
        settings.enableGitMcp = gitEntry?.enabled ?: false
        settings.gitCommitLanguage = gitEntry?.gitCommitLanguage ?: "en"
        settings.enableTerminalMcp = terminalEntry?.enabled ?: false
        settings.setUserInteractionMcpBackendKeys(
            userInteractionEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setJetbrainsMcpBackendKeys(
            jetbrainsEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setJetbrainsFileMcpBackendKeys(
            jetbrainsFileEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setContext7McpBackendKeys(
            context7Entry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setTerminalMcpBackendKeys(
            terminalEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setGitMcpBackendKeys(
            gitEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.context7ApiKey = context7Entry?.apiKey ?: ""
        settings.userInteractionInstructions = userInteractionEntry?.instructions ?: ""
        settings.userInteractionInstructionsClaude = userInteractionEntry?.instructionsClaude ?: ""
        settings.userInteractionInstructionsCodex = userInteractionEntry?.instructionsCodex ?: ""
        settings.jetbrainsInstructions = jetbrainsEntry?.instructions ?: ""
        settings.jetbrainsInstructionsClaude = jetbrainsEntry?.instructionsClaude ?: ""
        settings.jetbrainsInstructionsCodex = jetbrainsEntry?.instructionsCodex ?: ""
        settings.jetbrainsFileInstructions = jetbrainsFileEntry?.instructions ?: ""
        settings.jetbrainsFileInstructionsClaude = jetbrainsFileEntry?.instructionsClaude ?: ""
        settings.jetbrainsFileInstructionsCodex = jetbrainsFileEntry?.instructionsCodex ?: ""
        settings.context7Instructions = context7Entry?.instructions ?: ""
        settings.context7InstructionsClaude = context7Entry?.instructionsClaude ?: ""
        settings.context7InstructionsCodex = context7Entry?.instructionsCodex ?: ""
        settings.terminalInstructions = terminalEntry?.instructions ?: ""
        settings.terminalInstructionsClaude = terminalEntry?.instructionsClaude ?: ""
        settings.terminalInstructionsCodex = terminalEntry?.instructionsCodex ?: ""
        settings.gitInstructions = gitEntry?.instructions ?: ""
        settings.gitInstructionsClaude = gitEntry?.instructionsClaude ?: ""
        settings.gitInstructionsCodex = gitEntry?.instructionsCodex ?: ""
        settings.terminalMaxOutputLines = terminalEntry?.terminalMaxOutputLines ?: 500
        settings.terminalMaxOutputChars = terminalEntry?.terminalMaxOutputChars ?: 50000
        settings.terminalDefaultShell = terminalEntry?.terminalDefaultShell ?: ""
        settings.terminalAvailableShells = terminalEntry?.terminalAvailableShells ?: ""
        settings.terminalReadTimeout = terminalEntry?.terminalReadTimeout ?: 30
        // 保存 Terminal MCP 禁用工具配置（Bash 对应 Claude Code，shell_tool 对应 Codex）
        // 如果任一列表包含相应的禁用项，则启用禁用开关
        settings.terminalDisableBuiltinBash = (terminalEntry?.disabledTools?.contains("Bash") == true) ||
            (terminalEntry?.codexDisabledFeatures?.contains(CodexFeatures.SHELL_TOOL) == true)
        // 保存 JetBrains File MCP 禁用工具配置
        settings.jetbrainsFileDisableBuiltinTools = (jetbrainsFileEntry?.disabledTools?.isNotEmpty() ?: false)
        settings.setJetbrainsFileDisabledToolsList(jetbrainsFileEntry?.disabledTools ?: listOf("Read", "Write", "Edit"))
        // 保存超时配置
        settings.userInteractionMcpTimeout = userInteractionEntry?.toolTimeoutSec ?: 3600
        settings.jetbrainsMcpTimeout = jetbrainsEntry?.toolTimeoutSec ?: 60
        settings.jetbrainsFileMcpTimeout = jetbrainsFileEntry?.toolTimeoutSec ?: 60
        // 保存 JetBrains File MCP 外部文件配置
        settings.jetbrainsFileAllowExternal = jetbrainsFileEntry?.fileAllowExternal ?: true
        settings.jetbrainsFileExternalRules = jetbrainsFileEntry?.fileExternalRules ?: "[]"
        settings.context7McpTimeout = context7Entry?.toolTimeoutSec ?: 60
        settings.terminalMcpTimeout = terminalEntry?.toolTimeoutSec ?: 60
        settings.gitMcpTimeout = gitEntry?.toolTimeoutSec ?: 60

        // 保存 Codex 自动批准工具配置
        settings.setUserInteractionAutoApprovedTools(userInteractionEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setJetbrainsLspAutoApprovedTools(jetbrainsEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setJetbrainsFileAutoApprovedTools(jetbrainsFileEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setTerminalAutoApprovedTools(terminalEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setGitAutoApprovedTools(gitEntry?.codexAutoApprovedTools ?: emptyList())

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
                    entryMap["enabledBackends"] = kotlinx.serialization.json.JsonArray(
                        normalizeBackendKeys(server.enabledBackends)
                            .map { kotlinx.serialization.json.JsonPrimitive(it) }
                    )
                    if (server.instructions.isNotBlank()) {
                        entryMap["instructions"] = kotlinx.serialization.json.JsonPrimitive(server.instructions)
                    }
                    val instructionsByBackend = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                    if (server.instructionsClaude.isNotBlank()) {
                        instructionsByBackend[AgentSettingsService.MCP_BACKEND_CLAUDE] =
                            kotlinx.serialization.json.JsonPrimitive(server.instructionsClaude)
                    }
                    if (server.instructionsCodex.isNotBlank()) {
                        instructionsByBackend[AgentSettingsService.MCP_BACKEND_CODEX] =
                            kotlinx.serialization.json.JsonPrimitive(server.instructionsCodex)
                    }
                    if (instructionsByBackend.isNotEmpty()) {
                        entryMap["instructionsByBackend"] = JsonObject(instructionsByBackend)
                    }
                    // 添加超时配置（0 表示永不超时）
                    entryMap["toolTimeoutSec"] = kotlinx.serialization.json.JsonPrimitive(server.toolTimeoutSec)
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

        val userInteractionBackends = settings.getUserInteractionMcpBackendKeys()
        val jetbrainsBackends = settings.getJetbrainsMcpBackendKeys()
        val jetbrainsFileBackends = settings.getJetbrainsFileMcpBackendKeys()
        val context7Backends = settings.getContext7McpBackendKeys()
        val terminalBackends = settings.getTerminalMcpBackendKeys()
        val gitBackends = settings.getGitMcpBackendKeys()

        // 加载内置服务器
        builtInServers.clear()
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.userInteraction.name"),
            enabled = settings.enableUserInteractionMcp,
            enabledBackends = userInteractionBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.userInteraction.description"),
            isBuiltIn = true,
            instructions = settings.userInteractionInstructions,
            instructionsClaude = settings.userInteractionInstructionsClaude,
            instructionsCodex = settings.userInteractionInstructionsCodex,
            defaultInstructions = McpDefaults.USER_INTERACTION_INSTRUCTIONS,
            toolTimeoutSec = settings.userInteractionMcpTimeout,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.USER_INTERACTION,
            codexAutoApprovedTools = settings.getUserInteractionAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsIde.name"),
            enabled = settings.enableJetBrainsMcp,
            enabledBackends = jetbrainsBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsIde.description"),
            isBuiltIn = true,
            instructions = settings.jetbrainsInstructions,
            instructionsClaude = settings.jetbrainsInstructionsClaude,
            instructionsCodex = settings.jetbrainsInstructionsCodex,
            defaultInstructions = McpDefaults.JETBRAINS_INSTRUCTIONS,
            disabledTools = listOf("Glob", "Grep"),
            toolTimeoutSec = settings.jetbrainsMcpTimeout,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_LSP,
            codexAutoApprovedTools = settings.getJetbrainsLspAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsFile.name"),
            enabled = settings.enableJetBrainsFileMcp,
            enabledBackends = jetbrainsFileBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsFile.description"),
            isBuiltIn = true,
            instructions = settings.jetbrainsFileInstructions,
            instructionsClaude = settings.jetbrainsFileInstructionsClaude,
            instructionsCodex = settings.jetbrainsFileInstructionsCodex,
            defaultInstructions = McpDefaults.JETBRAINS_FILE_INSTRUCTIONS,
            disabledTools = if (settings.jetbrainsFileDisableBuiltinTools) settings.getJetbrainsFileDisabledToolsList() else emptyList(),
            codexDisabledFeatures = if (settings.jetbrainsFileDisableBuiltinTools) listOf(CodexFeatures.APPLY_PATCH_FREEFORM) else emptyList(),
            hasDisableToolsToggle = true,
            toolTimeoutSec = settings.jetbrainsFileMcpTimeout,
            fileAllowExternal = settings.jetbrainsFileAllowExternal,
            fileExternalRules = settings.jetbrainsFileExternalRules,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_FILE,
            codexAutoApprovedTools = settings.getJetbrainsFileAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.context7.name"),
            enabled = settings.enableContext7Mcp,
            enabledBackends = context7Backends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.context7.description"),
            isBuiltIn = true,
            instructions = settings.context7Instructions,
            instructionsClaude = settings.context7InstructionsClaude,
            instructionsCodex = settings.context7InstructionsCodex,
            apiKey = settings.context7ApiKey,
            defaultInstructions = McpDefaults.CONTEXT7_INSTRUCTIONS,
            toolTimeoutSec = settings.context7McpTimeout
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsTerminal.name"),
            enabled = settings.enableTerminalMcp,
            enabledBackends = terminalBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsTerminal.description"),
            isBuiltIn = true,
            instructions = settings.terminalInstructions,
            instructionsClaude = settings.terminalInstructionsClaude,
            instructionsCodex = settings.terminalInstructionsCodex,
            defaultInstructions = McpDefaults.TERMINAL_INSTRUCTIONS,
            disabledTools = if (settings.terminalDisableBuiltinBash) listOf("Bash") else emptyList(),
            codexDisabledFeatures = if (settings.terminalDisableBuiltinBash) listOf(CodexFeatures.SHELL_TOOL) else emptyList(),
            hasDisableToolsToggle = true,
            terminalMaxOutputLines = settings.terminalMaxOutputLines,
            terminalMaxOutputChars = settings.terminalMaxOutputChars,
            terminalDefaultShell = settings.terminalDefaultShell,
            terminalAvailableShells = settings.terminalAvailableShells,
            terminalReadTimeout = settings.terminalReadTimeout,
            toolTimeoutSec = settings.terminalMcpTimeout,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_TERMINAL,
            codexAutoApprovedTools = settings.getTerminalAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsGit.name"),
            enabled = settings.enableGitMcp,
            enabledBackends = gitBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsGit.description"),
            isBuiltIn = true,
            instructions = settings.gitInstructions,
            instructionsClaude = settings.gitInstructionsClaude,
            instructionsCodex = settings.gitInstructionsCodex,
            defaultInstructions = McpDefaults.GIT_INSTRUCTIONS,
            toolTimeoutSec = settings.gitMcpTimeout,
            gitCommitLanguage = settings.gitCommitLanguage,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.IDE_GIT,
            codexAutoApprovedTools = settings.getGitAutoApprovedTools()
        ))

        // 加载自定义服务器
        val fallbackBackends = settings.getMcpEnabledBackendKeys().ifEmpty {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        }
        customServers.clear()
        customServers.addAll(parseCustomServers(mcpSettings.getGlobalConfig(), McpServerLevel.GLOBAL, fallbackBackends))
        customServers.addAll(parseCustomServers(mcpSettings.getProjectConfig(project), McpServerLevel.PROJECT, fallbackBackends))

        refreshTable()
    }

    /**
     * 解析自定义服务器配置
     */
    private fun parseCustomServers(
        jsonStr: String,
        level: McpServerLevel,
        fallbackBackends: Set<String>
    ): List<McpServerEntry> {
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
                val instructionsByBackend = entryObj["instructionsByBackend"]?.jsonObject
                val instructionsClaude = instructionsByBackend
                    ?.get(AgentSettingsService.MCP_BACKEND_CLAUDE)
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""
                val instructionsCodex = instructionsByBackend
                    ?.get(AgentSettingsService.MCP_BACKEND_CODEX)
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""
                val enabledBackends = parseBackendKeys(entryObj["enabledBackends"], fallbackBackends)
                val toolTimeoutSec = entryObj["toolTimeoutSec"]?.jsonPrimitive?.intOrNull ?: 60

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
                    enabledBackends = enabledBackends,
                    level = level,
                    configSummary = summary,
                    isBuiltIn = false,
                    jsonConfig = """{"$name": $pureConfig}""",
                    instructions = instructions,
                    instructionsClaude = instructionsClaude,
                    instructionsCodex = instructionsCodex,
                    toolTimeoutSec = toolTimeoutSec
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
        private val columns = arrayOf("Status", "Name", "Configuration", "Backends", "Level")

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
                3 -> formatBackendLabel(entry.enabledBackends)
                4 -> entry.level
                else -> ""
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> java.lang.Boolean::class.java
                4 -> McpServerLevel::class.java
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
     * Backends 列渲染器
     */
    inner class BackendCellRenderer : DefaultTableCellRenderer() {
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
