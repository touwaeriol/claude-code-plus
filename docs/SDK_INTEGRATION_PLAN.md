# Node.js Claude SDK 集成插件方案

## 1. 概述

将 Claude SDK 的 Node.js 服务直接打包到 IntelliJ IDEA 插件中，实现一体化部署和管理。用户安装插件即可使用，无需额外配置 Node.js 环境。

## 2. 架构设计

```
┌─────────────────────────────────────────┐
│        IntelliJ IDEA Plugin             │
│        (Kotlin/Java)                    │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │      NodeServiceManager             │ │  ← 管理 Node 服务生命周期
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │      Embedded Node.js Runtime       │ │  ← 内嵌的 Node.js 运行时
│ │   ├── win32/node.exe                │ │
│ │   ├── darwin/node                   │ │
│ │   └── linux/node                    │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │      Claude SDK Wrapper Server      │ │  ← Node.js 服务代码
│ │   ├── server.js                     │ │
│ │   ├── services/                     │ │
│ │   └── node_modules/                 │ │
│ └─────────────────────────────────────┘ │
└─────────────┬───────────────────────────┘
              │ HTTP/WebSocket
              │ (localhost:18080)
┌─────────────▼───────────────────────────┐
│         @anthropic-ai/claude-code       │
│         (NPM Package)                   │
└─────────────────────────────────────────┘
```

## 3. 打包方案：嵌入式 Node.js + 服务代码

### 优点
- **零依赖部署**: 无需用户安装 Node.js 环境
- **版本一致性**: 确保所有用户使用相同的 Node.js 版本
- **跨平台支持**: 支持 Windows、macOS、Linux
- **自动管理**: 插件自动管理服务生命周期
- **安全隔离**: 服务运行在隔离环境中

### 资源打包结构
```
src/main/resources/
├── claude-node/
│   ├── runtime/                    # Node.js 运行时
│   │   ├── win32-x64/
│   │   │   └── node.exe
│   │   ├── darwin-x64/
│   │   │   └── node
│   │   ├── darwin-arm64/
│   │   │   └── node
│   │   └── linux-x64/
│   │       └── node
│   ├── server/                     # 服务代码
│   │   ├── package.json
│   │   ├── dist/                   # 编译后的 TypeScript 代码
│   │   │   ├── server.js
│   │   │   ├── services/
│   │   │   │   ├── claudeService.js
│   │   │   │   └── sessionManager.js
│   │   │   └── routes/
│   │   └── node_modules/           # 生产依赖
│   │       ├── @anthropic-ai/
│   │       ├── express/
│   │       ├── winston/
│   │       └── ...
│   └── scripts/
│       ├── extract.js              # 资源提取脚本
│       └── cleanup.js              # 清理脚本
```

## 4. 插件集成实现

### 4.1 Node.js 服务管理类

