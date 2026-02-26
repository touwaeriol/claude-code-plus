/**
 * Control Protocol Handler for Claude CLI
 *
 * This module provides bidirectional communication with Claude CLI through
 * stdin/stdout using JSON-RPC style control messages.
 *
 * Control commands:
 * - interrupt: Interrupt the current operation
 * - initialize: Initialize the control protocol
 * - set_model: Set the model for the session
 * - set_permission_mode: Set the permission mode
 * - set_max_thinking_tokens: Set max thinking tokens
 * - mcp_status: Get MCP server status
 * - mcp_reconnect: Reconnect MCP server
 * - mcp_disable/mcp_enable: Disable/enable MCP server
 * - mcp_set_servers: Dynamically set MCP servers
 */

import { EventEmitter } from 'events';
import type { ChildProcess } from 'child_process';
import type {
  ControlResponse,
  McpServerStatusInfo,
  McpReconnectResponse,
  McpDisableEnableResponse,
  McpSetServersResponse,
  McpStdioServerDto,
  Message,
  HookEvent,
  HookMatcher,
  HookCallback,
  PermissionResult,
  ToolPermissionContext,
} from './models';
import { MessageParser } from './messageParser';

/**
 * Exception for control protocol errors.
 */
export class ControlProtocolException extends Error {
  constructor(message: string, public cause?: Error) {
    super(message);
    this.name = 'ControlProtocolException';
  }
}

/**
 * Transport interface for CLI communication.
 */
export interface Transport {
  write(data: string): void;
  onMessage(callback: (data: unknown) => void): void;
  onClose(callback: () => void): void;
  onError(callback: (error: Error) => void): void;
}

/**
 * Options for ControlProtocol.
 */
export interface ControlProtocolOptions {
  /** MCP servers configuration */
  mcpServers?: Record<string, unknown>;
  /** Hook configurations */
  hooks?: Map<HookEvent, HookMatcher[]> | Record<HookEvent, HookMatcher[]>;
  /** Tool permission callback - can return boolean or PermissionResult */
  canUseTool?: (
    toolName: string,
    input: Record<string, unknown> | unknown,
    toolUseId?: string,
    context?: ToolPermissionContext
  ) => Promise<boolean | PermissionResult>;
  /** System init callback */
  systemInitCallback?: (model?: string) => void;
}

/**
 * Pending request tracking.
 */
interface PendingRequest {
  resolve: (response: ControlResponse) => void;
  reject: (error: Error) => void;
  timeoutId?: NodeJS.Timeout;
}

/**
 * Control protocol handler for managing bidirectional communication with Claude CLI.
 */
export class ControlProtocol extends EventEmitter {
  private messageParser = new MessageParser();
  private requestCounter = 0;
  private pendingRequests = new Map<string, PendingRequest>();
  private hookCallbacks = new Map<string, HookCallback>();
  private hookIdCounter = 0;
  private initialized = false;
  private initializePromise: Promise<Record<string, unknown>> | null = null;
  private initializeResolve: ((value: Record<string, unknown>) => void) | null = null;

  constructor(
    private transport: Transport,
    private options: ControlProtocolOptions = {}
  ) {
    super();
  }

  /**
   * Start processing messages from transport.
   */
  startMessageProcessing(): void {
    console.log('[ControlProtocol] Starting message processing');
    
    this.transport.onMessage((data) => {
      try {
        this.routeMessage(data);
      } catch (e) {
        console.error('[ControlProtocol] Error processing message:', e);
      }
    });

    this.transport.onClose(() => {
      console.log('[ControlProtocol] Transport closed');
      this.emit('close');
    });

    this.transport.onError((error) => {
      console.error('[ControlProtocol] Transport error:', error);
      this.emit('error', error);
    });
  }

  /**
   * Stop message processing.
   */
  stopMessageProcessing(): void {
    // Cancel all pending requests
    this.pendingRequests.forEach((pending, requestId) => {
      if (pending.timeoutId) {
        clearTimeout(pending.timeoutId);
      }
      pending.reject(new ControlProtocolException('Protocol stopped'));
    });
    this.pendingRequests.clear();
  }

