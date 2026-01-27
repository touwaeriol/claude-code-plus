/**
 * Agent Settings Service
 * 
 * Manages all AI Agent related configurations.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/AgentSettingsService.kt
 */

import * as vscode from 'vscode';
import {
    ThinkingLevelConfig,
    CustomModelConfig,
    ExternalPathRule,
    ExternalPathRuleType,
    ModelInfo,
    OptionConfig,
    AiAgentProvider,
    MCP_BACKEND_ALL,
    MCP_BACKEND_CLAUDE,
    MCP_BACKEND_CODEX
} from './agentSettingsModels';
import { EnvironmentDetection, NodeInfo, CodexInfo } from './environmentDetection';
import { McpDefaults, McpAutoApprovedDefaults, GitGenerateDefaults } from './mcpDefaults';

const CONFIG_SECTION = 'claudeCodePlus.agent';

/**
 * Agent Settings State
 */
export interface AgentSettingsState {
    // MCP Server Enable Config
    enableUserInteractionMcp: boolean;
    enableJetBrainsMcp: boolean;
    enableJetBrainsFileMcp: boolean;
    enableContext7Mcp: boolean;
    context7ApiKey: string;
    enableTerminalMcp: boolean;
    terminalDisableBuiltinBash: boolean;
    terminalMaxOutputLines: number;
    terminalMaxOutputChars: number;
    terminalDefaultShell: string;
    terminalAvailableShells: string;
    terminalReadTimeout: number;
    terminalDisableInteractive: boolean;
    enableGitMcp: boolean;
    
    // JetBrains File MCP Config
    jetbrainsFileDisableBuiltinTools: boolean;
    jetbrainsFileDisabledTools: string;
    jetbrainsFileAllowExternal: boolean;
    jetbrainsFileExternalRules: string;
    
    // MCP Tool Timeout Config (seconds)
    userInteractionMcpTimeout: number;
    jetbrainsMcpTimeout: number;
    jetbrainsFileMcpTimeout: number;
    context7McpTimeout: number;
    terminalMcpTimeout: number;
    gitMcpTimeout: number;
    
    // MCP Backend Enable Range
    userInteractionMcpBackends: string;
    jetbrainsMcpBackends: string;
    jetbrainsFileMcpBackends: string;
    context7McpBackends: string;
    terminalMcpBackends: string;
    gitMcpBackends: string;
    mcpEnabledBackends: string;
    
    // MCP System Instructions
    userInteractionInstructions: string;
    userInteractionInstructionsClaude: string;
    userInteractionInstructionsCodex: string;
    jetbrainsInstructions: string;
    jetbrainsInstructionsClaude: string;
    jetbrainsInstructionsCodex: string;
    jetbrainsFileInstructions: string;
    jetbrainsFileInstructionsClaude: string;
    jetbrainsFileInstructionsCodex: string;
    context7Instructions: string;
    context7InstructionsClaude: string;
    context7InstructionsCodex: string;
    terminalInstructions: string;
    terminalInstructionsClaude: string;
    terminalInstructionsCodex: string;
    gitInstructions: string;
    gitInstructionsClaude: string;
    gitInstructionsCodex: string;
    gitCommitLanguage: string;
    
    // MCP Auto-Approved Tools (Codex mode)
    jetbrainsFileAutoApprovedTools: string;
    jetbrainsLspAutoApprovedTools: string;
    terminalAutoApprovedTools: string;
    gitAutoApprovedTools: string;
    userInteractionAutoApprovedTools: string;
    
    // Git Generate Config
    gitGenerateSystemPrompt: string;
    gitGenerateUserPrompt: string;
    gitGenerateTools: string;
    gitGenerateModel: string;
    gitGenerateSaveSession: boolean;
    gitGenerateEnabled: boolean;
    gitGenerateBackend: string;
    gitGenerateClaudeThinkingLevelId: string;
    gitGenerateCodexReasoningEffort: string;
    
    // Default Permissions
    defaultBypassPermissions: boolean;
    claudeDefaultAutoCleanupContexts: boolean;
    codexDefaultAutoCleanupContexts: boolean;
    defaultBackendType: string;
    
    // Node.js and Codex Paths
    nodePath: string;
    codexPath: string;
    codexWebSearchEnabled: boolean;
    codexDefaultModelId: string;
    codexDefaultReasoningEffort: string;
    codexDefaultReasoningSummary: string;
    codexDefaultSandboxMode: string;
    codexCustomModels: string;
    
    // Model Config
    defaultModel: string;
    defaultThinkingLevelId: string;
    thinkTokens: number;
    ultraTokens: number;
    customThinkingLevels: string;
    permissionMode: string;
    includePartialMessages: boolean;
    customAgents: string;
    customModels: string;
}