```kotlin
@Service
class NodeServiceManager : Disposable {
    private var nodeProcess: Process? = null
    private var serverPort: Int = 0
    private val extractDir = File(System.getProperty("java.io.tmpdir"), "claude-node-${System.currentTimeMillis()}")
    
    companion object {
        fun getInstance(): NodeServiceManager = service()
        private const val DEFAULT_PORT = 18080
        private const val HEALTH_CHECK_TIMEOUT = 30_000L // 30秒
    }
    
    /**
     * 启动 Node.js 服务
     */
    fun startService(project: Project): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            try {
                // 1. 查找可用端口
                serverPort = findAvailablePort(DEFAULT_PORT)
                
                // 2. 提取 Node.js 运行时和服务代码
                val nodeRuntime = extractNodeRuntime()
                val serverPath = extractServerCode()
                
                // 3. 启动 Node 服务
                val command = listOf(
                    nodeRuntime.absolutePath,
                    File(serverPath, "server.js").absolutePath,
                    "--port", serverPort.toString(),
                    "--host", "127.0.0.1"
                )
                
                val processBuilder = ProcessBuilder(command)
                    .directory(project.basePath?.let { File(it) } ?: File("."))
                    .redirectErrorStream(true)
                
                // 设置环境变量
                processBuilder.environment().apply {
                    put("NODE_ENV", "production")
                    put("CLAUDE_NODE_MODULES", File(serverPath, "node_modules").absolutePath)
                }
                
                nodeProcess = processBuilder.start()
                
                // 4. 等待服务启动并进行健康检查
                if (waitForServiceReady()) {
                    logger.info("Node.js Claude SDK service started on port $serverPort")
                    serverPort
                } else {
                    throw RuntimeException("Failed to start Node.js service")
                }
            } catch (e: Exception) {
                logger.error("Failed to start Node.js service", e)
                throw e
            }
        }
    }
    
    /**
     * 停止服务
     */
    fun stopService() {
        try {
            nodeProcess?.let { process ->
                // 优雅关闭
                if (process.isAlive) {
                    process.destroy()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error stopping Node.js service", e)
        } finally {
            nodeProcess = null
            serverPort = 0
            // 清理临时文件
            cleanupExtractDir()
        }
    }
    
    /**
     * 提取 Node.js 运行时
     */
    private fun extractNodeRuntime(): File {
        val platform = getPlatform()
        val runtimePath = "/claude-node/runtime/$platform/"
        val nodeExecutable = if (platform.startsWith("win32")) "node.exe" else "node"
        
        val targetFile = File(extractDir, "runtime/$nodeExecutable")
        targetFile.parentFile.mkdirs()
        
        // 从插件资源中提取
        val resourceStream = javaClass.getResourceAsStream("$runtimePath$nodeExecutable")
            ?: throw RuntimeException("Node.js runtime not found for platform: $platform")
        
        resourceStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // 设置执行权限
        targetFile.setExecutable(true)
        
        return targetFile
    }
    
    /**
     * 提取服务代码和依赖
     */
    private fun extractServerCode(): File {
        val serverDir = File(extractDir, "server")
        serverDir.mkdirs()
        
        // 提取所有服务文件
        val resourcePaths = listOf(
            "/claude-node/server/package.json",
            "/claude-node/server/dist/",
            "/claude-node/server/node_modules/"
        )
        
        resourcePaths.forEach { path ->
            extractResourceRecursively(path, serverDir)
        }
        
        return serverDir
    }
    
    /**
     * 获取当前平台标识
     */
    private fun getPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        
        return when {
            os.contains("win") -> "win32-x64"
            os.contains("mac") || os.contains("darwin") -> {
                if (arch.contains("aarch64") || arch.contains("arm")) "darwin-arm64" else "darwin-x64"
            }
            os.contains("linux") -> "linux-x64"
            else -> throw RuntimeException("Unsupported platform: $os-$arch")
        }
    }
    
    /**
     * 等待服务准备就绪
     */
    private fun waitForServiceReady(): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < HEALTH_CHECK_TIMEOUT) {
            try {
                val response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:$serverPort/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                
                if (response.statusCode() == 200) {
                    return true
                }
            } catch (e: Exception) {
                // 继续等待
            }
            
            Thread.sleep(1000)
        }
        return false
    }
    
    /**
     * 查找可用端口
     */
    private fun findAvailablePort(startPort: Int): Int {
        for (port in startPort until startPort + 100) {
            try {
                ServerSocket(port).use { return port }
            } catch (e: IOException) {
                continue
            }
        }
        throw RuntimeException("No available port found")
    }
    
    /**
     * 获取服务端口
     */
    fun getServicePort(): Int = serverPort
    
    /**
     * 检查服务是否运行
     */
    fun isServiceRunning(): Boolean {
        return nodeProcess?.isAlive == true && serverPort > 0
    }
    
    override fun dispose() {
        stopService()
    }
}
```

### 4.2 HTTP 客户端

