package com.asakii.codex.agent.sdk

import java.nio.file.Path

/**
 * 全局 Codex 客户端配置。
 *
 * Java 使用示例：
 * ```java
 * // 使用默认配置
 * CodexClientOptions options = new CodexClientOptions();
 *
 * // 自定义配置
 * CodexClientOptions options = new CodexClientOptions(
 *     null,                              // codexPathOverride
 *     "https://api.example.com",         // baseUrl
 *     "your-api-key",                    // apiKey
 *     Map.of("KEY", "VALUE")             // env
 * );
 * ```
 */
data class CodexClientOptions @JvmOverloads constructor(
    /** Codex CLI 可执行文件路径覆盖 */
    @JvmField val codexPathOverride: Path? = null,
    /** API 基础 URL */
    @JvmField val baseUrl: String? = null,
    /** API 密钥 */
    @JvmField val apiKey: String? = null,
    /**
     * 传递给 Codex CLI 进程的环境变量。
     * 如果提供，则不会继承当前进程的环境。
     */
    @JvmField val env: Map<String, String>? = null,
)


