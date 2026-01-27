/**
 * Settings Module Exports
 * 
 * Provides settings management for Claude Code Plus VS Code extension
 */

// Agent Settings Models (types and enums)
export {
    DefaultThinkingLevel,
    DefaultThinkingLevelInfo,
    getDefaultThinkingLevelFromName,
    type ThinkingLevelConfig,
    type OptionConfig,
    type CustomModelConfig,
    ExternalPathRuleType,
    type ExternalPathRule,
    type ModelInfo,
    AiAgentProvider,
    MCP_BACKEND_ALL,
    MCP_BACKEND_CLAUDE,
    MCP_BACKEND_CODEX,
    type AgentConfig
} from './agentSettingsModels';

// Agent Settings Service
export {
    AgentSettingsService,
    agentSettingsService,
    type AgentSettingsState
} from './agentSettingsService';

// Environment Detection
export {
    EnvironmentDetection,
    type NodeInfo,
    type CodexInfo
} from './environmentDetection';

// MCP Models
export {
    McpServerLevel,
    type McpServerEntry,
    createDefaultMcpServerEntry,
    mcpServerEntryToJson,
    mcpServerEntryFromJson
} from './mcpModels';

// MCP Settings Service
export {
    McpSettingsService,
    mcpSettingsService
} from './mcpSettingsService';

// MCP Defaults
export {
    McpDefaults,
    KnownTools,
    AgentDefaults,
    GitGenerateDefaults,
    McpAutoApprovedDefaults
} from './mcpDefaults';

// Codex Settings (existing)
export {
    CodexSettings,
    codexSettings,
    ModelProvider,
    ModelProviderDisplayName,
    SandboxMode,
    SandboxModeDisplayName,
    type CodexSettingsState
} from './codexSettings';

// Backend Settings Service (existing)
export {
    BackendSettingsService,
    backendSettingsService,
    BackendType,
    BackendTypeInfo,
    type BackendAvailability,
    type BackendConfigDto,
    type BackendSettingsState
} from './backendSettingsService';