```kotlin
@Service
class ClaudeAPIClient {
    
    private val httpClient = HttpClient.newHttpClient()
    private val nodeService = NodeServiceManager.getInstance()
    
    /**
     * 流式发送消息
     */
    suspend fun streamMessage(
        message: String,
        sessionId: String? = null,
        options: Map<String, Any>? = null
    ): Flow<StreamChunk> = flow {
        val port = nodeService.getServicePort()
        if (port == 0) throw RuntimeException("Node.js service not running")
        
        val requestBody = buildJsonObject {
            put("message", message)
            sessionId?.let { put("session_id", it) }
            options?.forEach { (key, value) -> put(key, JsonPrimitive(value.toString())) }
        }
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$port/stream"))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build()
        
        // 处理 Server-Sent Events
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
        
        response.body().forEach { line ->
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ")
                if (data != "[DONE]") {
                    try {
                        val chunk = Json.decodeFromString<StreamChunk>(data)
                        emit(chunk)
                    } catch (e: Exception) {
                        // 忽略解析错误的行
                    }
                }
            }
        }
    }
    
    /**
     * 单次发送消息
     */
    suspend fun sendMessage(
        message: String,
        sessionId: String? = null,
        options: Map<String, Any>? = null
    ): ClaudeResponse {
        val port = nodeService.getServicePort()
        if (port == 0) throw RuntimeException("Node.js service not running")
        
        val requestBody = buildJsonObject {
            put("message", message)
            sessionId?.let { put("session_id", it) }
            options?.forEach { (key, value) -> put(key, JsonPrimitive(value.toString())) }
        }
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$port/message"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
            return Json.decodeFromString<ClaudeResponse>(response.body())
        } else {
            throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
        }
    }
    
    /**
     * 健康检查
     */
    fun healthCheck(): Boolean {
        return try {
            val port = nodeService.getServicePort()
            if (port == 0) return false
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:$port/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
}

// 数据类
@Serializable
data class StreamChunk(
    val type: String,
    val content: String? = null,
    val message_type: String? = null,
    val session_id: String? = null,
    val error: String? = null
)

@Serializable
data class ClaudeResponse(
    val success: Boolean,
    val response: String? = null,
    val error: String? = null,
    val session_id: String
)
```

### 4.3 插件生命周期集成

```kotlin
class ClaudeNodeStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val nodeService = NodeServiceManager.getInstance()
                nodeService.startService(project).get(60, TimeUnit.SECONDS)
                
                // 更新 UI 状态
                ApplicationManager.getApplication().invokeLater {
                    // 启用相关 UI 组件
                    project.service<ClaudeToolWindowService>().updateConnectionStatus(true)
                }
            } catch (e: Exception) {
                logger.warn("Failed to start Claude Node.js service", e)
                
                // 显示错误通知
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Claude Code Plus")
                    .createNotification(
                        "Claude 服务启动失败",
                        "无法启动内嵌的 Node.js 服务: ${e.message}",
                        NotificationType.WARNING
                    )
                    .notify(project)
            }
        }
    }
}

@Service(Service.Level.PROJECT)
class ClaudeProjectService(private val project: Project) : Disposable {
    
    init {
        // 项目打开时初始化
        ApplicationManager.getApplication().executeOnPooledThread {
            initializeService()
        }
    }
    
    private fun initializeService() {
        val nodeService = NodeServiceManager.getInstance()
        if (!nodeService.isServiceRunning()) {
            try {
                nodeService.startService(project).get(30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Failed to initialize Claude service", e)
            }
        }
    }
    
    override fun dispose() {
        // 项目关闭时会自动调用 NodeServiceManager.dispose()
    }
}
```

## 5. 构建脚本

### 5.1 Gradle 任务：下载 Node.js 运行时

