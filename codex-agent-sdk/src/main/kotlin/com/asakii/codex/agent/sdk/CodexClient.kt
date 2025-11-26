package com.asakii.codex.agent.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * Codex 客户端，用于管理与 Codex CLI 的交互。
 *
 * Java 使用示例：
 * ```java
 * // 使用默认选项
 * CodexClient client = new CodexClient();
 *
 * // 使用自定义选项
 * CodexClientOptions options = new CodexClientOptions(null, "https://api.example.com", "api-key", null);
 * CodexClient client = new CodexClient(options);
 * ```
 */
class CodexClient @JvmOverloads constructor(
    options: CodexClientOptions = CodexClientOptions(),
) {
    private val exec: CodexExecRunner = CodexExecProcess(options.codexPathOverride, options.env)
    private val clientOptions = options

    /**
     * 启动新的会话线程。
     *
     * Java 使用示例：
     * ```java
     * // 使用默认选项
     * CodexSession session = client.startThread();
     *
     * // 使用自定义选项
     * ThreadOptions threadOpts = new ThreadOptions(...);
     * CodexSession session = client.startThread(threadOpts);
     * ```
     */
    @JvmOverloads
    fun startThread(threadOptions: ThreadOptions = ThreadOptions()): CodexSession =
        CodexSession(exec, clientOptions, threadOptions)

    /**
     * 恢复已存在的会话线程。
     *
     * Java 使用示例：
     * ```java
     * // 使用默认选项
     * CodexSession session = client.resumeThread("thread-id-123");
     *
     * // 使用自定义选项
     * ThreadOptions threadOpts = new ThreadOptions(...);
     * CodexSession session = client.resumeThread("thread-id-123", threadOpts);
     * ```
     */
    @JvmOverloads
    fun resumeThread(threadId: String, threadOptions: ThreadOptions = ThreadOptions()): CodexSession =
        CodexSession(exec, clientOptions, threadOptions, threadId)
}

