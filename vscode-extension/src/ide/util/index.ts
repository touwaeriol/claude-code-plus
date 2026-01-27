/**
 * IDE Util Module - 统一导出
 *
 * 包含以下工具：
 * - PathResolver: 项目路径解析
 * - VirtualFileResolver: 虚拟文件解析（支持多种路径格式）
 * - ResourceLoader: 扩展资源文件加载
 * - MessageContentParser: 消息内容解析（@ 引用、图片识别）
 */

// 路径解析器
export {
    PathResolver,
    toAbsolutePath,
    toRelativePath
} from './pathResolver';

// 虚拟文件解析器
export {
    VirtualFileResolver,
    resolveFile,
    fileExists,
    readFileContent,
    type ResolvedFile
} from './virtualFileResolver';

// 资源加载器
export {
    ResourceLoader,
    loadTextResource,
    loadTextResourceOrDefault,
    loadJsonResource,
    type AgentDefinition,
    type AgentJsonDefinition,
    type AgentsConfig
} from './resourceLoader';

// 消息内容解析器
export {
    MessageContentParser,
    parseMessageContent,
    isImagePath,
    extractReferences,
    detectMimeType,
    type ContentBlock,
    type TextBlock,
    type ImageBlock,
    type ImageSource
} from './messageContentParser';
