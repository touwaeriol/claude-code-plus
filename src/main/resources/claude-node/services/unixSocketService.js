"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.UnixSocketService = void 0;
const net = __importStar(require("net"));
const os = __importStar(require("os"));
const path = __importStar(require("path"));
const fs = __importStar(require("fs"));
const logger_1 = require("../utils/logger");
const uuid_1 = require("uuid");
class UnixSocketService {
    constructor(claudeService) {
        this.server = null;
        this.clients = new Map();
        this.activeStreams = new Map();
        this.logger = new logger_1.Logger('UnixSocketService');
        this.claudeService = claudeService;
        const tmpDir = os.tmpdir();
        const timestamp = Date.now();
        const pid = process.pid;
        this.socketPath = path.join(tmpDir, `claude-code-plus-${pid}-${timestamp}.sock`);
    }
    async start() {
        return new Promise((resolve, reject) => {
            try {
                if (fs.existsSync(this.socketPath)) {
                    fs.unlinkSync(this.socketPath);
                }
                this.server = net.createServer((socket) => {
                    const clientId = (0, uuid_1.v4)();
                    this.clients.set(clientId, socket);
                    this.logger.info(`Client connected: ${clientId}`);
                    socket.setEncoding('utf8');
                    let buffer = '';
                    socket.on('data', (data) => {
                        buffer += data;
                        const lines = buffer.split('\n');
                        buffer = lines.pop() || '';
                        for (const line of lines) {
                            if (line.trim()) {
                                this.handleMessage(clientId, socket, line.trim());
                            }
                        }
                    });
                    socket.on('end', () => {
                        this.logger.info(`Client disconnected: ${clientId}`);
                        this.clients.delete(clientId);
                        for (const [streamId,] of this.activeStreams) {
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
                this.server.listen(this.socketPath, () => {
                    this.logger.info(`Unix socket server listening on: ${this.socketPath}`);
                    console.log(`SOCKET_PATH:${this.socketPath}`);
                    resolve(this.socketPath);
                });
                this.server.on('error', (err) => {
                    this.logger.error('Server error:', err);
                    reject(err);
                });
            }
            catch (error) {
                this.logger.error('Failed to start Unix socket server:', error);
                reject(error);
            }
        });
    }
    async handleMessage(clientId, socket, data) {
        try {
            const request = JSON.parse(data);
            if (request.jsonrpc !== '2.0') {
                this.sendError(socket, request.id, -32600, 'Invalid Request');
                return;
            }
            this.logger.debug(`Received request: ${request.method} (${request.id})`);
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
        }
        catch (error) {
            this.logger.error('Error handling message:', error);
            try {
                const errRequest = JSON.parse(data);
                this.sendError(socket, errRequest.id || null, -32700, 'Parse error');
            }
            catch (_a) {
                this.sendError(socket, null, -32700, 'Parse error');
            }
        }
    }
    async handleHealth(socket, request) {
        try {
            const health = await this.claudeService.checkHealth();
            this.sendResponse(socket, request.id, health);
        }
        catch (error) {
            this.sendError(socket, request.id, -32603, error.message);
        }
    }
    async handleStream(clientId, socket, request) {
        const { message, options } = request.params || {};
        if (!message) {
            this.sendError(socket, request.id, -32602, 'Invalid params: message is required');
            return;
        }
        const streamId = `${clientId}-${request.id}`;
        this.activeStreams.set(streamId, true);
        try {
            this.sendNotification(socket, 'stream.start', {
                id: request.id,
                timestamp: new Date().toISOString()
            });
            const sessionId = this.claudeService.sessionManager.createSession();
            for await (const chunk of this.claudeService.streamMessage(message, sessionId, options)) {
                if (!this.activeStreams.get(streamId)) {
                    throw new Error('Stream aborted');
                }
                if (chunk.type === 'content' && chunk.content) {
                    this.sendNotification(socket, 'stream.chunk', {
                        id: request.id,
                        chunk: chunk.content
                    });
                }
                else if (chunk.type === 'error') {
                    throw new Error(chunk.error || 'Unknown error');
                }
            }
            this.activeStreams.delete(streamId);
            this.sendResponse(socket, request.id, {
                status: 'completed',
                timestamp: new Date().toISOString()
            });
        }
        catch (error) {
            this.activeStreams.delete(streamId);
            if (error.message === 'Stream aborted') {
                this.sendResponse(socket, request.id, {
                    status: 'aborted',
                    timestamp: new Date().toISOString()
                });
            }
            else {
                this.sendError(socket, request.id, -32603, error.message);
            }
        }
    }
    async handleAbort(clientId, socket, request) {
        try {
            const { streamId } = request.params || {};
            if (streamId) {
                const fullStreamId = `${clientId}-${streamId}`;
                if (this.activeStreams.has(fullStreamId)) {
                    this.activeStreams.delete(fullStreamId);
                    this.sendResponse(socket, request.id, { aborted: true, streamId });
                    return;
                }
            }
            const aborted = this.claudeService.abort();
            this.sendResponse(socket, request.id, { aborted });
        }
        catch (error) {
            this.sendError(socket, request.id, -32603, error.message);
        }
    }
    sendResponse(socket, id, result) {
        const response = {
            jsonrpc: '2.0',
            id,
            result
        };
        this.send(socket, response);
    }
    sendError(socket, id, code, message, data) {
        const response = {
            jsonrpc: '2.0',
            id: id || 0,
            error: { code, message, data }
        };
        this.send(socket, response);
    }
    sendNotification(socket, method, params) {
        const notification = {
            jsonrpc: '2.0',
            method,
            params
        };
        this.send(socket, notification);
    }
    send(socket, data) {
        try {
            const json = JSON.stringify(data) + '\n';
            socket.write(json);
        }
        catch (error) {
            this.logger.error('Failed to send data:', error);
        }
    }
    stop() {
        try {
            for (const [clientId, socket] of this.clients) {
                socket.end();
            }
            this.clients.clear();
            this.activeStreams.clear();
            if (this.server) {
                this.server.close();
                this.server = null;
            }
            if (fs.existsSync(this.socketPath)) {
                fs.unlinkSync(this.socketPath);
            }
            this.logger.info('Unix socket server stopped');
        }
        catch (error) {
            this.logger.error('Error stopping server:', error);
        }
    }
    getSocketPath() {
        return this.socketPath;
    }
}
exports.UnixSocketService = UnixSocketService;
