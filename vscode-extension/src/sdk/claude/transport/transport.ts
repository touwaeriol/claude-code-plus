/**
 * Transport layer for communicating with Claude CLI.
 * 
 * This module provides an abstract interface for different transport implementations
 * (subprocess, etc.) that can be used to communicate with the Claude CLI.
 * 
 * Translated from Kotlin SDK: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/transport/Transport.kt
 */

/**
 * JSON element type for message parsing.
 * In TypeScript, we use `unknown` instead of Kotlin's JsonElement.
 */
export type JsonMessage = unknown

/**
 * Event emitter callback type for receiving messages.
 */
export type MessageCallback = (message: JsonMessage) => void

/**
 * Error callback type for transport errors.
 */
export type ErrorCallback = (error: Error) => void

/**
 * Close callback type for transport closure.
 */
export type CloseCallback = (code?: number, signal?: string) => void

/**
 * Transport events interface.
 */
export interface TransportEvents {
  /** Called when a JSON message is received from the CLI */
  onMessage?: MessageCallback
  /** Called when an error occurs */
  onError?: ErrorCallback
  /** Called when the transport is closed */
  onClose?: CloseCallback
}

/**
 * Abstract transport interface for communicating with Claude CLI.
 * 
 * This interface defines the contract for different transport implementations
 * that can be used to send and receive JSON-RPC messages with the Claude CLI.
 */
export interface Transport {
  /**
   * Connect to the Claude CLI process.
   * 
   * @throws TransportError if connection fails
   */
  connect(): Promise<void>

  /**
   * Write data to the CLI stdin.
   * 
   * @param data The string data to write (typically JSON)
   * @throws TransportError if write fails
   */
  write(data: string): Promise<void>

  /**
   * Start reading messages from CLI stdout.
   * Messages are emitted via the onMessage callback.
   * 
   * This method returns an AsyncIterable that yields JSON messages.
   * Alternatively, you can use the event-based API via setEventHandlers().
   */
  readMessages(): AsyncIterable<JsonMessage>

  /**
   * Set event handlers for the transport.
   * 
   * @param events The event handlers to set
   */
  setEventHandlers(events: TransportEvents): void

  /**
   * Check if the transport is ready for communication.
   * Ready means connected and the process is alive.
   */
  isReady(): boolean

  /**
   * End the input stream to the CLI.
   * This signals EOF to the CLI process.
   */
  endInput(): Promise<void>

  /**
   * Close the transport and cleanup resources.
   * This terminates the CLI process if running.
   */
  close(): Promise<void>

  /**
   * Check if the transport is connected.
   * Connected means the process was started and not yet closed.
   */
  isConnected(): boolean
}

/**
 * Base error class for transport-related errors.
 */
export class TransportError extends Error {
  constructor(
    message: string,
    public readonly cause?: Error
  ) {
    super(message)
    this.name = 'TransportError'
    if (cause) {
      this.stack = `${this.stack}\nCaused by: ${cause.stack}`
    }
  }
}

/**
 * Error thrown when the CLI is not found.
 */
export class CLINotFoundError extends TransportError {
  constructor(message: string, cause?: Error) {
    super(message, cause)
    this.name = 'CLINotFoundError'
  }

  static withInstallInstructions(nodeInstalled: boolean): CLINotFoundError {
    const instructions = nodeInstalled
      ? 'Claude CLI not found. Please install it using: npm install -g @anthropic-ai/claude-code'
      : 'Neither Claude CLI nor Node.js was found. Please install Node.js first, then install Claude CLI using: npm install -g @anthropic-ai/claude-code'
    return new CLINotFoundError(instructions)
  }
}

/**
 * Error thrown when Node.js is not found.
 */
export class NodeNotFoundError extends TransportError {
  constructor(message: string, cause?: Error) {
    super(message, cause)
    this.name = 'NodeNotFoundError'
  }

  static notFound(): NodeNotFoundError {
    return new NodeNotFoundError(
      'Node.js not found. Please install Node.js and ensure it is in your PATH, ' +
        'or configure the Node.js path in settings.'
    )
  }

  static invalidConfiguredPath(path: string): NodeNotFoundError {
    return new NodeNotFoundError(
      `Configured Node.js path is invalid or not executable: ${path}`
    )
  }
}

/**
 * Error thrown when connection to CLI fails.
 */
export class CLIConnectionError extends TransportError {
  constructor(message: string, cause?: Error) {
    super(message, cause)
    this.name = 'CLIConnectionError'
  }
}

/**
 * Error thrown when a process operation fails.
 */
export class ProcessError extends TransportError {
  constructor(
    message: string,
    public readonly exitCode?: number,
    public readonly stderr?: string,
    cause?: Error
  ) {
    super(message, cause)
    this.name = 'ProcessError'
  }
}

/**
 * Error thrown when JSON decoding fails.
 */
export class JSONDecodeError extends TransportError {
  constructor(
    message: string,
    public readonly originalLine?: string,
    cause?: Error
  ) {
    super(message, cause)
    this.name = 'JSONDecodeError'
  }
}
