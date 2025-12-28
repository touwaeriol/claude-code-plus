/**
 * Codex 工具类型定义
 *
 * 这些类型用于描述 Codex 后端的工具调用格式，
 * 在 ToolUseDisplay 中会被转换为 Claude 格式以复用现有显示组件。
 */

import type { BaseToolCall, ToolResult } from './display'

// ============================================================================
// Codex 工具结果格式
// ============================================================================

/**
 * Codex 通用结果格式
 * 注意：与 Claude 的 ToolResult 格式不同
 */
export interface CodexToolResult {
  /** 是否成功 */
  success: boolean
  /** 输出内容（成功时） */
  output?: string
  /** 错误信息（失败时） */
  error?: string
  /** 退出码（CommandExecution 专用） */
  exitCode?: number
  /** 其他扩展字段 */
  [key: string]: unknown
}

// ============================================================================
// Codex CommandExecution 工具
// ============================================================================

/**
 * Codex CommandExecution 工具输入参数
 */
export interface CodexCommandExecutionInput {
  /** 工具类型标识 */
  type: 'CommandExecution'
  /** 要执行的命令 */
  command: string
  /** 工作目录 */
  cwd?: string
  /** 超时时间（毫秒） */
  timeout?: number
  /** 命令描述 */
  description?: string
  /** 环境变量 */
  env?: Record<string, string>
}

/**
 * Codex CommandExecution 工具结果
 */
export interface CodexCommandExecutionResult extends CodexToolResult {
  /** 标准输出 */
  stdout?: string
  /** 标准错误输出 */
  stderr?: string
  /** 退出码 */
  exitCode: number
}

/**
 * Codex CommandExecution 工具调用
 */
export interface CodexCommandExecutionToolCall extends BaseToolCall {
  toolName: 'CommandExecution'
  input: CodexCommandExecutionInput
  result?: CodexCommandExecutionResult
}

// ============================================================================
// Codex FileChange 工具
// ============================================================================

/**
 * 文件操作类型
 */
export type FileChangeOperation = 'create' | 'edit' | 'delete'

/**
 * Codex FileChange 工具输入参数
 */
export interface CodexFileChangeInput {
  /** 工具类型标识 */
  type: 'FileChange'
  /** 文件操作类型 */
  operation: FileChangeOperation
  /** 文件路径 */
  path: string
  /** 文件内容（create 时必需） */
  content?: string
  /** 旧内容（edit 时） */
  oldContent?: string
  /** 新内容（edit 时） */
  newContent?: string
  /** 是否替换所有匹配（edit 时） */
  replaceAll?: boolean
  /** Diff 信息（可选） */
  diff?: string
}

/**
 * Codex FileChange 工具结果
 */
export interface CodexFileChangeResult extends CodexToolResult {
  /** 操作的文件路径 */
  path?: string
  /** 修改的行数 */
  linesChanged?: number
  /** 添加的行数 */
  linesAdded?: number
  /** 删除的行数 */
  linesRemoved?: number
}

/**
 * Codex FileChange 工具调用
 */
export interface CodexFileChangeToolCall extends BaseToolCall {
  toolName: 'FileChange'
  input: CodexFileChangeInput
  result?: CodexFileChangeResult
}

// ============================================================================
// Codex McpToolCall 工具
// ============================================================================

/**
 * Codex MCP 工具调用输入参数
 */
export interface CodexMcpToolCallInput {
  /** 工具类型标识 */
  type: 'McpToolCall'
  /** MCP 服务器名称 */
  server?: string
  /** MCP 工具名称 */
  toolName: string
  /** 工具参数 */
  parameters: Record<string, unknown>
}

/**
 * Codex MCP 工具调用结果
 */
export interface CodexMcpToolCallResult extends CodexToolResult {
  /** 工具返回的数据 */
  data?: unknown
}

/**
 * Codex MCP 工具调用
 */
export interface CodexMcpToolCall extends BaseToolCall {
  toolName: 'McpToolCall'
  input: CodexMcpToolCallInput
  result?: CodexMcpToolCallResult
}

