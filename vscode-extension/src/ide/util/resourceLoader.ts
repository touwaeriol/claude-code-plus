/**
 * 资源文件加载工具
 *
 * 用于从 resources 目录加载各种配置文件，包括：
 * - 子代理定义 (agents/agents.json)
 * - MCP prompts (prompts/[name].md)
 *
 * 特性：
 * - 支持缓存，多个会话共享加载的数据
 * - 支持手动刷新缓存
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/utils/ResourceLoader.kt
 */

import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { getLogger } from '../../logging';

const logger = getLogger('ResourceLoader');

// ==================== 类型定义 ====================

/**
 * JSON 格式的代理定义
 */
export interface AgentJsonDefinition {
    description: string;
    prompt: string;
    tools?: string[];
    model?: string;
}

/**
 * 子代理配置文件的 JSON 结构
 */
export interface AgentsConfig {
    agents: Record<string, AgentJsonDefinition>;
}

/**
 * 代理定义（运行时使用）
 */
export interface AgentDefinition {
    description: string;
    prompt: string;
    tools?: string[];
    model?: string;
}

// ==================== ResourceLoader 类 ====================

/**
 * 资源文件加载工具
 */
export class ResourceLoader {
    private static readonly AGENTS_JSON_PATH = 'resources/agents/agents.json';

    /** 扩展上下文（用于获取资源路径） */
    private static extensionContext: vscode.ExtensionContext | undefined;

    /** 缓存的代理定义 */
    private static cachedAgents: Map<string, AgentDefinition> | null = null;

    /**
     * 初始化 ResourceLoader
     * 必须在使用前调用，通常在扩展激活时
     *
     * @param context VS Code 扩展上下文
     */
    static initialize(context: vscode.ExtensionContext): void {
        this.extensionContext = context;
        logger.info('ResourceLoader initialized');
    }

    /**
     * 获取扩展资源的绝对路径
     *
     * @param resourcePath 相对于扩展根目录的资源路径
     * @returns 绝对路径
     */
    static getResourcePath(resourcePath: string): string | null {
        if (!this.extensionContext) {
            logger.warn('ResourceLoader not initialized, cannot get resource path');
            return null;
        }

        return path.join(this.extensionContext.extensionPath, resourcePath);
    }

    /**
     * 从资源文件加载文本内容
     *
     * @param resourcePath 资源路径，如 "resources/prompts/jetbrains-mcp-instructions.md"
     * @returns 文件内容，如果文件不存在则返回 null
     */
    static loadText(resourcePath: string): string | null {
        try {
            const absolutePath = this.getResourcePath(resourcePath);
            if (!absolutePath) {
                return null;
            }

            if (!fs.existsSync(absolutePath)) {
                logger.warn(`Resource not found: ${resourcePath}`);
                return null;
            }

            const content = fs.readFileSync(absolutePath, 'utf-8');
            logger.info(`Loaded resource '${resourcePath}'`);
            return content;
        } catch (e) {
            logger.warn(`Failed to load resource: ${resourcePath}`, e instanceof Error ? e : undefined);
            return null;
        }
    }

    /**
     * 异步从资源文件加载文本内容
     *
     * @param resourcePath 资源路径
     * @returns 文件内容，如果文件不存在则返回 null
     */
    static async loadTextAsync(resourcePath: string): Promise<string | null> {
        try {
            const absolutePath = this.getResourcePath(resourcePath);
            if (!absolutePath) {
                return null;
            }

            if (!fs.existsSync(absolutePath)) {
                logger.warn(`Resource not found: ${resourcePath}`);
                return null;
            }

            const content = await fs.promises.readFile(absolutePath, 'utf-8');
            logger.info(`Loaded resource '${resourcePath}'`);
            return content;
        } catch (e) {
            logger.warn(`Failed to load resource: ${resourcePath}`, e instanceof Error ? e : undefined);
            return null;
        }
    }

    /**
     * 从资源文件加载文本内容，如果文件不存在则返回默认值
     *
     * @param resourcePath 资源路径
     * @param defaultValue 默认值
     * @returns 文件内容或默认值
     */
    static loadTextOrDefault(resourcePath: string, defaultValue: string): string {
        return this.loadText(resourcePath) ?? defaultValue;
    }

