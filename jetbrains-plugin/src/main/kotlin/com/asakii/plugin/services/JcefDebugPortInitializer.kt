package com.asakii.plugin.services

import com.intellij.openapi.diagnostic.Logger
import java.net.ServerSocket

/**
 * JCEF 远程调试端口初始化器（懒加载版本）
 *
 * 支持动态插件：不再使用 ApplicationInitializedListener，
 * 改为在首次需要时懒加载初始化。
 */
object JcefDebugPortInitializer {
    private val logger = Logger.getInstance(JcefDebugPortInitializer::class.java)
    private const val REGISTRY_KEY = "ide.browser.jcef.debug.port"

    @Volatile
    private var initialized = false

    @Volatile
    private var configuredPort: Int? = null

    /**
     * 懒加载初始化 - 在首次需要时调用
     * @return 配置的端口号，如果初始化失败则返回 null
     */
    @Synchronized
    fun ensureInitialized(): Int? {
        if (initialized) return configuredPort
        initialized = true

        try {
            val currentPort = getConfiguredPort()
            if (currentPort != null && currentPort > 0) {
                configuredPort = currentPort
                logger.info("JCEF debug port already set: $currentPort")
                return currentPort
            }

            val randomPort = findAvailablePort()
            if (randomPort != null) {
                if (setPort(randomPort)) {
                    configuredPort = randomPort
                    logger.info("JCEF debug port set to: $randomPort")
                    return randomPort
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to initialize JCEF debug port: ${e.message}")
        }
        return null
    }

    private fun getConfiguredPort(): Int? {
        return try {
            val registryClass = Class.forName("com.intellij.openapi.util.registry.Registry")
            val intValueMethod = registryClass.getDeclaredMethod(
                "intValue",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            val port = intValueMethod.invoke(null, REGISTRY_KEY, -1) as Int
            if (port > 0) port else null
        } catch (_: Exception) {
            null
        }
    }

    private fun setPort(port: Int): Boolean {
        return try {
            val registryClass = Class.forName("com.intellij.openapi.util.registry.Registry")
            val getMethod = registryClass.getDeclaredMethod("get", String::class.java)
            val registryValue = getMethod.invoke(null, REGISTRY_KEY)
            val setValueMethod = registryValue.javaClass.getDeclaredMethod(
                "setValue",
                Int::class.javaPrimitiveType
            )
            setValueMethod.invoke(registryValue, port)
            true
        } catch (e: Exception) {
            logger.warn("Could not set JCEF debug port: ${e.message}")
            false
        }
    }

    private fun findAvailablePort(): Int? {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (_: Exception) {
            null
        }
    }
}
