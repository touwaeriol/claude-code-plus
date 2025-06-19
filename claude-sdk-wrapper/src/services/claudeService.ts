// 动态导入 ES 模块
let query: any;

// 在初始化时加载 SDK
async function loadSDK() {
  try {
    const sdk = await import('@anthropic-ai/claude-code');
    query = sdk.query;
    return true;
  } catch (error) {
    console.error('Failed to load @anthropic-ai/claude-code:', error);
    return false;
  }
}
import { Logger } from 'winston';
import { SessionManager } from './sessionManager';

export interface StreamChunk {
  type: string;
  content?: string;
  message_type?: string;
  session_id?: string;
  error?: string;
  tool_use_id?: string;  // 添加工具使用 ID
}

export class ClaudeService {
  private isInitialized = false;
  private sdkLoaded = false;
  private isProcessing = false;
  private currentAbortController: AbortController | null = null;
  private defaultOptions: any = {
    system_prompt: 'You are a helpful assistant.',
    max_turns: 20
  };

  constructor(
    private logger: Logger,
    public sessionManager: SessionManager
  ) {
    // 尝试加载 SDK
    this.loadSDKAsync();
  }
  
  private async loadSDKAsync() {
    this.sdkLoaded = await loadSDK();
    if (this.sdkLoaded) {
      this.logger.info('Claude SDK loaded successfully');
    } else {
      this.logger.error('Failed to load Claude SDK');
    }
  }

  /**
   * 初始化服务
   */
  initialize(config: any): { success: boolean; message?: string; error?: string } {
    try {
      this.logger.info(`Initializing with config: ${JSON.stringify(config)}`);
      
      // 更新默认选项
      if (config.system_prompt) {
        this.defaultOptions.system_prompt = config.system_prompt;
      }
      if (config.max_turns) {
        this.defaultOptions.max_turns = config.max_turns;
      }
      if (config.cwd) {
        this.defaultOptions.cwd = config.cwd;
      }
      if (config.allowed_tools) {
        this.defaultOptions.allowed_tools = config.allowed_tools;
      }
      if (config.permission_mode) {
        this.defaultOptions.permission_mode = config.permission_mode;
      }
      if (config.max_thinking_tokens) {
        this.defaultOptions.max_thinking_tokens = config.max_thinking_tokens;
      }
      if (config.model) {
        this.defaultOptions.model = config.model;
      }

      this.isInitialized = true;
      this.logger.info('Service initialized successfully');
      return { success: true, message: 'Service initialized with Claude SDK' };
    } catch (error) {
      this.logger.error(`Failed to initialize: ${error}`);
      return { success: false, error: String(error) };
    }
  }

  /**
   * 健康检查
   */
  async checkHealth() {
    return {
      isHealthy: this.isInitialized && this.sdkLoaded,
      isProcessing: this.isProcessing,
      activeSessions: this.sessionManager.getActiveSessions().length
    };
  }
  
  /**
   * 获取服务状态
   */
  getStatus() {
    return {
      initialized: this.isInitialized,
      sdk_available: true,
      active_sessions: this.sessionManager.getActiveSessions().length,
      is_processing: this.isProcessing
    };
  }

