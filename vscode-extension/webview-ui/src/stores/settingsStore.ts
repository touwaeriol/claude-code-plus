import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { vscode } from '@/utils/vscodeApi'

// MCP 默认提示词 (来自 JetBrains 插件)
export const McpDefaultInstructions = {
  USER_INTERACTION: `When you need clarification from the user, especially when presenting multiple options or choices, use the MCP server \`user_interaction\` tool \`AskUserQuestion\` to ask questions.
Tool identifiers may differ across providers. Do not assume a fixed prefix or delimiter; select the tool that matches this server + tool pair.
The user's response will be returned to you through the same tool.`,

  JETBRAINS_LSP: `### When to Use

CRITICAL: For code search and file discovery, prefer JetBrains MCP tools over any built-in search tools:
- ALWAYS use \`CodeSearch\` instead of built-in grep/search tools
- ALWAYS use \`FileIndex\` instead of built-in glob/find tools
- Only fall back to built-in tools if JetBrains tools return errors

IMPORTANT: After completing code modifications, you MUST use \`FileProblems\` to validate for syntax errors.

### Refactoring Workflow

When renaming symbols:
1. \`FindUsages\` or \`CodeSearch\` → get line number
2. \`Rename(line=N, newName="...")\` → safe rename across project
3. \`FileProblems\` → validate changes

**Note**: \`Rename\` requires \`line\` parameter. Use \`Rename\` for symbols; use Edit tool for other text changes.

### Reading Library Source Code

To read dependencies (JAR files, JDK sources, decompiled .class):
1. \`FileIndex(query="ClassName", searchType="Classes", scope="All")\`
2. \`ReadFile(filePath="<path from FileIndex>")\`

**Key**: Use \`scope="All"\` to include libraries, not just project files.`,

  JETBRAINS_FILE: `### When to Use

Use for file operations with relative path support (to project root).

**Note**: For reading library source code (JAR files, decompiled .class), use \`jetbrains / ReadFile\` from JetBrains LSP MCP instead.`,

  CONTEXT7: `### When to Use

IMPORTANT: When working with third-party libraries, ALWAYS query Context7 first to get up-to-date documentation and prevent hallucinated APIs.

### Workflow

1. \`resolve-library-id\` → get Context7 ID (unless user provides \`/org/project\` format)
2. \`get-library-docs\` → fetch documentation`,

  TERMINAL: `### When to Use

Use IDEA's integrated terminal instead of built-in Bash tool for command execution.

### Best Practices

- **Reuse sessions**: Always reuse existing sessions via \`session_id\`
- **Multiple terminals**: Only create multiple sessions for concurrent commands (e.g., dev server + tests)
- **Cleanup**: Close sessions with \`TerminalKill\` when no longer needed`,

  GIT: `### Git Commit Policy

**IMPORTANT**: Do NOT use terminal commands (git commit, git add, git push, etc.) for version control operations.
You MUST use jetbrains_git MCP tools instead.

### Commit Workflow

1. \`GetVcsChanges()\` → Get list of changes
2. Analyze changes, use \`SelectFiles\` / \`DeselectFiles\` to adjust file selection
3. \`SetCommitMessage()\` → Generate and fill commit message
4. **MUST** use \`AskUserQuestion\` to ask user for confirmation
5. After user confirms, call \`CommitChanges()\` to execute

### When to Use

Use for interacting with IDEA's VCS/Git integration: reading changes, setting commit messages, checking status.

### File Selection Tools

- \`SelectFiles(paths, mode)\` → Select files in Commit panel (mode: "replace" or "add")
- \`DeselectFiles(paths)\` → Deselect files from Commit panel
- \`SelectAllFiles()\` → Select all changed files
- \`DeselectAllFiles()\` → Deselect all files

### Commit Message Conventions (Conventional Commits)

Follow the Conventional Commits format:

\`\`\`
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
\`\`\`

**Types**:
- \`feat\`: A new feature
- \`fix\`: A bug fix
- \`docs\`: Documentation only changes
- \`style\`: Code style changes (formatting, missing semi colons, etc)
- \`refactor\`: Code refactoring without feature change or bug fix
- \`perf\`: Performance improvements
- \`test\`: Adding or modifying tests
- \`chore\`: Build process, auxiliary tool changes, etc
- \`ci\`: CI configuration changes
- \`build\`: Build system or external dependency changes

**Examples**:
- \`feat(auth): add OAuth2 login support\`
- \`fix(api): resolve null pointer exception in user endpoint\`
- \`docs: update README with installation instructions\`
- \`refactor(core): simplify data processing logic\`

### Notes

- Always wait for user review before committing
- Use \`push=true\` in \`CommitChanges\` to commit and push in one step`
}

// 获取 MCP 服务器的默认提示词
export function getDefaultInstructions(serverName: string): string {
  switch (serverName) {
    case 'User Interaction':
      return McpDefaultInstructions.USER_INTERACTION
    case 'JetBrains LSP':
      return McpDefaultInstructions.JETBRAINS_LSP
    case 'JetBrains File':
      return McpDefaultInstructions.JETBRAINS_FILE
    case 'Context7':
      return McpDefaultInstructions.CONTEXT7
    case 'Terminal':
      return McpDefaultInstructions.TERMINAL
    case 'Git':
      return McpDefaultInstructions.GIT
    default:
      return ''
  }
}

