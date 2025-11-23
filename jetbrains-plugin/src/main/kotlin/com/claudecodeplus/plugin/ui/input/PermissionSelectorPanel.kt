package com.claudecodeplus.plugin.ui.input

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * 权限模式选择器组件
 * 
 * 对应 frontend/src/components/chat/ChatInput.vue 第165-189行
 */
class PermissionSelectorPanel {
    
    private val permissionComboBox: ComboBox<PermissionOption>
    private var onPermissionChangeCallback: ((String) -> Unit)? = null
    
    init {
        val permissions = arrayOf(
            PermissionOption("DEFAULT", "默认"),
            PermissionOption("ACCEPT_EDITS", "接受编辑"),
            PermissionOption("BYPASS", "绕过权限"),
            PermissionOption("PLAN", "计划模式")
        )
        
        permissionComboBox = ComboBox(permissions)
        permissionComboBox.selectedIndex = 2  // 默认选择"绕过权限"
    }
    
    fun create(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        panel.border = EmptyBorder(JBUI.insets(0, 4))
        
        val label = JLabel("权限:")
        label.border = EmptyBorder(JBUI.insets(0, 0, 0, 4))
        panel.add(label)
        
        permissionComboBox.preferredSize = Dimension(120, 28)
        permissionComboBox.addActionListener {
            val selected = permissionComboBox.selectedItem as? PermissionOption
            selected?.let {
                onPermissionChangeCallback?.invoke(it.value)
            }
        }
        
        panel.add(permissionComboBox)
        
        return panel
    }
    
    fun setEnabled(enabled: Boolean) {
        permissionComboBox.isEnabled = enabled
    }
    
    fun getSelectedPermission(): String {
        return (permissionComboBox.selectedItem as? PermissionOption)?.value ?: "BYPASS"
    }
    
    fun onPermissionChange(callback: (String) -> Unit) {
        onPermissionChangeCallback = callback
    }
    
    data class PermissionOption(
        val value: String,
        val label: String
    ) {
        override fun toString(): String = label
    }
}


