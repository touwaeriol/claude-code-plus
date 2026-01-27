import * as path from 'path'

export type SnapshotEntry = {
  toolUseId: string
  filePath: string
  timestamp: number
  content: string
}

export class SnapshotStore {
  private readonly snapshotsByFile = new Map<string, SnapshotEntry[]>()
  private readonly snapshotsByToolUseId = new Map<string, SnapshotEntry>()

  constructor(private readonly options: { maxSnapshotsPerFile?: number } = {}) {}

  save(entry: SnapshotEntry): void {
    const fileKey = normalizeFileKey(entry.filePath)

    const list = this.snapshotsByFile.get(fileKey) ?? []
    list.push(entry)
    list.sort((a, b) => a.timestamp - b.timestamp)
    this.snapshotsByFile.set(fileKey, list)

    this.snapshotsByToolUseId.set(entry.toolUseId, entry)

    const max = this.options.maxSnapshotsPerFile ?? 50
    if (list.length > max) {
      const removed = list.splice(0, list.length - max)
      for (const r of removed) {
        if (this.snapshotsByToolUseId.get(r.toolUseId) === r) {
          this.snapshotsByToolUseId.delete(r.toolUseId)
        }
      }
    }
  }

  getOriginalContent(toolUseId: string): string | undefined {
    return this.snapshotsByToolUseId.get(toolUseId)?.content
  }

  /**
   * 获取某个时间戳之前（<= beforeTimestamp）的最近快照
   */
  getSnapshotBefore(filePath: string, beforeTimestamp: number): SnapshotEntry | undefined {
    const fileKey = normalizeFileKey(filePath)
    const list = this.snapshotsByFile.get(fileKey)
    if (!list || list.length === 0) return undefined

    for (let i = list.length - 1; i >= 0; i--) {
      const s = list[i]
      if (s.timestamp <= beforeTimestamp) return s
    }

    return undefined
  }
}

function normalizeFileKey(filePath: string): string {
  const normalized = path.normalize(filePath).replace(/\\/g, '/')
  return process.platform === 'win32' ? normalized.toLowerCase() : normalized
}

