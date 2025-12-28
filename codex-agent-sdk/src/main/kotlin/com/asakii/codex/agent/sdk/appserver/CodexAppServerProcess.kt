package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Codex App-Server 进程管理器
 *
 * 负责启动和管理 `codex app-server` 进程的生命周期
 */
class CodexAppServerProcess private constructor(
    private val process: Process,
    private val rpcClient: CodexJsonRpcClient
) : Closeable {

    val client: CodexJsonRpcClient get() = rpcClient

    val isAlive: Boolean get() = process.isAlive

    /**
     * 等待进程退出
     */
    fun waitFor(timeout: Long = 30, unit: TimeUnit = TimeUnit.SECONDS): Int {
        return if (process.waitFor(timeout, unit)) {
            process.exitValue()
        } else {
            -1
        }
    }

    /**
     * 强制终止进程
     */
    fun destroy() {
        process.destroy()
    }

    /**
     * 强制终止进程 (forcibly)
     */
    fun destroyForcibly(): Process {
        return process.destroyForcibly()
    }

    override fun close() {
        rpcClient.close()
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        /**
         * 启动 Codex App-Server 进程
         *
         * @param codexPath Codex 可执行文件路径，null 则自动查找
         * @param workingDirectory 工作目录
         * @param env 环境变量
         * @param scope 协程作用域
         */
        fun spawn(
            codexPath: Path? = null,
            workingDirectory: Path? = null,
            env: Map<String, String> = emptyMap(),
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): CodexAppServerProcess {
            val executablePath = codexPath?.toString() ?: findCodexExecutable()

            val processBuilder = ProcessBuilder(executablePath, "app-server")
                .redirectErrorStream(false)

            workingDirectory?.let {
                processBuilder.directory(it.toFile())
            }

            // 设置环境变量
            val processEnv = processBuilder.environment()
            env.forEach { (key, value) ->
                processEnv[key] = value
            }

            val process = try {
                processBuilder.start()
            } catch (e: IOException) {
                throw CodexAppServerException("Failed to start codex app-server: ${e.message}", e)
            }

            val stdin = process.outputStream
                ?: throw CodexAppServerException("Failed to get stdin of codex app-server")
            val stdout = process.inputStream
                ?: throw CodexAppServerException("Failed to get stdout of codex app-server")

            val rpcClient = CodexJsonRpcClient(stdin, stdout, scope)
            rpcClient.start()

            return CodexAppServerProcess(process, rpcClient)
        }

        /**
         * 查找 Codex 可执行文件
         */
        private fun findCodexExecutable(): String {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val binaryName = if (isWindows) "codex.exe" else "codex"

            // 1. 检查环境变量 CODEX_BIN
            System.getenv("CODEX_BIN")?.let { path ->
                if (File(path).exists()) return path
            }

            // 2. 检查 PATH 中的 codex
            val pathEnv = System.getenv("PATH") ?: ""
            val pathSeparator = if (isWindows) ";" else ":"
            for (dir in pathEnv.split(pathSeparator)) {
                val file = File(dir, binaryName)
                if (file.exists() && file.canExecute()) {
                    return file.absolutePath
                }
            }

            // 3. 检查常见位置
            val commonPaths = if (isWindows) {
                listOf(
                    System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\codex\\$binaryName" },
                    System.getenv("APPDATA")?.let { "$it\\codex\\$binaryName" },
                    "C:\\Program Files\\codex\\$binaryName"
                )
            } else {
                listOf(
                    System.getenv("HOME")?.let { "$it/.local/bin/$binaryName" },
                    "/usr/local/bin/$binaryName",
                    "/usr/bin/$binaryName"
                )
            }

            for (path in commonPaths.filterNotNull()) {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    return file.absolutePath
                }
            }

            throw CodexAppServerException(
                "Codex executable not found. Please install Codex or set CODEX_BIN environment variable."
            )
        }
    }
}

/**
 * App-Server 异常
 */
class CodexAppServerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