const DEFAULT_STATE: AgentSettingsState = {
    enableUserInteractionMcp: true,
    enableJetBrainsMcp: true,
    enableJetBrainsFileMcp: true,
    enableContext7Mcp: false,
    context7ApiKey: '',
    enableTerminalMcp: false,
    terminalDisableBuiltinBash: true,
    terminalMaxOutputLines: 500,
    terminalMaxOutputChars: 50000,
    terminalDefaultShell: '',
    terminalAvailableShells: '',
    terminalReadTimeout: 10,
    terminalDisableInteractive: true,
    enableGitMcp: false,
    jetbrainsFileDisableBuiltinTools: true,
    jetbrainsFileDisabledTools: 'Read,Write,Edit',
    jetbrainsFileAllowExternal: true,
    jetbrainsFileExternalRules: '[]',
    userInteractionMcpTimeout: 3600,
    jetbrainsMcpTimeout: 60,
    jetbrainsFileMcpTimeout: 60,
    context7McpTimeout: 60,
    terminalMcpTimeout: 60,
    gitMcpTimeout: 60,
    userInteractionMcpBackends: '',
    jetbrainsMcpBackends: '',
    jetbrainsFileMcpBackends: '',
    context7McpBackends: '',
    terminalMcpBackends: '',
    gitMcpBackends: '',
    mcpEnabledBackends: 'all',
    userInteractionInstructions: '',
    userInteractionInstructionsClaude: '',
    userInteractionInstructionsCodex: '',
    jetbrainsInstructions: '',
    jetbrainsInstructionsClaude: '',
    jetbrainsInstructionsCodex: '',
    jetbrainsFileInstructions: '',
    jetbrainsFileInstructionsClaude: '',
    jetbrainsFileInstructionsCodex: '',
    context7Instructions: '',
    context7InstructionsClaude: '',
    context7InstructionsCodex: '',
    terminalInstructions: '',
    terminalInstructionsClaude: '',
    terminalInstructionsCodex: '',
    gitInstructions: '',
    gitInstructionsClaude: '',
    gitInstructionsCodex: '',
    gitCommitLanguage: 'en',
    jetbrainsFileAutoApprovedTools: '',
    jetbrainsLspAutoApprovedTools: '',
    terminalAutoApprovedTools: '',
    gitAutoApprovedTools: '',
    userInteractionAutoApprovedTools: '',
    gitGenerateSystemPrompt: '',
    gitGenerateUserPrompt: '',
    gitGenerateTools: '[]',
    gitGenerateModel: '',
    gitGenerateSaveSession: false,
    gitGenerateEnabled: false,
    gitGenerateBackend: MCP_BACKEND_CLAUDE,
    gitGenerateClaudeThinkingLevelId: 'ultra',
    gitGenerateCodexReasoningEffort: 'xhigh',
    defaultBypassPermissions: false,
    claudeDefaultAutoCleanupContexts: true,
    codexDefaultAutoCleanupContexts: true,
    defaultBackendType: MCP_BACKEND_CLAUDE,
    nodePath: '',
    codexPath: '',
    codexWebSearchEnabled: false,
    codexDefaultModelId: 'gpt-5.2-codex',
    codexDefaultReasoningEffort: 'xhigh',
    codexDefaultReasoningSummary: 'auto',
    codexDefaultSandboxMode: 'workspace-write',
    codexCustomModels: '[]',
    defaultModel: 'claude-opus-4-5-20251101',
    defaultThinkingLevelId: 'ultra',
    thinkTokens: 2048,
    ultraTokens: 8096,
    customThinkingLevels: '[]',
    permissionMode: 'default',
    includePartialMessages: true,
    customAgents: '{}',
    customModels: '[]'
};

/**
 * Built-in Claude models
 */
const BUILT_IN_CLAUDE_MODELS: ModelInfo[] = [
    { modelId: 'claude-opus-4-5-20251101', displayName: 'Opus 4.5', isBuiltIn: true },
    { modelId: 'claude-sonnet-4-5-20250929', displayName: 'Sonnet 4.5', isBuiltIn: true },
    { modelId: 'claude-haiku-4-5-20251001', displayName: 'Haiku 4.5', isBuiltIn: true }
];

/**
 * Legacy Claude model aliases for migration
 */
const LEGACY_CLAUDE_MODEL_ALIASES: Record<string, string> = {
    'OPUS_45': 'claude-opus-4-5-20251101',
    'SONNET_45': 'claude-sonnet-4-5-20250929',
    'HAIKU_45': 'claude-haiku-4-5-20251001',
    'claude-opus-4-5-20250929': 'claude-opus-4-5-20251101',
    'claude-haiku-4-5-20250929': 'claude-haiku-4-5-20251001'
};

/**
 * Built-in Codex models
 */
const BUILT_IN_CODEX_MODELS: ModelInfo[] = [
    { modelId: 'gpt-5.1-codex-max', displayName: 'GPT-5.1-Codex-Max', isBuiltIn: true },
    { modelId: 'gpt-5.2-codex', displayName: 'GPT-5.2-Codex', isBuiltIn: true },
    { modelId: 'gpt-5.2', displayName: 'GPT-5.2', isBuiltIn: true }
];

/**
 * Agent Settings Service
 * 
 * Manages all AI Agent related configurations using VS Code workspace.getConfiguration()
 */
