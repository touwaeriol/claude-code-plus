package com.asakii.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 前后端通信协议定义
 */

@Serializable
data class FrontendRequest(
    val action: String,
    val data: JsonElement? = null
)

@Serializable
data class FrontendResponse(
    val success: Boolean,
    val data: Map<String, JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class IdeEvent(
    val type: String,
    val data: Map<String, JsonElement>? = null
)

// IdeTheme 已移至 com.asakii.rpc.api.IdeTheme

