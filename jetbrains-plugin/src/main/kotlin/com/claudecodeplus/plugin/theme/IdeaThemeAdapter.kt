package com.claudecodeplus.plugin.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager

/**
 * IDEA 主题适配器
 * 负责监听 IDEA 主题变化并同步到 Compose UI
 */
class IdeaThemeAdapter {
    
    companion object {
        private val logger = Logger.getInstance(IdeaThemeAdapter::class.java)
        
        /**
         * 判断当前 IDEA 是否使用暗色主题
         */
        fun isDarkTheme(): Boolean {
            return try {
                // 方法1：使用 UIUtil 工具类（推荐）
                UIUtil.isUnderDarcula()
            } catch (e: Exception) {
                try {
                    // 方法2：检查 Look and Feel 名称
                    val laf = UIManager.getLookAndFeel()
                    laf.name.contains("Darcula", ignoreCase = true) || 
                    laf.name.contains("Dark", ignoreCase = true)
                } catch (ex: Exception) {
                    logger.warn("无法检测主题，默认使用亮色主题", ex)
                    false
                }
            }
        }
        
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
         */
        fun registerThemeChangeListener(onChange: (Boolean) -> Unit) {
            try {
                val connection = ApplicationManager.getApplication().messageBus.connect()
                
                connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
                    val isDark = isDarkTheme()
                    logger.info("IDEA 主题已更改，当前是否为暗色主题: $isDark")
                    onChange(isDark)
                })
                
                logger.info("已注册 IDEA 主题变化监听器")
            } catch (e: Exception) {
                logger.error("注册主题监听器失败", e)
            }
        }
        
        /**
         * 获取主题相关的颜色
         */
        fun getThemeColors(): ThemeColors {
            return if (isDarkTheme()) {
                DarkThemeColors()
            } else {
                LightThemeColors()
            }
        }
    }
}

/**
 * 主题颜色接口
 */
interface ThemeColors {
    val background: Int
    val foreground: Int
    val primaryColor: Int
    val secondaryColor: Int
    val borderColor: Int
    val selectionBackground: Int
    val selectionForeground: Int
}

/**
 * 暗色主题颜色
 */
class DarkThemeColors : ThemeColors {
    override val background = 0xFF2B2B2B.toInt()
    override val foreground = 0xFFBBBBBB.toInt()
    override val primaryColor = 0xFF4C9AFF.toInt()
    override val secondaryColor = 0xFF6B7280.toInt()
    override val borderColor = 0xFF3C3F41.toInt()
    override val selectionBackground = 0xFF214283.toInt()
    override val selectionForeground = 0xFFFFFFFF.toInt()
}

/**
 * 亮色主题颜色
 */
class LightThemeColors : ThemeColors {
    override val background = 0xFFFFFFFF.toInt()
    override val foreground = 0xFF000000.toInt()
    override val primaryColor = 0xFF0066CC.toInt()
    override val secondaryColor = 0xFF6B7280.toInt()
    override val borderColor = 0xFFD1D5DB.toInt()
    override val selectionBackground = 0xFF3D7FC7.toInt()
    override val selectionForeground = 0xFFFFFFFF.toInt()
}