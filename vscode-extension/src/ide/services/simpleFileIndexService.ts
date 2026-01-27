/**
 * 简单的 FileIndexService 实现
 *
 * 使用 VS Code workspace API 提供基本的文件搜索功能
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/adapters/SimpleFileIndexService.kt
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import {
    FileIndexService,
    IndexedFileInfo,
    IndexedSymbolInfo,
    IndexStats,
    SymbolType,
    IndexingInProgressError,
} from './fileIndexService';
import { PathResolver } from '../util/pathResolver';

/**
 * 常用文件扩展名列表
 */
const COMMON_EXTENSIONS = [
    'kt', 'java', 'js', 'ts', 'tsx', 'jsx', 'py', 'rb', 'go', 'rs', 'c', 'cpp', 'h', 'hpp',
    'md', 'txt', 'json', 'yaml', 'yml', 'xml', 'html', 'css', 'scss', 'less', 'vue',
    'gradle', 'properties', 'kts', 'sh', 'bat', 'ps1',
];

/**
 * 支持的文件类型列表
 */
const SUPPORTED_FILE_TYPES = ['kt', 'java', 'js', 'ts', 'py', 'md', 'json', 'xml', 'vue', 'html', 'css'];

/**
 * 简单的 FileIndexService 实现
 * 使用 VS Code workspace API 提供基本的文件搜索功能
 */
export class SimpleFileIndexService implements FileIndexService {
    private static instance: SimpleFileIndexService | null = null;
    private isIndexing = false;

    private constructor() {}

    /**
     * 获取单例实例
     */
    static getInstance(): SimpleFileIndexService {
        if (!this.instance) {
            this.instance = new SimpleFileIndexService();
        }
        return this.instance;
    }

    /**
     * 检查索引是否正在进行
     * VS Code 没有类似 IDEA 的 DumbService，所以默认返回 false
     */
    private checkIndexing(): boolean {
        return this.isIndexing;
    }

    /**
     * 初始化索引
     * VS Code 自动管理文件索引，不需要手动初始化
     */
    async initialize(_rootPath: string): Promise<void> {
        // VS Code 自动管理索引
    }

    /**
     * 索引指定路径
     * VS Code 自动管理文件索引
     */
    async indexPath(_path: string, _recursive: boolean): Promise<void> {
        // VS Code 自动管理索引
    }

    /**
     * 搜索文件
     */
    async searchFiles(
        query: string,
        maxResults: number,
        fileTypes: string[] = []
    ): Promise<IndexedFileInfo[]> {
        if (!query.trim()) {
            return [];
        }

        if (this.checkIndexing()) {
            throw new IndexingInProgressError('Project is indexing, please wait');
        }

        try {
            const queryLower = query.toLowerCase();
            const results: IndexedFileInfo[] = [];

            // 使用 VS Code findFiles API
            const pattern = `**/*${query}*`;
            const excludePattern = '**/node_modules/**,**/.git/**,**/dist/**,**/build/**';
            const files = await vscode.workspace.findFiles(pattern, excludePattern, maxResults * 2);

            for (const file of files) {
                if (results.length >= maxResults) break;

                const fileInfo = await this.createFileInfo(file);
                if (fileInfo) {
                    // 检查文件类型过滤
                    if (fileTypes.length === 0 || fileTypes.includes(fileInfo.fileType.toLowerCase())) {
                        results.push(fileInfo);
                    }
                }
            }

            // 按相关性排序：精确匹配 > 前缀匹配 > 包含匹配
            return results.sort((a, b) => {
                const aScore = this.getMatchScore(a.name, query);
                const bScore = this.getMatchScore(b.name, query);
                return aScore - bScore;
            }).slice(0, maxResults);
        } catch (e) {
            console.warn(`Search failed: ${e}`);
            return [];
        }
    }

    /**
     * 获取匹配分数
     */
    private getMatchScore(fileName: string, query: string): number {
        const fileNameLower = fileName.toLowerCase();
        const queryLower = query.toLowerCase();

        if (fileNameLower === queryLower) return 0;
        if (fileNameLower.startsWith(queryLower)) return 1;
        if (fileNameLower.includes(queryLower)) return 2;
        return 3;
    }

    /**
     * 按文件名查找文件
     */
    async findFilesByName(fileName: string, maxResults: number): Promise<IndexedFileInfo[]> {
        return this.searchFiles(fileName, maxResults);
    }

    /**
     * 搜索符号
     * 暂不实现符号搜索，可以后续使用 VS Code 的符号 API
     */
    async searchSymbols(
        _query: string,
        _symbolTypes: SymbolType[],
        _maxResults: number
    ): Promise<IndexedSymbolInfo[]> {
        // 可以后续使用 vscode.commands.executeCommand('vscode.executeWorkspaceSymbolProvider', query)
        return [];
    }

