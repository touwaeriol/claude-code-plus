package com.asakii.plugin.ui.title

import com.asakii.rpc.api.JetBrainsSessionApi
import com.asakii.rpc.api.JetBrainsSessionCommand
import com.asakii.rpc.api.JetBrainsSessionCommandType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.logging.Logger

/**
 * 新建会话按钮 - 显示在 ToolWindow 标题栏右侧
 *
 * 点击后：
 * - 如果当前会话正在生成中 → 创建新 Tab
 * - 否则 → 重置/清空当前会话（不新建 Tab）
 */
class NewSessionAction(
    private val sessionApi: JetBrainsSessionApi
) : AnAction("新建会话", "创建新会话", AllIcons.General.Add) {

    private val logger = Logger.getLogger(NewSessionAction::class.java.name)

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("🆕 [NewSessionAction] 点击新建会话按钮")

        // 检查当前会话是否正在生成中
        val currentState = sessionApi.getState()
        val activeSessionId = currentState?.activeSessionId
        val activeSession = currentState?.sessions?.find { it.id == activeSessionId }
        val isGenerating = activeSession?.isGenerating == true || activeSession?.isConnecting == true

        if (isGenerating) {
            // 当前会话正在生成中，创建新 Tab
            logger.info("🆕 [NewSessionAction] 当前会话正在生成，发送 CREATE 命令")
            sessionApi.sendCommand(JetBrainsSessionCommand(
                type = JetBrainsSessionCommandType.CREATE
            ))
        } else {
            // 当前会话空闲，重置/清空当前会话
            logger.info("🆕 [NewSessionAction] 当前会话空闲，发送 RESET 命令")
            sessionApi.sendCommand(JetBrainsSessionCommand(
                type = JetBrainsSessionCommandType.RESET
            ))
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
}
