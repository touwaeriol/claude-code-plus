package com.claudecodeplus.ui.jewel.extension

import com.claudecodeplus.ui.models.ContextReference
import java.net.URI

fun ContextReference.toDisplayString(): String {
    return when (this) {
        is ContextReference.FileReference -> this.path.substringAfterLast('/')
        is ContextReference.SymbolReference -> this.name.substringAfterLast('.')
        is ContextReference.WebReference -> {
            runCatching { URI(this.url).host }.getOrDefault(this.url)
        }
        is ContextReference.GitReference -> "Git: ${this.type.name.lowercase()}"
        is ContextReference.ImageReference -> this.filename
        is ContextReference.TerminalReference -> "Terminal"
        is ContextReference.FolderReference -> this.path.substringAfterLast('/')
        is ContextReference.ProblemsReference -> "Problems (${this.problems.size})"
        is ContextReference.SelectionReference -> "Selection"
        is ContextReference.WorkspaceReference -> "Workspace"
    }
} 