/**
 * 工具调用增强拦截器（AOP 机制）
 * 
 * 统一处理工具调用的 IDE 增强，避免每个工具组件都写判断逻辑
 * 
 * 设计思路：
 * 1. 定义工具增强规则（哪些工具需要增强、如何增强）
 * 2. 提供统一的拦截器接口
 * 3. 工具组件通过拦截器处理增强逻辑，而不是直接判断环境
 */

import { jcefBridge } from './jcefBridge'
import type { ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'

/**
 * 工具增强上下文
 */
export interface ToolEnhancementContext {
  /** 工具类型 */
  toolType: string
  /** 工具输入 */
  input: any
  /** 工具结果 */
  result?: ToolCall['result']
  /** 是否成功 */
  isSuccess: boolean
}

/**
 * 工具增强动作
 */
export interface ToolEnhancementAction {
  /** 动作类型 */
  type: 'openFile' | 'showDiff' | 'custom'
  /** 动作参数 */
  params?: any
  /** 自定义处理函数 */
  handler?: (context: ToolEnhancementContext) => Promise<void> | void
}

/**
 * 工具增强规则
 */
export interface ToolEnhancementRule {
  /** 工具类型（支持通配符） */
  toolType: string | string[]
  /** 条件：是否应该应用增强 */
  condition?: (context: ToolEnhancementContext) => boolean
  /** 增强动作 */
  action: ToolEnhancementAction
  /** 优先级（数字越大优先级越高） */
  priority?: number
}

/**
 * 工具增强拦截器
 */
class ToolEnhancementInterceptor {
  private rules: ToolEnhancementRule[] = []
  private initialized = false

  /**
   * 初始化拦截器，注册默认规则
   */
  async init() {
    if (this.initialized) {
      return
    }

    // 确保 JCEF 桥接已初始化
    await jcefBridge.init()

    // 注册默认规则
    this.registerDefaultRules()

    this.initialized = true
    console.log('✅ Tool Enhancement Interceptor initialized')
  }

  /**
   * 注册默认规则
   */
  private registerDefaultRules() {
    // Read 工具：成功时打开文件
    this.registerRule({
      toolType: [CLAUDE_TOOL_TYPE.READ, 'read', 'Read'],
      condition: (ctx) => ctx.isSuccess,
      action: {
        type: 'openFile',
        params: (ctx: ToolEnhancementContext) => ({
          filePath: ctx.input.path || ctx.input.file_path,
          line: ctx.input.view_range?.[0] || ctx.input.offset || 1,
          endLine: ctx.input.view_range?.[1] || (ctx.input.offset && ctx.input.limit 
            ? ctx.input.offset + ctx.input.limit - 1 
            : undefined),
          selectContent: true,
          content: this.extractResultContent(ctx.result)
        })
      },
      priority: 10
    })

    // Edit 工具：成功时显示 Diff
    this.registerRule({
      toolType: [CLAUDE_TOOL_TYPE.EDIT, 'edit', 'Edit'],
      condition: (ctx) => ctx.isSuccess,
      action: {
        type: 'showDiff',
        params: (ctx: ToolEnhancementContext) => ({
          filePath: ctx.input.file_path || ctx.input.path,
          oldContent: ctx.input.old_string || ctx.input.old_str || '',
          newContent: ctx.input.new_string || ctx.input.new_str || '',
          rebuildFromFile: true,
          edits: [{
            oldString: ctx.input.old_string || ctx.input.old_str || '',
            newString: ctx.input.new_string || ctx.input.new_str || '',
            replaceAll: ctx.input.replace_all || false
          }]
        })
      },
      priority: 10
    })

    // MultiEdit 工具：成功时显示 Diff
    this.registerRule({
      toolType: [CLAUDE_TOOL_TYPE.MULTI_EDIT, 'multi-edit', 'MultiEdit'],
      condition: (ctx) => ctx.isSuccess && ctx.input.edits?.length > 0,
      action: {
        type: 'showDiff',
        params: (ctx: ToolEnhancementContext) => {
          // 对于 MultiEdit，需要从文件重建完整内容
          return {
            filePath: ctx.input.file_path || ctx.input.path,
            oldContent: '', // 从文件重建
            newContent: '', // 从文件重建
            rebuildFromFile: true,
            edits: ctx.input.edits.map((edit: any) => ({
              oldString: edit.old_string || edit.old_str || '',
              newString: edit.new_string || edit.new_str || '',
              replaceAll: edit.replace_all || false
            }))
          }
        }
      },
      priority: 10
    })

    // Write 工具：成功时打开文件
    this.registerRule({
      toolType: [CLAUDE_TOOL_TYPE.WRITE, 'write', 'Write'],
      condition: (ctx) => ctx.isSuccess,
      action: {
        type: 'openFile',
        params: (ctx: ToolEnhancementContext) => ({
          filePath: ctx.input.path || ctx.input.file_path,
          line: 1
        })
      },
      priority: 10
    })
  }

  /**
   * 注册增强规则
   */
  registerRule(rule: ToolEnhancementRule) {
    this.rules.push(rule)
    // 按优先级排序（优先级高的在前）
    this.rules.sort((a, b) => (b.priority || 0) - (a.priority || 0))
  }

  /**
   * 移除增强规则
   */
  unregisterRule(rule: ToolEnhancementRule) {
    const index = this.rules.indexOf(rule)
    if (index > -1) {
      this.rules.splice(index, 1)
    }
  }

  /**
   * 拦截工具调用，应用增强
   */
  async intercept(
    toolCall: ToolCall,
    result?: ToolCall['result']
  ): Promise<ToolEnhancementAction | null> {
    if (!this.initialized) {
      await this.init()
    }

    // 检查是否在 JCEF 环境中
    if (!jcefBridge.isJcefAvailable()) {
      return null
    }

    const effectiveResult = result ?? toolCall.result

    const context: ToolEnhancementContext = {
      toolType: toolCall.toolType,
      input: toolCall.input,
      result: effectiveResult,
      isSuccess: toolCall.status === ToolCallStatus.SUCCESS
    }

    // 查找匹配的规则
    for (const rule of this.rules) {
      // 检查工具类型是否匹配
      const toolTypes = Array.isArray(rule.toolType) ? rule.toolType : [rule.toolType]
      if (!toolTypes.some(type => 
        type === context.toolType || 
        type.toLowerCase() === context.toolType.toLowerCase()
      )) {
        continue
      }

      // 检查条件
      if (rule.condition && !rule.condition(context)) {
        continue
      }

      // 找到匹配的规则，返回增强动作
      const action = { ...rule.action }
      
      // 如果 params 是函数，执行它获取参数
      if (typeof action.params === 'function') {
        action.params = action.params(context)
      }

      return action
    }

    return null
  }

  /**
   * 执行增强动作
   */
  async executeAction(action: ToolEnhancementAction, context: ToolEnhancementContext): Promise<void> {
    if (!jcefBridge.isJcefAvailable()) {
      console.warn('⚠️ JCEF not available, cannot execute enhancement action')
      return
    }

    const capabilities = jcefBridge.getCapabilities()

    try {
      switch (action.type) {
        case 'openFile': {
          const params = action.params || {}
          await capabilities.openFile(params.filePath, {
            line: params.line,
            endLine: params.endLine,
            column: params.column,
            selectContent: params.selectContent,
            content: params.content
          })
          break
        }

        case 'showDiff': {
          const params = action.params || {}
          await capabilities.showDiff({
            filePath: params.filePath,
            oldContent: params.oldContent,
            newContent: params.newContent,
            title: params.title,
            rebuildFromFile: params.rebuildFromFile,
            edits: params.edits
          })
          break
        }

        case 'custom': {
          if (action.handler) {
            await action.handler(context)
          }
          break
        }

        default:
          console.warn(`⚠️ Unknown enhancement action type: ${action.type}`)
      }
    } catch (error) {
      console.error(`❌ Failed to execute enhancement action:`, error)
    }
  }

  /**
   * 提取工具结果内容
   */
  private extractResultContent(result?: ToolCall['result']): string {
    if (!result) return ''

    // 使用后端格式：直接读取 content
    const content = result.content
    if (!content) {
      return ''
    }

    if (typeof content === 'string') {
      return content
    }

    if (Array.isArray(content)) {
      return (content as any[])
        .filter((item: any) => item.type === 'text')
        .map((item: any) => item.text)
        .join('\n')
    }

    return JSON.stringify(content, null, 2)
  }
}

// 单例
let _interceptor: ToolEnhancementInterceptor | null = null

function getInterceptor(): ToolEnhancementInterceptor {
  if (!_interceptor) {
    _interceptor = new ToolEnhancementInterceptor()
  }
  return _interceptor
}

// 导出单例访问器
export const toolEnhancement = {
  init: () => getInterceptor().init(),
  intercept: (toolCall: ToolCall, result?: ToolCall['result']) => 
    getInterceptor().intercept(toolCall, result),
  executeAction: (action: ToolEnhancementAction, context: ToolEnhancementContext) =>
    getInterceptor().executeAction(action, context),
  registerRule: (rule: ToolEnhancementRule) => getInterceptor().registerRule(rule),
  unregisterRule: (rule: ToolEnhancementRule) => getInterceptor().unregisterRule(rule)
}








