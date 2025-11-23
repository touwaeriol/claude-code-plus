package com.claudecodeplus.plugin.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Token 使用统计对话框
 * 
 * 显示详细的 token 使用统计信息
 */
class TokenStatsDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    private var inputTokens = 0L
    private var outputTokens = 0L
    private var totalTokens = 0L
    private var sessionTokens = 0L
    private var estimatedCost = 0.0
    
    init {
        title = "Token 使用统计"
        setOKButtonText("关闭")
        init()
        loadStats()
    }
    
    private fun loadStats() {
        // TODO: 从实际的统计服务加载数据
        // 这里使用模拟数据
        inputTokens = 12450L
        outputTokens = 8932L
        totalTokens = inputTokens + outputTokens
        sessionTokens = 3456L
        estimatedCost = calculateCost()
    }
    
    private fun calculateCost(): Double {
        // Claude Sonnet 定价示例（实际价格可能不同）
        val inputCostPer1M = 3.0  // $3 per 1M input tokens
        val outputCostPer1M = 15.0 // $15 per 1M output tokens
        
        val inputCost = (inputTokens / 1_000_000.0) * inputCostPer1M
        val outputCost = (outputTokens / 1_000_000.0) * outputCostPer1M
        
        return inputCost + outputCost
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(500, 400)
        mainPanel.border = JBUI.Borders.empty(10)
        
        // 创建统计面板
        val statsPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // 总览部分
        addSection(statsPanel, gbc, 0, "总览")
        addStat(statsPanel, gbc, 1, "总 Token 使用量:", formatNumber(totalTokens))
        addStat(statsPanel, gbc, 2, "输入 Tokens:", formatNumber(inputTokens))
        addStat(statsPanel, gbc, 3, "输出 Tokens:", formatNumber(outputTokens))
        
        // 当前会话
        addSection(statsPanel, gbc, 4, "当前会话")
        addStat(statsPanel, gbc, 5, "会话 Token 使用:", formatNumber(sessionTokens))
        
        // 成本估算
        addSection(statsPanel, gbc, 6, "成本估算")
        addStat(statsPanel, gbc, 7, "预估成本:", String.format("$%.4f", estimatedCost))
        
        // 提示信息
        gbc.gridx = 0
        gbc.gridy = 8
        gbc.gridwidth = 2
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        val hint = JLabel("<html><small><i>注意: 成本估算仅供参考，实际费用以 API 提供商为准</i></small></html>")
        statsPanel.add(hint, gbc)
        
        val scrollPane = JBScrollPane(statsPanel)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    private fun addSection(panel: JPanel, gbc: GridBagConstraints, row: Int, title: String) {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.insets = JBUI.insets(10, 5, 5, 5)
        
        val sectionLabel = JLabel("<html><b>$title</b></html>")
        sectionLabel.font = sectionLabel.font.deriveFont(14f)
        panel.add(sectionLabel, gbc)
    }
    
    private fun addStat(panel: JPanel, gbc: GridBagConstraints, row: Int, label: String, value: String) {
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.insets = JBUI.insets(2, 20, 2, 5)
        
        // 标签
        gbc.gridx = 0
        gbc.weightx = 0.5
        panel.add(JLabel(label), gbc)
        
        // 值
        gbc.gridx = 1
        gbc.weightx = 0.5
        val valueLabel = JLabel("<html><b>$value</b></html>")
        panel.add(valueLabel, gbc)
    }
    
    private fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }
    
    companion object {
        /**
         * 显示 Token 统计对话框
         */
        fun show(project: Project?) {
            val dialog = TokenStatsDialog(project)
            dialog.show()
        }
    }
}


