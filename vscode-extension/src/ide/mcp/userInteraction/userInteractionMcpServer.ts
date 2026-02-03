/**
 * User Interaction MCP Server for VS Code
 * 
 * Provides AskUserQuestion tool for Claude to interact with users.
 * Uses RSocket to communicate with frontend Vue component.
 * 
 * 与 JetBrains 版本的 UserInteractionMcpServer.kt 完全对应。
 */

import { McpServer, RequestHandlerExtra } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';
import { ClientCallerRegistry } from '../../../server/rpc';
import { create } from '@bufbuild/protobuf';
import { Logger } from '../../../logging/logger';
import {
  AskUserQuestionRequestSchema,
  type AskUserQuestionRequest,
  type AskUserQuestionResponse,
} from '@proto/ide_pb';

// Custom AuthInfo type matching MCP SDK's AuthInfo with our extra field
interface McpAuthInfo {
    token: string;
    clientId: string;
    scopes: string[];
    extra?: {
        connectId?: string;
    };
}

// Logger instance
const logger = Logger.create('UserInteractionMcp');

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
            // IMPORTANT:
            // McpServer.tool() expects a Zod "raw shape" as the params schema.
            // Passing a plain JSON schema object here will be treated as "annotations",
            // resulting in tool.inputSchema being EMPTY, which makes the model guess params.
            {
                questions: z.array(QuestionSchema).describe('List of questions')
            },
            async (params: { questions: QuestionItem[] }, extra: RequestHandlerExtra) => {
                // Get connectId from extra.authInfo.extra (passed from mcpHttpGateway via req.auth)
                logger.info(`Tool handler called! extra = ${JSON.stringify(extra, (key, value) => {
                    if (key === 'signal') return '[AbortSignal]';
                    return value;
                })}`);
                const authInfo = extra.authInfo as McpAuthInfo | undefined;
                logger.info(`authInfo = ${JSON.stringify(authInfo)}`);
                // connectId is now in authInfo.extra.connectId (MCP SDK AuthInfo structure)
                const connectId = authInfo?.extra?.connectId;
                logger.info(`connectId = ${connectId}`);
                return this.handleAskUserQuestion(params, connectId);
            }
        );

        logger.info('初始化完成，已注册 AskUserQuestion 工具');
    }

    /**
     * 处理 AskUserQuestion 工具调用
     * 
     * 通过 RSocket 调用前端 AskUserQuestionInteractive.vue 组件。
     * 
     * @param params 问题参数
     * @param connectId 连接ID，从 extra.authInfo 获取（由 mcpHttpGateway 通过 req.auth 传递）
     */
    private async handleAskUserQuestion(params: { questions: QuestionItem[] }, connectId?: string): Promise<{
        content: Array<{ type: 'text'; text: string }>;
        isError?: boolean;
    }> {
        logger.info(`收到 AskUserQuestion 调用, connectId=${connectId}`);
        
        // 列出所有已注册的 ClientCaller
        const allCallers = ClientCallerRegistry.getAll();
        logger.debug(`已注册的 ClientCaller: ${JSON.stringify(Array.from(allCallers.keys()))}`);
        
        const caller = connectId ? ClientCallerRegistry.get(connectId) : undefined;

        if (!caller) {
            const errorMsg = `无法获取 ClientCaller，connectId=${connectId}, 已注册: [${Array.from(allCallers.keys()).join(', ')}]`;
            logger.warn(errorMsg);
            return createToolResult(errorMsg, true);
        }

        logger.info(`参数: ${JSON.stringify(params)}`);

        try {
            // 验证参数
            const parsed = AskUserQuestionInputSchema.safeParse(params);
            if (!parsed.success) {
                const errorMsg = `参数校验失败: ${parsed.error.message}`;
                logger.warn(errorMsg);
                return createToolResult(errorMsg, true);
            }

            const { questions } = parsed.data;
            logger.info(`解析后的参数: ${questions.length} 个问题`);

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

            logger.info('正在调用前端 callAskUserQuestion...');
            
            // 通过 RSocket 调用前端
            const protoResponse = await caller.callAskUserQuestion(protoRequest);

            logger.info(`收到前端响应: ${protoResponse.answers.length} 个回答`);

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

            logger.info(`完成，返回:\n${content.trim()}`);
            return createToolResult(content.trim());

        } catch (e) {
            const errorMsg = e instanceof Error ? e.message : String(e);
            logger.error(`处理失败: ${errorMsg}`);
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
