package com.claudecodeplus.ui.utils

import com.claudecodeplus.ui.models.ContextReference

/**
 * 消息构建工具类 - 上下文消息格式化核心组件
 * 
 * 负责将用户消息和上下文引用组合成 Claude 可以理解的格式。
 * 这个工具类是实现上下文管理功能的关键部分。
 * 
 * 主要功能：
 * - 将各种类型的上下文引用转换为 Markdown 格式
 * - 支持多种上下文类型（文件、网页、图片等）
 * - 生成统一的上下文展示格式
 * 
 * 上下文类型区分：
 * - TAG 类型：通过 "Add Context" 按钮添加的上下文
 * - 内联类型：通过 @ 符号直接在消息中引用的上下文
 * 
 * 注意：本工具类只处理 TAG 类型上下文，
 * 内联引用已经在用户消息中，不需要额外处理。
 */
object MessageBuilderUtils {
    
    /**
     * 构建包含上下文的完整消息
     * 
     * 这是消息构建的核心方法，将上下文引用和用户消息组合成
     * Claude 可以理解的格式。
     * 
     * 处理流程：
     * 1. 检查是否有上下文，无上下文直接返回原始消息
     * 2. 构建上下文引用区域，使用 Markdown 引用格式
     * 3. 根据上下文类型生成不同的显示格式
     * 4. 将上下文区域和用户消息合并
     * 
     * 上下文格式示例：
     * > **上下文资料**
     * > 
     * > - 📄 `/path/to/file.kt`
     * > - 🌐 https://example.com (页面标题)
     * > - 📁 `/folder/path` (10个文件)
     * 
     * 用户消息内容...
     * 
     * 注意事项：
     * - 只处理 TAG 类型上下文（Add Context 按钮添加的）
     * - @ 符号添加的上下文不会进入 contexts 列表，直接在 userMessage 中
     * - 使用 Markdown 引用格式，使上下文在 Claude 界面中更加突出
     * 
     * @param contexts 上下文引用列表（TAG 类型）
     * @param userMessage 用户消息（可能已包含内联引用）
     * @return 包含上下文的完整消息
     */
    fun buildFinalMessage(contexts: List<ContextReference>, userMessage: String): String {
        if (contexts.isEmpty()) {
            return userMessage
        }
        
        val contextSection = buildString {
            appendLine("> **上下文资料**")
            appendLine("> ")
            
            /**
             * 遍历所有上下文引用，根据类型生成对应的显示格式
             * 
             * 每种上下文类型都有特定的：
             * - 图标：用于快速识别类型
             * - 格式：适合该类型的信息展示
             * - 附加信息：如文件数量、错误标记等
             */
            contexts.forEach { context ->
                val contextLine = when (context) {
                    is ContextReference.FileReference -> {
                        // 文件引用：使用代码块格式显示路径
                        "> - 📄 `${context.path}`"
                    }
                    is ContextReference.WebReference -> {
                        // 网页引用：显示 URL 和可选的标题
                        val title = context.title?.let { " ($it)" } ?: ""
                        "> - 🌐 ${context.url}$title"
                    }
                    is ContextReference.FolderReference -> {
                        // 文件夹引用：显示路径和包含的文件数量
                        "> - 📁 `${context.path}` (${context.fileCount}个文件)"
                    }
                    is ContextReference.SymbolReference -> {
                        // 符号引用：显示符号名、类型和位置
                        "> - 🔗 `${context.name}` (${context.type}) - ${context.file}:${context.line}"
                    }
                    is ContextReference.TerminalReference -> {
                        // 终端引用：显示行数和错误标记
                        val errorFlag = if (context.isError) " ⚠️" else ""
                        "> - 💻 终端输出 (${context.lines}行)$errorFlag"
                    }
                    is ContextReference.ProblemsReference -> {
                        // 问题引用：显示问题数量和严重级别
                        val severityText = context.severity?.let { " [$it]" } ?: ""
                        "> - ⚠️ 问题报告 (${context.problems.size}个)$severityText"
                    }
                    is ContextReference.GitReference -> {
                        // Git 引用：显示 Git 操作类型
                        "> - 🔀 Git ${context.type}"
                    }
                    is ContextReference.ImageReference -> {
                        // 图片引用：显示文件名和大小（KB）
                        "> - 🖼 `${context.filename}` (${context.size / 1024}KB)"
                    }
                    is ContextReference.SelectionReference -> {
                        // 选中内容引用：不显示具体内容，只提示类型
                        "> - ✏️ 当前选择内容"
                    }
                    is ContextReference.WorkspaceReference -> {
                        // 工作区引用：标识整个工作区上下文
                        "> - 🏠 当前工作区"
                    }
                }
                appendLine(contextLine)
            }
            
            appendLine()
        }
        
        return contextSection + userMessage
    }
    
    /**
     * 构建包含上下文的消息 - 保留旧版本作为向后兼容
     * @deprecated 使用 buildFinalMessage 代替
     */
    @Deprecated("Use buildFinalMessage instead", ReplaceWith("buildFinalMessage(contexts, message)"))
    fun buildMessageWithContext(
        message: String,
        contexts: List<ContextReference>
    ): String {
        return buildFinalMessage(contexts, message)
    }
}