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
 * 点击后触发前端创建新会话
 */
class NewSessionAction(
    private val sessionApi: JetBrainsSessionApi
) : AnAction("新建会话", "创建新会话", AllIcons.General.Add) {

    private val logger = Logger.getLogger(NewSessionAction::class.java.name)

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("🆕 [NewSessionAction] 点击新建会话按钮")
        sessionApi.sendCommand(JetBrainsSessionCommand(
            type = JetBrainsSessionCommandType.CREATE
        ))
        logger.info("🆕 [NewSessionAction] 已发送 CREATE 命令")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
}
