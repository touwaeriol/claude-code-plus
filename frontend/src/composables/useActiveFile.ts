/**
 * Active File Composable
 * Manages IDEA's active file state (pushed from IDE)
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ideaRSocket, type ActiveFileInfo } from '@/services/ideaRSocket'
import { useSessionStore } from '@/stores/sessionStore'

export { type ActiveFileInfo }

export function useActiveFile() {
  const sessionStore = useSessionStore()

  // State
  const currentActiveFile = ref<ActiveFileInfo | null>(null)
  let unsubscribe: (() => void) | null = null

  // activeFileDisabled from sessionStore, independent per Tab
  const activeFileDisabled = computed({
    get: () => {
      const state = sessionStore.currentTab?.uiState as any
      return state?.activeFileDisabled ?? state?.activeFileDismissed ?? false
    },
    set: (value: boolean) => {
      const state = sessionStore.currentTab?.uiState as any
      if (state) {
        state.activeFileDisabled = value
      }
    }
  })

  // Computed: has active file
  const hasActiveFile = computed(() => {
    return currentActiveFile.value !== null
  })

  // Computed: active file is enabled for sending
  const activeFileEnabled = computed(() => {
    return hasActiveFile.value && !activeFileDisabled.value
  })

  /**
   * Extract filename from path
   */
  function getFileName(filePath: string): string {
    const parts = filePath.replace(/\\/g, '/').split('/')
    return parts[parts.length - 1] || filePath
  }

  // Computed: active file name (may be truncated)
  const activeFileName = computed(() => {
    if (!currentActiveFile.value) return ''
    return getFileName(currentActiveFile.value.relativePath)
  })

  // Computed: active file line range (including column info)
  const activeFileLineRange = computed(() => {
    if (!currentActiveFile.value) return ''
    const file = currentActiveFile.value
    if (file.hasSelection && file.startLine && file.endLine) {
      // Selection: show start line:column - end line:column
      const startCol = file.startColumn || 1
      const endCol = file.endColumn || 1
      return `:${file.startLine}:${startCol}-${file.endLine}:${endCol}`
    } else if (file.line) {
      // Cursor: show line:column
      const col = file.column || 1
      return `:${file.line}:${col}`
    }
    return ''
  })

  // Computed: active file display text (filename + range) - kept for compatibility
  const activeFileDisplayText = computed(() => {
    if (!currentActiveFile.value) return ''
    const file = currentActiveFile.value
    const fileName = getFileName(file.relativePath)
    if (file.hasSelection && file.startLine && file.endLine) {
      // Show line range when selected
      return `${fileName}:${file.startLine}-${file.endLine}`
    } else if (file.line) {
      // Show line number when cursor positioned
      return `${fileName}:${file.line}`
    }
    return fileName
  })

  // Computed: active file tooltip
  const activeFileTooltip = computed(() => {
    if (!currentActiveFile.value) return ''
    return currentActiveFile.value.path || currentActiveFile.value.relativePath || ''
  })

  /**
   * Toggle active file enabled/disabled
   */
  function toggleActiveFileEnabled() {
    if (!currentActiveFile.value) return
    activeFileDisabled.value = !activeFileDisabled.value
  }

  /**
   * Get active file for sending (if enabled)
   */
  function getActiveFileForSending(): ActiveFileInfo | null {
    if (!activeFileEnabled.value) return null
    return currentActiveFile.value
  }

  /**
   * Subscribe to active file changes
   */
  function subscribe() {
    // Subscribe to active file changes
    unsubscribe = ideaRSocket.onActiveFileChange((file) => {
      currentActiveFile.value = file
      // Keep disabled state as-is when file changes — user must manually re-enable
      console.log('📂 [useActiveFile] Active file updated:', file?.relativePath || 'none')
    })

    // Get current active file on init (handle case where IDE already has file open when frontend starts)
    ideaRSocket.getActiveFile().then((file) => {
      if (file) {
        currentActiveFile.value = file
        console.log('📂 [useActiveFile] Initial active file:', file.relativePath)
      }
    }).catch((error) => {
      console.warn('📂 [useActiveFile] Failed to get initial active file:', error)
    })
  }

  /**
   * Unsubscribe from active file changes
   */
  function unsubscribeFromChanges() {
    if (unsubscribe) {
      unsubscribe()
      unsubscribe = null
    }
  }

  // Auto subscribe/unsubscribe with component lifecycle
  onMounted(() => {
    subscribe()
  })

  onUnmounted(() => {
    unsubscribeFromChanges()
  })

  return {
    // State
    currentActiveFile,
    activeFileDisabled,
    // Computed
    hasActiveFile,
    activeFileEnabled,
    activeFileName,
    activeFileLineRange,
    activeFileDisplayText,
    activeFileTooltip,
    // Methods
    toggleActiveFileEnabled,
    getActiveFileForSending,
    getFileName,
    // Lifecycle management (for manual control if needed)
    subscribe,
    unsubscribeFromChanges
  }
}