  /**
   * Receive messages as an async iterable.
   * This creates an async generator that yields messages as they arrive.
   */
  async *receiveMessages(): AsyncGenerator<Message, void, unknown> {
    // Create a queue to hold incoming messages
    const messageQueue: Message[] = [];
    let resolveNext: ((value: IteratorResult<Message, void>) => void) | null = null;
    let done = false;

    const onMessage = (message: Message) => {
      if (resolveNext) {
        resolveNext({ value: message, done: false });
        resolveNext = null;
      } else {
        messageQueue.push(message);
      }
    };

    const onClose = () => {
      done = true;
      if (resolveNext) {
        resolveNext({ value: undefined as any, done: true });
        resolveNext = null;
      }
    };

    this.on('message', onMessage);
    this.on('close', onClose);

    try {
      while (!done) {
        if (messageQueue.length > 0) {
          yield messageQueue.shift()!;
        } else {
          const result = await new Promise<IteratorResult<Message, void>>((resolve) => {
            resolveNext = resolve;
          });
          if (result.done) break;
          if (result.value !== undefined) {
            yield result.value;
          }
        }
      }
    } finally {
      this.off('message', onMessage);
      this.off('close', onClose);
    }
  }

  /**
   * Wait for system initialization.
   */
  async waitForSystemInit(): Promise<Record<string, unknown>> {
    if (this.initialized && this.initializeResolve) {
      return new Promise((resolve) => {
        this.initializeResolve = resolve;
      });
    }
    return {};
  }

  /**
   * Initialize control protocol.
   * This must be called after startMessageProcessing() and before using hooks.
   */
  async initialize(): Promise<Record<string, unknown>> {
    if (this.initialized && this.initializePromise) {
      return this.initializePromise;
    }

    console.log('[ControlProtocol] Initializing...');

    // Build hooks configuration
    const hooksConfig: Record<string, unknown[]> = {};
    if (this.options.hooks) {
      const hooksMap = this.options.hooks instanceof Map 
        ? this.options.hooks 
        : new Map(Object.entries(this.options.hooks));
      hooksMap.forEach((matchers, event) => {
        if (matchers.length > 0) {
          const eventMatchers: unknown[] = [];
          for (const matcher of matchers) {
            const callbackIds: string[] = [];
            for (const callback of matcher.hooks) {
              const callbackId = `hook_${++this.hookIdCounter}`;
              this.hookCallbacks.set(callbackId, callback);
              callbackIds.push(callbackId);
              console.log(`[ControlProtocol] Registered hook callback: ${callbackId}`);
            }
            eventMatchers.push({
              matcher: matcher.matcher,
              hookCallbackIds: callbackIds,
            });
          }
          hooksConfig[event] = eventMatchers;
        }
      });
    }

    // Send initialize control request
    const initRequest: Record<string, unknown> = {
      subtype: 'initialize',
    };
    if (Object.keys(hooksConfig).length > 0) {
      initRequest.hooks = hooksConfig;
    }

    // Calculate timeout (similar to Python SDK)
    const timeoutMs = parseInt(process.env.CLAUDE_CODE_STREAM_CLOSE_TIMEOUT || '60000', 10);
    const initializeTimeout = Math.max(timeoutMs, 60000);

    console.log(`[ControlProtocol] Initialize timeout: ${initializeTimeout}ms`);

    const response = await this.sendControlRequestInternal(initRequest, initializeTimeout);
    this.initialized = true;

    const result = (response.response as Record<string, unknown>) || { status: 'initialized' };
    console.log('[ControlProtocol] Initialized successfully');
    
    return result;
  }

