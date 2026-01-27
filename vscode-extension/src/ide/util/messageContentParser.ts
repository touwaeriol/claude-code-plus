/**
 * 消息内容解析器
 *
 * 功能：
 * 1. 解析用户消息中的 @ 引用
 * 2. 识别图片路径并转换为 ImageBlock
 * 3. 保留普通文件引用给 Claude Code 处理
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/server/services/MessageContentParser.kt
 */

import * as fs from 'fs';
import * as path from 'path';
import { getLogger } from '../../logging';

const logger = getLogger('MessageContentParser');

// ==================== 类型定义 ====================

/**
 * 内容块基础类型
 */
export type ContentBlock = TextBlock | ImageBlock;

/**
 * 文本块
 */
export interface TextBlock {
    type: 'text';
    text: string;
}

/**
 * 图片源
 */
export interface ImageSource {
    type: 'base64' | 'url';
    mediaType: string;
    data: string;
}

/**
 * 图片块
 */
export interface ImageBlock {
    type: 'image';
    source: ImageSource;
}

// ==================== 常量 ====================

/** 图片文件扩展名 */
const IMAGE_EXTENSIONS = new Set([
    'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'
]);

/** MIME 类型映射 */
const MIME_TYPES: Record<string, string> = {
    'png': 'image/png',
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'gif': 'image/gif',
    'bmp': 'image/bmp',
    'webp': 'image/webp',
    'svg': 'image/svg+xml'
};

// ==================== MessageContentParser 类 ====================

/**
 * 消息内容解析器
 */
export class MessageContentParser {
    /** @ 引用的正则表达式 */
    private static readonly AT_REFERENCE_PATTERN = /@([^\s]+)/g;

    /**
     * 解析消息文本，提取 @ 引用并转换为 ContentBlock 列表
     *
     * @param text 用户输入的消息文本
     * @param convertImages 是否将图片转换为 ImageBlock（默认 false，作为文本处理）
     * @returns ContentBlock 列表（TextBlock 和 ImageBlock）
     */
    static parseMessageContent(text: string, convertImages: boolean = false): ContentBlock[] {
        const blocks: ContentBlock[] = [];
        const pattern = new RegExp(this.AT_REFERENCE_PATTERN);
        
        let lastIndex = 0;
        let match: RegExpExecArray | null;

        while ((match = pattern.exec(text)) !== null) {
            // 添加前面的文本块
            if (match.index > lastIndex) {
                const textContent = text.substring(lastIndex, match.index);
                if (textContent.trim()) {
                    blocks.push(this.createTextBlock(textContent));
                }
            }

            const referencePath = match[1];

            // 判断是否为图片路径
            if (this.isImagePath(referencePath)) {
                if (convertImages) {
                    // 尝试转换为 ImageBlock
                    const imageBlock = this.convertToImageBlock(referencePath);
                    if (imageBlock) {
                        blocks.push(imageBlock);
                    } else {
                        // 转换失败，作为文本处理
                        const fileName = path.basename(referencePath);
                        blocks.push(this.createTextBlock(`[图片: ${fileName}]`));
                    }
                } else {
                    // 不转换，作为文本处理
                    const fileName = path.basename(referencePath);
                    blocks.push(this.createTextBlock(`[图片: ${fileName}]`));
                }
            } else {
                // 普通文件引用，保留原样（Claude Code 会处理）
                blocks.push(this.createTextBlock(`@${referencePath}`));
            }

            lastIndex = match.index + match[0].length;
        }

        // 添加剩余文本
        if (lastIndex < text.length) {
            const textContent = text.substring(lastIndex);
            if (textContent.trim()) {
                blocks.push(this.createTextBlock(textContent));
            }
        }

        // 如果没有任何块，返回一个包含完整文本的块
        if (blocks.length === 0) {
            blocks.push(this.createTextBlock(text));
        }

        return blocks;
    }

    /**
     * 判断路径是否为图片文件
     */
    static isImagePath(filePath: string): boolean {
        const extension = this.getExtension(filePath).toLowerCase();
        return IMAGE_EXTENSIONS.has(extension);
    }

