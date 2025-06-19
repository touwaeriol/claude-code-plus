package com.claudecodeplus.sdk

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

/**
 * Node.js 资源提取器
 * 负责从插件 JAR 中提取 Node.js 运行时和服务代码
 */
class NodeResourceExtractor {
    
    private val logger = thisLogger()
    
    /**
     * 提取所有服务器资源到指定目录
     */
    fun extractServerResources(targetDir: File): Boolean {
        return try {
            logger.info("Extracting server resources to: ${targetDir.absolutePath}")
            println("[NodeResourceExtractor] 开始提取资源到: ${targetDir.absolutePath}")
            
            targetDir.mkdirs()
            
            // 提取所有资源 (现在所有文件都在根目录)
            println("[NodeResourceExtractor] 提取所有资源...")
            
            // 提取 package.json
            val packageResult = extractResource("/claude-node/package.json", File(targetDir, "package.json"))
            println("[NodeResourceExtractor] package.json 提取结果: $packageResult")
            
            // 提取服务文件
            val serverFiles = listOf("server.js", "server-esm-wrapper.mjs")
            serverFiles.forEach { fileName ->
                val result = extractResource("/claude-node/$fileName", File(targetDir, fileName))
                println("[NodeResourceExtractor] $fileName 提取结果: $result")
            }
            
            // 提取 services 和 routes 目录
            val servicesResult = extractDirectoryFromResources("/claude-node/services/", File(targetDir, "services"))
            println("[NodeResourceExtractor] services 目录提取结果: $servicesResult")
            
            val routesResult = extractDirectoryFromResources("/claude-node/routes/", File(targetDir, "routes"))
            println("[NodeResourceExtractor] routes 目录提取结果: $routesResult")
            
            // 提取 node_modules
            val modulesResult = extractDirectoryFromResources("/claude-node/node_modules/", File(targetDir, "node_modules"))
            println("[NodeResourceExtractor] node_modules 目录提取结果: $modulesResult")
            
            // 列出提取的文件
            println("[NodeResourceExtractor] 提取的文件：")
            targetDir.walk().forEach { file ->
                if (file.isFile) {
                    println("[NodeResourceExtractor]   - ${file.relativeTo(targetDir).path}")
                }
            }
            
            logger.info("Server resources extracted successfully")
            println("[NodeResourceExtractor] 资源提取完成")
            true
        } catch (e: Exception) {
            logger.error("Failed to extract server resources", e)
            println("[NodeResourceExtractor] 资源提取失败: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 从资源中提取单个文件
     */
    fun extractResource(resourcePath: String, targetFile: File): Boolean {
        return try {
            println("[NodeResourceExtractor] 尝试提取资源: $resourcePath")
            val resourceStream = javaClass.getResourceAsStream(resourcePath)
            if (resourceStream == null) {
                logger.warn("Resource not found: $resourcePath")
                println("[NodeResourceExtractor] 资源未找到: $resourcePath")
                return false
            }
            
            targetFile.parentFile.mkdirs()
            
            var totalBytes = 0L
            resourceStream.use { input ->
                targetFile.outputStream().use { output ->
                    totalBytes = input.copyTo(output)
                }
            }
            
            logger.debug("Extracted resource: $resourcePath -> ${targetFile.absolutePath}")
            println("[NodeResourceExtractor] 已提取: $resourcePath -> ${targetFile.absolutePath} (${totalBytes} 字节)")
            true
        } catch (e: Exception) {
            logger.error("Failed to extract resource: $resourcePath", e)
            println("[NodeResourceExtractor] 提取失败: $resourcePath - ${e.message}")
            false
        }
    }
    
    /**
     * 从资源中提取整个目录
     */
    private fun extractDirectoryFromResources(resourcePath: String, targetDir: File): Boolean {
        return try {
            targetDir.mkdirs()
            
            // 获取资源文件列表
            val resourceFiles = getResourceFiles(resourcePath)
            
            if (resourceFiles.isEmpty()) {
                logger.warn("No resources found in directory: $resourcePath")
                return false
            }
            
            resourceFiles.forEach { resourceFile ->
                val relativePath = resourceFile.removePrefix(resourcePath)
                if (relativePath.isNotEmpty()) {
                    val targetFile = File(targetDir, relativePath)
                    extractResource(resourceFile, targetFile)
                }
            }
            
            logger.debug("Extracted directory: $resourcePath -> ${targetDir.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("Failed to extract directory: $resourcePath", e)
            false
        }
    }
    
    /**
     * 获取资源目录下的所有文件列表
     */
    private fun getResourceFiles(resourcePath: String): List<String> {
        val resourceFiles = mutableListOf<String>()
        
        try {
            println("[NodeResourceExtractor] 获取资源文件列表: $resourcePath")
            val url = javaClass.getResource(resourcePath)
            if (url != null) {
                println("[NodeResourceExtractor] URL 协议: ${url.protocol}")
                when (url.protocol) {
                    "file" -> {
                        // 开发环境，直接从文件系统读取
                        val dir = File(url.toURI())
                        println("[NodeResourceExtractor] 文件系统路径: ${dir.absolutePath}")
                        if (dir.exists() && dir.isDirectory) {
                            collectFiles(dir, resourcePath, resourceFiles)
                        }
                    }
                    "jar" -> {
                        // 生产环境，从 JAR 文件读取
                        val jarConnection = url.openConnection() as JarURLConnection
                        val jarFile = jarConnection.jarFile
                        println("[NodeResourceExtractor] JAR 文件: ${jarFile.name}")
                        
                        jarFile.entries().asSequence()
                            .filter { it.name.startsWith(resourcePath.removePrefix("/")) }
                            .filter { !it.isDirectory }
                            .forEach { entry ->
                                resourceFiles.add("/" + entry.name)
                            }
                    }
                }
            } else {
                println("[NodeResourceExtractor] 资源目录不存在: $resourcePath")
            }
            
            println("[NodeResourceExtractor] 找到 ${resourceFiles.size} 个文件")
        } catch (e: Exception) {
            logger.error("Failed to get resource files for: $resourcePath", e)
            println("[NodeResourceExtractor] 获取资源列表失败: ${e.message}")
        }
        
        return resourceFiles
    }
    
    /**
     * 递归收集文件
     */
    private fun collectFiles(dir: File, basePath: String, resourceFiles: MutableList<String>) {
        dir.listFiles()?.forEach { file ->
            val relativePath = file.relativeTo(File(basePath)).path.replace('\\', '/')
            val resourcePath = "$basePath$relativePath"
            
            if (file.isDirectory) {
                collectFiles(file, basePath, resourceFiles)
            } else {
                resourceFiles.add(resourcePath)
            }
        }
    }
    
    /**
     * 验证文件完整性
     */
    fun verifyFileIntegrity(file: File, expectedHash: String): Boolean {
        return try {
            val actualHash = calculateSHA256(file)
            actualHash.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            logger.error("Failed to verify file integrity: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * 计算文件的 SHA256 哈希值
     */
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 检查资源是否存在
     */
    fun resourceExists(resourcePath: String): Boolean {
        return javaClass.getResource(resourcePath) != null
    }
    
    /**
     * 获取资源大小
     */
    fun getResourceSize(resourcePath: String): Long {
        return try {
            javaClass.getResourceAsStream(resourcePath)?.use { stream ->
                stream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            logger.error("Failed to get resource size: $resourcePath", e)
            0L
        }
    }
}

/**
 * Node.js 资源管理器
 * 提供缓存和优化功能
 */
class NodeResourceManager {
    
    private val logger = thisLogger()
    private val extractor = NodeResourceExtractor()
    private val extractionCache = mutableMapOf<String, File>()
    
    /**
     * 获取 Node.js 运行时（带缓存）
     */
    fun getNodeRuntime(platform: String, extractDir: File): File? {
        val cacheKey = "runtime_$platform"
        
        return extractionCache.computeIfAbsent(cacheKey) {
            val nodeExecutable = if (platform.startsWith("win32")) "node.exe" else "node"
            val targetFile = File(extractDir, "runtime/$nodeExecutable")
            
            if (targetFile.exists()) {
                logger.info("Node.js runtime already exists: ${targetFile.absolutePath}")
                return@computeIfAbsent targetFile
            }
            
            val resourcePath = "/claude-node/runtime/$platform/$nodeExecutable"
            
            if (extractor.extractResource(resourcePath, targetFile)) {
                targetFile.setExecutable(true)
                targetFile
            } else {
                throw RuntimeException("Failed to extract Node.js runtime for platform: $platform")
            }
        }
    }
    
    /**
     * 获取服务器代码（带缓存）
     */
    fun getServerCode(extractDir: File): File? {
        val cacheKey = "server_code"
        
        return extractionCache.computeIfAbsent(cacheKey) {
            val serverDir = File(extractDir, "server")
            
            if (serverDir.exists() && File(serverDir, "dist/server.js").exists()) {
                logger.info("Server code already exists: ${serverDir.absolutePath}")
                return@computeIfAbsent serverDir
            }
            
            if (extractor.extractServerResources(serverDir)) {
                serverDir
            } else {
                throw RuntimeException("Failed to extract server code")
            }
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        extractionCache.clear()
        logger.info("Resource cache cleared")
    }
    
    /**
     * 获取缓存信息
     */
    fun getCacheInfo(): Map<String, String> {
        return extractionCache.mapValues { (_, file) ->
            "${file.absolutePath} (exists: ${file.exists()})"
        }
    }
}