export class AgentSettingsService {
    private static instance: AgentSettingsService | undefined;
    private changeListeners: Set<(service: AgentSettingsService) => void> = new Set();
    private disposables: vscode.Disposable[] = [];

    private constructor() {
        // Listen for configuration changes
        this.disposables.push(
            vscode.workspace.onDidChangeConfiguration(e => {
                if (e.affectsConfiguration(CONFIG_SECTION)) {
                    this.notifyChange();
                }
            })
        );
    }

    static getInstance(): AgentSettingsService {
        if (!this.instance) {
            this.instance = new AgentSettingsService();
        }
        return this.instance;
    }

    // ==================== Configuration Access ====================

    private getConfig(): vscode.WorkspaceConfiguration {
        return vscode.workspace.getConfiguration(CONFIG_SECTION);
    }

    private getValue<T>(key: keyof AgentSettingsState): T {
        const config = this.getConfig();
        const defaultValue = DEFAULT_STATE[key];
        return config.get<T>(key as string, defaultValue as T);
    }

    private async setValue<T>(key: keyof AgentSettingsState, value: T, global = true): Promise<void> {
        const config = this.getConfig();
        const target = global ? vscode.ConfigurationTarget.Global : vscode.ConfigurationTarget.Workspace;
        await config.update(key as string, value, target);
    }

    // ==================== Listener Management ====================

    addChangeListener(listener: (service: AgentSettingsService) => void): void {
        this.changeListeners.add(listener);
    }

    removeChangeListener(listener: (service: AgentSettingsService) => void): void {
        this.changeListeners.delete(listener);
    }

    notifyChange(): void {
        this.changeListeners.forEach(listener => listener(this));
    }

    // ==================== Normalization Helpers ====================

    private normalizeBackendType(value: string): string {
        const normalized = value?.trim().toLowerCase() || '';
        return normalized === MCP_BACKEND_CODEX ? MCP_BACKEND_CODEX : MCP_BACKEND_CLAUDE;
    }

    private normalizeClaudeModelId(rawModelId: string): string {
        return LEGACY_CLAUDE_MODEL_ALIASES[rawModelId] || rawModelId;
    }

    private normalizeCodexReasoningEffort(value: string | null | undefined): string {
        const normalized = value?.trim().toLowerCase() || '';
        const valid = ['minimal', 'low', 'medium', 'high', 'xhigh'];
        return valid.includes(normalized) ? normalized : 'medium';
    }

    private normalizeCodexReasoningSummary(value: string | null | undefined): string {
        const normalized = value?.trim().toLowerCase() || '';
        const valid = ['auto', 'concise', 'detailed', 'none'];
        return valid.includes(normalized) ? normalized : 'auto';
    }

    private normalizeCodexSandboxMode(value: string | null | undefined): string {
        const normalized = value?.trim().toLowerCase() || '';
        if (normalized === 'full-access') return 'danger-full-access';
        const valid = ['read-only', 'workspace-write', 'danger-full-access'];
        return valid.includes(normalized) ? normalized : 'workspace-write';
    }

    private normalizeThinkingLevelId(value: string | null | undefined): string {
        const normalized = value?.trim().toLowerCase() || 'ultra';
        const levels = this.getAllThinkingLevels();
        return levels.find(l => l.id === normalized)?.id || 'ultra';
    }

    // ==================== MCP Enable Properties ====================

    get enableUserInteractionMcp(): boolean {
        return this.getValue<boolean>('enableUserInteractionMcp');
    }
    set enableUserInteractionMcp(value: boolean) {
        this.setValue('enableUserInteractionMcp', value);
    }

    get enableJetBrainsMcp(): boolean {
        return this.getValue<boolean>('enableJetBrainsMcp');
    }
    set enableJetBrainsMcp(value: boolean) {
        this.setValue('enableJetBrainsMcp', value);
    }

    get enableJetBrainsFileMcp(): boolean {
        return this.getValue<boolean>('enableJetBrainsFileMcp');
    }
    set enableJetBrainsFileMcp(value: boolean) {
        this.setValue('enableJetBrainsFileMcp', value);
    }

    get enableContext7Mcp(): boolean {
        return this.getValue<boolean>('enableContext7Mcp');
    }
    set enableContext7Mcp(value: boolean) {
        this.setValue('enableContext7Mcp', value);
    }

    get context7ApiKey(): string {
        return this.getValue<string>('context7ApiKey');
    }
    set context7ApiKey(value: string) {
        this.setValue('context7ApiKey', value);
    }

    get enableTerminalMcp(): boolean {
        return this.getValue<boolean>('enableTerminalMcp');
    }
    set enableTerminalMcp(value: boolean) {
        this.setValue('enableTerminalMcp', value);
    }

    get terminalDisableBuiltinBash(): boolean {
        return this.getValue<boolean>('terminalDisableBuiltinBash');
    }
    set terminalDisableBuiltinBash(value: boolean) {
        this.setValue('terminalDisableBuiltinBash', value);
    }