```kotlin
// build.gradle.kts

import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

// Node.js 版本配置
val nodeVersion = "20.18.0"
val platforms = listOf(
    "win32-x64" to "win-x64.zip",
    "darwin-x64" to "darwin-x64.tar.gz",
    "darwin-arm64" to "darwin-arm64.tar.gz", 
    "linux-x64" to "linux-x64.tar.xz"
)

tasks.register("downloadNodeRuntimes") {
    description = "Download Node.js runtimes for all platforms"
    group = "build"
    
    doLast {
        val runtimeDir = file("src/main/resources/claude-node/runtime")
        runtimeDir.mkdirs()
        
        platforms.forEach { (platform, archive) ->
            val platformDir = File(runtimeDir, platform)
            platformDir.mkdirs()
            
            val downloadUrl = "https://nodejs.org/dist/v$nodeVersion/node-v$nodeVersion-$archive"
            val tempFile = File.createTempFile("node-$platform", archive.substringAfterLast('.'))
            
            println("Downloading Node.js for $platform...")
            
            try {
                // 下载文件
                URL(downloadUrl).openStream().use { input ->
                    Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                
                // 解压文件
                when {
                    archive.endsWith(".zip") -> extractZip(tempFile, platformDir)
                    archive.endsWith(".tar.gz") -> extractTarGz(tempFile, platformDir) 
                    archive.endsWith(".tar.xz") -> extractTarXz(tempFile, platformDir)
                }
                
                println("Downloaded and extracted Node.js for $platform")
            } finally {
                tempFile.delete()
            }
        }
    }
}

tasks.register("buildNodeService") {
    description = "Build Node.js service code"
    group = "build"
    dependsOn(":claude-sdk-wrapper:build")
    
    doLast {
        val serverDir = file("src/main/resources/claude-node/server")
        serverDir.mkdirs()
        
        // 复制编译后的代码
        copy {
            from("claude-sdk-wrapper/dist")
            into("$serverDir/dist")
        }
        
        // 复制 package.json
        copy {
            from("claude-sdk-wrapper/package.json")
            into(serverDir)
        }
        
        // 安装生产依赖
        exec {
            workingDir = serverDir
            commandLine = listOf("npm", "ci", "--production", "--silent")
        }
        
        println("Node.js service built successfully")
    }
}

// 辅助函数
fun extractZip(zipFile: File, targetDir: File) {
    ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name.contains("/bin/node.exe") || entry.name.endsWith("/node.exe")) {
                val nodeFile = File(targetDir, "node.exe")
                Files.copy(zip, nodeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                break
            }
            entry = zip.nextEntry
        }
    }
}

fun extractTarGz(tarFile: File, targetDir: File) {
    TarArchiveInputStream(GzipCompressorInputStream(tarFile.inputStream().buffered())).use { tar ->
        var entry = tar.nextTarEntry
        while (entry != null) {
            if (entry.name.contains("/bin/node") && !entry.name.contains(".")) {
                val nodeFile = File(targetDir, "node")
                Files.copy(tar, nodeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                nodeFile.setExecutable(true)
                break
            }
            entry = tar.nextTarEntry
        }
    }
}

// 添加到构建依赖
tasks.named("prepareSandbox") {
    dependsOn("downloadNodeRuntimes", "buildNodeService")
}
```

## 6. 配置管理

### 插件设置界面

```kotlin
@State(
    name = "ClaudeNodeSettings",
    storages = [Storage("ClaudeCodePlus.xml")]
)
class ClaudeNodeSettings : PersistentStateComponent<ClaudeNodeSettings.State> {
    
    data class State(
        var nodeServicePort: Int = 18080,
        var autoStartService: Boolean = true,
        var logLevel: String = "INFO",
        var maxStartupTime: Int = 30, // 秒
        var enableHealthCheck: Boolean = true,
        var healthCheckInterval: Int = 60, // 秒
        var nodeVersion: String = "20.18.0",
        var cleanupOnExit: Boolean = true
    )
    
    private var state = State()
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    companion object {
        fun getInstance(): ClaudeNodeSettings = ApplicationManager.getApplication().getService(ClaudeNodeSettings::class.java)
    }
}

// 设置面板
class ClaudeNodeConfigurable : Configurable {
    private lateinit var panel: ClaudeNodeSettingsPanel
    
    override fun createComponent(): JComponent {
        panel = ClaudeNodeSettingsPanel()
        return panel.createPanel()
    }
    
    override fun isModified(): Boolean {
        return panel.isModified()
    }
    
    override fun apply() {
        panel.apply()
    }
    
    override fun reset() {
        panel.reset()
    }
    
    override fun getDisplayName(): String = "Claude Node 服务"
}
```

## 7. 错误处理和恢复

### 7.1 启动失败处理

