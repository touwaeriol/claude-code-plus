"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.setupHttpRoutes = setupHttpRoutes;
function setupHttpRoutes(app, claudeService, logger) {
    app.get('/health', (req, res) => {
        const status = claudeService.getStatus();
        const response = {
            status: 'ok',
            ...status,
            endpoints: {
                http: {
                    '/stream': 'SSE streaming endpoint',
                    '/message': 'Single response endpoint',
                    '/health': 'Health check',
                    '/abort': 'Abort current request'
                },
                websocket: {
                    '/ws': 'WebSocket endpoint'
                }
            }
        };
        logger.info(`[/health] Response: ${JSON.stringify(response)}`);
        res.json(response);
    });
    app.post('/abort', (req, res) => {
        const aborted = claudeService.abort();
        const response = {
            success: aborted,
            message: aborted ? 'Request aborted successfully' : 'No active request to abort'
        };
        logger.info(`[/abort] Response: ${JSON.stringify(response)}`);
        res.json(response);
    });
    app.post('/stream', async (req, res) => {
        try {
            const { message, session_id, new_session, options } = req.body;
            if (!message) {
                return res.status(400).json({ success: false, error: 'Message is required' });
            }
            let sessionId = session_id;
            if (new_session) {
                const sessionManager = claudeService.sessionManager;
                sessionId = sessionManager.createSession();
                logger.info(`[/stream] Creating new session: ${sessionId}`);
            }
            else if (!sessionId) {
                const sessionManager = claudeService.sessionManager;
                sessionId = sessionManager.getOrCreateDefaultSession();
                logger.info(`[/stream] Using default session: ${sessionId}`);
            }
            logger.info(`[/stream] Request (session: ${sessionId}): ${message.substring(0, 100)}...`);
            res.writeHead(200, {
                'Content-Type': 'text/event-stream',
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
                'Access-Control-Allow-Origin': '*'
            });
            let chunkCount = 0;
            for await (const chunk of claudeService.streamMessage(message, sessionId, options)) {
                res.write(`data: ${JSON.stringify(chunk)}\n\n`);
                chunkCount++;
            }
            res.write('data: [DONE]\n\n');
            res.end();
            logger.info(`[/stream] Completed with ${chunkCount} chunks`);
        }
        catch (error) {
            logger.error(`Error in /stream: ${error}`);
            if (!res.headersSent) {
                res.status(400).json({ success: false, error: String(error) });
            }
        }
    });
    app.post('/message', async (req, res) => {
        try {
            const { message, session_id, new_session, options } = req.body;
            if (!message) {
                return res.status(400).json({ success: false, error: 'Message is required' });
            }
            let sessionId = session_id;
            if (new_session) {
                const sessionManager = claudeService.sessionManager;
                sessionId = sessionManager.createSession();
                logger.info(`[/message] Creating new session: ${sessionId}`);
            }
            else if (!sessionId) {
                const sessionManager = claudeService.sessionManager;
                sessionId = sessionManager.getOrCreateDefaultSession();
                logger.info(`[/message] Using default session: ${sessionId}`);
            }
            logger.info(`[/message] Request (session: ${sessionId}): ${message.substring(0, 100)}...`);
            const result = await claudeService.getSingleResponse(message, sessionId, options);
            logger.info(`[/message] Response: success=${result.success}`);
            res.json(result);
        }
        catch (error) {
            logger.error(`Error in /message: ${error}`);
            res.status(400).json({ success: false, error: String(error) });
        }
    });
}
