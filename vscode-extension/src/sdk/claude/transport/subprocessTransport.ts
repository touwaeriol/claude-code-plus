/**
 * Subprocess transport implementation for Claude CLI communication.
 *
 * This module provides a subprocess-based transport that:
 * - Launches the official Claude CLI (claude-cli.mjs)
 * - Communicates via stdin/stdout using JSON-RPC protocol
 * - Handles process lifecycle and error recovery
 *
 * Translated from Kotlin SDK: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/transport/SubprocessTransport.kt
 */

import { spawn, ChildProcessWithoutNullStreams } from 'child_process'
import * as readline from 'readline'
import * as path from 'path'
import * as fs from 'fs'
import * as os from 'os'
import * as crypto from 'crypto'
import {
  Transport,
  TransportEvents,
  TransportError,
  CLINotFoundError,
  CLIConnectionError,
  ProcessError,
  JSONDecodeError,
  NodeNotFoundError,
  type JsonMessage,
} from './transport'

/**
 * Permission mode for tool execution.
 */
export type PermissionMode = 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions'

/**
 * MCP server configuration types.
 */
export interface McpStdioServerConfig {
  type: 'stdio'
  command: string
  args: string[]
  env?: Record<string, string>
}

export interface McpSSEServerConfig {
  type: 'sse'
  url: string
  headers?: Record<string, string>
}

export interface McpHttpServerConfig {
  type: 'http'
  url: string
  headers?: Record<string, string>
}

export type McpServerConfig = McpStdioServerConfig | McpSSEServerConfig | McpHttpServerConfig

/**
 * Agent definition for programmatic subagents.
 */
export interface AgentDefinition {
  description: string
  prompt: string
  tools?: string[]
  model?: string
}

/**
 * System prompt preset configuration.
 */
export interface SystemPromptPreset {
  type?: 'preset'
  preset: string
  append?: string
}

/**
 * Options for configuring the Claude Agent transport.
 * This is a simplified version focusing on transport-related options.
 */
export interface ClaudeAgentOptions {
  /** Working directory for the CLI */
  cwd?: string
  /** Environment variables */
  env?: Record<string, string>
  /** Custom CLI path */
  cliPath?: string
  /** Custom Node.js path */
  nodePath?: string
  /** Model to use */
  model?: string
  /** System prompt (string or preset) */
  systemPrompt?: string | SystemPromptPreset
  /** Append system prompt content */
  appendSystemPromptFile?: string
  /** Allowed tools */
  allowedTools?: string[]
  /** Disallowed tools */
  disallowedTools?: string[]
  /** Programmatic subagents */
  agents?: Record<string, AgentDefinition>
  /** Permission mode */
  permissionMode?: PermissionMode
  /** Skip permissions entirely (dangerous!) */
  dangerouslySkipPermissions?: boolean
  /** Allow dangerous skip permissions flag */
  allowDangerouslySkipPermissions?: boolean
  /** Enable permission prompt tool */
  permissionPromptToolName?: string
  /** Permission callback */
  canUseTool?: (toolName: string, input: unknown) => Promise<boolean>
  /** Continue previous conversation */
  continueConversation?: boolean
  /** Resume specific session */
  resume?: string
  /** Replay user messages when resuming */
  replayUserMessages?: boolean
  /** Disable session persistence */
  noSessionPersistence?: boolean
  /** Maximum turns */
  maxTurns?: number
  /** Additional directories */
  addDirs?: string[]
  /** Settings file path */
  settings?: string
  /** Maximum thinking tokens (0 to disable) */
  maxThinkingTokens?: number
  /** MCP servers configuration */
  mcpServers?: Record<string, McpServerConfig>
  /** Chrome integration (true = enable, false = disable, undefined = default) */
  chromeEnabled?: boolean
  /** Include partial messages */
  includePartialMessages?: boolean
  /** Verbose output */
  verbose?: boolean
  /** Print mode (non-interactive) */
  print?: boolean
  /** Extra CLI arguments */
  extraArgs?: Record<string, string | undefined>
}

/**
 * Default options for Claude Agent.
 */
