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
exports.ClaudeService = void 0;
let query;
async function loadSDK() {
    try {
        const sdk = await Promise.resolve().then(() => __importStar(require('@anthropic-ai/claude-code')));
        query = sdk.query;
        return true;
    }
    catch (error) {
        console.error('Failed to load @anthropic-ai/claude-code:', error);
        return false;
    }
}
class ClaudeService {
    constructor(logger, sessionManager) {
        this.logger = logger;
        this.sessionManager = sessionManager;
        this.isInitialized = false;
        this.sdkLoaded = false;
        this.isProcessing = false;
        this.currentAbortController = null;
        this.defaultOptions = {
            system_prompt: 'You are a helpful assistant.',
            max_turns: 20
        };
        this.loadSDKAsync();
    }
    async loadSDKAsync() {
        this.sdkLoaded = await loadSDK();
        if (this.sdkLoaded) {
            this.logger.info('Claude SDK loaded successfully');
        }
        else {
            this.logger.error('Failed to load Claude SDK');
        }
    }
    initialize(config) {
        try {
            this.logger.info(`Initializing with config: ${JSON.stringify(config)}`);
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
        }
        catch (error) {
            this.logger.error(`Failed to initialize: ${error}`);
            return { success: false, error: String(error) };
        }
    }
    getStatus() {
        return {
            initialized: this.isInitialized,
            sdk_available: true,
            active_sessions: this.sessionManager.getActiveSessions().length,
            is_processing: this.isProcessing
        };
    }
    abort() {
        if (this.currentAbortController) {
            this.logger.info('Aborting current request...');
            this.currentAbortController.abort();
            this.currentAbortController = null;
            this.isProcessing = false;
            return true;
        }
        return false;
    }
    async *streamMessage(message, sessionId, customOptions) {
        var _a;
        if (this.isProcessing) {
            yield { type: 'error', error: 'Service is busy processing another request. Please wait or abort the current request.' };
            return;
        }
        if (!this.isInitialized) {
            this.logger.info('Service not initialized, attempting auto-initialization...');
            const initConfig = (customOptions === null || customOptions === void 0 ? void 0 : customOptions.cwd) ? { cwd: customOptions.cwd } : {};
            const initResult = this.initialize(initConfig);
            if (!initResult.success) {
                yield { type: 'error', error: `Failed to auto-initialize: ${initResult.error}` };
                return;
            }
        }
        if (!this.sdkLoaded) {
            this.logger.info('SDK not loaded, attempting to load...');
            this.sdkLoaded = await loadSDK();
            if (!this.sdkLoaded) {
                yield { type: 'error', error: 'Failed to load Claude SDK' };
                return;
            }
        }
        if (!query || typeof query !== 'function') {
            yield { type: 'error', error: 'Claude SDK query function not available' };
            return;
        }
        this.logger.info(`Stream processing message (session: ${sessionId}): ${message.substring(0, 100)}...`);
        const session = this.sessionManager.getSession(sessionId);
        if (!session) {
            yield { type: 'error', error: 'Session expired or not found' };
            return;
        }
        this.isProcessing = true;
        this.currentAbortController = new AbortController();
        try {
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
            const responseChunks = [];
            for await (const msg of query(queryParams)) {
                const msgType = msg.constructor.name;
                if (msg.type === 'assistant' && msg.message) {
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
                            }
                            else if (block.type === 'tool_use') {
                                yield {
                                    type: 'tool_use',
                                    message_type: 'assistant',
                                    content: JSON.stringify(block),
                                    session_id: sessionId
                                };
                            }
                        }
                    }
                }
                else if (msg.type === 'user' && msg.message) {
                    const userMsg = msg.message;
                    if (userMsg.content && Array.isArray(userMsg.content)) {
                        for (const block of userMsg.content) {
                            if (block.type === 'tool_result') {
                                this.logger.info(`Tool result from ${block.tool_use_id}: ${(_a = block.content) === null || _a === void 0 ? void 0 : _a.substring(0, 200)}...`);
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
                }
                else if (msg.type === 'system') {
                    this.logger.info(`System message: ${msg.subtype}`);
                }
                else if (msg.type === 'result') {
                    this.logger.info(`Completed with result: ${msg.result}`);
                    this.logger.info(`Total cost: $${msg.total_cost_usd}`);
                }
                else {
                    this.logger.warn(`Unknown message type: ${JSON.stringify(msg).substring(0, 200)}`);
                }
            }
            this.sessionManager.updateSession(sessionId, {
                is_first_message: false,
                message_count: session.message_count + 1
            });
            if (responseChunks.length > 0) {
                const fullResponse = responseChunks.join('');
                this.logger.info(`Stream response completed: ${fullResponse.substring(0, 200)}...`);
            }
        }
        catch (error) {
            this.logger.error(`Error in streamMessage: ${error}`);
            if ((error === null || error === void 0 ? void 0 : error.name) === 'AbortError') {
                yield { type: 'error', error: 'Request was aborted' };
            }
            else {
                yield { type: 'error', error: String(error) };
            }
        }
        finally {
            this.isProcessing = false;
            this.currentAbortController = null;
        }
    }
    async getSingleResponse(message, sessionId, customOptions) {
        const responseChunks = [];
        let error = null;
        try {
            for await (const chunk of this.streamMessage(message, sessionId, customOptions)) {
                if (chunk.type === 'error') {
                    error = chunk.error || 'Unknown error';
                    break;
                }
                else if (chunk.type === 'text') {
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
        }
        catch (err) {
            return {
                success: false,
                error: String(err),
                session_id: sessionId
            };
        }
    }
}
exports.ClaudeService = ClaudeService;