  /**
   * Route incoming messages to appropriate handlers.
   */
  private routeMessage(data: unknown): void {
    if (typeof data !== 'object' || data === null) {
      console.warn('[ControlProtocol] Invalid message format:', data);
      return;
    }

    const jsonObject = data as Record<string, unknown>;
    const type = jsonObject.type as string | undefined;

    console.log(`[ControlProtocol] Routing message: type=${type}`);

    switch (type) {
      case 'system': {
        const subtype = jsonObject.subtype as string | undefined;
        console.log(`[ControlProtocol] System message: subtype=${subtype}`);
        
        if (subtype === 'init') {
          this.handleSystemInit(jsonObject);
        } else {
          // Parse and emit system message
          try {
            const message = this.messageParser.parseMessage(data);
            this.emit('message', message);
          } catch (e) {
            console.error('[ControlProtocol] Error parsing system message:', e);
          }
        }
        break;
      }

      case 'control_request': {
        const requestId = jsonObject.request_id as string;
        const request = jsonObject.request as Record<string, unknown>;
        this.handleControlRequest(requestId, request);
        break;
      }

      case 'control_response': {
        const response = this.messageParser.parseControlResponse(data);
        console.log(`[ControlProtocol] Control response: requestId=${response.request_id}, subtype=${response.subtype}`);
        
        const pending = this.pendingRequests.get(response.request_id);
        if (pending) {
          this.pendingRequests.delete(response.request_id);
          if (pending.timeoutId) {
            clearTimeout(pending.timeoutId);
          }
          pending.resolve(response);
        } else {
          console.warn(`[ControlProtocol] No pending request found for: ${response.request_id}`);
        }
        break;
      }

      case 'assistant':
      case 'user':
      case 'result':
      case 'stream_event': {
        // Regular SDK messages
        try {
          const message = this.messageParser.parseMessage(data);
          this.emit('message', message);
        } catch (e) {
          console.error(`[ControlProtocol] Error parsing ${type} message:`, e);
        }
        break;
      }

      default:
        console.warn(`[ControlProtocol] Unknown message type: ${type}`);
    }
  }

  /**
   * Handle system initialization message from Claude CLI.
   */
  private handleSystemInit(jsonObject: Record<string, unknown>): void {
    try {
      const sessionId = (jsonObject.session_id as string) || 'default';
      const model = jsonObject.model as string | undefined;

      console.log(`[ControlProtocol] System init: sessionId=${sessionId}, model=${model}`);

      // Emit system init message
      const systemInitMessage = {
        type: 'system',
        subtype: 'init',
        session_id: sessionId,
        cwd: jsonObject.cwd as string | undefined,
        model,
        permissionMode: jsonObject.permissionMode as string | undefined,
        apiKeySource: jsonObject.apiKeySource as string | undefined,
        tools: jsonObject.tools as string[] | undefined,
        mcp_servers: jsonObject.mcp_servers,
      };
      this.emit('message', systemInitMessage);

      // Invoke callback
      this.options.systemInitCallback?.(model);
    } catch (e) {
      console.error('[ControlProtocol] Error handling system init:', e);
    }
  }

  /**
   * Handle incoming control requests from CLI.
   */
  private async handleControlRequest(requestId: string, request: Record<string, unknown>): Promise<void> {
    const subtype = request.subtype as string;
    console.log(`[ControlProtocol] Handling control request: requestId=${requestId}, subtype=${subtype}`);

    try {
      let response: unknown;

      switch (subtype) {
        case 'hook_callback': {
          response = await this.handleHookCallback(request);
          break;
        }
        case 'can_use_tool': {
          response = await this.handlePermissionRequest(request);
          break;
        }
        case 'mcp_message': {
          const mcpResponse = await this.handleMcpMessage(request);
          response = { mcp_response: mcpResponse };
          break;
        }
        default:
          throw new ControlProtocolException(`Unsupported control request: ${subtype}`);
      }

      await this.sendControlResponse(requestId, 'success', response);
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      await this.sendControlResponse(requestId, 'error', undefined, error);
    }
  }

  /**
   * Handle hook callback requests.
   */
  private async handleHookCallback(request: Record<string, unknown>): Promise<unknown> {
    const callbackId = request.callback_id as string;
    const callback = this.hookCallbacks.get(callbackId);
    
    if (!callback) {
      throw new ControlProtocolException(`Unknown hook callback ID: ${callbackId}`);
    }

    const input = request.input as Record<string, unknown>;
    const toolUseId = request.tool_use_id as string | undefined;

    return callback(input, toolUseId, {});
  }

