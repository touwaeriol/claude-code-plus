import { ClaudeService } from './claudeService';
import { Logger } from '../utils/logger';
import readline from 'readline';

/**
 * 简单的消息协议定义
 */
interface Message {
  id: string;
  type: 'request' | 'response' | 'stream' | 'error';
  method?: string;
  params?: any;
  result?: any;
  error?: {
    code: number;
    message: string;
  };
}

/**
 * 基于标准输入输出的消息服务
 * 使用换行符分隔的 JSON 消息（NDJSON 格式）
 */
export class StdinMessageService {
  private logger: Logger;
  private claudeService: ClaudeService;
  private rl: readline.Interface;
  
  constructor(claudeService: ClaudeService) {
    this.logger = new Logger('StdinMessageService');
    this.claudeService = claudeService;
    
    // 创建 readline 接口
    this.rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
      terminal: false
    });
  }
  
  /**
   * 启动消息服务
   */
  public start() {
    this.logger.info('Starting stdin message service...');
    
    // 监听每一行输入
    this.rl.on('line', async (line: string) => {
      try {
        const message: Message = JSON.parse(line.trim());
        await this.handleMessage(message);
      } catch (error: any) {
        this.logger.error('Failed to parse message:', error);
        this.sendError('unknown', -32700, 'Parse error');
      }
    });
    
    // 发送就绪消息
    this.sendResponse('ready', { status: 'ready', pid: process.pid });
    this.logger.info('Stdin message service started');
  }
  
  /**
   * 处理消息
   */
  private async handleMessage(message: Message) {
    if (message.type !== 'request') {
      return;
    }
    
    const { id, method, params } = message;
    
    try {
      switch (method) {
        case 'health':
          await this.handleHealth(id);
          break;
          
        case 'stream':
          await this.handleStream(id, params);
          break;
          
        case 'abort':
          await this.handleAbort(id);
          break;
          
        default:
          this.sendError(id, -32601, `Method not found: ${method}`);
      }
    } catch (error: any) {
      this.logger.error(`Error handling ${method}:`, error);
      this.sendError(id, -32603, error.message);
    }
  }
  
  /**
   * 处理健康检查
   */
  private async handleHealth(id: string) {
    const health = await this.claudeService.checkHealth();
    this.sendResponse(id, health);
  }
  
  /**
   * 处理流式消息
   */
  private async handleStream(id: string, params: any) {
    const { message, options } = params || {};
    
    if (!message) {
      this.sendError(id, -32602, 'Message is required');
      return;
    }
    
    try {
      // 发送开始响应
      this.sendStream(id, { type: 'start' });
      
      // 创建会话
      const sessionId = this.claudeService.sessionManager.createSession();
      
      // 使用异步生成器处理流
      for await (const chunk of this.claudeService.streamMessage(message, sessionId, options)) {
        if (chunk.type === 'content' && chunk.content) {
          // 发送流式数据块
          this.sendStream(id, { type: 'chunk', data: chunk.content });
        } else if (chunk.type === 'error') {
          throw new Error(chunk.error || 'Unknown error');
        }
      }
      
      // 发送完成响应
      this.sendStream(id, { type: 'end' });
      
    } catch (error: any) {
      if (error.message?.includes('aborted')) {
        this.sendStream(id, { type: 'aborted' });
      } else {
        this.sendError(id, -32603, error.message);
      }
    }
  }
  
  /**
   * 处理中止请求
   */
  private async handleAbort(id: string) {
    const aborted = this.claudeService.abort();
    this.sendResponse(id, { aborted });
  }
  
  /**
   * 发送响应
   */
  private sendResponse(id: string, result: any) {
    const message: Message = {
      id,
      type: 'response',
      result
    };
    this.send(message);
  }
  
  /**
   * 发送流式消息
   */
  private sendStream(id: string, result: any) {
    const message: Message = {
      id,
      type: 'stream',
      result
    };
    this.send(message);
  }
  
  /**
   * 发送错误
   */
  private sendError(id: string, code: number, message: string) {
    const errorMessage: Message = {
      id,
      type: 'error',
      error: { code, message }
    };
    this.send(errorMessage);
  }
  
  /**
   * 发送消息到标准输出
   */
  private send(message: Message) {
    // 使用 console.log 发送到 stdout
    console.log(JSON.stringify(message));
  }
  
  /**
   * 停止服务
   */
  public stop() {
    this.rl.close();
    this.logger.info('Stdin message service stopped');
  }
}