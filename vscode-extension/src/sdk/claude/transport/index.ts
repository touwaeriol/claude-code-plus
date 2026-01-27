/**
 * Transport module for Claude CLI communication.
 *
 * This module provides transport layer abstractions for communicating
 * with the Claude CLI via different mechanisms (subprocess, etc.).
 *
 * Usage:
 * ```typescript
 * import { SubprocessTransport, type Transport } from './transport'
 *
 * const transport = new SubprocessTransport({
 *   cwd: '/path/to/project',
 *   model: 'claude-sonnet-4-20250514',
 * })
 *
 * await transport.connect()
 * await transport.write(JSON.stringify({ type: 'user', message: 'Hello' }))
 *
 * for await (const message of transport.readMessages()) {
 *   console.log('Received:', message)
 * }
 *
 * await transport.close()
 * ```
 */

// Re-export transport interface and error types
export {
  type Transport,
  type TransportEvents,
  type JsonMessage,
  type MessageCallback,
  type ErrorCallback,
  type CloseCallback,
  TransportError,
  CLINotFoundError,
  CLIConnectionError,
  ProcessError,
  JSONDecodeError,
  NodeNotFoundError,
} from './transport'

// Re-export subprocess transport implementation and types
export {
  SubprocessTransport,
  type ClaudeAgentOptions,
  type PermissionMode,
  type McpServerConfig,
  type McpStdioServerConfig,
  type McpSSEServerConfig,
  type McpHttpServerConfig,
  type AgentDefinition,
  type SystemPromptPreset,
} from './subprocessTransport'
