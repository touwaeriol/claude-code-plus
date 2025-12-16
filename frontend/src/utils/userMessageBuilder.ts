import type { ContentBlock, TextBlock, ImageBlock } from '@/types/message'
import type { ContextReference } from '@/types/display'
import { hasCurrentOpenFileTag, parseCurrentOpenFileTag, removeCurrentOpenFileTag, type ParsedCurrentOpenFile } from '@/utils/xmlTagParser'

/**
 * 解析用户消息的结果
 */
export interface ParsedUserMessage {
  contexts: ContextReference[]      // 上下文引用（文件引用）
  contextImages: ImageBlock[]       // Context 图片
  userContent: ContentBlock[]       // 用户输入内容（文本 + 图片）
  currentOpenFile?: ParsedCurrentOpenFile  // 当前打开的文件标记
}

/**
 * 解析用户消息，分离上下文引用和用户输入
 *
 * @param content 消息内容块数组
 * @returns 解析后的结果
 */
export function parseUserMessage(content: ContentBlock[]): ParsedUserMessage {
  const contexts: ContextReference[] = []
  const contextImages: ImageBlock[] = []
  const userContent: ContentBlock[] = []
  let currentOpenFile: ParsedCurrentOpenFile | undefined = undefined

  let foundFirstUserText = false

  for (const block of content) {
    // tool_result / tool_use 等后端流控块不应该显示为普通用户消息
    if (block.type === 'tool_result' || block.type === 'tool_use') {
      continue
    }

    if (block.type === 'text') {
      const textBlock = block as TextBlock
      let text = textBlock.text

      // 检查并解析 <current-open-file> 标签
      if (hasCurrentOpenFileTag(text)) {
        const parsed = parseCurrentOpenFileTag(text)
        if (parsed) {
          currentOpenFile = parsed
        }
        // 移除标签
        text = removeCurrentOpenFileTag(text)
      }

      // 如果移除标签后没有内容，跳过
      if (!text.trim()) {
        continue
      }

      // 检查是否是文件引用格式（例如：@file:///path/to/file.ts）
      const fileRefMatch = text.match(/^@file:\/\/(.+)$/)
      if (fileRefMatch && !foundFirstUserText) {
        // 提取文件路径（去除 file:// 前缀后的部分）
        const filePath = fileRefMatch[1]
        contexts.push({
          type: 'file',
          uri: `file://${filePath}`,
          path: filePath,
          displayType: 'TAG'
        })
      } else {
        // 第一个普通文本块之后的所有内容都是用户输入
        foundFirstUserText = true
        userContent.push({ type: 'text', text } as TextBlock)
      }
    } else if (block.type === 'image') {
      const imageBlock = block as ImageBlock

      // 如果还没遇到用户文本，认为是 Context 图片
      if (!foundFirstUserText) {
        contextImages.push(imageBlock)
      } else {
        // 否则是用户输入的图片
        userContent.push(block)
      }
    } else {
      // 其他类型（例如 todo_list）暂不显示在用户消息中
      // 只保留文本/图片，避免将工具参数渲染为普通消息
    }
  }

  return {
    contexts,
    contextImages,
    userContent,
    currentOpenFile
  }
}

export interface BuildUserMessageContentOptions {
  text: string
  contexts?: ContextReference[]
  activeFile?: {
    path: string
    line?: number
  }
}

export function buildUserMessageContent(options: BuildUserMessageContentOptions): ContentBlock[] {
  const content: ContentBlock[] = []
  const contexts: ContextReference[] = [...(options.contexts ?? [])]

  if (options.activeFile?.path) {
    const filePath = options.activeFile.line
      ? `${options.activeFile.path}:${options.activeFile.line}`
      : options.activeFile.path
    contexts.unshift({
      type: 'file',
      uri: `file://${filePath}`,
      displayType: 'TAG'
    })
  }

  for (const context of contexts) {
    if (context.type === 'file') {
      const rawPath = context.fullPath || context.path || context.uri?.replace(/^file:\/\//, '')
      if (rawPath) {
        const fileUri = rawPath.startsWith('file://') ? rawPath : `file://${rawPath}`
        content.push({ type: 'text', text: `@${fileUri}` } as TextBlock)
      }
    } else if (context.type === 'image') {
      const mediaType = context.mimeType || 'image/png'
      if (context.base64Data) {
        content.push({
          type: 'image',
          source: {
            type: 'base64',
            media_type: mediaType,
            data: context.base64Data
          }
        } as ImageBlock)
      } else if (context.uri) {
        const url = context.uri.startsWith('image://') ? context.uri.slice('image://'.length) : context.uri
        content.push({
          type: 'image',
          source: {
            type: 'url',
            media_type: mediaType,
            url
          }
        } as ImageBlock)
      }
    } else if (context.type === 'web' && (context.url || context.uri)) {
      const url = context.url || context.uri
      if (url) {
        content.push({ type: 'text', text: url } as TextBlock)
      }
    }
  }

  if (options.text.trim()) {
    content.push({ type: 'text', text: options.text.trim() } as TextBlock)
  }

  return content
}

export async function fileToImageBlock(file: File): Promise<ImageBlock> {
  const base64Data = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result
      if (typeof result === 'string') {
        resolve(result.split(',')[1] || '')
      } else {
        reject(new Error('Unexpected file reader result'))
      }
    }
    reader.onerror = () => reject(reader.error || new Error('Failed to read file'))
    reader.readAsDataURL(file)
  })

  return {
    type: 'image',
    source: {
      type: 'base64',
      media_type: file.type || 'image/png',
      data: base64Data
    }
  }
}

/**
 * 检查文本是否是文件引用格式
 *
 * @param text 文本内容
 * @returns 是否是文件引用
 */
export function isFileReference(text: string): boolean {
  return /^@file:\/\/.+$/.test(text)
}
