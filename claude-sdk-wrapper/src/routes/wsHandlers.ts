import { WebSocketServer, WebSocket } from 'ws';
import { Logger } from 'winston';
import { ClaudeService } from '../services/claudeService';

interface WSMessage {
  command: string;
  message?: string;
  session_id?: string;
  new_session?: boolean;
  options?: any;
}

export function setupWebSocketHandlers(wss: WebSocketServer, claudeService: ClaudeService, logger: Logger) {
  
  wss.on('connection', (ws: WebSocket) => {
    logger.info('New WebSocket connection established');
    
    // 发送欢迎消息
    ws.send(JSON.stringify({
      type: 'welcome',
      message: 'Connected to Claude SDK Wrapper (WebSocket)',
      initialized: claudeService.getStatus().initialized
    }));

    ws.on('message', async (data: Buffer) => {
      try {
        const message: WSMessage = JSON.parse(data.toString());
        const { command } = message;

        switch (command) {
          case 'ping':
            ws.send(JSON.stringify({ type: 'pong' }));
            break;

          case 'message':
            await handleMessage(ws, message, claudeService, logger);
            break;

          case 'health':
            ws.send(JSON.stringify({
              type: 'health',
              status: 'ok',
              ...claudeService.getStatus()
            }));
            break;

          default:
            ws.send(JSON.stringify({
              type: 'error',
              error: `Unknown command: ${command}`
            }));
        }
      } catch (error) {
        logger.error(`WebSocket message error: ${error}`);
        ws.send(JSON.stringify({
          type: 'error',
          error: String(error)
        }));
      }
    });

    ws.on('error', (error) => {
      logger.error(`WebSocket error: ${error}`);
    });

    ws.on('close', () => {
      logger.info('WebSocket connection closed');
    });
  });
}

async function handleMessage(
  ws: WebSocket,
  message: WSMessage,
  claudeService: ClaudeService,
  logger: Logger
) {
  const { message: userMessage, session_id, new_session, options } = message;
  
  if (!userMessage) {
    ws.send(JSON.stringify({
      type: 'error',
      error: 'Message is required'
    }));
    return;
  }

  // 处理会话
  let sessionId: string = session_id || '';
  const sessionManager = (claudeService as any).sessionManager;
  
  if (new_session) {
    sessionId = sessionManager.createSession();
  } else if (!sessionId) {
    sessionId = sessionManager.getOrCreateDefaultSession();
  }

  // 确保 sessionId 不为空
  if (!sessionId) {
    ws.send(JSON.stringify({ type: 'error', error: 'Failed to create or get session' }));
    return;
  }

  logger.info(`[WebSocket] Processing message (session: ${sessionId})`);

  // 流式发送响应
  try {
    for await (const chunk of claudeService.streamMessage(userMessage, sessionId, options)) {
      ws.send(JSON.stringify(chunk));
    }
    
    // 发送结束标记
    ws.send(JSON.stringify({ type: 'done', session_id: sessionId }));
  } catch (error) {
    logger.error(`Error streaming message: ${error}`);
    ws.send(JSON.stringify({
      type: 'error',
      error: String(error)
    }));
  }
}