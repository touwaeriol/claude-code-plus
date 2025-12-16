/**
 * 会话统计 Composable
 *
 * 每个 Tab 实例独立持有自己的请求统计信息
 * 提供 Token 使用量、请求耗时等统计功能
 */

import { ref, computed, reactive } from 'vue'
import type { RequestStats } from '@/types/display'
import { loggers } from '@/utils/logger'

const log = loggers.session

/**
 * 请求追踪信息
 */
export interface RequestTrackerInfo {
  /** 最后一条用户消息 ID */
  lastUserMessageId: string
  /** 请求开始时间 */
  requestStartTime: number
  /** 输入 tokens */
  inputTokens: number
  /** 输出 tokens */
  outputTokens: number
  /** 当前正在流式输出的消息 ID */
  currentStreamingMessageId: string | null
}

/**
 * 累计统计信息
 */
export interface CumulativeStats {
  /** 总请求数 */
  totalRequests: number
  /** 总输入 tokens */
  totalInputTokens: number
  /** 总输出 tokens */
  totalOutputTokens: number
  /** 总耗时（毫秒） */
  totalDuration: number
  /** 成功请求数 */
  successfulRequests: number
  /** 失败请求数 */
  failedRequests: number
}

/**
 * 最后一条消息的 usage（用于计算完整上下文大小）
 */
export interface LastMessageUsage {
  inputTokens: number
  outputTokens: number
  cachedInputTokens: number  // 保留兼容，等于 cacheCreationTokens + cacheReadTokens
  cacheCreationTokens: number  // 新创建到缓存的 tokens
  cacheReadTokens: number      // 从缓存读取的 tokens
}

/**
 * 会话统计 Composable
 *
 * 设计原则：
 * - 每个 Tab 实例独立管理自己的统计数据
 * - 支持单次请求追踪和累计统计
 * - 纯响应式数据管理
 */
