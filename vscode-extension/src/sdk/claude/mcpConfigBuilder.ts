/**
 * MCP 配置构建器
 * 
 * 负责将 MCP 服务器设置转换为 Claude CLI 可用的配置格式，
 * 并生成 instructions 追加到系统提示词。
 * 
 * 与 JetBrains 版本的 SubprocessTransport.kt 功能对应。
 */

import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import * as crypto from 'crypto'

/**
 * MCP 服务器设置（来自前端 settingsStore）
 */
export interface McpServerSettings {
  name: string
  enabled: boolean
  backends: string  // "All" | "Claude" | "Codex" | "Claude,Codex"
  level: string
  isBuiltIn: boolean
  
  // 连接配置
  type?: 'http' | 'stdio' | 'sse'
  url?: string
  headers?: Record<string, string>
  command?: string
  args?: string[]
  env?: Record<string, string>
  
  // Instructions (System Prompts)
  instructions?: string       // 通用提示词
  instructionsClaude?: string // Claude 特定提示词
  instructionsCodex?: string  // Codex 特定提示词
  defaultInstructions?: string // 默认提示词（只读）
}

/**
 * MCP 配置构建结果
 */
export interface McpConfigResult {
  /** MCP 配置 JSON 临时文件路径，null 表示无需配置 */
  configFilePath: string | null
  
  /** 需要追加到 systemPrompt 的 instructions */
  systemPromptAppendix: string
  
  /** 创建的临时文件列表（用于清理） */
  tempFiles: string[]
}

/**
 * 获取临时文件目录
 */
function getTempDir(): string {
  const tempDir = path.join(os.tmpdir(), 'claude-code-plus')
  if (!fs.existsSync(tempDir)) {
    fs.mkdirSync(tempDir, { recursive: true })
  }
  return tempDir
}

/**
 * 生成唯一的临时文件名
 */
function generateTempFileName(prefix: string, extension: string): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19)
  const uuid = crypto.randomUUID().substring(0, 8)
  return `${prefix}_${timestamp}_${uuid}.${extension}`
}

/**
 * 检查服务器是否对指定后端启用
 */
function isBackendEnabled(backends: string, backend: 'claude' | 'codex'): boolean {
  if (!backends || backends === 'All') {
    return true
  }
  return backends.toLowerCase().includes(backend)
}

/**
 * 构建 MCP 配置 JSON 并写入临时文件
 * 
 * @param servers MCP 服务器配置列表
 * @param backend 当前后端类型
 * @param options 可选配置
 * @param options.connectId 前端连接 ID（用于内置服务器回调）
 * @param options.mcpGatewayPort MCP HTTP Gateway 端口（用于内置服务器）
 * @returns 配置结果，包含临时文件路径和 instructions
 */