    /**
     * 加载所有子代理定义（带缓存）
     *
     * @param forceReload 是否强制重新加载（忽略缓存）
     * @returns 代理名称到定义的映射
     */
    static loadAllAgentDefinitions(forceReload: boolean = false): Map<string, AgentDefinition> {
        // 如果不强制重新加载且缓存存在，直接返回缓存
        if (!forceReload && this.cachedAgents) {
            logger.debug(`Using cached agent definitions (${this.cachedAgents.size} agents)`);
            return this.cachedAgents;
        }

        // 加载并解析 JSON
        const agents = this.loadAgentsFromJson();

        // 更新缓存
        this.cachedAgents = agents;

        if (agents.size > 0) {
            logger.info(`Loaded ${agents.size} agent definitions from ${this.AGENTS_JSON_PATH}`);
        }

        return agents;
    }

    /**
     * 刷新缓存（下次调用 loadAllAgentDefinitions 时重新加载）
     */
    static invalidateCache(): void {
        this.cachedAgents = null;
        logger.info('Agent definitions cache invalidated');
    }

    /**
     * 强制重新加载代理定义
     *
     * @returns 代理名称到定义的映射
     */
    static reloadAgentDefinitions(): Map<string, AgentDefinition> {
        return this.loadAllAgentDefinitions(true);
    }

    /**
     * 从 JSON 文件加载代理定义
     */
    private static loadAgentsFromJson(): Map<string, AgentDefinition> {
        logger.info(`Loading agent definitions from: ${this.AGENTS_JSON_PATH}`);
        
        try {
            const content = this.loadText(this.AGENTS_JSON_PATH);
            if (!content) {
                logger.warn(`Agent definitions file not found: ${this.AGENTS_JSON_PATH}`);
                return new Map();
            }

            logger.info(`Agent JSON content length: ${content.length} chars`);
            const config: AgentsConfig = JSON.parse(content);
            logger.info(`Parsed ${Object.keys(config.agents || {}).length} agents from JSON`);

            // 转换为 Map<string, AgentDefinition>
            const result = new Map<string, AgentDefinition>();
            for (const [name, jsonDef] of Object.entries(config.agents || {})) {
                const definition: AgentDefinition = {
                    description: jsonDef.description,
                    prompt: jsonDef.prompt,
                    tools: jsonDef.tools,
                    model: jsonDef.model
                };
                result.set(name, definition);
                logger.info(`Loaded agent: ${name} (tools: ${jsonDef.tools?.length ?? 0})`);
            }

            return result;
        } catch (e) {
            logger.error(`Failed to load agent definitions from JSON`, e instanceof Error ? e : undefined);
            return new Map();
        }
    }

    /**
     * 加载 JSON 文件并解析
     *
     * @param resourcePath 资源路径
     * @returns 解析后的对象，如果失败则返回 null
     */
    static loadJson<T>(resourcePath: string): T | null {
        try {
            const content = this.loadText(resourcePath);
            if (!content) {
                return null;
            }
            return JSON.parse(content) as T;
        } catch (e) {
            logger.error(`Failed to parse JSON from: ${resourcePath}`, e instanceof Error ? e : undefined);
            return null;
        }
    }

    /**
     * 列出目录中的所有文件
     *
     * @param resourceDir 资源目录路径
     * @param extension 可选的文件扩展名过滤
     * @returns 文件名列表
     */
    static listFiles(resourceDir: string, extension?: string): string[] {
        try {
            const absolutePath = this.getResourcePath(resourceDir);
            if (!absolutePath || !fs.existsSync(absolutePath)) {
                return [];
            }

            let files = fs.readdirSync(absolutePath);
            
            if (extension) {
                const ext = extension.startsWith('.') ? extension : `.${extension}`;
                files = files.filter(f => f.endsWith(ext));
            }

            return files;
        } catch (e) {
            logger.warn(`Failed to list files in: ${resourceDir}`, e instanceof Error ? e : undefined);
            return [];
        }
    }
}

// ==================== 辅助函数 ====================

/**
 * 加载文本资源
 */
export function loadTextResource(resourcePath: string): string | null {
    return ResourceLoader.loadText(resourcePath);
}

/**
 * 加载文本资源或返回默认值
 */
export function loadTextResourceOrDefault(resourcePath: string, defaultValue: string): string {
    return ResourceLoader.loadTextOrDefault(resourcePath, defaultValue);
}

/**
 * 加载 JSON 资源
 */
export function loadJsonResource<T>(resourcePath: string): T | null {
    return ResourceLoader.loadJson<T>(resourcePath);
}
