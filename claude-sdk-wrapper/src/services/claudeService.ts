import winston from 'winston';
import { SessionManager } from './sessionManager';
import { query, type SDKMessage } from './claudeCodeProxy';

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

    this.logger.info(`Stream processing message (session: ${sessionId}): ${message ? JSON.stringify(message).substring(0, 100) : 'empty'}...`);
    this.logger.info(`Options: ${JSON.stringify(customOptions || {})}`);

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
      // 构建查询参数
      const queryOptions: any = {
        prompt: message,
        abortController: this.currentAbortController,
        options: {
          maxTurns: customOptions?.maxTurns || 1,
          cwd: customOptions?.cwd || process.cwd(),
          systemPrompt: customOptions?.system || undefined,
          allowedTools: customOptions?.allowedTools || undefined,
          permissionMode: customOptions?.permissionMode || undefined
        }
      };

      // 根据模型设置不同的系统提示
      if (customOptions?.model) {
        this.logger.info(`Using model: ${customOptions.model}`);
        // 在系统提示中指定模型偏好
        if (customOptions.model === 'Opus') {
          queryOptions.options.systemPrompt = (queryOptions.options.systemPrompt || '') + '\n[Use Claude 4 Opus capabilities]';
        } else if (customOptions.model === 'Sonnet') {
          queryOptions.options.systemPrompt = (queryOptions.options.systemPrompt || '') + '\n[Use Claude 4 Sonnet capabilities]';
        }
      }

      // 收集所有消息
      const messages: SDKMessage[] = [];
      let isFirstChunk = true;

      // 使用 Claude Code SDK 的 query 函数
      for await (const sdkMessage of query(queryOptions)) {
        if (this.currentAbortController?.signal.aborted) {
          break;
        }

        messages.push(sdkMessage);
        this.logger.debug('Received SDK message:', JSON.stringify(sdkMessage));

        // 处理不同类型的消息
        if (sdkMessage.type === 'assistant') {
          // Assistant 消息包含实际的响应内容
          // 根据 SDK 文档，assistant 消息可能有不同的结构
          const assistantMsg = sdkMessage as any;
          const content = assistantMsg.text || assistantMsg.content || assistantMsg.message || '';
          
          if (content) {
            // 流式发送内容
            yield { 
              type: 'text', 
              content: content,
              session_id: sessionId 
            };
          }
        } else if (sdkMessage.type === 'result') {
          // 结果消息包含会话的元数据
          this.logger.info(`Session completed. Duration: ${(sdkMessage as any).duration_ms}ms`);
          if ((sdkMessage as any).error) {
            yield { 
              type: 'error', 
              error: (sdkMessage as any).error,
              session_id: sessionId 
            };
          }
        }
      }
      
      // 更新会话信息
      this.sessionManager.updateSession(sessionId, {
        is_first_message: false,
        message_count: session.message_count + 1
      });

    } catch (error: any) {
      this.logger.error(`Error in streamMessage: ${error}`);
      if (error.name === 'AbortError') {
        yield { type: 'error', error: 'Request was aborted', session_id: sessionId };
      } else {
        yield { type: 'error', error: String(error), session_id: sessionId };
      }
    } finally {
      // 清理状态
      this.isProcessing = false;
      this.currentAbortController = null;
    }
  }

}