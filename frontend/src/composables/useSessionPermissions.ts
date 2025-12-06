/**
 * 权限和用户问题处理 Composable
 *
 * 每个 Tab 实例独立持有自己的权限状态和待回答问题
 * 提供权限请求处理、用户问题处理、会话级权限规则管理
 */

import { reactive, computed } from 'vue'
import type {
  PendingPermissionRequest,
  PendingUserQuestion,
  PermissionResponse,
  SessionPermissionRule,
  PermissionBehavior,
  PermissionUpdate
} from '@/types/permission'
import { loggers } from '@/utils/logger'

const log = loggers.session

/**
 * 权限和用户问题处理 Composable
 *
 * 设计原则：
 * - 每个 Tab 实例独立管理自己的权限和问题
 * - 不依赖任何外部 sessionId 或 store
 * - 提供 Promise 风格的异步处理接口
 */
export function useSessionPermissions() {
  // ========== 待处理请求 ==========

  /**
   * 待处理的权限请求 Map
   * key: permissionId
   * value: PendingPermissionRequest
   */
  const pendingPermissions = reactive(new Map<string, PendingPermissionRequest>())

  /**
   * 待回答的用户问题 Map
   * key: questionId
   * value: PendingUserQuestion
   */
  const pendingQuestions = reactive(new Map<string, PendingUserQuestion>())

  // ========== 会话级权限规则 ==========

  /**
   * 会话级权限规则列表
   * 用于快速判断工具调用是否需要授权
   */
  const permissionRules = reactive<SessionPermissionRule[]>([])

  /**
   * 允许的目录列表
   * 某些工具在这些目录下操作时自动放行
   */
  const permissionDirectories = reactive<string[]>([])

  // ========== 计算属性 ==========

  /**
   * 是否有待处理的权限请求
   */
  const hasPendingPermissions = computed(() => pendingPermissions.size > 0)

  /**
   * 是否有待回答的问题
   */
  const hasPendingQuestions = computed(() => pendingQuestions.size > 0)

  /**
   * 待处理权限请求列表
   */
  const pendingPermissionList = computed(() => Array.from(pendingPermissions.values()))

  /**
   * 待回答问题列表
   */
  const pendingQuestionList = computed(() => Array.from(pendingQuestions.values()))

  // ========== 权限请求处理 ==========

  /**
   * 添加权限请求
   *
   * 当收到后端的 RequestPermission RPC 调用时调用
   *
   * @param request 权限请求
   */
  function addPermissionRequest(request: Omit<PendingPermissionRequest, 'createdAt'>): void {
    const fullRequest: PendingPermissionRequest = {
      ...request,
      createdAt: Date.now()
    }
    pendingPermissions.set(request.id, fullRequest)
    log.info(`[useSessionPermissions] 添加权限请求: ${request.id} (${request.toolName})`)
  }

  /**
   * 响应权限请求
   *
   * @param permissionId 权限请求 ID
   * @param response 权限响应
   * @returns 是否成功处理
   */
  function respondPermission(permissionId: string, response: PermissionResponse): boolean {
    const request = pendingPermissions.get(permissionId)
    if (!request) {
      log.warn(`[useSessionPermissions] 权限请求 ${permissionId} 不存在`)
      return false
    }

    // 如果用户选择了权限建议，应用到会话规则中
    if (response.approved && response.permissionUpdates) {
      applyPermissionUpdates(response.permissionUpdates)
    }

    // 调用 resolve 回调
    request.resolve(response)

    // 从 pending 中移除
    pendingPermissions.delete(permissionId)

    log.info(`[useSessionPermissions] 权限请求 ${permissionId} 已响应: approved=${response.approved}`)
    return true
  }

  /**
   * 取消权限请求
   *
   * @param permissionId 权限请求 ID
   * @param reason 取消原因
   */
  function cancelPermission(permissionId: string, reason?: string): boolean {
    const request = pendingPermissions.get(permissionId)
    if (!request) {
      return false
    }

    request.reject(new Error(reason || 'Permission request cancelled'))
    pendingPermissions.delete(permissionId)

    log.info(`[useSessionPermissions] 权限请求 ${permissionId} 已取消: ${reason}`)
    return true
  }

  /**
   * 根据工具调用 ID 获取权限请求
   *
   * @param toolCallId 工具调用 ID
   */
  function getPermissionForToolCall(toolCallId: string): PendingPermissionRequest | undefined {
    for (const request of pendingPermissions.values()) {
      if (request.matchedToolCallId === toolCallId) {
        return request
      }
    }
    return undefined
  }

  // ========== 用户问题处理 ==========

  /**
   * 添加用户问题
   *
   * 当收到后端的 AskUserQuestion RPC 调用时调用
   *
   * @param question 用户问题
   */
  function addUserQuestion(question: Omit<PendingUserQuestion, 'createdAt'>): void {
    const fullQuestion: PendingUserQuestion = {
      ...question,
      createdAt: Date.now()
    }
    pendingQuestions.set(question.id, fullQuestion)
    log.info(`[useSessionPermissions] 添加用户问题: ${question.id}`)
  }

  /**
   * 回答问题
   *
   * @param questionId 问题 ID
   * @param answers 用户回答 { [header]: selectedOption }
   * @returns 是否成功处理
   */
  function answerQuestion(questionId: string, answers: Record<string, string>): boolean {
    const question = pendingQuestions.get(questionId)
    if (!question) {
      log.warn(`[useSessionPermissions] 问题 ${questionId} 不存在`)
      return false
    }

    // 调用 resolve 回调
    question.resolve(answers)

    // 从 pending 中移除
    pendingQuestions.delete(questionId)

    log.info(`[useSessionPermissions] 问题 ${questionId} 已回答`)
    return true
  }

  /**
   * 取消问题
   *
   * @param questionId 问题 ID
   * @param reason 取消原因
   */
  function cancelQuestion(questionId: string, reason?: string): boolean {
    const question = pendingQuestions.get(questionId)
    if (!question) {
      return false
    }

    question.reject(new Error(reason || 'Question cancelled'))
    pendingQuestions.delete(questionId)

    log.info(`[useSessionPermissions] 问题 ${questionId} 已取消: ${reason}`)
    return true
  }

  // ========== 会话级权限规则管理 ==========

  /**
   * 添加会话权限规则
   *
   * @param rule 权限规则
   */
  function addSessionPermissionRule(rule: SessionPermissionRule): void {
    // 检查是否已存在相同工具和规则内容的规则
    const existingIndex = permissionRules.findIndex(
      r => r.toolName === rule.toolName && r.ruleContent === rule.ruleContent
    )

    if (existingIndex >= 0) {
      // 更新已存在的规则
      permissionRules[existingIndex] = rule
      log.debug(`[useSessionPermissions] 更新权限规则: ${rule.toolName}`)
    } else {
      // 添加新规则
      permissionRules.push(rule)
      log.debug(`[useSessionPermissions] 添加权限规则: ${rule.toolName}`)
    }
  }

  /**
   * 移除会话权限规则
   *
   * @param toolName 工具名称
   * @param ruleContent 规则内容（可选，不提供则移除该工具的所有规则）
   */
  function removeSessionPermissionRule(toolName: string, ruleContent?: string): number {
    const initialLength = permissionRules.length
    const filtered = permissionRules.filter(r => {
      if (ruleContent) {
        return !(r.toolName === toolName && r.ruleContent === ruleContent)
      }
      return r.toolName !== toolName
    })

    permissionRules.splice(0, permissionRules.length, ...filtered)
    const removed = initialLength - permissionRules.length
    log.debug(`[useSessionPermissions] 移除了 ${removed} 条权限规则`)
    return removed
  }

  /**
   * 检查会话权限
   *
   * 根据会话级规则判断工具调用是否被允许/拒绝
   *
   * @param toolName 工具名称
   * @param input 工具输入参数
   * @returns 权限行为（allow/deny/ask）
   */
  function checkSessionPermission(toolName: string, input?: Record<string, any>): PermissionBehavior {
    // 遍历规则，找到匹配的规则
    for (const rule of permissionRules) {
      if (rule.toolName === toolName) {
        // 如果有 ruleContent，需要进一步匹配
        if (rule.ruleContent) {
          // 简单实现：检查输入参数中是否包含规则内容
          // 实际实现可能需要更复杂的匹配逻辑
          const inputStr = JSON.stringify(input || {})
          if (inputStr.includes(rule.ruleContent)) {
            return rule.behavior
          }
        } else {
          // 没有 ruleContent，直接应用规则
          return rule.behavior
        }
      }
    }

    // 默认需要询问
    return 'ask'
  }

  /**
   * 获取会话权限规则列表
   */
  function getSessionPermissionRules(): SessionPermissionRule[] {
    return [...permissionRules]
  }

  // ========== 目录权限管理 ==========

  /**
   * 添加允许的目录
   */
  function addPermissionDirectory(directory: string): void {
    if (!permissionDirectories.includes(directory)) {
      permissionDirectories.push(directory)
      log.debug(`[useSessionPermissions] 添加允许目录: ${directory}`)
    }
  }

  /**
   * 移除允许的目录
   */
  function removePermissionDirectory(directory: string): boolean {
    const index = permissionDirectories.indexOf(directory)
    if (index >= 0) {
      permissionDirectories.splice(index, 1)
      log.debug(`[useSessionPermissions] 移除允许目录: ${directory}`)
      return true
    }
    return false
  }

  /**
   * 检查路径是否在允许的目录中
   */
  function isPathAllowed(filePath: string): boolean {
    return permissionDirectories.some(dir => filePath.startsWith(dir))
  }

  // ========== 辅助方法 ==========

  /**
   * 应用权限更新
   *
   * 根据 PermissionUpdate 列表更新会话级权限规则
   */
  function applyPermissionUpdates(updates: PermissionUpdate[]): void {
    for (const update of updates) {
      // 只处理 session 目标的更新
      if (update.destination && update.destination !== 'session') {
        continue
      }

      switch (update.type) {
        case 'addRules':
          if (update.rules && update.behavior) {
            for (const rule of update.rules) {
              addSessionPermissionRule({
                toolName: rule.toolName,
                ruleContent: rule.ruleContent,
                behavior: update.behavior
              })
            }
          }
          break

        case 'removeRules':
          if (update.rules) {
            for (const rule of update.rules) {
              removeSessionPermissionRule(rule.toolName, rule.ruleContent)
            }
          }
          break

        case 'addDirectories':
          if (update.directories) {
            for (const dir of update.directories) {
              addPermissionDirectory(dir)
            }
          }
          break

        case 'removeDirectories':
          if (update.directories) {
            for (const dir of update.directories) {
              removePermissionDirectory(dir)
            }
          }
          break
      }
    }
  }

  /**
   * 取消所有待处理的权限请求
   */
  function cancelAllPermissions(reason?: string): number {
    const count = pendingPermissions.size
    for (const [id] of pendingPermissions) {
      cancelPermission(id, reason)
    }
    return count
  }

  /**
   * 取消所有待回答的问题
   */
  function cancelAllQuestions(reason?: string): number {
    const count = pendingQuestions.size
    for (const [id] of pendingQuestions) {
      cancelQuestion(id, reason)
    }
    return count
  }

  /**
   * 重置所有状态
   */
  function reset(): void {
    // 取消所有待处理请求
    cancelAllPermissions('Session reset')
    cancelAllQuestions('Session reset')

    // 清空规则和目录
    permissionRules.splice(0, permissionRules.length)
    permissionDirectories.splice(0, permissionDirectories.length)

    log.debug('[useSessionPermissions] 状态已重置')
  }

  // ========== 导出 ==========

  return {
    // 响应式状态
    pendingPermissions,
    pendingQuestions,
    permissionRules,
    permissionDirectories,

    // 计算属性
    hasPendingPermissions,
    hasPendingQuestions,
    pendingPermissionList,
    pendingQuestionList,

    // 权限请求方法
    addPermissionRequest,
    respondPermission,
    cancelPermission,
    getPermissionForToolCall,

    // 用户问题方法
    addUserQuestion,
    answerQuestion,
    cancelQuestion,

    // 会话权限规则方法
    addSessionPermissionRule,
    removeSessionPermissionRule,
    checkSessionPermission,
    getSessionPermissionRules,

    // 目录权限方法
    addPermissionDirectory,
    removePermissionDirectory,
    isPathAllowed,

    // 管理方法
    applyPermissionUpdates,
    cancelAllPermissions,
    cancelAllQuestions,
    reset
  }
}

/**
 * useSessionPermissions 返回类型
 */
export type SessionPermissionsInstance = ReturnType<typeof useSessionPermissions>
