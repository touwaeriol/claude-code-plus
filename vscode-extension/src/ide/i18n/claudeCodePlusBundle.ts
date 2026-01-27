/**
 * Claude Code Plus Bundle
 * 
 * Internationalization bundle for Claude Code Plus plugin messages.
 * Equivalent to JetBrains ClaudeCodePlusBundle.
 */

import { BaseBundle } from './baseBundle';
import { Locale, LocaleMessages } from './types';

/**
 * Message keys for ClaudeCodePlusBundle
 */
export type ClaudeCodePlusMessageKey =
    // MCP Configuration
    | 'mcp.settings.notice'
    | 'mcp.settings.custom.warning'
    // Agents Configuration
    | 'agents.settings.notice'
    // Dialog titles
    | 'dialog.edit.builtin.mcp'
    | 'dialog.new.mcp.server'
    | 'dialog.edit.mcp.server'
    // Labels
    | 'label.enable'
    | 'label.json.configuration'
    | 'label.appended.system.prompt'
    | 'label.server.level'
    | 'label.global'
    | 'label.project'
    | 'label.global.hint'
    | 'label.project.hint'
    | 'label.api.key'
    | 'label.reset.to.default'
    // Placeholders
    | 'placeholder.json.stdio'
    | 'placeholder.json.http';

/**
 * English messages (default)
 */
const en: Record<ClaudeCodePlusMessageKey, string> = {
    // MCP Configuration
    'mcp.settings.notice': 'Note: MCP servers configured here only apply to Claude Code Plus plugin sessions. They do not affect the standalone Claude Code CLI.',
    'mcp.settings.custom.warning': 'Proceed with caution and only connect to trusted servers.',
    
    // Agents Configuration
    'agents.settings.notice': 'Note: Custom agents configured here only apply to Claude Code Plus plugin sessions. They do not affect the standalone Claude Code CLI.',
    
    // Dialog titles
    'dialog.edit.builtin.mcp': 'Edit {0}',
    'dialog.new.mcp.server': 'New MCP Server',
    'dialog.edit.mcp.server': 'Edit MCP Server',
    
    // Labels
    'label.enable': 'Enable',
    'label.json.configuration': 'JSON configuration:',
    'label.appended.system.prompt': 'Appended System Prompt (optional):',
    'label.server.level': 'Server level:',
    'label.global': 'Global',
    'label.project': 'Project',
    'label.global.hint': 'Global: all projects',
    'label.project.hint': 'Project: current project only',
    'label.api.key': 'API Key (optional):',
    'label.reset.to.default': 'Reset to Default',
    
    // Placeholders
    'placeholder.json.stdio': '{"server-name": {"command": "...", "args": [...]}}',
    'placeholder.json.http': 'HTTP: {"name": {"type": "http", "url": "https://..."}}',
};

/**
 * Simplified Chinese messages
 */
const zh_CN: Record<ClaudeCodePlusMessageKey, string> = {
    // MCP Configuration
    'mcp.settings.notice': '注意：此处配置的 MCP 服务器仅对 Claude Code Plus 插件会话生效，不会影响独立的 Claude Code 命令行工具。',
    'mcp.settings.custom.warning': '请谨慎操作，仅连接可信任的服务器。',
    
    // Agents Configuration
    'agents.settings.notice': '注意：此处配置的自定义代理仅对 Claude Code Plus 插件会话生效，不会影响独立的 Claude Code 命令行工具。',
    
    // Dialog titles
    'dialog.edit.builtin.mcp': '编辑 {0}',
    'dialog.new.mcp.server': '新建 MCP 服务器',
    'dialog.edit.mcp.server': '编辑 MCP 服务器',
    
    // Labels
    'label.enable': '启用',
    'label.json.configuration': 'JSON 配置：',
    'label.appended.system.prompt': '追加系统提示词（可选）：',
    'label.server.level': '服务器级别：',
    'label.global': '全局',
    'label.project': '项目',
    'label.global.hint': '全局：所有项目',
    'label.project.hint': '项目：仅当前项目',
    'label.api.key': 'API 密钥（可选）：',
    'label.reset.to.default': '重置为默认值',
    
    // Placeholders
    'placeholder.json.stdio': '{"server-name": {"command": "...", "args": [...]}}',
    'placeholder.json.http': 'HTTP: {"name": {"type": "http", "url": "https://..."}}',
};

