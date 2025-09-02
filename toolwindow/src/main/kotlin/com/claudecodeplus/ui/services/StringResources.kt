package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.services.LocalizationService.SupportedLanguage

/**
 * 字符串资源管理器
 * 由于在插件环境中Compose资源系统可能不工作，使用手动管理的方式
 */
object StringResources {
    
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
            "tool_parameters" to "Parameters:",
            "tool_file_changes" to "File changes",
            "tool_search_results" to "Search results",
            
            // 消息显示
            "assistant_message" to "Assistant",
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
            "tool_parameters" to "参数：",
            "tool_file_changes" to "文件变更",
            "tool_search_results" to "搜索结果",
            
            // 消息显示
            "assistant_message" to "助手",
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
            "tool_parameters" to "參數：",
            "tool_file_changes" to "檔案變更",
            "tool_search_results" to "搜尋結果",
            
            // 消息顯示
            "assistant_message" to "助手",
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
            "tool_parameters" to "パラメータ：",
            "tool_file_changes" to "ファイル変更",
            "tool_search_results" to "検索結果",
            
            // メッセージ表示
            "assistant_message" to "アシスタント",
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
            
            // 메시지 표시
            "assistant_message" to "어시스턴트",
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