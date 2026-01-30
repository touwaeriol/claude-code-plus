/**
 * Parity check runner for JB vs VS Code backends.
 *
 * Goals (minimal but useful):
 * - Probe HTTP endpoints used by frontend (health, files/search, history, settings).
 * - Probe RSocket endpoints (/rsocket, /ide-rsocket) with a few safe routes.
 * - Output machine-readable JSON for CI/regression.
 *
 * Note: This file is TypeScript and is intended to be bundled via `scripts/parity-check.mjs`.
 */

import * as fs from 'fs'
import * as path from 'path'

import { Buffer } from 'buffer'
import { RSocketConnector } from 'rsocket-core'
import { WebsocketClientTransport } from 'rsocket-websocket-client'
import type {
  Payload,
  RSocket,
  OnExtensionSubscriber,
  OnNextSubscriber,
  OnTerminalSubscriber,
} from 'rsocket-core'

import { create, fromBinary, toBinary } from '@bufbuild/protobuf'

import {
  // HTTP history protobuf
  GetHistoryMetadataRequestSchema,
  LoadHistoryRequestSchema,
  HistoryMetadataSchema,
  HistoryResultSchema,

  // RSocket agent
  ConnectResultSchema,
  Provider,

  // IDE RSocket
  JetBrainsGetThemeResponseSchema,
  GetIdeSettingsResponseSchema,
  JetBrainsGetLocaleResponseSchema,
  JetBrainsGetProjectPathResponseSchema,
  IdeThemeProtoSchema,

  // server->client calls (IDE notifications)
  ServerCallRequestSchema,
  ServerCallResponseSchema,
} from '../ai-agent-proto/ts'

type CheckResult = {
  name: string
  ok: boolean
  durationMs: number
  details?: unknown
  error?: string
}

type Report = {
  timestamp: string
  baseUrl: string
  wsBaseUrl: string
  tokenProvided: boolean
  http: CheckResult[]
  rsocket: CheckResult[]
  ideRSocket: CheckResult[]
  summary: {
    ok: boolean
    okCount: number
    failCount: number
  }
}

type CliArgs = {
  baseUrl: string
  token?: string
  out?: string
  fontFamily?: string
  timeoutMs: number
}

function nowIso(): string {
  return new Date().toISOString()
}

function toWsBaseUrl(httpBaseUrl: string): string {
  if (httpBaseUrl.startsWith('https://')) return 'wss://' + httpBaseUrl.slice('https://'.length)
  if (httpBaseUrl.startsWith('http://')) return 'ws://' + httpBaseUrl.slice('http://'.length)
  // assume already ws(s)
  return httpBaseUrl
}

function parseArgs(argv: string[]): CliArgs {
  const args: CliArgs = {
    baseUrl: 'http://127.0.0.1:8765',
    timeoutMs: 3500,
  }

  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--baseUrl' && argv[i + 1]) {
      args.baseUrl = argv[++i]
      continue
    }
    if (a === '--token' && argv[i + 1]) {
      args.token = argv[++i]
      continue
    }
    if (a === '--out' && argv[i + 1]) {
      args.out = argv[++i]
      continue
    }
    if (a === '--font' && argv[i + 1]) {
      args.fontFamily = argv[++i]
      continue
    }
    if (a === '--timeoutMs' && argv[i + 1]) {
      const n = Number(argv[++i])
      if (Number.isFinite(n) && n > 0) args.timeoutMs = n
      continue
    }
  }

  return args
}

function withTokenHeader(token: string | undefined, headers: Record<string, string> = {}): Record<string, string> {
  if (!token) return headers
  return { ...headers, 'X-Claude-Code-Plus-Token': token }
}

async function httpJson(
  url: string,
  init: RequestInit,
  timeoutMs: number
): Promise<{ status: number; ok: boolean; json?: any; text?: string }> {
  const controller = new AbortController()
  const id = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(url, { ...init, signal: controller.signal })
    const contentType = res.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const json = await res.json().catch(() => undefined)
      return { status: res.status, ok: res.ok, json }
    }
    const text = await res.text().catch(() => '')
    return { status: res.status, ok: res.ok, text }
  } finally {
    clearTimeout(id)
  }
}

