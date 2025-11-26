package com.asakii.codex.agent.sdk

import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

enum class ApprovalMode(val wireValue: String) {
    NEVER("never"),
    @SerialName("on-request")
    ON_REQUEST("on-request"),
    @SerialName("on-failure")
    ON_FAILURE("on-failure"),
    @SerialName("untrusted")
    UNTRUSTED("untrusted"),
}

enum class SandboxMode(val wireValue: String) {
    @SerialName("read-only")
    READ_ONLY("read-only"),
    @SerialName("workspace-write")
    WORKSPACE_WRITE("workspace-write"),
    @SerialName("danger-full-access")
    DANGER_FULL_ACCESS("danger-full-access"),
}

enum class ModelReasoningEffort(val wireValue: String) {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

data class ThreadOptions @JvmOverloads constructor(
    val model: String? = null,
    val sandboxMode: SandboxMode? = null,
    val workingDirectory: String? = null,
    val skipGitRepoCheck: Boolean = false,
    val modelReasoningEffort: ModelReasoningEffort? = null,
    val networkAccessEnabled: Boolean? = null,
    val webSearchEnabled: Boolean? = null,
    val approvalPolicy: ApprovalMode? = null,
    val additionalDirectories: List<String> = emptyList(),
)

data class TurnOptions @JvmOverloads constructor(
    /**
     * 预期输出的 JSON Schema。
     */
    val outputSchema: JsonObject? = null,
    /**
     * 可选的取消信号。取消时会终止底层 CLI 进程。
     */
    val cancellation: Job? = null,
)

