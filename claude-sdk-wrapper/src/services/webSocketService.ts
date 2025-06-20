import WebSocket from 'ws';
import { SessionManager } from './sessionManager';
import { ClaudeService } from './claudeService';
import { Logger } from '../utils/logger';

/**
 * WebSocket 服务 - 使用端口通信
 */
export class WebSocketService {
    private wss?: WebSocket.Server;
    private sessionManager: SessionManager;
    private claudeService: ClaudeService;
    private logger: Logger;
    private port: number;

    constructor(port: number = 9925) {
        this.port = port;
        this.logger = new Logger('WebSocketService');
        this.sessionManager = new SessionManager((this.logger as any).logger);
        this.claudeService = new ClaudeService((this.logger as any).logger, this.sessionManager);
    }

    /**
     * 启动服务
     */
    async start() {
        try {
            // 创建 WebSocket 服务器
            this.wss = new WebSocket.Server({ 
                port: this.port,
                perMessageDeflate: false
            });

            this.wss.on('connection', (ws) => {
                this.logger.info('New WebSocket connection');

                // 发送欢迎消息
                ws.send(JSON.stringify({
                    type: 'welcome',
                    data: {
                        message: 'Connected to Claude Code Plus service',
                        version: '1.0.0'
                    }
                }));

                // 处理消息
                ws.on('message', async (message) => {
                    try {
                        const request = JSON.parse(message.toString());
                        await this.handleMessage(ws, request);
                    } catch (error) {
                        this.logger.error('Error handling message:', error);
                        ws.send(JSON.stringify({
                            type: 'error',
                            error: {
                                message: error instanceof Error ? error.message : 'Unknown error'
                            }
                        }));
                    }
                });

                ws.on('close', () => {
                    this.logger.info('WebSocket connection closed');
                });

                ws.on('error', (error) => {
                    this.logger.error('WebSocket error:', error);
                });
            });

            this.logger.info(`WebSocket server started on port ${this.port}`);
            console.log(`WebSocket server listening on port ${this.port}`);
            
        } catch (error) {
            this.logger.error('Failed to start WebSocket server:', error);
            throw error;
        }
    }

    /**
     * 处理消息
     */
    private async handleMessage(ws: WebSocket, request: any) {
        const { type, data, id } = request;

        switch (type) {
            case 'health':
                ws.send(JSON.stringify({
                    type: 'health',
                    id,
                    data: {
                        isHealthy: true,
                        isProcessing: this.claudeService.getStatus().is_processing,
                        activeSessionId: this.sessionManager.getCurrentSession()?.id || null
                    }
                }));
                break;

            case 'stream':
                await this.handleStream(ws, data, id);
                break;

            case 'abort':
                const sessionId = data?.sessionId || this.sessionManager.getCurrentSession()?.id;
                if (sessionId) {
                    this.claudeService.abort();
                    ws.send(JSON.stringify({
                        type: 'abort',
                        id,
                        data: { sessionId, status: 'aborted' }
                    }));
                }
                break;

            default:
                ws.send(JSON.stringify({
                    type: 'error',
                    id,
                    error: {
                        message: `Unknown message type: ${type}`
                    }
                }));
        }
    }

    /**
     * 处理流式请求
     */
    private async handleStream(ws: WebSocket, data: any, id: string) {
        const { message, options = {} } = data;

        try {
            // 创建会话
            const sessionId = this.sessionManager.createSession();

            // 发送会话开始消息
            ws.send(JSON.stringify({
                type: 'stream.start',
                id,
                data: { sessionId }
            }));

            // 调用 Claude 服务
            for await (const chunk of this.claudeService.streamMessage(message, sessionId, options)) {
                if (chunk.type === 'text') {
                    // 发送流数据块
                    ws.send(JSON.stringify({
                        type: 'stream.chunk',
                        id,
                        data: {
                            sessionId,
                            chunk: chunk.content
                        }
                    }));
                } else if (chunk.type === 'error') {
                    throw new Error(chunk.error || 'Stream error');
                }
            }
            
            // 发送完成消息
            ws.send(JSON.stringify({
                type: 'stream.end',
                id,
                data: {
                    sessionId,
                    status: 'completed'
                }
            }));

        } catch (error) {
            this.logger.error('Stream error:', error);
            ws.send(JSON.stringify({
                type: 'stream.error',
                id,
                error: {
                    message: error instanceof Error ? error.message : 'Stream failed'
                }
            }));
        }
    }

    /**
     * 停止服务
     */
    stop() {
        if (this.wss) {
            this.wss.close(() => {
                this.logger.info('WebSocket server stopped');
            });
        }
    }
}