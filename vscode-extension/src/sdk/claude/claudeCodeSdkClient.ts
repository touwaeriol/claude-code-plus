/**
 * Claude Code SDK Client
 * 
 * Full-featured client for bidirectional, interactive conversations with Claude Agent.
 * Translated from: claude-agent-sdk/.../ClaudeCodeSdkClient.kt
 * 
 * Key features:
 * - Bidirectional: Send and receive messages at any time
 * - Stateful: Maintains conversation context across messages
 * - Interactive: Send follow-ups based on responses
 * - Control flow: Support for interrupts and session management
 * - Partial messages: Stream partial message updates (when enabled)
 * - Programmatic agents: Define subagents inline
 */

import { EventEmitter } from 'events';
import * as vscode from 'vscode';
import { SubprocessTransport, type ClaudeAgentOptions, type TransportEvents } from './transport';
import { ControlProtocol } from './protocol';
import type {
  Message,
  AssistantMessage,
  ResultMessage,
  UserMessage,
  StreamEvent,
  McpServerStatusInfo,
  McpReconnectResponse,
} from './protocol/models';

/**
 * Permission modes for the SDK client.
 */
export type PermissionMode = 'default' | 'acceptEdits' | 'bypassPermissions' | 'plan';

/**
 * User input content types.
 */
export type UserInputContent = TextInput | ImageInput;

export interface TextInput {
  type: 'text';
  text: string;
}

export interface ImageInput {
  type: 'image';
  source: {
    type: 'base64';
    media_type: string;
    data: string;
  };
}

/**
 * Stream JSON user message format.
 */
export interface StreamJsonUserMessage {
  type: 'user';
  message: {
    role: 'user';
    content: UserInputContent[] | string;
  };
  session_id: string;
  parent_tool_use_id?: string | null;
}

/**
 * SDK Client options extending transport options.
 */
export interface SdkClientOptions extends ClaudeAgentOptions {
  /** Enable streaming mode */
  streamingMode?: boolean;
  /** Log function for debugging */
  log?: (message: string) => void;
}

/**
 * Server initialization info.
 */
export interface ServerInfo {
  status: string;
  mode: string;
  model?: string;
  [key: string]: unknown;
}

/**
 * Exception thrown when client is not connected.
 */
export class ClientNotConnectedException extends Error {
  constructor() {
    super('Client is not connected. Call connect() first.');
    this.name = 'ClientNotConnectedException';
  }
}

/**
 * Main SDK client for interacting with Claude CLI.
 */
export class ClaudeCodeSdkClient extends EventEmitter implements vscode.Disposable {
  private transport: SubprocessTransport | null = null;
  private controlProtocol: ControlProtocol | null = null;
  private serverInfo: ServerInfo | null = null;
  private commandLock = false;
  private pendingModelUpdate: {
    resolve: (value: string | null) => void;
    reject: (error: Error) => void;
  } | null = null;
  
  private readonly options: SdkClientOptions;
  private readonly log: (message: string) => void;

  constructor(options: SdkClientOptions = {}) {
    super();
    this.options = {
      streamingMode: true,
      ...options,
    };
    this.log = options.log || (() => {});
  }

  /**
   * Connect to Claude with optional initial prompt.
   */
  async connect(prompt?: string): Promise<void> {
    this.log('🔌 [SDK] Starting connection to Claude CLI...');
    this.log(`📋 [SDK] Options: model=${this.options.model}, permissionMode=${this.options.permissionMode}`);

    // Create transport
    this.transport = new SubprocessTransport(
      this.options,
      this.options.streamingMode ?? true,
      this.log
    );

    // Create control protocol
    this.controlProtocol = new ControlProtocol(this.transport as any, this.options);
    
    // Set up system init callback
    this.controlProtocol.on('systemInit', (modelId: string | null) => {
      this.onSystemInit(modelId);
    });

    try {
      // Connect transport
      this.log('🚀 Starting transport connection...');
      await this.transport.connect();
      this.log('✅ Transport connected');

      // Start message processing
      this.log('📥 Starting message processing...');
      this.controlProtocol.startMessageProcessing();
      this.log('✅ Message processing started');

      // Initialize control protocol
      this.log('🔧 Initializing control protocol...');
      await this.controlProtocol.initialize();
      this.log('✅ Control protocol initialized');

      this.serverInfo = {
        status: 'connected',
        mode: 'stream-json',
      };
      this.log('🎉 Claude SDK client connected!');

      // Send initial prompt if provided
      if (prompt) {
        this.log(`📝 Sending initial prompt: ${prompt}`);
        await this.query(prompt);
      }
    } catch (error) {
      this.log(`❌ Connection failed: ${error}`);
      await this.disconnect();
      throw error;
    }
  }

