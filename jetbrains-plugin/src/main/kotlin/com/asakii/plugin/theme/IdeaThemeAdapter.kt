package com.asakii.plugin.theme

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager

/**
 * IDEA 主题适配器
 * 负责监听 IDEA 主题变化并通知前端
 *
 * 设计原则：不判断亮暗，直接使用 IDE 颜色值
 */
class IdeaThemeAdapter {

    companion object {
        private val logger = Logger.getInstance(IdeaThemeAdapter::class.java)

        /**
         * 获取当前 IDEA 主题名称
         */
        fun getCurrentThemeName(): String {
            return try {
                UIManager.getLookAndFeel().name
            } catch (e: Exception) {
                "Unknown"
            }
        }

        /**
         * 注册主题变化监听器
         * 主题变化时触发回调，回调参数为主题名称（而非亮暗判断）
         */
        fun registerThemeChangeListener(onChange: (Boolean) -> Unit) {
            try {
                val connection = ApplicationManager.getApplication().messageBus.connect()

                connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
                    val themeName = getCurrentThemeName()
                    logger.info("IDEA 主题已更改: $themeName")
                    // 回调参数保持兼容，但调用方应使用完整颜色值而非亮暗判断
                    onChange(true) // 仅触发刷新，不传递亮暗信息
                })

                logger.info("已注册 IDEA 主题变化监听器")
            } catch (e: Exception) {
                logger.error("注册主题监听器失败", e)
            }
        }
    }
}