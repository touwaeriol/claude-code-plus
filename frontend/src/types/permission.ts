/**
 * 工具授权相关类型定义
 */

/**
 * 待处理的授权请求
 */
export interface PendingPermissionRequest {
  /** 唯一标识符 */
  id: string
  /** 所属会话 ID */
  sessionId: string
  /** 需要授权的工具名称 */
  toolName: string
  /** 工具输入参数 */
  toolInput: PermissionToolInput
  /** 创建时间戳 */
  createdAt: number
  /** 用户响应后调用 */
  resolve: (response: PermissionResponse) => void
  /** 用户取消或出错时调用 */
  reject: (error: Error) => void
}

/**
 * 授权响应
 */
export interface PermissionResponse {
  /** 是否批准 */
  approved: boolean
}

/**
 * 工具输入参数类型
 * 根据不同工具类型有不同的字段
 */
export interface PermissionToolInput {
  // Bash 工具
  command?: string
  timeout?: number

  // Write 工具
  file_path?: string
  content?: string

  // Edit 工具
  old_string?: string
  new_string?: string
  replace_all?: boolean

  // Read 工具
  path?: string
  offset?: number
  limit?: number

  // 其他字段（MCP 工具等）
  [key: string]: unknown
}

/**
 * 后端发送的授权请求参数
 */
export interface PermissionRequestParams {
  tool_name: string
  tool_input: PermissionToolInput
}

/**
 * 待回答的用户问题
 */
export interface PendingUserQuestion {
  id: string
  sessionId: string
  questions: Array<{
    question: string
    header: string
    options: Array<{ label: string; description?: string }>
    multiSelect: boolean
  }>
  createdAt: number
  resolve: (answers: Record<string, string>) => void
  reject: (error: Error) => void
}
