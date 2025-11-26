package com.asakii.claude.agent.sdk.plugin.types

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class SessionState(
    var lastFileSize: Long = 0,
    var lastLineCount: Int = 0,
    var lastModified: Long = 0,
    val messageCache: MutableList<SessionMessage> = mutableListOf(),
    val messages: List<SessionMessage> = emptyList(),
    var isGenerating: Boolean = false
)

sealed class SessionUpdate {
    data class NewMessage(val message: SessionMessage) : SessionUpdate()
    data class Compressed(val messageCount: Int) : SessionUpdate()
    data class Error(val error: Throwable) : SessionUpdate()
}

data class SessionMessage(
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)