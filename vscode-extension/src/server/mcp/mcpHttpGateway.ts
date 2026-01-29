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
import { withMcpContext, type McpCallContextData } from './mcpCallContext';

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

    return new Promise((resolve, reject) => {
      this.httpServer = http.createServer(async (req, res) => {
        await this.handleRequest(req, res);
      });

      this.httpServer.on('error', (err) => {
        this.log?.(`[McpHttpGateway] Server error: ${err.message}`);
        reject(err);
      });

      // Listen on random port
      this.httpServer.listen(0, '127.0.0.1', () => {
        const address = this.httpServer!.address();
        if (address && typeof address !== 'string') {
          this.actualPort = address.port;
          this.started = true;
          this.log?.(`[McpHttpGateway] HTTP gateway started on http://127.0.0.1:${this.actualPort}/mcp`);
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

    // Wrap the request handling with MCP context
    const context: McpCallContextData = { connectId: connectId || undefined };
    
    try {
      await withMcpContext(context, async () => {
        await endpoint.transport.handleRequest(req, res);
      });
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

    // Create transport for this server
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => crypto.randomUUID(),
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
