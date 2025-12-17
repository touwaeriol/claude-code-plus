package com.asakii.plugin.services

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import java.net.ServerSocket

/**
 * JCEF 远程调试端口初始化器
 *
 * 在应用初始化时设置随机调试端口，确保在 JCEF 初始化之前完成。
 * 这解决了 Windows 上 JCEF out-of-process 模式下 DevTools 窗口空白的问题。
 */
class JcefDebugPortInitializer : ApplicationInitializedListener {

    companion object {
        private val logger = Logger.getInstance(JcefDebugPortInitializer::class.java)
        private const val REGISTRY_KEY = "ide.browser.jcef.debug.port"

        @Volatile
        private var initializedPort: Int? = null

        /**
         * 获取已初始化的调试端口
         */
        fun getInitializedPort(): Int? = initializedPort
    }

    override suspend fun execute() {
        try {
            // 使用反射调用 Registry API 避免 Kotlin 编译器生成 Companion 引用
            // 这样可以兼容 2024.2.6 等旧版本（没有 Registry.Companion）
            val registryClass = Class.forName("com.intellij.openapi.util.registry.Registry")

            // 检查当前 Registry 设置
            val currentPort = try {
                val intValueMethod = registryClass.getDeclaredMethod("intValue", String::class.java, Int::class.javaPrimitiveType)
                intValueMethod.invoke(null, REGISTRY_KEY, -1) as Int
            } catch (_: Exception) {
                -1
            }

            // 如果端口是 -1（默认值），则设置一个随机端口
            if (currentPort == -1) {
                val randomPort = findAvailablePort()
                if (randomPort != null) {
                    val success = try {
                        val getMethod = registryClass.getDeclaredMethod("get", String::class.java)
                        val registryValue = getMethod.invoke(null, REGISTRY_KEY)

                        val setValueMethod = registryValue.javaClass.getDeclaredMethod("setValue", Int::class.javaPrimitiveType)
                        setValueMethod.invoke(registryValue, randomPort)
                        true
                    } catch (e: Exception) {
                        logger.warn("⚠️ Could not set JCEF debug port via reflection: ${e.message}")
                        false
                    }

                    initializedPort = randomPort
                    if (success) {
                        logger.info("✅ JCEF debug port initialized to: $randomPort")
                    } else {
                        logger.info("ℹ️ JCEF debug port available: $randomPort (manual setup required)")
                        logger.info("ℹ️ To enable JCEF remote debugging, set Registry key '$REGISTRY_KEY' to $randomPort")
                    }
                } else {
                    logger.warn("⚠️ Could not find available port for JCEF debugging")
                }
            } else {
                initializedPort = currentPort
                logger.info("ℹ️ JCEF debug port already set to: $currentPort")
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to initialize JCEF debug port: ${e.message}")
        }
    }

    /**
     * 查找可用端口
     */
    private fun findAvailablePort(): Int? {
        return try {
            ServerSocket(0).use { socket ->
                socket.localPort
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to find available port: ${e.message}")
            null
        }
    }
}
