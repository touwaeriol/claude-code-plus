package com.claudecodeplus.test

import com.claudecodeplus.core.ui.ConversationPanel
import com.claudecodeplus.sdk.ClaudeCliWrapper
import java.awt.*
import javax.swing.*
import java.io.File

/**
 * 独立的测试应用，用于快速测试UI组件
 * 
 * 运行方法：
 * 1. 直接运行这个 main 函数
 * 2. 或者使用 gradle 任务: ./gradlew runTestApp
 */
fun main(args: Array<String>) {
    // 获取项目路径，默认使用当前工作目录
    val projectPath = if (args.isNotEmpty()) {
        args[0]
    } else {
        System.getProperty("user.dir")
    }
    
    println("Starting test app with project path: $projectPath")
    
    // 设置系统外观
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    SwingUtilities.invokeLater {
        TestFrame(projectPath).isVisible = true
    }
}

/**
 * 测试窗口
 */
class TestFrame(projectPath: String) : JFrame("Claude Code Plus - Test Environment") {
    
    private val projectService = MockProjectService(projectPath)
    private val fileIndex = CustomFileIndex(projectPath)
    private val fileSearchService = IndexedFileSearchService(projectPath, fileIndex)
    private val cliWrapper = ClaudeCliWrapper()
    
    init {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        setSize(900, 700)
        setLocationRelativeTo(null)
        
        setupUI()
    }
    
    private fun setupUI() {
        layout = BorderLayout()
        
        // 使用共享的ConversationPanel
        val conversationPanel = ConversationPanel(projectService, fileSearchService, cliWrapper)
        add(conversationPanel, BorderLayout.CENTER)
        
        // 添加状态栏
        add(createStatusBar(), BorderLayout.SOUTH)
    }
    
    private fun createStatusBar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            )
            
            add(JLabel("Status: Ready | "))
            add(JLabel("Project: ${projectService.getProjectName()} | "))
            add(JLabel("Path: ${projectService.getProjectPath()}"))
            
            // 添加索引状态
            if (fileIndex.isIndexReady()) {
                add(JLabel(" | Index: Ready ✓"))
            } else {
                add(JLabel(" | Index: Building..."))
            }
        }
    }
}