  /**
   * Send a user message to Claude (text only).
   */
  async query(prompt: string, sessionId = 'default'): Promise<void> {
    const message: StreamJsonUserMessage = {
      type: 'user',
      message: { role: 'user', content: prompt },
      session_id: sessionId,
    };
    await this.queryMessage(message);
  }

  /**
   * Send a user message with arbitrary content blocks.
   */
  async queryWithContent(content: UserInputContent[], sessionId = 'default'): Promise<void> {
    const message: StreamJsonUserMessage = {
      type: 'user',
      message: { role: 'user', content },
      session_id: sessionId,
    };
    await this.queryMessage(message);
  }

  /**
   * Send a complete StreamJsonUserMessage to Claude.
   */
  async queryMessage(message: StreamJsonUserMessage): Promise<void> {
    await this.runCommand(async () => {
      this.ensureConnected();
      
      const contentDesc = typeof message.message.content === 'string'
        ? message.message.content.substring(0, 100)
        : `${(message.message.content as UserInputContent[]).length} content blocks`;
      this.log(`💬 Sending user message [session=${message.session_id}]: ${contentDesc}`);

      const jsonString = JSON.stringify(message);
      this.log(`📤 Sending JSON: ${jsonString.substring(0, 200)}...`);
      await this.transport!.write(jsonString);
      this.log('✅ Message sent to CLI');
    });
  }

  /**
   * Receive a single complete response (until ResultMessage).
   * Returns an async iterator that yields messages.
   */
  async *receiveResponse(): AsyncGenerator<Message, void, unknown> {
    this.ensureConnected();
    this.log('📬 [receiveResponse] Starting to receive Claude response...');

    let messageCount = 0;

    for await (const message of this.controlProtocol!.receiveMessages()) {
      messageCount++;
      const messageType = (message as any).type || message.constructor.name;
      this.log(`📨 [receiveResponse] Received message #${messageCount}: ${messageType}`);

      yield message;

      // Stop after ResultMessage (like Python SDK)
      if ((message as any).type === 'result' || message instanceof Object && 'subtype' in message) {
        if ((message as ResultMessage).subtype) {
          this.log(`🎯 [receiveResponse] Result message received, ending stream`);
          break;
        }
      }
    }

    this.log(`✅ [receiveResponse] Stream complete, received ${messageCount} messages`);
  }

  /**
   * Get the continuous message stream (doesn't end after ResultMessage).
   */
  getAllMessages(): AsyncIterable<Message> {
    this.ensureConnected();
    return this.controlProtocol!.receiveMessages();
  }

  /**
   * Interrupt the current operation.
   */
  async interrupt(): Promise<void> {
    this.ensureConnected();
    await this.controlProtocol!.interrupt();
  }

  /**
   * Get MCP servers status.
   */
  async getMcpStatus(): Promise<McpServerStatusInfo[]> {
    this.ensureConnected();
    return this.controlProtocol!.getMcpStatus();
  }

  /**
   * Reconnect a specific MCP server.
   */
  async reconnectMcp(serverName: string): Promise<McpReconnectResponse> {
    this.ensureConnected();
    return this.controlProtocol!.reconnectMcp(serverName);
  }

  /**
   * Change permission mode during conversation.
   */
  async setPermissionMode(mode: PermissionMode): Promise<void> {
    await this.runCommand(async () => {
      this.ensureConnected();
      this.log(`🔐 Setting permission mode: ${mode}`);

      await this.controlProtocol!.setPermissionMode(mode);

      this.log(`✅ Permission mode updated to: ${mode}`);
    });
  }

