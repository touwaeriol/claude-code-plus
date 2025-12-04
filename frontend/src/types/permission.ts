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
  input: PermissionToolInput
  /** 创建时间戳 */
  createdAt: number
  /** 匹配到的 tool_use_id（用于在对应的工具块位置显示授权 UI） */
  matchedToolCallId?: string
  /** CLI 提供的权限建议 */
  permissionSuggestions?: PermissionUpdate[]
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
  /** 用户选择的权限更新列表（如果选择了建议，与官方 SDK 保持一致） */
  permissionUpdates?: PermissionUpdate[]
  /** 拒绝原因（如果拒绝） */
  denyReason?: string
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
 * 权限行为类型
 */
export type PermissionBehavior = 'allow' | 'deny' | 'ask'

/**
 * 权限模式
 */
export type PermissionMode = 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions' | 'dontAsk'

/**
 * 权限更新目标
 */
export type PermissionUpdateDestination = 'userSettings' | 'projectSettings' | 'localSettings' | 'session'

/**
 * 权限更新类型
 */
export type PermissionUpdateType = 'addRules' | 'replaceRules' | 'removeRules' | 'setMode' | 'addDirectories' | 'removeDirectories'

/**
 * 权限规则值
 */
export interface PermissionRuleValue {
  toolName: string
  ruleContent?: string
}

/**
 * 权限更新配置（CLI 建议）
 */
export interface PermissionUpdate {
  type: PermissionUpdateType
  rules?: PermissionRuleValue[]
  behavior?: PermissionBehavior
  mode?: PermissionMode
  directories?: string[]
  destination?: PermissionUpdateDestination
}

/**
 * 后端发送的授权请求参数
 */
export interface PermissionRequestParams {
  toolName: string
  input: PermissionToolInput
  /** 工具调用 ID（来自 canUseTool 回调，用于精确关联前端工具块） */
  toolUseId?: string
  /** CLI 提供的权限建议 */
  permissionSuggestions?: PermissionUpdate[]
}

/**
 * 会话级权限规则
 */
export interface SessionPermissionRule {
  toolName: string
  ruleContent?: string
  behavior: PermissionBehavior
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
