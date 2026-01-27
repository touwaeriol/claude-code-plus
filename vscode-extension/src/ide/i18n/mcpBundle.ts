/**
 * MCP Bundle
 * 
 * Internationalization bundle for MCP-related messages.
 * Equivalent to JetBrains McpBundle.
 */

import { BaseBundle } from './baseBundle';
import { Locale, LocaleMessages } from './types';

/**
 * Message keys for McpBundle
 */
export type McpMessageKey =
    // MCP Server Descriptions
    | 'mcp.userInteraction.name'
    | 'mcp.userInteraction.description'
    | 'mcp.jetbrainsIde.name'
    | 'mcp.jetbrainsIde.description'
    | 'mcp.jetbrainsFile.name'
    | 'mcp.jetbrainsFile.description'
    | 'mcp.context7.name'
    | 'mcp.context7.description'
    | 'mcp.jetbrainsTerminal.name'
    | 'mcp.jetbrainsTerminal.description'
    | 'mcp.jetbrainsGit.name'
    | 'mcp.jetbrainsGit.description'
    // MCP Settings UI
    | 'mcp.settings.warning'
    | 'mcp.settings.title'
    | 'mcp.settings.learnMore'
    | 'mcp.settings.note'
    // Column Headers
    | 'mcp.settings.column.status'
    | 'mcp.settings.column.name'
    | 'mcp.settings.column.configuration'
    | 'mcp.settings.column.backends'
    // Git MCP Commit Language Requirement
    | 'mcp.git.commitLang.title'
    | 'mcp.git.commitLang.en'
    | 'mcp.git.commitLang.zh_CN'
    | 'mcp.git.commitLang.zh_TW'
    | 'mcp.git.commitLang.ja'
    | 'mcp.git.commitLang.ko';

/**
 * English messages (default)
 */
const en: Record<McpMessageKey, string> = {
    // MCP Server Descriptions
    'mcp.userInteraction.name': 'User Interaction MCP',
    'mcp.userInteraction.description': 'Allows the agent to ask questions',
    'mcp.jetbrainsIde.name': 'JetBrains LSP MCP',
    'mcp.jetbrainsIde.description': 'Code search, file indexing',
    'mcp.jetbrainsFile.name': 'JetBrains File MCP',
    'mcp.jetbrainsFile.description': 'File read/write/edit operations',
    'mcp.context7.name': 'Context7 MCP',
    'mcp.context7.description': 'Library documentation',
    'mcp.jetbrainsTerminal.name': 'JetBrains Terminal MCP',
    'mcp.jetbrainsTerminal.description': 'IDEA integrated terminal',
    'mcp.jetbrainsGit.name': 'JetBrains Git MCP',
    'mcp.jetbrainsGit.description': 'VCS integration and commit message generation',
    
    // MCP Settings UI
    'mcp.settings.warning': 'Proceed with caution and only connect to trusted servers.',
    'mcp.settings.title': 'Configure MCP (Model Context Protocol) servers.',
    'mcp.settings.learnMore': 'Learn more',
    'mcp.settings.note': 'Note: MCP servers configured here only apply to Claude Code Plus plugin sessions. They do not affect the standalone Claude CLI.',
    
    // Column Headers
    'mcp.settings.column.status': 'Status',
    'mcp.settings.column.name': 'Name',
    'mcp.settings.column.configuration': 'Configuration',
    'mcp.settings.column.backends': 'Backends',
    
    // Git MCP Commit Language Requirement
    'mcp.git.commitLang.title': '### Commit Message Language',
    'mcp.git.commitLang.en': '**IMPORTANT**: You MUST write all git commit messages in **English**.',
    'mcp.git.commitLang.zh_CN': '**IMPORTANT**: You MUST write all git commit messages in **Simplified Chinese (简体中文)**.',
    'mcp.git.commitLang.zh_TW': '**IMPORTANT**: You MUST write all git commit messages in **Traditional Chinese (繁體中文)**.',
    'mcp.git.commitLang.ja': '**IMPORTANT**: You MUST write all git commit messages in **Japanese (日本語)**.',
    'mcp.git.commitLang.ko': '**IMPORTANT**: You MUST write all git commit messages in **Korean (한국어)**.',
};

