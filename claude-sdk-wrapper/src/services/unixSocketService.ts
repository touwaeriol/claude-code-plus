import * as net from 'net';
import * as os from 'os';
import * as path from 'path';
import * as fs from 'fs';
import { ClaudeService } from './claudeService';
import { Logger } from '../utils/logger';
import { v4 as uuidv4 } from 'uuid';

/**
 * JSON-RPC 2.0 协议定义
 */
interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: string | number;
  method: string;
  params?: any;
}

interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: string | number;
  result?: any;
  error?: {
    code: number;
    message: string;
    data?: any;
  };
}

interface JsonRpcNotification {
  jsonrpc: '2.0';
  method: string;
  params?: any;
}

/**
 * 基于 Unix Domain Socket 的 JSON-RPC 服务
 */
export class UnixSocketService {
  private logger: Logger;
  private claudeService: ClaudeService;
  private server: net.Server | null = null;
  private socketPath: string;
  private clients: Map<string, net.Socket> = new Map();
  private activeStreams: Map<string, boolean> = new Map();
  
  constructor(claudeService: ClaudeService) {
    this.logger = new Logger('UnixSocketService');
    this.claudeService = claudeService;
    
    // 生成唯一的 socket 文件路径
    const tmpDir = os.tmpdir();
    const timestamp = Date.now();
    const pid = process.pid;
    this.socketPath = path.join(tmpDir, `claude-code-plus-${pid}-${timestamp}.sock`);
  }
  
  /**
   * 启动 Unix socket 服务器
   */
  public async start(): Promise<string> {
    return new Promise((resolve, reject) => {
      try {
        // 确保旧的 socket 文件不存在
        if (fs.existsSync(this.socketPath)) {
          fs.unlinkSync(this.socketPath);
        }
        
        // 创建服务器
        this.server = net.createServer((socket) => {
          const clientId = uuidv4();
          this.clients.set(clientId, socket);
          this.logger.info(`Client connected: ${clientId}`);
          
          // 设置编码
          socket.setEncoding('utf8');
          
          // 缓冲区用于处理分片的 JSON
          let buffer = '';
          
          socket.on('data', (data) => {
            buffer += data;
            
            // 尝试解析完整的 JSON 消息（以换行符分隔）
            const lines = buffer.split('\n');
            buffer = lines.pop() || ''; // 保留最后一个不完整的行
            
            for (const line of lines) {
              if (line.trim()) {
                this.handleMessage(clientId, socket, line.trim());
              }
            }
          });
          
          socket.on('end', () => {
            this.logger.info(`Client disconnected: ${clientId}`);
            this.clients.delete(clientId);
            // 清理该客户端的活动流
            for (const [streamId, ] of this.activeStreams) {
              if (streamId.startsWith(clientId)) {
                this.activeStreams.delete(streamId);
              }
            }
          });
          
          socket.on('error', (err) => {
            this.logger.error(`Socket error for client ${clientId}:`, err);
            this.clients.delete(clientId);
          });
        });
        
        // 监听 Unix socket
        this.server.listen(this.socketPath, () => {
          this.logger.info(`Unix socket server listening on: ${this.socketPath}`);
          
          // 输出 socket 路径供 Java 客户端读取
          console.log(`SOCKET_PATH:${this.socketPath}`);
          
          resolve(this.socketPath);
        });
        
        this.server.on('error', (err) => {
          this.logger.error('Server error:', err);
          reject(err);
        });
        
      } catch (error: any) {
        this.logger.error('Failed to start Unix socket server:', error);
        reject(error);
      }
    });
  }
  
  /**
   * 处理客户端消息
   */
  private async handleMessage(clientId: string, socket: net.Socket, data: string) {
    try {
      const request: JsonRpcRequest = JSON.parse(data);
      
      if (request.jsonrpc !== '2.0') {
        this.sendError(socket, request.id, -32600, 'Invalid Request');
        return;
      }
      
      this.logger.debug(`Received request: ${request.method} (${request.id})`);
      
      // 处理不同的方法
      switch (request.method) {
        case 'health':
          await this.handleHealth(socket, request);
          break;
          
        case 'stream':
          await this.handleStream(clientId, socket, request);
          break;
          
        case 'abort':
          await this.handleAbort(clientId, socket, request);
          break;
          
        default:
          this.sendError(socket, request.id, -32601, `Method not found: ${request.method}`);
      }
      
    } catch (error: any) {
      this.logger.error('Error handling message:', error);
      try {
        const errRequest = JSON.parse(data);
        this.sendError(socket, errRequest.id || null, -32700, 'Parse error');
      } catch {
        this.sendError(socket, null, -32700, 'Parse error');
      }
    }
  }
  
