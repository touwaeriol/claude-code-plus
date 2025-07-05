package com.claudecodeplus.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.diagnostic.Logger
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * 使用 MethodHandles 安全地设置 ComposePanel 内容
 * 兼容不同版本的 ComposePanel.setContent 方法签名
 */
object ComposeContentSetter {
    private val logger = Logger.getInstance(ComposeContentSetter::class.java)
    private val lookup = MethodHandles.lookup()
    
    /**
     * 设置 ComposePanel 的内容
     * 自动处理不同版本的方法签名
     */
    fun setContent(panel: ComposePanel, content: @Composable () -> Unit): Boolean {
        try {
            // 首先记录 ComposePanel 的类信息
            logger.info("ComposePanel class: ${panel.javaClass.name}")
            logger.info("ComposePanel classLoader: ${panel.javaClass.classLoader}")
            
            // 获取所有方法并记录详细信息
            val allMethods = panel.javaClass.methods
            logger.info("Total methods in ComposePanel: ${allMethods.size}")
            
            // 查找所有 setContent 方法
            val methods = allMethods.filter { it.name == "setContent" }
            logger.info("Found ${methods.size} setContent methods on ComposePanel")
            
            // 记录每个 setContent 方法的详细签名
            methods.forEach { method ->
                logger.info("setContent method: ${method}")
                method.parameterTypes.forEachIndexed { index, type ->
                    logger.info("  Parameter $index: ${type.name} (classLoader: ${type.classLoader})")
                }
            }
            
            // 记录 content 参数的类型信息
            logger.info("Content parameter class: ${content.javaClass.name}")
            logger.info("Content parameter interfaces: ${content.javaClass.interfaces.joinToString { it.name }}")
            
            // 尝试每个方法
            for (method in methods) {
                try {
                    val paramTypes = method.parameterTypes
                    logger.info("Trying setContent with ${paramTypes.size} parameters: ${paramTypes.joinToString { it.name }}")
                    
                    when (paramTypes.size) {
                        1 -> {
                            // 单参数版本：setContent(content: Function0<Unit>)
                            logger.info("Attempting single parameter invocation")
                            method.invoke(panel, content)
                            logger.info("Successfully called setContent with 1 parameter")
                            return true
                        }
                        2 -> {
                            // 双参数版本：setContent(parent: CompositionContext?, content: Function0<Unit>)
                            logger.info("Attempting two parameter invocation with null parent")
                            method.invoke(panel, null, content)
                            logger.info("Successfully called setContent with 2 parameters (parent=null)")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to invoke setContent method: ${e.javaClass.name}: ${e.message}", e)
                    if (e.cause != null) {
                        logger.warn("Caused by: ${e.cause!!.javaClass.name}: ${e.cause!!.message}")
                    }
                }
            }
            
            // 如果反射失败，记录错误
            logger.error("All reflection attempts failed to set ComposePanel content")
            return false
            
        } catch (e: Exception) {
            logger.error("Failed to set ComposePanel content", e)
            return false
        }
    }
    
    /**
     * 检查类型是否可能是 Composable 函数
     */
    private fun isComposableFunction(type: Class<*>): Boolean {
        // Function0, Function2 等都继承自 Function
        return type.name.contains("Function") || 
               type.name.contains("Composable") ||
               kotlin.jvm.functions.Function0::class.java.isAssignableFrom(type)
    }
}