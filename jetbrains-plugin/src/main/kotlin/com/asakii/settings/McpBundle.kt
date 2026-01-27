package com.asakii.settings

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_PATH = "messages.McpBundle"

/**
 * MCP 国际化资源包
 *
 * 用于加载 UI 短文本（描述、警告等）
 * 使用 DynamicBundle 跟随 IDEA 语言设置
 */
object McpBundle : DynamicBundle(BUNDLE_PATH) {

    /**
     * 获取本地化消息
     *
     * @param key 资源键
     * @param params 参数
     * @return 本地化字符串
     */
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE_PATH) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}

/**
 * MCP 提示词加载器
 *
 * 用于加载长提示词（.md 文件）
 * 跟随 IDEA 语言设置
 */
object McpInstructions {

    /**
     * 根据 IDEA 语言设置获取语言目录
     */
    private fun getLanguageDir(): String {
        val locale = DynamicBundle.getLocale()
        return when (locale.language) {
            "zh" -> "zh_CN"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> "en"
        }
    }

    /**
     * 加载指定 MCP 的提示词
     *
     * @param name MCP 名称（如 "jetbrains-git", "jetbrains-terminal"）
     * @return 提示词内容
     */
    fun load(name: String): String {
        val lang = getLanguageDir()
        val path = "/mcp-instructions/$lang/$name.md"

        return try {
            McpInstructions::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
                ?: loadFallback(name)
        } catch (e: Exception) {
            loadFallback(name)
        }
    }

    /**
     * 回退到英文版本
     */
    private fun loadFallback(name: String): String {
        val fallbackPath = "/mcp-instructions/en/$name.md"
        return try {
            McpInstructions::class.java.getResourceAsStream(fallbackPath)?.bufferedReader()?.readText()
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // 预定义的 MCP 名称常量
    const val USER_INTERACTION = "user-interaction"
    const val IDE_LSP = "ide-lsp"
    const val IDE_FILE = "ide-file"
    const val IDE_TERMINAL = "ide-terminal"
    const val IDE_GIT = "ide-git"
    const val CONTEXT7 = "context7"
    
    // 向后兼容的别名（已废弃，将在后续版本移除）
    @Deprecated("Use IDE_LSP instead", ReplaceWith("IDE_LSP"))
    const val JETBRAINS_LSP = IDE_LSP
    @Deprecated("Use IDE_FILE instead", ReplaceWith("IDE_FILE"))
    const val JETBRAINS_FILE = IDE_FILE
    @Deprecated("Use IDE_TERMINAL instead", ReplaceWith("IDE_TERMINAL"))
    const val JETBRAINS_TERMINAL = IDE_TERMINAL
    @Deprecated("Use IDE_GIT instead", ReplaceWith("IDE_GIT"))
    const val JETBRAINS_GIT = IDE_GIT
}