/**
 * Simplified Chinese messages
 */
const zh_CN: Record<McpMessageKey, string> = {
    // MCP Server Descriptions
    'mcp.userInteraction.name': 'User Interaction MCP',
    'mcp.userInteraction.description': '允许 Agent 向用户提问',
    'mcp.jetbrainsIde.name': 'JetBrains LSP MCP',
    'mcp.jetbrainsIde.description': '代码搜索、文件索引',
    'mcp.jetbrainsFile.name': 'JetBrains File MCP',
    'mcp.jetbrainsFile.description': '文件读取/写入/编辑操作',
    'mcp.context7.name': 'Context7 MCP',
    'mcp.context7.description': '库文档查询',
    'mcp.jetbrainsTerminal.name': 'JetBrains Terminal MCP',
    'mcp.jetbrainsTerminal.description': 'IDEA 集成终端',
    'mcp.jetbrainsGit.name': 'JetBrains Git MCP',
    'mcp.jetbrainsGit.description': '版本控制集成与提交消息生成',
    
    // MCP Settings UI
    'mcp.settings.warning': '请谨慎操作，仅连接可信任的服务器。',
    'mcp.settings.title': '配置 MCP (Model Context Protocol) 服务器。',
    'mcp.settings.learnMore': '了解更多',
    'mcp.settings.note': '注意：此处配置的 MCP 服务器仅适用于 Claude Code Plus 插件会话，不影响独立的 Claude CLI。',
    
    // Column Headers
    'mcp.settings.column.status': '状态',
    'mcp.settings.column.name': '名称',
    'mcp.settings.column.configuration': '配置',
    'mcp.settings.column.backends': '后端',
    
    // Git MCP Commit Language Requirement
    'mcp.git.commitLang.title': '### 提交消息语言',
    'mcp.git.commitLang.en': '**重要**: 你必须使用 **英文 (English)** 编写所有 git commit 消息。',
    'mcp.git.commitLang.zh_CN': '**重要**: 你必须使用 **简体中文** 编写所有 git commit 消息。',
    'mcp.git.commitLang.zh_TW': '**重要**: 你必须使用 **繁體中文** 编写所有 git commit 消息。',
    'mcp.git.commitLang.ja': '**重要**: 你必须使用 **日本語** 编写所有 git commit 消息。',
    'mcp.git.commitLang.ko': '**重要**: 你必须使用 **한국어** 编写所有 git commit 消息。',
};

/**
 * Japanese messages
 */
const ja: Record<McpMessageKey, string> = {
    // MCP Server Descriptions
    'mcp.userInteraction.name': 'User Interaction MCP',
    'mcp.userInteraction.description': 'エージェントがユーザーに質問できるようにする',
    'mcp.jetbrainsIde.name': 'JetBrains LSP MCP',
    'mcp.jetbrainsIde.description': 'コード検索、ファイルインデックス',
    'mcp.jetbrainsFile.name': 'JetBrains File MCP',
    'mcp.jetbrainsFile.description': 'ファイルの読み取り/書き込み/編集操作',
    'mcp.context7.name': 'Context7 MCP',
    'mcp.context7.description': 'ライブラリドキュメント',
    'mcp.jetbrainsTerminal.name': 'JetBrains Terminal MCP',
    'mcp.jetbrainsTerminal.description': 'IDEA 統合ターミナル',
    'mcp.jetbrainsGit.name': 'JetBrains Git MCP',
    'mcp.jetbrainsGit.description': 'バージョン管理統合とコミットメッセージ生成',
    
    // MCP Settings UI
    'mcp.settings.warning': '信頼できるサーバーにのみ接続してください。',
    'mcp.settings.title': 'MCP (Model Context Protocol) サーバーを設定します。',
    'mcp.settings.learnMore': '詳細',
    'mcp.settings.note': '注意：ここで設定した MCP サーバーは Claude Code Plus プラグインセッションにのみ適用され、スタンドアロン版 Claude CLI には影響しません。',
    
    // Column Headers
    'mcp.settings.column.status': 'ステータス',
    'mcp.settings.column.name': '名前',
    'mcp.settings.column.configuration': '設定',
    'mcp.settings.column.backends': 'バックエンド',
    
    // Git MCP Commit Language Requirement
    'mcp.git.commitLang.title': '### コミットメッセージの言語',
    'mcp.git.commitLang.en': '**重要**: すべての git commit メッセージは **英語 (English)** で記述してください。',
    'mcp.git.commitLang.zh_CN': '**重要**: すべての git commit メッセージは **簡体字中国語 (简体中文)** で記述してください。',
    'mcp.git.commitLang.zh_TW': '**重要**: すべての git commit メッセージは **繁体字中国語 (繁體中文)** で記述してください。',
    'mcp.git.commitLang.ja': '**重要**: すべての git commit メッセージは **日本語** で記述してください。',
    'mcp.git.commitLang.ko': '**重要**: すべての git commit メッセージは **韓国語 (한국어)** で記述してください。',
};