```kotlin
enum class StartupFailureReason {
    PORT_IN_USE,
    NODE_RUNTIME_MISSING,
    SERVICE_CODE_MISSING,
    EXTRACTION_FAILED,
    PROCESS_START_FAILED,
    HEALTH_CHECK_TIMEOUT
}

class NodeServiceRecovery {
    
    fun handleStartupFailure(reason: StartupFailureReason, exception: Exception): Boolean {
        return when (reason) {
            PORT_IN_USE -> {
                // 尝试其他端口
                val newPort = findAlternativePort()
                retryWithPort(newPort)
            }
            
            NODE_RUNTIME_MISSING -> {
                // 重新下载运行时
                downloadMissingRuntime()
            }
            
            SERVICE_CODE_MISSING -> {
                // 重新提取服务代码
                reextractServiceCode()
            }
            
            EXTRACTION_FAILED -> {
                // 清理并重试
                cleanupAndRetry()
            }
            
            PROCESS_START_FAILED -> {
                // 检查权限和依赖
                checkPermissionsAndRetry()
            }
            
            HEALTH_CHECK_TIMEOUT -> {
                // 延长超时时间重试
                retryWithExtendedTimeout()
            }
        }
    }
    
    private fun showRecoveryDialog(reason: StartupFailureReason): Boolean {
        val message = when (reason) {
            PORT_IN_USE -> "端口 18080 被占用，是否尝试其他端口？"
            NODE_RUNTIME_MISSING -> "Node.js 运行时缺失，是否重新下载？"
            else -> "服务启动失败，是否重试？"
        }
        
        return Messages.showYesNoDialog(
            message,
            "Claude 服务启动失败",
            Messages.getQuestionIcon()
        ) == Messages.YES
    }
}
```

### 7.2 运行时监控和自动恢复

```kotlin
@Service
class NodeServiceMonitor : Disposable {
    
    private val healthCheckScheduler = Executors.newSingleThreadScheduledExecutor()
    private var isMonitoring = false
    
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        healthCheckScheduler.scheduleWithFixedDelay({
            try {
                performHealthCheck()
            } catch (e: Exception) {
                logger.warn("Health check failed", e)
            }
        }, 60, 60, TimeUnit.SECONDS)
    }
    
    private fun performHealthCheck() {
        val nodeService = NodeServiceManager.getInstance()
        
        if (!nodeService.isServiceRunning()) {
            logger.warn("Node service is not running, attempting restart...")
            
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val project = ProjectManager.getInstance().openProjects.firstOrNull()
                    if (project != null) {
                        nodeService.startService(project).get(30, TimeUnit.SECONDS)
                        logger.info("Node service restarted successfully")
                        
                        // 通知用户服务已恢复
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Claude Code Plus")
                            .createNotification(
                                "Claude 服务已恢复",
                                "Node.js 服务已自动重启",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to restart Node service", e)
                }
            }
        }
    }
    
    override fun dispose() {
        isMonitoring = false
        healthCheckScheduler.shutdown()
    }
}
```

## 8. 优化和性能

### 8.1 资源管理优化

```kotlin
class NodeResourceManager {
    
    // 懒加载提取
    private val extractionCache = ConcurrentHashMap<String, File>()
    
    fun getNodeRuntime(): File {
        val platform = getPlatform()
        return extractionCache.computeIfAbsent("runtime_$platform") {
            extractNodeRuntime()
        }
    }
    
    // 压缩存储
    fun compressResources() {
        val resourceDir = File("src/main/resources/claude-node")
        
        // 使用 GZIP 压缩大文件
        val nodeModulesDir = File(resourceDir, "server/node_modules")
        if (nodeModulesDir.exists()) {
            compressDirectory(nodeModulesDir)
        }
    }
    
    // 增量更新
    fun updateServiceCode(newVersion: String) {
        val currentVersion = getCurrentServiceVersion()
        if (currentVersion != newVersion) {
            downloadServiceUpdate(newVersion)
            applyServiceUpdate()
        }
    }
}
```

### 8.2 启动性能优化

