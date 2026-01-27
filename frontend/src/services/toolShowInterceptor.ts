/**
 * 工具展示拦截器
 * 类似 Spring Boot 的 HandlerInterceptor 模式
 *
 * 在 JetBrains IDE 环境中，拦截工具点击事件，调用 IDEA 原生功能
 * 通过 RSocket 与后端通信
 */

import {
  ideaBridgeService,
  isIdeEnvironment,
  type OpenFileRequest,
  type ShowDiffRequest,
  type ShowMultiEditDiffRequest,
  type ShowEditFullDiffRequest
} from './ideaApi'
import { ideaRSocket } from './ideaRSocket'
import { parseJbMeta } from '@/utils/jbMetaParser'

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

// ====== JetBrains File MCP 工具输入类型（camelCase）======

export interface JetBrainsReadFileInput {
  filePath?: string
  maxLines?: number
  offset?: number
}

export interface JetBrainsWriteFileInput {
  filePath?: string
  content?: string
}

export interface JetBrainsEditFileInput {
  filePath?: string
  oldString?: string
  newString?: string
  replaceAll?: boolean
}

// ====== 泛型上下文和处理器 ======

export interface ToolShowContext<TInput = Record<string, unknown>> {
  toolType: string
  toolUseId?: string
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
  showEditFullDiff: (payload: ShowEditFullDiffRequest) => void
}

/**
 * 泛型 Handler 类型
 * TInput 指定工具输入的具体类型
 */
export type ToolShowHandler<TInput = Record<string, unknown>> = (
  context: ToolShowContext<TInput>,
  api: ToolShowApi
) => void | Promise<void>

class ToolShowInterceptorService {
  private handlers = new Map<string, ToolShowHandler<unknown>>()
  private initialized = false

  /**
   * 检测拦截器是否可用
   * 只有在 IDE 插件内运行且后端支持时才可用
   * 浏览器环境下不可用（看不到 IDEA 界面，直接展示卡片）
   */
  isAvailable(): boolean {
    return isIdeEnvironment() && ideaBridgeService.isEnabled()
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

    // 创建 API 适配器，调用 ideaBridgeService
    const api: ToolShowApi = {
      openFile: (payload) => ideaBridgeService.openFile(payload),
      showDiff: (payload) => ideaBridgeService.showDiff(payload),
      showMultiEditDiff: (payload) => ideaBridgeService.showMultiEditDiff(payload),
      showEditFullDiff: (payload) => ideaBridgeService.showEditFullDiff(payload)
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
    // ====== IDE File MCP 工具（camelCase 参数）======
    // 注：原生 Claude/Codex 工具（Read, Write, Edit, MultiEdit）不注册 hook
    // 浏览器环境下直接展开卡片显示内容，IDE 环境下也不调用 IDEA 打开文件

    // IDE ReadFile 工具：打开文件
    this.register<JetBrainsReadFileInput>('mcp__ide-file__ReadFile', (ctx, api) => {
      api.openFile({
        filePath: ctx.input.filePath || '',
        line: ctx.input.offset ? undefined : 1,
        startOffset: ctx.input.offset,
        endOffset:
          ctx.input.offset && ctx.input.maxLines ? ctx.input.offset + ctx.input.maxLines - 1 : undefined
      })
    })

    // IDE WriteFile 工具：显示 Diff（新建或覆写）
    this.register<JetBrainsWriteFileInput>('mcp__ide-file__WriteFile', async (ctx, api) => {
      const filePath = ctx.input.filePath || ''
      const newContent = ctx.input.content || ''

      // 从工具结果中解析 isOverwrite 和 historyTs 元数据
      let oldContent = ''
      const resultContent = this.extractResultContent(ctx.result)
      if (resultContent) {
        const { meta } = parseJbMeta(resultContent)

        if (meta.isOverwrite && meta.historyTs && ideaRSocket.isConnected()) {
          // 覆写文件：通过 RSocket 查询 LocalHistory 获取原始内容
          oldContent = await ideaRSocket.getFileHistoryContent(filePath, meta.historyTs) || ''
        }
        // 新建文件（isOverwrite=false）：oldContent 保持为空
      }

      api.showDiff({
        filePath,
        oldContent,
        newContent,
        title: oldContent ? `Overwrite: ${filePath}` : `New File: ${filePath}`
      })
    })

    // IDE EditFile 工具：显示完整文件 Diff
    this.register<JetBrainsEditFileInput>('mcp__ide-file__EditFile', async (ctx, api) => {
      const filePath = ctx.input.filePath || ''

      // 从工具结果中解析 historyTs 元数据
      let originalContent: string | undefined
      const resultContent = this.extractResultContent(ctx.result)
      if (resultContent) {
        const { meta } = parseJbMeta(resultContent)
        if (meta.historyTs && ideaRSocket.isConnected()) {
          // 通过 RSocket 查询 LocalHistory 获取历史内容
          originalContent = await ideaRSocket.getFileHistoryContent(filePath, meta.historyTs) || undefined
        }
      }

      api.showEditFullDiff({
        filePath,
        oldString: ctx.input.oldString || '',
        newString: ctx.input.newString || '',
        replaceAll: ctx.input.replaceAll || false,
        title: `Edit: ${filePath}`,
        originalContent
      })
    })
  }

  /**
   * 从工具结果中提取文本内容
   */
  private extractResultContent(result?: { content?: string | unknown[] }): string | null {
    if (!result?.content) return null
    if (typeof result.content === 'string') return result.content
    if (Array.isArray(result.content)) {
      // 查找 text 类型的 content block
      for (const block of result.content) {
        if (typeof block === 'object' && block !== null && 'type' in block) {
          const typedBlock = block as { type: string; text?: string }
          if (typedBlock.type === 'text' && typedBlock.text) {
            return typedBlock.text
          }
        }
      }
    }
    return null
  }
}

// 单例导出
export const toolShowInterceptor = new ToolShowInterceptorService()

/**
 * 初始化工具展示拦截器
 * 仅在 IDE 插件内且后端支持时初始化
 */
export function initToolShowInterceptor(): void {
  if (isIdeEnvironment() && ideaBridgeService.isEnabled()) {
    toolShowInterceptor.init()
    console.log('[ToolShowInterceptor] Initialized in IDE plugin mode')
  } else {
    console.log('[ToolShowInterceptor] Skipped - browser mode or no JetBrains bridge')
  }
}