  /**
   * Handle tool permission requests.
   */
  private async handlePermissionRequest(request: Record<string, unknown>): Promise<unknown> {
    const canUseTool = this.options.canUseTool;
    if (!canUseTool) {
      throw new ControlProtocolException('No permission callback configured');
    }

    const toolName = request.tool_name as string;
    const input = request.input as Record<string, unknown>;
    const toolUseId = request.tool_use_id as string | undefined;
    const suggestions = request.permission_suggestions as unknown[] | undefined;

    const context: ToolPermissionContext = {
      suggestions: suggestions as any[],
    };

    const result = await canUseTool(toolName, input, toolUseId, context);

    if (result.behavior === 'allow') {
      return {
        behavior: result.behavior,
        updatedInput: result.updatedInput || input,
        updatedPermissions: result.updatedPermissions,
      };
    } else {
      return {
        behavior: result.behavior,
        message: result.message,
        interrupt: result.interrupt,
      };
    }
  }

  /**
   * Handle MCP message requests.
   */
  private async handleMcpMessage(request: Record<string, unknown>): Promise<unknown> {
    // This would be handled by McpMessageHandler in a full implementation
    console.log('[ControlProtocol] MCP message received:', request.server_name);
    return { error: 'MCP message handling not implemented' };
  }

  /**
   * Send control response back to CLI.
   */
  private async sendControlResponse(
    requestId: string,
    subtype: string,
    response?: unknown,
    error?: string
  ): Promise<void> {
    const responseMessage = {
      type: 'control_response',
      response: {
        subtype,
        request_id: requestId,
        response,
        error,
      },
    };

    this.transport.write(JSON.stringify(responseMessage));
  }

