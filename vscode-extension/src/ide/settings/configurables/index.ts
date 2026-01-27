/**
 * VS Code Settings Configurables
 * 
 * 翻译自 JetBrains 设置 UI 模块:
 * - ClaudeCodeConfigurable.kt -> ClaudeCodeConfigurable.ts
 * - ClaudeCodePlusConfigurable.kt -> ClaudeCodePlusConfigurable.ts
 * - CodexConfigurable.kt -> CodexConfigurable.ts
 * - GitGenerateConfigurable.kt -> GitGenerateConfigurable.ts
 * - McpConfigurable.kt -> McpConfigurable.ts
 */

// Main settings
export { 
  ClaudeCodePlusConfigurable,
  BACKEND_TYPES,
  BACKEND_OPTIONS,
  MCP_BACKEND_CLAUDE,
  MCP_BACKEND_CODEX,
  MCP_BACKEND_ALL
} from './ClaudeCodePlusConfigurable'
export type { BackendType, BackendOption } from './ClaudeCodePlusConfigurable'

// Claude Code settings
export {
  ClaudeCodeConfigurable,
  BUILT_IN_MODELS,
  PERMISSION_MODES,
  THINKING_LEVELS,
  KNOWN_TOOLS,
  EXPLORE_WITH_VSCODE_DEFAULTS
} from './ClaudeCodeConfigurable'
export type {
  ModelInfo,
  CustomModelConfig,
  ThinkingLevelConfig,
  AgentConfigItem,
  AgentsConfigData,
  PermissionMode,
  ThinkingLevel
} from './ClaudeCodeConfigurable'

// Codex settings
export {
  CodexConfigurable,
  CODEX_BUILT_IN_MODELS,
  REASONING_EFFORT_OPTIONS,
  REASONING_SUMMARY_OPTIONS,
  SANDBOX_MODE_OPTIONS
} from './CodexConfigurable'
export type {
  CodexModelInfo,
  CodexCustomModelConfig,
  SandboxOption,
  ReasoningEffort,
  ReasoningSummary,
  SandboxMode
} from './CodexConfigurable'

// Git Generate settings
export {
  GitGenerateConfigurable,
  GIT_GENERATE_DEFAULT_SYSTEM_PROMPT,
  GIT_GENERATE_DEFAULT_USER_PROMPT
} from './GitGenerateConfigurable'

// MCP settings
export {
  McpConfigurable,
  BUILTIN_MCP_SERVERS,
  BUILTIN_MCP_DESCRIPTIONS
} from './McpConfigurable'
export type {
  McpServerLevel,
  McpServerEntry,
  McpServerConfig,
  CustomMcpServerConfig
} from './McpConfigurable'
