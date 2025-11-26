package com.asakii.server
import com.asakii.plugin.tools.IdeToolsImpl


import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

/**
 * HTTP 服务器项目级服务
 * 在项目打开时自动启动 HTTP API 服务器
 */
@Service(Service.Level.PROJECT)
class HttpServerProjectService(private val project: Project) : Disposable {
    private val logger = Logger.getLogger(javaClass.name)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var httpServer: HttpApiServer? = null
    private var extractedFrontendDir: Path? = null

    var serverUrl: String? = null
        private set

    init {
        logger.info("🚀 Initializing HTTP Server Project Service")
        startServer()
    }

    /**
     * 启动 HTTP 服务器
     */
    private fun startServer() {
        try {
            // 准备前端资源目录
            val frontendDir = prepareFrontendResources()
            logger.info("📂 Frontend directory: $frontendDir")

            // 启动 Ktor HTTP 服务器
            // 创建 IdeTools 的实现
            val ideTools = IdeToolsImpl(project)

            // 启动 Ktor HTTP 服务器
            // 开发模式：使用环境变量指定端口（默认 8765）
            // 生产模式：随机端口（支持多项目）
            val server = HttpApiServer(ideTools, scope, frontendDir)
            val devPort = System.getenv("CLAUDE_DEV_PORT")?.toIntOrNull()
            val url = server.start(preferredPort = devPort)
            httpServer = server
            serverUrl = url
            logger.info("🚀 HTTP Server started at: $url")
            logger.info("✅ HTTP Server Project Service initialized successfully")
        } catch (e: Exception) {
            logger.severe("❌ Failed to start HTTP server: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 准备前端资源
     * 从 JAR 解压到临时目录
     */
    private fun prepareFrontendResources(): Path {
        // 复用已解压的目录
        val existing = extractedFrontendDir
        if (existing != null && Files.exists(existing.resolve("index.html"))) {
            logger.info("✅ Reusing extracted frontend directory: $existing")
            return existing
        }

        val htmlUrl = javaClass.getResource("/frontend/index.html")
            ?: throw IllegalStateException("""
                ❌ Frontend resources not found in JAR!

                Solution:
                1. Run: ./gradlew :jetbrains-plugin:buildFrontend
                2. Or rebuild the project
            """.trimIndent())

        return when (htmlUrl.protocol) {
            "jar" -> {
                val connection = htmlUrl.openConnection() as JarURLConnection
                val tempDir = Files.createTempDirectory("claude-frontend-")
                logger.info("📦 Extracting frontend resources to: $tempDir")

                connection.jarFile.use { jarFile ->
                    jarFile.stream().use { entries ->
                        entries
                            .filter { !it.isDirectory && it.name.startsWith("frontend/") }
                            .forEach { entry ->
                                val relative = entry.name.removePrefix("frontend/")
                                val target = tempDir.resolve(relative)
                                target.parent?.let { Files.createDirectories(it) }
                                jarFile.getInputStream(entry).use { input ->
                                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                    }
                }

                extractedFrontendDir = tempDir
                logger.info("✅ Frontend extracted successfully")
                tempDir
            }
            "file" -> {
                // 开发模式：直接使用文件系统路径
                val file = Path.of(htmlUrl.toURI()).parent
                logger.info("✅ Using filesystem frontend directory: $file")
                file
            }
            else -> throw IllegalStateException("Unsupported protocol: ${htmlUrl.protocol}")
        }
    }

    /**
     * 获取 HTTP 服务器实例
     */
    fun getServer(): HttpApiServer? = httpServer

    override fun dispose() {
        logger.info("🛑 Disposing HTTP Server Project Service")
        httpServer?.stop()
        httpServer = null

        // 清理临时目录
        extractedFrontendDir?.toFile()?.deleteRecursively()
        extractedFrontendDir = null

        scope.cancel()
        logger.info("✅ HTTP Server Project Service disposed")
    }

    companion object {
        fun getInstance(project: Project): HttpServerProjectService {
            return project.getService(HttpServerProjectService::class.java)
        }
    }
}