    get terminalMaxOutputLines(): number {
        return this.getValue<number>('terminalMaxOutputLines');
    }
    set terminalMaxOutputLines(value: number) {
        this.setValue('terminalMaxOutputLines', value);
    }

    get terminalMaxOutputChars(): number {
        return this.getValue<number>('terminalMaxOutputChars');
    }
    set terminalMaxOutputChars(value: number) {
        this.setValue('terminalMaxOutputChars', value);
    }

    get terminalDefaultShell(): string {
        return this.getValue<string>('terminalDefaultShell');
    }
    set terminalDefaultShell(value: string) {
        this.setValue('terminalDefaultShell', value);
    }

    get terminalAvailableShells(): string {
        return this.getValue<string>('terminalAvailableShells');
    }
    set terminalAvailableShells(value: string) {
        this.setValue('terminalAvailableShells', value);
    }

    get terminalReadTimeout(): number {
        return this.getValue<number>('terminalReadTimeout');
    }
    set terminalReadTimeout(value: number) {
        this.setValue('terminalReadTimeout', value);
    }

    get terminalReadTimeoutMs(): number {
        return this.terminalReadTimeout * 1000;
    }

    get terminalDisableInteractive(): boolean {
        return this.getValue<boolean>('terminalDisableInteractive');
    }
    set terminalDisableInteractive(value: boolean) {
        this.setValue('terminalDisableInteractive', value);
    }

    get enableGitMcp(): boolean {
        return this.getValue<boolean>('enableGitMcp');
    }
    set enableGitMcp(value: boolean) {
        this.setValue('enableGitMcp', value);
    }

    // ==================== JetBrains File MCP Config ====================

    get jetbrainsFileDisableBuiltinTools(): boolean {
        return this.getValue<boolean>('jetbrainsFileDisableBuiltinTools');
    }
    set jetbrainsFileDisableBuiltinTools(value: boolean) {
        this.setValue('jetbrainsFileDisableBuiltinTools', value);
    }

    get jetbrainsFileDisabledTools(): string {
        return this.getValue<string>('jetbrainsFileDisabledTools');
    }
    set jetbrainsFileDisabledTools(value: string) {
        this.setValue('jetbrainsFileDisabledTools', value);
    }

    getJetbrainsFileDisabledToolsList(): string[] {
        return this.jetbrainsFileDisabledTools
            .split(',')
            .map(t => t.trim())
            .filter(t => t.length > 0);
    }

    setJetbrainsFileDisabledToolsList(tools: string[]): void {
        this.jetbrainsFileDisabledTools = tools.join(',');
    }

    get jetbrainsFileAllowExternal(): boolean {
        return this.getValue<boolean>('jetbrainsFileAllowExternal');
    }
    set jetbrainsFileAllowExternal(value: boolean) {
        this.setValue('jetbrainsFileAllowExternal', value);
    }

    get jetbrainsFileExternalRules(): string {
        return this.getValue<string>('jetbrainsFileExternalRules');
    }
    set jetbrainsFileExternalRules(value: string) {
        this.setValue('jetbrainsFileExternalRules', value);
    }

    getExternalPathRules(): ExternalPathRule[] {
        try {
            return JSON.parse(this.jetbrainsFileExternalRules);
        } catch {
            return [];
        }
    }

    setExternalPathRules(rules: ExternalPathRule[]): void {
        this.jetbrainsFileExternalRules = JSON.stringify(rules);
    }

    addExternalPathRule(rule: ExternalPathRule): void {
        const rules = this.getExternalPathRules();
        if (!rules.some(r => r.path === rule.path && r.type === rule.type)) {
            rules.push(rule);
            this.setExternalPathRules(rules);
        }
    }

    removeExternalPathRule(index: number): void {
        const rules = this.getExternalPathRules();
        if (index >= 0 && index < rules.length) {
            rules.splice(index, 1);
            this.setExternalPathRules(rules);
        }
    }

    getJetbrainsFileExternalDirs(): string[] {
        if (!this.jetbrainsFileAllowExternal) return [];
        return this.getExternalPathRules()
            .filter(r => r.type === ExternalPathRuleType.INCLUDE)
            .map(r => r.path);
    }

    isFilePathAllowed(filePath: string, projectBasePath: string): boolean {
        const normalizedPath = filePath.replace(/\\/g, '/').toLowerCase();
        const normalizedProjectPath = projectBasePath.replace(/\\/g, '/').toLowerCase();

        // Project files are always allowed
        if (normalizedPath.startsWith(normalizedProjectPath)) {
            return true;
        }

        // If external access is disabled, deny
        if (!this.jetbrainsFileAllowExternal) {
            return false;
        }

        const rules = this.getExternalPathRules();

        // If no rules, allow all external paths
        if (rules.length === 0) {
            return true;
        }

        // Check exclude rules first (higher priority)
        for (const rule of rules.filter(r => r.type === ExternalPathRuleType.EXCLUDE)) {
            const rulePath = rule.path.replace(/\\/g, '/').toLowerCase();
            if (normalizedPath.startsWith(rulePath) || normalizedPath.startsWith(`${rulePath}/`)) {
                return false;
            }
        }

        // Check include rules
        const includeRules = rules.filter(r => r.type === ExternalPathRuleType.INCLUDE);
        if (includeRules.length === 0) {
            return true; // Only exclude rules, allow non-excluded paths
        }

        for (const rule of includeRules) {
            const rulePath = rule.path.replace(/\\/g, '/').toLowerCase();
            if (normalizedPath.startsWith(rulePath) || normalizedPath.startsWith(`${rulePath}/`)) {
                return true;
            }
        }

        return false;
    }