    /**
     * 将图片路径转换为 ImageBlock
     *
     * @param imagePath 图片文件路径（绝对路径或相对路径）
     * @returns ImageBlock 或 null（如果文件不存在或读取失败）
     */
    static convertToImageBlock(imagePath: string): ImageBlock | null {
        try {
            if (!fs.existsSync(imagePath)) {
                logger.warn(`Image file not found: ${imagePath}`);
                return null;
            }

            const stats = fs.statSync(imagePath);
            if (!stats.isFile()) {
                logger.warn(`Path is not a file: ${imagePath}`);
                return null;
            }

            // 读取文件内容
            const fileBytes = fs.readFileSync(imagePath);

            // 转换为 base64
            const base64Data = fileBytes.toString('base64');

            // 检测 MIME 类型
            const mimeType = this.detectMimeType(imagePath);

            logger.info(`Converted image to ImageBlock: ${path.basename(imagePath)} (${fileBytes.length} bytes)`);

            return {
                type: 'image',
                source: {
                    type: 'base64',
                    mediaType: mimeType,
                    data: base64Data
                }
            };
        } catch (e) {
            logger.error(`Failed to convert image to ImageBlock: ${imagePath}`, e instanceof Error ? e : undefined);
            return null;
        }
    }

    /**
     * 检测图片的 MIME 类型
     */
    static detectMimeType(filePath: string): string {
        const extension = this.getExtension(filePath).toLowerCase();
        return MIME_TYPES[extension] ?? 'image/png'; // 默认 PNG
    }

    /**
     * 获取文件扩展名（不含点号）
     */
    private static getExtension(filePath: string): string {
        const lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex === -1 || lastDotIndex === filePath.length - 1) {
            return '';
        }
        return filePath.substring(lastDotIndex + 1);
    }

    /**
     * 创建文本块
     */
    private static createTextBlock(text: string): TextBlock {
        return {
            type: 'text',
            text: text
        };
    }

    /**
     * 提取消息中的所有 @ 引用路径
     *
     * @param text 消息文本
     * @returns 引用路径列表
     */
    static extractReferences(text: string): string[] {
        const references: string[] = [];
        const pattern = new RegExp(this.AT_REFERENCE_PATTERN);
        let match: RegExpExecArray | null;

        while ((match = pattern.exec(text)) !== null) {
            references.push(match[1]);
        }

        return references;
    }

    /**
     * 分离图片引用和文件引用
     *
     * @param text 消息文本
     * @returns 分离后的引用
     */
    static separateReferences(text: string): { images: string[]; files: string[] } {
        const references = this.extractReferences(text);
        const images: string[] = [];
        const files: string[] = [];

        for (const ref of references) {
            if (this.isImagePath(ref)) {
                images.push(ref);
            } else {
                files.push(ref);
            }
        }

        return { images, files };
    }

    /**
     * 将 ContentBlock 列表转换回纯文本
     *
     * @param blocks ContentBlock 列表
     * @returns 纯文本
     */
    static blocksToText(blocks: ContentBlock[]): string {
        return blocks
            .map(block => {
                if (block.type === 'text') {
                    return block.text;
                } else if (block.type === 'image') {
                    return '[Image]';
                }
                return '';
            })
            .join('');
    }
}

// ==================== 辅助函数 ====================

/**
 * 解析消息内容
 */
export function parseMessageContent(text: string, convertImages?: boolean): ContentBlock[] {
    return MessageContentParser.parseMessageContent(text, convertImages);
}

/**
 * 判断是否为图片路径
 */
export function isImagePath(filePath: string): boolean {
    return MessageContentParser.isImagePath(filePath);
}

/**
 * 提取消息中的所有引用
 */
export function extractReferences(text: string): string[] {
    return MessageContentParser.extractReferences(text);
}

/**
 * 检测 MIME 类型
 */
export function detectMimeType(filePath: string): string {
    return MessageContentParser.detectMimeType(filePath);
}