  /**
   * Internal method for sending control request.
   */
  private async sendControlRequestInternal(
    request: Record<string, unknown>,
    timeoutMs: number = 60000
  ): Promise<ControlResponse> {
    const requestId = `req_${++this.requestCounter}_${Date.now()}`;
    const subtype = request.subtype as string;

    const requestMessage = {
      type: 'control_request',
      request_id: requestId,
      request,
    };

    return new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        this.pendingRequests.delete(requestId);
        reject(new ControlProtocolException(
          `Control request timeout for ${requestId} after ${timeoutMs}ms`
        ));
      }, timeoutMs);

      this.pendingRequests.set(requestId, { resolve, reject, timeoutId });

      console.log(`[ControlProtocol] Sending control request: requestId=${requestId}, subtype=${subtype}`);
      this.transport.write(JSON.stringify(requestMessage));
    });
  }

  // ============================================================================
  // Public Control Methods
  // ============================================================================

  /**
   * Send interrupt request to CLI.
   */
  async interrupt(): Promise<void> {
    const request = { subtype: 'interrupt' };
    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Interrupt failed: ${response.error}`);
    }
  }

  /**
   * Set max thinking tokens for the current session.
   * 
   * @param maxThinkingTokens The maximum thinking tokens to set
   */
  async setMaxThinkingTokens(maxThinkingTokens: number | null): Promise<void> {
    const request = {
      subtype: 'set_max_thinking_tokens',
      max_thinking_tokens: maxThinkingTokens,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Set max thinking tokens failed: ${response.error}`);
    }

    console.log(`[ControlProtocol] Set maxThinkingTokens = ${maxThinkingTokens}`);
  }

  /**
   * Set model for the current session.
   * 
   * @param model The model to use, or "default" to use the default model
   */
  async setModel(model: string): Promise<void> {
    const request = {
      subtype: 'set_model',
      model,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Set model failed: ${response.error}`);
    }

    console.log(`[ControlProtocol] Set model = ${model}`);
  }

  /**
   * Set permission mode for the current session.
   * 
   * @param mode The permission mode to set
   */
  async setPermissionMode(mode: string): Promise<void> {
    const request = {
      subtype: 'set_permission_mode',
      mode,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Set permission mode failed: ${response.error}`);
    }

    console.log(`[ControlProtocol] Set permissionMode = ${mode}`);
  }

  /**
   * Get MCP servers status.
   * 
   * @returns List of MCP server status info
   */
  async getMcpStatus(): Promise<McpServerStatusInfo[]> {
    const request = { subtype: 'mcp_status' };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Get MCP status failed: ${response.error}`);
    }

    const responseData = response.response as Record<string, unknown> | undefined;
    const mcpServers = responseData?.mcpServers as unknown[] | undefined;

    if (!mcpServers) {
      return [];
    }

    return mcpServers.map((server) => {
      const obj = server as Record<string, unknown>;
      return {
        name: (obj.name as string) || '',
        status: (obj.status as string) || '',
        serverInfo: obj.serverInfo,
      };
    });
  }

  /**
   * Reconnect a specific MCP server.
   * 
   * @param serverName The name of the MCP server to reconnect
   * @returns Response with success status, server info and any errors
   */
  async reconnectMcp(serverName: string): Promise<McpReconnectResponse> {
    const request = {
      subtype: 'mcp_reconnect',
      server_name: serverName,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Reconnect MCP failed: ${response.error}`);
    }

    const responseObj = response.response as Record<string, unknown> | undefined;
    if (!responseObj) {
      return {
        success: false,
        serverName,
        toolsCount: 0,
        error: 'Empty response',
      };
    }

    return {
      success: (responseObj.success as boolean) ?? false,
      serverName: (responseObj.server_name as string) ?? serverName,
      status: responseObj.status as string | undefined,
      toolsCount: (responseObj.tools_count as number) ?? 0,
      error: responseObj.error as string | undefined,
    };
  }

  /**
   * Disable a specific MCP server.
   * 
   * @param serverName The name of the MCP server to disable
   * @returns Response with success status and server state
   */
  async disableMcp(serverName: string): Promise<McpDisableEnableResponse> {
    const request = {
      subtype: 'mcp_disable',
      server_name: serverName,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Disable MCP failed: ${response.error}`);
    }

    const responseObj = response.response as Record<string, unknown> | undefined;
    if (!responseObj) {
      return {
        success: false,
        serverName,
        toolsCount: 0,
        error: 'Empty response',
      };
    }

    return {
      success: (responseObj.success as boolean) ?? false,
      serverName: (responseObj.server_name as string) ?? serverName,
      status: responseObj.status as string | undefined,
      toolsCount: (responseObj.tools_count as number) ?? 0,
      error: responseObj.error as string | undefined,
    };
  }

  /**
   * Enable a specific MCP server.
   * 
   * @param serverName The name of the MCP server to enable
   * @returns Response with success status and server state
   */
  async enableMcp(serverName: string): Promise<McpDisableEnableResponse> {
    const request = {
      subtype: 'mcp_enable',
      server_name: serverName,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Enable MCP failed: ${response.error}`);
    }

    const responseObj = response.response as Record<string, unknown> | undefined;
    if (!responseObj) {
      return {
        success: false,
        serverName,
        toolsCount: 0,
        error: 'Empty response',
      };
    }

    return {
      success: (responseObj.success as boolean) ?? false,
      serverName: (responseObj.server_name as string) ?? serverName,
      status: responseObj.status as string | undefined,
      toolsCount: (responseObj.tools_count as number) ?? 0,
      error: responseObj.error as string | undefined,
    };
  }

  /**
   * Dynamically set MCP servers for the current session.
   * 
   * IMPORTANT: This is a FULL REPLACEMENT, not incremental update!
   * 
   * @param servers Map of server name to server configuration (replaces all servers)
   * @returns Response with added, removed servers and any errors
   */
  async setMcpServers(servers: Record<string, McpStdioServerDto>): Promise<McpSetServersResponse> {
    const serversObj: Record<string, unknown> = {};
    for (const [name, config] of Object.entries(servers)) {
      serversObj[name] = {
        command: config.command,
        args: config.args || [],
        env: config.env || {},
      };
    }

    const request = {
      subtype: 'mcp_set_servers',
      servers: serversObj,
    };

    const response = await this.sendControlRequestInternal(request);

    if (response.subtype === 'error') {
      throw new ControlProtocolException(`Set MCP servers failed: ${response.error}`);
    }

    const responseObj = response.response as Record<string, unknown> | undefined;
    if (!responseObj) {
      return { added: [], removed: [], errors: {} };
    }

    const errorsObj = responseObj.errors as Record<string, unknown> | undefined;
    const errors: Record<string, string> = {};
    if (errorsObj) {
      for (const [key, value] of Object.entries(errorsObj)) {
        errors[key] = String(value ?? '');
      }
    }

    return {
      added: (responseObj.added as string[]) ?? [],
      removed: (responseObj.removed as string[]) ?? [],
      errors,
    };
  }
}
