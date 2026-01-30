import { Provider, type RpcMessage } from '@proto'

export interface HistorySessionMetadataDto {
  sessionId: string
  firstUserMessage: string
  timestamp: number
  messageCount: number
  projectPath: string
  customTitle?: string
}

export interface HistoryMetadataDto {
  totalLines: number
  sessionId: string
  projectPath: string
  customTitle?: string
}

interface SessionHistory {
  sessionId: string
  projectPath: string
  createdAt: number
  updatedAt: number
  providers: Set<'claude' | 'codex' | 'unknown'>
  customTitle?: string
  firstUserMessage?: string
  messages: RpcMessage[]
}

export class HistoryStore {
  private readonly sessions = new Map<string, SessionHistory>()

  hasSession(sessionId: string): boolean {
    return this.sessions.has(sessionId)
  }

  sessionHasProvider(sessionId: string, provider: 'claude' | 'codex'): boolean {
    const session = this.sessions.get(sessionId)
    return Boolean(session && session.providers.has(provider))
  }

  ensureSession(sessionId: string, projectPath: string, provider?: 'claude' | 'codex' | 'unknown') {
    const existing = this.sessions.get(sessionId)
    if (existing) {
      if (provider) existing.providers.add(provider)
      return existing
    }

    const now = Date.now()
    const created: SessionHistory = {
      sessionId,
      projectPath,
      createdAt: now,
      updatedAt: now,
      providers: new Set(provider ? [provider] : []),
      messages: [],
    }
    this.sessions.set(sessionId, created)
    return created
  }

  appendMessage(sessionId: string, projectPath: string, message: RpcMessage) {
    const session = this.ensureSession(sessionId, projectPath, providerKeyFromMessage(message))
    session.messages.push(message)
    session.updatedAt = Date.now()

    if (!session.firstUserMessage && message.message.case === 'user') {
      session.firstUserMessage = extractFirstUserText(message) || ''
    }
  }

  listSessions(offset: number, maxResults: number, provider?: 'claude' | 'codex'): HistorySessionMetadataDto[] {
    const sessions = [...this.sessions.values()]
      .filter((s) => (provider ? s.providers.has(provider) : true))
      .sort((a, b) => b.updatedAt - a.updatedAt)
    return sessions.slice(offset, offset + maxResults).map((s) => ({
      sessionId: s.sessionId,
      firstUserMessage: s.firstUserMessage || '',
      timestamp: s.updatedAt,
      messageCount: s.messages.length,
      projectPath: s.projectPath,
      ...(s.customTitle ? { customTitle: s.customTitle } : {}),
    }))
  }

  loadHistory(
    sessionId: string,
    offset: number,
    limit: number,
    provider?: 'claude' | 'codex'
  ): { messages: RpcMessage[]; offset: number; count: number; availableCount: number } {
    const session = this.sessions.get(sessionId)
    if (provider && session && !session.providers.has(provider)) {
      return { messages: [], offset: Math.max(offset, 0), count: 0, availableCount: 0 }
    }
    const all = session?.messages ?? []
    const start = Math.max(offset, 0)
    const end = Math.max(start, start + Math.max(limit, 0))
    const page = all.slice(start, end)
    return { messages: page, offset: start, count: page.length, availableCount: all.length }
  }

  getMetadata(sessionId: string, projectPath: string, provider?: 'claude' | 'codex'): HistoryMetadataDto {
    const session = this.sessions.get(sessionId)
    if (provider && session && !session.providers.has(provider)) {
      return { totalLines: 0, sessionId, projectPath }
    }
    const totalLines = session?.messages.length ?? 0
    return {
      totalLines,
      sessionId,
      projectPath: session?.projectPath ?? projectPath,
      ...(session?.customTitle ? { customTitle: session.customTitle } : {}),
    }
  }

  getSessionMessages(sessionId: string): RpcMessage[] {
    return this.sessions.get(sessionId)?.messages ?? []
  }

  truncateHistory(sessionId: string, messageUuid: string): { success: boolean; remainingLines: number; error?: string } {
    const session = this.sessions.get(sessionId)
    if (!session) {
      return { success: false, remainingLines: 0, error: `Session not found: ${sessionId}` }
    }

    const index = session.messages.findIndex((msg) => extractTopLevelUuid(msg) === messageUuid)
    if (index < 0) {
      return { success: false, remainingLines: session.messages.length, error: `Message not found: ${messageUuid}` }
    }

    session.messages.splice(index)
    session.updatedAt = Date.now()
    return { success: true, remainingLines: session.messages.length }
  }

  deleteSession(sessionId: string): boolean {
    return this.sessions.delete(sessionId)
  }
}

function providerKeyFromMessage(message: RpcMessage): 'claude' | 'codex' | 'unknown' {
  // RpcMessage.provider is an enum value; map it to a stable string key for filtering.
  switch (message.provider) {
    case Provider.CLAUDE:
      return 'claude'
    case Provider.CODEX:
      return 'codex'
    default:
      return 'unknown'
  }
}

function extractFirstUserText(message: RpcMessage): string | undefined {
  if (message.message.case !== 'user') return undefined
  const content = message.message.value.message?.content ?? []
  for (const block of content) {
    if (block.block.case === 'text') return block.block.value.text
  }
  return undefined
}

function extractTopLevelUuid(message: RpcMessage): string | undefined {
  switch (message.message.case) {
    case 'user':
      return message.message.value.uuid
    case 'assistant':
      return message.message.value.uuid
    case 'streamEvent':
      return message.message.value.uuid
    default:
      return undefined
  }
}
