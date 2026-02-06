import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'

import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js'

function resolveFromExtensionRoot(...parts) {
  return path.resolve(process.cwd(), ...parts)
}

function getLatestLogFilePath() {
  const base = resolveFromExtensionRoot('.vscode-dev')
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
  }

  const httpMatch = text.match(/\[HttpApiServer\]\s+http listening\s+(http:\/\/127\.0\.0\.1:(\d+))/)
  if (httpMatch) {
    result.httpBaseUrl = httpMatch[1]
  }

  const mcpMatch = text.match(/\[McpHttpGateway\].*127\.0\.0\.1:(\d+)\/mcp/)
  if (mcpMatch) result.mcpGatewayPort = Number(mcpMatch[1])

  const connectIdMatch = text.match(/\[rsocket\]\s+agent\.connect: connectId=([0-9a-f\-]+)/i)
  if (connectIdMatch) result.connectId = connectIdMatch[1]

  return result
}

async function main() {
  const argPath = process.argv[2]
  const logPath = argPath ? resolveFromExtensionRoot(argPath) : getLatestLogFilePath()
  if (!logPath || !existsSync(logPath)) {
    throw new Error('[test-ask-user] log file not found')
  }

  const text = readFileSync(logPath, 'utf8')
  const info = parseLog(text)
  if (!info.mcpGatewayPort || !info.connectId) {
    throw new Error(`[test-ask-user] failed to parse mcpGatewayPort/connectId from log: ${logPath}`)
  }

  const endpoint = new URL(`http://127.0.0.1:${info.mcpGatewayPort}/mcp/user-interaction`)

  // eslint-disable-next-line no-console
  console.log(`[test-ask-user] logPath=${logPath}`)
  // eslint-disable-next-line no-console
  console.log(`[test-ask-user] connectId=${info.connectId}`)
  // eslint-disable-next-line no-console
  console.log(`[test-ask-user] calling AskUserQuestion via ${endpoint.href}`)
  // eslint-disable-next-line no-console
  console.log('[test-ask-user] 请在 VS Code Dev Host 的界面里选择 A/B...')

  const client = new Client(
    { name: 'ccp-dev-test', version: '0.0.0' },
    {
      // 给用户手动选择留足时间。
      requestTimeout: 10 * 60 * 1000,
    }
  )
  const transport = new StreamableHTTPClientTransport(endpoint, {
    requestInit: {
      headers: {
        'x-mcp-connect-id': info.connectId,
      },
    },
  })
  await client.connect(transport)

  const result = await client.callTool({
    name: 'AskUserQuestion',
    arguments: {
      questions: [
        {
          header: 'Test',
          question: '请选择 A 或 B（本地自测）',
          options: [
            { label: 'A', description: '选 A' },
            { label: 'B', description: '选 B' },
          ],
          multiSelect: false,
        },
      ],
    },
  })

  // eslint-disable-next-line no-console
  console.log('[test-ask-user] result:', JSON.stringify(result, null, 2))
  await client.close()
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err)
  process.exit(1)
})