    // ==================== MCP Auto-Approved Tools ====================

    getJetbrainsFileAutoApprovedTools(): string[] {
        const custom = this.getValue<string>('jetbrainsFileAutoApprovedTools');
        if (!custom || custom.trim() === '') {
            return McpAutoApprovedDefaults.JETBRAINS_FILE;
        }
        return custom.split(',').map(t => t.trim()).filter(t => t.length > 0);
    }

    setJetbrainsFileAutoApprovedTools(tools: string[]): void {
        this.setValue('jetbrainsFileAutoApprovedTools', tools.join(','));
    }

    getJetbrainsLspAutoApprovedTools(): string[] {
        const custom = this.getValue<string>('jetbrainsLspAutoApprovedTools');
        if (!custom || custom.trim() === '') {
            return McpAutoApprovedDefaults.JETBRAINS_LSP;
        }
        return custom.split(',').map(t => t.trim()).filter(t => t.length > 0);
    }

    setJetbrainsLspAutoApprovedTools(tools: string[]): void {
        this.setValue('jetbrainsLspAutoApprovedTools', tools.join(','));
    }

    getTerminalAutoApprovedTools(): string[] {
        const custom = this.getValue<string>('terminalAutoApprovedTools');
        if (!custom || custom.trim() === '') {
            return McpAutoApprovedDefaults.JETBRAINS_TERMINAL;
        }
        return custom.split(',').map(t => t.trim()).filter(t => t.length > 0);
    }

    setTerminalAutoApprovedTools(tools: string[]): void {
        this.setValue('terminalAutoApprovedTools', tools.join(','));
    }

    getGitAutoApprovedTools(): string[] {
        const custom = this.getValue<string>('gitAutoApprovedTools');
        if (!custom || custom.trim() === '') {
            return McpAutoApprovedDefaults.JETBRAINS_GIT;
        }
        return custom.split(',').map(t => t.trim()).filter(t => t.length > 0);
    }

    setGitAutoApprovedTools(tools: string[]): void {
        this.setValue('gitAutoApprovedTools', tools.join(','));
    }

    getUserInteractionAutoApprovedTools(): string[] {
        const custom = this.getValue<string>('userInteractionAutoApprovedTools');
        if (!custom || custom.trim() === '') {
            return McpAutoApprovedDefaults.USER_INTERACTION;
        }
        return custom.split(',').map(t => t.trim()).filter(t => t.length > 0);
    }

    setUserInteractionAutoApprovedTools(tools: string[]): void {
        this.setValue('userInteractionAutoApprovedTools', tools.join(','));
    }

    // ==================== Terminal Environment ====================

    getTerminalEnvVariables(): Record<string, string> {
        if (this.terminalDisableInteractive) {
            return {
                'TERM': 'dumb',
                'GIT_PAGER': 'cat',
                'PAGER': 'cat'
            };
        }
        return {};
    }

    getEffectiveDefaultShell(): string {
        const configured = this.terminalDefaultShell;
        if (configured) {
            return configured;
        }
        // Default based on platform
        return EnvironmentDetection.isWindows() ? 'powershell' : 'bash';
    }

    getEffectiveAvailableShells(): string[] {
        const configured = this.terminalAvailableShells.trim();
        if (configured) {
            return configured.split(',').map(s => s.trim()).filter(s => s.length > 0);
        }
        // Return default shells based on platform
        if (EnvironmentDetection.isWindows()) {
            return ['powershell', 'cmd', 'git-bash', 'wsl'];
        }
        return ['bash', 'zsh', 'fish', 'sh'];
    }

    isWindows(): boolean {
        return EnvironmentDetection.isWindows();
    }

    // ==================== Backend Configuration ====================

    get defaultBackendType(): string {
        return this.normalizeBackendType(this.getValue<string>('defaultBackendType'));
    }
    set defaultBackendType(value: string) {
        this.setValue('defaultBackendType', this.normalizeBackendType(value));
    }

    getDefaultBackendProvider(): AiAgentProvider {
        return this.defaultBackendType === MCP_BACKEND_CODEX
            ? AiAgentProvider.CODEX
            : AiAgentProvider.CLAUDE;
    }

    // ==================== Model Management ====================

    get defaultModel(): string {
        return this.getValue<string>('defaultModel');
    }
    set defaultModel(value: string) {
        this.setValue('defaultModel', value);
    }

    getCustomModels(): CustomModelConfig[] {
        try {
            return JSON.parse(this.getValue<string>('customModels'));
        } catch {
            return [];
        }
    }