// ============================================================================
// Codex Reasoning 项（注意：通常不作为 ToolCall）
// ============================================================================

/**
 * Codex Reasoning 输入参数
 * 注意：Reasoning 通常作为流式事件发送，而非工具调用
 */
export interface CodexReasoningInput {
  /** 工具类型标识 */
  type: 'Reasoning'
  /** 推理努力级别 */
  effort?: 'minimal' | 'low' | 'medium' | 'high' | 'xhigh'
  /** 摘要模式 */
  summaryMode?: 'auto' | 'concise' | 'detailed' | 'none'
}

/**
 * Codex Reasoning 结果
 */
export interface CodexReasoningResult {
  /** 推理摘要文本 */
  summary?: string
  /** 详细推理内容（如果可用） */
  details?: string
}

/**
 * Codex Reasoning 项（兜底，不建议作为 ToolCall）
 */
export interface CodexReasoningToolCall extends BaseToolCall {
  toolName: 'Reasoning'
  input: CodexReasoningInput
  result?: CodexReasoningResult
}

// ============================================================================
// Codex 工具调用联合类型
// ============================================================================

/**
 * 所有 Codex 工具调用的联合类型
 */
export type CodexToolCall =
  | CodexCommandExecutionToolCall
  | CodexFileChangeToolCall
  | CodexMcpToolCall
  | CodexReasoningToolCall

// ============================================================================
// 类型守卫
// ============================================================================

/**
 * 检查是否为 Codex CommandExecution 工具
 */
export function isCodexCommandExecution(toolCall: any): toolCall is CodexCommandExecutionToolCall {
  return toolCall.toolName === 'CommandExecution' ||
         toolCall.input?.type === 'CommandExecution'
}

/**
 * 检查是否为 Codex FileChange 工具
 */
export function isCodexFileChange(toolCall: any): toolCall is CodexFileChangeToolCall {
  return toolCall.toolName === 'FileChange' ||
         toolCall.input?.type === 'FileChange'
}

/**
 * 检查是否为 Codex MCP 工具调用
 */
export function isCodexMcpToolCall(toolCall: any): toolCall is CodexMcpToolCall {
  return toolCall.toolName === 'McpToolCall' ||
         toolCall.input?.type === 'McpToolCall'
}

/**
 * 检查是否为 Codex Reasoning 项
 */
export function isCodexReasoning(toolCall: any): toolCall is CodexReasoningToolCall {
  return toolCall.toolName === 'Reasoning' ||
         toolCall.input?.type === 'Reasoning'
}

/**
 * 检查结果是否为 Codex 格式
 */
export function isCodexResult(result: any): result is CodexToolResult {
  return result &&
         typeof result === 'object' &&
         'success' in result
}

// ============================================================================
// 格式转换辅助函数
// ============================================================================

/**
 * 将 Codex 结果格式转换为 Claude ToolResult 格式
 */
export function convertCodexResultToClaudeFormat(
  codexResult: CodexToolResult | undefined,
  fallbackOutput?: string
): ToolResult {
  if (!codexResult) {
    return {
      content: fallbackOutput || '',
      is_error: false
    }
  }

  const isError = codexResult.success === false || !!codexResult.error
  const content = isError
    ? (codexResult.error || 'Unknown error')
    : (codexResult.output || codexResult.stdout || fallbackOutput || '')

  return {
    content,
    is_error: isError
  }
}

/**
 * 将 Codex CommandExecution 转换为 Claude Bash 格式
 */
export function convertCommandExecutionToBash(toolCall: CodexCommandExecutionToolCall) {
  return {
    ...toolCall,
    toolType: 'CLAUDE_BASH' as const,
    input: {
      command: toolCall.input.command,
      cwd: toolCall.input.cwd,
      timeout: toolCall.input.timeout,
      description: toolCall.input.description
    },
    result: convertCodexResultToClaudeFormat(
      toolCall.result,
      toolCall.result?.stdout
    )
  }
}

/**
 * 将 Codex FileChange (create) 转换为 Claude Write 格式
 */
