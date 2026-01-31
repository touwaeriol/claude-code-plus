/**
 * MCP 服务器常量定义
 * 
 * 定义内置 MCP 服务器的名称映射和默认配置
 */

/**
 * 内置 MCP 服务器名称映射
 * 
 * UI 显示名 -> MCP 服务器名（传递给 CLI）
 * 
 * 与 JetBrains 版本保持一致：
 * - User Interaction -> user-interaction
 * - JetBrains LSP -> ide-lsp (VS Code 版本也使用相同的 MCP 名称)
 * - JetBrains File -> ide-file
 * - Terminal -> ide-terminal
 * - Git -> ide-git
 * - Context7 -> context7
 */
export const MCP_SERVER_NAMES: Record<string, string> = {
  'User Interaction': 'user-interaction',
  'VS Code LSP': 'ide-lsp',
  'VS Code File': 'ide-file',
  'Context7': 'context7',
  'Terminal': 'ide-terminal',
  'Git': 'ide-git',
} as const

/**
 * 反向映射：MCP 名称 -> UI 名称
 */
export const MCP_SERVER_UI_NAMES: Record<string, string> = Object.fromEntries(
  Object.entries(MCP_SERVER_NAMES).map(([ui, mcp]) => [mcp, ui])
)

/**
 * 将 UI 服务器名称转换为 MCP 服务器名称
 */
export function toMcpServerName(uiName: string): string {
  return MCP_SERVER_NAMES[uiName] || uiName.toLowerCase().replace(/\s+/g, '-')
}

/**
 * 将 MCP 服务器名称转换为 UI 服务器名称
 */
export function toUiServerName(mcpName: string): string {
  return MCP_SERVER_UI_NAMES[mcpName] || mcpName
}

/**
 * Context7 MCP 服务器配置
 */
export const CONTEXT7_CONFIG = {
  URL: 'https://mcp.context7.com/mcp',
  API_KEY_HEADER: 'X-Context7-Api-Key',
  DESCRIPTION: 'Context7 documentation retrieval',
} as const