/**
 * Japanese messages
 */
const ja: Record<ClaudeCodePlusMessageKey, string> = {
    // MCP Configuration
    'mcp.settings.notice': '注意：ここで設定した MCP サーバーは Claude Code Plus プラグインのセッションにのみ適用されます。スタンドアロンの Claude Code CLI には影響しません。',
    'mcp.settings.custom.warning': '信頼できるサーバーにのみ接続してください。',
    
    // Agents Configuration
    'agents.settings.notice': '注意：ここで設定したカスタムエージェントは Claude Code Plus プラグインのセッションにのみ適用されます。スタンドアロンの Claude Code CLI には影響しません。',
    
    // Dialog titles
    'dialog.edit.builtin.mcp': '編集 {0}',
    'dialog.new.mcp.server': '新規 MCP サーバー',
    'dialog.edit.mcp.server': 'MCP サーバーの編集',
    
    // Labels
    'label.enable': '有効',
    'label.json.configuration': 'JSON 設定：',
    'label.appended.system.prompt': '追加システムプロンプト（任意）：',
    'label.server.level': 'サーバーレベル：',
    'label.global': 'グローバル',
    'label.project': 'プロジェクト',
    'label.global.hint': 'グローバル：全プロジェクト',
    'label.project.hint': 'プロジェクト：現在のプロジェクトのみ',
    'label.api.key': 'API キー（任意）：',
    'label.reset.to.default': 'デフォルトにリセット',
    
    // Placeholders
    'placeholder.json.stdio': '{"server-name": {"command": "...", "args": [...]}}',
    'placeholder.json.http': 'HTTP: {"name": {"type": "http", "url": "https://..."}}',
};

/**
 * Korean messages
 */
const ko: Record<ClaudeCodePlusMessageKey, string> = {
    // MCP Configuration
    'mcp.settings.notice': '참고: 여기서 구성한 MCP 서버는 Claude Code Plus 플러그인 세션에만 적용됩니다. 독립 실행형 Claude Code CLI에는 영향을 미치지 않습니다.',
    'mcp.settings.custom.warning': '신뢰할 수 있는 서버에만 연결하세요.',
    
    // Agents Configuration
    'agents.settings.notice': '참고: 여기서 구성한 사용자 정의 에이전트는 Claude Code Plus 플러그인 세션에만 적용됩니다. 독립 실행형 Claude Code CLI에는 영향을 미치지 않습니다.',
    
    // Dialog titles
    'dialog.edit.builtin.mcp': '{0} 편집',
    'dialog.new.mcp.server': '새 MCP 서버',
    'dialog.edit.mcp.server': 'MCP 서버 편집',
    
    // Labels
    'label.enable': '활성화',
    'label.json.configuration': 'JSON 구성:',
    'label.appended.system.prompt': '추가 시스템 프롬프트 (선택 사항):',
    'label.server.level': '서버 레벨:',
    'label.global': '전역',
    'label.project': '프로젝트',
    'label.global.hint': '전역: 모든 프로젝트',
    'label.project.hint': '프로젝트: 현재 프로젝트만',
    'label.api.key': 'API 키 (선택 사항):',
    'label.reset.to.default': '기본값으로 재설정',
    
    // Placeholders
    'placeholder.json.stdio': '{"server-name": {"command": "...", "args": [...]}}',
    'placeholder.json.http': 'HTTP: {"name": {"type": "http", "url": "https://..."}}',
};

/**
 * All locale messages
 */
const messages: LocaleMessages<ClaudeCodePlusMessageKey> = {
    en,
    zh_CN,
    ja,
    ko,
};

/**
 * ClaudeCodePlusBundle class
 */
class ClaudeCodePlusBundleImpl extends BaseBundle<ClaudeCodePlusMessageKey> {
    constructor(locale?: Locale) {
        super(messages, locale);
    }
}

/**
 * Singleton instance
 */
export const ClaudeCodePlusBundle = new ClaudeCodePlusBundleImpl();

/**
 * Convenience function for getting messages
 * 
 * @param key Message key
 * @param params Parameters to substitute
 * @returns Localized string
 */
export function message(key: ClaudeCodePlusMessageKey, ...params: (string | number)[]): string {
    return ClaudeCodePlusBundle.message(key, ...params);
}