// 检测信息
export interface DetectedInfo {
  path: string
  version?: string
}

// Claude Code 设置
export interface ClaudeSettings {
  // Default Permissions
  defaultBypassPermissions: boolean
  defaultAutoCleanupContexts: boolean
  permissionMode: string
  includePartialMessages: boolean
  // Runtime Settings
  nodePath: string
  defaultModelId: string
  // Thinking Configuration
  defaultThinkingLevel: string
  thinkTokens: number
  ultraTokens: number
  // Custom Models
  customModels: Array<{ displayName: string; modelId: string }>
}

// Codex 设置
export interface CodexSettings {
  // Default Permissions
  defaultBypassPermissions: boolean
  defaultAutoCleanupContexts: boolean
  // Runtime Settings
  codexPath: string
  webSearch: boolean
  // Model Settings
  defaultModelId: string
  // Custom Models
  customModels: Array<{ displayName: string; modelId: string }>
  // Session Defaults
  reasoningEffort: string
  reasoningSummary: string
  sandboxMode: string
}

// Git Generate 设置
export interface GitGenerateSettings {
  enabled: boolean
  backend: string
  modelId: string
  claudeThinkingLevel: string
  codexReasoningEffort: string
  saveSession: boolean
  systemPrompt: string
  userPrompt: string
  tools?: string[]  // 允许的工具列表（空数组表示使用默认）
}

// MCP 设置
export interface McpServer {
  name: string
  enabled: boolean
  backends: string  // 对应 JetBrains enabledBackends，格式: "All" | "Claude" | "Codex" | "Claude,Codex"
  level: string     // 对应 JetBrains McpServerLevel: "BUILTIN" | "GLOBAL" | "PROJECT"
  isBuiltIn: boolean
  configuration?: string   // 对应 JetBrains configSummary
  jsonConfig?: string      // JSON 配置（自定义 MCP 使用）
  // Instructions (System Prompts)
  instructions?: string       // 通用提示词（非 Claude/Codex 特定）
  instructionsClaude?: string // Claude 特定提示词
  instructionsCodex?: string  // Codex 特定提示词
  // Default Instructions (只读，内置 MCP 使用)
  defaultInstructions?: string
  // Tool Timeout
  toolTimeoutSec?: number
  // Disable Tools Toggle
  hasDisableToolsToggle?: boolean  // 是否有禁用工具开关（如 Terminal 的 Bash）
  // Disabled Tools (for Claude Code)
  disabledTools?: string[]
  // Default Disabled Tools (只读，内置 MCP 使用)
  defaultDisabledTools?: string[]
  // Codex Disabled Features
  codexDisabledFeatures?: string[]
  // Default Codex Disabled Features (只读，内置 MCP 使用)
  defaultCodexDisabledFeatures?: string[]
  // Codex Auto-Approved Tools
  codexAutoApprovedTools?: string[]
  // Default Auto-Approved Tools (只读，内置 MCP 使用)
  defaultAutoApprovedTools?: string[]
  // Context7 specific
  apiKey?: string
  // Terminal MCP specific
  terminalMaxOutputLines?: number
  terminalMaxOutputChars?: number
  terminalReadTimeout?: number
  terminalDefaultShell?: string
  terminalAvailableShells?: string
  terminalDisableInteractive?: boolean  // 禁用交互式终端
  // Git MCP specific
  gitCommitLanguage?: string
  // File MCP specific
  fileAllowExternal?: boolean
  fileExternalRules?: string
}

export interface McpSettings {
  servers: McpServer[]
}

