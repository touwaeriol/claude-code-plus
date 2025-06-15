package com.claudecodeplus.core

import com.intellij.openapi.diagnostic.Logger
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * GraalVM Python 环境管理器
 */
object GraalPythonManager {
    
    private val logger = Logger.getInstance(GraalPythonManager::class.java)
    private var pythonContext: Context? = null
    
    /**
     * 初始化 Python 环境
     */
    fun initialize(): Context {
        if (pythonContext != null) {
            return pythonContext!!
        }
        
        try {
            logger.info("Initializing GraalVM Python environment...")
            
            // 创建 Python 上下文
            pythonContext = Context.newBuilder("python")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup { true }
                .allowIO(true)
                .allowCreateThread(true)
                .allowNativeAccess(true)
                // .allowEnvironmentAccess(HostAccess.INHERIT) // 可能不支持的选项
                .option("python.ForceImportSite", "true")
                .build()
            
            // 设置 Python 路径
            setupPythonPath()
            
            // 测试 Python 环境
            testPythonEnvironment()
            
            logger.info("GraalVM Python environment initialized successfully")
            return pythonContext!!
            
        } catch (e: PolyglotException) {
            logger.error("Failed to initialize GraalVM Python environment", e)
            throw RuntimeException("GraalVM Python initialization failed: ${e.message}", e)
        }
    }
    
    /**
     * 获取 Python 上下文
     */
    fun getContext(): Context {
        return pythonContext ?: initialize()
    }
    
    /**
     * 清理 Python 环境
     */
    fun cleanup() {
        pythonContext?.close()
        pythonContext = null
    }
    
    /**
     * 设置 Python 路径
     */
    private fun setupPythonPath() {
        val context = pythonContext ?: return
        
        try {
            // 获取插件的 Python 包路径
            val pluginPath = getPluginPythonPath()
            
            // 添加到 Python sys.path
            context.eval("python", """
import sys
import os

# 添加插件 Python 包路径
plugin_path = '${pluginPath.replace("\\", "\\\\")}'
if plugin_path not in sys.path:
    sys.path.insert(0, plugin_path)

# 添加用户 site-packages（如果存在）
try:
    import site
    site_packages = site.getusersitepackages()
    if site_packages and os.path.exists(site_packages):
        if site_packages not in sys.path:
            sys.path.append(site_packages)
except:
    pass

print(f"Python path configured: {sys.path}")
""")
            
        } catch (e: Exception) {
            logger.warn("Failed to setup Python path", e)
        }
    }
    
    /**
     * 获取插件的 Python 包路径
     */
    private fun getPluginPythonPath(): String {
        // 尝试从插件目录查找
        val pluginDir = File(System.getProperty("user.dir"))
        val pythonDir = File(pluginDir, "claude-sdk-wrapper")
        
        if (pythonDir.exists()) {
            return pythonDir.absolutePath
        }
        
        // 尝试从类路径查找
        val resourceUrl = GraalPythonManager::class.java.getResource("/claude-sdk-wrapper")
        if (resourceUrl != null) {
            return File(resourceUrl.toURI()).absolutePath
        }
        
        // 默认返回当前目录
        return pluginDir.absolutePath
    }
    
    /**
     * 测试 Python 环境
     */
    private fun testPythonEnvironment() {
        val context = pythonContext ?: return
        
        try {
            val result = context.eval("python", """
import sys
f"Python {sys.version} on GraalVM"
""")
            
            logger.info("Python environment test: ${result.asString()}")
            
        } catch (e: Exception) {
            logger.error("Python environment test failed", e)
            throw e
        }
    }
    
    /**
     * 检查是否已安装 Claude SDK
     */
    fun checkClaudeSdkInstalled(): Boolean {
        return try {
            val context = getContext()
            val result = context.eval("python", """
try:
    import claudecode
    True
except ImportError:
    False
""")
            result.asBoolean()
        } catch (e: Exception) {
            logger.warn("Failed to check Claude SDK installation", e)
            false
        }
    }
    
    /**
     * 安装 Python 包
     */
    fun installPackage(packageName: String): Boolean {
        return try {
            val context = getContext()
            context.eval("python", """
import subprocess
import sys

result = subprocess.run(
    [sys.executable, "-m", "pip", "install", "$packageName"],
    capture_output=True,
    text=True
)

result.returncode == 0
""")
            true
        } catch (e: Exception) {
            logger.error("Failed to install package: $packageName", e)
            false
        }
    }
}