package com.claudecodeplus.ui.theme

import com.claudecodeplus.core.logging.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UI 尺寸常量定义
 * 
 * 统一管理插件UI的各种尺寸常量，包括动态最小宽度、间距、组件大小等
 */
object Dimensions {
    
    /**
     * 窗口和容器最小宽度管理
     */
    object MinWidth {
        /** 存储插件初始打开时的默认宽度 */
        private var _defaultWidth by mutableStateOf<Dp?>(null)
        
        /** 获取或设置默认宽度 */
        var defaultWidth: Dp?
            get() = _defaultWidth
            set(value) {
                if (_defaultWidth == null && value != null) {
                    _defaultWidth = value
                }
            }
        
        /** 主窗口最小宽度 - 使用插件默认宽度，回退值为 400dp */
        val MAIN_WINDOW: Dp
            get() = _defaultWidth ?: 400.dp
        
        /** 输入框区域最小宽度 - 比主窗口略小 */
        val INPUT_AREA: Dp 
            get() = (_defaultWidth?.let { it - 40.dp } ?: 360.dp)
        
        /** 底部工具栏最小宽度 - 与输入框相同 */
        val BOTTOM_TOOLBAR: Dp
            get() = INPUT_AREA
        
        /** 模型选择器最小宽度 */
        val MODEL_SELECTOR = 80.dp
        
        /** 权限选择器最小宽度 */
        val PERMISSION_SELECTOR = 90.dp
        
        /**
         * 初始化默认宽度
         * 在首次渲染时调用，记录插件打开时的实际宽度
         */
        fun initializeDefaultWidth(width: Dp) {
            if (_defaultWidth == null) {
                _defaultWidth = width
    logD("[Dimensions] 初始化插件默认宽度: $width")
            }
        }
    }
    
    /**
     * 响应式断点
     */
    object Breakpoints {
        /** 宽屏模式：显示所有控件 */
        val WIDE_SCREEN = 600.dp
        
        /** 中等宽度：紧凑布局 */
        val MEDIUM_SCREEN = 400.dp
        
        /** 最小模式：只显示必要控件 */
        val COMPACT_SCREEN = 300.dp
    }
    
    /**
     * 组件间距
     */
    object Spacing {
        /** 极小间距 */
        val TINY = 2.dp
        
        /** 小间距 */
        val SMALL = 4.dp
        
        /** 正常间距 */
        val MEDIUM = 8.dp
        
        /** 大间距 */
        val LARGE = 12.dp
        
        /** 超大间距 */
        val EXTRA_LARGE = 16.dp
    }
    
    /**
     * 组件高度
     */
    object Height {
        /** 标准按钮高度 */
        val BUTTON = 32.dp
        
        /** 输入框高度 */
        val INPUT_FIELD = 36.dp
        
        /** 工具栏高度 */
        val TOOLBAR = 44.dp
        
        /** 上下文标签高度 */
        val CONTEXT_TAG = 24.dp
    }
}
