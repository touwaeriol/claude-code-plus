/**
 * MCP HTTP Gateway for VS Code Extension
 * 
 * Provides HTTP endpoints for built-in MCP servers, allowing Claude CLI to call them.
 * Translated from: ai-agent-server/src/main/kotlin/com/asakii/server/mcp/McpHttpGateway.kt
 * 
 * Key differences from JetBrains version:
 * - Uses Node.js http module instead of Jetty
 * - Uses @modelcontextprotocol/sdk's StreamableHTTPServerTransport
 * - Uses AsyncLocalStorage instead of Kotlin coroutine context
 */

import * as http from 'http';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
// Note: mcpCallContext is no longer needed here since we use req.auth for context passing
// The AsyncLocalStorage approach was unreliable; using req.auth is the official MCP SDK mechanism

// Header name for connect ID
export const HEADER_CONNECT_ID = 'x-mcp-connect-id';

// Keep-alive interval (10 seconds)
const KEEP_ALIVE_INTERVAL_MS = 10000;

/**
 * MCP Server endpoint information
 */
interface McpEndpoint {
  path: string;
  server: McpServer;
  transport: StreamableHTTPServerTransport;
}

/**
 * MCP HTTP Gateway
 * 
 * Project-level instance design:
 * - Each VS Code window has its own McpHttpGateway instance
 * - Each instance has its own HTTP server and port
 * - Solves MCP routing issues when multiple projects are open
 * 
 * Instance reuse design:
 * - Within the same gateway, each serverName corresponds to one Transport
 * - MCP SDK manages Transport and Session
 * - We only handle HTTP routing and connectId extraction
 */
export class McpHttpGateway {
  /** MCP endpoints: key = serverName */
  private endpoints: Map<string, McpEndpoint> = new Map();
  
  private httpServer: http.Server | null = null;
  private actualPort: number = 0;
  private started: boolean = false;
  
  private log: ((message: string) => void) | undefined;

  constructor(logger?: (message: string) => void) {
    this.log = logger;
  }

  /**
   * Ensure the gateway is started
   * @returns The port number
   */
  async ensureStarted(): Promise<number> {
    if (this.started && this.httpServer) {
      return this.actualPort;
    }

    // Try multiple times with different port strategies
    const maxRetries = 5;
    let lastError: Error | null = null;

    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        const port = await this.tryStartServer(attempt);
        return port;
      } catch (err) {
        lastError = err as Error;
        this.log?.(`[McpHttpGateway] Attempt ${attempt + 1}/${maxRetries} failed: ${lastError.message}`);
        
        // Clean up failed server
        if (this.httpServer) {
          this.httpServer.close();
          this.httpServer = null;
        }
        
        // Wait a bit before retrying
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }

