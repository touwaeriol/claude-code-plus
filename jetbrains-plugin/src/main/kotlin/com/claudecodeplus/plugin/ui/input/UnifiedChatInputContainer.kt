package com.claudecodeplus.plugin.ui.input

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder

/**
 * 圆角边框（12px圆角）
 */
private class RoundedBorder(
    private val color: Color,
    private val thickness: Int,
    private val radius: Int
) : AbstractBorder() {
    
    override fun paintBorder(
        c: Component,
        g: Graphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = color
        g2d.stroke = BasicStroke(thickness.toFloat())
        
        // 绘制圆角矩形
        val arc = radius * 2
        g2d.drawRoundRect(
            x + thickness / 2,
            y + thickness / 2,
            width - thickness,
            height - thickness,
            arc,
            arc
        )
    }
    
    override fun getBorderInsets(c: Component): Insets {
        return Insets(radius, radius, radius, radius)
    }
}

/**
 * 统一聊天输入容器
 * 
 * 完全复刻 frontend/src/components/chat/ChatInput.vue 的 unified-chat-input-container 样式
 * 
 * 样式特性：
 * - 圆角12px
 * - 边框1.5px
 * - 焦点状态：蓝色边框 + 阴影效果
 * - 背景色：var(--ide-panel-background, #f6f8fa)
 */
class UnifiedChatInputContainer {
    
    private val container = JPanel(BorderLayout())
    private var isFocused = false
    
    // 颜色定义（参考Vue样式）
    private val borderColor = JBColor(Color(0xE1E4E8), Color(0x3C3C3C))
    private val focusedBorderColor = JBColor(Color(0x0366D6), Color(0x0366D6))
    private val backgroundColor = JBColor(Color(0xF6F8FA), Color(0x2B2B2B))
    private val shadowColor = Color(0x0366D6, true)  // 带透明度的蓝色
    
    init {
        setupContainer()
    }
    
    private fun setupContainer() {
        // 设置背景色
        container.background = backgroundColor
        
        // 设置初始边框（圆角12px，边框1.5px）
        updateBorder()
    }
    
    /**
     * 创建边框（支持焦点状态和圆角）
     */
    private fun createBorder(focused: Boolean): CompoundBorder {
        val borderColor = if (focused) focusedBorderColor else this.borderColor
        val thickness = 2  // 1.5px ≈ 2px
        val radius = 12  // 12px圆角
        
        // 圆角边框
        val roundedBorder = RoundedBorder(borderColor, thickness, radius)
        
        // 焦点状态下的阴影效果（使用额外的边框模拟）
        val shadowBorder = if (focused) {
            // 创建外边框模拟阴影效果
            EmptyBorder(JBUI.insets(3))  // 3px 阴影
        } else {
            EmptyBorder(JBUI.insets(0))
        }
        
        return CompoundBorder(shadowBorder, roundedBorder)
    }
    
    /**
     * 更新边框（根据焦点状态）
     */
    private fun updateBorder() {
        container.border = createBorder(isFocused)
        container.repaint()
    }
    
    /**
     * 添加焦点监听器到组件
     */
    fun addFocusListener(component: JComponent) {
        component.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent) {
                isFocused = true
                updateBorder()
            }
            
            override fun focusLost(e: FocusEvent) {
                isFocused = false
                updateBorder()
            }
        })
    }
    
    /**
     * 获取容器面板
     */
    fun getContainer(): JPanel = container
    
    /**
     * 设置焦点状态（外部调用）
     */
    fun setFocused(focused: Boolean) {
        if (isFocused != focused) {
            isFocused = focused
            updateBorder()
        }
    }
    
    /**
     * 获取焦点状态
     */
    fun isFocused(): Boolean = isFocused
}

