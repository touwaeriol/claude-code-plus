export default {
  common: {
    send: 'Send',
    cancel: 'Cancel',
    confirm: 'Confirm',
    save: 'Save',
    delete: 'Delete',
    edit: 'Edit',
    copy: 'Copy',
    close: 'Close',
    loading: 'Loading...',
    success: 'Success',
    error: 'Error',
    search: 'Search',
    clear: 'Clear',
    settings: 'Settings',
    tools: 'Tools',
    unknown: 'Unknown',
    remove: 'Remove',
    yes: 'Yes',
    no: 'No',
    ok: 'OK',
    pending: 'Pending',
    cancelled: 'Cancelled',
    copyFailed: 'Copy failed'
  },
  chat: {
    placeholder: 'Type a message...',
    placeholderWithShortcuts: 'Type a message... (Enter to send, Shift+Enter for new line, Alt+Enter to interrupt)',
    placeholderWithShortcutsCtrl: 'Type a message... (Ctrl+Enter to send, Shift+Enter for new line)',
    newSession: 'New Session',
    history: 'History',
    thinking: 'Thinking...',
    retry: 'Retry',
    stop: 'Stop',
    stopGenerating: 'Stop Generating',
    welcome: 'Hello! I am Claude, your AI coding assistant.',
    emptyState: 'Start a new session or select one from history.',
    sendMessage: 'Send Message',
    sendMessageShortcut: 'Send Message (Enter) | Right-click for more options',
    addContext: 'Add Context',
    autoCleanupContext: 'Auto Cleanup Context',
    autoCleanupContextTooltip: 'Automatically clear context tags after sending message',
    dropFileToAddContext: 'Drop files here to add to context',
    taskQueue: 'Task Queue',
    taskQueueCount: 'Task Queue ({count})',
    taskStatus: {
      pending: 'Pending',
      running: 'Running',
      success: 'Success',
      failed: 'Failed'
    },
    error: {
      title: 'Error',
      unknown: 'Unknown error',
      initSessionFailed: 'Failed to initialize session: {message}',
      switchSessionFailed: 'Failed to switch session: {message}',
      sendMessageFailed: 'Failed to send message: {message}'
    },
    debug: {
      title: 'Debug Info',
      sessionId: 'Session ID',
      projectPath: 'Project Path',
      messageCount: 'Message Count',
      generating: 'Generating',
      pendingTasks: 'Pending Tasks',
      contexts: 'Contexts',
      notSet: 'Not Set',
      generatingStatus: 'Generating'
    },
    enterToSend: 'Enter to send message ·',
    noMessages: 'No messages. Please create a session and send a message.'
  },
  tools: {
    read: 'Read File',
    write: 'Write File',
    edit: 'Edit File',
    bash: 'Execute Command',
    search: 'Search Files',
    status: {
      pending: 'Pending',
      running: 'Running',
      runningWithDots: 'Running...',
      completed: 'Completed',
      failed: 'Failed',
      cancelled: 'Cancelled',
      success: 'Success'
    },
    editSuccess: 'Edit successful',
    editFailed: 'Edit failed',
    confirmed: 'Confirmed',
    terminated: 'Terminated',
    copyContent: 'Copy Content'
  },
  settings: {
    title: 'Settings',
    language: 'Language',
    theme: 'Theme',
    autoScroll: 'Auto Scroll',
    fontSize: 'Font Size',
    autoLoadContext: 'Automatically load last conversation context when creating a new session.',
    temperature: 'Temperature',
    temperatureDescription: 'Controls model creativity. 0 = deterministic, 1 = highly creative. Leave empty to use default value.'
  },
  session: {
    group: {
      today: 'Today',
      yesterday: 'Yesterday',
      sevenDays: 'Previous 7 Days',
      thirtyDays: 'Previous 30 Days',
      older: 'Older'
    },
    search: 'Search sessions...',
    export: 'Export Session',
    editGroup: 'Edit Group',
    createGroup: 'Create Group',
    editTag: 'Edit Tag',
    createTag: 'Create Tag',
    deleteSession: 'Delete Session',
    searchFailed: 'Search failed'
  },
  context: {
    usage: 'Context usage: {used} / {max} tokens ({percentage}%)',
    critical: '🚨 Context window is almost full! Consider starting a new conversation',
    warning: '💡 Context is half full, manage carefully'
  },
  keyboard: {
    sendMessage: 'Send Message',
    closeDialog: 'Close Dialog'
  }
}

