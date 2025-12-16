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
    copyFailed: 'Copy failed',
    copied: 'Copied',
    renderFailed: 'Render failed',
    noMore: 'No more'
  },
  chat: {
    placeholder: '',
    placeholderWithShortcuts: '',
    placeholderWithShortcutsCtrl: '',
    input: {
      connecting: 'Initializing connection...',
      disconnected: 'Disconnected, please refresh the page'
    },
    newSession: 'New Session',
    history: 'History',
    thinking: 'Thinking...',
    retry: 'Retry',
    stop: 'Stop',
    stopGenerating: 'Stop Generating',
    escToInterrupt: 'ESC to interrupt',
    streamingStatsTooltip: 'Stats: Duration ↑Input tokens ↓Output tokens',
    slashCommands: 'Slash Commands',
    noMatchingCommands: 'No matching commands',
    welcome: 'Hello! I am Claude, your AI coding assistant.',
    emptyState: 'Start a new session or select one from history.',
    sendMessage: 'Send Message',
    sendMessageShortcut: 'Send Message (Enter) | Right-click for more options',
    addContext: "{'@'} Add Context",
    autoCleanupContext: 'Auto Cleanup Context',
    autoCleanupContextTooltip: 'Automatically clear context tags after sending message',
    dropFileToAddContext: 'Drop files here to add to context',
    taskQueue: 'Task Queue',
    taskQueueCount: 'Task Queue ({count})',
    pendingQueue: 'Pending ({count})',
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
      sendMessageFailed: 'Failed to send message: {message}',
      connecting: 'Initializing connection, please wait...',
      disconnected: 'Disconnected, reconnecting...'
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
    noMessages: 'No messages. Please create a session and send a message.',
    thinkingLabel: 'Thinking',
    thinkingCollapsed: 'Thinking complete (click to expand)',
    generating: 'Generating...',
    actualModel: 'Actual model',
    uploadImage: 'Upload image',
    interruptAndSend: 'Interrupt & Send',
    interruptAndSendShortcut: 'Interrupt and send (Ctrl+Enter)',
    moreContexts: '{count} more contexts',
    tokenTooltip: 'Input: {input}, Output: {output}, Cache creation: {cacheCreation}, Cache read: {cacheRead}',
    welcomeScreen: {
      title: 'Start a conversation with Claude',
      description: 'Enter your questions or ideas, Claude will help you write code and answer questions',
      askCode: 'Ask code questions',
      refactor: 'Refactor existing code',
      debug: 'Debug errors',
      sendHint: 'send message',
      newLineHint: 'new line',
      interruptHint: 'interrupt & send',
      stopHint: 'stop generation',
      toggleThinkingHint: 'toggle thinking',
      switchModeHint: 'switch mode',
      clearToLineStartHint: 'clear to line start'
    },
    connectionStatus: {
      connected: 'Connected',
      connecting: 'Connecting...',
      disconnected: 'Disconnected',
      generating: 'Generating...'
    },
    claudeThinking: 'Claude is thinking...',
    loadingHistory: 'Loading history...',
    scrollToBottom: 'Scroll to bottom'
  },
  tools: {
    error: 'Error',
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
    copyContent: 'Copy Content',
    openInEditor: 'Open in editor',
    parsingParams: 'Parsing parameters...',
    ctrlBToBackground: 'Ctrl+B to background',
    contentTruncated: 'Content truncated',
    noOutput: 'No output',
    replaceAll: 'Replace all',
    changeNumber: 'Change #{number}',
    questions: '{count} questions',
    subtask: 'Subtask',
    newContent: 'New content',
    label: {
      path: 'Path',
      pattern: 'Pattern',
      lines: 'Lines',
      start: 'Start',
      duration: 'Duration',
      filter: 'Filter',
      output: 'Output',
      result: 'Result',
      searchPattern: 'Search pattern',
      searchPath: 'Search path',
      fileFilter: 'File filter',
      fileType: 'File type',
      outputMode: 'Output mode',
      cell: 'Cell',
      mode: 'Mode',
      agentType: 'Agent type',
      model: 'Model',
      prompt: 'Prompt',
      query: 'Search query',
      allowedDomains: 'Allowed domains',
      blockedDomains: 'Blocked domains'
    },
    readTool: {
      reading: 'Reading file...',
      readResult: 'Read result'
    },
    grepTool: {
      outputModes: {
        content: 'Content',
        filesWithMatches: 'Files with matches',
        count: 'Count'
      },
      options: {
        ignoreCase: 'Ignore case',
        showLineNumbers: 'Show line numbers',
        multiline: 'Multiline'
      }
    },
    todoTool: {
      pending: 'Pending',
      inProgress: 'In progress',
      completed: 'Completed',
      tasksCount: '{n} tasks'
    },
    multiEdit: {
      changes: '{n} changes'
    },
    writeTool: {
      lines: '{n} lines'
    }
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
    defaultName: 'Session {time}',
    close: 'Close session',
    sessionId: 'Session ID',
    copyHint: 'Click again or double-click to copy',
    copySuccess: 'Session ID copied',
    copyFailed: 'Copy failed',
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
    searchFailed: 'Search failed',
    history: 'Session History',
    sessionCount: 'sessions',
    noHistory: 'No history',
    active: 'Active',
    noActive: 'No active sessions',
    historySection: 'History',
    unnamed: 'Unnamed session',
    empty: 'No active sessions',
    messages: 'messages',
    loadHistoryFailed: 'Failed to load history sessions',
    deleteSuccess: 'Session deleted',
    deleteFailed: 'Failed to delete session',
    loadMore: 'Load more',
    scrollToLoadMore: 'Scroll down to load more',
    selected: 'Selected'
  },
  time: {
    justNow: 'Just now',
    minutesAgo: '{n} min ago',
    hoursAgo: '{n} hr ago',
    daysAgo: '{n} days ago'
  },
  thinking: {
    label: 'Thinking',
    enabled: 'Enabled',
    disabled: 'Disabled',
    alwaysOn: 'Thinking is always enabled for this model',
    notSupported: 'This model does not support thinking',
    toggleOn: 'Click to enable thinking',
    toggleOff: 'Click to disable thinking'
  },
  context: {
    usage: 'Context usage: {used} / {max} tokens ({percentage}%)',
    critical: '🚨 Context window is almost full! Consider starting a new conversation',
    warning: '💡 Context is half full, manage carefully'
  },
  keyboard: {
    sendMessage: 'Send Message',
    closeDialog: 'Close Dialog'
  },
  permission: {
    needsAuth: 'Needs Authorization',
    allow: 'Allow',
    deny: 'Deny',
    denyReasonPlaceholder: 'Reason for denial (optional)',
    noParams: 'No parameters',
    confirm: 'Confirm',
    escToDeny: 'Press ESC to deny',
    viewInIdea: 'View in IDEA',
    expand: 'Expand',
    collapse: 'Collapse',
    edits: 'edits',
    planReady: 'Plan is ready',
    noPlanContent: 'No plan content available',
    replace: 'Replace',
    with: 'With',
    destination: {
      session: 'this session',
      projectSettings: 'project settings',
      userSettings: 'user settings',
      localSettings: 'local settings'
    },
    suggestion: {
      rememberTo: 'Remember {tool} to {dest}',
      rememberWithRuleTo: 'Remember {tool}({rule}) to {dest}',
      replaceTo: 'Replace rules to {dest}',
      removeFrom: 'Remove {tool} from {dest}',
      removeRulesFrom: 'Remove rules from {dest}',
      switchTo: 'Switch to {mode}',
      applyTo: 'Apply to {dest}',
      addDirTo: 'Add directory {dir} to {dest}',
      removeDirFrom: 'Remove directory {dir} from {dest}'
    },
    mode: {
      default: 'default mode',
      acceptEdits: 'auto-accept edits',
      plan: 'plan mode',
      bypassPermissions: 'bypass permissions',
      bypass: 'Bypass',
      bypassTooltip: 'Skip all permission confirmations and execute directly',
      dontAsk: "don't ask"
    },
    editPreviewTitle: 'Edit Preview',
    multiEditPreviewTitle: 'Multi-Edit Preview',
    planPreviewTitle: 'Plan Preview'
  }
  ,
  system: {
    interrupted: '[Request interrupted by user]'
  },
  compact: {
    compacting: 'Compacting conversation context...',
    contextRestored: 'Context restored',
    sessionSummary: '(Session summary)',
    expand: 'Expand',
    collapse: 'Collapse',
    originalTokens: 'Original tokens'
  },
  slashCommand: {
    compact: 'Compact current session context',
    context: 'Show current session context info',
    rename: 'Rename current session'
  },
  askUser: {
    title: 'Claude needs your answer',
    typeSomething: 'Type something...',
    cancel: 'Cancel',
    submit: 'Submit',
    answer: 'Answer'
  }
}
