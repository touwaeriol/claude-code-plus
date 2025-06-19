"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.StdinMessageService = void 0;
const logger_1 = require("../utils/logger");
const readline_1 = __importDefault(require("readline"));
class StdinMessageService {
    constructor(claudeService) {
        this.logger = new logger_1.Logger('StdinMessageService');
        this.claudeService = claudeService;
        this.rl = readline_1.default.createInterface({
            input: process.stdin,
            output: process.stdout,
            terminal: false
        });
    }
    start() {
        this.logger.info('Starting stdin message service...');
        this.rl.on('line', async (line) => {
            try {
                const message = JSON.parse(line.trim());
                await this.handleMessage(message);
            }
            catch (error) {
                this.logger.error('Failed to parse message:', error);
                this.sendError('unknown', -32700, 'Parse error');
            }
        });
        this.sendResponse('ready', { status: 'ready', pid: process.pid });
        this.logger.info('Stdin message service started');
    }
    async handleMessage(message) {
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
        }
        catch (error) {
            this.logger.error(`Error handling ${method}:`, error);
            this.sendError(id, -32603, error.message);
        }
    }
    async handleHealth(id) {
        const health = await this.claudeService.checkHealth();
        this.sendResponse(id, health);
    }
    async handleStream(id, params) {
        var _a;
        const { message, options } = params || {};
        if (!message) {
            this.sendError(id, -32602, 'Message is required');
            return;
        }
        try {
            this.sendStream(id, { type: 'start' });
            const sessionId = this.claudeService.sessionManager.createSession();
            for await (const chunk of this.claudeService.streamMessage(message, sessionId, options)) {
                if (chunk.type === 'content' && chunk.content) {
                    this.sendStream(id, { type: 'chunk', data: chunk.content });
                }
                else if (chunk.type === 'error') {
                    throw new Error(chunk.error || 'Unknown error');
                }
            }
            this.sendStream(id, { type: 'end' });
        }
        catch (error) {
            if ((_a = error.message) === null || _a === void 0 ? void 0 : _a.includes('aborted')) {
                this.sendStream(id, { type: 'aborted' });
            }
            else {
                this.sendError(id, -32603, error.message);
            }
        }
    }
    async handleAbort(id) {
        const aborted = this.claudeService.abort();
        this.sendResponse(id, { aborted });
    }
    sendResponse(id, result) {
        const message = {
            id,
            type: 'response',
            result
        };
        this.send(message);
    }
    sendStream(id, result) {
        const message = {
            id,
            type: 'stream',
            result
        };
        this.send(message);
    }
    sendError(id, code, message) {
        const errorMessage = {
            id,
            type: 'error',
            error: { code, message }
        };
        this.send(errorMessage);
    }
    send(message) {
        console.log(JSON.stringify(message));
    }
    stop() {
        this.rl.close();
        this.logger.info('Stdin message service stopped');
    }
}
exports.StdinMessageService = StdinMessageService;
