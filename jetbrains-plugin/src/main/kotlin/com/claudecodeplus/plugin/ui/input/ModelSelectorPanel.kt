package com.claudecodeplus.plugin.ui.input

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * 模型选择器组件
 * 
 * 对应 frontend/src/components/chat/ChatInput.vue 第110-163行
 */
class ModelSelectorPanel {
    
    private val modelComboBox: ComboBox<ModelOption>
    private var onModelChangeCallback: ((String) -> Unit)? = null
    
    init {
        val models = arrayOf(
            ModelOption("DEFAULT", "默认"),
            ModelOption("SONNET", "Sonnet"),
            ModelOption("OPUS", "Opus"),
            ModelOption("HAIKU", "Haiku"),
            ModelOption("OPUS_PLAN", "Opus Plan")
        )
        
        modelComboBox = ComboBox(models)
        modelComboBox.selectedIndex = 1  // 默认选择 Sonnet
    }
    
    fun create(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        panel.border = EmptyBorder(JBUI.insets(0, 4))
        
        val label = JLabel("模型:")
        label.border = EmptyBorder(JBUI.insets(0, 0, 0, 4))
        panel.add(label)
        
        modelComboBox.preferredSize = Dimension(120, 28)
        modelComboBox.addActionListener {
            val selected = modelComboBox.selectedItem as? ModelOption
            selected?.let {
                onModelChangeCallback?.invoke(it.value)
            }
        }
        
        panel.add(modelComboBox)
        
        return panel
    }
    
    fun setEnabled(enabled: Boolean) {
        modelComboBox.isEnabled = enabled
    }
    
    fun getSelectedModel(): String {
        return (modelComboBox.selectedItem as? ModelOption)?.value ?: "SONNET"
    }
    
    fun setSelectedModel(model: String) {
        for (i in 0 until modelComboBox.itemCount) {
            val item = modelComboBox.getItemAt(i)
            if (item.value == model) {
                modelComboBox.selectedIndex = i
                break
            }
        }
    }
    
    fun onModelChange(callback: (String) -> Unit) {
        onModelChangeCallback = callback
    }
    
    /**
     * 模型选项
     */
    data class ModelOption(
        val value: String,
        val label: String
    ) {
        override fun toString(): String = label
    }
}