export const useSettingsStore = defineStore('settings', () => {
  // 标记是否正在加载设置（防止 watch 在初始化时触发保存）
  const isLoading = ref(true)
  
  // Claude Code 设置
  const claude = ref<ClaudeSettings>({
    defaultBypassPermissions: false,
    defaultAutoCleanupContexts: true,
    permissionMode: 'default',
    includePartialMessages: true,
    nodePath: '',
    defaultModelId: 'claude-opus-4-6',
    defaultThinkingLevel: 'ultra',
    thinkTokens: 2048,
    ultraTokens: 8096,
    customModels: []
  })

  // Codex 设置
  const codex = ref<CodexSettings>({
    defaultBypassPermissions: false,
    defaultAutoCleanupContexts: true,
    codexPath: '',
    webSearch: false,
    defaultModelId: 'gpt-5.2-codex',
    customModels: [],
    reasoningEffort: 'medium',
    reasoningSummary: 'auto',
    sandboxMode: 'workspace-write'
  })

  // Git Generate 设置
  const gitGenerate = ref<GitGenerateSettings>({
    enabled: false,
    backend: 'claude',
    modelId: '',
    claudeThinkingLevel: 'ultra',
    codexReasoningEffort: 'xhigh',
    saveSession: false,
    systemPrompt: '',
    userPrompt: '',
    tools: []  // 空数组表示使用默认工具列表
  })

  // MCP 设置
  const mcp = ref<McpSettings>({
    servers: [
      { 
        name: 'User Interaction', 
        enabled: true, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in', 
        toolTimeoutSec: 3600,
        defaultAutoApprovedTools: ['AskUserQuestion']
      },
      { 
        name: 'JetBrains LSP', 
        enabled: true, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in', 
        toolTimeoutSec: 60, 
        // JetBrains LSP 固定禁用 Glob 和 Grep (因为使用 IDE 索引)
        disabledTools: ['Glob', 'Grep'],
        defaultAutoApprovedTools: ['DirectoryTree', 'FileProblems', 'FileIndex', 'CodeSearch', 'FindUsages', 'Rename']
      },
      { 
        name: 'JetBrains File', 
        enabled: true, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in', 
        toolTimeoutSec: 60, 
        hasDisableToolsToggle: true,
        defaultDisabledTools: ['Read', 'Write', 'Edit'],
        defaultCodexDisabledFeatures: ['apply_patch_freeform'],
        defaultAutoApprovedTools: ['ReadFile'],
        fileAllowExternal: true, 
        fileExternalRules: '[]' 
      },
      { 
        name: 'Context7', 
        enabled: false, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in', 
        toolTimeoutSec: 60, 
        apiKey: '' 
      },
      { 
        name: 'Terminal', 
        enabled: false, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in', 
        toolTimeoutSec: 60, 
        hasDisableToolsToggle: true,
        defaultDisabledTools: ['Bash'],
        defaultCodexDisabledFeatures: ['shell_tool'],
        defaultAutoApprovedTools: ['TerminalRead', 'TerminalList', 'TerminalKill', 'TerminalTypes', 'TerminalRename', 'TerminalInterrupt'],
        terminalMaxOutputLines: 500, 
        terminalMaxOutputChars: 50000, 
        terminalReadTimeout: 10,  // 与 JetBrains 版本一致
        terminalDefaultShell: '', 
        terminalAvailableShells: '',
        terminalDisableInteractive: false  // 禁用交互式终端 
      },
      { 
        name: 'Git', 
        enabled: false, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in', 
        toolTimeoutSec: 60, 
        defaultAutoApprovedTools: ['GetVcsChanges', 'GetCommitMessage', 'SetCommitMessage', 'GetVcsStatus', 'SelectFiles', 'DeselectFiles', 'SelectAllFiles', 'DeselectAllFiles'],
        gitCommitLanguage: 'en' 
      }
    ]
  })

  // 检测状态
  const detectingNode = ref(false)
  const detectingCodex = ref(false)
  const detectedNode = ref<DetectedInfo | null>(null)
  const detectedCodex = ref<DetectedInfo | null>(null)

  // 加载设置
  const loadSettings = () => {
    vscode.postMessage({ type: 'getSettings' })
    // 请求检测可执行文件
    detectExecutables()
  }

  // 检测可执行文件
  const detectExecutables = () => {
    detectingNode.value = true
    detectingCodex.value = true
    vscode.postMessage({ type: 'detectNode' })
    vscode.postMessage({ type: 'detectCodex' })
  }

  // 保存单个设置
  const saveSetting = (key: string, value: any) => {
    vscode.postMessage({ type: 'saveSetting', payload: { key, value } })
  }

  // 浏览文件
  const browseFile = (settingKey: string) => {
    vscode.postMessage({ type: 'browseFile', payload: { settingKey } })
  }

  // 处理检测结果
  const handleDetectionResult = (type: string, info: DetectedInfo | null) => {
    if (type === 'node') {
      detectingNode.value = false
      detectedNode.value = info
    } else if (type === 'codex') {
      detectingCodex.value = false
      detectedCodex.value = info
    }
  }

  // 监听设置变化并自动保存（只在非加载状态时触发）
  watch(claude, (newVal) => {
    if (!isLoading.value) {
      saveSetting('claude', newVal)
    }
  }, { deep: true })

  watch(codex, (newVal) => {
    if (!isLoading.value) {
      saveSetting('codex', newVal)
    }
  }, { deep: true })

  watch(gitGenerate, (newVal) => {
    if (!isLoading.value) {
      saveSetting('gitGenerate', newVal)
    }
  }, { deep: true })

  watch(mcp, (newVal) => {
    if (!isLoading.value) {
      saveSetting('mcp', newVal)
    }
  }, { deep: true })

  // 设置加载完成后的回调
  const onSettingsLoaded = () => {
    // 短暂延迟后启用保存，避免初始化赋值触发保存
    setTimeout(() => {
      isLoading.value = false
    }, 100)
  }

  return {
    claude,
    codex,
    gitGenerate,
    mcp,
    // 加载状态
    isLoading,
    // 检测状态
    detectingNode,
    detectingCodex,
    detectedNode,
    detectedCodex,
    // 方法
    loadSettings,
    detectExecutables,
    saveSetting,
    browseFile,
    handleDetectionResult,
    onSettingsLoaded
  }
})
