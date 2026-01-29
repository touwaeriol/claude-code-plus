import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { vscode } from '@/utils/vscodeApi'

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
}

// MCP 设置
export interface McpServer {
  name: string
  enabled: boolean
  backends: string
  level: string
  isBuiltIn: boolean
  configuration?: string
}

export interface McpSettings {
  servers: McpServer[]
}

export const useSettingsStore = defineStore('settings', () => {
  // Claude Code 设置
  const claude = ref<ClaudeSettings>({
    defaultBypassPermissions: false,
    defaultAutoCleanupContexts: true,
    permissionMode: 'default',
    includePartialMessages: true,
    nodePath: '',
    defaultModelId: 'claude-opus-4-5-20251101',
    defaultThinkingLevel: 'HIGH',
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
    sandboxMode: 'auto'
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
    userPrompt: ''
  })

  // MCP 设置
  const mcp = ref<McpSettings>({
    servers: [
      { name: 'JetBrains LSP', enabled: true, backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
      { name: 'JetBrains File', enabled: true, backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
      { name: 'Terminal', enabled: true, backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
      { name: 'Git', enabled: true, backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
      { name: 'User Interaction', enabled: true, backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' }
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

  // 监听设置变化并自动保存
  watch(claude, (newVal) => {
    saveSetting('claude', newVal)
  }, { deep: true })

  watch(codex, (newVal) => {
    saveSetting('codex', newVal)
  }, { deep: true })

  watch(gitGenerate, (newVal) => {
    saveSetting('gitGenerate', newVal)
  }, { deep: true })

  watch(mcp, (newVal) => {
    saveSetting('mcp', newVal)
  }, { deep: true })

  return {
    claude,
    codex,
    gitGenerate,
    mcp,
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
    handleDetectionResult
  }
})