  /**
   * 处理健康检查
   */
  private async handleHealth(socket: net.Socket, request: JsonRpcRequest) {
    try {
      const health = await this.claudeService.checkHealth();
      this.sendResponse(socket, request.id, health);
    } catch (error: any) {
      this.sendError(socket, request.id, -32603, error.message);
    }
  }
  
  /**
   * 处理流式消息
   */
  private async handleStream(clientId: string, socket: net.Socket, request: JsonRpcRequest) {
    const { message, options } = request.params || {};
    
    if (!message) {
      this.sendError(socket, request.id, -32602, 'Invalid params: message is required');
      return;
    }
    
    const streamId = `${clientId}-${request.id}`;
    this.activeStreams.set(streamId, true);
    
    try {
      // 发送开始通知
      this.sendNotification(socket, 'stream.start', { 
        id: request.id,
        timestamp: new Date().toISOString()
      });
      
      // 创建会话
      const sessionId = this.claudeService.sessionManager.createSession();
      
      // 使用异步生成器处理流
      for await (const chunk of this.claudeService.streamMessage(message, sessionId, options)) {
        // 检查流是否已被中止
        if (!this.activeStreams.get(streamId)) {
          throw new Error('Stream aborted');
        }
        
        if (chunk.type === 'content' && chunk.content) {
          // 发送数据块通知
          this.sendNotification(socket, 'stream.chunk', {
            id: request.id,
            chunk: chunk.content
          });
        } else if (chunk.type === 'error') {
          throw new Error(chunk.error || 'Unknown error');
        }
      }
      
      // 发送完成响应
      this.activeStreams.delete(streamId);
      this.sendResponse(socket, request.id, { 
        status: 'completed',
        timestamp: new Date().toISOString()
      });
      
    } catch (error: any) {
      this.activeStreams.delete(streamId);
      
      if (error.message === 'Stream aborted') {
        this.sendResponse(socket, request.id, { 
          status: 'aborted',
          timestamp: new Date().toISOString()
        });
      } else {
        this.sendError(socket, request.id, -32603, error.message);
      }
    }
  }
  
  /**
   * 处理中止请求
   */
  private async handleAbort(clientId: string, socket: net.Socket, request: JsonRpcRequest) {
    try {
      const { streamId } = request.params || {};
      
      // 如果指定了 streamId，中止特定的流
      if (streamId) {
        const fullStreamId = `${clientId}-${streamId}`;
        if (this.activeStreams.has(fullStreamId)) {
          this.activeStreams.delete(fullStreamId);
          this.sendResponse(socket, request.id, { aborted: true, streamId });
          return;
        }
      }
      
      // 否则尝试中止 Claude 服务的当前请求
      const aborted = this.claudeService.abort();
      this.sendResponse(socket, request.id, { aborted });
      
    } catch (error: any) {
      this.sendError(socket, request.id, -32603, error.message);
    }
  }
  
  /**
   * 发送响应
   */
  private sendResponse(socket: net.Socket, id: string | number, result: any) {
    const response: JsonRpcResponse = {
      jsonrpc: '2.0',
      id,
      result
    };
    this.send(socket, response);
  }
  
  /**
   * 发送错误
   */
  private sendError(socket: net.Socket, id: string | number | null, code: number, message: string, data?: any) {
    const response: JsonRpcResponse = {
      jsonrpc: '2.0',
      id: id || 0,
      error: { code, message, data }
    };
    this.send(socket, response);
  }
  
  /**
   * 发送通知（无需响应）
   */
  private sendNotification(socket: net.Socket, method: string, params?: any) {
    const notification: JsonRpcNotification = {
      jsonrpc: '2.0',
      method,
      params
    };
    this.send(socket, notification);
  }
  
  /**
   * 发送数据到 socket
   */
  private send(socket: net.Socket, data: any) {
    try {
      const json = JSON.stringify(data) + '\n';
      socket.write(json);
    } catch (error: any) {
      this.logger.error('Failed to send data:', error);
    }
  }
  
  /**
   * 停止服务
   */
  public stop() {
    try {
      // 关闭所有客户端连接
      for (const [clientId, socket] of this.clients) {
        socket.end();
      }
      this.clients.clear();
      this.activeStreams.clear();
      
      // 关闭服务器
      if (this.server) {
        this.server.close();
        this.server = null;
      }
      
      // 删除 socket 文件
      if (fs.existsSync(this.socketPath)) {
        fs.unlinkSync(this.socketPath);
      }
      
      this.logger.info('Unix socket server stopped');
    } catch (error: any) {
      this.logger.error('Error stopping server:', error);
    }
  }
  
  /**
   * 获取 socket 路径
   */
  public getSocketPath(): string {
    return this.socketPath;
  }
}