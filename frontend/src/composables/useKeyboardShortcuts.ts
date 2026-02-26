/**
 * Keyboard Shortcuts Composable
 * Manages keyboard event handling for ChatInput component
 */
import { onMounted, onUnmounted, nextTick, type Ref, type ComputedRef } from 'vue'
import type { BackendType } from '@/types/backend'

/**
 * RichTextInput instance interface (minimal required methods)
 */
export interface RichTextInputRef {
  insertNewLine: () => void
  deleteToLineStart: () => void
}

/**
 * Options for useKeyboardShortcuts composable
 */
export interface KeyboardShortcutsOptions {
  // Props (reactive)
  isGenerating: ComputedRef<boolean>
  enabled: ComputedRef<boolean>
  inline: ComputedRef<boolean>
  backendType: ComputedRef<BackendType>
  
  // Refs
  richTextInputRef: Ref<RichTextInputRef | undefined>
  showThinkingConfig: Ref<boolean>
  
  // Composable functions - @ symbol
  checkAtSymbol: () => void
  
  // Composable functions - permission modes
  cyclePermissionMode: () => void
  cycleCodexSandboxMode: () => void
  
  // Composable functions - thinking
  toggleThinkingEnabled: (source: 'keyboard' | 'click') => Promise<void>
  cycleCodexReasoningEffort: () => void
  
  // Composable functions - send
  handleForceSend: () => void
  
  // Emits
  onStop: () => void
  onCancel: () => void
}

/**
 * Composable for handling keyboard shortcuts in ChatInput
 */
export function useKeyboardShortcuts(options: KeyboardShortcutsOptions) {
  const {
    isGenerating,
    enabled,
    inline,
    backendType,
    richTextInputRef,
    showThinkingConfig,
    checkAtSymbol,
    cyclePermissionMode,
    cycleCodexSandboxMode,
    toggleThinkingEnabled,
    cycleCodexReasoningEffort,
    handleForceSend,
    onStop,
    onCancel
  } = options

  /**
   * Main keydown handler for input element
   * Handles all keyboard shortcuts: ESC, Tab, Shift+Tab, Ctrl+Enter, Ctrl+B, Ctrl+J, Ctrl+U, Shift+Enter
   */
  async function handleKeydown(event: KeyboardEvent) {
    // Arrow keys - trigger @ symbol check
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) {
      nextTick(() => checkAtSymbol())
    }

    // ESC key handling
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation() // Prevent global listener from re-triggering
      
      // If thinking config panel is open, close it first
      if (showThinkingConfig.value) {
        showThinkingConfig.value = false
        return
      }
      
      // If generating, stop generation
      if (isGenerating.value) {
        onStop()
        return
      }
      
      // If inline mode, cancel edit
      if (inline.value) {
        onCancel()
        return
      }
    }

    // Shift + Tab - cycle permission/sandbox mode
    if (
      event.key === 'Tab' &&
      event.shiftKey &&
      !event.ctrlKey &&
      !event.metaKey
    ) {
      event.preventDefault()
      if (backendType.value === 'claude') {
        cyclePermissionMode()
      } else if (backendType.value === 'codex') {
        cycleCodexSandboxMode()
      }
      return
    }

    // Tab - toggle thinking (Claude) / cycle reasoning effort (Codex)
    if (
      event.key === 'Tab' &&
      !event.shiftKey &&
      !event.ctrlKey &&
      !event.metaKey
    ) {
      event.preventDefault()
      if (backendType.value === 'claude') {
        await toggleThinkingEnabled('keyboard')
      } else if (backendType.value === 'codex') {
        cycleCodexReasoningEffort()
      }
      return
    }

    // Ctrl+Enter - force send (interrupt current generation and send)
    if (event.key === 'Enter' && event.ctrlKey && !event.shiftKey && !event.altKey) {
      event.preventDefault()
      handleForceSend()
      return
    }

    // Shift+Enter - insert newline (default behavior)
    if (event.key === 'Enter' && event.shiftKey) {
      // Default behavior already inserts newline, no extra handling needed
      return
    }

    // Ctrl+J - insert newline (browser default is not newline)
    if (event.key === 'j' && event.ctrlKey && !event.shiftKey && !event.altKey) {
      event.preventDefault()
      richTextInputRef.value?.insertNewLine()
      return
    }

    // Ctrl+U - delete from cursor to line start
    if (event.key === 'u' && event.ctrlKey && !event.shiftKey && !event.altKey) {
      event.preventDefault()
      richTextInputRef.value?.deleteToLineStart()
      return
    }

    // Enter key is handled by RichTextInput's @submit event, not here
  }

  /**
   * Global keydown handler (listens on document)
   * Used to ensure ESC can stop generation regardless of focus state
   */
  function handleGlobalKeydown(event: KeyboardEvent) {
    // ESC to stop generation (global listener ensures response from any focus state)
    if (event.key === 'Escape' && isGenerating.value) {
      event.preventDefault()
      event.stopPropagation()
      console.log('🛑 [GlobalKeydown] ESC pressed, stopping generation')
      onStop()
    }
  }

  // Lifecycle - mount/unmount global listeners
  onMounted(() => {
    document.addEventListener('keydown', handleGlobalKeydown)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleGlobalKeydown)
  })

  return {
    handleKeydown
  }
}
