package com.claudecodeplus.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.ui.Messages

/**
 * 测试自动完成配置的动作
 */
class TestCompletionAction : AnAction("Test Completion Config") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val info = buildString {
            appendLine("=== 自动完成配置测试 ===")
            appendLine()
            appendLine("PlainTextLanguage 信息:")
            appendLine("- ID: ${PlainTextLanguage.INSTANCE.id}")
            appendLine("- Display Name: ${PlainTextLanguage.INSTANCE.displayName}")
            appendLine()
            appendLine("使用说明:")
            appendLine("1. 在输入框中输入空格，然后输入 @ 符号")
            appendLine("2. 如果自动完成没有触发，尝试按 Ctrl+Space (Windows/Linux) 或 Cmd+Space (macOS)")
            appendLine("3. 检查 IDE 日志中的调试信息")
            appendLine()
            appendLine("调试信息位置:")
            appendLine("Help → Show Log in Explorer/Finder")
            appendLine("搜索: [FileReferenceEditorField] 或 [FileReferenceCompletionProvider]")
        }
        
        Messages.showInfoMessage(project, info, "自动完成配置信息")
    }
}