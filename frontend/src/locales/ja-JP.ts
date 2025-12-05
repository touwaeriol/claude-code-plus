export default {
  common: {
    send: '送信',
    cancel: 'キャンセル',
    confirm: '確認',
    save: '保存',
    delete: '削除',
    edit: '編集',
    copy: 'コピー',
    close: '閉じる',
    loading: '読み込み中...',
    success: '成功',
    error: 'エラー',
    search: '検索',
    clear: 'クリア',
    settings: '設定',
    tools: 'ツール',
    unknown: '不明',
    remove: '削除',
    yes: 'はい',
    no: 'いいえ',
    ok: 'OK',
    pending: '保留中',
    cancelled: 'キャンセル済み',
    copyFailed: 'コピー失敗',
    copied: 'コピー済み',
    renderFailed: 'レンダリング失敗'
  },
  chat: {
    placeholder: 'メッセージを入力...',
    placeholderWithShortcuts: 'メッセージを入力... (Enterで送信、Shift+Enterで改行、Ctrl+Enterで中断)',
    placeholderWithShortcutsCtrl: 'メッセージを入力... (Enterで送信、Shift+Enterで改行、Ctrl+Enterで中断)',
    newSession: '新しいセッション',
    history: '履歴',
    thinking: '考え中...',
    retry: '再試行',
    stop: '停止',
    stopGenerating: '生成を停止',
    escToInterrupt: 'ESC で中断',
    welcome: 'こんにちは！私はAIコーディングアシスタントのClaudeです。',
    emptyState: '新しいセッションを開始するか、履歴から選択してください。',
    sendMessage: 'メッセージを送信',
    sendMessageShortcut: 'メッセージを送信 (Enter) | 右クリックでその他のオプション',
    addContext: 'コンテキストを追加',
    autoCleanupContext: 'コンテキスト自動クリーンアップ',
    autoCleanupContextTooltip: 'メッセージ送信後にコンテキストタグを自動的にクリア',
    dropFileToAddContext: 'ファイルをここにドロップしてコンテキストに追加',
    taskQueue: 'タスクキュー',
    taskQueueCount: 'タスクキュー ({count})',
    taskStatus: {
      pending: '保留中',
      running: '実行中',
      success: '成功',
      failed: '失敗'
    },
    error: {
      title: 'エラー',
      unknown: '不明なエラー',
      initSessionFailed: 'セッション初期化失敗: {message}',
      switchSessionFailed: 'セッション切替失敗: {message}',
      sendMessageFailed: 'メッセージ送信失敗: {message}'
    },
    debug: {
      title: 'デバッグ情報',
      sessionId: 'セッションID',
      projectPath: 'プロジェクトパス',
      messageCount: 'メッセージ数',
      generating: '生成中',
      pendingTasks: '保留タスク',
      contexts: 'コンテキスト',
      notSet: '未設定',
      generatingStatus: '生成中'
    },
    enterToSend: 'Enterでメッセージを送信 ·',
    noMessages: 'メッセージがありません。セッションを作成してメッセージを送信してください。',
    thinkingLabel: '思考中',
    generating: '生成中...',
    actualModel: '実際のモデル',
    uploadImage: '画像をアップロード',
    interruptAndSend: '中断して送信',
    interruptAndSendShortcut: '中断して送信 (Ctrl+Enter)',
    moreContexts: 'あと{count}個のコンテキスト',
    tokenTooltip: '入力: {input}, 出力: {output}, キャッシュ作成: {cacheCreation}, キャッシュ読取: {cacheRead}',
    welcomeScreen: {
      title: 'Claudeとの会話を始めましょう',
      description: '質問やアイデアを入力してください。Claudeがコード作成や質問への回答をお手伝いします',
      askCode: 'コードについて質問',
      refactor: '既存コードをリファクタリング',
      debug: 'エラーをデバッグ',
      sendHint: 'メッセージを送信',
      newLineHint: '改行',
      interruptHint: '中断して送信',
      stopHint: '生成停止',
      toggleThinkingHint: '思考切替',
      switchModeHint: 'モード切替'
    },
    claudeThinking: 'Claudeが考えています...',
    scrollToBottom: '一番下へ移動'
  },
  tools: {
    read: 'ファイル読取',
    write: 'ファイル書込',
    edit: 'ファイル編集',
    bash: 'コマンド実行',
    search: 'ファイル検索',
    status: {
      pending: '保留中',
      running: '実行中',
      runningWithDots: '実行中...',
      completed: '完了',
      failed: '失敗',
      cancelled: 'キャンセル済み',
      success: '成功'
    },
    editSuccess: '編集成功',
    editFailed: '編集失敗',
    confirmed: '確認済み',
    terminated: '終了済み',
    copyContent: '内容をコピー',
    openInEditor: 'エディタで開く',
    parsingParams: 'パラメータを解析中...',
    contentTruncated: '内容が切り捨てられました',
    noOutput: '出力なし',
    replaceAll: 'すべて置換',
    changeNumber: '変更 #{number}',
    questions: '{count}件の質問',
    subtask: 'サブタスク',
    newContent: '新しい内容',
    label: {
      path: 'パス',
      pattern: 'パターン',
      lines: '行数',
      start: '開始',
      duration: '所要時間',
      filter: 'フィルター',
      output: '出力',
      result: '結果',
      searchPattern: '検索パターン',
      searchPath: '検索パス',
      fileFilter: 'ファイルフィルター',
      fileType: 'ファイルタイプ',
      outputMode: '出力モード',
      cell: 'セル',
      mode: 'モード',
      agentType: 'エージェントタイプ',
      model: 'モデル',
      prompt: 'プロンプト',
      query: '検索クエリ',
      allowedDomains: '許可ドメイン',
      blockedDomains: 'ブロックドメイン'
    },
    readTool: {
      reading: 'ファイルを読み取り中...',
      readResult: '読取結果'
    },
    grepTool: {
      outputModes: {
        content: '内容',
        filesWithMatches: '一致するファイル',
        count: 'カウント'
      },
      options: {
        ignoreCase: '大文字小文字を無視',
        showLineNumbers: '行番号を表示',
        multiline: '複数行'
      }
    },
    todoTool: {
      pending: '保留中',
      inProgress: '進行中',
      completed: '完了'
    }
  },
  settings: {
    title: '設定',
    language: '言語',
    theme: 'テーマ',
    autoScroll: '自動スクロール',
    fontSize: 'フォントサイズ',
    autoLoadContext: '新しいセッション作成時に前回の会話コンテキストを自動的に読み込みます。',
    temperature: '温度',
    temperatureDescription: 'モデルの創造性を制御します。0 = 確定的、1 = 高創造性。デフォルト値を使用するには空欄にしてください。'
  },
  session: {
    defaultName: 'セッション {time}',
    group: {
      today: '今日',
      yesterday: '昨日',
      sevenDays: '過去7日間',
      thirtyDays: '過去30日間',
      older: '以前'
    },
    search: 'セッションを検索...',
    export: 'セッションをエクスポート',
    editGroup: 'グループを編集',
    createGroup: 'グループを作成',
    editTag: 'タグを編集',
    createTag: 'タグを作成',
    deleteSession: 'セッションを削除',
    searchFailed: '検索失敗',
    history: 'セッション履歴',
    sessionCount: '件のセッション',
    noHistory: '履歴なし',
    active: 'アクティブ',
    noActive: 'アクティブなセッションなし',
    historySection: '履歴',
    unnamed: '無題のセッション',
    messages: '件のメッセージ'
  },
  time: {
    justNow: 'たった今',
    minutesAgo: '{n}分前',
    hoursAgo: '{n}時間前',
    daysAgo: '{n}日前'
  },
  thinking: {
    label: '思考',
    enabled: 'オン',
    disabled: 'オフ',
    alwaysOn: 'このモデルは思考が常に有効です',
    notSupported: 'このモデルは思考をサポートしていません',
    toggleOn: 'クリックして思考を有効にする',
    toggleOff: 'クリックして思考を無効にする'
  },
  context: {
    usage: 'コンテキスト使用量: {used} / {max} トークン ({percentage}%)',
    critical: '🚨 コンテキストウィンドウがほぼいっぱいです！新しい会話を始めることを検討してください',
    warning: '💡 コンテキストが半分使用されています、注意して管理してください'
  },
  keyboard: {
    sendMessage: 'メッセージを送信',
    closeDialog: 'ダイアログを閉じる'
  },
  permission: {
    needsAuth: '承認が必要',
    allow: '許可',
    deny: '拒否',
    denyReasonPlaceholder: '拒否理由（任意）',
    confirm: '確認',
    escToDeny: 'ESCで拒否',
    destination: {
      session: 'このセッション',
      projectSettings: 'プロジェクト設定',
      userSettings: 'ユーザー設定',
      localSettings: 'ローカル設定'
    },
    suggestion: {
      rememberTo: '{tool}を{dest}に記憶',
      rememberWithRuleTo: '{tool}({rule})を{dest}に記憶',
      replaceTo: '{dest}にルールを置換',
      removeFrom: '{dest}から{tool}を削除',
      removeRulesFrom: '{dest}からルールを削除',
      switchTo: '{mode}に切替',
      applyTo: '{dest}に適用',
      addDirTo: '{dest}にディレクトリ{dir}を追加',
      removeDirFrom: '{dest}からディレクトリ{dir}を削除'
    },
    mode: {
      default: 'デフォルトモード',
      acceptEdits: '編集を自動承認',
      plan: 'プランモード',
      bypassPermissions: '権限をバイパス',
      dontAsk: '再度確認しない'
    }
  }
  ,
  system: {
    interrupted: '[ユーザーによりリクエストが中断されました]'
  }
}
