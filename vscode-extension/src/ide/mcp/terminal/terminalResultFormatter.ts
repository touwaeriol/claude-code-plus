/**
 * Terminal Result Formatter
 * 
 * Converts JSON format results to readable Markdown format.
 * Translated from JetBrains plugin's TerminalResultFormatter.kt
 */

import { SearchMatch, ShellTypeInfo } from './terminalModels';

/**
 * Session info for list results
 */
export interface SessionInfo {
    id: string;
    name: string;
    shellType: string;
    isRunning: boolean;
    outputPreview?: string;
}

/**
 * Terminal result formatter class
 */
export class TerminalResultFormatter {
    /**
     * Format Terminal execution result
     */
    static formatTerminalResult(
        success: boolean,
        sessionId?: string,
        sessionName?: string,
        message?: string,
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            lines.push(`**Session:** \`${sessionId}\` (${sessionName})`);
            lines.push('');
            lines.push('**Status:** Command sent');
            if (message) {
                lines.push('');
                lines.push(`> ${message}`);
            }
        } else {
            lines.push(`**Error:** ${error}`);
            if (sessionId) {
                lines.push(`**Session:** \`${sessionId}\``);
            }
        }

        return lines.join('\n');
    }

    /**
     * Format TerminalRead result
     */
    static formatReadResult(
        success: boolean,
        sessionId?: string,
        isRunning?: boolean,
        output?: string,
        lineCount?: number,
        searchMatches?: SearchMatch[],
        waitTimedOut?: boolean,
        waitMessage?: string,
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            // Session info line
            const status = isRunning === true ? 'running' : isRunning === false ? 'idle' : 'unknown';
            lines.push(`**Session:** \`${sessionId}\` | **Status:** ${status}`);

            // Wait timeout warning
            if (waitTimedOut) {
                lines.push('');
                lines.push(`> **Warning:** ${waitMessage}`);
            }

            lines.push('');

            // Search match results
            if (searchMatches && searchMatches.length > 0) {
                lines.push(`**Matches:** ${searchMatches.length}`);
                lines.push('');
                for (const match of searchMatches) {
                    const truncatedLine = match.line.length > 100 
                        ? match.line.substring(0, 100) + '...' 
                        : match.line;
                    lines.push(`- **Line ${match.lineNumber}:** \`${truncatedLine}\``);
                }
            } else {
                // Normal output
                lines.push(`**Lines:** ${lineCount ?? 0}`);
                lines.push('');
                lines.push('```');
                lines.push(output?.trimEnd() ?? '');
                lines.push('```');
            }
        } else {
            lines.push(`**Error:** ${error}`);
            if (sessionId) {
                lines.push(`**Session:** \`${sessionId}\``);
            }
        }

        return lines.join('\n');
    }

    /**
     * Format TerminalList result
     */
    static formatListResult(
        success: boolean,
        count: number,
        sessions: SessionInfo[],
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            lines.push(`**Terminal Sessions:** ${count}`);
            lines.push('');
            if (sessions.length === 0) {
                lines.push('_No active sessions_');
            } else {
                for (const session of sessions) {
                    const status = session.isRunning ? 'running' : 'idle';
                    lines.push(`- \`${session.id}\` **${session.name}** [${status}] (${session.shellType})`);
                    if (session.outputPreview) {
                        const lastLine = session.outputPreview
                            .split('\n')
                            .filter(l => l.trim().length > 0)
                            .pop();
                        const shortPreview = lastLine?.substring(0, 60) ?? '';
                        if (shortPreview) {
                            lines.push(`  > \`${shortPreview}\``);
                        }
                    }
                }
            }
        } else {
            lines.push(`**Error:** ${error}`);
        }

        return lines.join('\n');
    }

    /**
     * Format TerminalKill result
     */
    static formatKillResult(
        success: boolean,
        killed: string[],
        failed: string[],
        message?: string,
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            lines.push(`**Result:** ${message}`);
            if (killed.length > 0) {
                lines.push('');
                lines.push(`**Killed:** ${killed.map(id => `\`${id}\``).join(', ')}`);
            }
            if (failed.length > 0) {
                lines.push('');
                lines.push(`**Failed:** ${failed.map(id => `\`${id}\``).join(', ')}`);
            }
        } else {
            lines.push(`**Error:** ${error}`);
        }

        return lines.join('\n');
    }

    /**
     * Format TerminalTypes result
     */
    static formatTypesResult(
        success: boolean,
        platform?: string,
        types?: ShellTypeInfo[],
        defaultType?: string,
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            lines.push(`**Platform:** ${platform} | **Default:** ${defaultType}`);
            lines.push('');
            lines.push('**Available Shells:**');
            if (types) {
                for (const type of types) {
                    const defaultMark = type.isDefault ? ' (default)' : '';
                    lines.push(`- \`${type.name}\` - ${type.displayName}${defaultMark}`);
                }
            }
        } else {
            lines.push(`**Error:** ${error}`);
        }

        return lines.join('\n');
    }

    /**
     * Format TerminalRename result
     */
    static formatRenameResult(
        success: boolean,
        sessionId?: string,
        newName?: string,
        message?: string,
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            lines.push(`**Session:** \`${sessionId}\` renamed to **${newName}**`);
            if (message) {
                lines.push(`> ${message}`);
            }
        } else {
            lines.push(`**Error:** ${error}`);
            if (sessionId) {
                lines.push(`**Session:** \`${sessionId}\``);
            }
        }

        return lines.join('\n');
    }

    /**
     * Format TerminalInterrupt result
     */
    static formatInterruptResult(
        success: boolean,
        sessionId?: string,
        signal?: string,
        wasRunning?: boolean,
        isStillRunning?: boolean,
        message?: string,
        error?: string
    ): string {
        const lines: string[] = [];

        if (success) {
            lines.push(`**Session:** \`${sessionId}\` | **Signal:** ${signal}`);
            lines.push('');
            
            let runningStatus: string;
            if (wasRunning === true && isStillRunning === true) {
                runningStatus = 'Command may still be stopping';
            } else if (wasRunning === true && isStillRunning === false) {
                runningStatus = 'Command stopped';
            } else if (wasRunning === false) {
                runningStatus = 'No command was running';
            } else {
                runningStatus = 'Status unknown';
            }
            
            lines.push(`**Status:** ${runningStatus}`);
            if (message) {
                lines.push(`> ${message}`);
            }
        } else {
            lines.push(`**Error:** ${error}`);
            if (sessionId) {
                lines.push(`**Session:** \`${sessionId}\``);
            }
        }

        return lines.join('\n');
    }
}