    setCustomModels(models: CustomModelConfig[]): void {
        this.setValue('customModels', JSON.stringify(models));
    }

    addCustomModel(displayName: string, modelId: string): CustomModelConfig {
        const models = this.getCustomModels();
        const id = `custom_${Date.now()}`;
        const newModel: CustomModelConfig = { id, displayName, modelId };
        models.push(newModel);
        this.setCustomModels(models);
        return newModel;
    }

    updateCustomModel(id: string, displayName: string, modelId: string): CustomModelConfig | null {
        const models = this.getCustomModels();
        const index = models.findIndex(m => m.id === id);
        if (index >= 0) {
            const updated: CustomModelConfig = { id, displayName, modelId };
            models[index] = updated;
            this.setCustomModels(models);
            return updated;
        }
        return null;
    }

    removeCustomModel(id: string): void {
        const models = this.getCustomModels();
        const removedModel = models.find(m => m.id === id);
        const filtered = models.filter(m => m.id !== id);
        this.setCustomModels(filtered);
        
        // If removed model was default, switch to first built-in
        if (removedModel && this.defaultModel === removedModel.modelId) {
            this.defaultModel = BUILT_IN_CLAUDE_MODELS[0].modelId;
        }
    }

    getAllAvailableModels(): ModelInfo[] {
        const custom = this.getCustomModels().map(m => ({
            modelId: m.modelId,
            displayName: m.displayName,
            isBuiltIn: false
        }));
        return [...BUILT_IN_CLAUDE_MODELS, ...custom];
    }

    getModelById(modelId: string): ModelInfo | undefined {
        const normalized = this.normalizeClaudeModelId(modelId);
        const builtIn = BUILT_IN_CLAUDE_MODELS.find(m => m.modelId === normalized);
        if (builtIn) return builtIn;
        
        const custom = this.getCustomModels().find(m => m.modelId === normalized);
        if (custom) {
            return {
                modelId: custom.modelId,
                displayName: custom.displayName,
                isBuiltIn: false
            };
        }
        return undefined;
    }

    get effectiveDefaultModelId(): string {
        const model = this.getModelById(this.defaultModel);
        return model?.modelId || BUILT_IN_CLAUDE_MODELS[0].modelId;
    }

    // ==================== Codex Models ====================

    getCodexBuiltInModels(): ModelInfo[] {
        return BUILT_IN_CODEX_MODELS;
    }

    getCodexCustomModels(): CustomModelConfig[] {
        try {
            return JSON.parse(this.getValue<string>('codexCustomModels'));
        } catch {
            return [];
        }
    }

    setCodexCustomModels(models: CustomModelConfig[]): void {
        this.setValue('codexCustomModels', JSON.stringify(models));
    }

    getAllCodexModels(): ModelInfo[] {
        const custom = this.getCodexCustomModels().map(m => ({
            modelId: m.modelId,
            displayName: m.displayName,
            isBuiltIn: false
        }));
        return [...BUILT_IN_CODEX_MODELS, ...custom];
    }

    getCodexModelById(modelId: string): ModelInfo | undefined {
        const builtIn = BUILT_IN_CODEX_MODELS.find(m => m.modelId === modelId);
        if (builtIn) return builtIn;
        
        const custom = this.getCodexCustomModels().find(m => m.modelId === modelId);
        if (custom) {
            return {
                modelId: custom.modelId,
                displayName: custom.displayName,
                isBuiltIn: false
            };
        }
        return undefined;
    }

    get codexDefaultModelId(): string {
        return this.getValue<string>('codexDefaultModelId');
    }
    set codexDefaultModelId(value: string) {
        this.setValue('codexDefaultModelId', value);
    }

    get effectiveCodexDefaultModelId(): string {
        const model = this.getCodexModelById(this.codexDefaultModelId);
        return model?.modelId || BUILT_IN_CODEX_MODELS[0].modelId;
    }

    get codexDefaultReasoningEffort(): string {
        return this.normalizeCodexReasoningEffort(this.getValue<string>('codexDefaultReasoningEffort'));
    }
    set codexDefaultReasoningEffort(value: string) {
        this.setValue('codexDefaultReasoningEffort', this.normalizeCodexReasoningEffort(value));
    }

    get codexDefaultReasoningSummary(): string {
        return this.normalizeCodexReasoningSummary(this.getValue<string>('codexDefaultReasoningSummary'));
    }
    set codexDefaultReasoningSummary(value: string) {
        this.setValue('codexDefaultReasoningSummary', this.normalizeCodexReasoningSummary(value));
    }

    get codexDefaultSandboxMode(): string {
        return this.normalizeCodexSandboxMode(this.getValue<string>('codexDefaultSandboxMode'));
    }
    set codexDefaultSandboxMode(value: string) {
        this.setValue('codexDefaultSandboxMode', this.normalizeCodexSandboxMode(value));
    }

    // ==================== Thinking Levels ====================

