#!/usr/bin/env node

/**
 * Claude Code SDK Wrapper
 * 
 * 这个 Node.js 脚本作为 Kotlin ClaudeCliWrapper 和 Claude Code SDK 之间的桥接层。
 * 它接收来自 Kotlin 的 JSON 参数，使用 Claude Code SDK 执行查询，
 * 并将结果通过 stdout 返回给 Kotlin。
 * 
 * 使用方式：
 * node claude-sdk-wrapper.js '{"prompt": "Hello", "options": {...}}'
 * 
 * 或通过环境变量：
 * CLAUDE_WRAPPER_INPUT='{"prompt": "Hello", "options": {...}}' node claude-sdk-wrapper.js
 */

import { query } from '@anthropic-ai/claude-code';
import { createWriteStream } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

// 获取当前文件目录
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// 日志文件路径
const logFile = join(__dirname, 'claude-wrapper.log');

/**
 * 日志函数 - 输出到文件而不是 stdout，避免干扰正常输出
 */
function log(message) {
    const timestamp = new Date().toISOString();
    const logMessage = `[${timestamp}] ${message}\n`;
    
    try {
        // 同步写入日志文件
        import('fs').then(fs => {
            fs.appendFileSync(logFile, logMessage);
        });
    } catch (error) {
        // 忽略日志写入错误
    }
}

/**
 * 输出消息到 stdout（供 Kotlin 读取）
 */
function outputMessage(message) {
    console.log(JSON.stringify(message));
}

/**
 * 映射 Kotlin 参数到 SDK 参数
 */
function mapKotlinOptionsToSdk(kotlinOptions) {
    const sdkOptions = {};
    
    // 基本选项映射
    if (kotlinOptions.model) sdkOptions.model = kotlinOptions.model;
    if (kotlinOptions.fallbackModel) sdkOptions.fallbackModel = kotlinOptions.fallbackModel;
    if (kotlinOptions.maxTurns) sdkOptions.maxTurns = kotlinOptions.maxTurns;
    if (kotlinOptions.customSystemPrompt) sdkOptions.systemPrompt = kotlinOptions.customSystemPrompt;
    if (kotlinOptions.cwd) sdkOptions.cwd = kotlinOptions.cwd;
    
    // 会话管理
    if (kotlinOptions.resume) sdkOptions.resume = kotlinOptions.resume;
    if (kotlinOptions.sessionId) sdkOptions.sessionId = kotlinOptions.sessionId;
    
    // 权限和工具
    if (kotlinOptions.allowedTools && kotlinOptions.allowedTools.length > 0) {
        sdkOptions.allowedTools = kotlinOptions.allowedTools;
    }
    if (kotlinOptions.disallowedTools && kotlinOptions.disallowedTools.length > 0) {
        sdkOptions.disallowedTools = kotlinOptions.disallowedTools;
    }
    
    // 权限模式处理
    if (kotlinOptions.skipPermissions || kotlinOptions.permissionMode === 'bypassPermissions') {
        sdkOptions.dangerouslySkipPermissions = true;
    }
    
    // MCP 配置
    if (kotlinOptions.mcpServers) {
        sdkOptions.mcpConfig = kotlinOptions.mcpServers;
    }
    
    // 调试和高级选项
    if (kotlinOptions.debug) sdkOptions.debug = kotlinOptions.debug;
    if (kotlinOptions.verbose) sdkOptions.verbose = kotlinOptions.verbose;
    if (kotlinOptions.showStats) sdkOptions.showStats = kotlinOptions.showStats;
    
    // 其他选项
    if (kotlinOptions.continueRecent) sdkOptions.continueRecent = kotlinOptions.continueRecent;
    if (kotlinOptions.settingsFile) sdkOptions.settingsFile = kotlinOptions.settingsFile;
    if (kotlinOptions.additionalDirectories) sdkOptions.additionalDirectories = kotlinOptions.additionalDirectories;
    if (kotlinOptions.autoConnectIde) sdkOptions.autoConnectIde = kotlinOptions.autoConnectIde;
    
    return sdkOptions;
}

