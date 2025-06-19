"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.logger = void 0;
console.log('Starting server...');
const express_1 = __importDefault(require("express"));
const cors_1 = __importDefault(require("cors"));
const ws_1 = require("ws");
const http_1 = __importDefault(require("http"));
const winston_1 = __importDefault(require("winston"));
const yargs_1 = __importDefault(require("yargs"));
const helpers_1 = require("yargs/helpers");
const claudeService_1 = require("./services/claudeService");
const sessionManager_1 = require("./services/sessionManager");
const httpRoutes_1 = require("./routes/httpRoutes");
const wsHandlers_1 = require("./routes/wsHandlers");
console.log('Imports completed');
const logger = winston_1.default.createLogger({
    level: 'info',
    format: winston_1.default.format.combine(winston_1.default.format.timestamp(), winston_1.default.format.printf(({ timestamp, level, message }) => {
        return `${timestamp} [${level.toUpperCase()}] ${message}`;
    })),
    transports: [
        new winston_1.default.transports.Console()
    ]
});
exports.logger = logger;
const argv = (0, yargs_1.default)((0, helpers_1.hideBin)(process.argv))
    .option('port', {
    alias: 'p',
    type: 'number',
    description: '服务器端口',
    default: 18080
})
    .option('host', {
    alias: 'h',
    type: 'string',
    description: '服务器地址',
    default: '127.0.0.1'
})
    .help()
    .parseSync();
const app = (0, express_1.default)();
const server = http_1.default.createServer(app);
app.use((0, cors_1.default)());
app.use(express_1.default.json({ limit: '10mb' }));
app.use(express_1.default.urlencoded({ extended: true, limit: '10mb' }));
app.use((req, res, next) => {
    logger.info(`${req.method} ${req.path}`);
    next();
});
const sessionManager = new sessionManager_1.SessionManager(logger);
const claudeService = new claudeService_1.ClaudeService(logger, sessionManager);
(0, httpRoutes_1.setupHttpRoutes)(app, claudeService, logger);
const wss = new ws_1.WebSocketServer({ server });
(0, wsHandlers_1.setupWebSocketHandlers)(wss, claudeService, logger);
server.listen(argv.port, argv.host, () => {
    logger.info(`Claude SDK Wrapper Server started on http://${argv.host}:${argv.port}`);
    logger.info('Available endpoints:');
    logger.info('  POST /stream  - Stream messages with Server-Sent Events');
    logger.info('  POST /message - Single request/response');
    logger.info('  GET  /health  - Health check');
    logger.info('  WS   /ws      - WebSocket connection');
});
process.on('SIGINT', () => {
    logger.info('Shutting down server...');
    server.close(() => {
        logger.info('Server closed');
        process.exit(0);
    });
});
