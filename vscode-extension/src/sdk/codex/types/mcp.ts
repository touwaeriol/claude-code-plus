/**
 * MCP 相关类型
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 750-814
 */

export interface McpTool {
  name?: string
  description?: string
  inputSchema?: unknown
}

export interface McpResource {
  uri?: string
  name?: string
  description?: string
  mimeType?: string
}

export interface McpResourceTemplate {
  uriTemplate?: string
  name?: string
  description?: string
  mimeType?: string
}

export interface McpServerStatus {
  name: string
  tools: Record<string, McpTool>
  resources: McpResource[]
  resourceTemplates: McpResourceTemplate[]
  authStatus?: string
}

export interface ListMcpServerStatusParams {
  cursor?: string
  limit?: number
}

export interface ListMcpServerStatusResponse {
  data: McpServerStatus[]
  nextCursor?: string
}

export interface McpServerOauthLoginParams {
  name: string
  scopes?: string[]
  timeoutSecs?: number
}

export interface McpServerOauthLoginResponse {
  authorizationUrl: string
}