  /**
   * Change the AI model during conversation.
   */
  async setModel(model: string | null): Promise<string | null> {
    return this.runCommand(async () => {
      this.ensureConnected();
      this.log(`🤖 Setting model: ${model ?? 'default'}`);

      // Create a promise to track model update confirmation
      const modelPromise = new Promise<string | null>((resolve, reject) => {
        this.pendingModelUpdate = { resolve, reject };
      });

      try {
        await this.controlProtocol!.setModel(model ?? 'default');
      } catch (error) {
        this.pendingModelUpdate = null;
        throw error;
      }

      // Wait for confirmation with timeout
      const result = await Promise.race([
        modelPromise,
        new Promise<string | null>((resolve) => {
          setTimeout(() => {
            this.log(`⚠️ Model switch confirmation timeout, using requested model: ${model ?? 'default'}`);
            resolve(model);
          }, 5000);
        }),
      ]);

      this.pendingModelUpdate = null;
      this.updateCachedModel(result ?? model);
      this.log(`✅ Model updated to: ${result ?? model ?? 'default'}`);
      return result ?? model;
    });
  }

  /**
   * Dynamically set max thinking tokens without reconnecting.
   */
  async setMaxThinkingTokens(maxThinkingTokens: number | null): Promise<void> {
    await this.runCommand(async () => {
      this.ensureConnected();
      this.log(`🧠 Setting max thinking tokens: ${maxThinkingTokens}`);
      await this.controlProtocol!.setMaxThinkingTokens(maxThinkingTokens);
      this.log(`✅ Max thinking tokens set to: ${maxThinkingTokens}`);
    });
  }

  /**
   * Get server initialization information.
   */
  getServerInfo(): ServerInfo | null {
    return this.serverInfo;
  }

  /**
   * Check if the client is connected.
   */
  isConnected(): boolean {
    const transportConnected = this.transport?.isConnected() ?? false;
    const hasBasicConnection = this.serverInfo !== null;
    return transportConnected && hasBasicConnection;
  }

  /**
   * Disconnect from Claude and cleanup resources.
   */
  async disconnect(): Promise<void> {
    try {
      if (this.pendingModelUpdate) {
        this.pendingModelUpdate.reject(new Error('Disconnected'));
        this.pendingModelUpdate = null;
      }
      this.controlProtocol?.stopMessageProcessing();
      await this.transport?.close();
    } finally {
      this.transport = null;
      this.controlProtocol = null;
      this.serverInfo = null;
    }
  }

  /**
   * Use the client within a scope that automatically handles connection lifecycle.
   */
  async use<T>(block: (client: ClaudeCodeSdkClient) => Promise<T>): Promise<T> {
    await this.connect();
    try {
      return await block(this);
    } finally {
      await this.disconnect();
    }
  }

  /**
   * Create a simple query function for one-shot interactions.
   */
  async simpleQuery(prompt: string): Promise<Message[]> {
    return this.use(async (client) => {
      await client.query(prompt);
      const messages: Message[] = [];
      for await (const msg of client.receiveResponse()) {
        messages.push(msg);
      }
      return messages;
    });
  }

  /**
   * Dispose resources.
   */
  dispose(): void {
    this.disconnect().catch(() => {});
  }

  // Private methods

  private updateCachedModel(model: string | null): void {
    this.serverInfo = {
      ...this.serverInfo,
      status: this.serverInfo?.status ?? 'connected',
      mode: this.serverInfo?.mode ?? 'stream-json',
      model: model ?? 'default',
    };
  }

  private async runCommand<T>(block: () => Promise<T>): Promise<T> {
    // Simple mutex implementation
    while (this.commandLock) {
      await new Promise(resolve => setTimeout(resolve, 10));
    }
    this.commandLock = true;
    try {
      return await block();
    } finally {
      this.commandLock = false;
    }
  }

  private onSystemInit(modelId: string | null): void {
    if (this.pendingModelUpdate) {
      this.pendingModelUpdate.resolve(modelId);
    }
  }

  private ensureConnected(): void {
    if (!this.isConnected()) {
      throw new ClientNotConnectedException();
    }
  }
}

/**
 * Builder function for creating ClaudeCodeSdkClient with options.
 */
export function createSdkClient(options: SdkClientOptions = {}): ClaudeCodeSdkClient {
  return new ClaudeCodeSdkClient(options);
}

/**
 * Convenience function for simple one-shot queries.
 */
export async function claudeQuery(
  prompt: string,
  options: SdkClientOptions = {}
): Promise<Message[]> {
  return new ClaudeCodeSdkClient(options).simpleQuery(prompt);
}
