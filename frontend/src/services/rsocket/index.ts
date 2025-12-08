/**
 * RSocket 模块导出
 *
 * 提供基于 RSocket + Protobuf 的 AI Agent 通信能力
 */

export { RSocketClient, createRSocketClient, type RSocketClientOptions, type StreamSubscriber } from './RSocketClient'
export { RSocketSession, type ConnectOptions, type AgentStreamEvent, type ContentBlock } from './RSocketSession'
export { ProtoCodec, Provider, PermissionMode, SandboxMode } from './protoCodec'
