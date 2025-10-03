package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.services.LocalizationService.SupportedLanguage

/**
 * 字符串资源管理器
 * 由于在插件环境中Compose资源系统可能不工作，使用手动管理的方式
 */
object StringResources {
    
    // 常用错误消息键的静态常量
    const val OPERATION_FAILED = "operation_failed"
    const val SESSION_CREATION_FAILED = "session_creation_failed"
    const val SEND_MESSAGE_FAILED = "send_message_failed"
    const val LOAD_HISTORY_FAILED = "load_history_failed"
    const val SESSION_CONNECTION_ERROR = "session_connection_error"
    
    // 工具调用状态
    const val TOOL_STATUS = "tool_status"
    // 工具状态（简短标签）
    const val TOOL_STATUS_PENDING = "tool_status_pending_short"
    const val TOOL_STATUS_RUNNING = "tool_status_running_short"
    const val TOOL_STATUS_SUCCESS = "tool_status_success_short"
    const val TOOL_STATUS_FAILED = "tool_status_failed_short"
    const val TOOL_STATUS_CANCELLED = "tool_status_cancelled_short"
    // 展开状态
    const val UI_EXPANDED = "ui_expanded"
    const val UI_COLLAPSED = "ui_collapsed"
    const val FILES_FOUND = "files_found"
    const val FILES_MORE = "files_more"
    const val SEARCH_RESULTS = "search_results"
    const val SEARCH_MORE = "search_more"
    const val CONTENT_LENGTH = "content_length"
    const val TASK_EXECUTION_FAILED = "task_execution_failed"
    const val NOTEBOOK_OPERATION_FAILED = "notebook_operation_failed"
    const val MCP_TOOL_FAILED = "mcp_tool_failed"
    const val EDIT_CHANGES = "edit_changes"
    const val EDIT_DIFF_TITLE = "edit_diff_title"
    const val PARAMETERS_COUNT = "parameters_count"
    const val UPDATE_TASKS = "update_tasks"
    const val TASK_MANAGEMENT = "task_management"
    const val TASK_COMPLETED_COUNT = "task_completed_count"
    const val TASK_STATUS_PENDING = "task_status_pending"
    const val TASK_STATUS_IN_PROGRESS = "task_status_in_progress"
    const val TASK_STATUS_COMPLETED = "task_status_completed"
    const val NO_TASKS = "no_tasks"
    
    // 文件操作
    const val DIRECTORIES = "directories"
    const val FILES = "files"
    const val FILE_TYPE_JSON = "file_type_json"
    const val FILE_TYPE_XML = "file_type_xml"
    const val FILE_TYPE_CODE = "file_type_code"
    const val FILE_TYPE_CONFIG = "file_type_config"
    const val FILE_CONTENT = "file_content"
    const val MORE_LINES = "more_lines"
    const val MORE_ITEMS = "more_items"
    
