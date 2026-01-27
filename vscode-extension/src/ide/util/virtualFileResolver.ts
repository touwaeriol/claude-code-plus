/**
 * 虚拟文件解析器
 *
 * 支持多种路径格式：
 * - 相对路径（相对于项目根目录）: frontend/src/App.vue
 * - 绝对路径: C:/path/to/file.java 或 /path/to/file.java
 * - zip/jar 路径: C:/path.zip!/inner/path
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/util/VirtualFileResolver.kt
 */

import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { getLogger } from '../../logging';

const logger = getLogger('VirtualFileResolver');

/**
 * 文件解析结果
 */
export interface ResolvedFile {
    /** 文件绝对路径 */
    absolutePath: string;
    /** 是否存在 */
    exists: boolean;
    /** 是否为目录 */
    isDirectory: boolean;
    /** 是否为归档内文件 (zip/jar) */
    isArchiveEntry: boolean;
    /** 归档文件路径 (如果是归档内文件) */
    archivePath?: string;
    /** 归档内部路径 (如果是归档内文件) */
    innerPath?: string;
}

/**
 * 通用的虚拟文件解析器
 */
export class VirtualFileResolver {
    /**
     * 解析文件路径
     *
     * @param filePath 文件路径（支持多种格式）
     * @param basePath 项目根目录（用于解析相对路径，可选）
     * @returns ResolvedFile 或 null（如果无法解析）
     */
    static resolve(filePath: string, basePath?: string): ResolvedFile | null {
        const trimmedPath = filePath.trim();
        if (!trimmedPath) {
            return null;
        }

        // 0. 如果是相对路径，先尝试在项目根目录下查找
        if (this.isRelativePath(trimmedPath)) {
            const projectBasePath = basePath ?? this.getWorkspaceBasePath();
            if (projectBasePath) {
                const absolutePath = path.join(projectBasePath, trimmedPath);
                const normalized = path.normalize(absolutePath);
                
                if (fs.existsSync(normalized)) {
                    const stats = fs.statSync(normalized);
                    logger.info(`Resolved relative path '${trimmedPath}' to '${normalized}'`);
                    return {
                        absolutePath: normalized,
                        exists: true,
                        isDirectory: stats.isDirectory(),
                        isArchiveEntry: false
                    };
                }
            }
        }

        // 1. 处理包含 !/ 的归档路径 (zip/jar)
        if (trimmedPath.includes('!/')) {
            return this.resolveArchivePath(trimmedPath);
        }

        // 2. 处理 URL 格式
        if (trimmedPath.startsWith('file://')) {
            const fsPath = trimmedPath.replace('file://', '');
            return this.resolveLocalPath(fsPath);
        }

        // 3. 尝试作为普通文件路径
        return this.resolveLocalPath(trimmedPath);
    }

