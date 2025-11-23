package com.claudecodeplus.plugin.ui.input

import com.claudecodeplus.plugin.types.ContextReference
import com.claudecodeplus.plugin.types.ContextType
import com.claudecodeplus.plugin.types.ContextDisplayType

/**
 * 上下文管理器
 * 
 * 对应 frontend/src/components/chat/ChatInput.vue 的上下文管理逻辑
 */
class ContextManager {
    
    private val contexts = mutableListOf<ContextReference>()
    private var onContextsChangedCallback: ((List<ContextReference>) -> Unit)? = null
    
    /**
     * 添加文件上下文
     */
    fun addFileContext(filePath: String) {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val context = ContextReference(
            type = ContextType.FILE,
            uri = filePath,
            displayType = ContextDisplayType.TAG,
            path = filePath,
            fullPath = filePath,
            name = fileName
        )
        
        // 避免重复
        if (contexts.none { it.uri == filePath }) {
            contexts.add(context)
            notifyContextsChanged()
        }
    }
    
    /**
     * 添加文件夹上下文
     */
    fun addFolderContext(folderPath: String) {
        val folderName = folderPath.substringAfterLast('/').substringAfterLast('\\')
        val context = ContextReference(
            type = ContextType.FOLDER,
            uri = folderPath,
            displayType = ContextDisplayType.TAG,
            path = folderPath,
            name = folderName
        )
        
        if (contexts.none { it.uri == folderPath }) {
            contexts.add(context)
            notifyContextsChanged()
        }
    }
    
    /**
     * 添加图片上下文
     */
    fun addImageContext(imagePath: String, base64Data: String? = null) {
        val imageName = imagePath.substringAfterLast('/').substringAfterLast('\\')
        val context = ContextReference(
            type = ContextType.IMAGE,
            uri = imagePath,
            displayType = ContextDisplayType.TAG,
            path = imagePath,
            name = imageName,
            base64Data = base64Data
        )
        
        if (contexts.none { it.uri == imagePath }) {
            contexts.add(context)
            notifyContextsChanged()
        }
    }
    
    /**
     * 添加 Web 上下文
     */
    fun addWebContext(url: String, title: String? = null) {
        val context = ContextReference(
            type = ContextType.WEB,
            uri = url,
            displayType = ContextDisplayType.TAG,
            url = url,
            title = title
        )
        
        if (contexts.none { it.uri == url }) {
            contexts.add(context)
            notifyContextsChanged()
        }
    }
    
    /**
     * 移除上下文
     */
    fun removeContext(context: ContextReference) {
        contexts.remove(context)
        notifyContextsChanged()
    }
    
    /**
     * 清空所有上下文
     */
    fun clearContexts() {
        contexts.clear()
        notifyContextsChanged()
    }
    
    /**
     * 获取所有上下文
     */
    fun getContexts(): List<ContextReference> {
        return contexts.toList()
    }
    
    /**
     * 注册上下文变化回调
     */
    fun onContextsChanged(callback: (List<ContextReference>) -> Unit) {
        onContextsChangedCallback = callback
    }
    
    private fun notifyContextsChanged() {
        onContextsChangedCallback?.invoke(contexts.toList())
    }
}


