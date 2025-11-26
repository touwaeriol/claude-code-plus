package com.asakii.codex.agent.sdk

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val INTERNAL_ORIGINATOR_ENV = "CODEX_INTERNAL_ORIGINATOR_OVERRIDE"
private const val TYPESCRIPT_SDK_ORIGINATOR = "codex_sdk_kotlin"

data class CodexExecArgs @JvmOverloads constructor(
    val input: String,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val threadId: String? = null,
    val images: List<Path> = emptyList(),
    val model: String? = null,
    val sandboxMode: SandboxMode? = null,
    val workingDirectory: String? = null,
    val additionalDirectories: List<String> = emptyList(),
    val skipGitRepoCheck: Boolean = false,
    val outputSchemaFile: Path? = null,
    val modelReasoningEffort: ModelReasoningEffort? = null,
    val networkAccessEnabled: Boolean? = null,
    val webSearchEnabled: Boolean? = null,
    val approvalPolicy: ApprovalMode? = null,
    val cancellation: Job? = null,
)

internal interface CodexExecRunner {
    fun run(args: CodexExecArgs): Flow<String>
}

class CodexExecProcess @JvmOverloads constructor(
    private val pathOverride: Path? = null,
    private val envOverride: Map<String, String>? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CodexExecRunner {
    private val executablePath: Path = pathOverride ?: findCodexExecutable()

    override fun run(args: CodexExecArgs): Flow<String> = callbackFlow {
        val command = buildCommand(args)

        // Windows 上如果是 .cmd 文件，需要通过 cmd /c 执行
        val finalCommand = if (executablePath.toString().endsWith(".cmd")) {
            listOf("cmd", "/c") + command
        } else {
            command
        }

        val processBuilder = ProcessBuilder(finalCommand)
        processBuilder.redirectErrorStream(false)
        val environment = processBuilder.environment()

        if (envOverride != null) {
            environment.clear()
            environment.putAll(envOverride)
        }
        if (!environment.containsKey(INTERNAL_ORIGINATOR_ENV)) {
            environment[INTERNAL_ORIGINATOR_ENV] = TYPESCRIPT_SDK_ORIGINATOR
        }
        args.baseUrl?.let { environment["OPENAI_BASE_URL"] = it }
        args.apiKey?.let { environment["CODEX_API_KEY"] = it }

        val process = processBuilder.start()
        val cancelled = AtomicBoolean(false)

        args.cancellation?.invokeOnCompletion {
            cancelled.set(true)
            process.destroyForcibly()
        }

        val writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
        writer.use {
            it.write(args.input)
            it.flush()
        }

        val stderr = StringBuilder()

        val stderrJob = launch(ioDispatcher) {
            BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    stderr.appendLine(line)
                }
            }
        }

        val stdoutJob = launch(ioDispatcher) {
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    trySend(line).isSuccess
                }
            }
        }

        val waitJob = launch(ioDispatcher) {
            val exitCode = process.waitFor()
            stderrJob.join()
            stdoutJob.join()
            if (!cancelled.get() && exitCode != 0) {
                val message = stderr.toString().ifBlank { "Codex exec exited with code $exitCode" }
                close(CodexExecutionException(message))
            } else {
                close()
            }
        }

        awaitClose {
            process.destroy()
            stderrJob.cancel()
            stdoutJob.cancel()
            waitJob.cancel()
        }
    }

    private fun buildCommand(args: CodexExecArgs): List<String> {
        val command = mutableListOf(executablePath.toString(), "exec", "--experimental-json")
        args.model?.let {
            command += listOf("--model", it)
        }
        args.sandboxMode?.let {
            command += listOf("--sandbox", it.wireValue)
        }
        args.workingDirectory?.let {
            command += listOf("--cd", it)
        }
        args.additionalDirectories.forEach { dir ->
            command += listOf("--add-dir", dir)
        }
        if (args.skipGitRepoCheck) {
            command += "--skip-git-repo-check"
        }
        args.outputSchemaFile?.let {
            command += listOf("--output-schema", it.toString())
        }
        args.modelReasoningEffort?.let {
            command += listOf("--config", """model_reasoning_effort="${it.wireValue}"""")
        }
        args.networkAccessEnabled?.let {
            command += listOf("--config", "sandbox_workspace_write.network_access=$it")
        }
        args.webSearchEnabled?.let {
            command += listOf("--config", "features.web_search_request=$it")
        }
        args.approvalPolicy?.let {
            command += listOf("--config", """approval_policy="${it.wireValue}"""")
        }
        args.images.forEach { image ->
            command += listOf("--image", image.toString())
        }
        if (!args.threadId.isNullOrBlank()) {
            command += listOf("resume", args.threadId!!)
        }
        return command
    }

    private fun findCodexExecutable(): Path {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        val isWindows = os.contains("win")

        // 1. 先尝试从 PATH 查找
        try {
            val whichCommand = if (isWindows) "where" else "which"
            val binaryName = "codex"
            val process = ProcessBuilder(whichCommand, binaryName).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && result.isNotEmpty()) {
                val lines = result.lines()
                if (isWindows) {
                    // Windows: 优先选择 .cmd 文件，其次 .exe 文件
                    val cmdFile = lines.find { it.endsWith(".cmd") }
                    if (cmdFile != null) return Paths.get(cmdFile)
                    val exeFile = lines.find { it.endsWith(".exe") }
                    if (exeFile != null) return Paths.get(exeFile)
                }
                return Paths.get(lines.first())
            }
        } catch (_: Exception) {
            // 忽略，尝试下一个方法
        }

        // 2. 回退到 vendor 目录
        val arch = System.getProperty("os.arch").lowercase(Locale.ENGLISH)
        val triple = when {
            isWindows && arch.contains("64") -> "x86_64-pc-windows-msvc"
            os.contains("mac") && arch.contains("aarch64") -> "aarch64-apple-darwin"
            os.contains("mac") && arch.contains("x86") -> "x86_64-apple-darwin"
            os.contains("linux") && arch.contains("aarch64") -> "aarch64-unknown-linux-musl"
            os.contains("linux") && arch.contains("64") -> "x86_64-unknown-linux-musl"
            else -> error("Unsupported platform: $os ($arch)")
        }
        val vendorBinaryName = if (isWindows) "codex.exe" else "codex"
        return Paths.get("external", "openai-codex", "sdk", "vendor", triple, "codex", vendorBinaryName)
    }
}