    /**
     * 解析归档文件路径 (zip/jar)
     */
    private static resolveArchivePath(archivePath: string): ResolvedFile | null {
        // 解析 jar:// 或 zip:// 前缀
        let cleanPath = archivePath
            .replace(/^jar:\/\//, '')
            .replace(/^zip:\/\//, '')
            .replace(/\\/g, '/');

        const parts = cleanPath.split('!/');
        if (parts.length !== 2) {
            logger.warn(`Invalid archive path format: ${archivePath}`);
            return null;
        }

        const [zipPath, innerPath] = parts;
        const normalizedZipPath = path.normalize(zipPath);

        // 检查归档文件是否存在
        if (!fs.existsSync(normalizedZipPath)) {
            logger.warn(`Archive file not found: ${normalizedZipPath}`);
            return {
                absolutePath: archivePath,
                exists: false,
                isDirectory: false,
                isArchiveEntry: true,
                archivePath: normalizedZipPath,
                innerPath: innerPath
            };
        }

        // 注意：Node.js 原生不支持直接读取 zip 内容
        // 这里只返回路径信息，实际读取需要使用 adm-zip 等库
        return {
            absolutePath: archivePath,
            exists: true,  // 归档文件存在，但内部文件是否存在未知
            isDirectory: false,
            isArchiveEntry: true,
            archivePath: normalizedZipPath,
            innerPath: innerPath
        };
    }

    /**
     * 解析本地文件路径
     */
    private static resolveLocalPath(filePath: string): ResolvedFile | null {
        const normalizedPath = path.normalize(filePath.replace(/\\/g, '/'));

        try {
            if (fs.existsSync(normalizedPath)) {
                const stats = fs.statSync(normalizedPath);
                return {
                    absolutePath: normalizedPath,
                    exists: true,
                    isDirectory: stats.isDirectory(),
                    isArchiveEntry: false
                };
            }
        } catch (e) {
            logger.debug(`Failed to stat file: ${normalizedPath}`);
        }

        return {
            absolutePath: normalizedPath,
            exists: false,
            isDirectory: false,
            isArchiveEntry: false
        };
    }

    /**
     * 判断是否为相对路径
     */
    static isRelativePath(filePath: string): boolean {
        const trimmed = filePath.trim();
        
        // 绝对路径的特征：
        // - Windows: C:/ D:\ 等
        // - Unix: / 开头
        // - URL: file:// jar:// jrt:// 等
        const isWindowsAbsolute = /^[a-zA-Z]:[/\\]/.test(trimmed);
        const isUnixAbsolute = trimmed.startsWith('/');
        const isUrlScheme = trimmed.includes('://');

        return !isWindowsAbsolute && !isUnixAbsolute && !isUrlScheme;
    }

    /**
     * 从 VS Code 工作区获取基础路径
     */
    static getWorkspaceBasePath(): string | undefined {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (workspaceFolders && workspaceFolders.length > 0) {
            return workspaceFolders[0].uri.fsPath;
        }
        return undefined;
    }

    /**
     * 检查文件是否存在
     */
    static exists(filePath: string, basePath?: string): boolean {
        const resolved = this.resolve(filePath, basePath);
        return resolved?.exists ?? false;
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @param basePath 项目根目录
     * @param encoding 文件编码，默认 utf-8
     * @returns 文件内容或 null
     */
    static readFile(filePath: string, basePath?: string, encoding: BufferEncoding = 'utf-8'): string | null {
        const resolved = this.resolve(filePath, basePath);
        
        if (!resolved || !resolved.exists) {
            return null;
        }

        if (resolved.isArchiveEntry) {
            // 归档文件内容读取需要额外库支持
            logger.warn(`Reading archive entries is not supported natively: ${filePath}`);
            return null;
        }

        try {
            return fs.readFileSync(resolved.absolutePath, encoding);
        } catch (e) {
            logger.error(`Failed to read file: ${resolved.absolutePath}`, e instanceof Error ? e : undefined);
            return null;
        }
    }

    /**
     * 异步读取文件内容
     */
    static async readFileAsync(filePath: string, basePath?: string, encoding: BufferEncoding = 'utf-8'): Promise<string | null> {
        const resolved = this.resolve(filePath, basePath);
        
        if (!resolved || !resolved.exists) {
            return null;
        }

        if (resolved.isArchiveEntry) {
            logger.warn(`Reading archive entries is not supported natively: ${filePath}`);
            return null;
        }

        try {
            return await fs.promises.readFile(resolved.absolutePath, encoding);
        } catch (e) {
            logger.error(`Failed to read file: ${resolved.absolutePath}`, e instanceof Error ? e : undefined);
            return null;
        }
    }
}

// ==================== 辅助函数 ====================

/**
 * 解析文件路径
 */
export function resolveFile(filePath: string, basePath?: string): ResolvedFile | null {
    return VirtualFileResolver.resolve(filePath, basePath);
}

/**
 * 检查文件是否存在
 */
export function fileExists(filePath: string, basePath?: string): boolean {
    return VirtualFileResolver.exists(filePath, basePath);
}

/**
 * 读取文件内容
 */
export function readFileContent(filePath: string, basePath?: string): string | null {
    return VirtualFileResolver.readFile(filePath, basePath);
}
