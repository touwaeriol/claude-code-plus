/**
 * IDE integration types (IDEA compatibility layer).
 */

// ========== 枚举定义 ==========

/**
 * 回滚状态枚举
 */
export enum RollbackStatus {
  STARTED = 0,
  SUCCESS = 1,
  FAILED = 2
}

/**
 * 终端后台状态枚举
 */
export enum TerminalBackgroundStatus {
  STARTED = 0,
  SUCCESS = 1,
  FAILED = 2
}

// ========== 批量回滚相关接口 ==========

/**
 * 批量回滚项
 */
export interface BatchRollbackItem {
  filePath: string
  beforeTimestamp: number
  toolUseId: string
}

/**
 * 批量回滚事件
 */
export interface BatchRollbackEvent {
  filePath: string
  toolUseId: string
  status: RollbackStatus
  error?: string
}

// ========== Terminal 后台执行接口 ==========

/**
 * 终端后台项
 */
export interface TerminalBackgroundItem {
  sessionId: string
  toolUseId: string
}

/**
 * 终端后台事件
 */
export interface TerminalBackgroundEvent {
  sessionId: string
  toolUseId: string
  status: TerminalBackgroundStatus
  error?: string
}

/**
 * 可后台的终端任务
 */
export interface BackgroundableTerminal {
  sessionId: string
  toolUseId: string
  command: string
  startTime: number
  elapsedMs: number
}

// ========== IDE 主题和设置接口 ==========

export interface IdeTheme {
  background: string
  foreground: string
  borderColor: string
  panelBackground: string
  textFieldBackground: string
  selectionBackground: string
  selectionForeground: string
  linkColor: string
  errorColor: string
  warningColor: string
  successColor: string
  separatorColor: string
  hoverBackground: string
  accentColor: string
  infoBackground: string
  codeBackground: string
  secondaryForeground: string
  fontFamily: string
  fontSize: number
  editorFontFamily: string
  editorFontSize: number
}

export interface SessionCommand {
  type: 'switch' | 'create' | 'close' | 'rename' | 'toggleHistory' | 'setLocale' | 'delete' | 'reset'
  sessionId?: string
  newName?: string
  locale?: string
}

export interface SessionSummary {
  id: string
  title: string
  sessionId?: string | null
  isGenerating: boolean
  isConnected: boolean
  isConnecting: boolean
}

export interface SessionState {
  sessions: SessionSummary[]
  activeSessionId?: string | null
}

// ========== 终端任务更新接口 ==========

/**
 * 终端任务更新信息
 */
export interface TerminalTaskUpdate {
  toolUseId: string       // MCP 工具调用 ID
  sessionId: string       // 终端会话 ID
  action: 'started' | 'completed' | 'backgrounded'  // 任务动作
  command: string         // 执行的命令
  isBackground: boolean   // 是否在后台执行
  startTime: number       // 开始时间戳（毫秒）
  elapsedMs?: number      // 已执行时长（毫秒）
}

// ========== 活跃文件信息接口 ==========

/**
 * 当前活跃文件信息
 */
export interface ActiveFileInfo {
  path: string           // 文件绝对路径
  relativePath: string   // 相对于项目根目录的路径
  name: string           // 文件名
  line?: number          // 当前光标所在行（1-based）
  column?: number        // 当前光标所在列（1-based）
  // 选区信息
  hasSelection: boolean
  startLine?: number     // 选区起始行（1-based）
  startColumn?: number   // 选区起始列（1-based）
  endLine?: number       // 选区结束行（1-based）
  endColumn?: number     // 选区结束列（1-based）
  selectedContent?: string // 选中的文本内容（可选）
  // 文件类型相关字段
  fileType?: string      // 文件类型: "text", "diff", "image", "binary"
  // Diff 视图专用字段
  diffOldContent?: string  // Diff 旧内容（左侧）
  diffNewContent?: string  // Diff 新内容（右侧）
  diffTitle?: string       // Diff 标题
}

// ========== IDE 设置接口 ==========

/**
 * IDE 设置接口（所有属性可选，以支持扩展和部分更新）
 */
export interface IdeSettings {
  defaultModelId?: string
  defaultModelName?: string
  defaultBypassPermissions?: boolean
  claudeDefaultAutoCleanupContexts?: boolean
  codexDefaultAutoCleanupContexts?: boolean
  enableUserInteractionMcp?: boolean
  enableJetbrainsMcp?: boolean
  includePartialMessages?: boolean
  codexDefaultModelId?: string
  codexDefaultReasoningEffort?: string
  codexDefaultReasoningSummary?: string
  codexDefaultSandboxMode?: string
  // 思考配置
  defaultThinkingLevelId?: string  // 默认思考级别 ID（如 "off", "think", "ultra", "custom_xxx"）
  defaultThinkingTokens?: number   // 默认思考 token 数量
  thinkingLevels?: ThinkingLevelConfig[]  // 所有可用的思考级别
  // 旧字段，保留向后兼容
  defaultThinkingLevel?: string  // 思考等级枚举名称（如 "HIGH", "MEDIUM", "OFF"）
  // 权限模式
  permissionMode?: string  // 权限模式（default, acceptEdits, plan, bypassPermissions）

  // 配置选项列表（由后端动态返回）
  codexReasoningEffortOptions?: OptionConfig[]  // Codex 推理努力级别选项
  codexReasoningSummaryOptions?: OptionConfig[] // Codex 推理总结模式选项
  codexSandboxModeOptions?: OptionConfig[]      // Codex 沙盒模式选项
  permissionModeOptions?: OptionConfig[]        // 权限模式选项
}

/**
 * 思考级别配置
 */
export interface ThinkingLevelConfig {
  id: string        // 唯一标识：off, think, ultra, custom_xxx
  name: string      // 显示名称
  tokens: number    // token 数量
  isCustom: boolean // 是否为自定义级别
}

/**
 * 通用选项配置（用于下拉列表）
 */
export interface OptionConfig {
  id: string           // 唯一标识
  label: string        // 显示名称
  description?: string // 描述（可选）
  isDefault?: boolean  // 是否为默认值
}

// ========== 事件处理器类型 ==========

export type ThemeChangeHandler = (theme: IdeTheme) => void
export type SessionCommandHandler = (command: SessionCommand) => void
export type SettingsChangeHandler = (settings: IdeSettings) => void
export type ActiveFileChangeHandler = (activeFile: ActiveFileInfo | null) => void
export type TerminalTaskUpdateHandler = (update: TerminalTaskUpdate) => void