const DEFAULT_OPTIONS: Partial<ClaudeAgentOptions> = {
  env: {},
  allowedTools: [],
  disallowedTools: [],
  addDirs: [],
  maxThinkingTokens: 0,
  mcpServers: {},
  includePartialMessages: false,
  verbose: false,
  print: true,
  extraArgs: {},
}

/**
 * Cache for system prompt files.
 * Key = content MD5 hash, value = temp file path
 */
const systemPromptFileCache = new Map<string, string>()

/**
 * Calculate MD5 hash of a string.
 */
function md5(content: string): string {
  return crypto.createHash('md5').update(content).digest('hex')
}

/**
 * Get or create a cached system prompt file.
 */
function getOrCreateSystemPromptFile(content: string, logger?: (msg: string) => void): string {
  const digest = md5(content)

  // Check cache
  const cached = systemPromptFileCache.get(digest)
  if (cached && fs.existsSync(cached)) {
    logger?.(`[SubprocessTransport] Using cached system prompt file: ${cached}`)
    return cached
  }

  // Create new file
  const tempDir = path.join(os.tmpdir(), 'claude-agent-sdk', 'system-prompts')
  fs.mkdirSync(tempDir, { recursive: true })

  const tempFile = path.join(tempDir, `prompt-${digest}.md`)
  fs.writeFileSync(tempFile, content, 'utf8')

  // Cache it
  systemPromptFileCache.set(digest, tempFile)
  logger?.(`[SubprocessTransport] Created system prompt file: ${tempFile}`)

  return tempFile
}

/**
 * Transport implementation using subprocess for Claude CLI communication.
 */
export class SubprocessTransport implements Transport {
  private process: ChildProcessWithoutNullStreams | null = null
  private stdoutRL: readline.Interface | null = null
  private stderrRL: readline.Interface | null = null
  private isConnectedFlag = false
  private readonly options: Required<
    Pick<ClaudeAgentOptions, 'env' | 'allowedTools' | 'disallowedTools' | 'addDirs' | 'maxThinkingTokens' | 'mcpServers' | 'includePartialMessages' | 'verbose' | 'print' | 'extraArgs'>
  > &
    ClaudeAgentOptions
  private readonly streamingMode: boolean
  private events: TransportEvents = {}
  private tempFiles: string[] = []

  /** Logger function */
  private readonly log?: (message: string) => void

  constructor(
    options: ClaudeAgentOptions,
    streamingMode = true,
    logger?: (message: string) => void
  ) {
    this.options = { ...DEFAULT_OPTIONS, ...options } as typeof this.options
    this.streamingMode = streamingMode
    this.log = logger
  }

