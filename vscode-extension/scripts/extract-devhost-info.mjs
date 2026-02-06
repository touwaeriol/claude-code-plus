import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'

function resolveFromRepoRoot(...parts) {
  return path.resolve(process.cwd(), ...parts)
}

function getLatestLogFilePath() {
  const base = resolveFromRepoRoot('.vscode-dev')
  if (!existsSync(base)) return null

  const candidates = []
  for (const entry of readdirSync(base, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue
    const userDataDir = path.join(base, entry.name)
    const logsDir = path.join(userDataDir, 'logs')
    if (!existsSync(logsDir)) continue

    for (const logSet of readdirSync(logsDir, { withFileTypes: true })) {
      if (!logSet.isDirectory()) continue
      const full = path.join(
        logsDir,
        logSet.name,
        'window1',
        'exthost',
        'asakii.claude-code-plus',
        'claude-code-plus.log'
      )
      if (!existsSync(full)) continue
      const mtimeMs = statSync(full).mtimeMs
      candidates.push({ full, mtimeMs })
    }
  }

  candidates.sort((a, b) => b.mtimeMs - a.mtimeMs)
  return candidates[0]?.full ?? null
}

function parseLog(text) {
  const result = {
    httpBaseUrl: null,
    mcpGatewayPort: null,
    connectId: null,
    wsToken: null,
  }

  const httpMatch = text.match(/\[HttpApiServer\]\s+http listening\s+(http:\/\/127\.0\.0\.1:(\d+))/)
  if (httpMatch) {
    result.httpBaseUrl = httpMatch[1]
  }

  const mcpMatch = text.match(/\[McpHttpGateway\].*port now:\s+(\d+)/)
  if (mcpMatch) {
    result.mcpGatewayPort = Number(mcpMatch[1])
  } else {
    const mcpStartedMatch = text.match(/\[McpHttpGateway\]\s+HTTP gateway started.*127\.0\.0\.1:(\d+)\/mcp/)
    if (mcpStartedMatch) result.mcpGatewayPort = Number(mcpStartedMatch[1])
  }

  const connectIdMatch = text.match(/\[rsocket\]\s+agent\.connect: connectId=([0-9a-f\-]+)/i)
  if (connectIdMatch) result.connectId = connectIdMatch[1]

  const tokenMatch = text.match(/\/rsocket\?token=([0-9a-f\-]+)/i)
  if (tokenMatch) result.wsToken = tokenMatch[1]

  return result
}

function main() {
  const argPath = process.argv[2]
  const logPath = argPath ? resolveFromRepoRoot(argPath) : getLatestLogFilePath()
  if (!logPath || !existsSync(logPath)) {
    // eslint-disable-next-line no-console
    console.error('[extract-devhost-info] log file not found')
    process.exit(1)
  }

  const text = readFileSync(logPath, 'utf8')
  const parsed = parseLog(text)

  // eslint-disable-next-line no-console
  console.log(
    JSON.stringify(
      {
        logPath,
        ...parsed,
        mcpGatewayBaseUrl: parsed.mcpGatewayPort ? `http://127.0.0.1:${parsed.mcpGatewayPort}/mcp` : null,
      },
      null,
      2
    )
  )
}

main()