export function convertFileCreateToWrite(toolCall: CodexFileChangeToolCall) {
  return {
    ...toolCall,
    toolType: 'CLAUDE_WRITE' as const,
    input: {
      file_path: toolCall.input.path,
      path: toolCall.input.path,
      content: toolCall.input.content || ''
    },
    result: convertCodexResultToClaudeFormat(toolCall.result)
  }
}

/**
 * 将 Codex FileChange (edit) 转换为 Claude Edit 格式
 */
export function convertFileEditToEdit(toolCall: CodexFileChangeToolCall) {
  return {
    ...toolCall,
    toolType: 'CLAUDE_EDIT' as const,
    input: {
      file_path: toolCall.input.path,
      old_string: toolCall.input.oldContent || '',
      new_string: toolCall.input.newContent || toolCall.input.content || '',
      replace_all: toolCall.input.replaceAll ?? false
    },
    result: convertCodexResultToClaudeFormat(toolCall.result)
  }
}

/**
 * 将 Codex McpToolCall 转换为 MCP 格式
 */
export function convertMcpToolCall(toolCall: CodexMcpToolCall) {
  return {
    ...toolCall,
    toolType: 'MCP' as const,
    toolName: `mcp__${toolCall.input.server ? toolCall.input.server + '__' : ''}${toolCall.input.toolName}`,
    input: toolCall.input.parameters,
    result: convertCodexResultToClaudeFormat(toolCall.result)
  }
}

// ============================================================================
// 示例数据（用于测试）
// ============================================================================

/**
 * Codex CommandExecution 示例
 */
export const exampleCodexCommandExecution: CodexCommandExecutionToolCall = {
  id: 'cmd-1',
  displayType: 'toolCall',
  toolName: 'CommandExecution',
  toolType: 'CODEX_TOOL' as any,
  status: 'SUCCESS',
  startTime: Date.now(),
  timestamp: Date.now(),
  input: {
    type: 'CommandExecution',
    command: 'npm install',
    cwd: '/workspace/project',
    timeout: 30000
  },
  result: {
    success: true,
    output: 'added 100 packages in 5s',
    stdout: 'added 100 packages in 5s',
    exitCode: 0
  }
}

/**
 * Codex FileChange (create) 示例
 */
export const exampleCodexFileCreate: CodexFileChangeToolCall = {
  id: 'file-1',
  displayType: 'toolCall',
  toolName: 'FileChange',
  toolType: 'CODEX_TOOL' as any,
  status: 'SUCCESS',
  startTime: Date.now(),
  timestamp: Date.now(),
  input: {
    type: 'FileChange',
    operation: 'create',
    path: '/src/components/NewComponent.vue',
    content: '<template>\n  <div>New Component</div>\n</template>'
  },
  result: {
    success: true,
    output: 'File created successfully',
    linesAdded: 3
  }
}

/**
 * Codex FileChange (edit) 示例
 */
export const exampleCodexFileEdit: CodexFileChangeToolCall = {
  id: 'file-2',
  displayType: 'toolCall',
  toolName: 'FileChange',
  toolType: 'CODEX_TOOL' as any,
  status: 'SUCCESS',
  startTime: Date.now(),
  timestamp: Date.now(),
  input: {
    type: 'FileChange',
    operation: 'edit',
    path: '/src/store/index.ts',
    oldContent: 'const initialState = { count: 0 }',
    newContent: 'const initialState = { count: 0, user: null }',
    replaceAll: false
  },
  result: {
    success: true,
    output: 'File edited successfully',
    linesChanged: 1
  }
}

/**
 * Codex MCP 工具调用示例
 */
export const exampleCodexMcpTool: CodexMcpToolCall = {
  id: 'mcp-1',
  displayType: 'toolCall',
  toolName: 'McpToolCall',
  toolType: 'CODEX_TOOL' as any,
  status: 'SUCCESS',
  startTime: Date.now(),
  timestamp: Date.now(),
  input: {
    type: 'McpToolCall',
    server: 'excel',
    toolName: 'read',
    parameters: {
      file: '/data/sales.xlsx',
      sheet: 'Q1 Sales'
    }
  },
  result: {
    success: true,
    output: JSON.stringify({ rows: 100, columns: 4 })
  }
}
