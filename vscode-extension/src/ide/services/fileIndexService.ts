/**
 * 文件索引服务接口和类型定义
 *
 * 提供文件搜索和索引功能
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/FileIndexService.kt
 */

/**
 * 符号类型枚举
 */
export enum SymbolType {
    CLASS = 'CLASS',
    INTERFACE = 'INTERFACE',
    FUNCTION = 'FUNCTION',
    METHOD = 'METHOD',
    PROPERTY = 'PROPERTY',
    FIELD = 'FIELD',
    VARIABLE = 'VARIABLE',
    ENUM = 'ENUM',
    ENUM_VALUE = 'ENUM_VALUE',
    PACKAGE = 'PACKAGE',
    MODULE = 'MODULE',
    FILE = 'FILE',
}

/**
 * 索引文件信息
 */
export interface IndexedFileInfo {
    name: string;
    relativePath: string;
    absolutePath: string;
    fileType: string;
    size: number;
    lastModified: number;
    isDirectory: boolean;
    language?: string;
    encoding?: string;
}

/**
 * 索引符号信息
 */
export interface IndexedSymbolInfo {
    name: string;
    type: SymbolType;
    filePath: string;
    lineNumber?: number;
    columnNumber?: number;
    signature?: string;
}

/**
 * 索引统计信息
 */
export interface IndexStats {
    totalFiles: number;
    indexedFiles: number;
    totalSymbols: number;
    lastIndexTime: number;
    indexSizeBytes: number;
    supportedFileTypes: string[];
}

/**
 * 索引正在进行中异常
 */
export class IndexingInProgressError extends Error {
    constructor(message: string = 'Project is indexing') {
        super(message);
        this.name = 'IndexingInProgressError';
    }
}

/**
 * 文件索引服务接口
 * 提供文件搜索和索引功能
 */
export interface FileIndexService {
    /**
     * 初始化索引
     */
    initialize(rootPath: string): Promise<void>;

    /**
     * 索引指定路径
     */
    indexPath(path: string, recursive: boolean): Promise<void>;

    /**
     * 搜索文件
     */
    searchFiles(
        query: string,
        maxResults: number,
        fileTypes?: string[]
    ): Promise<IndexedFileInfo[]>;

    /**
     * 按文件名查找文件
     */
    findFilesByName(fileName: string, maxResults: number): Promise<IndexedFileInfo[]>;

    /**
     * 搜索符号
     */
    searchSymbols(
        query: string,
        symbolTypes: SymbolType[],
        maxResults: number
    ): Promise<IndexedSymbolInfo[]>;

    /**
     * 获取最近文件
     */
    getRecentFiles(maxResults: number): Promise<IndexedFileInfo[]>;

    /**
     * 获取最近修改的文件
     */
    getRecentlyModifiedFiles(projectPath: string, limit: number): Promise<IndexedFileInfo[]>;

    /**
     * 获取文件内容
     */
    getFileContent(filePath: string): Promise<string | null>;

    /**
     * 获取文件符号
     */
    getFileSymbols(filePath: string): Promise<IndexedSymbolInfo[]>;

    /**
     * 检查索引是否就绪
     */
    isIndexReady(): boolean;

    /**
     * 获取索引统计信息
     */
    getIndexStats(): Promise<IndexStats>;

    /**
     * 刷新索引
     */
    refreshIndex(): Promise<void>;

    /**
     * 清理索引
     */
    cleanup(): Promise<void>;
}
