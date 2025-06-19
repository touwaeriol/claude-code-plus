"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.setupWebSocketHandlers = setupWebSocketHandlers;
function setupWebSocketHandlers(wss, claudeService, logger) {
    wss.on('connection', (ws) => {
        logger.info('New WebSocket connection established');
        ws.send(JSON.stringify({
            type: 'welcome',
            message: 'Connected to Claude SDK Wrapper (WebSocket)',
            initialized: claudeService.getStatus().initialized
        }));
        ws.on('message', async (data) => {
            try {
                const message = JSON.parse(data.toString());
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
            }
            catch (error) {
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
async function handleMessage(ws, message, claudeService, logger) {
    const { message: userMessage, session_id, new_session, options } = message;
    if (!userMessage) {
        ws.send(JSON.stringify({
            type: 'error',
            error: 'Message is required'
        }));
        return;
    }
    let sessionId = session_id || '';
    const sessionManager = claudeService.sessionManager;
    if (new_session) {
        sessionId = sessionManager.createSession();
    }
    else if (!sessionId) {
        sessionId = sessionManager.getOrCreateDefaultSession();
    }
    if (!sessionId) {
        ws.send(JSON.stringify({ type: 'error', error: 'Failed to create or get session' }));
        return;
    }
    logger.info(`[WebSocket] Processing message (session: ${sessionId})`);
    try {
        for await (const chunk of claudeService.streamMessage(userMessage, sessionId, options)) {
            ws.send(JSON.stringify(chunk));
        }
        ws.send(JSON.stringify({ type: 'done', session_id: sessionId }));
    }
    catch (error) {
        logger.error(`Error streaming message: ${error}`);
        ws.send(JSON.stringify({
            type: 'error',
            error: String(error)
        }));
    }
}
