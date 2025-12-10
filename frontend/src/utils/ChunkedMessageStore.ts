import Denque from 'denque'

interface ChunkedStoreOptions<T> {
  windowSize: number
  dedupe?: boolean
  /**
   * 提供唯一键，用于去重（默认不去重）
   */
  keySelector?: (item: T) => string | number | undefined
}

/**
 * 基于 denque 的轻量存储，支持头/尾批量插入与窗口导出。
 * - 全量保存在内部队列，窗口导出用于渲染。
 */
export class ChunkedMessageStore<T> {
  private queue: Denque<T>
  private readonly windowSize: number
  private readonly dedupe: boolean
  private readonly keySelector?: (item: T) => string | number | undefined
  private readonly keySet?: Set<string | number>

  constructor(options: ChunkedStoreOptions<T>) {
    this.windowSize = options.windowSize
    this.dedupe = options.dedupe ?? false
    this.keySelector = options.keySelector
    this.queue = new Denque<T>()
    this.keySet = this.dedupe ? new Set() : undefined
  }

  clear(): void {
    this.queue = new Denque<T>()
    if (this.keySet) this.keySet.clear()
  }

  pushBatch(items: T[]): void {
    for (const item of items) {
      if (this.shouldSkip(item)) continue
      this.queue.push(item)
      this.maybeAddKey(item)
    }
    this.trimIfNeeded()
  }

  prependBatch(items: T[]): void {
    // 头插时倒序确保原有顺序一致
    for (let i = items.length - 1; i >= 0; i -= 1) {
      const item = items[i]
      if (this.shouldSkip(item)) continue
      this.queue.unshift(item)
      this.maybeAddKey(item)
    }
    this.trimIfNeededFromHead()
  }

  getWindow(windowSize = this.windowSize): T[] {
    const size = Math.min(windowSize, this.queue.length)
    if (size === this.queue.length) {
      return this.queue.toArray()
    }
    return this.queue.toArray().slice(this.queue.length - size)
  }

  size(): number {
    return this.queue.length
  }

  private shouldSkip(item: T): boolean {
    if (!this.keySet || !this.keySelector) return false
    const key = this.keySelector(item)
    if (key === undefined) return false
    return this.keySet.has(key)
  }

  private maybeAddKey(item: T): void {
    if (!this.keySet || !this.keySelector) return
    const key = this.keySelector(item)
    if (key === undefined) return
    this.keySet.add(key)
  }

  private trimIfNeeded(): void {
    while (this.queue.length > this.windowSize) {
      const removed = this.queue.shift()
      this.removeKey(removed)
    }
  }

  private trimIfNeededFromHead(): void {
    while (this.queue.length > this.windowSize) {
      const removed = this.queue.pop()
      this.removeKey(removed)
    }
  }

  private removeKey(item: T | undefined): void {
    if (!item || !this.keySet || !this.keySelector) return
    const key = this.keySelector(item)
    if (key !== undefined) {
      this.keySet.delete(key)
    }
  }
}