```kotlin
class FastStartupManager {
    
    // 预热机制
    fun preheatService() {
        ApplicationManager.getApplication().executeOnPooledThread {
            // 预先提取关键文件
            extractCriticalFiles()
            
            // 预编译正则表达式
            precompilePatterns()
            
            // 预分配端口
            preallocatePort()
        }
    }
    
    // 并行启动
    fun parallelStartup(project: Project): CompletableFuture<Int> {
        val extractionFuture = CompletableFuture.supplyAsync {
            extractAllResources()
        }
        
        val portFuture = CompletableFuture.supplyAsync {
            findAvailablePort()
        }
        
        return extractionFuture.thenCombine(portFuture) { extractedPath, port ->
            startNodeProcess(extractedPath, port)
        }
    }
}
```

## 9. 安全性考虑

### 9.1 文件安全

```kotlin
class NodeSecurityManager {
    
    // 文件完整性验证
    fun verifyFileIntegrity(file: File, expectedHash: String): Boolean {
        val actualHash = calculateSHA256(file)
        return actualHash == expectedHash
    }
    
    // 安全的临时目录
    fun createSecureTempDir(): File {
        val tempDir = Files.createTempDirectory("claude-node-").toFile()
        tempDir.deleteOnExit()
        
        // 设置权限（仅当前用户可访问）
        tempDir.setReadable(false, false)
        tempDir.setWritable(false, false)
        tempDir.setExecutable(false, false)
        tempDir.setReadable(true, true)
        tempDir.setWritable(true, true)
        tempDir.setExecutable(true, true)
        
        return tempDir
    }
    
    // 进程隔离
    fun createIsolatedProcess(command: List<String>): ProcessBuilder {
        return ProcessBuilder(command).apply {
            // 清理环境变量
            environment().clear()
            environment()["PATH"] = getMinimalPath()
            environment()["NODE_ENV"] = "production"
            
            // 重定向输出
            redirectErrorStream(true)
        }
    }
}
```

## 10. 部署和发布

### 10.1 CI/CD 集成

```yaml
# .github/workflows/build.yml
name: Build Plugin with Embedded Node.js

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Setup JDK
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '20'
        
    - name: Build Node Service
      run: |
        cd claude-sdk-wrapper
        npm ci
        npm run build
        
    - name: Download Node Runtimes
      run: ./gradlew downloadNodeRuntimes
      
    - name: Build Plugin
      run: ./gradlew buildPlugin
      
    - name: Upload Plugin
      uses: actions/upload-artifact@v3
      with:
        name: plugin-artifact
        path: build/distributions/*.zip
```

### 10.2 版本管理

```kotlin
object NodeServiceVersion {
    const val NODE_VERSION = "20.18.0"
    const val SERVICE_VERSION = "1.0.0"
    const val SDK_VERSION = "latest"
    
    // 版本兼容性检查
    fun isCompatible(requiredVersion: String): Boolean {
        return compareVersions(SERVICE_VERSION, requiredVersion) >= 0
    }
    
    // 自动更新检查
    fun checkForUpdates(): UpdateInfo? {
        // 检查远程版本信息
        return null
    }
}
```

## 11. 监控和诊断

### 11.1 性能监控

```kotlin
class NodeServiceMetrics {
    
    private val startupTime = AtomicLong(0)
    private val requestCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    
    fun recordStartupTime(time: Long) {
        startupTime.set(time)
    }
    
    fun recordRequest() {
        requestCount.incrementAndGet()
    }
    
    fun recordError() {
        errorCount.incrementAndGet()
    }
    
    fun generateReport(): String {
        return """
            Node.js Service Metrics:
            - Startup Time: ${startupTime.get()}ms
            - Total Requests: ${requestCount.get()}
            - Error Count: ${errorCount.get()}
            - Error Rate: ${errorCount.get().toDouble() / requestCount.get() * 100}%
        """.trimIndent()
    }
}
```

这个方案提供了完整的 Node.js 环境和代码打包解决方案，包括：

1. **零依赖部署** - 用户无需安装 Node.js
2. **跨平台支持** - 支持 Windows、macOS、Linux
3. **自动管理** - 插件自动管理服务生命周期
4. **错误恢复** - 完善的错误处理和自动恢复机制
5. **性能优化** - 多种优化策略提升启动速度
6. **安全保障** - 文件完整性验证和进程隔离
7. **监控诊断** - 完整的监控和诊断功能