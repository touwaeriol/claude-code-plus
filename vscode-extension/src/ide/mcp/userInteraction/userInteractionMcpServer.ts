/**
 * User Interaction MCP Server for VS Code
 * 
 * Provides AskUserQuestion tool for Claude to interact with users.
 * Uses VS Code's native UI (QuickPick) for user interaction.
 */

import * as vscode from 'vscode';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import * as z from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';

// ========== Types ==========

/**
 * Question option
 */
interface QuestionOption {
    label: string;
    description?: string;
}

/**
 * Question item
 */
interface QuestionItem {
    question: string;
    header?: string;
    options?: QuestionOption[];
    multiSelect?: boolean;
}

/**
 * User answer item
 */
interface UserAnswerItem {
    question: string;
    header?: string;
    answer: string;
}

// ========== Zod Schemas ==========

const OptionSchema = z.object({
    label: z.string().describe('Option display text'),
    description: z.string().optional().describe('Option description (optional)')
});

const QuestionSchema = z.object({
    question: z.string().describe('Question content'),
    header: z.string().optional().describe('Question header/category label'),
    options: z.array(OptionSchema).optional().describe('List of options'),
    multiSelect: z.boolean().optional().default(false).describe('Allow multiple selections, default false')
});

const AskUserQuestionSchema = z.object({
    questions: z.array(QuestionSchema).describe('List of questions')
});

// ========== Default Instructions ==========

const DEFAULT_INSTRUCTIONS = `向用户询问问题并获取选择。使用此工具在需要用户输入或确认时与用户交互。`;

const SYSTEM_PROMPT_APPENDIX = `When you need clarification from the user, especially when presenting multiple options or choices, use the MCP server \`user_interaction\` tool \`AskUserQuestion\` to ask questions.
Tool identifiers may differ across providers. Do not assume a fixed prefix or delimiter; select the tool that matches this server + tool pair.
The user's response will be returned to you through the same tool.`;

// ========== User Interaction MCP Server ==========

/**
 * User Interaction MCP Server Implementation
 * 
 * Uses VS Code's QuickPick and InputBox for user interaction.
 */
export class UserInteractionMcpServer extends McpServerBase {
    
    constructor() {
        super({
            name: 'user-interaction',
            version: '1.0.0',
            description: '用户交互工具服务器，提供向用户提问等功能'
        });
    }

    getSystemPromptAppendix(): string {
        return SYSTEM_PROMPT_APPENDIX;
    }

    getAllowedTools(): string[] {
        return ['AskUserQuestion'];
    }

    async initialize(): Promise<void> {
        // Register AskUserQuestion tool
        this.server.tool(
            'AskUserQuestion',
            DEFAULT_INSTRUCTIONS,
            {
                questions: {
                    type: 'array',
                    description: 'List of questions',
                    items: {
                        type: 'object',
                        properties: {
                            question: {
                                type: 'string',
                                description: 'Question content'
                            },
                            header: {
                                type: 'string',
                                description: 'Question header/category label'
                            },
                            options: {
                                type: 'array',
                                description: 'List of options',
                                items: {
                                    type: 'object',
                                    properties: {
                                        label: {
                                            type: 'string',
                                            description: 'Option display text'
                                        },
                                        description: {
                                            type: 'string',
                                            description: 'Option description (optional)'
                                        }
                                    },
                                    required: ['label']
                                }
                            },
                            multiSelect: {
                                type: 'boolean',
                                description: 'Allow multiple selections, default false'
                            }
                        },
                        required: ['question', 'header', 'options']
                    }
                }
            },
            async (args: Record<string, unknown>) => {
                return this.handleAskUserQuestion(args);
            }
        );

        console.log('[UserInteraction MCP] Initialized with AskUserQuestion tool');
    }