async function httpBytes(url: string, init: RequestInit, timeoutMs: number): Promise<{ status: number; ok: boolean; bytes: Uint8Array }> {
  const controller = new AbortController()
  const id = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(url, { ...init, signal: controller.signal })
    const buf = new Uint8Array(await res.arrayBuffer())
    return { status: res.status, ok: res.ok, bytes: buf }
  } finally {
    clearTimeout(id)
  }
}

function encodeRoute(route: string): Buffer {
  const routeBytes = Buffer.from(route, 'utf8')
  const metadata = Buffer.alloc(1 + routeBytes.length)
  metadata[0] = routeBytes.length
  routeBytes.copy(metadata, 1)
  return metadata
}

function extractRoute(payload: Payload): string {
  if (!payload.metadata) throw new Error('Missing metadata')
  const metadata = Buffer.from(payload.metadata as any)
  if (metadata.length === 0) throw new Error('Empty metadata')
  const len = metadata[0]
  return metadata.subarray(1, 1 + len).toString('utf8')
}

function createPayload(route: string, data?: Uint8Array): Payload {
  return {
    data: data ? Buffer.from(data) : undefined,
    metadata: encodeRoute(route),
  }
}

function getWsCtor(): any {
  // `ws` exists in vscode-extension deps; we resolve it at bundle time.
  // Keep it dynamic to avoid ESM/CJS default export pitfalls.
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const mod = require('ws')
  return mod.WebSocket || mod
}

async function connectRSocket(url: string, timeoutMs: number): Promise<RSocket> {
  const WebSocketCtor = getWsCtor()

  const responder: Partial<RSocket> = {
    // Server may call `client.call` to push notifications (theme/settings/activeFile).
    // We respond with an empty success to keep the connection healthy.
    requestResponse: (payload: Payload, responderStream: OnTerminalSubscriber & OnNextSubscriber & OnExtensionSubscriber) => {
      try {
        const route = extractRoute(payload)
        if (route === 'client.call') {
          const data = payload.data ? new Uint8Array(payload.data as any) : new Uint8Array()
          const req = data.length > 0 ? fromBinary(ServerCallRequestSchema, data) : create(ServerCallRequestSchema, {} as any)
          const resp = create(ServerCallResponseSchema, {
            callId: String((req as any).callId ?? ''),
            success: true,
            result: { case: 'resultJson', value: new Uint8Array() },
          } as any)
          const bytes = toBinary(ServerCallResponseSchema, resp)
          responderStream.onNext({ data: Buffer.from(bytes) }, true)
          responderStream.onComplete()
          return { cancel: () => {}, onExtension: () => {} }
        }

        responderStream.onError(new Error(`Unsupported server call route: ${route}`))
        return { cancel: () => {}, onExtension: () => {} }
      } catch (err) {
        responderStream.onError(err instanceof Error ? err : new Error(String(err)))
        return { cancel: () => {}, onExtension: () => {} }
      }
    },
  }

  const connector = new RSocketConnector({
    setup: {
      keepAlive: 30_000,
      lifetime: 90_000,
      dataMimeType: 'application/x-protobuf',
      metadataMimeType: 'message/x.rsocket.routing.v0',
    },
    transport: new WebsocketClientTransport({
      url,
      wsCreator: (u) => new WebSocketCtor(u) as any,
    }),
    responder,
  })

  const controller = new AbortController()
  const id = setTimeout(() => controller.abort(), timeoutMs)
  try {
    // rsocket-core connector doesn't accept AbortSignal; we implement timeout via Promise.race.
    const timeout = new Promise<never>((_, reject) => {
      controller.signal.addEventListener('abort', () => reject(new Error(`RSocket connect timeout (${timeoutMs}ms)`)), { once: true })
    })
    return await Promise.race([connector.connect(), timeout])
  } finally {
    clearTimeout(id)
  }
}

async function rsocketRequestResponse(rsocket: RSocket, route: string, data?: Uint8Array, timeoutMs: number = 5000): Promise<Uint8Array> {
  const payload = createPayload(route, data)
  return await new Promise((resolve, reject) => {
    let settled = false
    const id = setTimeout(() => {
      if (settled) return
      settled = true
      reject(new Error(`requestResponse timeout (${timeoutMs}ms): ${route}`))
    }, timeoutMs)

    rsocket.requestResponse(payload, {
      onError: (error: Error) => {
        if (settled) return
        settled = true
        clearTimeout(id)
        reject(error)
      },
      onNext: (p: Payload) => {
        if (settled) return
        settled = true
        clearTimeout(id)
        const bytes = p.data ? new Uint8Array(p.data as any) : new Uint8Array()
        resolve(bytes)
      },
      onComplete: () => {},
      onExtension: () => {},
    })
  })
}

