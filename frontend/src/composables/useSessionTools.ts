/**
 * 工具调用管理 Composable
 *
 * 每个 Tab 实例独立持有自己的工具调用状态
 * 提供工具调用的注册、更新、查询功能
 */

import { reactive, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'
import { ToolCallStatus } from '@/types/display'
import type { ToolCall, ToolResult } from '@/types/display'
import { createToolCall as createToolCallDisplay } from '@/utils/displayItemConverter'
import { loggers } from '@/utils/logger'

const log = loggers.session

/**
 * 工具调用状态（向后兼容旧接口）
 */
export interface ToolCallState {
  id: string
  name: string
  status: 'running' | 'success' | 'failed'
  result?: any
  startTime: number
  endTime?: number
}

/**
 * 工具调用管理 Composable
 *
 * 设计原则：
 * - 每个 Tab 实例独立管理自己的工具调用
 * - 不依赖任何外部 sessionId 或 store
 * - 纯响应式数据管理
 */
export function useSessionTools() {
  // ========== 工具调用状态 ==========

  /**
   * 待处理的工具调用 Map
   * key: toolUseId (工具调用的唯一标识)
   * value: ToolCall (完整的工具调用信息)
   */
  const pendingToolCalls = reactive(new Map<string, ToolCall>())

  /**
   * 工具调用状态 Map（向后兼容）
   * 用于快速查询工具调用状态
   */
  const toolCallsMap = reactive(new Map<string, ToolCallState>())

  /**
   * 工具输入 JSON 累积器
   * 用于 input_json_delta 增量更新
   * key: toolUseId
   * value: 累积的 JSON 字符串
   */
  const toolInputJsonAccumulator = reactive(new Map<string, string>())

  // ========== 计算属性 ==========

  /**
   * 正在运行的工具调用列表
   */
  const runningToolCalls = computed(() => {
    return Array.from(pendingToolCalls.values())
      .filter(tc => tc.status === ToolCallStatus.RUNNING)
  })

  /**
   * 是否有工具调用正在运行
   */
  const hasRunningToolCalls = computed(() => runningToolCalls.value.length > 0)

  /**
   * 工具调用总数
   */
  const totalToolCalls = computed(() => pendingToolCalls.size)

  // ========== 核心方法 ==========

  /**
   * 注册工具调用
   *
   * 当收到 tool_use content_block_start 事件时调用
   * 初始化工具调用状态
   *
   * @param block tool_use 块
   */
  function registerToolCall(block: ToolUseBlock): ToolCall | null {
    // 如果已经注册过，跳过（避免重复注册导致状态被重置）
    if (pendingToolCalls.has(block.id)) {
      log.debug(`[useSessionTools] 工具调用 ${block.id} 已存在，跳过注册`)
      return pendingToolCalls.get(block.id) || null
    }

    // 创建 ToolCall 对象
    const toolCall = createToolCallDisplay(block as any, pendingToolCalls)

    // 添加到 pendingToolCalls
    pendingToolCalls.set(block.id, toolCall)

    // 向后兼容：同时更新 toolCallsMap
    toolCallsMap.set(block.id, {
      id: block.id,
      name: block.toolName,
      status: 'running',
      startTime: Date.now()
    })

    // 初始化 JSON 累积器
    toolInputJsonAccumulator.set(block.id, '')

    log.debug(`[useSessionTools] 注册工具调用: ${block.id} (${block.toolName})`)
    return toolCall
  }

  /**
   * 更新工具调用结果
   *
   * 当收到 tool_result 消息时调用
   *
   * @param toolUseId 工具调用 ID
   * @param result 工具结果
   */
  function updateToolResult(toolUseId: string, result: ToolResultBlock | ToolResult): boolean {
    const toolCall = pendingToolCalls.get(toolUseId)
    if (!toolCall) {
      log.warn(`[useSessionTools] 更新结果失败：工具调用 ${toolUseId} 不存在`)
      return false
    }

    // 更新状态
    const isError = 'is_error' in result ? result.is_error : false
    toolCall.status = isError ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
    toolCall.endTime = Date.now()
    toolCall.result = result as ToolResult

    // 向后兼容：同时更新 toolCallsMap
    const state = toolCallsMap.get(toolUseId)
    if (state) {
      state.status = isError ? 'failed' : 'success'
      state.result = result
      state.endTime = Date.now()
    }

    log.debug(`[useSessionTools] 更新工具结果: ${toolUseId}, 状态: ${toolCall.status}`)
    return true
  }

  /**
   * 更新工具调用的输入参数
   *
   * 当收到完整的 tool_use 块或 content_block_stop 事件时调用
   *
   * @param toolUseId 工具调用 ID
   * @param input 输入参数
   */
  function updateToolInput(toolUseId: string, input: Record<string, any>): boolean {
    const toolCall = pendingToolCalls.get(toolUseId)
    if (!toolCall) {
      log.warn(`[useSessionTools] 更新输入失败：工具调用 ${toolUseId} 不存在`)
      return false
    }

    toolCall.input = input
    log.debug(`[useSessionTools] 更新工具输入: ${toolUseId}`)
    return true
  }

  /**
   * 累加 JSON delta
   *
   * 用于处理 input_json_delta 增量更新
   *
   * @param toolUseId 工具调用 ID
   * @param delta JSON 片段
   * @returns 累积后的完整 JSON 字符串
   */
  function appendJsonDelta(toolUseId: string, delta: string): string {
    const current = toolInputJsonAccumulator.get(toolUseId) || ''
    const accumulated = current + delta
    toolInputJsonAccumulator.set(toolUseId, accumulated)
    return accumulated
  }

  /**
   * 获取累积的 JSON 字符串
   */
  function getAccumulatedJson(toolUseId: string): string {
    return toolInputJsonAccumulator.get(toolUseId) || ''
  }

  /**
   * 解析并应用累积的 JSON
   *
   * 在 content_block_stop 时调用，将累积的 JSON 解析为 input
   *
   * @param toolUseId 工具调用 ID
   * @returns 解析后的输入对象，解析失败返回 null
   */
  function parseAndApplyAccumulatedJson(toolUseId: string): Record<string, any> | null {
    const jsonStr = toolInputJsonAccumulator.get(toolUseId)
    if (!jsonStr) {
      return null
    }

    try {
      const input = JSON.parse(jsonStr)
      updateToolInput(toolUseId, input)
      return input
    } catch (error) {
      log.warn(`[useSessionTools] JSON 解析失败: ${toolUseId}, error:`, error)
      return null
    }
  }

  /**
   * 获取工具调用状态
   */
  function getToolStatus(toolUseId: string): ToolCallStatus | null {
    const toolCall = pendingToolCalls.get(toolUseId)
    return toolCall?.status ?? null
  }

  /**
   * 获取工具调用结果
   */
  function getToolResult(toolUseId: string): ToolResult | undefined {
    const toolCall = pendingToolCalls.get(toolUseId)
    return toolCall?.result
  }

  /**
   * 获取工具调用
   */
  function getToolCall(toolUseId: string): ToolCall | undefined {
    return pendingToolCalls.get(toolUseId)
  }

  /**
   * 检查工具调用是否存在
   */
  function hasToolCall(toolUseId: string): boolean {
    return pendingToolCalls.has(toolUseId)
  }

  /**
   * 清理已完成的工具调用
   *
   * 可选：保留最近 N 个或清理超过指定时间的
   */
  function cleanupCompletedToolCalls(maxAge?: number): number {
    let cleaned = 0
    const now = Date.now()
    const maxAgeMs = maxAge || 5 * 60 * 1000 // 默认 5 分钟

    for (const [id, toolCall] of pendingToolCalls) {
      if (
        toolCall.status !== ToolCallStatus.RUNNING &&
        toolCall.endTime &&
        now - toolCall.endTime > maxAgeMs
      ) {
        pendingToolCalls.delete(id)
        toolCallsMap.delete(id)
        toolInputJsonAccumulator.delete(id)
        cleaned++
      }
    }

    if (cleaned > 0) {
      log.debug(`[useSessionTools] 清理了 ${cleaned} 个已完成的工具调用`)
    }
    return cleaned
  }

  /**
   * 重置所有状态
   *
   * 在会话断开或重置时调用
   */
  function reset(): void {
    pendingToolCalls.clear()
    toolCallsMap.clear()
    toolInputJsonAccumulator.clear()
    log.debug('[useSessionTools] 状态已重置')
  }

  // ========== 导出 ==========

  return {
    // 响应式状态
    pendingToolCalls,
    toolCallsMap,
    toolInputJsonAccumulator,

    // 计算属性
    runningToolCalls,
    hasRunningToolCalls,
    totalToolCalls,

    // 核心方法
    registerToolCall,
    updateToolResult,
    updateToolInput,

    // JSON 累积器方法
    appendJsonDelta,
    getAccumulatedJson,
    parseAndApplyAccumulatedJson,

    // 查询方法
    getToolStatus,
    getToolResult,
    getToolCall,
    hasToolCall,

    // 管理方法
    cleanupCompletedToolCalls,
    reset
  }
}

/**
 * useSessionTools 返回类型
 */
export type SessionToolsInstance = ReturnType<typeof useSessionTools>