/**
 * 主函数
 */
async function main() {
    try {
        log('Claude SDK Wrapper 启动');
        
        // 获取输入参数
        let inputJson;
        if (process.argv[2]) {
            // 从命令行参数获取
            inputJson = process.argv[2];
            log('从命令行参数获取输入');
        } else if (process.env.CLAUDE_WRAPPER_INPUT) {
            // 从环境变量获取
            inputJson = process.env.CLAUDE_WRAPPER_INPUT;
            log('从环境变量获取输入');
        } else {
            throw new Error('未提供输入参数。请通过命令行参数或 CLAUDE_WRAPPER_INPUT 环境变量提供 JSON 输入。');
        }
        
        // 解析输入
        const input = JSON.parse(inputJson);
        log(`解析输入成功: ${JSON.stringify(input, null, 2).substring(0, 200)}...`);
        
        const { prompt, options = {} } = input;
        
        if (!prompt) {
            throw new Error('缺少必需的 prompt 参数');
        }
        
        // 映射选项
        const sdkOptions = mapKotlinOptionsToSdk(options);
        log(`映射选项完成: ${JSON.stringify(sdkOptions, null, 2).substring(0, 200)}...`);
        
        // 输出开始消息
        outputMessage({
            type: 'start',
            sessionId: options.sessionId || options.resume || null,
            processId: process.pid,
            timestamp: new Date().toISOString()
        });
        
        // 执行查询
        log('开始执行 Claude Code SDK 查询');
        
        let messageCount = 0;
        const startTime = Date.now();
        
        for await (const message of query({
            prompt: prompt,
            options: sdkOptions
        })) {
            messageCount++;
            log(`收到消息 ${messageCount}: ${message.type || 'unknown'}`);
            
            // 转发消息到 Kotlin
            outputMessage({
                type: 'message',
                data: message,
                messageIndex: messageCount,
                timestamp: new Date().toISOString()
            });
        }
        
        const endTime = Date.now();
        const duration = endTime - startTime;
        
        // 输出完成消息
        outputMessage({
            type: 'complete',
            sessionId: options.sessionId || options.resume || null,
            processId: process.pid,
            messageCount: messageCount,
            duration: duration,
            timestamp: new Date().toISOString()
        });
        
        log(`查询完成，共收到 ${messageCount} 条消息，耗时 ${duration}ms`);
        
    } catch (error) {
        log(`错误: ${error.message}`);
        log(`错误堆栈: ${error.stack}`);
        
        // 输出错误消息
        outputMessage({
            type: 'error',
            error: error.message,
            stack: error.stack,
            timestamp: new Date().toISOString()
        });
        
        process.exit(1);
    }
}

// 处理未捕获的异常
process.on('unhandledRejection', (reason, promise) => {
    log(`未处理的 Promise 拒绝: ${reason}`);
    outputMessage({
        type: 'error',
        error: 'Unhandled promise rejection',
        details: String(reason),
        timestamp: new Date().toISOString()
    });
    process.exit(1);
});

process.on('uncaughtException', (error) => {
    log(`未捕获的异常: ${error.message}`);
    outputMessage({
        type: 'error',
        error: 'Uncaught exception',
        message: error.message,
        stack: error.stack,
        timestamp: new Date().toISOString()
    });
    process.exit(1);
});

// 优雅退出处理
process.on('SIGINT', () => {
    log('收到 SIGINT 信号，正在退出...');
    outputMessage({
        type: 'terminated',
        reason: 'SIGINT',
        timestamp: new Date().toISOString()
    });
    process.exit(0);
});

process.on('SIGTERM', () => {
    log('收到 SIGTERM 信号，正在退出...');
    outputMessage({
        type: 'terminated',
        reason: 'SIGTERM',
        timestamp: new Date().toISOString()
    });
    process.exit(0);
});

// 启动
main();