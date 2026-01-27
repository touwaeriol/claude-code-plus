/**
 * 项目路径解析工具
 *
 * 统一处理项目相对路径到绝对路径的转换。
 * 支持 Windows 和 Unix 风格路径。
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/util/PathResolver.kt
 */

import * as path from 'path';
import * as vscode from 'vscode';

export class PathResolver {
    /**
     * 将路径解析为绝对路径
     *
     * @param filePath 文件路径（可以是相对路径或绝对路径）
     * @param basePath 项目根目录路径
     * @returns 规范化的绝对路径
     */
    static resolve(filePath: string, basePath?: string): string {
        const normalizedPath = filePath.trim();

        if (this.isAbsolutePath(normalizedPath)) {
            return path.normalize(normalizedPath);
        }

        // 如果没有提供 basePath，尝试从工作区获取
        const base = basePath ?? this.getWorkspaceBasePath();
        if (!base) {
            return path.normalize(normalizedPath);
        }

        return path.normalize(path.join(base, normalizedPath));
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
     * 判断路径是否为绝对路径
     *
     * 支持：
     * - Unix 绝对路径：以 / 开头
     * - Windows 绝对路径：以盘符开头（如 C:, D:）
     * - Windows UNC 路径：以 \\ 开头
     *
     * @param filePath 文件路径
     * @returns 是否为绝对路径
     */
    static isAbsolutePath(filePath: string): boolean {
        const trimmed = filePath.trim();
        if (trimmed.length === 0) return false;

        // Unix 绝对路径
        if (trimmed.startsWith('/')) return true;

        // Windows 盘符路径 (C:, D:, etc.)
        if (trimmed.length >= 2 && trimmed[1] === ':') return true;

        // Windows UNC 路径
        if (trimmed.startsWith('\\\\')) return true;

        return false;
    }

    /**
     * 判断路径是否为相对路径
     */
    static isRelativePath(filePath: string): boolean {
        return !this.isAbsolutePath(filePath);
    }

    /**
     * 将绝对路径转换为相对于项目的路径
     *
     * @param absolutePath 绝对路径
     * @param basePath 项目根目录路径
     * @returns 相对路径，如果无法转换则返回原路径
     */
    static toRelative(absolutePath: string, basePath?: string): string {
        const base = basePath ?? this.getWorkspaceBasePath();
        if (!base) return absolutePath;

        const normalizedAbsolute = path.normalize(absolutePath);
        const normalizedBase = path.normalize(base);

        if (normalizedAbsolute.startsWith(normalizedBase)) {
            let relative = normalizedAbsolute.slice(normalizedBase.length);
            // 移除开头的路径分隔符
            if (relative.startsWith(path.sep)) {
                relative = relative.slice(1);
            }
            return relative || '.';
        }

        return absolutePath;
    }

    /**
     * 规范化路径分隔符（转换为当前平台的分隔符）
     */
    static normalizeSeparators(filePath: string): string {
        return path.normalize(filePath);
    }

    /**
     * 将路径转换为 Unix 风格（使用 /）
     */
    static toUnixStyle(filePath: string): string {
        return filePath.replace(/\\/g, '/');
    }

    /**
     * 将路径转换为 Windows 风格（使用 \）
     */
    static toWindowsStyle(filePath: string): string {
        return filePath.replace(/\//g, '\\');
    }
}

// ==================== 辅助函数 ====================

/**
 * 将路径转换为绝对路径
 */
export function toAbsolutePath(filePath: string, basePath?: string): string {
    return PathResolver.resolve(filePath, basePath);
}

/**
 * 将绝对路径转换为相对路径
 */
export function toRelativePath(absolutePath: string, basePath?: string): string {
    return PathResolver.toRelative(absolutePath, basePath);
}
