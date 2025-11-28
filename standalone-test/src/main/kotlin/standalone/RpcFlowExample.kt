package standalone

import com.asakii.bridge.IdeTheme
import com.asakii.rpc.api.RpcConnectOptions
import com.asakii.rpc.api.RpcTextBlock
import com.asakii.server.rpc.AiAgentRpcServiceImpl
import com.asakii.server.tools.DiffRequest
import com.asakii.server.tools.FileInfo
import com.asakii.server.tools.IdeTools
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * 直接在 JVM 中演示如何通过 AiAgentRpcServiceImpl + DTO 消费流式事件。
 *
 * 运行方式（可覆盖 mainClass）：
 * ./gradlew :standalone-test:run -PmainClass=standalone.RpcFlowExampleKt
 *
 * 依赖环境变量：
 * - CLAUDE_API_KEY 或其它底层 Provider 所需的密钥
 */
fun main() = runBlocking {
    val rpcService = AiAgentRpcServiceImpl(ConsoleIdeTools)

    try {
        val connectResult = rpcService.connect(
            RpcConnectOptions(
                model = System.getenv("AI_AGENT_MODEL"),
                // Claude 相关配置（统一扁平结构）
                includePartialMessages = true,
                dangerouslySkipPermissions = true,
                allowDangerouslySkipPermissions = true
            )
        )
        println("✅ Connected session=${connectResult.sessionId} provider=${connectResult.provider}")

        rpcService.queryWithContent(
            listOf(
                RpcTextBlock(text = "简单介绍一下 Kotlin Flow 的作用，并给我一个简略示例。")
            )
        ).collect { event ->
            println("→ ${event::class.simpleName}: $event")
        }
    } finally {
        rpcService.disconnect()
    }
}

private object ConsoleIdeTools : IdeTools {
    override fun openFile(path: String, line: Int, column: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Standalone sample - openFile not supported"))

    override fun showDiff(request: DiffRequest): Result<Unit> =
        Result.failure(UnsupportedOperationException("Standalone sample - showDiff not supported"))

    override fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> =
        Result.success(emptyList())

    override fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> =
        Result.failure(UnsupportedOperationException("Standalone sample - getFileContent not supported"))

    override fun getRecentFiles(maxResults: Int): Result<List<FileInfo>> =
        Result.success(emptyList())

    override fun getTheme(): IdeTheme = IdeTheme(isDark = true)

    override fun getProjectPath(): String = System.getProperty("user.dir")

    override fun getLocale(): String = "en-US"

    override fun setLocale(locale: String): Result<Unit> = Result.success(Unit)
}