    /**
     * Handle AskUserQuestion tool call
     */
    private async handleAskUserQuestion(args: Record<string, unknown>): Promise<{
        content: Array<{ type: 'text'; text: string }>;
        isError?: boolean;
    }> {
        try {
            // Parse and validate arguments
            const parsed = AskUserQuestionSchema.safeParse(args);
            if (!parsed.success) {
                const errorMsg = `参数格式错误: ${parsed.error.message}`;
                console.error('[UserInteraction MCP]', errorMsg);
                return createToolResult(errorMsg, true);
            }

            const { questions } = parsed.data;
            
            if (questions.length === 0) {
                return createToolResult('No questions provided', true);
            }

            console.log(`[UserInteraction MCP] Received ${questions.length} question(s)`);

            // Collect answers for all questions
            const answers: UserAnswerItem[] = [];

            for (const q of questions) {
                const answer = await this.askSingleQuestion(q);
                if (answer === null) {
                    // User cancelled
                    return createToolResult('用户取消了问题', true);
                }
                answers.push({
                    question: q.question,
                    header: q.header,
                    answer
                });
            }

            // Format response as Markdown
            const content = this.formatAnswersAsMarkdown(questions, answers);
            console.log('[UserInteraction MCP] Answers collected successfully');
            
            return createToolResult(content);

        } catch (error) {
            const errorMsg = error instanceof Error ? error.message : 'Unknown error';
            console.error('[UserInteraction MCP] Error:', errorMsg);
            return createToolResult(`处理用户问题时发生错误: ${errorMsg}`, true);
        }
    }

    /**
     * Ask a single question using VS Code UI
     */
    private async askSingleQuestion(q: QuestionItem): Promise<string | null> {
        const header = q.header || 'Question';
        
        // If no options provided, use InputBox
        if (!q.options || q.options.length === 0) {
            const result = await vscode.window.showInputBox({
                title: header,
                prompt: q.question,
                placeHolder: 'Type your answer...',
                ignoreFocusOut: true
            });
            return result ?? null;
        }

        // Convert options to QuickPickItems
        const items: vscode.QuickPickItem[] = q.options.map(opt => ({
            label: opt.label,
            description: opt.description
        }));

        // Add "Other..." option for custom input
        items.push({
            label: '$(edit) Other...',
            description: 'Enter a custom answer'
        });

        if (q.multiSelect) {
            // Multi-select mode
            const selected = await vscode.window.showQuickPick(items, {
                title: `${header}: ${q.question}`,
                placeHolder: 'Select one or more options (use space to toggle)',
                canPickMany: true,
                ignoreFocusOut: true
            });

            if (!selected || selected.length === 0) {
                return null;
            }

            // Check if "Other..." was selected
            const otherSelected = selected.some(s => s.label.includes('Other...'));
            const regularSelections = selected
                .filter(s => !s.label.includes('Other...'))
                .map(s => s.label);

            if (otherSelected) {
                const customInput = await vscode.window.showInputBox({
                    title: `${header}: Custom Answer`,
                    prompt: 'Enter your custom answer',
                    placeHolder: 'Type here...',
                    ignoreFocusOut: true
                });
                if (customInput) {
                    regularSelections.push(customInput);
                }
            }

            return regularSelections.join(', ');

        } else {
            // Single-select mode
            const selected = await vscode.window.showQuickPick(items, {
                title: `${header}: ${q.question}`,
                placeHolder: 'Select an option',
                ignoreFocusOut: true
            });

            if (!selected) {
                return null;
            }

            // Check if "Other..." was selected
            if (selected.label.includes('Other...')) {
                const customInput = await vscode.window.showInputBox({
                    title: `${header}: Custom Answer`,
                    prompt: 'Enter your custom answer',
                    placeHolder: 'Type here...',
                    ignoreFocusOut: true
                });
                return customInput ?? null;
            }

            return selected.label;
        }
    }

    /**
     * Format answers as Markdown
     */
    private formatAnswersAsMarkdown(questions: QuestionItem[], answers: UserAnswerItem[]): string {
        const lines: string[] = ['## User Answers', ''];

        for (let i = 0; i < questions.length; i++) {
            const q = questions[i];
            const a = answers.find(ans => ans.question === q.question);
            const answer = a?.answer || '(no answer)';
            const header = q.header || `Question ${i + 1}`;

            lines.push(`### ${header}`);
            lines.push(`**Q:** ${q.question}`);
            lines.push(`**A:** ${answer}`);
            lines.push('');
        }

        return lines.join('\n').trim();
    }

    dispose(): void {
        console.log('[UserInteraction MCP] Disposed');
    }
}

// ========== MCP Server Provider ==========

/**
 * User Interaction MCP Server Provider
 */
export class UserInteractionMcpServerProvider implements McpServerProvider {
    name = 'user-interaction';
    private server: UserInteractionMcpServer;

    constructor() {
        this.server = new UserInteractionMcpServer();
    }

    getServer(): McpServer {
        return this.server.getServer();
    }

    getDisallowedBuiltinTools(): string[] {
        // No builtin tools to disable
        return [];
    }

    async initialize(): Promise<void> {
        await this.server.initialize();
    }

    dispose(): void {
        this.server.dispose();
    }
}
