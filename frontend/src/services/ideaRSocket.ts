/**
 * IDE integration RSocket service (IDEA compatibility layer).
 *
 * Uses RSocket + Protobuf to communicate with the backend.
 */

import { RSocketClient } from './rsocket/RSocketClient'
import { resolveServerHttpUrl } from '@/utils/serverUrl'
import { withServerTokenInUrl } from '@/utils/serverAuth'
import type {
  OpenFileRequest,
  ShowDiffRequest,
  ShowMultiEditDiffRequest,
  ShowEditPreviewRequest,
  ShowEditFullDiffRequest,
  ShowMarkdownRequest
} from './ideaApi'

// Types
export {
  RollbackStatus,
  TerminalBackgroundStatus,
  type BatchRollbackItem,
  type BatchRollbackEvent,
  type TerminalBackgroundItem,
  type TerminalBackgroundEvent,
  type BackgroundableTerminal,
  type IdeTheme,
  type SessionCommand,
  type SessionSummary,
  type SessionState,
  type TerminalTaskUpdate,
  type ActiveFileInfo,
  type IdeSettings,
  type ThinkingLevelConfig,
  type OptionConfig,
  type ThemeChangeHandler,
  type SessionCommandHandler,
  type SettingsChangeHandler,
  type ActiveFileChangeHandler,
  type TerminalTaskUpdateHandler
} from './ideaTypes'

import {
  RollbackStatus,
  TerminalBackgroundStatus,
  type IdeTheme,
  type SessionCommand,
  type SessionState,
  type TerminalTaskUpdate,
  type ActiveFileInfo,
  type IdeSettings,
  type BatchRollbackItem,
  type BatchRollbackEvent,
  type TerminalBackgroundItem,
  type TerminalBackgroundEvent,
  type BackgroundableTerminal,
  type ThemeChangeHandler,
  type SessionCommandHandler,
  type SettingsChangeHandler,
  type ActiveFileChangeHandler,
  type TerminalTaskUpdateHandler
} from './ideaTypes'

// Protobuf codec
import {
  encodeOpenFileRequest,
  encodeShowDiffRequest,
  encodeShowMultiEditDiffRequest,
  encodeShowEditPreviewRequest,
  encodeShowMarkdownRequest,
  encodeShowEditFullDiffRequest,
  encodeSetLocaleRequest,
  encodeGetFileHistoryContentRequest,
  encodeRollbackFileRequest,
  encodeSessionState,
  encodeBatchRollbackRequest,
  encodeTerminalBackgroundRequest,
  decodeOperationResponse,
  decodeThemeResponse,
  decodeLocaleResponse,
  decodeProjectPathResponse,
  decodeGetOriginalContentResponse,
  decodeGetFileHistoryContentResponse,
  decodeRollbackFileResponse,
  decodeActiveFileResponse,
  decodeSettingsResponse,
  decodeBatchRollbackEvent,
  decodeTerminalBackgroundEvent,
  decodeGetBackgroundableTerminalsResponse
} from './ideaProtoCodec'

/**
 * 映射 protoCodec 的 SessionCommandParams.type 到 SessionCommand.type
 */
function mapSessionCommandType(type: string): SessionCommand['type'] {
  switch (type) {
    case 'switch': return 'switch'
    case 'create': return 'create'
    case 'close': return 'close'
    case 'rename': return 'rename'
    case 'toggleHistory': return 'toggleHistory'
    case 'setLocale': return 'setLocale'
    case 'delete': return 'delete'
    case 'reset': return 'reset'
    default:
      console.warn(`[IdeaRSocket] Unknown session command type: ${type}`)
      return type as SessionCommand['type'] // 保持原值，避免错误转换
  }
}

// ========== RSocket 服务 ==========

// 连接状态类型
export type ConnectionState = 'disconnected' | 'connecting' | 'connected'

// 连接状态变化处理器
export type ConnectionStateHandler = (state: ConnectionState, error?: Error) => void

class IdeaRSocketService {
  private client: RSocketClient | null = null
  private themeChangeHandlers: ThemeChangeHandler[] = []
  private sessionCommandHandlers: SessionCommandHandler[] = []
  private settingsChangeHandlers: SettingsChangeHandler[] = []
  private activeFileChangeHandlers: ActiveFileChangeHandler[] = []
  private terminalTaskUpdateHandlers: TerminalTaskUpdateHandler[] = []
  private connectionStateHandlers: ConnectionStateHandler[] = []
  private connected = false
  private disconnectUnsubscribe: (() => void) | null = null

