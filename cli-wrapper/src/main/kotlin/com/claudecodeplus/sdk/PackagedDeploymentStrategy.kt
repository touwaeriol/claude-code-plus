package com.claudecodeplus.sdk

import com.claudecodeplus.core.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 打包部署策略
 * 
 * 解决可执行程序打包后的资源定位和部署问题
 */
object PackagedDeploymentStrategy {
    private val logger = LoggerFactory.getLogger(PackagedDeploymentStrategy::class.java)
    
    /**
     * 获取打包后的资源目录
     * 
     * 策略：
     * 1. 优先使用用户指定的资源目录（环境变量）
     * 2. 在用户家目录创建应用专用目录
     * 3. 从JAR中提取必要文件到该目录
     */
    fun getResourceDirectory(): File {
        // 策略1: 检查环境变量指定的目录
        val customDir = System.getenv("CLAUDE_CODE_PLUS_HOME")
        if (customDir != null) {
            val dir = File(customDir)
            if (dir.exists() && dir.isDirectory) {
                logger.info("使用环境变量指定的资源目录: ${dir.absolutePath}")
                return dir
            }
        }
        
        // 策略2: 使用用户家目录下的应用目录
        val homeDir = System.getProperty("user.home")
        val appDir = File(homeDir, ".claude-code-plus")
        
        if (!appDir.exists()) {
            appDir.mkdirs()
            logger.info("创建应用资源目录: ${appDir.absolutePath}")
        }
        
        return appDir
    }
    
    /**
     * 确保Node.js运行时环境就绪
     * 
     * @return Pair<脚本文件, 工作目录>
     */
    fun ensureNodejsRuntime(): Pair<File, File> {
        val resourceDir = getResourceDirectory()
        val cliWrapperDir = File(resourceDir, "cli-wrapper")
        
        // 确保目录存在
        if (!cliWrapperDir.exists()) {
            cliWrapperDir.mkdirs()
        }
        
        // 提取必要文件
        extractResourceToFile("/nodejs/claude-sdk-wrapper.js", File(cliWrapperDir, "claude-sdk-wrapper.js"))
        extractResourceToFile("/nodejs/package.json", File(cliWrapperDir, "package.json"))
        
        // 检查并安装依赖
        ensureNodeDependencies(cliWrapperDir)
        
        val scriptFile = File(cliWrapperDir, "claude-sdk-wrapper.js")
        return Pair(scriptFile, cliWrapperDir)
    }
    
    /**
     * 从JAR资源中提取文件
     */
    private fun extractResourceToFile(resourcePath: String, targetFile: File) {
        try {
            val resource = PackagedDeploymentStrategy::class.java.getResourceAsStream(resourcePath)
            if (resource != null) {
                resource.use { input ->
                    Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    logger.debug("提取资源文件: $resourcePath -> ${targetFile.absolutePath}")
                }
                
                // 为脚本文件添加执行权限
                if (targetFile.name.endsWith(".js")) {
                    targetFile.setExecutable(true)
                }
            } else {
                logger.warn("资源文件不存在: $resourcePath")
            }
        } catch (e: Exception) {
            logger.error("提取资源文件失败: $resourcePath", e)
            throw RuntimeException("无法提取必要的运行时文件: $resourcePath", e)
        }
    }
    
    /**
     * 确保Node.js依赖已安装
     */
    private fun ensureNodeDependencies(cliWrapperDir: File) {
        val nodeModules = File(cliWrapperDir, "node_modules")
        val packageLock = File(cliWrapperDir, "package-lock.json")
        
        if (!nodeModules.exists() || !isNodeModulesValid(nodeModules)) {
            logger.info("Node.js依赖缺失或过期，开始安装...")
            installNodeDependencies(cliWrapperDir)
        } else {
            logger.debug("Node.js依赖已存在且有效")
        }
    }
    
    /**
     * 检查node_modules是否有效
     */
    private fun isNodeModulesValid(nodeModules: File): Boolean {
        return try {
            // 检查关键依赖是否存在
            val claudeCodeModule = File(nodeModules, "@anthropic-ai/claude-code")
            claudeCodeModule.exists() && claudeCodeModule.isDirectory
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 安装Node.js依赖
     */
    private fun installNodeDependencies(cliWrapperDir: File) {
        try {
            val processBuilder = ProcessBuilder("npm", "install")
            processBuilder.directory(cliWrapperDir)
            
            // 设置环境变量
            val env = processBuilder.environment()
            env["NODE_ENV"] = "production"
            
            logger.info("执行: npm install (工作目录: ${cliWrapperDir.absolutePath})")
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                logger.info("Node.js依赖安装成功")
            } else {
                val error = process.errorStream.bufferedReader().readText()
                throw RuntimeException("npm install 失败，退出码: $exitCode\n错误信息: $error")
            }
        } catch (e: Exception) {
            logger.error("安装Node.js依赖失败", e)
            throw RuntimeException("无法安装必要的Node.js依赖", e)
        }
    }
    
    /**
     * 获取Node.js可执行文件路径
     */
    fun getNodeExecutablePath(): String {
        val osName = System.getProperty("os.name").lowercase()
        return if (osName.contains("windows")) {
            "node.exe"  // Windows系统
        } else {
            "node"      // Unix系统
        }
    }
    
    /**
     * 检查运行环境
     */
    fun checkEnvironment(): EnvironmentCheck {
        val checks = mutableMapOf<String, Boolean>()
        val issues = mutableListOf<String>()
        
        // 检查Node.js
        try {
            val process = ProcessBuilder(getNodeExecutablePath(), "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val version = process.inputStream.bufferedReader().readText().trim()
                checks["nodejs"] = true
                logger.info("Node.js 可用: $version")
            } else {
                checks["nodejs"] = false
                issues.add("Node.js 不可用或版本过低（需要18+）")
            }
        } catch (e: Exception) {
            checks["nodejs"] = false
            issues.add("Node.js 未安装: ${e.message}")
        }
        
        // 检查npm
        try {
            val process = ProcessBuilder("npm", "--version").start()
            val exitCode = process.waitFor()
            checks["npm"] = exitCode == 0
            if (exitCode != 0) {
                issues.add("npm 不可用")
            }
        } catch (e: Exception) {
            checks["npm"] = false
            issues.add("npm 未安装: ${e.message}")
        }
        
        // 检查API Key
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
        checks["api_key"] = !apiKey.isNullOrBlank()
        if (apiKey.isNullOrBlank()) {
            issues.add("ANTHROPIC_API_KEY 环境变量未设置")
        }
        
        return EnvironmentCheck(checks, issues)
    }
    
    data class EnvironmentCheck(
        val checks: Map<String, Boolean>,
        val issues: List<String>
    ) {
        val isValid: Boolean get() = issues.isEmpty()
    }
}