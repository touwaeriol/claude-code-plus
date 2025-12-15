export default {
  common: {
    send: '发送',
    cancel: '取消',
    confirm: '确认',
    save: '保存',
    delete: '删除',
    edit: '编辑',
    copy: '复制',
    close: '关闭',
    loading: '加载中...',
    success: '成功',
    error: '错误',
    search: '搜索',
    clear: '清空',
    settings: '设置',
    tools: '工具',
    unknown: '未知',
    remove: '移除',
    yes: '是',
    no: '否',
    ok: '确定',
    pending: '等待',
    cancelled: '已取消',
    copyFailed: '复制失败',
    copied: '已复制',
    renderFailed: '渲染失败',
    noMore: '没有更多了'
  },
  chat: {
    placeholder: '',
    placeholderWithShortcuts: '',
    placeholderWithShortcutsCtrl: '',
    input: {
      connecting: '正在初始化连接...',
      disconnected: '连接已断开，请刷新页面'
    },
    newSession: '新会话',
    history: '历史记录',
    thinking: '思考中...',
    retry: '重试',
    stop: '停止',
    stopGenerating: '停止生成',
    escToInterrupt: 'ESC 打断',
    streamingStatsTooltip: '生成统计：耗时 ↑上行tokens ↓下行tokens',
    slashCommands: '斜杠命令',
    noMatchingCommands: '没有匹配的命令',
    welcome: '你好！我是 Claude，你的 AI 编程助手。',
    emptyState: '开始一个新的会话，或选择一个历史记录。',
    sendMessage: '发送消息',
    sendMessageShortcut: '发送消息 (Enter) | 右键查看更多选项',
    addContext: "{'@'} 添加上下文",
    autoCleanupContext: '自动清理上下文',
    autoCleanupContextTooltip: '发送消息后自动清空上下文标签',
    dropFileToAddContext: '释放文件以添加到上下文',
    taskQueue: '任务队列',
    taskQueueCount: '任务队列 ({count})',
    pendingQueue: '待发送 ({count})',
    taskStatus: {
      pending: '排队中',
      running: '执行中',
      success: '成功',
      failed: '失败'
    },
    error: {
      title: '错误',
      unknown: '未知错误',
      initSessionFailed: '初始化会话失败: {message}',
      switchSessionFailed: '切换会话失败: {message}',
      sendMessageFailed: '发送消息失败: {message}',
      connecting: '正在初始化连接，请稍候...',
      disconnected: '连接已断开，正在尝试重新连接...'
    },
    debug: {
      title: '调试信息',
      sessionId: '会话ID',
      projectPath: '项目路径',
      messageCount: '消息数',
      generating: '生成中',
      pendingTasks: '待处理任务',
      contexts: '上下文',
      notSet: '未设置',
      generatingStatus: '正在生成中'
    },
    enterToSend: 'Enter 发送消息 ·',
    noMessages: '没有消息。请创建会话并发送消息。',
    thinkingLabel: '思考过程',
    thinkingCollapsed: '思考完成 (点击展开)',
    generating: '生成中...',
    actualModel: '实际模型',
    uploadImage: '上传图片',
    interruptAndSend: '打断发送',
    interruptAndSendShortcut: '打断并发送 (Ctrl+Enter)',
    moreContexts: '还有 {count} 个上下文',
    tokenTooltip: '输入: {input}, 输出: {output}, 缓存创建: {cacheCreation}, 缓存读取: {cacheRead}',
    welcomeScreen: {
      title: '开始与 Claude 对话',
      description: '输入您的问题或想法，Claude 将帮助您编写代码、解答疑问',
      askCode: '询问代码问题',
      refactor: '重构现有代码',
      debug: '调试错误',
      sendHint: '发送消息',
      newLineHint: '换行',
      interruptHint: '打断发送',
      stopHint: '停止生成',
      toggleThinkingHint: '开关思考',
      switchModeHint: '切换模式',
      clearToLineStartHint: '清空到行首'
    },
    connectionStatus: {
      connected: '已连接',
      connecting: '正在连接...',
      disconnected: '未连接',
      generating: '生成中...'
    },
    claudeThinking: 'Claude 正在思考...',
    loadingHistory: '正在加载历史会话...',
    scrollToBottom: '回到底部'
  },
  tools: {
    error: '错误',
    read: '读取文件',
    write: '写入文件',
    edit: '编辑文件',
    bash: '执行命令',
    search: '搜索文件',
    status: {
      pending: '等待',
      running: '执行中',
      runningWithDots: '执行中...',
      completed: '已完成',
      failed: '失败',
      cancelled: '已取消',
      success: '成功'
    },
    editSuccess: '编辑成功',
    editFailed: '编辑失败',
    confirmed: '已确认',
    terminated: '已终止',
    copyContent: '复制内容',
    openInEditor: '在编辑器中打开',
    parsingParams: '正在解析参数...',
    contentTruncated: '内容已截断',
    noOutput: '无输出',
    replaceAll: '全部替换',
    changeNumber: '修改 #{number}',
    questions: '{count} 个问题',
    subtask: '子任务',
    newContent: '新内容',
    label: {
      path: '路径',
      pattern: '模式',
      lines: '行数',
      start: '开始',
      duration: '耗时',
      filter: '过滤器',
      output: '输出内容',
      result: '结果',
      searchPattern: '搜索模式',
      searchPath: '搜索路径',
      fileFilter: '文件过滤',
      fileType: '文件类型',
      outputMode: '输出模式',
      cell: '单元',
      mode: '模式',
      agentType: '代理类型',
      model: '模型',
      prompt: '提示',
      query: '搜索查询',
      allowedDomains: '允许域名',
      blockedDomains: '屏蔽域名'
    },
    readTool: {
      reading: '正在读取文件...',
      readResult: '读取结果'
    },
    grepTool: {
      outputModes: {
        content: '内容',
        filesWithMatches: '匹配文件',
        count: '计数'
      },
      options: {
        ignoreCase: '忽略大小写',
        showLineNumbers: '显示行号',
        multiline: '多行匹配'
      }
    },
    todoTool: {
      pending: '待处理',
      inProgress: '进行中',
      completed: '已完成',
      tasksCount: '{n}项任务'
    },
    multiEdit: {
      changes: '{n}处'
    },
    writeTool: {
      lines: '{n}行'
    }
  },
  settings: {
    title: '设置',
    language: '语言',
    theme: '主题',
    autoScroll: '自动滚动',
    fontSize: '字体大小',
    autoLoadContext: '新建会话时自动加载上次对话的上下文。',
    temperature: '温度',
    temperatureDescription: '控制模型的创造性。0 = 确定性, 1 = 高创造性。留空使用默认值。'
  },
  session: {
    defaultName: '会话 {time}',
    close: '关闭会话',
    sessionId: '会话 ID',
    copyHint: '再次单击或双击复制',
    copySuccess: '会话 ID 已复制',
    copyFailed: '复制失败',
    group: {
      today: '今天',
      yesterday: '昨天',
      sevenDays: '7天内',
      thirtyDays: '30天内',
      older: '更早'
    },
    search: '搜索会话...',
    export: '导出会话',
    editGroup: '编辑分组',
    createGroup: '创建分组',
    editTag: '编辑标签',
    createTag: '创建标签',
    deleteSession: '删除会话',
    searchFailed: '搜索失败',
    history: '会话历史',
    sessionCount: '个会话',
    noHistory: '暂无历史',
    active: '激活中',
    noActive: '无激活会话',
    historySection: '历史',
    unnamed: '未命名会话',
    empty: '暂无活动会话',
    messages: '条消息',
    loadHistoryFailed: '加载历史会话失败'
  },
  time: {
    justNow: '刚刚',
    minutesAgo: '{n} 分钟前',
    hoursAgo: '{n} 小时前',
    daysAgo: '{n} 天前'
  },
  thinking: {
    label: '思考',
    enabled: '已开启',
    disabled: '已关闭',
    alwaysOn: '此模型强制开启思考',
    notSupported: '此模型不支持思考',
    toggleOn: '点击开启思考',
    toggleOff: '点击关闭思考'
  },
  context: {
    usage: '上下文使用: {used} / {max} tokens ({percentage}%)',
    critical: '🚨 上下文窗口即将用完！建议立即开启新对话',
    warning: '💡 上下文已使用一半，注意管理'
  },
  keyboard: {
    sendMessage: '发送消息',
    closeDialog: '关闭对话框'
  },
  permission: {
    needsAuth: '需要授权',
    allow: '允许',
    deny: '不允许',
    denyReasonPlaceholder: '拒绝原因（可选）',
    noParams: '无参数信息',
    confirm: '确认',
    escToDeny: '按 ESC 直接拒绝',
    viewInIdea: '在 IDEA 中查看',
    expand: '展开',
    collapse: '收起',
    edits: '处修改',
    planReady: '计划已准备',
    noPlanContent: '无计划内容',
    replace: '替换',
    with: '为',
    editPreviewTitle: '编辑预览',
    multiEditPreviewTitle: '多处编辑预览',
    planPreviewTitle: '计划预览',
    destination: {
      session: '本次会话',
      projectSettings: '项目设置',
      userSettings: '用户设置',
      localSettings: '本地设置'
    },
    suggestion: {
      rememberTo: '记住 {tool} 到{dest}',
      rememberWithRuleTo: '记住 {tool}({rule}) 到{dest}',
      replaceTo: '替换规则到{dest}',
      removeFrom: '移除 {tool} 从{dest}',
      removeRulesFrom: '移除规则从{dest}',
      switchTo: '切换到{mode}',
      applyTo: '应用到{dest}',
      addDirTo: '添加目录 {dir} 到{dest}',
      removeDirFrom: '移除目录 {dir} 从{dest}'
    },
    mode: {
      default: '默认模式',
      acceptEdits: '自动接受编辑',
      plan: '计划模式',
      bypassPermissions: '跳过权限',
      bypass: '跳过',
      bypassTooltip: '跳过所有权限确认，直接执行操作',
      dontAsk: '不再询问'
    }
  }
  ,
  system: {
    interrupted: '[用户已打断请求]'
  },
  compact: {
    compacting: '正在压缩会话上下文...',
    contextRestored: '上下文已恢复',
    sessionSummary: '（会话压缩摘要）',
    expand: '展开',
    collapse: '收起',
    originalTokens: '原始 Token'
  },
  slashCommand: {
    compact: '压缩当前会话的上下文',
    context: '显示当前会话的上下文信息',
    rename: '重命名当前会话'
  },
  askUser: {
    title: 'Claude 需要您的回答',
    typeSomething: '输入内容...',
    cancel: '取消',
    submit: '提交',
    answer: '回答'
  }
}