export function buildMcpConfig(
  servers: McpServerSettings[],
  backend: 'claude' | 'codex',
  options?: {
    connectId?: string
    mcpGatewayPort?: number
  }
): McpConfigResult {
  const tempFiles: string[] = []
  const { connectId, mcpGatewayPort } = options || {}
  
  // 过滤启用且支持当前后端的服务器
  const enabledServers = servers.filter(s => {
    if (!s.enabled) return false
    return isBackendEnabled(s.backends, backend)
  })

  if (enabledServers.length === 0) {
    return { configFilePath: null, systemPromptAppendix: '', tempFiles }
  }

  // 1. 构建 MCP 服务器 JSON（用于 --mcp-config）
  const mcpServers: Record<string, any> = {}
  
  for (const server of enabledServers) {
    // 内置服务器：通过 MCP HTTP Gateway 暴露
    if (server.isBuiltIn) {
      if (mcpGatewayPort) {
        // 为内置服务器生成 HTTP URL 配置
        const url = `http://127.0.0.1:${mcpGatewayPort}/mcp/${server.name}`
        mcpServers[server.name] = {
          type: 'http',
          url,
          headers: connectId ? { 'x-mcp-connect-id': connectId } : {}
        }
      }
      // 如果没有 mcpGatewayPort，则跳过内置服务器（向后兼容）
      continue
    }
    
    // 外部服务器：使用用户配置
    if (server.type === 'http' && server.url) {
      mcpServers[server.name] = {
        type: 'http',
        url: server.url,
        headers: server.headers ?? {}
      }
    } else if (server.type === 'sse' && server.url) {
      mcpServers[server.name] = {
        type: 'sse',
        url: server.url,
        headers: server.headers ?? {}
      }
    } else if (server.type === 'stdio' && server.command) {
      mcpServers[server.name] = {
        type: 'stdio',
        command: server.command,
        args: server.args ?? [],
        env: server.env ?? {}
      }
    }
  }

  // 2. 写入 MCP 配置临时文件
  let configFilePath: string | null = null
  if (Object.keys(mcpServers).length > 0) {
    const tempDir = getTempDir()
    const fileName = generateTempFileName('claude_mcp_config', 'json')
    configFilePath = path.join(tempDir, fileName)
    
    const configJson = JSON.stringify({ mcpServers }, null, 2)
    fs.writeFileSync(configFilePath, configJson, 'utf8')
    tempFiles.push(configFilePath)
  }

  // 3. 构建 instructions 追加到 systemPrompt
  const instructionsParts: string[] = []
  
  for (const server of enabledServers) {
    // 获取适用于当前后端的 instructions
    let instruction: string | undefined
    
    if (backend === 'claude' && server.instructionsClaude?.trim()) {
      instruction = server.instructionsClaude
    } else if (backend === 'codex' && server.instructionsCodex?.trim()) {
      instruction = server.instructionsCodex
    } else if (server.instructions?.trim()) {
      // 回退到通用 instructions
      instruction = server.instructions
    } else if (server.defaultInstructions?.trim()) {
      // 回退到默认 instructions
      instruction = server.defaultInstructions
    }
    
    if (instruction && instruction.trim()) {
      instructionsParts.push(`## ${server.name} MCP\n\n${instruction.trim()}`)
    }
  }

  const systemPromptAppendix = instructionsParts.length > 0
    ? '\n\n---\n\n# MCP Server Instructions\n\n' + instructionsParts.join('\n\n---\n\n')
    : ''

  return { configFilePath, systemPromptAppendix, tempFiles }
}

/**
 * 将 systemPrompt 追加内容写入临时文件
 * 
 * @param appendix 要追加的内容
 * @returns 临时文件路径，如果内容为空则返回 null
 */
export function writeSystemPromptAppendix(appendix: string): string | null {
  if (!appendix || !appendix.trim()) {
    return null
  }
  
  const tempDir = getTempDir()
  const fileName = generateTempFileName('claude_system_prompt', 'md')
  const filePath = path.join(tempDir, fileName)
  
  fs.writeFileSync(filePath, appendix, 'utf8')
  return filePath
}

/**
 * 清理临时 MCP 配置文件
 * 
 * @param files 要清理的文件路径列表
 */
export function cleanupTempFiles(files: string[]): void {
  for (const filePath of files) {
    if (filePath && fs.existsSync(filePath)) {
      try {
        fs.unlinkSync(filePath)
      } catch (e) {
        // 忽略清理错误
        console.warn(`[MCP] Failed to cleanup temp file: ${filePath}`, e)
      }
    }
  }
}

/**
 * 清理过期的临时文件
 * 清理超过 1 小时的临时文件
 */
export function cleanupExpiredTempFiles(): void {
  const tempDir = path.join(os.tmpdir(), 'claude-code-plus')
  if (!fs.existsSync(tempDir)) {
    return
  }
  
  const maxAge = 60 * 60 * 1000  // 1 小时
  const now = Date.now()
  
  try {
    const files = fs.readdirSync(tempDir)
    for (const file of files) {
      if (file.startsWith('claude_mcp_config_') || file.startsWith('claude_system_prompt_')) {
        const filePath = path.join(tempDir, file)
        const stat = fs.statSync(filePath)
        if (now - stat.mtimeMs > maxAge) {
          fs.unlinkSync(filePath)
        }
      }
    }
  } catch (e) {
    // 忽略清理错误
  }
}