    /**
     * 字符串资源映射
     */
    private val strings = mapOf(
        // 通用
        SupportedLanguage.ENGLISH to mapOf(
            "app_name" to "Claude Code Plus",
            "send" to "Send",
            "cancel" to "Cancel",
            "ok" to "OK",
            "close" to "Close",
            "loading" to "Loading...",
            "error" to "Error",
            "retry" to "Retry",
            "save" to "Save",
            "delete" to "Delete",
            "edit" to "Edit",
            "copy" to "Copy",
            "paste" to "Paste",
            "cut" to "Cut",
            "select_all" to "Select All",
            
            // 聊天界面
            "chat_input_placeholder" to "Type a message...",
            "model_selector_title" to "Select Model",
            "model_selector_anthropic" to "Anthropic",
            "model_selector_openai" to "OpenAI",
            "permission_mode_auto" to "Auto",
            "permission_mode_manual" to "Manual",
            "permission_mode_skip" to "Skip",
            "permission_label" to "Permission:",
            "add_context" to "Add Context",
            "file_selector_recent" to "Recent Files",
            "file_selector_search" to "Search Files",
            "file_selector_no_files" to "No files found",
            "interrupting" to "Interrupting...",
            "interrupt_and_send" to "Interrupt & Send",
            "select_image" to "Select Image",
            "image_files" to "Image Files (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
            
            // 工具调用
            "tool_read" to "Read",
            "tool_write" to "Write",
            "tool_edit" to "Edit",
            "tool_multiedit" to "MultiEdit",
            "tool_bash" to "Bash",
            "tool_todowrite" to "TodoWrite",
            "tool_executing" to "Executing...",
            "tool_completed" to "Completed",
            "tool_failed" to "Failed",
            "tool_results" to "Results:",
            // 工具状态短标签
            TOOL_STATUS_PENDING to "Pending",
            TOOL_STATUS_RUNNING to "Running",
            TOOL_STATUS_SUCCESS to "Success",
            TOOL_STATUS_FAILED to "Failed",
            TOOL_STATUS_CANCELLED to "Cancelled",
            // 展开/折叠
            UI_EXPANDED to "Expanded",
            UI_COLLAPSED to "Collapsed",
            "tool_parameters" to "Parameters:",
            "tool_file_changes" to "File changes",
            "tool_search_results" to "Search results",
            
            // 消息显示
            "assistant_message" to "Assistant",
            
            // 错误消息
            "operation_failed" to "Operation failed: %1\$s",
            "session_creation_failed" to "Session creation failed: %1\$s",
            "send_message_failed" to "Send message failed: %1\$s",
            "load_history_failed" to "Load history failed: %1\$s",
            "session_connection_error" to "Session connection error: %1\$s",
            
            // 工具调用状态
            "tool_status" to "Status: %1\$s",
            "files_found" to "📂 Found %1\$s matching files:",
            "files_more" to "... %1\$s more files",
            "search_results" to "🔍 Search \"%1\$s\" found %2\$s matches:",
            "search_more" to "... %1\$s more matches",
            "content_length" to "Content length: %1\$s characters",
            "task_execution_failed" to "❌ Task execution failed: %1\$s",
            "notebook_operation_failed" to "❌ Notebook operation failed: %1\$s",
            "mcp_tool_failed" to "❌ MCP tool execution failed: %1\$s",
            "edit_changes" to "%1\$s changes",
            "edit_diff_title" to "Edit Preview - %1\$s",
            "parameters_count" to "%1\$s parameters",
            "update_tasks" to "Update %1\$s tasks",
            "task_management" to "Task Management",
            "task_completed_count" to "(%1\$d/%2\$d completed)",
            "task_status_pending" to "Pending",
            "task_status_in_progress" to "In Progress",
            "task_status_completed" to "Completed",
            "no_tasks" to "No tasks",
            "directories" to "directories",
            "files" to "files",
            "file_type_json" to "JSON",
            "file_type_xml" to "XML/HTML",
            "file_type_code" to "Code",
            "file_type_config" to "Configuration",
            "file_content" to "📄 %1\$s file content (%2\$s lines, %3\$s characters)",
            "more_lines" to "... %1\$s more lines",
            "more_items" to "... %1\$s more items",
            "user_message" to "You",
            "system_message" to "System",
            "thinking" to "Thinking...",
            "generating" to "Generating..."
        ),
        
        SupportedLanguage.SIMPLIFIED_CHINESE to mapOf(
            "app_name" to "Claude Code Plus",
            "send" to "发送",
            "cancel" to "取消",
            "ok" to "确定",
            "close" to "关闭",
            "loading" to "加载中...",
            "error" to "错误",
            "retry" to "重试",
            "save" to "保存",
            "delete" to "删除",
            "edit" to "编辑",
            "copy" to "复制",
            "paste" to "粘贴",
            "cut" to "剪切",
            "select_all" to "全选",
            
            // 聊天界面
            "chat_input_placeholder" to "输入消息...",
            "model_selector_title" to "选择模型",
            "model_selector_anthropic" to "Anthropic",
            "model_selector_openai" to "OpenAI",
            "permission_mode_auto" to "自动",
            "permission_mode_manual" to "手动",
            "permission_mode_skip" to "跳过",
            "permission_label" to "权限：",
            "add_context" to "添加上下文",
            "file_selector_recent" to "最近文件",
            "file_selector_search" to "搜索文件",
            "file_selector_no_files" to "未找到文件",
            "interrupting" to "中断中...",
            "interrupt_and_send" to "中断并发送",
            "select_image" to "选择图片",
            "image_files" to "图片文件 (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
            
            // 工具调用
            "tool_read" to "读取",
            "tool_write" to "写入",
            "tool_edit" to "编辑",
            "tool_multiedit" to "多处编辑",
            "tool_bash" to "命令执行",
            "tool_todowrite" to "任务列表",
            "tool_executing" to "执行中...",
            "tool_completed" to "已完成",
            "tool_failed" to "失败",
            "tool_results" to "结果：",
            // 工具状态短标签
            TOOL_STATUS_PENDING to "待处理",
            TOOL_STATUS_RUNNING to "执行中",
            TOOL_STATUS_SUCCESS to "已完成",
            TOOL_STATUS_FAILED to "已失败",
            TOOL_STATUS_CANCELLED to "已取消",
            // 展开/折叠
            UI_EXPANDED to "展开",
            UI_COLLAPSED to "折叠",
            "tool_parameters" to "参数：",
            "tool_file_changes" to "文件变更",
            "tool_search_results" to "搜索结果",
            
            // 消息显示
            "assistant_message" to "助手",
            
            // 错误消息
            "operation_failed" to "操作失败: %1\$s",
            "session_creation_failed" to "创建会话失败: %1\$s",
            "send_message_failed" to "发送消息失败: %1\$s",
            "load_history_failed" to "加载历史消息失败: %1\$s",
            "session_connection_error" to "会话连接异常: %1\$s",
            
            // 工具调用状态
            "tool_status" to "状态: %1\$s",
            "files_found" to "📂 找到 %1\$s 个匹配文件：",
            "files_more" to "... 还有 %1\$s 个文件",
            "search_results" to "🔍 搜索 \"%1\$s\" 找到 %2\$s 处匹配：",
            "search_more" to "... 还有 %1\$s 处匹配",
            "content_length" to "内容长度：%1\$s 字符",
            "task_execution_failed" to "❌ 任务执行失败：%1\$s",
            "notebook_operation_failed" to "❌ Notebook 操作失败：%1\$s",
            "mcp_tool_failed" to "❌ MCP 工具执行失败：%1\$s",
            "edit_changes" to "%1\$s 处修改",
            "parameters_count" to "%1\$s 个参数",
            "update_tasks" to "更新 %1\$s 个任务",
            "task_management" to "任务管理",
            "task_completed_count" to "(%1\$d/%2\$d 完成)",
            "task_status_pending" to "待办",
            "task_status_in_progress" to "进行中",
            "task_status_completed" to "已完成",
            "no_tasks" to "暂无任务",
            "directories" to "个目录",
            "files" to "个文件",
            "file_type_json" to "JSON",
            "file_type_xml" to "XML/HTML",
            "file_type_code" to "代码",
            "file_type_config" to "配置",
            "file_content" to "📄 %1\$s 文件内容 (%2\$s 行，%3\$s 字符)",
            "more_lines" to "... 还有 %1\$s 行",
            "more_items" to "... 还有 %1\$s 项",
            "user_message" to "你",
            "system_message" to "系统",
            "thinking" to "思考中...",
            "generating" to "生成中..."
        ),
        
        SupportedLanguage.TRADITIONAL_CHINESE to mapOf(
            "app_name" to "Claude Code Plus",
            "send" to "傳送",
            "cancel" to "取消",
            "ok" to "確定",
            "close" to "關閉",
            "loading" to "載入中...",
            "error" to "錯誤",
            "retry" to "重試",
            "save" to "儲存",
            "delete" to "刪除",
            "edit" to "編輯",
            "copy" to "複製",
            "paste" to "貼上",
            "cut" to "剪下",
            "select_all" to "全選",
            
            // 聊天界面
            "chat_input_placeholder" to "輸入訊息...",
            "model_selector_title" to "選擇模型",
            "model_selector_anthropic" to "Anthropic",
            "model_selector_openai" to "OpenAI",
            "permission_mode_auto" to "自動",
            "permission_mode_manual" to "手動",
            "permission_mode_skip" to "跳過",
            "permission_label" to "權限：",
            "add_context" to "新增內容",
            "file_selector_recent" to "最近檔案",
            "file_selector_search" to "搜尋檔案",
            "file_selector_no_files" to "未找到檔案",
            "interrupting" to "中斷中...",
            "interrupt_and_send" to "中斷並傳送",
            "select_image" to "選擇圖片",
            "image_files" to "圖片檔案 (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
            
            // 工具調用
            "tool_read" to "讀取",
            "tool_write" to "寫入",
            "tool_edit" to "編輯",
            "tool_multiedit" to "多處編輯",
            "tool_bash" to "命令執行",
            "tool_todowrite" to "任務清單",
            "tool_executing" to "執行中...",
            "tool_completed" to "已完成",
            "tool_failed" to "失敗",
            "tool_results" to "結果：",
            // 工具狀態短標籤
            TOOL_STATUS_PENDING to "待處理",
            TOOL_STATUS_RUNNING to "執行中",
            TOOL_STATUS_SUCCESS to "已完成",
            TOOL_STATUS_FAILED to "已失敗",
            TOOL_STATUS_CANCELLED to "已取消",
            // 展開/收合
            UI_EXPANDED to "展開",
            UI_COLLAPSED to "收合",
            "tool_parameters" to "參數：",
            "tool_file_changes" to "檔案變更",
            "tool_search_results" to "搜尋結果",
            
            // 消息顯示
            "assistant_message" to "助手",
            
            // 錯誤訊息
            "operation_failed" to "操作失敗: %1\$s",
            "session_creation_failed" to "建立會話失敗: %1\$s",
            "send_message_failed" to "傳送訊息失敗: %1\$s",
            "load_history_failed" to "載入歷史訊息失敗: %1\$s",
            "session_connection_error" to "會話連線異常: %1\$s",
            
            // 工具調用狀態
            "tool_status" to "狀態: %1\$s",
            "files_found" to "📂 找到 %1\$s 個匹配檔案：",
            "files_more" to "... 還有 %1\$s 個檔案",
            "search_results" to "🔍 搜尋 \"%1\$s\" 找到 %2\$s 處匹配：",
            "search_more" to "... 還有 %1\$s 處匹配",
            "content_length" to "內容長度：%1\$s 字元",
            "task_execution_failed" to "❌ 任務執行失敗：%1\$s",
            "notebook_operation_failed" to "❌ Notebook 操作失敗：%1\$s",
            "mcp_tool_failed" to "❌ MCP 工具執行失敗：%1\$s",
            "edit_changes" to "%1\$s 處修改",
            "parameters_count" to "%1\$s 個參數",
            "update_tasks" to "更新 %1\$s 個任務",
            "task_management" to "任務管理",
            "task_completed_count" to "(%1\$d/%2\$d 完成)",
            "task_status_pending" to "待辦",
            "task_status_in_progress" to "進行中",
            "task_status_completed" to "已完成",
            "no_tasks" to "暫無任務",
            "directories" to "個目錄",
            "files" to "個檔案",
            "file_type_json" to "JSON",
            "file_type_xml" to "XML/HTML",
            "file_type_code" to "程式碼",
            "file_type_config" to "配置",
            "file_content" to "📄 %1\$s 檔案內容 (%2\$s 行，%3\$s 字元)",
            "more_lines" to "... 還有 %1\$s 行",
            "more_items" to "... 還有 %1\$s 項",
            "user_message" to "你",
            "system_message" to "系統",
            "thinking" to "思考中...",
            "generating" to "產生中..."
        ),
        
        SupportedLanguage.JAPANESE to mapOf(
            "app_name" to "Claude Code Plus",
            "send" to "送信",
            "cancel" to "キャンセル",
            "ok" to "OK",
            "close" to "閉じる",
            "loading" to "読み込み中...",
            "error" to "エラー",
            "retry" to "再試行",
            "save" to "保存",
            "delete" to "削除",
            "edit" to "編集",
            "copy" to "コピー",
            "paste" to "貼り付け",
            "cut" to "切り取り",
            "select_all" to "すべて選択",
            
            // チャット画面
            "chat_input_placeholder" to "メッセージを入力...",
            "model_selector_title" to "モデルを選択",
            "model_selector_anthropic" to "Anthropic",
            "model_selector_openai" to "OpenAI",
            "permission_mode_auto" to "自動",
            "permission_mode_manual" to "手動",
            "permission_mode_skip" to "スキップ",
            "permission_label" to "権限：",
            "add_context" to "コンテキストを追加",
            "file_selector_recent" to "最近のファイル",
            "file_selector_search" to "ファイル検索",
            "file_selector_no_files" to "ファイルが見つかりません",
            "interrupting" to "中断中...",
            "interrupt_and_send" to "中断して送信",
            "select_image" to "画像を選択",
            "image_files" to "画像ファイル (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
            
            // ツール呼び出し
            "tool_read" to "読み取り",
            "tool_write" to "書き込み",
            "tool_edit" to "編集",
            "tool_multiedit" to "複数編集",
            "tool_bash" to "コマンド実行",
            "tool_todowrite" to "タスクリスト",
            "tool_executing" to "実行中...",
            "tool_completed" to "完了",
            "tool_failed" to "失敗",
            "tool_results" to "結果：",
            // ツール状態（短いラベル）
            TOOL_STATUS_PENDING to "保留",
            TOOL_STATUS_RUNNING to "実行中",
            TOOL_STATUS_SUCCESS to "完了",
            TOOL_STATUS_FAILED to "失敗",
            TOOL_STATUS_CANCELLED to "取消",
            // 展開/折りたたみ
            UI_EXPANDED to "展開",
            UI_COLLAPSED to "折りたたみ",
            "tool_parameters" to "パラメータ：",
            "tool_file_changes" to "ファイル変更",
            "tool_search_results" to "検索結果",
            
            // メッセージ表示
            "assistant_message" to "アシスタント",
            
            // エラーメッセージ
            "operation_failed" to "操作が失敗しました: %1\$s",
            "session_creation_failed" to "セッション作成に失敗しました: %1\$s",
            "send_message_failed" to "メッセージ送信に失敗しました: %1\$s",
            "load_history_failed" to "履歴の読み込みに失敗しました: %1\$s",
            "session_connection_error" to "セッション接続エラー: %1\$s",
            
            // ツール状態
            "tool_status" to "ステータス: %1\$s",
            "files_found" to "📂 %1\$s 個のマッチングファイルが見つかりました：",
            "files_more" to "... あと %1\$s 個のファイル",
            "search_results" to "🔍 \"%1\$s\" の検索で %2\$s 個のマッチが見つかりました：",
            "search_more" to "... あと %1\$s 個のマッチ",
            "content_length" to "コンテンツ長: %1\$s 文字",
            "task_execution_failed" to "❌ タスク実行に失敗しました: %1\$s",
            "notebook_operation_failed" to "❌ Notebook 操作に失敗しました: %1\$s",
            "mcp_tool_failed" to "❌ MCP ツール実行に失敗しました: %1\$s",
            "edit_changes" to "%1\$s 箇所の変更",
            "parameters_count" to "%1\$s 個のパラメータ",
            "update_tasks" to "%1\$s 個のタスクを更新",
            "task_management" to "タスク管理",
            "task_completed_count" to "(%1\$d/%2\$d 完了)",
            "task_status_pending" to "待機中",
            "task_status_in_progress" to "実行中",
            "task_status_completed" to "完了",
            "no_tasks" to "タスクなし",
            "directories" to "個のディレクトリ",
            "files" to "個のファイル",
            "file_type_json" to "JSON",
            "file_type_xml" to "XML/HTML",
            "file_type_code" to "コード",
            "file_type_config" to "設定",
            "file_content" to "📄 %1\$s ファイル内容 (%2\$s 行、%3\$s 文字)",
            "more_lines" to "... あと %1\$s 行",
            "more_items" to "... あと %1\$s 項目",
            "user_message" to "あなた",
            "system_message" to "システム",
            "thinking" to "考え中...",
            "generating" to "生成中..."
        ),
        
        SupportedLanguage.KOREAN to mapOf(
            "app_name" to "Claude Code Plus",
            "send" to "전송",
            "cancel" to "취소",
            "ok" to "확인",
            "close" to "닫기",
            "loading" to "로딩 중...",
            "error" to "오류",
            "retry" to "재시도",
            "save" to "저장",
            "delete" to "삭제",
            "edit" to "편집",
            "copy" to "복사",
            "paste" to "붙여넣기",
            "cut" to "잘라내기",
            "select_all" to "모두 선택",
            
            // 채팅 인터페이스
            "chat_input_placeholder" to "메시지를 입력하세요...",
            "model_selector_title" to "모델 선택",
            "model_selector_anthropic" to "Anthropic",
            "model_selector_openai" to "OpenAI",
            "permission_mode_auto" to "자동",
            "permission_mode_manual" to "수동",
            "permission_mode_skip" to "건너뛰기",
            "permission_label" to "권한:",
            "add_context" to "컨텍스트 추가",
            "file_selector_recent" to "최근 파일",
            "file_selector_search" to "파일 검색",
            "file_selector_no_files" to "파일을 찾을 수 없습니다",
            "interrupting" to "중단 중...",
            "interrupt_and_send" to "중단 후 전송",
            "select_image" to "이미지 선택",
            "image_files" to "이미지 파일 (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
            
            // 도구 호출
            "tool_read" to "읽기",
            "tool_write" to "쓰기",
            "tool_edit" to "편집",
            "tool_multiedit" to "다중 편집",
            "tool_bash" to "명령 실행",
            "tool_todowrite" to "작업 목록",
            "tool_executing" to "실행 중...",
            "tool_completed" to "완료됨",
            "tool_failed" to "실패",
            "tool_results" to "결과:",
            "tool_parameters" to "매개변수:",
            "tool_file_changes" to "파일 변경사항",
            "tool_search_results" to "검색 결과",
            // 도구 상태(짧은 라벨)
            TOOL_STATUS_PENDING to "대기",
            TOOL_STATUS_RUNNING to "실행 중",
            TOOL_STATUS_SUCCESS to "성공",
            TOOL_STATUS_FAILED to "실패",
            TOOL_STATUS_CANCELLED to "취소",
            // 펼침/접기
            UI_EXPANDED to "펼침",
            UI_COLLAPSED to "접힘",
            
            // 메시지 표시
            "assistant_message" to "어시스턴트",
            
            // 오류 메시지
            "operation_failed" to "작업 실패: %1\$s",
            "session_creation_failed" to "세션 생성 실패: %1\$s",
            "send_message_failed" to "메시지 전송 실패: %1\$s",
            "load_history_failed" to "히스토리 로드 실패: %1\$s",
            "session_connection_error" to "세션 연결 오류: %1\$s",
            
            // 도구 상태
            "tool_status" to "상태: %1\$s",
            "files_found" to "📂 %1\$s개의 매칭 파일을 찾았습니다:",
            "files_more" to "... %1\$s개 더 많은 파일",
            "search_results" to "🔍 \"%1\$s\" 검색에서 %2\$s개 매치 발견:",
            "search_more" to "... %1\$s개 더 많은 매치",
            "content_length" to "콘텐츠 길이: %1\$s 문자",
            "task_execution_failed" to "❌ 작업 실행 실패: %1\$s",
            "notebook_operation_failed" to "❌ Notebook 작업 실패: %1\$s",
            "mcp_tool_failed" to "❌ MCP 도구 실행 실패: %1\$s",
            "edit_changes" to "%1\$s개 변경사항",
            "parameters_count" to "%1\$s개 매개변수",
            "update_tasks" to "%1\$s개 작업 업데이트",
            "task_management" to "작업 관리",
            "task_completed_count" to "(%1\$d/%2\$d 완료)",
            "task_status_pending" to "대기 중",
            "task_status_in_progress" to "진행 중",
            "task_status_completed" to "완료",
            "no_tasks" to "작업 없음",
            "directories" to "개 디렉토리",
            "files" to "개 파일",
            "file_type_json" to "JSON",
            "file_type_xml" to "XML/HTML",
            "file_type_code" to "코드",
            "file_type_config" to "구성",
            "file_content" to "📄 %1\$s 파일 콘텐츠 (%2\$s 줄, %3\$s 문자)",
            "more_lines" to "... %1\$s줄 더",
            "more_items" to "... %1\$s개 더",
            "user_message" to "사용자",
            "system_message" to "시스템",
            "thinking" to "생각하는 중...",
            "generating" to "생성 중..."
        )
    )
    
    /**
     * 获取本地化字符串
     * @param key 字符串键
     * @param language 指定语言，如果为null则使用当前语言
     * @return 本地化的字符串，如果找不到则返回键名
     */
    fun getString(key: String, language: SupportedLanguage? = null): String {
        val currentLanguage = language ?: LocalizationService.getCurrentLanguage()
        return strings[currentLanguage]?.get(key) 
            ?: strings[SupportedLanguage.ENGLISH]?.get(key) 
            ?: key
    }
    
    /**
     * 格式化本地化字符串
     * @param key 字符串键
     * @param args 格式化参数
     * @param language 指定语言
     * @return 格式化后的本地化字符串
     */
    fun formatString(key: String, vararg args: Any, language: SupportedLanguage? = null): String {
        val template = getString(key, language)
        return LocalizationService.formatString(template, *args)
    }
}

/**
 * 便捷函数：获取本地化字符串
 */
fun stringResource(key: String): String = StringResources.getString(key)

/**
 * 便捷函数：格式化本地化字符串
 */
fun formatStringResource(key: String, vararg args: Any): String = 
    StringResources.formatString(key, *args)
