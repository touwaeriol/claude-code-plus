/**
 * Unified Logger for VS Code Extension
 * 
 * Provides consistent logging across all modules with:
 * - Multiple log levels (debug, info, warn, error)
 * - Output to VS Code Output Channel
 * - Optional file logging
 * - Tag-based filtering
 */

import * as fs from 'fs'
import * as path from 'path'
import * as vscode from 'vscode'

export type LogLevel = 'debug' | 'info' | 'warn' | 'error'

const LOG_LEVEL_PRIORITY: Record<LogLevel, number> = {
    debug: 0,
    info: 1,
    warn: 2,
    error: 3,
}

const LOG_LEVEL_ICONS: Record<LogLevel, string> = {
    debug: '🔍',
    info: 'ℹ️',
    warn: '⚠️',
    error: '❌',
}

export interface LoggerConfig {
    /** Minimum log level to output */
    minLevel?: LogLevel
    /** Output to VS Code Output Channel */
    outputChannel?: vscode.OutputChannel
    /** File path for file logging */
    logFilePath?: string
    /** Also output to console */
    console?: boolean
}

export class Logger {
    private readonly tag: string
    private readonly config: Required<LoggerConfig>
    
    private static globalConfig: LoggerConfig = {
        minLevel: 'info',
        console: false,
    }
    
    private static outputChannel: vscode.OutputChannel | undefined
    private static logFilePath: string | undefined

    constructor(tag: string, config?: Partial<LoggerConfig>) {
        this.tag = tag
        this.config = {
            minLevel: config?.minLevel ?? Logger.globalConfig.minLevel ?? 'info',
            outputChannel: config?.outputChannel ?? Logger.outputChannel,
            logFilePath: config?.logFilePath ?? Logger.logFilePath,
            console: config?.console ?? Logger.globalConfig.console ?? false,
        } as Required<LoggerConfig>
    }

    /**
     * Initialize global logger configuration
     * Call this once during extension activation
     */
    static initialize(context: vscode.ExtensionContext, options?: {
        channelName?: string
        minLevel?: LogLevel
        enableFileLog?: boolean
    }): void {
        const channelName = options?.channelName ?? 'Claude Code Plus'
        Logger.outputChannel = vscode.window.createOutputChannel(channelName)
        context.subscriptions.push(Logger.outputChannel)

        if (options?.enableFileLog) {
            const dir = context.logUri.fsPath
            fs.mkdirSync(dir, { recursive: true })
            Logger.logFilePath = path.join(dir, 'claude-code-plus.log')
        }

        Logger.globalConfig = {
            minLevel: options?.minLevel ?? 'info',
            outputChannel: Logger.outputChannel,
            logFilePath: Logger.logFilePath,
            console: context.extensionMode === vscode.ExtensionMode.Development,
        }
    }

    /**
     * Get the global output channel
     */
    static getOutputChannel(): vscode.OutputChannel | undefined {
        return Logger.outputChannel
    }

    /**
     * Create a logger with a specific tag
     */
    static create(tag: string): Logger {
        return new Logger(tag)
    }

    private shouldLog(level: LogLevel): boolean {
        return LOG_LEVEL_PRIORITY[level] >= LOG_LEVEL_PRIORITY[this.config.minLevel]
    }

    private format(level: LogLevel, message: string): string {
        const timestamp = new Date().toISOString()
        const icon = LOG_LEVEL_ICONS[level]
        return `[${timestamp}] [${this.tag}] ${icon} ${message}`
    }

    private write(level: LogLevel, message: string, error?: Error): void {
        if (!this.shouldLog(level)) return

        const formattedMessage = this.format(level, message)
        const errorStack = error ? `\n${error.stack || error.message}` : ''
        const fullMessage = formattedMessage + errorStack

        // Output to VS Code Output Channel
        if (this.config.outputChannel) {
            this.config.outputChannel.appendLine(fullMessage)
        }

        // Output to file
        if (this.config.logFilePath) {
            try {
                fs.appendFileSync(this.config.logFilePath, fullMessage + '\n', 'utf8')
            } catch {
                // Ignore file write failures
            }
        }

        // Output to console in dev mode
        if (this.config.console) {
            switch (level) {
                case 'debug':
                    console.debug(fullMessage)
                    break
                case 'info':
                    console.info(fullMessage)
                    break
                case 'warn':
                    console.warn(fullMessage)
                    break
                case 'error':
                    console.error(fullMessage)
                    break
            }
        }
    }

    debug(message: string): void {
        this.write('debug', message)
    }

    info(message: string): void {
        this.write('info', message)
    }

    warn(message: string, error?: Error): void {
        this.write('warn', message, error)
    }

    error(message: string, error?: Error): void {
        this.write('error', message, error)
    }

    /**
     * Log with explicit level
     */
    log(level: LogLevel, message: string, error?: Error): void {
        this.write(level, message, error)
    }
}

/**
 * Quick access to create loggers
 */
export function getLogger(tag: string): Logger {
    return Logger.create(tag)
}

// Pre-created loggers for common modules
export const serverLogger = new Logger('Server')
export const mcpLogger = new Logger('MCP')
export const rsocketLogger = new Logger('RSocket')
export const ideLogger = new Logger('IDE')
