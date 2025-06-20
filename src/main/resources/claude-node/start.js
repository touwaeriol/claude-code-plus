#!/usr/bin/env node

// Claude SDK Server Launcher
const path = require('path');
const fs = require('fs');

// 定义错误输出格式
function reportError(stage, error) {
    console.error('ERROR:' + JSON.stringify({
        stage: stage,
        message: error.message,
        stack: error.stack,
        code: error.code || 'UNKNOWN'
    }));
}

// 捕获未处理的异常
process.on('uncaughtException', (error) => {
    reportError('UNCAUGHT_EXCEPTION', error);
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    reportError('UNHANDLED_REJECTION', new Error(String(reason)));
    process.exit(1);
});

// 检查 Node.js 版本
const nodeVersion = process.version;
const majorVersion = parseInt(nodeVersion.slice(1).split('.')[0]);
if (majorVersion < 18) {
    reportError('VERSION_CHECK', new Error('Node.js 18.0.0 or higher required. Current: ' + nodeVersion));
    process.exit(1);
}

// 设置工作目录
try {
    process.chdir(__dirname);
} catch (error) {
    reportError('CHDIR', error);
    process.exit(1);
}

// 启动服务器
try {
    require('./server.bundle.js');
} catch (error) {
    reportError('SERVER_START', error);
    process.exit(1);
}
