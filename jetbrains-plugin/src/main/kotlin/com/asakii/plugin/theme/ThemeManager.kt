package com.asakii.plugin.theme

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * 主题管理器
 *
 * 监听 IDE 主题变更并通知组件重绘
 * 设计原则：不判断亮暗，直接使用 IDE 颜色值
 */
class ThemeManager(private val project: Project) : Disposable {

    private val themeChangeListeners = mutableListOf<() -> Unit>()

    init {
        // 注册主题变更监听器
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            notifyThemeChanged()
        })
    }

    /**
     * 注册主题变更监听器
     */
    fun addThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.add(listener)
    }

    /**
     * 移除主题变更监听器
     */
    fun removeThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.remove(listener)
    }

    /**
     * 通知所有监听器主题已变更
     */
    private fun notifyThemeChanged() {
        themeChangeListeners.forEach { it() }
    }

    /**
     * 获取当前主题颜色（直接从 IDE 读取，不做亮暗判断）
     */
    fun getThemeColors(): ThemeColors {
        return ThemeColors(
            background = colorToHex(UIUtil.getPanelBackground()),
            foreground = colorToHex(UIUtil.getLabelForeground()),
            panelBackground = colorToHex(UIUtil.getPanelBackground()),
            borderColor = colorToHex(JBColor.border()),
            highlightColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor.BLUE))
        )
    }

    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }

    override fun dispose() {
        themeChangeListeners.clear()
    }

    /**
     * 主题颜色定义（动态获取，不再有预定义常量）
     */
    data class ThemeColors(
        val background: String,
        val foreground: String,
        val panelBackground: String,
        val borderColor: String,
        val highlightColor: String
    )

    companion object {
        private val instances = mutableMapOf<Project, ThemeManager>()

        /**
         * 获取项目的主题管理器实例
         */
        fun getInstance(project: Project): ThemeManager {
            return instances.getOrPut(project) {
                ThemeManager(project)
            }
        }
    }
}


