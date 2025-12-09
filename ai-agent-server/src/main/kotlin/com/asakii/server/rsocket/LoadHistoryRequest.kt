package com.asakii.server.rsocket

import kotlinx.serialization.Serializable

@Serializable
data class LoadHistoryRequest(
    val sessionId: String? = null,
    val projectPath: String? = null,
    val offset: Int = 0,
    val limit: Int = 0
)