  /**
   * 中断当前处理
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
   * 流式发送消息
   */
  async *streamMessage(
    message: string,
    sessionId: string,
    customOptions?: any
  ): AsyncGenerator<StreamChunk> {
    // 检查是否正在处理
    if (this.isProcessing) {
      yield { type: 'error', error: 'Service is busy processing another request. Please wait or abort the current request.' };
      return;
    }

    // 自动初始化
    if (!this.isInitialized) {
      this.logger.info('Service not initialized, attempting auto-initialization...');
      const initConfig = customOptions?.cwd ? { cwd: customOptions.cwd } : {};
      const initResult = this.initialize(initConfig);
      if (!initResult.success) {
        yield { type: 'error', error: `Failed to auto-initialize: ${initResult.error}` };
        return;
      }
    }

    // 确保 SDK 已加载
    if (!this.sdkLoaded) {
      this.logger.info('SDK not loaded, attempting to load...');
      this.sdkLoaded = await loadSDK();
      if (!this.sdkLoaded) {
        yield { type: 'error', error: 'Failed to load Claude SDK' };
        return;
      }
    }

    // 再次检查 query 函数是否存在
    if (!query || typeof query !== 'function') {
      yield { type: 'error', error: 'Claude SDK query function not available' };
      return;
    }

    this.logger.info(`Stream processing message (session: ${sessionId}): ${message.substring(0, 100)}...`);

    // 获取会话
    const session = this.sessionManager.getSession(sessionId);
    if (!session) {
      yield { type: 'error', error: 'Session expired or not found' };
      return;
    }

    // 标记开始处理
    this.isProcessing = true;
    
    // 创建新的 AbortController
    this.currentAbortController = new AbortController();

    try {
      // 准备查询参数
      const queryParams = {
        prompt: message,
        abortController: this.currentAbortController,
        options: {
          maxTurns: this.defaultOptions.max_turns || 20,
          systemPrompt: this.defaultOptions.system_prompt,
          cwd: this.defaultOptions.cwd,
          allowedTools: this.defaultOptions.allowed_tools,
          permissionMode: this.defaultOptions.permission_mode,
          maxThinkingTokens: this.defaultOptions.max_thinking_tokens,
          model: this.defaultOptions.model,
          ...customOptions
        }
      };

      // 使用 Claude SDK
      const responseChunks: string[] = [];
      
      for await (const msg of query(queryParams)) {
        const msgType = msg.constructor.name;
        
        // 根据消息类型处理
        if (msg.type === 'assistant' && msg.message) {
          // 处理助手消息
          const assistantMsg = msg.message;
          if (assistantMsg.content && Array.isArray(assistantMsg.content)) {
            for (const block of assistantMsg.content) {
              if (block.type === 'text' && block.text) {
                responseChunks.push(block.text);
                yield {
                  type: 'text',
                  message_type: 'assistant',
                  content: block.text,
                  session_id: sessionId
                };
              } else if (block.type === 'tool_use') {
                yield {
                  type: 'tool_use',
                  message_type: 'assistant',
                  content: JSON.stringify(block),
                  session_id: sessionId
                };
              }
            }
          }
        } else if (msg.type === 'user' && msg.message) {
          // 处理用户消息（包含工具结果）
          const userMsg = msg.message;
          if (userMsg.content && Array.isArray(userMsg.content)) {
            for (const block of userMsg.content) {
              if (block.type === 'tool_result') {
                this.logger.info(`Tool result from ${block.tool_use_id}: ${block.content?.substring(0, 200)}...`);
                yield {
                  type: 'tool_result',
                  message_type: 'user',
                  content: block.content,
                  tool_use_id: block.tool_use_id,
                  session_id: sessionId
                };
              }
            }
          }
        } else if (msg.type === 'system') {
          // 系统消息，可以选择性地返回
          this.logger.info(`System message: ${msg.subtype}`);
        } else if (msg.type === 'result') {
          // 结果摘要，记录但不返回给客户端
          this.logger.info(`Completed with result: ${msg.result}`);
          this.logger.info(`Total cost: $${msg.total_cost_usd}`);
        } else {
          // 其他未知类型的消息
          this.logger.warn(`Unknown message type: ${JSON.stringify(msg).substring(0, 200)}`);
        }
      }

      // 更新会话状态
      this.sessionManager.updateSession(sessionId, {
        is_first_message: false,
        message_count: session.message_count + 1
      });

      // 记录完整响应
      if (responseChunks.length > 0) {
        const fullResponse = responseChunks.join('');
        this.logger.info(`Stream response completed: ${fullResponse.substring(0, 200)}...`);
      }

    } catch (error: any) {
      this.logger.error(`Error in streamMessage: ${error}`);
      // 检查是否是中断错误
      if (error?.name === 'AbortError') {
        yield { type: 'error', error: 'Request was aborted' };
      } else {
        yield { type: 'error', error: String(error) };
      }
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