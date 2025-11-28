/**
 * 操作队列 - 确保所有操作严格顺序执行
 *
 * 用于防止并发操作导致的状态混乱，如快速连续发送消息、生成中切换配置等
 */
export class OperationQueue {
  private queue: Promise<void> = Promise.resolve()
  private _isPending = false

  get isPending(): boolean {
    return this._isPending
  }

  /**
   * 将操作加入队列
   * @param operation 要执行的异步操作
   * @returns 操作结果的 Promise
   */
  async enqueue<T>(operation: () => Promise<T>): Promise<T> {
    let resolve: (value: T) => void
    let reject: (error: Error) => void

    const resultPromise = new Promise<T>((res, rej) => {
      resolve = res
      reject = rej
    })

    // 将新操作追加到队列
    this.queue = this.queue
      .then(async () => {
        this._isPending = true
        try {
          const result = await operation()
          resolve!(result)
        } catch (error) {
          reject!(error as Error)
        } finally {
          this._isPending = false
        }
      })
      .catch(() => {
        // 确保队列不会因为错误而中断
      })

    return resultPromise
  }

  /**
   * 清空队列（用于会话断开、中断操作等场景）
   */
  clear(): void {
    this.queue = Promise.resolve()
    this._isPending = false
  }
}
