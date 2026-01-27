/**
 * 字体处理辅助类
 * 负责查找和加载 VS Code 内置字体和系统字体
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/tools/FontHelper.kt
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

/**
 * 字体数据结构
 */
export interface FontData {
    fontFamily: string;
    data: Buffer;
    format: string;
    mimeType: string;
}

export class FontHelper {
    /**
     * VS Code / 常见编辑器内置字体名称到文件名的映射表
     */
    private static readonly fontNameMapping: Record<string, string> = {
        // JetBrains 字体
        'jetbrains mono': 'JetBrainsMono-Regular',
        'jetbrainsmono': 'JetBrainsMono-Regular',
        'fira code': 'FiraCode-Regular',
        'firacode': 'FiraCode-Regular',
        // 常见编程字体
        'consolas': 'consola',
        'cascadia code': 'CascadiaCode',
        'cascadiacode': 'CascadiaCode',
        'source code pro': 'SourceCodePro-Regular',
        'sourcecodepro': 'SourceCodePro-Regular',
        'ubuntu mono': 'UbuntuMono-Regular',
        'ubuntumono': 'UbuntuMono-Regular',
        'inconsolata': 'Inconsolata',
        'menlo': 'Menlo',
        'monaco': 'Monaco',
        'courier new': 'cour',
        'couriernew': 'cour',
    };

    /**
     * 获取字体文件数据
     *
     * 从系统字体目录中查找指定字体并返回其二进制数据
     * 支持 TrueType (.ttf) 和 OpenType (.otf) 字体
     */
    static async getFontData(fontFamily: string): Promise<FontData | null> {
        try {
            // 标准化字体名称（移除空格、转小写）
            const normalizedName = fontFamily.toLowerCase().replace(/\s+/g, '');

            // 查找映射表中的文件名
            const mappedFileName = this.fontNameMapping[normalizedName] || 
                                   this.fontNameMapping[fontFamily.toLowerCase()];
            console.log(`🔤 [Font] Looking for: ${fontFamily} (normalized: ${normalizedName}, mapped: ${mappedFileName})`);

            // 获取系统字体目录
            const fontDirs = this.getSystemFontDirs();

            // 搜索字体文件
            for (const fontDir of fontDirs) {
                if (!fs.existsSync(fontDir)) continue;

                const fontFile = await this.findFontFile(fontDir, normalizedName, mappedFileName);
                if (fontFile) {
                    const extension = path.extname(fontFile).toLowerCase().slice(1);
                    const format = this.getFormatFromExtension(extension);
                    const mimeType = this.getMimeTypeFromExtension(extension);

                    console.log(`✅ Found font file: ${fontFile}`);
                    return {
                        fontFamily,
                        data: fs.readFileSync(fontFile),
                        format,
                        mimeType,
                    };
                }
            }

            console.log(`⚠️ Font not found: ${fontFamily}`);
            return null;
        } catch (e) {
            console.warn(`Failed to get font data: ${e}`);
            return null;
        }
    }

    /**
     * 获取系统字体目录列表
     */
    private static getSystemFontDirs(): string[] {
        const dirs: string[] = [];
        const platform = os.platform();

        if (platform === 'win32') {
            // Windows 字体目录
            dirs.push('C:\\Windows\\Fonts');
            const localAppData = process.env.LOCALAPPDATA;
            if (localAppData) {
                dirs.push(path.join(localAppData, 'Microsoft', 'Windows', 'Fonts'));
            }
        } else if (platform === 'darwin') {
            // macOS 字体目录
            dirs.push('/System/Library/Fonts');
            dirs.push('/Library/Fonts');
            dirs.push(path.join(os.homedir(), 'Library', 'Fonts'));
        } else {
            // Linux 字体目录
            dirs.push('/usr/share/fonts');
            dirs.push('/usr/local/share/fonts');
            dirs.push(path.join(os.homedir(), '.fonts'));
            dirs.push(path.join(os.homedir(), '.local', 'share', 'fonts'));
        }

        // VS Code 扩展目录中的字体
        const vscodeExtPath = vscode.extensions.all
            .find(ext => ext.id.includes('font'))?.extensionPath;
        if (vscodeExtPath) {
            dirs.push(vscodeExtPath);
        }

        return dirs.filter(dir => {
            try {
                return fs.existsSync(dir);
            } catch {
                return false;
            }
        });
    }

    /**
     * 在目录中递归搜索字体文件
     */
    private static async findFontFile(
        dir: string,
        normalizedName: string,
        mappedFileName?: string
    ): Promise<string | null> {
        const fontExtensions = new Set(['ttf', 'otf', 'woff', 'woff2']);

        // 递归获取所有字体文件
        const files = await this.walkDir(dir, fontExtensions);

        // 1. 首先尝试使用映射的文件名精确匹配
        if (mappedFileName) {
            const mappedLower = mappedFileName.toLowerCase();
            for (const file of files) {
                const fileName = path.basename(file, path.extname(file)).toLowerCase();
                if (fileName === mappedLower || fileName.startsWith(mappedLower)) {
                    return file;
                }
            }
        }

        // 2. 尝试标准化名称精确匹配
        for (const file of files) {
            const fileName = path.basename(file, path.extname(file))
                .toLowerCase()
                .replace(/[\s\-_]/g, '');
            if (fileName === normalizedName ||
                fileName === normalizedName.replace(/-/g, '') ||
                fileName.startsWith(normalizedName)) {
                return file;
            }
        }

        // 3. 尝试匹配常见变体
        const variants = [
            normalizedName,
            `${normalizedName}regular`,
            `${normalizedName}-regular`,
            `${normalizedName}_regular`,
            `${normalizedName}medium`,
            `${normalizedName}-medium`,
        ];

        for (const file of files) {
            const fileName = path.basename(file, path.extname(file))
                .toLowerCase()
                .replace(/[\s\-_]/g, '');
            if (variants.some(v => fileName.includes(v))) {
                return file;
            }
        }

        return null;
    }

    /**
     * 递归遍历目录，收集指定扩展名的文件
     */
    private static async walkDir(dir: string, extensions: Set<string>): Promise<string[]> {
        const results: string[] = [];

        try {
            const entries = fs.readdirSync(dir, { withFileTypes: true });

            for (const entry of entries) {
                const fullPath = path.join(dir, entry.name);

                if (entry.isDirectory()) {
                    // 递归子目录（限制深度避免过深递归）
                    const subResults = await this.walkDir(fullPath, extensions);
                    results.push(...subResults);
                } else if (entry.isFile()) {
                    const ext = path.extname(entry.name).toLowerCase().slice(1);
                    if (extensions.has(ext)) {
                        results.push(fullPath);
                    }
                }
            }
        } catch (e) {
            // 忽略无法访问的目录
        }

        return results;
    }

    /**
     * 根据扩展名获取字体格式
     */
    private static getFormatFromExtension(extension: string): string {
        switch (extension) {
            case 'ttf': return 'truetype';
            case 'otf': return 'opentype';
            case 'woff': return 'woff';
            case 'woff2': return 'woff2';
            default: return 'truetype';
        }
    }

    /**
     * 根据扩展名获取 MIME 类型
     */
    private static getMimeTypeFromExtension(extension: string): string {
        switch (extension) {
            case 'ttf': return 'font/ttf';
            case 'otf': return 'font/otf';
            case 'woff': return 'font/woff';
            case 'woff2': return 'font/woff2';
            default: return 'font/ttf';
        }
    }
}