export function useSessionStats() {
  // ========== 当前请求追踪 ==========

  /**
   * 当前请求追踪器
   * 用于追踪正在进行的请求
   */
  const requestTracker = ref<RequestTrackerInfo | null>(null)

  // ========== 累计统计 ==========

  /**
   * 累计统计信息
   */
  const cumulativeStats = reactive<CumulativeStats>({
    totalRequests: 0,
    totalInputTokens: 0,
    totalOutputTokens: 0,
    totalDuration: 0,
    successfulRequests: 0,
    failedRequests: 0
  })

  /**
   * 最后一条消息的 usage（用于计算完整上下文大小）
   * 每次收到消息时更新，保存最新的 usage 数据
   */
  const lastMessageUsage = ref<LastMessageUsage>({
    inputTokens: 0,
    outputTokens: 0,
    cachedInputTokens: 0,
    cacheCreationTokens: 0,
    cacheReadTokens: 0
  })

  /**
   * 流式内容版本号
   * 每次有内容增量更新时递增，用于触发自动滚动
   */
  const streamingContentVersion = ref(0)

  // ========== 计算属性 ==========

  /**
   * 是否有正在进行的请求
   */
  const hasActiveRequest = computed(() => requestTracker.value !== null)

  /**
   * 当前请求耗时（毫秒）
   */
  const currentRequestDuration = computed(() => {
    if (!requestTracker.value) return 0
    return Date.now() - requestTracker.value.requestStartTime
  })

  /**
   * 平均请求耗时
   */
  const averageRequestDuration = computed(() => {
    if (cumulativeStats.totalRequests === 0) return 0
    return Math.round(cumulativeStats.totalDuration / cumulativeStats.totalRequests)
  })

  /**
   * 总 tokens 使用量
   */
  const totalTokens = computed(() => {
    return cumulativeStats.totalInputTokens + cumulativeStats.totalOutputTokens
  })

  // ========== 核心方法 ==========

  /**
   * 开始追踪请求
   *
   * 在发送用户消息时调用
   *
   * @param userMessageId 用户消息 ID
   */
  function startRequestTracking(userMessageId: string): void {
    requestTracker.value = {
      lastUserMessageId: userMessageId,
      requestStartTime: Date.now(),
      inputTokens: 0,
      outputTokens: 0,
      currentStreamingMessageId: null
    }

    cumulativeStats.totalRequests++
    log.debug(`[useSessionStats] 开始追踪请求: ${userMessageId}`)
  }

  /**
   * 设置当前流式消息 ID
   *
   * @param messageId 流式消息 ID
   */
  function setStreamingMessageId(messageId: string | null): void {
    if (requestTracker.value) {
      requestTracker.value.currentStreamingMessageId = messageId
    }
  }

  /**
   * 添加 Token 使用量
   *
   * 在收到 result 消息时调用
   *
   * @param inputTokens 输入 tokens
   * @param outputTokens 输出 tokens
   * @param cacheCreationTokens 新创建到缓存的 tokens
   * @param cacheReadTokens 从缓存读取的 tokens
   */
  function addTokenUsage(
    inputTokens: number,
    outputTokens: number,
    cacheCreationTokens: number = 0,
    cacheReadTokens: number = 0
  ): void {
    if (requestTracker.value) {
      requestTracker.value.inputTokens += inputTokens
      requestTracker.value.outputTokens += outputTokens
    }

    // 累加到累计统计
    cumulativeStats.totalInputTokens += inputTokens
    cumulativeStats.totalOutputTokens += outputTokens

    // 计算总缓存（保留兼容）
    const cachedInputTokens = cacheCreationTokens + cacheReadTokens

    // 更新最后一条消息的 usage（用于完整上下文大小计算）
    lastMessageUsage.value = {
      inputTokens,
      outputTokens,
      cachedInputTokens,
      cacheCreationTokens,
      cacheReadTokens
    }

    log.debug(`[useSessionStats] 添加 Token 使用: input=${inputTokens}, output=${outputTokens}, cacheCreation=${cacheCreationTokens}, cacheRead=${cacheReadTokens}`)
  }

  /**
   * 递增流式内容版本号
   * 每次有内容增量更新（如 thinking_delta、text_delta）时调用
   * 用于触发自动滚动
   */
  function incrementContentVersion(): void {
    streamingContentVersion.value++
  }

  /**
   * 获取当前请求统计信息
   *
   * 在请求完成时调用，返回统计信息并清除追踪器
   *
   * @param success 请求是否成功
   * @returns 请求统计信息
   */
  function getRequestStats(success: boolean = true): RequestStats | null {
    if (!requestTracker.value) {
      return null
    }

    const duration = Date.now() - requestTracker.value.requestStartTime
    const stats: RequestStats = {
      requestDuration: duration,
      inputTokens: requestTracker.value.inputTokens,
      outputTokens: requestTracker.value.outputTokens
    }

    // 更新累计统计
    cumulativeStats.totalDuration += duration
    if (success) {
      cumulativeStats.successfulRequests++
    } else {
      cumulativeStats.failedRequests++
    }

    log.debug(`[useSessionStats] 请求完成: duration=${duration}ms, tokens=${stats.inputTokens}+${stats.outputTokens}`)

    return stats
  }

  /**
   * 完成请求追踪
   *
   * 清除当前追踪器，返回统计信息
   *
   * @param success 请求是否成功
   * @returns 请求统计信息
   */
  function finishRequestTracking(success: boolean = true): RequestStats | null {
    const stats = getRequestStats(success)
    requestTracker.value = null
    return stats
  }

  /**
   * 取消请求追踪
   *
   * 在请求被中断或取消时调用
   */
  function cancelRequestTracking(): void {
    if (requestTracker.value) {
      cumulativeStats.failedRequests++
      const duration = Date.now() - requestTracker.value.requestStartTime
      cumulativeStats.totalDuration += duration
      log.debug(`[useSessionStats] 请求取消: duration=${duration}ms`)
    }
    requestTracker.value = null
  }

  /**
   * 获取当前追踪器信息（只读）
   */
  function getCurrentTracker(): RequestTrackerInfo | null {
    return requestTracker.value ? { ...requestTracker.value } : null
  }

  /**
   * 获取累计统计信息（只读）
   */
  function getCumulativeStats(): CumulativeStats {
    return { ...cumulativeStats }
  }

  /**
   * 获取最后一条消息的 usage（用于计算完整上下文大小）
   */
  function getLastMessageUsage(): LastMessageUsage {
    return { ...lastMessageUsage.value }
  }

  /**
   * 重置累计统计
   */
  function resetCumulativeStats(): void {
    cumulativeStats.totalRequests = 0
    cumulativeStats.totalInputTokens = 0
    cumulativeStats.totalOutputTokens = 0
    cumulativeStats.totalDuration = 0
    cumulativeStats.successfulRequests = 0
    cumulativeStats.failedRequests = 0
    lastMessageUsage.value = {
      inputTokens: 0,
      outputTokens: 0,
      cachedInputTokens: 0,
      cacheCreationTokens: 0,
      cacheReadTokens: 0
    }
    log.debug('[useSessionStats] 累计统计已重置')
  }

  /**
   * 重置所有状态
   */
  function reset(): void {
    requestTracker.value = null
    resetCumulativeStats()
    log.debug('[useSessionStats] 所有状态已重置')
  }

  // ========== 导出 ==========

  return {
    // 响应式状态
    requestTracker,
    cumulativeStats,
    streamingContentVersion,

    // 计算属性
    hasActiveRequest,
    currentRequestDuration,
    averageRequestDuration,
    totalTokens,

    // 追踪方法
    startRequestTracking,
    setStreamingMessageId,
    addTokenUsage,
    incrementContentVersion,
    getRequestStats,
    finishRequestTracking,
    cancelRequestTracking,

    // 查询方法
    getCurrentTracker,
    getCumulativeStats,
    getLastMessageUsage,

    // 管理方法
    resetCumulativeStats,
    reset
  }
}

/**
 * useSessionStats 返回类型
 */
export type SessionStatsInstance = ReturnType<typeof useSessionStats>