    get defaultThinkingLevelId(): string {
        const id = this.getValue<string>('defaultThinkingLevelId');
        return ['off', 'think', 'ultra'].includes(id) ? id : 'ultra';
    }
    set defaultThinkingLevelId(value: string) {
        this.setValue('defaultThinkingLevelId', value);
    }

    get thinkTokens(): number {
        return this.getValue<number>('thinkTokens');
    }
    set thinkTokens(value: number) {
        this.setValue('thinkTokens', value);
    }

    get ultraTokens(): number {
        return this.getValue<number>('ultraTokens');
    }
    set ultraTokens(value: number) {
        this.setValue('ultraTokens', value);
    }

    getAllThinkingLevels(): ThinkingLevelConfig[] {
        return [
            { id: 'off', name: 'Off', tokens: 0, isCustom: false },
            { id: 'think', name: 'Think', tokens: this.thinkTokens, isCustom: false },
            { id: 'ultra', name: 'Ultra', tokens: this.ultraTokens, isCustom: false }
        ];
    }

    getThinkingLevelById(id: string): ThinkingLevelConfig | undefined {
        return this.getAllThinkingLevels().find(l => l.id === id);
    }

    get defaultThinkingTokens(): number {
        const level = this.getThinkingLevelById(this.defaultThinkingLevelId);
        return level?.tokens || this.ultraTokens;
    }

    // ==================== Git Generate Config ====================

    get gitGenerateEnabled(): boolean {
        return this.getValue<boolean>('gitGenerateEnabled');
    }
    set gitGenerateEnabled(value: boolean) {
        this.setValue('gitGenerateEnabled', value);
    }

    get gitGenerateBackend(): string {
        return this.normalizeBackendType(this.getValue<string>('gitGenerateBackend'));
    }
    set gitGenerateBackend(value: string) {
        this.setValue('gitGenerateBackend', this.normalizeBackendType(value));
    }

    get gitGenerateModel(): string {
        return this.getValue<string>('gitGenerateModel');
    }
    set gitGenerateModel(value: string) {
        this.setValue('gitGenerateModel', value);
    }

    get effectiveGitGenerateSystemPrompt(): string {
        const custom = this.getValue<string>('gitGenerateSystemPrompt');
        return custom || GitGenerateDefaults.SYSTEM_PROMPT;
    }

    get effectiveGitGenerateUserPrompt(): string {
        const custom = this.getValue<string>('gitGenerateUserPrompt');
        return custom || GitGenerateDefaults.USER_PROMPT;
    }

    getGitGenerateTools(): string[] {
        try {
            const tools = JSON.parse(this.getValue<string>('gitGenerateTools'));
            return tools.length > 0 ? tools : GitGenerateDefaults.TOOLS;
        } catch {
            return GitGenerateDefaults.TOOLS;
        }
    }

    get effectiveGitGenerateModelId(): string {
        const backend = this.gitGenerateBackend;
        const configuredModelId = this.gitGenerateModel;
        
        if (!configuredModelId) {
            return backend === MCP_BACKEND_CODEX
                ? this.effectiveCodexDefaultModelId
                : this.effectiveDefaultModelId;
        }
        
        if (backend === MCP_BACKEND_CODEX) {
            const model = this.getCodexModelById(configuredModelId);
            return model?.modelId || BUILT_IN_CODEX_MODELS[0].modelId;
        }
        
        const model = this.getModelById(this.normalizeClaudeModelId(configuredModelId));
        return model?.modelId || BUILT_IN_CLAUDE_MODELS[0].modelId;
    }

    // ==================== MCP Instructions ====================

    get effectiveUserInteractionInstructions(): string {
        return this.getValue<string>('userInteractionInstructions') || McpDefaults.USER_INTERACTION_INSTRUCTIONS;
    }

    get effectiveJetbrainsInstructions(): string {
        return this.getValue<string>('jetbrainsInstructions') || McpDefaults.JETBRAINS_INSTRUCTIONS;
    }

    get effectiveJetbrainsFileInstructions(): string {
        return this.getValue<string>('jetbrainsFileInstructions') || McpDefaults.JETBRAINS_FILE_INSTRUCTIONS;
    }

    get effectiveContext7Instructions(): string {
        return this.getValue<string>('context7Instructions') || McpDefaults.CONTEXT7_INSTRUCTIONS;
    }

    get effectiveTerminalInstructions(): string {
        return this.getValue<string>('terminalInstructions') || McpDefaults.TERMINAL_INSTRUCTIONS;
    }

    get effectiveGitInstructions(): string {
        return this.getValue<string>('gitInstructions') || McpDefaults.GIT_INSTRUCTIONS;
    }

    // ==================== Other Properties ====================

    get permissionMode(): string {
        return this.getValue<string>('permissionMode');
    }
    set permissionMode(value: string) {
        this.setValue('permissionMode', value);
    }

    get includePartialMessages(): boolean {
        return this.getValue<boolean>('includePartialMessages');
    }
    set includePartialMessages(value: boolean) {
        this.setValue('includePartialMessages', value);
    }

