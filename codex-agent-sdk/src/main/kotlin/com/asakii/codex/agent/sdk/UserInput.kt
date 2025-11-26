package com.asakii.codex.agent.sdk

import java.nio.file.Path

sealed interface UserInput {
    data class Text(val text: String) : UserInput
    data class LocalImage(val path: Path) : UserInput
}


