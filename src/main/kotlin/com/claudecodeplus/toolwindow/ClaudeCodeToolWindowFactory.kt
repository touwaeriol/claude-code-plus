package com.claudecodeplus.toolwindow

import com.claudecodeplus.service.ClaudeCodeService
import com.claudecodeplus.ui.IntelliJMarkdownChatWindow
import com.claudecodeplus.util.ProjectPathDebugger
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * 工具窗口工厂，用于创建 Claude Code 聊天窗口
 */
class ClaudeCodeToolWindowFactory : ToolWindowFactory {
    companion object {
        private val LOG = logger<ClaudeCodeToolWindowFactory>()
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 立即打印项目路径信息
        LOG.info("=== Claude Code Plus 插件启动 ===")
        LOG.info("项目名称: ${project.name}")
        LOG.info("项目路径: ${project.basePath}")
        
        // 获取详细的路径调试信息
        val debugInfo = ProjectPathDebugger.debugProjectPath(project)
        LOG.info("详细路径信息:\n$debugInfo")
        
        // 获取推荐的工作目录
        val workingDirectory = ProjectPathDebugger.getProjectWorkingDirectory(project)
        LOG.info("推荐的工作目录: $workingDirectory")
        
        // 获取服务实例
        val service = project.service<ClaudeCodeService>()
        
        // 创建聊天窗口 - 使用 IntelliJ Markdown 渲染器
        LOG.info("创建 IntelliJ Markdown 聊天窗口")
        val chatWindow = IntelliJMarkdownChatWindow(project, service)
        val component = chatWindow.createComponent()
        
        // 保存引用以便清理
        toolWindow.disposable.let { parent ->
            com.intellij.openapi.util.Disposer.register(parent, chatWindow)
        }
        
        val content = ContentFactory.getInstance().createContent(component, "", false)
        
        // 添加到工具窗口
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}