  /**
   * 连接到 IDE integration RSocket 端点
   *
   * 约束：不做“无限重试/自动回退”。连接失败应直接暴露问题，避免前端刷屏与隐性降级。
   */
  async connect(): Promise<boolean> {
    if (this.connected) return true
    this.notifyConnectionState('connecting')
    return await this.tryConnect()
  }

  /**
   * 尝试单次连接
   */
  private async tryConnect(): Promise<boolean> {
      try {
        const httpUrl = resolveServerHttpUrl()
        const wsUrl = withServerTokenInUrl(httpUrl.replace(/^http/, 'ws') + '/ide-rsocket')

        this.client = new RSocketClient({ url: wsUrl })

      // 注册 ServerCall handler（统一 Protobuf 格式）
      // 后端通过 client.call 路由发送，RSocketClient 解码后按 method 分发
      this.client.registerHandler('onThemeChanged', async (params: any) => {
        console.log('[IdeaRSocket] 收到主题变化推送 (Protobuf)')
        // params 已经是 ThemeChangedParams 类型
        const theme: IdeTheme = {
          background: params.background,
          foreground: params.foreground,
          borderColor: params.borderColor,
          panelBackground: params.panelBackground,
          textFieldBackground: params.textFieldBackground,
          selectionBackground: params.selectionBackground,
          selectionForeground: params.selectionForeground,
          linkColor: params.linkColor,
          errorColor: params.errorColor,
          warningColor: params.warningColor,
          successColor: params.successColor,
          separatorColor: params.separatorColor,
          hoverBackground: params.hoverBackground,
          accentColor: params.accentColor,
          infoBackground: params.infoBackground,
          codeBackground: params.codeBackground,
          secondaryForeground: params.secondaryForeground,
          fontFamily: params.fontFamily,
          fontSize: params.fontSize,
          editorFontFamily: params.editorFontFamily,
          editorFontSize: params.editorFontSize
        }
        this.themeChangeHandlers.forEach(h => h(theme))
        return {} // 返回空响应
      })

      this.client.registerHandler('onSessionCommand', async (params: any) => {
        console.log('[IdeaRSocket] 收到会话命令推送 (Protobuf)')
        // params 已经是 SessionCommandParams 类型
        const command: SessionCommand = {
          type: mapSessionCommandType(params.type),
          sessionId: params.sessionId,
          newName: params.newName,
          locale: params.locale
        }
        console.log('[IdeaRSocket] 会话命令:', command)
        this.sessionCommandHandlers.forEach(h => h(command))
        return {} // 返回空响应
      })

      this.client.registerHandler('onSettingsChanged', async (params: any) => {
        console.log('[IdeaRSocket] 收到设置变更推送 (Protobuf)')
        // params 已由 protoCodec 解码，包含 { settings: IdeSettings }
        const settingsData = params.settings || params

        const settings: IdeSettings = {
          defaultModelId: settingsData.defaultModelId || '',
          defaultModelName: settingsData.defaultModelName || '',
          defaultBypassPermissions: settingsData.defaultBypassPermissions ?? false,
          claudeDefaultAutoCleanupContexts: settingsData.claudeDefaultAutoCleanupContexts ?? true,
          codexDefaultAutoCleanupContexts: settingsData.codexDefaultAutoCleanupContexts ?? true,
          enableUserInteractionMcp: settingsData.enableUserInteractionMcp ?? true,
          enableJetbrainsMcp: settingsData.enableJetbrainsMcp ?? true,
          includePartialMessages: settingsData.includePartialMessages ?? true,
          codexDefaultModelId: settingsData.codexDefaultModelId || undefined,
          codexDefaultReasoningEffort: settingsData.codexDefaultReasoningEffort || undefined,
          codexDefaultReasoningSummary: settingsData.codexDefaultReasoningSummary || undefined,
          codexDefaultSandboxMode: settingsData.codexDefaultSandboxMode || undefined,
          defaultThinkingLevel: settingsData.defaultThinkingLevel || 'ULTRA',
          defaultThinkingTokens: settingsData.defaultThinkingTokens,
          defaultThinkingLevelId: settingsData.defaultThinkingLevelId || 'ultra',
          thinkingLevels: settingsData.thinkingLevels || [
            { id: 'off', name: 'Off', tokens: 0, isCustom: false },
            { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
            { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
          ],
          permissionMode: settingsData.permissionMode || 'default',
          // 配置选项列表
          codexReasoningEffortOptions: settingsData.codexReasoningEffortOptions || [],
          codexReasoningSummaryOptions: settingsData.codexReasoningSummaryOptions || [],
          codexSandboxModeOptions: settingsData.codexSandboxModeOptions || [],
          permissionModeOptions: settingsData.permissionModeOptions || []
        }
        console.log('[IdeaRSocket] 设置变更:', settings)
        this.settingsChangeHandlers.forEach(h => h(settings))
        return {} // 返回空响应
      })

      this.client.registerHandler('onActiveFileChanged', async (params: any) => {
        console.log('[IdeaRSocket] 收到活跃文件变更推送 (Protobuf)')
        // params 是 ActiveFileChangedNotify 类型
        let activeFile: ActiveFileInfo | null = null
        if (params.hasActiveFile) {
          activeFile = {
            path: params.path || '',
            relativePath: params.relativePath || '',
            name: params.name || '',
            line: params.line || undefined,
            column: params.column || undefined,
            hasSelection: params.hasSelection || false,
            startLine: params.startLine || undefined,
            startColumn: params.startColumn || undefined,
            endLine: params.endLine || undefined,
            endColumn: params.endColumn || undefined,
            selectedContent: params.selectedContent || undefined
          }
          console.log('[IdeaRSocket] 活跃文件:', activeFile.relativePath,
            activeFile.hasSelection ? `(selection: ${activeFile.startLine}:${activeFile.startColumn} - ${activeFile.endLine}:${activeFile.endColumn}, content: ${activeFile.selectedContent?.substring(0, 50)}...)` : '')
        } else {
          console.log('[IdeaRSocket] 无活跃文件')
        }
        this.activeFileChangeHandlers.forEach(h => h(activeFile))
        return {} // 返回空响应
      })

      // 处理终端任务更新推送
      this.client.registerHandler('onTerminalTaskUpdate', async (params: any) => {
        console.log('[IdeaRSocket] 收到终端任务更新推送 (Protobuf)')
        
        // 将 action 枚举值转换为字符串
        let actionStr: 'started' | 'completed' | 'backgrounded' = 'started'
        if (params.action === 0 || params.action === 'TERMINAL_TASK_STARTED') {
          actionStr = 'started'
        } else if (params.action === 1 || params.action === 'TERMINAL_TASK_COMPLETED') {
          actionStr = 'completed'
        } else if (params.action === 2 || params.action === 'TERMINAL_TASK_BACKGROUNDED') {
          actionStr = 'backgrounded'
        }

        const update: TerminalTaskUpdate = {
          toolUseId: params.toolUseId || '',
          sessionId: params.sessionId || '',
          action: actionStr,
          command: params.command || '',
          isBackground: params.isBackground ?? false,
          startTime: typeof params.startTime === 'number' ? params.startTime : 0,
          elapsedMs: params.elapsedMs
        }
        
        console.log('[IdeaRSocket] 终端任务更新:', update.action, update.toolUseId)
        this.terminalTaskUpdateHandlers.forEach(h => h(update))
        return {} // 返回空响应
      })

      await this.client.connect()
      this.connected = true
      this.notifyConnectionState('connected')
      console.log('[IdeaRSocket] Connected')

      // 订阅断开事件，实现自动重连
      this.disconnectUnsubscribe = this.client.onDisconnect((error?: Error) => {
        console.warn('[IdeaRSocket] 连接断开', error ? `原因: ${error.message}` : '')
        this.connected = false
        this.client = null
        this.disconnectUnsubscribe = null
        this.notifyConnectionState('disconnected', error)
      })

      return true
    } catch (error) {
      // 清理失败的 client
      if (this.client) {
        try {
          this.client.disconnect()
        } catch (_) { /* ignore */ }
        this.client = null
      }
      throw error
    }
  }

  /**
   * 通知连接状态变化
   */
  private notifyConnectionState(state: ConnectionState, error?: Error): void {
    this.connectionStateHandlers.forEach(handler => {
      try {
        handler(state, error)
      } catch (e) {
        console.error('[IdeaRSocket] 连接状态回调执行失败:', e)
      }
    })
  }

  /**
   * 断开连接（主动断开，不会触发自动重连）
   */
  disconnect(): void {
    // 取消断开事件订阅
    if (this.disconnectUnsubscribe) {
      this.disconnectUnsubscribe()
      this.disconnectUnsubscribe = null
    }

    if (this.client) {
      this.client.disconnect()
      this.client = null
      this.connected = false
      this.notifyConnectionState('disconnected')
      console.log('[IdeaRSocket] Disconnected')
    }
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.connected
  }

  // ========== 前端 → 后端 调用 ==========

  /**
   * 打开文件
   */
  async openFile(request: OpenFileRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeOpenFileRequest(request)
      const response = await this.client.requestResponse('ide.openFile', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Opened file:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to open file:', error)
      return false
    }
  }

  /**
   * 显示 Diff
   */
  async showDiff(request: ShowDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowDiffRequest(request)
      const response = await this.client.requestResponse('ide.showDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Showing diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to show diff:', error)
      return false
    }
  }

  /**
   * 显示多编辑 Diff
   */
  async showMultiEditDiff(request: ShowMultiEditDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowMultiEditDiffRequest(request)
      const response = await this.client.requestResponse('ide.showMultiEditDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Showing multi-edit diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to show multi-edit diff:', error)
      return false
    }
  }