    get defaultBypassPermissions(): boolean {
        return this.getValue<boolean>('defaultBypassPermissions');
    }
    set defaultBypassPermissions(value: boolean) {
        this.setValue('defaultBypassPermissions', value);
    }

    get claudeDefaultAutoCleanupContexts(): boolean {
        return this.getValue<boolean>('claudeDefaultAutoCleanupContexts');
    }
    set claudeDefaultAutoCleanupContexts(value: boolean) {
        this.setValue('claudeDefaultAutoCleanupContexts', value);
    }

    get codexDefaultAutoCleanupContexts(): boolean {
        return this.getValue<boolean>('codexDefaultAutoCleanupContexts');
    }
    set codexDefaultAutoCleanupContexts(value: boolean) {
        this.setValue('codexDefaultAutoCleanupContexts', value);
    }

    get nodePath(): string {
        return this.getValue<string>('nodePath');
    }
    set nodePath(value: string) {
        this.setValue('nodePath', value);
    }

    get codexPath(): string {
        return this.getValue<string>('codexPath');
    }
    set codexPath(value: string) {
        this.setValue('codexPath', value);
    }

    get codexWebSearchEnabled(): boolean {
        return this.getValue<boolean>('codexWebSearchEnabled');
    }
    set codexWebSearchEnabled(value: boolean) {
        this.setValue('codexWebSearchEnabled', value);
    }

    get gitCommitLanguage(): string {
        return this.getValue<string>('gitCommitLanguage');
    }
    set gitCommitLanguage(value: string) {
        this.setValue('gitCommitLanguage', value);
    }

    // ==================== Option Lists ====================

    getCodexReasoningEffortOptions(): OptionConfig[] {
        const defaultId = this.codexDefaultReasoningEffort;
        return [
            { id: 'none', label: 'None', description: 'No reasoning', isDefault: defaultId === 'none' },
            { id: 'minimal', label: 'Minimal', description: 'Minimal reasoning', isDefault: defaultId === 'minimal' },
            { id: 'low', label: 'Low', description: 'Low reasoning', isDefault: defaultId === 'low' },
            { id: 'medium', label: 'Medium', description: 'Balanced reasoning', isDefault: defaultId === 'medium' },
            { id: 'high', label: 'High', description: 'High reasoning', isDefault: defaultId === 'high' },
            { id: 'xhigh', label: 'Extra High', description: 'Extra high reasoning', isDefault: defaultId === 'xhigh' }
        ];
    }

    getCodexReasoningSummaryOptions(): OptionConfig[] {
        const defaultId = this.codexDefaultReasoningSummary;
        return [
            { id: 'auto', label: 'Auto', description: 'Automatic summary', isDefault: defaultId === 'auto' },
            { id: 'concise', label: 'Concise', description: 'Brief summary', isDefault: defaultId === 'concise' },
            { id: 'detailed', label: 'Detailed', description: 'Full summary', isDefault: defaultId === 'detailed' },
            { id: 'none', label: 'None', description: 'No summary', isDefault: defaultId === 'none' }
        ];
    }

    getCodexSandboxModeOptions(): OptionConfig[] {
        const defaultId = this.codexDefaultSandboxMode;
        return [
            { id: 'read-only', label: 'Read Only', description: 'Read-only access', isDefault: defaultId === 'read-only' },
            { id: 'workspace-write', label: 'Workspace Write', description: 'Write to workspace only', isDefault: defaultId === 'workspace-write' },
            { id: 'danger-full-access', label: 'Full Access', description: 'Full file system access (dangerous)', isDefault: defaultId === 'danger-full-access' }
        ];
    }

    getPermissionModeOptions(): OptionConfig[] {
        const defaultId = this.permissionMode;
        return [
            { id: 'default', label: 'Default', description: 'Normal permission checks', isDefault: defaultId === 'default' },
            { id: 'acceptEdits', label: 'Accept Edits', description: 'Auto-accept file edits', isDefault: defaultId === 'acceptEdits' },
            { id: 'plan', label: 'Plan Mode', description: 'Plan before execution', isDefault: defaultId === 'plan' },
            { id: 'bypassPermissions', label: 'Bypass', description: 'Skip all permission checks', isDefault: defaultId === 'bypassPermissions' }
        ];
    }

    // ==================== Static Methods ====================

    static detectNodeInfo(): NodeInfo | undefined {
        return EnvironmentDetection.detectNodeInfo();
    }

    static detectCodexInfo(): CodexInfo | undefined {
        return EnvironmentDetection.detectCodexInfo();
    }

    static detectNodePath(): string {
        return EnvironmentDetection.detectNodePath();
    }

    static detectCodexPath(): string {
        return EnvironmentDetection.detectCodexPath();
    }

    // ==================== Dispose ====================

    dispose(): void {
        this.disposables.forEach(d => d.dispose());
        this.disposables = [];
        this.changeListeners.clear();
        AgentSettingsService.instance = undefined;
    }
}

/**
 * Singleton instance
 */
export const agentSettingsService = AgentSettingsService.getInstance();

export default AgentSettingsService;