    throw lastError || new Error('Failed to start HTTP server after multiple attempts');
  }

  /**
   * Try to start the server on a specific attempt
   */
  private async tryStartServer(attempt: number): Promise<number> {
    return new Promise((resolve, reject) => {
      this.httpServer = http.createServer(async (req, res) => {
        await this.handleRequest(req, res);
      });

      this.httpServer.on('error', (err) => {
        this.log?.(`[McpHttpGateway] Server error: ${err.message}`);
        reject(err);
      });

      // Use different port strategies based on attempt
      // Attempt 0: random port (port 0)
      // Attempt 1-4: specific high ports to avoid conflicts
      const portOptions = [0, 0, 0, 0, 0]; // All use random port but retry gives different results
      const port = portOptions[attempt] || 0;

      this.httpServer.listen(port, '127.0.0.1', () => {
        const address = this.httpServer!.address();
        if (address && typeof address !== 'string') {
          this.actualPort = address.port;
          this.started = true;
          this.log?.(`[McpHttpGateway] HTTP gateway started on http://127.0.0.1:${this.actualPort}/mcp (attempt ${attempt + 1})`);
          resolve(this.actualPort);
        } else {
          reject(new Error('Failed to get server address'));
        }
      });
    });
  }

  /**
   * Handle incoming HTTP request
   */
  private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
    const path = req.url || '';
    this.log?.(`[McpHttpGateway] Incoming request: ${req.method} ${path}`);

    // Extract connectId from header
    const connectIdHeader = req.headers[HEADER_CONNECT_ID];
    const connectId = Array.isArray(connectIdHeader) ? connectIdHeader[0] : connectIdHeader;

    // Find matching endpoint
    const endpoint = this.resolveEndpoint(path);
    if (!endpoint) {
      this.log?.(`[McpHttpGateway] No endpoint found for path: ${path}`);
      res.statusCode = 404;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'Not Found', path }));
      return;
    }

    // Pass connectId via req.auth.extra, which will be available in tool handlers as extra.authInfo
    // This is the official MCP SDK mechanism, equivalent to JB's contextExtractor + callHandler
    // AuthInfo interface requires token, clientId, scopes fields - we use extra for custom data
    const reqWithAuth = req as typeof req & { auth?: { 
      token: string;
      clientId: string;
      scopes: string[];
      extra?: Record<string, unknown>;
    } };
    reqWithAuth.auth = { 
      token: 'builtin-mcp',  // Placeholder for built-in MCP servers
      clientId: 'claude-code-plus',
      scopes: [],
      extra: { connectId: connectId || undefined }
    };
    
    this.log?.(`[McpHttpGateway] Setting req.auth = ${JSON.stringify(reqWithAuth.auth)}`);
    
    try {
      await endpoint.transport.handleRequest(reqWithAuth, res);
    } catch (error) {
      this.log?.(`[McpHttpGateway] Request handling error: ${error}`);
      if (!res.headersSent) {
        res.statusCode = 500;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Internal Server Error' }));
      }
    }
  }

  /**
   * Resolve endpoint from request path
   */
  private resolveEndpoint(path: string): McpEndpoint | undefined {
    const normalized = path.replace(/\/$/, '');
    const serverName = normalized.replace(/^\/mcp\//, '');
    
    if (!serverName || serverName === normalized) {
      return undefined;
    }
    
    return this.endpoints.get(serverName);
  }

  /**
   * Register an MCP server
   * 
   * @param serverName MCP server name
   * @param server MCP server instance
   * @returns MCP endpoint URL
   */
  async registerServer(serverName: string, server: McpServer): Promise<string> {
    await this.ensureStarted();

    // Reuse existing endpoint if available
    const existing = this.endpoints.get(serverName);
    if (existing) {
      this.log?.(`[McpHttpGateway] Reusing endpoint: ${serverName}`);
      return this.buildUrl(existing.path);
    }

    const endpointPath = `/mcp/${serverName}`;

    // Create transport for this server.
    //
    // IMPORTANT:
    // Use stateless mode (sessionIdGenerator: undefined) so that:
    // - External debugging clients (curl/node scripts) can connect repeatedly.
    // - The endpoint doesn't get stuck in "Server already initialized" state
    //   when multiple clients attempt to initialize.
    //
    // Claude CLI can still initialize normally; the server transport will simply
    // not require Mcp-Session-Id headers on subsequent requests.
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
    });

    // Connect the server to the transport
    await server.connect(transport);

    const endpoint: McpEndpoint = {
      path: endpointPath,
      server,
      transport
    };

    this.endpoints.set(serverName, endpoint);
    this.log?.(`[McpHttpGateway] Registered endpoint: ${serverName} -> ${endpointPath}`);

    return this.buildUrl(endpointPath);
  }

  /**
   * Unregister an MCP server
   */
  async unregisterServer(serverName: string): Promise<void> {
    const endpoint = this.endpoints.get(serverName);
    if (endpoint) {
      try {
        await endpoint.transport.close();
      } catch (e) {
        this.log?.(`[McpHttpGateway] Error closing transport: ${e}`);
      }
      this.endpoints.delete(serverName);
      this.log?.(`[McpHttpGateway] Unregistered endpoint: ${serverName}`);
    }
  }

  /**
   * Build full URL for an endpoint path
   */
  private buildUrl(path: string): string {
    return `http://127.0.0.1:${this.actualPort}${path}`;
  }

  /**
   * Build server URL for a given server name
   */
  buildServerUrl(serverName: string): string {
    return this.buildUrl(`/mcp/${serverName}`);
  }

  /**
   * Get the gateway port
   */
  getPort(): number {
    return this.actualPort;
  }

  /**
   * Check if gateway is started
   */
  isStarted(): boolean {
    return this.started;
  }

  /**
   * Get list of registered server names
   */
  getRegisteredServers(): string[] {
    return Array.from(this.endpoints.keys());
  }

  /**
   * Shutdown the gateway
   */
  async shutdown(): Promise<void> {
    this.log?.(`[McpHttpGateway] Shutting down HTTP gateway (port=${this.actualPort}, endpoints=${this.endpoints.size})`);

    // Close all transports
    for (const [name, endpoint] of this.endpoints) {
      try {
        await endpoint.transport.close();
        this.log?.(`[McpHttpGateway] Closed transport: ${name}`);
      } catch (e) {
        this.log?.(`[McpHttpGateway] Error closing transport ${name}: ${e}`);
      }
    }
    this.endpoints.clear();

    // Close HTTP server
    if (this.httpServer) {
      await new Promise<void>((resolve) => {
        this.httpServer!.close(() => {
          this.log?.(`[McpHttpGateway] HTTP server stopped`);
          resolve();
        });
      });
      this.httpServer = null;
    }

    this.started = false;
    this.actualPort = 0;
    this.log?.(`[McpHttpGateway] HTTP gateway shutdown complete`);
  }
}

/**
 * Export singleton instance (will be replaced by HttpApiServer-managed instance)
 */
let gatewayInstance: McpHttpGateway | null = null;

export function getMcpHttpGateway(): McpHttpGateway | null {
  return gatewayInstance;
}

export function setMcpHttpGateway(gateway: McpHttpGateway | null): void {
  gatewayInstance = gateway;
}
