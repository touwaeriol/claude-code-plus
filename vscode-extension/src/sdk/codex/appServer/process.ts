/**
 * Codex App-Server 进程管理器
 *
 * 翻译自: codex-agent-sdk/.../appserver/CodexAppServerProcess.kt
 *
 * 负责启动和管理 `codex app-server` 进程的生命周期
 */

import { spawn, ChildProcess } from 'child_process'
import * as path from 'path'
import * as fs from 'fs'
import { CodexJsonRpcClient } from './jsonRpcClient'

export class CodexAppServerException extends Error {
  constructor(
    message: string,
    public cause?: Error
  ) {
    super(message)
    this.name = 'CodexAppServerException'
  }
}

export interface SpawnOptions {
  codexPath?: string
  workingDirectory?: string
  env?: Record<string, string>
  configOverrides?: Record<string, string>
}

export class CodexAppServerProcess {
  private _process: ChildProcess
  private _client: CodexJsonRpcClient

  private constructor(proc: ChildProcess, client: CodexJsonRpcClient) {
    this._process = proc
    this._client = client
  }

  get client(): CodexJsonRpcClient {
    return this._client
  }

  get isAlive(): boolean {
    return !this._process.killed && this._process.exitCode === null
  }

  close(): void {
    this._client.close()
    if (this.isAlive) {
      this._process.kill('SIGTERM')
      setTimeout(() => {
        if (this.isAlive) {
          this._process.kill('SIGKILL')
        }
      }, 5000)
    }
  }

  static spawn(options: SpawnOptions = {}): CodexAppServerProcess {
    const executablePath = options.codexPath || findCodexExecutable()
    console.log(
      `[CodexAppServerProcess] Starting codex app-server: path=${executablePath} cwd=${options.workingDirectory || 'default'}`
    )

    const args = ['app-server']

    // 添加配置覆盖参数 (-c key=value)
    if (options.configOverrides) {
      for (const [key, value] of Object.entries(options.configOverrides)) {
        args.push('-c', `${key}=${value}`)
      }
    }

    console.log(`[CodexAppServerProcess] Executing command: ${executablePath} ${args.join(' ')}`)

    const proc = spawn(executablePath, args, {
      cwd: options.workingDirectory,
      env: { ...process.env, ...options.env },
      stdio: ['pipe', 'pipe', 'pipe'],
      shell: process.platform === 'win32',
    })

    if (!proc.stdin || !proc.stdout) {
      throw new CodexAppServerException('Failed to get stdin/stdout of codex app-server')
    }

    // 记录 stderr
    proc.stderr?.on('data', (data: Buffer) => {
      const line = data.toString().trim()
      if (line) {
        console.warn(`[codex stderr] ${line}`)
      }
    })

    proc.on('exit', (code) => {
      console.warn(`[CodexAppServerProcess] codex app-server exited: code=${code}`)
    })

    proc.on('error', (err) => {
      console.error(`[CodexAppServerProcess] codex app-server error:`, err)
    })

    const client = new CodexJsonRpcClient(proc.stdin, proc.stdout)
    client.start()

    return new CodexAppServerProcess(proc, client)
  }
}

function findCodexExecutable(): string {
  const isWindows = process.platform === 'win32'
  const candidates = isWindows ? ['codex.exe', 'codex.cmd', 'codex.bat', 'codex'] : ['codex']

  // 1. 检查环境变量 CODEX_BIN
  const codexBin = process.env.CODEX_BIN
  if (codexBin && fs.existsSync(codexBin)) {
    return codexBin
  }

  // 2. 检查 PATH
  const pathEnv = process.env.PATH || ''
  const pathSeparator = isWindows ? ';' : ':'
  for (const dir of pathEnv.split(pathSeparator)) {
    for (const candidate of candidates) {
      const fullPath = path.join(dir, candidate)
      if (fs.existsSync(fullPath)) {
        return fullPath
      }
    }
  }

  // 3. 检查常见位置
  const commonPaths = isWindows
    ? [
        process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Programs', 'codex', 'codex.exe'),
        process.env.APPDATA && path.join(process.env.APPDATA, 'npm', 'codex.cmd'),
      ]
    : [
        process.env.HOME && path.join(process.env.HOME, '.local', 'bin', 'codex'),
        '/usr/local/bin/codex',
        '/usr/bin/codex',
      ]

  for (const p of commonPaths.filter(Boolean) as string[]) {
    if (fs.existsSync(p)) {
      return p
    }
  }

  throw new CodexAppServerException(
    'Codex executable not found. Please install Codex or set CODEX_BIN environment variable.'
  )
}
