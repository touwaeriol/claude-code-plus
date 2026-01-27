/**
 * 文件改动追踪 Composable
 *
 * 追踪当前会话中 JetBrains MCP 工具（WriteFile/EditFile）的文件修改，
 * 支持回滚功能。
 * 
 * 架构：直接回调机制
 * - 由外部在工具结果处理时调用 addFileEdit
 * - 不依赖事件订阅，避免时序问题
 */

import { ref, computed, watch, type Ref, type ComputedRef } from 'vue'
import type { DisplayItem, ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { ideaRSocket, RollbackStatus, type BatchRollbackItem, type BatchRollbackEvent } from '@/services/ideaRSocket'
import { useToastStore } from '@/stores/toastStore'
import { i18n } from '@/i18n'

// ============ 类型定义 ============

/**
 * JetBrains MCP 文件编辑工具名称
 */
const JETBRAINS_FILE_EDIT_TOOLS = [
  'mcp__ide-file__WriteFile',
  'mcp__ide-file__EditFile'
] as const

type JetBrainsFileEditToolName = typeof JETBRAINS_FILE_EDIT_TOOLS[number]

/**
 * 单次文件修改记录
 */
export interface FileModification {
  /** 工具调用 ID */
  toolUseId: string
  /** 本地历史时间戳（用于回滚），新建文件为 0 */
  historyTs: number
  /** 工具名称 */
  toolName: 'WriteFile' | 'EditFile'
  /** 修改摘要 */
  summary: string
  /** 文件路径 */
  filePath: string
  /** 是否已回滚 */
  rolledBack: boolean
  /** 是否已接受（接受后不再显示，无法回滚） */
  accepted: boolean
  /** 时间戳（显示用） */
  timestamp: number
  /** 是否是新建文件（回滚时删除文件） */
  isNewFile: boolean
  /** 增加的行数 */
  linesAdded?: number
  /** 删除的行数 */
  linesRemoved?: number
}

/**
 * 文件改动记录（包含该文件的所有修改）
 */
export interface FileChange {
  /** 文件路径 */
  filePath: string
  /** 文件名 */
  fileName: string
  /** 该文件的所有修改记录（按时间顺序） */
  modifications: FileModification[]
}

/**
 * 回滚结果
 */
export interface RollbackResult {
  success: boolean
  error?: string
}

// ============ 工具函数 ============

/**
 * 判断是否为 JetBrains 文件编辑工具
 */
export function isJetBrainsFileEditTool(toolName: string): toolName is JetBrainsFileEditToolName {
  return JETBRAINS_FILE_EDIT_TOOLS.includes(toolName as JetBrainsFileEditToolName)
}

/**
 * 从工具结果中提取的元信息
 */
export interface ToolResultMeta {
  historyTs: number | null
  canRollback: boolean
  isNewFile: boolean
  linesAdded?: number
  linesRemoved?: number
}

/**
 * 从工具结果中提取元信息（historyTs、canRollback、isNewFile、行数变化等）
 */
export function extractToolResultMeta(result: any): ToolResultMeta {
  const meta: ToolResultMeta = { historyTs: null, canRollback: false, isNewFile: false }
  
  if (!result?.content) return meta
  
  // 处理多种 content 格式：
  // 1. 字符串: "text content"
  // 2. 数组 (Claude): [{ type: "text", text: "..." }, ...]
  // 3. 单个对象 (Codex): { type: "text", text: "..." }
  const content = typeof result.content === 'string' 
    ? result.content 
    : Array.isArray(result.content) 
      ? result.content.map((c: any) => typeof c === 'string' ? c : c.text || '').join('')
      : (result.content?.text || '')
  
  // 匹配 [jb:historyTs=xxx] 格式
  const historyMatch = content.match(/\[jb:historyTs=(\d+)\]/)
  if (historyMatch) {
    meta.historyTs = parseInt(historyMatch[1], 10)
  }
  
  // 匹配 [jb:canRollback=xxx] 格式
  const rollbackMatch = content.match(/\[jb:canRollback=(true|false)\]/)
  if (rollbackMatch) {
    meta.canRollback = rollbackMatch[1] === 'true'
  }
  
  // 匹配 [jb:isNewFile=xxx] 格式
  const newFileMatch = content.match(/\[jb:isNewFile=(true|false)\]/)
  if (newFileMatch) {
    meta.isNewFile = newFileMatch[1] === 'true'
  }
  
  return meta
}

/**
 * 从工具结果中提取 historyTs（兼容旧代码）
 */
export function extractHistoryTs(result: any): number | null {
  return extractToolResultMeta(result).historyTs
}

/**
 * 从 JetBrains MCP 工具调用中提取文件路径（camelCase）
 */
function extractFilePath(toolCall: ToolCall): string | null {
  const input = toolCall.input as Record<string, any>
  return input?.filePath || null
}

/**
 * 生成修改摘要
 */
function generateSummary(toolCall: ToolCall): string {
  const input = toolCall.input as Record<string, any>
  const toolName = toolCall.toolName
  
  if (toolName === 'mcp__ide-file__EditFile') {
    const oldStr = input?.oldString || ''
    const newStr = input?.newString || ''
    const oldPreview = oldStr.length > 20 ? oldStr.slice(0, 20) + '...' : oldStr
    const newPreview = newStr.length > 20 ? newStr.slice(0, 20) + '...' : newStr
    return `"${oldPreview}" → "${newPreview}"`
  }
  
  if (toolName === 'mcp__ide-file__WriteFile') {
    const content = input?.content || ''
    const lines = content.split('\n').length
    return `${lines} lines`
  }
  
  return toolName
}

/**
 * 获取简短的工具名称
 */
function getShortToolName(toolName: string): 'WriteFile' | 'EditFile' {
  if (toolName === 'mcp__ide-file__WriteFile') return 'WriteFile'
  return 'EditFile'
}

/**
 * 计算行数变化
 */
function calculateLineChanges(toolCall: ToolCall): { added: number; removed: number } {
  const input = toolCall.input as Record<string, any>
  const toolName = toolCall.toolName
  
  if (toolName === 'mcp__ide-file__EditFile') {
    const oldStr = input?.oldString || ''
    const newStr = input?.newString || ''
    const oldLines = oldStr.split('\n').length
    const newLines = newStr.split('\n').length
    
    // 简化计算：新增行数 = max(0, newLines - oldLines)，删除行数 = max(0, oldLines - newLines)
    // 更准确的方式需要 diff 算法，这里用简化版
    if (newLines > oldLines) {
      return { added: newLines - oldLines, removed: 0 }
    } else if (oldLines > newLines) {
      return { added: 0, removed: oldLines - newLines }
    }
    // 行数相同但内容不同，标记为 1 行变化
    return { added: 1, removed: 1 }
  }
  
  if (toolName === 'mcp__ide-file__WriteFile') {
    const content = input?.content || ''
    const lines = content.split('\n').length
    // WriteFile 是覆盖写入，简化为全部是新增
    return { added: lines, removed: 0 }
  }
  
  return { added: 0, removed: 0 }
}

// ============ Composable ============

/**
 * 文件改动追踪 Composable
 * 
 * 重构：改为直接回调机制，由外部在工具结果处理时调用 addFileEdit
 * 
 * @param displayItems - 用于清理已删除的记录（shallow watch）
 */
export function useFileChanges(
  displayItems: Ref<DisplayItem[]> | ComputedRef<DisplayItem[]>
) {
  const toastStore = useToastStore()
  const t = i18n.global.t
  
  // 当前会话的文件修改记录（直接存储，不从 displayItems 计算）
  const fileEdits = ref<FileModification[]>([])
  
  // 正在回滚的文件集合
  const rollingBackFiles = ref<Set<string>>(new Set())
  
  // 正在批量回滚所有文件
  const isRollingBackAll = ref(false)
  
  // 正在回滚的 toolUseId 集合（用于显示单个工具的回滚状态）
  const rollingBackToolIds = ref<Set<string>>(new Set())
  
  // toolUseId -> FileModification 映射（用于快速查找）
  const fileEditMap = computed(() => 
    new Map(fileEdits.value.map(e => [e.toolUseId, e]))
  )
  
  /**
   * 计算属性：按文件分组的改动列表
   * 过滤掉已回滚和已接受的改动
   */
  const fileChanges = computed<FileChange[]>(() => {
    const map = new Map<string, FileChange>()
    
    for (const edit of fileEdits.value) {
      // 过滤掉已回滚和已接受的改动
      if (edit.rolledBack || edit.accepted) continue
      
      let fileChange = map.get(edit.filePath)
      if (!fileChange) {
        const fileName = edit.filePath.split(/[/\\]/).pop() || edit.filePath
        fileChange = {
          filePath: edit.filePath,
          fileName,
          modifications: []
        }
        map.set(edit.filePath, fileChange)
      }
      fileChange.modifications.push(edit)
    }
    
    // 按时间排序
    for (const fileChange of map.values()) {
      fileChange.modifications.sort((a, b) => a.timestamp - b.timestamp)
    }
    
    // 按最新修改时间排序文件列表
    return Array.from(map.values()).sort((a, b) => {
      const aLatest = Math.max(...a.modifications.map(m => m.timestamp))
      const bLatest = Math.max(...b.modifications.map(m => m.timestamp))
      return bLatest - aLatest
    })
  })
  
  /**
   * 计算属性：是否有未回滚的改动
   */
  const hasChanges = computed(() => fileChanges.value.length > 0)
  
  /**
   * 计算属性：总修改文件数
   */
  const changedFileCount = computed(() => fileChanges.value.length)
  
  /**
   * 计算属性：总编辑次数
   */
  const totalEditCount = computed(() => 
    fileEdits.value.filter(e => !e.rolledBack).length
  )
  
  /**
   * 添加文件修改记录（由外部在工具调用完成时调用）
   */
  function addFileEdit(toolCall: ToolCall): boolean {
    // 检查是否为 JetBrains 文件编辑工具
    if (!isJetBrainsFileEditTool(toolCall.toolName)) return false
    
    // 检查是否成功
    if (toolCall.status !== ToolCallStatus.SUCCESS) return false
    
    // 提取文件路径
    const filePath = extractFilePath(toolCall)
    if (!filePath) return false
    
    // 提取元信息（historyTs、canRollback、行数变化等）
    const meta = extractToolResultMeta(toolCall.result)
    
    // 检查是否支持回滚（项目外文件不支持）
    if (!meta.canRollback) {
      console.info('[useFileChanges] 文件不支持回滚（项目外文件），跳过:', toolCall.toolName)
      return false
    }
    
    // 新建文件不需要 historyTs（回滚时使用 0 表示删除文件）
    // 覆盖文件需要 historyTs
    if (!meta.isNewFile && !meta.historyTs) {
      console.warn('[useFileChanges] 无法提取 historyTs（非新建文件），跳过:', toolCall.toolName)
      return false
    }
    
    // 检查是否已存在（避免重复）
    if (fileEditMap.value.has(toolCall.id)) return false
    
    // 从工具输入计算行数变化
    const lineStats = calculateLineChanges(toolCall)
    
    // 添加记录（新建文件 historyTs 为 0，回滚时会删除文件）
    fileEdits.value.push({
      toolUseId: toolCall.id,
      historyTs: meta.isNewFile ? 0 : meta.historyTs!,
      toolName: getShortToolName(toolCall.toolName),
      summary: generateSummary(toolCall),
      filePath,
      rolledBack: false,
      accepted: false,
      timestamp: toolCall.timestamp,
      isNewFile: meta.isNewFile,
      linesAdded: lineStats.added,
      linesRemoved: lineStats.removed
    })
    
    return true
  }
  
  /**
   * 检查某个工具调用是否可以回滚
   */
  function canRollback(toolUseId: string): boolean {
    const edit = fileEditMap.value.get(toolUseId)
    return edit != null && !edit.rolledBack && !edit.accepted
  }
  
  // ============ 核心回滚方法 ============
  
  /**
   * 核心回滚方法（统一使用批量回滚 API）
   * 所有回滚操作都复用此方法，单个回滚 = items.length = 1
   * 
   * @param items 回滚项列表
   * @param isBatchAll 是否是"回滚所有"操作（控制 isRollingBackAll 状态）
   * @returns Promise 在所有回滚完成时 resolve
   */
  function executeRollback(
    items: BatchRollbackItem[],
    isBatchAll: boolean = false
  ): Promise<{ success: boolean; failedCount: number }> {
    return new Promise((resolve) => {
      if (items.length === 0) {
        resolve({ success: true, failedCount: 0 })
        return
      }
      
      // 设置回滚状态
      if (isBatchAll) {
        isRollingBackAll.value = true
      }
      
      // 标记所有待回滚的 toolUseId 和文件
      for (const item of items) {
        rollingBackToolIds.value.add(item.toolUseId)
        rollingBackFiles.value.add(item.filePath)
      }
      
      let failedCount = 0
      
      // 调用批量回滚 API（单个回滚也用这个，items.length = 1）
      ideaRSocket.batchRollback(
        items,
        // onEvent: 每个文件的状态变化
        (event: BatchRollbackEvent) => {
          const edit = fileEditMap.value.get(event.toolUseId)
          if (!edit) return
          
          switch (event.status) {
            case RollbackStatus.STARTED:
              console.log(`[useFileChanges] Rollback started: ${event.toolUseId}`)
              break
              
            case RollbackStatus.SUCCESS:
              console.log(`[useFileChanges] Rollback success: ${event.toolUseId}`)
              // 回滚成功后，标记该修改及同一文件的所有后续修改为已回滚
              // 因为回滚到某个版本后，该版本之后的所有修改都已失效
              for (const e of fileEdits.value) {
                if (e.filePath === edit.filePath && !e.rolledBack && !e.accepted && e.timestamp >= edit.timestamp) {
                  e.rolledBack = true
                }
              }
              rollingBackToolIds.value.delete(event.toolUseId)
              // 检查该文件是否还有正在回滚的修改
              const fileStillRolling = fileEdits.value.some(
                e => e.filePath === edit.filePath && rollingBackToolIds.value.has(e.toolUseId)
              )
              if (!fileStillRolling) {
                rollingBackFiles.value.delete(edit.filePath)
              }
              break
              
            case RollbackStatus.FAILED:
              console.error(`[useFileChanges] Rollback failed: ${event.toolUseId}`, event.error)
              failedCount++
              rollingBackToolIds.value.delete(event.toolUseId)
              // 立即通知用户该文件回滚失败
              const fileName = edit.filePath.split(/[/\\]/).pop() || edit.filePath
              toastStore.error(t('tools.rollbackFileFailed', { file: fileName, error: event.error || 'Unknown error' }))
              // 检查该文件是否还有正在回滚的修改
              const fileStillRolling2 = fileEdits.value.some(
                e => e.filePath === edit.filePath && rollingBackToolIds.value.has(e.toolUseId)
              )
              if (!fileStillRolling2) {
                rollingBackFiles.value.delete(edit.filePath)
              }
              break
          }
        },
        // onComplete: 所有回滚完成
        () => {
          if (isBatchAll) {
            isRollingBackAll.value = false
          }
          // 清理状态（只清理本次回滚涉及的）
          for (const item of items) {
            rollingBackToolIds.value.delete(item.toolUseId)
            rollingBackFiles.value.delete(item.filePath)
          }
          resolve({ success: failedCount === 0, failedCount })
        },
        // onError: 回滚出错
        (error: Error) => {
          console.error('[useFileChanges] Batch rollback error:', error)
          if (isBatchAll) {
            isRollingBackAll.value = false
          }
          // 清理状态
          for (const item of items) {
            rollingBackToolIds.value.delete(item.toolUseId)
            rollingBackFiles.value.delete(item.filePath)
          }
          resolve({ success: false, failedCount: -1 })
        }
      )
    })
  }
  
  // ============ 回滚操作方法（都复用 executeRollback） ============
  
  /**
   * 回滚单个修改（通过 filePath + historyTs）
   */
  async function rollbackModification(filePath: string, historyTs: number): Promise<RollbackResult> {
    // 找到对应的修改记录
    const edit = fileEdits.value.find(
      e => e.filePath === filePath && e.historyTs === historyTs && !e.rolledBack
    )
    
    if (!edit) {
      return { success: false, error: 'Modification not found' }
    }
    
    const items: BatchRollbackItem[] = [{
      filePath: edit.filePath,
      beforeTimestamp: edit.historyTs,
      toolUseId: edit.toolUseId
    }]
    
    const result = await executeRollback(items, false)
    return { success: result.success, error: result.failedCount > 0 ? 'Rollback failed' : undefined }
  }
  
  /**
   * 回滚整个文件（回滚该文件的所有未回滚修改）
   */
  async function rollbackFile(filePath: string): Promise<RollbackResult> {
    // 找到该文件所有未回滚的修改
    const fileModifications = fileEdits.value.filter(
      e => e.filePath === filePath && !e.rolledBack && !e.accepted
    )
    
    if (fileModifications.length === 0) {
      return { success: false, error: 'No modifications to rollback' }
    }
    
    const items: BatchRollbackItem[] = fileModifications.map(edit => ({
      filePath: edit.filePath,
      beforeTimestamp: edit.historyTs,
      toolUseId: edit.toolUseId
    }))
    
    const result = await executeRollback(items, false)
    return { success: result.success, error: result.failedCount > 0 ? `${result.failedCount} rollback(s) failed` : undefined }
  }
  
  /**
   * 检查文件是否正在回滚
   */
  function isRollingBack(filePath: string): boolean {
    return rollingBackFiles.value.has(filePath)
  }
  
  /**
   * 检查某个工具调用是否正在回滚
   */
  function isToolRollingBack(toolUseId: string): boolean {
    return rollingBackToolIds.value.has(toolUseId)
  }
  
  /**
   * 回滚所有文件（批量回滚）
   *
   * 对于每个文件，只回滚到最初版本：
   * - 如果文件是新建的（historyTs: 0），回滚后会删除文件
   * - 如果文件原本存在，回滚到最早的 historyTs
   */
  function rollbackAll(): Promise<{ success: boolean; failedCount: number }> {
    // 收集所有未回滚的修改
    const pendingEdits = fileEdits.value.filter(e => !e.rolledBack && !e.accepted)

    if (pendingEdits.length === 0) {
      return Promise.resolve({ success: true, failedCount: 0 })
    }

    // 按文件分组，找出每个文件的最早修改（即最初版本）
    const earliestByFile = new Map<string, FileModification>()
    for (const edit of pendingEdits) {
      const existing = earliestByFile.get(edit.filePath)
      // 选择 timestamp 最小的（最早的修改）
      if (!existing || edit.timestamp < existing.timestamp) {
        earliestByFile.set(edit.filePath, edit)
      }
    }

    // 只发送每个文件的最早修改记录
    const items: BatchRollbackItem[] = Array.from(earliestByFile.values()).map(edit => ({
      filePath: edit.filePath,
      beforeTimestamp: edit.historyTs,
      toolUseId: edit.toolUseId
    }))

    return executeRollback(items, true)
  }
  
  /**
   * 根据 toolUseId 执行回滚（用于工具卡片上的回滚按钮）
   */
  async function rollbackByToolUseId(toolUseId: string): Promise<RollbackResult> {
    const edit = fileEditMap.value.get(toolUseId)
    if (!edit) {
      return { success: false, error: 'Modification not found' }
    }
    
    const items: BatchRollbackItem[] = [{
      filePath: edit.filePath,
      beforeTimestamp: edit.historyTs,
      toolUseId: edit.toolUseId
    }]
    
    const result = await executeRollback(items, false)
    return { success: result.success, error: result.failedCount > 0 ? 'Rollback failed' : undefined }
  }
  
  /**
   * 获取指定 toolUseId 的修改信息
   */
  function getModificationByToolUseId(toolUseId: string): FileModification | null {
    return fileEditMap.value.get(toolUseId) || null
  }
  
  /**
   * 清空所有改动记录
   */
  function clear() {
    fileEdits.value = []
    rollingBackFiles.value = new Set()
  }
  
  /**
   * 接受单个修改及其之前的所有修改
   * 因为回滚是按时间顺序的，接受也需要保持时间顺序
   */
  function acceptModification(filePath: string, historyTs: number): void {
    for (const edit of fileEdits.value) {
      if (edit.filePath === filePath && edit.historyTs <= historyTs && !edit.rolledBack) {
        edit.accepted = true
      }
    }
  }
  
  /**
   * 接受整个文件的所有修改
   */
  function acceptFile(filePath: string): void {
    for (const edit of fileEdits.value) {
      if (edit.filePath === filePath && !edit.rolledBack) {
        edit.accepted = true
      }
    }
  }
  
  /**
   * 接受所有修改
   */
  function acceptAll(): void {
    for (const edit of fileEdits.value) {
      if (!edit.rolledBack) {
        edit.accepted = true
      }
    }
  }
  
  /**
   * 检查某个工具调用是否已被接受
   */
  function isAccepted(toolUseId: string): boolean {
    const edit = fileEditMap.value.get(toolUseId)
    return edit?.accepted ?? false
  }
  
  /**
   * 根据 toolUseId 接受修改
   */
  function acceptByToolUseId(toolUseId: string): void {
    const edit = fileEditMap.value.get(toolUseId)
    if (edit) {
      acceptModification(edit.filePath, edit.historyTs)
    }
  }
  
  // ========== 直接回调机制 ==========
  // 由外部在工具结果处理时直接调用 addFileEdit，不依赖事件订阅
  // 这样可以确保在正确的时机（流式处理期间）添加记录
  
  // ========== 清理机制：shallow watch 仅监听长度变化 ==========
  // 用于清理已删除的记录（用户编辑历史消息时）
  watch(
    () => displayItems.value.length,
    () => {
      // 收集当前有效的 toolUseId
      const validIds = new Set<string>()
      for (const item of displayItems.value) {
        if (item.displayType === 'toolCall') {
          validIds.add(item.id)
        }
      }
      
      // 过滤掉不存在的记录
      const before = fileEdits.value.length
      fileEdits.value = fileEdits.value.filter(e => validIds.has(e.toolUseId))
      
      if (fileEdits.value.length < before) {
        console.log(`[useFileChanges] Cleaned ${before - fileEdits.value.length} stale file edits`)
      }
    }
  )
  
  return {
    // 状态
    fileChanges,
    hasChanges,
    changedFileCount,
    totalEditCount,
    isRollingBackAll,
    
    // 回滚方法
    addFileEdit,
    canRollback,
    rollbackFile,
    rollbackModification,
    rollbackByToolUseId,
    rollbackAll,
    getModificationByToolUseId,
    isRollingBack,
    isToolRollingBack,
    clear,
    
    // 接受方法
    acceptModification,
    acceptFile,
    acceptAll,
    acceptByToolUseId,
    isAccepted
  }
}

/**
 * useFileChanges 返回类型
 */
export type FileChangesInstance = {
  // 状态
  fileChanges: ComputedRef<FileChange[]>
  hasChanges: ComputedRef<boolean>
  changedFileCount: ComputedRef<number>
  totalEditCount: ComputedRef<number>
  isRollingBackAll: Ref<boolean>

  // 回滚方法
  addFileEdit: (toolCall: ToolCall) => boolean
  canRollback: (toolUseId: string) => boolean
  rollbackFile: (filePath: string) => Promise<RollbackResult>
  rollbackModification: (filePath: string, historyTs: number) => Promise<RollbackResult>
  rollbackByToolUseId: (toolUseId: string) => Promise<RollbackResult>
  rollbackAll: () => Promise<{ success: boolean; failedCount: number }>
  getModificationByToolUseId: (toolUseId: string) => FileModification | null
  isRollingBack: (filePath: string) => boolean
  isToolRollingBack: (toolUseId: string) => boolean
  clear: () => void

  // 接受方法
  acceptModification: (filePath: string, historyTs: number) => void
  acceptFile: (filePath: string) => void
  acceptAll: () => void
  acceptByToolUseId: (toolUseId: string) => void
  isAccepted: (toolUseId: string) => boolean
}