    /**
     * 获取最近文件
     */
    async getRecentFiles(maxResults: number): Promise<IndexedFileInfo[]> {
        try {
            console.log(`getRecentFiles called, maxResults=${maxResults}`);

            const results: IndexedFileInfo[] = [];

            // 使用 VS Code findFiles API 获取常用文件
            const patterns = COMMON_EXTENSIONS.map(ext => `**/*.${ext}`);
            const excludePattern = '**/node_modules/**,**/.git/**,**/dist/**,**/build/**';

            for (const pattern of patterns) {
                if (results.length >= maxResults) break;

                const files = await vscode.workspace.findFiles(pattern, excludePattern, maxResults);
                for (const file of files) {
                    if (results.length >= maxResults) break;

                    const fileInfo = await this.createFileInfo(file);
                    if (fileInfo) {
                        results.push(fileInfo);
                    }
                }
            }

            // 按优先级排序
            const sortedResults = results.sort((a, b) => {
                const aPriority = this.getFilePriority(a.fileType);
                const bPriority = this.getFilePriority(b.fileType);
                if (aPriority !== bPriority) {
                    return aPriority - bPriority;
                }
                return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
            });

            console.log(`Found ${sortedResults.length} files`);
            return sortedResults.slice(0, maxResults);
        } catch (e) {
            console.warn(`Failed to get recent files: ${e}`);
            return [];
        }
    }

    /**
     * 获取文件优先级
     */
    private getFilePriority(fileType: string): number {
        const type = fileType.toLowerCase();
        // 优先级：配置文件 > 源代码文件 > 其他
        if (['gradle', 'kts', 'properties', 'json', 'yaml', 'yml'].includes(type)) {
            return 0;
        }
        if (['kt', 'java', 'ts', 'js', 'tsx', 'jsx'].includes(type)) {
            return 1;
        }
        return 2;
    }

    /**
     * 获取最近修改的文件
     */
    async getRecentlyModifiedFiles(projectPath: string, limit: number): Promise<IndexedFileInfo[]> {
        try {
            const results: IndexedFileInfo[] = [];
            const excludePattern = '**/node_modules/**,**/.git/**,**/dist/**,**/build/**';
            const files = await vscode.workspace.findFiles('**/*', excludePattern, limit * 3);

            for (const file of files) {
                const fileInfo = await this.createFileInfo(file);
                if (fileInfo && !fileInfo.isDirectory) {
                    results.push(fileInfo);
                }
            }

            // 按最后修改时间排序
            return results
                .sort((a, b) => b.lastModified - a.lastModified)
                .slice(0, limit);
        } catch (e) {
            console.warn(`Failed to get recently modified files: ${e}`);
            return [];
        }
    }

    /**
     * 获取文件内容
     */
    async getFileContent(filePath: string): Promise<string | null> {
        try {
            const absolutePath = PathResolver.resolve(filePath);
            return fs.readFileSync(absolutePath, 'utf-8');
        } catch (e) {
            console.warn(`Failed to read file: ${filePath}`, e);
            return null;
        }
    }

    /**
     * 获取文件符号
     * 可以后续使用 VS Code 的符号 API
     */
    async getFileSymbols(_filePath: string): Promise<IndexedSymbolInfo[]> {
        // 可以后续使用 vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider', uri)
        return [];
    }

    /**
     * 检查索引是否就绪
     */
    isIndexReady(): boolean {
        return !this.checkIndexing();
    }

    /**
     * 获取索引统计信息
     */
    async getIndexStats(): Promise<IndexStats> {
        return {
            totalFiles: 0,
            indexedFiles: 0,
            totalSymbols: 0,
            lastIndexTime: Date.now(),
            indexSizeBytes: 0,
            supportedFileTypes: SUPPORTED_FILE_TYPES,
        };
    }

    /**
     * 刷新索引
     * VS Code 自动管理
     */
    async refreshIndex(): Promise<void> {
        // VS Code 自动管理
    }

    /**
     * 清理索引
     * 无需清理
     */
    async cleanup(): Promise<void> {
        // 无需清理
    }

    /**
     * 创建文件信息
     */
    private async createFileInfo(uri: vscode.Uri): Promise<IndexedFileInfo | null> {
        try {
            const workspaceFolder = vscode.workspace.getWorkspaceFolder(uri);
            const projectPath = workspaceFolder?.uri.fsPath || '';
            const absolutePath = uri.fsPath;
            const relativePath = projectPath
                ? absolutePath.replace(projectPath, '').replace(/^[\/\\]/, '')
                : path.basename(absolutePath);

            const stat = await vscode.workspace.fs.stat(uri);
            const extension = path.extname(absolutePath).replace('.', '');

            return {
                name: path.basename(absolutePath),
                relativePath,
                absolutePath,
                fileType: extension,
                size: stat.size,
                lastModified: stat.mtime,
                isDirectory: stat.type === vscode.FileType.Directory,
                language: this.detectLanguage(extension),
                encoding: 'utf-8',
            };
        } catch (e) {
            return null;
        }
    }

    /**
     * 检测语言
     */
    private detectLanguage(extension: string): string | undefined {
        const langMap: Record<string, string> = {
            kt: 'Kotlin',
            kts: 'Kotlin',
            java: 'Java',
            js: 'JavaScript',
            jsx: 'JavaScript',
            ts: 'TypeScript',
            tsx: 'TypeScript',
            py: 'Python',
            md: 'Markdown',
            json: 'JSON',
            xml: 'XML',
            vue: 'Vue',
            html: 'HTML',
            css: 'CSS',
            scss: 'SCSS',
            go: 'Go',
            rs: 'Rust',
            rb: 'Ruby',
            c: 'C',
            cpp: 'C++',
            cs: 'C#',
        };
        return langMap[extension.toLowerCase()];
    }

    /**
     * 释放资源
     */
    dispose(): void {
        SimpleFileIndexService.instance = null;
    }
}

/**
 * 获取文件索引服务实例
 */
export function getFileIndexService(): SimpleFileIndexService {
    return SimpleFileIndexService.getInstance();
}
