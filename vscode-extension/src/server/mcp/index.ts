/**
 * MCP Server Module Exports
 */

export { withMcpContext, currentConnectId, type McpCallContextData } from './mcpCallContext';
export { 
  McpHttpGateway, 
  getMcpHttpGateway, 
  setMcpHttpGateway,
  HEADER_CONNECT_ID 
} from './mcpHttpGateway';
