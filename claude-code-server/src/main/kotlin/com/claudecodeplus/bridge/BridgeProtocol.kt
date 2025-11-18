package com.claudecodeplus.bridge

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

@Serializable
data class IdeTheme(
    val isDark: Boolean,
    val background: String = "",
    val foreground: String = "",
    val borderColor: String = "",
    val panelBackground: String = "",
    val textFieldBackground: String = "",
    val selectionBackground: String = "",
    val selectionForeground: String = "",
    val linkColor: String = "",
    val errorColor: String = "",
    val warningColor: String = "",
    val successColor: String = "",
    val separatorColor: String = "",
    val hoverBackground: String = "",
    val accentColor: String = "",
    val infoBackground: String = "",
    val codeBackground: String = "",
    val secondaryForeground: String = ""
)