  /**
   * 显示编辑预览 Diff（权限请求时使用）
   */
  async showEditPreviewDiff(request: ShowEditPreviewRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowEditPreviewRequest(request)
      const response = await this.client.requestResponse('ide.showEditPreviewDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Showing edit preview diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to show edit preview diff:', error)
      return false
    }
  }

  /**
   * 显示 Markdown 内容（计划预览）
   */
  async showMarkdown(request: ShowMarkdownRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowMarkdownRequest(request)
      const response = await this.client.requestResponse('ide.showMarkdown', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Showing markdown:', request.title || 'Plan Preview')
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to show markdown:', error)
      return false
    }
  }

  /**
   * 显示完整文件 Diff（修改前后对比）
   */
  async showEditFullDiff(request: ShowEditFullDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowEditFullDiffRequest(request)
      const response = await this.client.requestResponse('ide.showEditFullDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Showing edit full diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to show edit full diff:', error)
      return false
    }
  }

  /**
   * 获取主题
   */
  async getTheme(): Promise<IdeTheme | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('ide.getTheme', new Uint8Array())
      return decodeThemeResponse(response)
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get theme:', error)
      return null
    }
  }

  /**
   * 获取 IDE 设置
   */
  async getSettings(): Promise<IdeSettings | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('ide.getSettings', new Uint8Array())
      return decodeSettingsResponse(response)
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get settings:', error)
      return null
    }
  }

  /**
   * 获取语言设置
   */
  async getLocale(): Promise<string> {
    if (!this.client) return 'en-US'

    try {
      const response = await this.client.requestResponse('ide.getLocale', new Uint8Array())
      return decodeLocaleResponse(response)
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get locale:', error)
      return 'en-US'
    }
  }

  /**
   * 设置语言
   */
  async setLocale(locale: string): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeSetLocaleRequest(locale)
      const response = await this.client.requestResponse('ide.setLocale', data)
      const result = decodeOperationResponse(response)
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to set locale:', error)
      return false
    }
  }

  /**
   * 获取项目路径
   */
  async getProjectPath(): Promise<string | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('ide.getProjectPath', new Uint8Array())
      return decodeProjectPathResponse(response)
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get project path:', error)
      return null
    }
  }

  /**
   * 获取当前活跃文件
   */
  async getActiveFile(): Promise<ActiveFileInfo | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('ide.getActiveFile', new Uint8Array())
      return decodeActiveFileResponse(response)
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get active file:', error)
      return null
    }
  }

  /**
   * 获取文件修改前的原始内容
   * 基于 LocalHistory Label 机制
   */
  async getOriginalContent(toolUseId: string): Promise<string | null> {
    if (!this.client) return null

    try {
      // 直接发送 toolUseId 字符串作为请求数据
      const data = new TextEncoder().encode(toolUseId)
      const response = await this.client.requestResponse('ide.getOriginalContent', data)
      const result = decodeGetOriginalContentResponse(response)
      if (result.success && result.found) {
        console.log('[IdeaRSocket] Got original content for:', toolUseId)
        return result.content || null
      }
      if (!result.found) {
        console.log('[IdeaRSocket] Original content not found for:', toolUseId)
      }
      if (result.error) {
        console.warn('[IdeaRSocket] Error getting original content:', result.error)
      }
      return null
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get original content:', error)
      return null
    }
  }

  /**
   * 获取文件历史内容（基于时间戳查询 LocalHistory）
   * 用于历史会话加载时的 Diff 显示
   *
   * @param filePath 文件绝对路径
   * @param beforeTimestamp 时间戳（毫秒），获取此时间之前的版本
   * @returns 历史文件内容，如果不存在返回 null
   */
  async getFileHistoryContent(filePath: string, beforeTimestamp: number): Promise<string | null> {
    if (!this.client) return null

    try {
      const data = encodeGetFileHistoryContentRequest(filePath, beforeTimestamp)
      const response = await this.client.requestResponse('ide.getFileHistoryContent', data)
      const result = decodeGetFileHistoryContentResponse(response)
      if (result.success && result.found) {
        console.log('[IdeaRSocket] Got file history content for:', filePath, 'before:', beforeTimestamp)
        return result.content || null
      }
      if (!result.found) {
        console.log('[IdeaRSocket] File history content not found for:', filePath)
      }
      if (result.error) {
        console.warn('[IdeaRSocket] Error getting file history content:', result.error)
      }
      return null
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get file history content:', error)
      return null
    }
  }

  /**
   * 回滚文件到指定时间戳之前的版本
   * 使用 LocalHistory API 恢复文件内容
   *
   * @param filePath 文件绝对路径
   * @param beforeTimestamp 时间戳（毫秒），回滚到此时间之前的版本
   * @returns 回滚结果，包含是否成功和错误信息
   */
  async rollbackFile(filePath: string, beforeTimestamp: number): Promise<{ success: boolean; error?: string }> {
    if (!this.client) {
      return { success: false, error: 'RSocket client not connected' }
    }

    try {
      const data = encodeRollbackFileRequest(filePath, beforeTimestamp)
      const response = await this.client.requestResponse('ide.rollbackFile', data)
      const result = decodeRollbackFileResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Rolled back file:', filePath, 'to before:', beforeTimestamp)
      } else {
        console.warn('[IdeaRSocket] Rollback failed:', result.error)
      }
      return result
    } catch (error) {
      console.error('[IdeaRSocket] Failed to rollback file:', error)
      return { success: false, error: String(error) }
    }
  }

  /**
   * 批量回滚文件（流式返回结果）
   * 用于"回滚所有"功能，实时返回每个文件的回滚状态
   *
   * @param items 回滚项列表
   * @param onEvent 事件回调（每个文件的状态变化）
   * @returns 取消函数
   */
  batchRollback(
    items: BatchRollbackItem[],
    onEvent: (event: BatchRollbackEvent) => void,
    onComplete?: () => void,
    onError?: (error: Error) => void
  ): () => void {
    if (!this.client) {
      onError?.(new Error('RSocket client not connected'))
      return () => {}
    }

    console.log('[IdeaRSocket] Starting batch rollback:', items.length, 'items')
    const data = encodeBatchRollbackRequest(items)

    return this.client.requestStream(
      'ide.batchRollback',
      data,
      {
        onNext: (responseData: Uint8Array) => {
          try {
            const event = decodeBatchRollbackEvent(responseData, RollbackStatus)
            console.log('[IdeaRSocket] Rollback event:', event.toolUseId, RollbackStatus[event.status])
            onEvent(event)
          } catch (e) {
            console.error('[IdeaRSocket] Failed to decode rollback event:', e)
          }
        },
        onComplete: () => {
          console.log('[IdeaRSocket] Batch rollback completed')
          onComplete?.()
        },
        onError: (error: Error) => {
          console.error('[IdeaRSocket] Batch rollback error:', error)
          onError?.(error)
        }
      }
    )
  }

  // ========== Terminal 后台执行 ==========

  /**
   * 获取可后台的终端任务
   */
  async getBackgroundableTerminals(): Promise<BackgroundableTerminal[]> {
    if (!this.client) return []

    try {
      // 发送空请求（使用当前会话）
      const response = await this.client.requestResponse('ide.getBackgroundableTerminals', new Uint8Array())
      return decodeGetBackgroundableTerminalsResponse(response)
    } catch (error) {
      console.error('[IdeaRSocket] Failed to get backgroundable terminals:', error)
      return []
    }
  }

  /**
   * 批量后台终端任务（流式返回结果）
   * 
   * @param items 后台项列表
   * @param onEvent 事件回调（每个任务的状态变化）
   * @returns 取消函数
   */
  terminalBackground(
    items: TerminalBackgroundItem[],
    onEvent: (event: TerminalBackgroundEvent) => void,
    onComplete?: () => void,
    onError?: (error: Error) => void
  ): () => void {
    if (!this.client) {
      onError?.(new Error('RSocket client not connected'))
      return () => {}
    }

    console.log('[IdeaRSocket] Starting terminal background:', items.length, 'items')
    const data = encodeTerminalBackgroundRequest(items)

    return this.client.requestStream(
      'ide.terminalBackground',
      data,
      {
        onNext: (responseData: Uint8Array) => {
          try {
            const event = decodeTerminalBackgroundEvent(responseData, TerminalBackgroundStatus)
            console.log('[IdeaRSocket] Terminal background event:', event.toolUseId, TerminalBackgroundStatus[event.status])
            onEvent(event)
          } catch (e) {
            console.error('[IdeaRSocket] Failed to decode terminal background event:', e)
          }
        },
        onComplete: () => {
          console.log('[IdeaRSocket] Terminal background completed')
          onComplete?.()
        },
        onError: (error: Error) => {
          console.error('[IdeaRSocket] Terminal background error:', error)
          onError?.(error)
        }
      }
    )
  }

  /**
   * 上报会话状态到后端
   */
  async reportSessionState(state: SessionState): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeSessionState(state)
      const response = await this.client.requestResponse('ide.reportSessionState', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[IdeaRSocket] Reported session state:', state.sessions.length, 'sessions')
      }
      return result.success
    } catch (error) {
      console.error('[IdeaRSocket] Failed to report session state:', error)
      return false
    }
  }

  // ========== 后端 → 前端 事件监听 ==========

  /**
   * 添加主题变化监听器
   */
  onThemeChange(handler: ThemeChangeHandler): () => void {
    this.themeChangeHandlers.push(handler)
    return () => {
      const index = this.themeChangeHandlers.indexOf(handler)
      if (index >= 0) this.themeChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加会话命令监听器
   */
  onSessionCommand(handler: SessionCommandHandler): () => void {
    this.sessionCommandHandlers.push(handler)
    return () => {
      const index = this.sessionCommandHandlers.indexOf(handler)
      if (index >= 0) this.sessionCommandHandlers.splice(index, 1)
    }
  }

  /**
   * 添加设置变更监听器
   */
  onSettingsChange(handler: SettingsChangeHandler): () => void {
    this.settingsChangeHandlers.push(handler)
    return () => {
      const index = this.settingsChangeHandlers.indexOf(handler)
      if (index >= 0) this.settingsChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加活跃文件变更监听器
   */
  onActiveFileChange(handler: ActiveFileChangeHandler): () => void {
    this.activeFileChangeHandlers.push(handler)
    return () => {
      const index = this.activeFileChangeHandlers.indexOf(handler)
      if (index >= 0) this.activeFileChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加终端任务更新监听器
   */
  onTerminalTaskUpdate(handler: TerminalTaskUpdateHandler): () => void {
    this.terminalTaskUpdateHandlers.push(handler)
    return () => {
      const index = this.terminalTaskUpdateHandlers.indexOf(handler)
      if (index >= 0) this.terminalTaskUpdateHandlers.splice(index, 1)
    }
  }

  /**
   * 添加连接状态变化监听器
   * @param handler 状态变化处理器
   * @returns 取消订阅函数
   */
  onConnectionStateChange(handler: ConnectionStateHandler): () => void {
    this.connectionStateHandlers.push(handler)
    return () => {
      const index = this.connectionStateHandlers.indexOf(handler)
      if (index >= 0) this.connectionStateHandlers.splice(index, 1)
    }
  }

  /**
   * 获取当前连接状态
   */
  getConnectionState(): ConnectionState {
    if (this.connected) return 'connected'
    return 'disconnected'
  }
}

// ========== 单例导出 ==========

export const ideaRSocket = new IdeaRSocketService()