class CodexSession internal constructor(
    private val exec: CodexExecRunner,
    private val clientOptions: CodexClientOptions,
    private val threadOptions: ThreadOptions,
    threadId: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var currentThreadId: String? = threadId

    /**
     * 获取当前会话线程 ID。
     */
    val id: String?
        get() = currentThreadId

    /**
     * 执行一个回合（turn），发送文本输入并等待完整响应。
     *
     * @param input 用户输入的文本
     * @param turnOptions 回合选项
     * @return 完整的回合结果
     * @throws CodexTurnFailedException 如果回合执行失败
     *
     * Java 使用示例：
     * ```java
     * // 使用默认选项
     * TurnResult result = session.run("Hello, world!");
     *
     * // 使用自定义选项
     * TurnOptions opts = new TurnOptions(...);
     * TurnResult result = session.run("Hello!", opts);
     * ```
     */
    @JvmOverloads
    @Throws(CodexTurnFailedException::class)
    suspend fun run(input: String, turnOptions: TurnOptions = TurnOptions()): TurnResult {
        val normalized = NormalizedInput(prompt = input, images = emptyList())
        return runInternal(normalized, turnOptions)
    }

    /**
     * 执行一个回合（turn），发送多个用户输入（文本 + 图片）并等待完整响应。
     *
     * @param inputs 用户输入列表（文本和图片）
     * @param turnOptions 回合选项
     * @return 完整的回合结果
     * @throws CodexTurnFailedException 如果回合执行失败
     *
     * Java 使用示例：
     * ```java
     * List<UserInput> inputs = Arrays.asList(
     *     new UserInput.Text("Describe this image:"),
     *     new UserInput.LocalImage(Paths.get("/path/to/image.png"))
     * );
     * TurnResult result = session.run(inputs);
     * ```
     */
    @JvmOverloads
    @Throws(CodexTurnFailedException::class)
    suspend fun run(inputs: List<UserInput>, turnOptions: TurnOptions = TurnOptions()): TurnResult {
        val normalized = normalizeInput(inputs)
        return runInternal(normalized, turnOptions)
    }

    /**
     * 执行一个流式回合（turn），发送文本输入并获取事件流。
     *
     * @param input 用户输入的文本
     * @param turnOptions 回合选项
     * @return 流式回合对象，包含事件流
     *
     * Java 使用示例：
     * ```java
     * StreamedTurn turn = session.runStreamed("Hello!");
     * turn.getEvents().collect(event -> {
     *     System.out.println("Event: " + event.getType());
     * });
     * ```
     */
    @JvmOverloads
    fun runStreamed(input: String, turnOptions: TurnOptions = TurnOptions()): StreamedTurn {
        val normalized = NormalizedInput(prompt = input, images = emptyList())
        return StreamedTurn(events = runStreamedInternal(normalized, turnOptions))
    }

    /**
     * 执行一个流式回合（turn），发送多个用户输入并获取事件流。
     *
     * @param inputs 用户输入列表（文本和图片）
     * @param turnOptions 回合选项
     * @return 流式回合对象，包含事件流
     */
    @JvmOverloads
    fun runStreamed(inputs: List<UserInput>, turnOptions: TurnOptions = TurnOptions()): StreamedTurn {
        val normalized = normalizeInput(inputs)
        return StreamedTurn(events = runStreamedInternal(normalized, turnOptions))
    }

    private suspend fun runInternal(
        normalized: NormalizedInput,
        turnOptions: TurnOptions,
    ): TurnResult {
        val events = mutableListOf<ThreadItem>()
        var finalResponse = ""
        var usage: Usage? = null
        var failure: ThreadError? = null

        runStreamedInternal(normalized, turnOptions).collect { event ->
            when (event.type) {
                "thread.started" -> currentThreadId = event.threadId
                "item.completed" -> {
                    event.item?.let { item ->
                        events += item
                        if (item.type == "agent_message" && item.text != null) {
                            finalResponse = item.text
                        }
                    }
                }
                "turn.completed" -> usage = event.usage
                "turn.failed" -> {
                    failure = event.error
                    return@collect
                }
            }
        }

        if (failure != null) {
            throw CodexTurnFailedException(failure!!)
        }

        return TurnResult(
            items = events,
            finalResponse = finalResponse,
            usage = usage,
        )
    }

    private fun runStreamedInternal(
        normalized: NormalizedInput,
        turnOptions: TurnOptions,
    ): Flow<ThreadEvent> = flow {
        val schemaHandle = createOutputSchemaFile(turnOptions.outputSchema)
        try {
            val args = CodexExecArgs(
                input = normalized.prompt,
                baseUrl = clientOptions.baseUrl,
                apiKey = clientOptions.apiKey,
                threadId = currentThreadId,
                images = normalized.images,
                model = threadOptions.model,
                sandboxMode = threadOptions.sandboxMode,
                workingDirectory = threadOptions.workingDirectory,
                additionalDirectories = threadOptions.additionalDirectories,
                skipGitRepoCheck = threadOptions.skipGitRepoCheck,
                outputSchemaFile = schemaHandle.schemaPath,
                modelReasoningEffort = threadOptions.modelReasoningEffort,
                networkAccessEnabled = threadOptions.networkAccessEnabled,
                webSearchEnabled = threadOptions.webSearchEnabled,
                approvalPolicy = threadOptions.approvalPolicy,
                cancellation = turnOptions.cancellation,
            )
            exec.run(args).collect { line ->
                val event = json.decodeFromString(ThreadEvent.serializer(), line)
                if (event.type == "thread.started") {
                    currentThreadId = event.threadId
                }
                emit(event)
            }
        } finally {
            schemaHandle.cleanup()
        }
    }
}

private data class NormalizedInput(
    val prompt: String,
    val images: List<Path>,
)

private fun normalizeInput(input: List<UserInput>): NormalizedInput {
    val promptParts = mutableListOf<String>()
    val images = mutableListOf<Path>()
    input.forEach { part ->
        when (part) {
            is UserInput.Text -> promptParts.add(part.text)
            is UserInput.LocalImage -> images.add(part.path)
        }
    }
    return NormalizedInput(
        prompt = promptParts.joinToString(separator = "\n\n"),
        images = images,
    )
}