  async connect(): Promise<void> {
    const command = this.buildCommand()
    const commandString = command.join(' ')
    this.log?.(`[SubprocessTransport] Building command: ${commandString}`)

    try {
      const cwd = this.options.cwd || process.cwd()

      // Merge environment variables
      const env: Record<string, string> = {
        ...process.env as Record<string, string>,
        ...this.options.env,
        CLAUDE_CODE_ENTRYPOINT: 'vscode-extension',
      }

      this.log?.(`[SubprocessTransport] Starting Claude CLI process...`)

      // Spawn the process
      this.process = spawn(command[0], command.slice(1), {
        cwd,
        env,
        shell: process.platform === 'win32', // Use shell on Windows for path resolution
        windowsHide: true,
        stdio: ['pipe', 'pipe', 'pipe'],
      })

      // Wait a bit and check if process started
      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => {
          if (this.process && !this.process.killed) {
            resolve()
          } else {
            reject(new CLIConnectionError('Process failed to start within timeout'))
          }
        }, 100)

        this.process!.once('spawn', () => {
          clearTimeout(timeout)
          resolve()
        })

        this.process!.once('error', (err) => {
          clearTimeout(timeout)
          reject(new CLIConnectionError(`Failed to spawn process: ${err.message}`, err))
        })
      })

      // Check if process exited immediately
      if (!this.process.pid || this.process.exitCode !== null) {
        const exitCode = this.process.exitCode ?? -1
        const stderr = await this.readStderr()
        this.log?.(`[SubprocessTransport] Process exited immediately with code ${exitCode}`)
        throw new CLIConnectionError(
          `Claude CLI process exited immediately with code ${exitCode}. Command: ${commandString}. stderr: ${stderr}`
        )
      }

      this.log?.(`[SubprocessTransport] Process started with PID: ${this.process.pid}`)

      // Setup stdout line reader
      this.stdoutRL = readline.createInterface({
        input: this.process.stdout,
        crlfDelay: Infinity,
      })

      // Setup stderr line reader for logging
      this.stderrRL = readline.createInterface({
        input: this.process.stderr,
        crlfDelay: Infinity,
      })
      this.stderrRL.on('line', (line) => {
        const trimmed = line.trim()
        if (trimmed) {
          this.log?.(`[claude:stderr] ${trimmed}`)
        }
      })

      // Handle process exit
      this.process.on('exit', (code, signal) => {
        this.log?.(`[SubprocessTransport] Process exited: code=${code}, signal=${signal}`)
        this.isConnectedFlag = false
        this.events.onClose?.(code ?? undefined, signal ?? undefined)
      })

      this.process.on('error', (err) => {
        this.log?.(`[SubprocessTransport] Process error: ${err.message}`)
        this.events.onError?.(err)
      })

      this.isConnectedFlag = true
      this.log?.(`[SubprocessTransport] Connection established!`)
    } catch (err) {
      this.log?.(`[SubprocessTransport] Connection failed: ${err}`)
      if (err instanceof Error) {
        // Check if it's a "not found" error
        if (
          err.message.includes('ENOENT') ||
          err.message.includes('not found') ||
          err.message.includes('No such file')
        ) {
          throw CLINotFoundError.withInstallInstructions(await this.isNodeInstalled())
        }
        throw new CLIConnectionError(`Failed to start Claude CLI: ${err.message}`, err)
      }
      throw new CLIConnectionError(`Failed to start Claude CLI: ${String(err)}`)
    }
  }

  async write(data: string): Promise<void> {
    if (!this.process || !this.process.stdin.writable) {
      throw new TransportError('Transport not connected')
    }

    return new Promise((resolve, reject) => {
      this.log?.(`[SubprocessTransport] Writing to CLI: ${data}`)
      this.process!.stdin.write(data + '\n', 'utf8', (err) => {
        if (err) {
          this.log?.(`[SubprocessTransport] Write failed: ${err.message}`)
          reject(new TransportError(`Failed to write to CLI stdin: ${err.message}`, err))
        } else {
          this.log?.(`[SubprocessTransport] Write successful`)
          resolve()
        }
      })
    })
  }

  async *readMessages(): AsyncIterable<JsonMessage> {
    if (!this.stdoutRL) {
      throw new TransportError('Transport not connected')
    }

    let jsonBuffer = ''
    let braceCount = 0
    let inString = false
    let escapeNext = false

    for await (const line of this.stdoutRL) {
      this.log?.(`[SubprocessTransport] Raw line from CLI: ${line}`)
      jsonBuffer += line

      // Parse character by character to detect complete JSON objects
      for (const char of line) {
        if (escapeNext) {
          escapeNext = false
          continue
        }
        if (char === '\\' && inString) {
          escapeNext = true
          continue
        }
        if (char === '"' && !escapeNext) {
          inString = !inString
          continue
        }
        if (!inString) {
          if (char === '{') braceCount++
          if (char === '}') braceCount--
        }
      }

      // If we have a complete JSON object
      if (braceCount === 0 && jsonBuffer.length > 0) {
        try {
          const parsed = JSON.parse(jsonBuffer)
          this.log?.(`[SubprocessTransport] Complete JSON received: ${jsonBuffer}`)
          yield parsed
          this.events.onMessage?.(parsed)
        } catch (err) {
          this.log?.(`[SubprocessTransport] JSON parse failed: ${jsonBuffer}`)
          const error = new JSONDecodeError(
            'Failed to decode JSON from CLI output',
            jsonBuffer,
            err instanceof Error ? err : undefined
          )
          this.events.onError?.(error)
          throw error
        }
        jsonBuffer = ''
      }
    }

    // Handle process completion
    if (this.process && !this.process.killed && this.process.exitCode !== null) {
      const exitCode = this.process.exitCode
      if (exitCode !== 0) {
        const stderr = await this.readStderr()
        this.log?.(`[SubprocessTransport] Process failed with exit code ${exitCode}`)
        throw new ProcessError(
          `Command failed with exit code ${exitCode}`,
          exitCode,
          stderr
        )
      }
    }
  }

  setEventHandlers(events: TransportEvents): void {
    this.events = events
  }

  isReady(): boolean {
    return this.isConnectedFlag && this.process !== null && !this.process.killed
  }

  async endInput(): Promise<void> {
    if (this.process?.stdin) {
      return new Promise((resolve, reject) => {
        this.process!.stdin.end((err: Error | null | undefined) => {
          if (err) {
            reject(new TransportError(`Failed to close CLI stdin: ${err.message}`, err))
          } else {
            resolve()
          }
        })
      })
    }
  }

  async close(): Promise<void> {
    try {
      // Close readline interfaces
      this.stdoutRL?.close()
      this.stderrRL?.close()
      this.stdoutRL = null
      this.stderrRL = null

      // Terminate process
      if (this.process && !this.process.killed) {
        // Try graceful shutdown first
        this.process.stdin?.end()

        // Wait for process to exit or force kill after timeout
        await Promise.race([
          new Promise<void>((resolve) => {
            this.process!.once('exit', () => resolve())
          }),
          new Promise<void>((resolve) => {
            setTimeout(() => {
              if (this.process && !this.process.killed) {
                this.process.kill('SIGKILL')
              }
              resolve()
            }, 5000)
          }),
        ])
      }

      this.process = null
      this.isConnectedFlag = false

      // Clean up temp files
      for (const tempFile of this.tempFiles) {
        try {
          if (fs.existsSync(tempFile)) {
            fs.unlinkSync(tempFile)
            this.log?.(`[SubprocessTransport] Cleaned up temp file: ${tempFile}`)
          }
        } catch (err) {
          this.log?.(`[SubprocessTransport] Failed to clean up temp file: ${tempFile}`)
        }
      }
      this.tempFiles = []
    } catch (err) {
      throw new TransportError(
        `Failed to close transport: ${err instanceof Error ? err.message : String(err)}`,
        err instanceof Error ? err : undefined
      )
    }
  }

  isConnected(): boolean {
    return this.isConnectedFlag && this.process !== null && !this.process.killed
  }

  /**
   * Build the Claude CLI command with appropriate arguments.
   */
  private buildCommand(): string[] {
    const command: string[] = []
    const isWindows = process.platform === 'win32'

    // Base command - find claude executable
    command.push(...this.findClaudeExecutable())

    // Verbose output - must be before --print
    // Note: --output-format=stream-json requires --verbose
    const outputFormat = this.options.extraArgs?.['output-format'] ?? 'stream-json'
    const needsVerbose = this.options.verbose || outputFormat === 'stream-json'
    if (needsVerbose) {
      command.push('--verbose')
    }

    // Output format
    command.push('--output-format', outputFormat)

    // Print flag - non-interactive mode
    if (this.options.print || outputFormat === 'stream-json' || this.streamingMode) {
      command.push('--print')
    }

    // Include partial messages
    if (this.options.includePartialMessages) {
      command.push('--include-partial-messages')
    }

    // Input format for streaming mode
    if (this.streamingMode) {
      command.push('--input-format', 'stream-json')
    } else {
      command.push('--')
    }

    // Model selection
    if (this.options.model) {
      command.push('--model', this.options.model)
    }

    // System prompt
    if (this.options.systemPrompt) {
      if (typeof this.options.systemPrompt === 'string') {
        const tempFile = getOrCreateSystemPromptFile(this.options.systemPrompt, this.log)
        command.push('--system-prompt-file', tempFile)
      } else {
        const preset = this.options.systemPrompt
        if (preset.preset === 'claude_code') {
          // For claude_code preset, use default system prompt
          if (preset.append) {
            const tempFile = getOrCreateSystemPromptFile(preset.append, this.log)
            command.push('--append-system-prompt-file', tempFile)
          }
        } else {
          command.push('--system-prompt', preset.preset)
        }
      }
    }

    // Append system prompt file
    if (this.options.appendSystemPromptFile) {
      const tempFile = getOrCreateSystemPromptFile(this.options.appendSystemPromptFile, this.log)
      command.push('--append-system-prompt-file', tempFile)
    }

    // Allowed tools
    if (this.options.allowedTools && this.options.allowedTools.length > 0) {
      const toolsArg = this.options.allowedTools.join(',')
      command.push('--allowed-tools', isWindows ? `"${toolsArg}"` : toolsArg)
    }

    // Disallowed tools
    if (this.options.disallowedTools && this.options.disallowedTools.length > 0) {
      const toolsArg = this.options.disallowedTools.join(',')
      command.push('--disallowed-tools', isWindows ? `"${toolsArg}"` : toolsArg)
    }

    // Agents (programmatic subagents)
    if (this.options.agents && Object.keys(this.options.agents).length > 0) {
      const agentsJson: Record<string, unknown> = {}
      for (const [name, def] of Object.entries(this.options.agents)) {
        agentsJson[name] = {
          description: def.description,
          prompt: def.prompt,
          ...(def.tools && { tools: def.tools }),
          ...(def.model && { model: def.model }),
        }
      }
      const jsonStr = JSON.stringify(agentsJson)
      const escapedJson = isWindows
        ? `"${jsonStr.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`
        : jsonStr
      command.push('--agents', escapedJson)
    }

    // Permission mode
    if (this.options.permissionMode) {
      command.push('--permission-mode', this.options.permissionMode)
    }

    // Dangerous skip permissions
    if (this.options.dangerouslySkipPermissions) {
      command.push('--dangerously-skip-permissions')
    }

    // Allow dangerously skip permissions
    if (this.options.allowDangerouslySkipPermissions) {
      command.push('--allow-dangerously-skip-permissions')
    }

    // Permission prompt tool
    const effectivePermissionPromptTool =
      this.options.permissionPromptToolName ??
      (this.options.canUseTool ? 'stdio' : undefined)
    if (effectivePermissionPromptTool) {
      command.push('--permission-prompt-tool', effectivePermissionPromptTool)
    }

    // Continue conversation
    if (this.options.continueConversation) {
      command.push('--continue')
    }

    // Resume session
    if (this.options.resume) {
      command.push('--resume', this.options.resume)
    }

    // Replay user messages
    if (this.options.replayUserMessages) {
      command.push('--replay-user-messages')
    }

    // No session persistence
    if (this.options.noSessionPersistence) {
      command.push('--no-session-persistence')
    }

    // Max turns
    if (this.options.maxTurns !== undefined) {
      command.push('--max-turns', String(this.options.maxTurns))
    }

    // Additional directories
    if (this.options.addDirs) {
      for (const dir of this.options.addDirs) {
        command.push('--add-dir', dir)
      }
    }

    // Settings file
    if (this.options.settings) {
      command.push('--settings', this.options.settings)
    }

    // Max thinking tokens
    command.push('--max-thinking-tokens', String(Math.max(0, this.options.maxThinkingTokens)))

    // MCP servers configuration
    if (this.options.mcpServers && Object.keys(this.options.mcpServers).length > 0) {
      const mcpConfig: Record<string, unknown> = { mcpServers: {} }
      const servers = mcpConfig.mcpServers as Record<string, unknown>

      for (const [name, config] of Object.entries(this.options.mcpServers)) {
        if (config.type === 'stdio') {
          servers[name] = {
            type: 'stdio',
            command: config.command,
            args: config.args,
            env: config.env ?? {},
          }
        } else if (config.type === 'sse') {
          servers[name] = {
            type: 'sse',
            url: config.url,
            headers: config.headers ?? {},
          }
        } else if (config.type === 'http') {
          servers[name] = {
            type: 'http',
            url: config.url,
            headers: config.headers ?? {},
          }
        }
      }

      // Create temp file for MCP config
      const tempDir = path.join(os.tmpdir(), 'claude-code-plus')
      fs.mkdirSync(tempDir, { recursive: true })
      const timestamp = new Date().toISOString().replace(/[:.]/g, '_').slice(0, 13)
      const uuid = crypto.randomUUID().slice(0, 8)
      const tempFile = path.join(tempDir, `claude_mcp_config_${timestamp}_${uuid}.json`)
      fs.writeFileSync(tempFile, JSON.stringify(mcpConfig), 'utf8')
      this.tempFiles.push(tempFile)

      command.push('--mcp-config', tempFile)
      this.log?.(`[SubprocessTransport] MCP config file: ${tempFile}`)
    }

    // Chrome integration
    if (this.options.chromeEnabled === true) {
      command.push('--chrome')
    } else if (this.options.chromeEnabled === false) {
      command.push('--no-chrome')
    }

    // Extra arguments
    const processedKeys = new Set(['output-format', 'print'])
    for (const [key, value] of Object.entries(this.options.extraArgs ?? {})) {
      if (!processedKeys.has(key)) {
        command.push(`--${key}`)
        if (value !== undefined) {
          command.push(value)
        }
      }
    }

    this.log?.(`[SubprocessTransport] Full command: ${command.join(' ')}`)
    return command
  }

  /**
   * Find the Claude executable.
   * Priority:
   * 1. User-specified path (options.cliPath)
   * 2. SDK bundled CLI (resources/bundled/claude-cli.mjs)
   */
  private findClaudeExecutable(): string[] {
    // 1. User-specified path
    if (this.options.cliPath) {
      this.log?.(`[SubprocessTransport] Using user-specified CLI: ${this.options.cliPath}`)
      return [this.options.cliPath]
    }

    // 2. SDK bundled CLI
    const bundledCli = this.findBundledCliJs()
    if (bundledCli) {
      const nodeCommand = this.findNodeExecutable()
      this.log?.(`[SubprocessTransport] Using bundled CLI: ${nodeCommand} ${bundledCli}`)
      return [nodeCommand, bundledCli]
    }

    throw new CLINotFoundError(
      'Could not find SDK bundled Claude CLI. Please ensure:\n' +
        '1. The extension is properly installed\n' +
        '2. claude-cli.mjs exists in resources/bundled/'
    )
  }

  /**
   * Find the bundled CLI JavaScript file.
   */
  private findBundledCliJs(): string | null {
    try {
      // Try to find the CLI in the extension's resources folder
      // This path will vary depending on how the extension is packaged
      const possiblePaths = [
        // Development path
        path.join(__dirname, '../../../../resources/bundled/claude-cli.mjs'),
        // Packaged extension path
        path.join(__dirname, '../../../resources/bundled/claude-cli.mjs'),
        // Alternative paths
        path.join(process.cwd(), 'resources/bundled/claude-cli.mjs'),
      ]

      for (const cliPath of possiblePaths) {
        if (fs.existsSync(cliPath)) {
          this.log?.(`[SubprocessTransport] Found bundled CLI at: ${cliPath}`)
          return cliPath
        }
      }

      this.log?.(`[SubprocessTransport] Bundled CLI not found in any of: ${possiblePaths.join(', ')}`)
      return null
    } catch (err) {
      this.log?.(`[SubprocessTransport] Error finding bundled CLI: ${err}`)
      return null
    }
  }

  /**
   * Find the Node.js executable.
   */
  private findNodeExecutable(): string {
    // 1. User-configured path
    if (this.options.nodePath) {
      if (!fs.existsSync(this.options.nodePath)) {
        throw NodeNotFoundError.invalidConfiguredPath(this.options.nodePath)
      }
      this.log?.(`[SubprocessTransport] Using configured Node.js: ${this.options.nodePath}`)
      return this.options.nodePath
    }

    // 2. Use process.execPath (the Node.js that VS Code is running on)
    // This is typically the best option in VS Code context
    this.log?.(`[SubprocessTransport] Using current Node.js: ${process.execPath}`)
    return process.execPath
  }

  /**
   * Read remaining stderr content.
   */
  private async readStderr(): Promise<string> {
    if (!this.process?.stderr) return 'No stderr available'
    try {
      const chunks: Buffer[] = []
      for await (const chunk of this.process.stderr) {
        chunks.push(chunk)
      }
      return Buffer.concat(chunks).toString('utf8')
    } catch {
      return 'Failed to read stderr'
    }
  }

  /**
   * Check if Node.js is installed.
   */
  private async isNodeInstalled(): Promise<boolean> {
    try {
      const { execSync } = await import('child_process')
      execSync('node --version', { stdio: 'ignore' })
      return true
    } catch {
      return false
    }
  }
}
