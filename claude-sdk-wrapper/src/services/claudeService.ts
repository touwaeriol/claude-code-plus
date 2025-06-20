import winston from 'winston';
import { SessionManager } from './sessionManager';

export interface StreamChunk {
  type: string;
  content?: string;
  message_type?: string;
  session_id?: string;
  error?: string;
  tool_use_id?: string;
}

export class ClaudeService {
  private isProcessing = false;
  private currentAbortController: AbortController | null = null;

  constructor(
    private logger: winston.Logger,
    public sessionManager: SessionManager
  ) {
    this.logger.info('Claude Service created');
  }


  /**
   * 健康检查
   */
  async checkHealth() {
    return {
      isHealthy: true,
      isProcessing: this.isProcessing
    };
  }
  
  /**
   * 获取服务状态
   */
  getStatus() {
    return {
      sdk_available: false, // 暂时禁用
      has_active_session: this.sessionManager.getCurrentSession() !== null,
      is_processing: this.isProcessing
    };
  }

  /**
   * 中止当前请求
   */
  abort(): boolean {
    if (this.currentAbortController) {
      this.logger.info('Aborting current request...');
      this.currentAbortController.abort();
      this.currentAbortController = null;
      this.isProcessing = false;
      return true;
    }
    return false;
  }

  /**
   * 流式处理消息
   */
  async *streamMessage(
    message: string,
    sessionId: string,
    customOptions?: any
  ): AsyncGenerator<StreamChunk> {
    // 检查是否正在处理
    if (this.isProcessing) {
      yield { type: 'error', error: 'Service is busy. Only one request can be processed at a time.' };
      return;
    }


    this.logger.info(`Stream processing message (session: ${sessionId}): ${message.substring(0, 100)}...`);

    // 获取会话
    const session = this.sessionManager.getSession(sessionId);
    if (!session) {
      yield { type: 'error', error: 'Session expired or not found' };
      return;
    }

    // 标记为正在处理
    this.isProcessing = true;
    this.currentAbortController = new AbortController();

    try {
      // 暂时返回模拟响应
      yield { 
        type: 'text', 
        content: 'Claude SDK integration is temporarily disabled while we update the packaging configuration. The service is running correctly.',
        session_id: sessionId 
      };
      
      // 更新会话信息
      this.sessionManager.updateSession(sessionId, {
        is_first_message: false,
        message_count: session.message_count + 1
      });

    } catch (error: any) {
      this.logger.error(`Error in streamMessage: ${error}`);
      yield { type: 'error', error: String(error) };
    } finally {
      // 清理状态
      this.isProcessing = false;
      this.currentAbortController = null;
    }
  }

  /**
   * 获取单次完整响应
   */
  async getSingleResponse(
    message: string,
    sessionId: string,
    customOptions?: any
  ): Promise<{ success: boolean; response?: string; error?: string; session_id: string }> {
    const responseChunks: string[] = [];
    let error: string | null = null;

    try {
      for await (const chunk of this.streamMessage(message, sessionId, customOptions)) {
        if (chunk.type === 'error') {
          error = chunk.error || 'Unknown error';
          break;
        } else if (chunk.type === 'text') {
          responseChunks.push(chunk.content || '');
        }
      }

      if (error) {
        return { success: false, error, session_id: sessionId };
      }

      return {
        success: true,
        response: responseChunks.join(''),
        session_id: sessionId
      };
    } catch (err) {
      return {
        success: false,
        error: String(err),
        session_id: sessionId
      };
    }
  }
}