/**
 * Korean messages
 */
const ko: Record<McpMessageKey, string> = {
    // MCP Server Descriptions
    'mcp.userInteraction.name': 'User Interaction MCP',
    'mcp.userInteraction.description': '에이전트가 사용자에게 질문할 수 있도록 허용',
    'mcp.jetbrainsIde.name': 'JetBrains LSP MCP',
    'mcp.jetbrainsIde.description': '코드 검색, 파일 인덱싱',
    'mcp.jetbrainsFile.name': 'JetBrains File MCP',
    'mcp.jetbrainsFile.description': '파일 읽기/쓰기/편집 작업',
    'mcp.context7.name': 'Context7 MCP',
    'mcp.context7.description': '라이브러리 문서',
    'mcp.jetbrainsTerminal.name': 'JetBrains Terminal MCP',
    'mcp.jetbrainsTerminal.description': 'IDEA 통합 터미널',
    'mcp.jetbrainsGit.name': 'JetBrains Git MCP',
    'mcp.jetbrainsGit.description': '버전 관리 통합 및 커밋 메시지 생성',
    
    // MCP Settings UI
    'mcp.settings.warning': '신뢰할 수 있는 서버에만 연결하십시오.',
    'mcp.settings.title': 'MCP (Model Context Protocol) 서버를 구성합니다.',
    'mcp.settings.learnMore': '자세히 보기',
    'mcp.settings.note': '참고: 여기서 구성한 MCP 서버는 Claude Code Plus 플러그인 세션에만 적용되며, 독립 실행형 Claude CLI에는 영향을 미치지 않습니다.',
    
    // Column Headers
    'mcp.settings.column.status': '상태',
    'mcp.settings.column.name': '이름',
    'mcp.settings.column.configuration': '구성',
    'mcp.settings.column.backends': '백엔드',
    
    // Git MCP Commit Language Requirement
    'mcp.git.commitLang.title': '### 커밋 메시지 언어',
    'mcp.git.commitLang.en': '**중요**: 모든 git commit 메시지는 **영어 (English)**로 작성해야 합니다.',
    'mcp.git.commitLang.zh_CN': '**중요**: 모든 git commit 메시지는 **간체 중국어 (简体中文)**로 작성해야 합니다.',
    'mcp.git.commitLang.zh_TW': '**중요**: 모든 git commit 메시지는 **번체 중국어 (繁體中文)**로 작성해야 합니다.',
    'mcp.git.commitLang.ja': '**중요**: 모든 git commit 메시지는 **일본어 (日本語)**로 작성해야 합니다.',
    'mcp.git.commitLang.ko': '**중요**: 모든 git commit 메시지는 **한국어**로 작성해야 합니다.',
};

/**
 * All locale messages
 */
const messages: LocaleMessages<McpMessageKey> = {
    en,
    zh_CN,
    ja,
    ko,
};

/**
 * McpBundle class
 */
class McpBundleImpl extends BaseBundle<McpMessageKey> {
    constructor(locale?: Locale) {
        super(messages, locale);
    }
}

/**
 * Singleton instance
 */
export const McpBundle = new McpBundleImpl();

/**
 * Convenience function for getting MCP messages
 * 
 * @param key Message key
 * @param params Parameters to substitute
 * @returns Localized string
 */
export function mcpMessage(key: McpMessageKey, ...params: (string | number)[]): string {
    return McpBundle.message(key, ...params);
}
