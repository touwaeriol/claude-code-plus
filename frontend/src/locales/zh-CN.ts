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
    copyFailed: '复制失败'
  },
  chat: {
    placeholder: '输入消息...',
    placeholderWithShortcuts: '输入消息... (Enter 发送, Shift+Enter 换行, Alt+Enter 打断发送)',
    placeholderWithShortcutsCtrl: '输入消息... (Ctrl+Enter 发送, Shift+Enter 换行)',
    newSession: '新会话',
    history: '历史记录',
    thinking: '思考中...',
    retry: '重试',
    stop: '停止',
    stopGenerating: '停止生成',
    welcome: '你好！我是 Claude，你的 AI 编程助手。',
    emptyState: '开始一个新的会话，或选择一个历史记录。',
    sendMessage: '发送消息',
    sendMessageShortcut: '发送消息 (Enter) | 右键查看更多选项',
    addContext: '添加上下文',
    autoCleanupContext: '自动清理上下文',
    autoCleanupContextTooltip: '发送消息后自动清空上下文标签',
    dropFileToAddContext: '释放文件以添加到上下文',
    taskQueue: '任务队列',
    taskQueueCount: '任务队列 ({count})',
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
      sendMessageFailed: '发送消息失败: {message}'
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
    noMessages: '没有消息。请创建会话并发送消息。'
  },
  tools: {
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
    copyContent: '复制内容'
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
    searchFailed: '搜索失败'
  },
  context: {
    usage: '上下文使用: {used} / {max} tokens ({percentage}%)',
    critical: '🚨 上下文窗口即将用完！建议立即开启新对话',
    warning: '💡 上下文已使用一半，注意管理'
  },
  keyboard: {
    sendMessage: '发送消息',
    closeDialog: '关闭对话框'
  }
}

