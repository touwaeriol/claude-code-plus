package com.claudecodeplus.plugin.ui.indicators

import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 流式状态指示器（完整版）
 * 
 * 对应 frontend/src/components/chat/StreamingStatusIndicator.vue
 * 
 * 功能：
 * - 显示"Claude 正在思考..."
 * - 实时显示上行/下行 tokens
 * - 显示耗时
 * - 旋转的加载动画
 */
class StreamingIndicator(
    private val isStreamingFlow: StateFlow<Boolean>,
    private val inputTokensFlow: StateFlow<Int>,
    private val outputTokensFlow: StateFlow<Int>
) {
    
    private val statusLabel = JLabel()
    private val tokenStatsLabel = JLabel()
    private val timeLabel = JLabel()
    private val spinnerLabel = JLabel("⟳")
    private val panel = JPanel()
    
    private var startTime = 0L
    private var timer: Timer? = null
    private var spinnerTimer: Timer? = null
    
    init {
        setupUI()
        setupReactiveBindings()
    }
    
    private fun setupUI() {
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.background = Color(255, 255, 255, 240)  // 半透明白色
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xE0E0E0), 1),
            EmptyBorder(JBUI.insets(8, 16))
        )
        panel.isVisible = false  // 默认隐藏
        
        // 旋转动画的 spinner
        spinnerLabel.foreground = Color(0x2196F3)
        spinnerLabel.font = spinnerLabel.font.deriveFont(Font.BOLD, 16f)
        panel.add(spinnerLabel)
        panel.add(Box.createHorizontalStrut(8))
        
        // 状态文本
        statusLabel.font = statusLabel.font.deriveFont(Font.PLAIN, 13f)
        statusLabel.foreground = Color(0x333333)
        panel.add(statusLabel)
        
        panel.add(Box.createHorizontalStrut(16))
        
        // 分隔线
        val separator1 = createSeparator()
        panel.add(separator1)
        panel.add(Box.createHorizontalStrut(16))
        
        // Token 统计
        tokenStatsLabel.font = Font("Monospaced", Font.PLAIN, 12)
        tokenStatsLabel.foreground = Color(0x666666)
        panel.add(tokenStatsLabel)
        
        panel.add(Box.createHorizontalStrut(16))
        
        // 分隔线
        val separator2 = createSeparator()
        panel.add(separator2)
        panel.add(Box.createHorizontalStrut(16))
        
        // 耗时
        timeLabel.font = Font("Monospaced", Font.PLAIN, 12)
        timeLabel.foreground = Color(0x888888)
        panel.add(timeLabel)
    }
    
    private fun createSeparator(): JComponent {
        val sep = JPanel()
        sep.background = Color(0xE0E0E0)
        sep.preferredSize = Dimension(1, 16)
        sep.maximumSize = Dimension(1, 16)
        return sep
    }
    
    private fun setupReactiveBindings() {
        // 监听流式状态变化
        isStreamingFlow.onEach { isStreaming ->
            SwingUtilities.invokeLater {
                handleStreamingStateChange(isStreaming)
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))
        
        // 监听 token 变化
        inputTokensFlow.onEach {
            SwingUtilities.invokeLater {
                updateTokenStats()
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))
        
        outputTokensFlow.onEach {
            SwingUtilities.invokeLater {
                updateTokenStats()
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))
    }
    
    private fun handleStreamingStateChange(isStreaming: Boolean) {
        panel.isVisible = isStreaming
        
        if (isStreaming) {
            // 开始生成
            startTime = System.currentTimeMillis()
            statusLabel.text = "Claude 正在思考..."
            
            // 启动耗时计时器
            timer = Timer(100) {
                updateElapsedTime()
            }
            timer?.start()
            
            // 启动 spinner 旋转动画
            startSpinnerAnimation()
            
        } else {
            // 停止生成
            timer?.stop()
            timer = null
            spinnerTimer?.stop()
            spinnerTimer = null
        }
    }
    
    private fun updateTokenStats() {
        val inputTokens = inputTokensFlow.value
        val outputTokens = outputTokensFlow.value
        val total = inputTokens + outputTokens
        
        tokenStatsLabel.text = "📊 Tokens: ${formatTokens(inputTokens)} in · ${formatTokens(outputTokens)} out · ${formatTokens(total)} total"
    }
    
    private fun updateElapsedTime() {
        val elapsed = System.currentTimeMillis() - startTime
        timeLabel.text = "⏱ ${formatTime(elapsed)}"
    }
    
    private fun startSpinnerAnimation() {
        // 使用 Timer 实现旋转效果（简化版，使用 Unicode 旋转字符）
        val spinChars = arrayOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
        var index = 0
        
        spinnerTimer = Timer(80) {
            spinnerLabel.text = spinChars[index]
            index = (index + 1) % spinChars.size
        }
        spinnerTimer?.start()
    }
    
    fun getPanel(): JComponent = panel
    
    private fun formatTokens(count: Int): String {
        if (count >= 1000) {
            return "${(count / 1000.0).format(1)}k"
        }
        return count.toString()
    }
    
    private fun formatTime(ms: Long): String {
        if (ms < 1000) {
            return "${ms}ms"
        }
        val seconds = (ms / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        
        return if (minutes > 0) {
            "${minutes}m ${remainingSeconds}s"
        } else {
            "${seconds}s"
        }
    }
    
    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}

