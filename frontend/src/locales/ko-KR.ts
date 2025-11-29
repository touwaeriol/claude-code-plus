export default {
  common: {
    send: '보내기',
    cancel: '취소',
    confirm: '확인',
    save: '저장',
    delete: '삭제',
    edit: '편집',
    copy: '복사',
    close: '닫기',
    loading: '로딩 중...',
    success: '성공',
    error: '오류',
    search: '검색',
    clear: '지우기',
    settings: '설정',
    tools: '도구',
    unknown: '알 수 없음',
    remove: '제거',
    yes: '예',
    no: '아니오',
    ok: '확인',
    pending: '대기 중',
    cancelled: '취소됨',
    copyFailed: '복사 실패',
    copied: '복사됨',
    renderFailed: '렌더링 실패'
  },
  chat: {
    placeholder: '메시지 입력...',
    placeholderWithShortcuts: '메시지 입력... (Enter로 보내기, Shift+Enter로 줄바꿈, Alt+Enter로 중단)',
    placeholderWithShortcutsCtrl: '메시지 입력... (Ctrl+Enter로 보내기, Shift+Enter로 줄바꿈)',
    newSession: '새 세션',
    history: '기록',
    thinking: '생각 중...',
    retry: '다시 시도',
    stop: '중지',
    stopGenerating: '생성 중지',
    welcome: '안녕하세요! 저는 AI 코딩 어시스턴트 Claude입니다.',
    emptyState: '새 세션을 시작하거나 기록에서 선택하세요.',
    sendMessage: '메시지 보내기',
    sendMessageShortcut: '메시지 보내기 (Enter) | 더 많은 옵션은 오른쪽 클릭',
    addContext: '컨텍스트 추가',
    autoCleanupContext: '자동 컨텍스트 정리',
    autoCleanupContextTooltip: '메시지 전송 후 컨텍스트 태그 자동 정리',
    dropFileToAddContext: '파일을 여기에 놓아 컨텍스트에 추가',
    taskQueue: '작업 대기열',
    taskQueueCount: '작업 대기열 ({count})',
    taskStatus: {
      pending: '대기 중',
      running: '실행 중',
      success: '성공',
      failed: '실패'
    },
    error: {
      title: '오류',
      unknown: '알 수 없는 오류',
      initSessionFailed: '세션 초기화 실패: {message}',
      switchSessionFailed: '세션 전환 실패: {message}',
      sendMessageFailed: '메시지 전송 실패: {message}'
    },
    debug: {
      title: '디버그 정보',
      sessionId: '세션 ID',
      projectPath: '프로젝트 경로',
      messageCount: '메시지 수',
      generating: '생성 중',
      pendingTasks: '대기 작업',
      contexts: '컨텍스트',
      notSet: '설정 안됨',
      generatingStatus: '생성 중'
    },
    enterToSend: 'Enter로 메시지 보내기 ·',
    noMessages: '메시지가 없습니다. 세션을 만들고 메시지를 보내세요.',
    thinkingLabel: '생각 중',
    generating: '생성 중...',
    actualModel: '실제 모델',
    uploadImage: '이미지 업로드',
    interruptAndSend: '중단 후 보내기',
    interruptAndSendShortcut: '중단 후 보내기 (Alt+Enter)',
    moreContexts: '{count}개 더 많은 컨텍스트',
    tokenTooltip: '입력: {input}, 출력: {output}, 캐시 생성: {cacheCreation}, 캐시 읽기: {cacheRead}',
    welcomeScreen: {
      title: 'Claude와 대화 시작하기',
      description: '질문이나 아이디어를 입력하세요. Claude가 코드 작성과 질문 답변을 도와드립니다',
      askCode: '코드 질문하기',
      refactor: '기존 코드 리팩토링',
      debug: '오류 디버깅',
      sendHint: '메시지 보내기',
      newLineHint: '줄바꿈'
    },
    claudeThinking: 'Claude가 생각 중입니다...',
    scrollToBottom: '맨 아래로 이동'
  },
  tools: {
    read: '파일 읽기',
    write: '파일 쓰기',
    edit: '파일 편집',
    bash: '명령 실행',
    search: '파일 검색',
    status: {
      pending: '대기 중',
      running: '실행 중',
      runningWithDots: '실행 중...',
      completed: '완료됨',
      failed: '실패',
      cancelled: '취소됨',
      success: '성공'
    },
    editSuccess: '편집 성공',
    editFailed: '편집 실패',
    confirmed: '확인됨',
    terminated: '종료됨',
    copyContent: '내용 복사',
    openInEditor: '편집기에서 열기',
    parsingParams: '매개변수 분석 중...',
    contentTruncated: '내용 잘림',
    noOutput: '출력 없음',
    replaceAll: '모두 교체',
    changeNumber: '변경 #{number}',
    questions: '{count}개 질문',
    subtask: '하위 작업',
    newContent: '새 내용',
    label: {
      path: '경로',
      pattern: '패턴',
      lines: '줄 수',
      start: '시작',
      duration: '소요 시간',
      filter: '필터',
      output: '출력',
      result: '결과',
      searchPattern: '검색 패턴',
      searchPath: '검색 경로',
      fileFilter: '파일 필터',
      fileType: '파일 유형',
      outputMode: '출력 모드',
      cell: '셀',
      mode: '모드',
      agentType: '에이전트 유형',
      model: '모델',
      prompt: '프롬프트',
      query: '검색어',
      allowedDomains: '허용된 도메인',
      blockedDomains: '차단된 도메인'
    },
    readTool: {
      reading: '파일 읽는 중...',
      readResult: '읽기 결과'
    },
    grepTool: {
      outputModes: {
        content: '내용',
        filesWithMatches: '일치하는 파일',
        count: '개수'
      },
      options: {
        ignoreCase: '대소문자 무시',
        showLineNumbers: '줄 번호 표시',
        multiline: '여러 줄'
      }
    },
    todoTool: {
      pending: '대기 중',
      inProgress: '진행 중',
      completed: '완료됨'
    }
  },
  settings: {
    title: '설정',
    language: '언어',
    theme: '테마',
    autoScroll: '자동 스크롤',
    fontSize: '글꼴 크기',
    autoLoadContext: '새 세션 생성 시 마지막 대화 컨텍스트를 자동으로 로드합니다.',
    temperature: '온도',
    temperatureDescription: '모델 창의성을 제어합니다. 0 = 결정적, 1 = 높은 창의성. 기본값을 사용하려면 비워두세요.'
  },
  session: {
    defaultName: '세션 {time}',
    group: {
      today: '오늘',
      yesterday: '어제',
      sevenDays: '지난 7일',
      thirtyDays: '지난 30일',
      older: '이전'
    },
    search: '세션 검색...',
    export: '세션 내보내기',
    editGroup: '그룹 편집',
    createGroup: '그룹 만들기',
    editTag: '태그 편집',
    createTag: '태그 만들기',
    deleteSession: '세션 삭제',
    searchFailed: '검색 실패',
    history: '세션 기록',
    sessionCount: '개의 세션',
    noHistory: '기록 없음',
    active: '활성',
    noActive: '활성 세션 없음',
    historySection: '기록',
    unnamed: '이름 없는 세션',
    messages: '개의 메시지'
  },
  time: {
    justNow: '방금',
    minutesAgo: '{n}분 전',
    hoursAgo: '{n}시간 전',
    daysAgo: '{n}일 전'
  },
  thinking: {
    label: '사고',
    enabled: '켜짐',
    disabled: '꺼짐',
    alwaysOn: '이 모델은 사고가 항상 활성화됩니다',
    notSupported: '이 모델은 사고를 지원하지 않습니다',
    toggleOn: '클릭하여 사고 활성화',
    toggleOff: '클릭하여 사고 비활성화'
  },
  context: {
    usage: '컨텍스트 사용량: {used} / {max} 토큰 ({percentage}%)',
    critical: '🚨 컨텍스트 창이 거의 가득 찼습니다! 새 대화를 시작하는 것을 고려하세요',
    warning: '💡 컨텍스트가 절반 찼습니다, 주의해서 관리하세요'
  },
  keyboard: {
    sendMessage: '메시지 보내기',
    closeDialog: '대화상자 닫기'
  }
}
