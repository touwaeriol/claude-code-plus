/**
 * User Interaction MCP Server for VS Code
 * 
 * Provides AskUserQuestion tool for Claude to interact with users.
 * Uses RSocket to communicate with frontend Vue component.
 * 
 * 与 JetBrains 版本的 UserInteractionMcpServer.kt 完全对应。
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';
import { currentConnectId } from '../../../server/mcp/mcpCallContext';
import { ClientCallerRegistry } from '../../../server/rpc';
import { create } from '@bufbuild/protobuf';
import {
  AskUserQuestionRequestSchema,
  type AskUserQuestionRequest,
  type AskUserQuestionResponse,
} from '@proto/ide_pb';

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

const AskUserQuestionInputSchema = z.object({
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
 * Uses RSocket to communicate with frontend Vue component (AskUserQuestionInteractive.vue).
 * 与 JetBrains 版本架构完全一致。
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
            async (params: { questions: QuestionItem[] }) => {
                return this.handleAskUserQuestion(params);
            }
        );

        console.log('✅ [UserInteractionMcpServer] 初始化完成，已注册 AskUserQuestion 工具');
    }

    /**
     * 处理 AskUserQuestion 工具调用
     * 
     * 通过 RSocket 调用前端 AskUserQuestionInteractive.vue 组件。
     */
    private async handleAskUserQuestion(params: { questions: QuestionItem[] }): Promise<{
        content: Array<{ type: 'text'; text: string }>;
        isError?: boolean;
    }> {
        // 获取当前连接的 connectId
        const connectId = currentConnectId();
        const caller = connectId ? ClientCallerRegistry.get(connectId) : undefined;

        if (!caller) {
            console.warn(`⚠️ [AskUserQuestion] 无法获取 ClientCaller，connectId=${connectId}`);
            return createToolResult(`无法获取前端连接，connectId=${connectId}`, true);
        }

        console.log(`📩 [AskUserQuestion] 收到工具调用，参数: ${JSON.stringify(params)}`);

        try {
            // 验证参数
            const parsed = AskUserQuestionInputSchema.safeParse(params);
            if (!parsed.success) {
                const errorMsg = `参数校验失败: ${parsed.error.message}`;
                console.warn(`⚠️ [AskUserQuestion] ${errorMsg}`);
                return createToolResult(errorMsg, true);
            }

            const { questions } = parsed.data;
            console.log(`📤 [AskUserQuestion] 解析后的参数: ${questions.length} 个问题`);

            // 构建 Protobuf 请求
            const protoRequest = create(AskUserQuestionRequestSchema, {
                questions: questions.map(q => ({
                    question: q.question,
                    header: q.header || '',
                    multiSelect: q.multiSelect || false,
                    options: (q.options || []).map(opt => ({
                        label: opt.label,
                        description: opt.description || ''
                    }))
                }))
            });

            // 通过 RSocket 调用前端
            const protoResponse = await caller.callAskUserQuestion(protoRequest);

            console.log(`📥 [AskUserQuestion] 收到前端响应: ${protoResponse.answers.length} 个回答`);

            // 构建回答映射
            const answersMap: Map<string, string> = new Map();
            for (const answer of protoResponse.answers) {
                answersMap.set(answer.question, answer.answer);
            }

            // 生成 Markdown 格式的回复
            let content = '## User Answers\n\n';
            questions.forEach((q, index) => {
                const answer = answersMap.get(q.question) || '(no answer)';
                const header = q.header || `Question ${index + 1}`;
                content += `### ${header}\n`;
                content += `**Q:** ${q.question}\n`;
                content += `**A:** ${answer}\n\n`;
            });

            console.log(`✅ [AskUserQuestion] 完成，返回:\n${content.trim()}`);
            return createToolResult(content.trim());

        } catch (e) {
            const errorMsg = e instanceof Error ? e.message : String(e);
            console.error(`❌ [AskUserQuestion] 处理失败: ${errorMsg}`);
            return createToolResult(`处理用户问题时发生错误: ${errorMsg}`, true);
        }
    }
}

// ========== Provider ==========

/**
 * User Interaction MCP Server Provider
 */
export class UserInteractionMcpServerProvider implements McpServerProvider {
    name = 'user-interaction';
    private server: UserInteractionMcpServer;

    constructor() {
        this.server = new UserInteractionMcpServer();
    }

    async initialize(): Promise<void> {
        await this.server.initialize();
    }

    getServer(): McpServer {
        return this.server.getServer();
    }

    getDisallowedBuiltinTools(): string[] {
        return [];
    }

    dispose(): void {
        // Cleanup if needed
    }
}
