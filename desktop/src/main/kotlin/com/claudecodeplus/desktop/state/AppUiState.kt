package com.claudecodeplus.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 统一管理应用级别的UI状态，例如对话框的可见性。
 */
class AppUiState {
    var isOrganizerVisible by mutableStateOf(false)
    var isSearchVisible by mutableStateOf(false)
    var isTemplatesVisible by mutableStateOf(false)
    var isExportDialogVisible by mutableStateOf(false)
    var isSettingsVisible by mutableStateOf(false)
}
