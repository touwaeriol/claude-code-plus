/**
 * 工具展示拦截器
 * 类似 Spring Boot 的 HandlerInterceptor 模式
 *
 * 在 JetBrains IDE 环境中，拦截工具点击事件，调用 IDEA 原生功能
 * 通过 HTTP API 与后端通信
 */

import {
  jetbrainsBridge,
  isIdeEnvironment,
  type OpenFileRequest,
  type ShowDiffRequest,
  type ShowMultiEditDiffRequest
} from './jetbrainsApi'

// ====== 工具输入类型定义 ======

export interface ReadToolInput {
  file_path?: string
  path?: string
  offset?: number
  limit?: number
}

export interface WriteToolInput {
  file_path?: string
  path?: string
  content?: string
}

export interface EditToolInput {
  file_path?: string
  path?: string
  old_string?: string
  new_string?: string
  replace_all?: boolean
}

export interface MultiEditToolInput {
  file_path?: string
  path?: string
  edits?: Array<{
    old_string?: string
    new_string?: string
    replace_all?: boolean
  }>
}

// ====== 泛型上下文和处理器 ======

export interface ToolShowContext<TInput = Record<string, unknown>> {
  toolType: string
  input: TInput
  result?: {
    content?: string | unknown[]
    is_error?: boolean
  }
}

export interface ToolShowApi {
  openFile: (payload: OpenFileRequest) => void
  showDiff: (payload: ShowDiffRequest) => void
  showMultiEditDiff: (payload: ShowMultiEditDiffRequest) => void
}

/**
 * 泛型 Handler 类型
 * TInput 指定工具输入的具体类型
 */
export type ToolShowHandler<TInput = Record<string, unknown>> = (
  context: ToolShowContext<TInput>,
  api: ToolShowApi
) => void

class ToolShowInterceptorService {
  private handlers = new Map<string, ToolShowHandler<unknown>>()
  private initialized = false

  /**
   * 检测拦截器是否可用
   * 只有在 IDE 插件内运行且后端支持时才可用
   * 浏览器环境下不可用（看不到 IDEA 界面，直接展示卡片）
   */
  isAvailable(): boolean {
    return isIdeEnvironment() && jetbrainsBridge.isEnabled()
  }

  /**
   * 检测拦截器是否已初始化
   */
  isInitialized(): boolean {
    return this.initialized
  }

  /**
   * 注册 toolType 对应的 handler（泛型版本）
   * @example
   * interceptor.register<ReadToolInput>('Read', (ctx, api) => {
   *   // ctx.input 类型为 ReadToolInput
   *   api.openFile({ filePath: ctx.input.file_path || ctx.input.path })
   * })
   */
  register<TInput = Record<string, unknown>>(
    toolType: string,
    handler: ToolShowHandler<TInput>
  ): void {
    this.handlers.set(toolType, handler as ToolShowHandler<unknown>)
  }

  /**
   * 批量注册（无泛型，用于快速注册）
   */
  registerAll(handlers: Record<string, ToolShowHandler<unknown>>): void {
    Object.entries(handlers).forEach(([toolType, handler]) => {
      this.handlers.set(toolType, handler)
    })
  }

  /**
   * 拦截工具点击
   * @returns true = 已拦截（IDEA 处理），false = 放行（前端展开）
   */
  intercept(context: ToolShowContext): boolean {
    console.log('[ToolShowInterceptor] intercept called:', {
      toolType: context.toolType,
      isAvailable: this.isAvailable(),
      hasHandler: this.handlers.has(context.toolType),
      initialized: this.initialized,
      handlersCount: this.handlers.size,
      isError: context.result?.is_error
    })

    // 如果工具执行失败，不拦截，让前端展开显示错误信息
    if (context.result?.is_error) {
      console.log('[ToolShowInterceptor] Tool execution failed, passing through to show error')
      return false
    }

    if (!this.isAvailable()) {
      console.log('[ToolShowInterceptor] Not available, passing through')
      return false // 非 IDE 环境，放行
    }

    const handler = this.handlers.get(context.toolType)
    if (!handler) {
      console.log(`[ToolShowInterceptor] No handler for ${context.toolType}, passing through`)
      return false // 没有注册 handler，放行
    }

    // 创建 API 适配器，调用 jetbrainsBridge
    const api: ToolShowApi = {
      openFile: (payload) => jetbrainsBridge.openFile(payload),
      showDiff: (payload) => jetbrainsBridge.showDiff(payload),
      showMultiEditDiff: (payload) => jetbrainsBridge.showMultiEditDiff(payload)
    }

    try {
      console.log(`[ToolShowInterceptor] Calling handler for ${context.toolType}`)
      handler(context, api)
      return true // 拦截成功
    } catch (error) {
      console.error(`[ToolShowInterceptor] Handler error for ${context.toolType}:`, error)
      return false // 出错时放行
    }
  }

  /**
   * 初始化默认 handlers
   */
  init(): void {
    if (this.initialized) {
      console.log('[ToolShowInterceptor] Already initialized, skipping')
      return
    }
    this.registerDefaultHandlers()
    this.initialized = true
    console.log('✅ ToolShowInterceptor initialized with handlers:', Array.from(this.handlers.keys()))
  }

  private registerDefaultHandlers(): void {
    // Read 工具：打开文件并选中（使用泛型获得类型提示）
    this.register<ReadToolInput>('Read', (ctx, api) => {
      api.openFile({
        filePath: ctx.input.file_path || ctx.input.path || '',
        line: ctx.input.offset ? undefined : 1,
        startOffset: ctx.input.offset,
        // endOffset = offset + limit - 1（例如 offset=600, limit=38 → 600-637行）
        endOffset:
          ctx.input.offset && ctx.input.limit ? ctx.input.offset + ctx.input.limit - 1 : undefined
      })
    })

    // Write 工具：打开文件
    this.register<WriteToolInput>('Write', (ctx, api) => {
      api.openFile({
        filePath: ctx.input.file_path || ctx.input.path || ''
      })
    })

    // Edit 工具：显示 Diff
    this.register<EditToolInput>('Edit', (ctx, api) => {
      const filePath = ctx.input.file_path || ctx.input.path || ''
      api.showDiff({
        filePath,
        oldContent: ctx.input.old_string || '',
        newContent: ctx.input.new_string || '',
        title: `Edit: ${filePath}`
      })
    })

    // MultiEdit 工具：显示多处编辑 Diff
    this.register<MultiEditToolInput>('MultiEdit', (ctx, api) => {
      api.showMultiEditDiff({
        filePath: ctx.input.file_path || ctx.input.path || '',
        edits: (ctx.input.edits || []).map((edit) => ({
          oldString: edit.old_string || '',
          newString: edit.new_string || '',
          replaceAll: edit.replace_all || false
        }))
      })
    })
  }
}

// 单例导出
export const toolShowInterceptor = new ToolShowInterceptorService()

/**
 * 初始化工具展示拦截器
 * 仅在 IDE 插件内且后端支持时初始化
 */
export function initToolShowInterceptor(): void {
  if (isIdeEnvironment() && jetbrainsBridge.isEnabled()) {
    toolShowInterceptor.init()
    console.log('[ToolShowInterceptor] Initialized in IDE plugin mode')
  } else {
    console.log('[ToolShowInterceptor] Skipped - browser mode or no JetBrains bridge')
  }
}