async function rsocketAssertStreamNotCompleteQuickly(rsocket: RSocket, route: string, data: Uint8Array | undefined, windowMs: number): Promise<void> {
  const payload = createPayload(route, data)
  return await new Promise((resolve, reject) => {
    let completed = false
    let errored = false
    let cancelled = false

    const timer = setTimeout(() => {
      if (!completed && !errored) {
        // Good: stream is still alive.
        cancelled = true
        try {
          cancellable.cancel()
        } catch {
          // ignore
        }
        resolve()
      }
    }, windowMs)

    const cancellable = rsocket.requestStream(payload, 0x7fffffff, {
      onError: (error: Error) => {
        if (cancelled) return
        errored = true
        clearTimeout(timer)
        reject(error)
      },
      onNext: (_p: Payload) => {
        // ignore; existence of data is not required for this check
      },
      onComplete: () => {
        if (cancelled) return
        completed = true
        clearTimeout(timer)
        reject(new Error(`stream completed too early: ${route}`))
      },
      onExtension: () => {},
    })
  })
}

async function runCheck(name: string, fn: () => Promise<unknown>): Promise<CheckResult> {
  const start = Date.now()
  try {
    const details = await fn()
    return { name, ok: true, durationMs: Date.now() - start, details }
  } catch (err) {
    return {
      name,
      ok: false,
      durationMs: Date.now() - start,
      error: err instanceof Error ? err.message : String(err),
    }
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const baseUrl = args.baseUrl.replace(/\/+$/, '')
  const wsBaseUrl = toWsBaseUrl(baseUrl).replace(/\/+$/, '')

  const report: Report = {
    timestamp: nowIso(),
    baseUrl,
    wsBaseUrl,
    tokenProvided: Boolean(args.token),
    http: [],
    rsocket: [],
    ideRSocket: [],
    summary: { ok: false, okCount: 0, failCount: 0 },
  }

  // --------------------
  // HTTP checks
  // --------------------
  report.http.push(
    await runCheck('HTTP GET /api/health', async () => {
      const r = await httpJson(`${baseUrl}/api/health`, { method: 'GET', headers: withTokenHeader(args.token) }, args.timeoutMs)
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      if (!r.json || r.json.status !== 'ok') throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
      return r.json
    })
  )

  const codexHealthCheck = await runCheck('HTTP GET /api/codex/health', async () => {
    const r = await httpJson(`${baseUrl}/api/codex/health`, { method: 'GET', headers: withTokenHeader(args.token) }, args.timeoutMs)
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const status = r.json?.status
    if (status !== 'ok' && status !== 'unavailable') throw new Error(`Unexpected status: ${JSON.stringify(r.json)}`)
    return r.json
  })
  report.http.push(codexHealthCheck)

  // `/api/codex/*` routing checks (accept 503 when backend is unavailable).
  const codexConfigGetCheck = await runCheck('HTTP GET /api/codex/config', async () => {
    const r = await httpJson(`${baseUrl}/api/codex/config`, { method: 'GET', headers: withTokenHeader(args.token) }, args.timeoutMs)
    if (r.status === 503) return { status: 503 }
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    if (!r.json?.success) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
    return r.json
  })
  report.http.push(codexConfigGetCheck)

  const codexConfigPutCheck = await runCheck('HTTP PUT /api/codex/config', async () => {
    const r = await httpJson(
      `${baseUrl}/api/codex/config`,
      { method: 'PUT', headers: withTokenHeader(args.token, { 'Content-Type': 'application/json' }), body: JSON.stringify({}) },
      args.timeoutMs
    )
    if (r.status === 503) return { status: 503 }
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    if (!r.json?.success) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
    return r.json
  })
  report.http.push(codexConfigPutCheck)

  const codexThreadStartCheck = await runCheck('HTTP POST /api/codex/thread/start', async () => {
    const r = await httpJson(
      `${baseUrl}/api/codex/thread/start`,
      { method: 'POST', headers: withTokenHeader(args.token, { 'Content-Type': 'application/json' }), body: JSON.stringify({}) },
      args.timeoutMs
    )
    if (r.status === 503) return { status: 503 }
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    if (!r.json?.success || typeof r.json?.threadId !== 'string') throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
    return { threadId: r.json.threadId }
  })
  report.http.push(codexThreadStartCheck)

  let startedThreadId: string | undefined
  if (codexThreadStartCheck.ok && codexThreadStartCheck.details && typeof codexThreadStartCheck.details === 'object') {
    const d = codexThreadStartCheck.details as any
    if (typeof d.threadId === 'string') startedThreadId = d.threadId
  }

  if (startedThreadId) {
    report.http.push(
      await runCheck('HTTP GET /api/codex/thread/{threadId}/state', async () => {
        const r = await httpJson(
          `${baseUrl}/api/codex/thread/${encodeURIComponent(startedThreadId)}/state`,
          { method: 'GET', headers: withTokenHeader(args.token) },
          args.timeoutMs
        )
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        if (!r.json?.success || r.json?.state?.threadId !== startedThreadId) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
        return { ok: true }
      })
    )

    report.http.push(
      await runCheck('HTTP POST /api/codex/thread/archive', async () => {
        const r = await httpJson(
          `${baseUrl}/api/codex/thread/archive`,
          {
            method: 'POST',
            headers: withTokenHeader(args.token, { 'Content-Type': 'application/json' }),
            body: JSON.stringify({ threadId: startedThreadId }),
          },
          args.timeoutMs
        )
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        if (!r.json?.success) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
        return { ok: true }
      })
    )
  }

  report.http.push(
    await runCheck('HTTP GET /api/files/search (empty query)', async () => {
      const r = await httpJson(
        `${baseUrl}/api/files/search?query=&maxResults=5`,
        { method: 'GET', headers: withTokenHeader(args.token) },
        args.timeoutMs
      )
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      if (typeof r.json?.success !== 'boolean') throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
      return { success: r.json.success, count: Array.isArray(r.json.data) ? r.json.data.length : null }
    })
  )

  report.http.push(
    await runCheck('HTTP POST /api/ action=settings.get', async () => {
      const r = await httpJson(
        `${baseUrl}/api/`,
        {
          method: 'POST',
          headers: withTokenHeader(args.token, { 'Content-Type': 'application/json' }),
          body: JSON.stringify({ action: 'settings.get' }),
        },
        args.timeoutMs
      )
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      if (!r.json?.success) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
      if (!r.json?.data?.settings) throw new Error('Missing data.settings')
      return { keys: Object.keys(r.json.data.settings) }
    })
  )

  report.http.push(
    await runCheck('HTTP POST /api/ action=models.getAvailable', async () => {
      const r = await httpJson(
        `${baseUrl}/api/`,
        {
          method: 'POST',
          headers: withTokenHeader(args.token, { 'Content-Type': 'application/json' }),
          body: JSON.stringify({ action: 'models.getAvailable' }),
        },
        args.timeoutMs
      )
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      if (!r.json?.success) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
      const claudeModels = r.json?.data?.claudeModels
      const codexModels = r.json?.data?.codexModels
      if (!Array.isArray(claudeModels) || !Array.isArray(codexModels)) throw new Error('Missing claudeModels/codexModels')
      return { claude: claudeModels.length, codex: codexModels.length }
    })
  )

  report.http.push(
    await runCheck('HTTP GET /api/history/sessions', async () => {
      const r = await httpJson(
        `${baseUrl}/api/history/sessions?offset=0&maxResults=5`,
        { method: 'GET', headers: withTokenHeader(args.token) },
        args.timeoutMs
      )
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const sessions = r.json?.sessions
      if (!Array.isArray(sessions)) throw new Error(`Unexpected body: ${JSON.stringify(r.json)}`)
      return { count: sessions.length }
    })
  )

  report.http.push(
    await runCheck('HTTP POST /api/history/metadata.pb (empty)', async () => {
      const req = create(GetHistoryMetadataRequestSchema, { sessionId: '', projectPath: '' })
      const bin = toBinary(GetHistoryMetadataRequestSchema, req)
      const r = await httpBytes(
        `${baseUrl}/api/history/metadata.pb`,
        { method: 'POST', headers: withTokenHeader(args.token, { 'Content-Type': 'application/octet-stream' }), body: Buffer.from(bin) },
        args.timeoutMs
      )
      // Some servers may 400 on missing sessionId; treat non-2xx as error, but include status in details.
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const decoded = fromBinary(HistoryMetadataSchema, r.bytes)
      return { totalLines: decoded.totalLines, sessionId: decoded.sessionId }
    })
  )

  report.http.push(
    await runCheck('HTTP POST /api/history/load.pb (empty)', async () => {
      const req = create(LoadHistoryRequestSchema, { sessionId: '', projectPath: '', offset: 0, limit: 10 })
      const bin = toBinary(LoadHistoryRequestSchema, req)
      const r = await httpBytes(
        `${baseUrl}/api/history/load.pb`,
        { method: 'POST', headers: withTokenHeader(args.token, { 'Content-Type': 'application/octet-stream' }), body: Buffer.from(bin) },
        args.timeoutMs
      )
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const decoded = fromBinary(HistoryResultSchema, r.bytes)
      return { count: decoded.count, availableCount: decoded.availableCount }
    })
  )

  if (args.fontFamily) {
    report.http.push(
      await runCheck(`HTTP GET /api/font/${args.fontFamily}`, async () => {
        const url = `${baseUrl}/api/font/${encodeURIComponent(args.fontFamily)}`
        const r = await httpBytes(url, { method: 'GET', headers: withTokenHeader(args.token) }, args.timeoutMs)
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        if (r.bytes.length < 1000) throw new Error(`Font payload too small: ${r.bytes.length} bytes`)
        return { bytes: r.bytes.length }
      })
    )
  }

  // --------------------
  // RSocket (/rsocket)
  // --------------------
  report.rsocket.push(
    await runCheck('RSocket connect /rsocket + agent.connect', async () => {
      const url = new URL(`${wsBaseUrl}/rsocket`)
      if (args.token) url.searchParams.set('token', args.token)
      const rsocket = await connectRSocket(url.toString(), args.timeoutMs * 2)
      try {
        const raw = await rsocketRequestResponse(rsocket, 'agent.connect', undefined, args.timeoutMs * 2)
        const decoded = fromBinary(ConnectResultSchema, raw)
        const provider = decoded.provider
        const providerName = provider === Provider.CLAUDE ? 'claude' : provider === Provider.CODEX ? 'codex' : String(provider)
        if (!decoded.sessionId) throw new Error(`Missing sessionId: ${JSON.stringify(decoded)}`)
        return { sessionId: decoded.sessionId, provider: providerName }
      } finally {
        try {
          rsocket.close()
        } catch {
          // ignore
        }
      }
    })
  )

  report.rsocket.push(
    await runCheck('RSocket agent.events should not complete immediately', async () => {
      const url = new URL(`${wsBaseUrl}/rsocket`)
      if (args.token) url.searchParams.set('token', args.token)
      const rsocket = await connectRSocket(url.toString(), args.timeoutMs * 2)
      try {
        await rsocketAssertStreamNotCompleteQuickly(rsocket, 'agent.events', undefined, 500)
        return { windowMs: 500 }
      } finally {
        try {
          rsocket.close()
        } catch {
          // ignore
        }
      }
    })
  )

  // --------------------
  // IDE RSocket (/ide-rsocket)
  // --------------------
  report.ideRSocket.push(
    await runCheck('RSocket connect /ide-rsocket + ide.getTheme', async () => {
      const url = new URL(`${wsBaseUrl}/ide-rsocket`)
      if (args.token) url.searchParams.set('token', args.token)
      const rsocket = await connectRSocket(url.toString(), args.timeoutMs * 2)
      try {
        const raw = await rsocketRequestResponse(rsocket, 'ide.getTheme', undefined, args.timeoutMs * 2)
        const decoded = fromBinary(JetBrainsGetThemeResponseSchema, raw)
        const theme = decoded.theme ? fromBinary(IdeThemeProtoSchema, toBinary(IdeThemeProtoSchema, decoded.theme)) : undefined
        return {
          hasTheme: Boolean(decoded.theme),
          isDarkTheme: decoded.theme?.isDarkTheme ?? null,
          fontFamily: decoded.theme?.fontFamily ?? null,
          // Keep payload small; do not dump all colors here.
        }
      } finally {
        try {
          rsocket.close()
        } catch {
          // ignore
        }
      }
    })
  )

  report.ideRSocket.push(
    await runCheck('RSocket /ide-rsocket ide.getSettings options should not be empty', async () => {
      const url = new URL(`${wsBaseUrl}/ide-rsocket`)
      if (args.token) url.searchParams.set('token', args.token)
      const rsocket = await connectRSocket(url.toString(), args.timeoutMs * 2)
      try {
        const raw = await rsocketRequestResponse(rsocket, 'ide.getSettings', undefined, args.timeoutMs * 2)
        const decoded = fromBinary(GetIdeSettingsResponseSchema, raw)
        const s: any = decoded.settings
        if (!s) throw new Error('Missing settings')

        const counts = {
          thinkingLevels: Array.isArray(s.thinkingLevels) ? s.thinkingLevels.length : 0,
          permissionModeOptions: Array.isArray(s.permissionModeOptions) ? s.permissionModeOptions.length : 0,
          codexReasoningEffortOptions: Array.isArray(s.codexReasoningEffortOptions) ? s.codexReasoningEffortOptions.length : 0,
          codexReasoningSummaryOptions: Array.isArray(s.codexReasoningSummaryOptions) ? s.codexReasoningSummaryOptions.length : 0,
          codexSandboxModeOptions: Array.isArray(s.codexSandboxModeOptions) ? s.codexSandboxModeOptions.length : 0,
        }

        // This is a *parity gate*: empty option lists typically break UI dropdowns.
        if (counts.thinkingLevels <= 0) throw new Error(`thinkingLevels empty: ${JSON.stringify(counts)}`)
        if (counts.permissionModeOptions <= 0) throw new Error(`permissionModeOptions empty: ${JSON.stringify(counts)}`)
        if (counts.codexReasoningEffortOptions <= 0) throw new Error(`codexReasoningEffortOptions empty: ${JSON.stringify(counts)}`)
        if (counts.codexReasoningSummaryOptions <= 0) throw new Error(`codexReasoningSummaryOptions empty: ${JSON.stringify(counts)}`)
        if (counts.codexSandboxModeOptions <= 0) throw new Error(`codexSandboxModeOptions empty: ${JSON.stringify(counts)}`)

        return counts
      } finally {
        try {
          rsocket.close()
        } catch {
          // ignore
        }
      }
    })
  )

  report.ideRSocket.push(
    await runCheck('RSocket /ide-rsocket ide.getLocale + ide.getProjectPath', async () => {
      const url = new URL(`${wsBaseUrl}/ide-rsocket`)
      if (args.token) url.searchParams.set('token', args.token)
      const rsocket = await connectRSocket(url.toString(), args.timeoutMs * 2)
      try {
        const localeBytes = await rsocketRequestResponse(rsocket, 'ide.getLocale', undefined, args.timeoutMs * 2)
        const localeResp = fromBinary(JetBrainsGetLocaleResponseSchema, localeBytes)

        const projectBytes = await rsocketRequestResponse(rsocket, 'ide.getProjectPath', undefined, args.timeoutMs * 2)
        const projectResp = fromBinary(JetBrainsGetProjectPathResponseSchema, projectBytes)

        return { locale: localeResp.locale, projectPath: projectResp.projectPath }
      } finally {
        try {
          rsocket.close()
        } catch {
          // ignore
        }
      }
    })
  )

  // --------------------
  // Summary + output
  // --------------------
  const all = [...report.http, ...report.rsocket, ...report.ideRSocket]
  const okCount = all.filter((r) => r.ok).length
  const failCount = all.length - okCount
  report.summary = { ok: failCount === 0, okCount, failCount }

  const json = JSON.stringify(report, null, 2)
  if (args.out) {
    const outPath = path.resolve(process.cwd(), args.out)
    fs.mkdirSync(path.dirname(outPath), { recursive: true })
    fs.writeFileSync(outPath, json, 'utf8')
  } else {
    // eslint-disable-next-line no-console
    console.log(json)
  }

  // Exit code for CI
  if (failCount > 0) process.exitCode = 1
}

void main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(`[parity-check] fatal: ${err instanceof Error ? err.stack || err.message : String(err)}`)
  process.exit(1)
})
