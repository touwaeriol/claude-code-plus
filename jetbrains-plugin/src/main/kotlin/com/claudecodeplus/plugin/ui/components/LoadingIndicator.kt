package com.claudecodeplus.plugin.ui.components

import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JPanel
import javax.swing.Timer

/**
 * 加载指示器组件
 * 
 * 显示动画旋转器、进度条和"思考中"状态
 */
class LoadingIndicator : JPanel() {
    
    private var angle = 0.0
    private val timer: Timer
    private var loadingText = "加载中..."
    
    init {
        preferredSize = Dimension(60, 60)
        isOpaque = false
        
        // 动画计时器
        timer = Timer(50) {
            angle += 0.1
            if (angle > 2 * Math.PI) {
                angle = 0.0
            }
            repaint()
        }
    }
    
    /**
     * 设置加载文本
     */
    fun setLoadingText(text: String) {
        loadingText = text
        repaint()
    }
    
    /**
     * 开始动画
     */
    fun start() {
        isVisible = true
        timer.start()
    }
    
    /**
     * 停止动画
     */
    fun stop() {
        timer.stop()
        isVisible = false
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        val centerX = width / 2
        val centerY = height / 2
        val radius = 20
        
        // 绘制旋转的圆点
        for (i in 0 until 8) {
            val dotAngle = angle + i * Math.PI / 4
            val x = centerX + (radius * Math.cos(dotAngle)).toInt()
            val y = centerY + (radius * Math.sin(dotAngle)).toInt()
            
            val alpha = (255 * (1 - i / 8.0)).toInt()
            g2d.color = Color(100, 150, 255, alpha)
            g2d.fillOval(x - 3, y - 3, 6, 6)
        }
        
        // 绘制加载文本
        g2d.color = Color.GRAY
        g2d.font = g2d.font.deriveFont(12f)
        val fm = g2d.fontMetrics
        val textWidth = fm.stringWidth(loadingText)
        g2d.drawString(loadingText, centerX - textWidth / 2, centerY + radius + 20)
    }
    
    companion object {
        /**
         * 创建思考中状态的加载指示器
         */
        fun createThinkingIndicator(): LoadingIndicator {
            val indicator = LoadingIndicator()
            indicator.setLoadingText("思考中...")
            return indicator
        }
        
        /**
         * 创建流式输出状态的加载指示器
         */
        fun createStreamingIndicator(): LoadingIndicator {
            val indicator = LoadingIndicator()
            indicator.setLoadingText("正在生成...")
            return indicator
        }
        
        /**
         * 创建工具调用状态的加载指示器
         */
        fun createToolCallIndicator(): LoadingIndicator {
            val indicator = LoadingIndicator()
            indicator.setLoadingText("执行工具...")
            return indicator
        }
    }
}

/**
 * 进度条组件
 */
class ProgressIndicator : JPanel() {
    
    private var progress = 0.0 // 0.0 to 1.0
    private var progressText = ""
    
    init {
        preferredSize = Dimension(300, 30)
        border = JBUI.Borders.empty(5)
    }
    
    /**
     * 设置进度（0.0 到 1.0）
     */
    fun setProgress(value: Double) {
        progress = value.coerceIn(0.0, 1.0)
        repaint()
    }
    
    /**
     * 设置进度文本
     */
    fun setProgressText(text: String) {
        progressText = text
        repaint()
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        val padding = 5
        val barWidth = width - 2 * padding
        val barHeight = 20
        val barY = (height - barHeight) / 2
        
        // 绘制背景
        g2d.color = Color(220, 220, 220)
        g2d.fillRoundRect(padding, barY, barWidth, barHeight, 10, 10)
        
        // 绘制进度
        val progressWidth = (barWidth * progress).toInt()
        g2d.color = Color(100, 150, 255)
        g2d.fillRoundRect(padding, barY, progressWidth, barHeight, 10, 10)
        
        // 绘制边框
        g2d.color = Color(180, 180, 180)
        g2d.drawRoundRect(padding, barY, barWidth, barHeight, 10, 10)
        
        // 绘制文本
        if (progressText.isNotEmpty()) {
            g2d.color = Color.DARK_GRAY
            g2d.font = g2d.font.deriveFont(11f)
            val fm = g2d.fontMetrics
            val textWidth = fm.stringWidth(progressText)
            val textX = (width - textWidth) / 2
            val textY = barY + barHeight + fm.height
            g2d.drawString(progressText, textX, textY)
        }
    }
}


