import type { ContentBlock, TextBlock, ImageBlock } from '@/types/message'
import type { ContextReference } from '@/types/display'
import type { ActiveFileInfo } from '@/services/jetbrainsRSocket'
import {
  hasCurrentOpenFileTag,
  parseCurrentOpenFileTag,
  removeCurrentOpenFileTag,
  parseSystemReminder,
  isSystemReminderTag,
  type ParsedCurrentOpenFile,
  type ParsedOpenFileReminder,
  type ParsedSelectLinesReminder
} from '@/utils/xmlTagParser'

/**
 * 解析用户消息的结果
 */
export interface ParsedUserMessage {
  contexts: ContextReference[]      // 上下文引用（文件引用）
  contextImages: ImageBlock[]       // Context 图片
  userContent: ContentBlock[]       // 用户输入内容（文本 + 图片）
  currentOpenFile?: ParsedCurrentOpenFile  // 当前打开的文件标记（兼容旧格式）
  openFile?: ParsedOpenFileReminder        // 打开的文件（新格式）
  selectedLines?: ParsedSelectLinesReminder // 选中的行（新格式）
}

/**
 * 解析用户消息，分离上下文引用和用户输入
 *
 * 支持两种格式：
 * 1. 新格式（从后往前）：用户内容 → open-file → select-lines → attachment（contexts）
 * 2. 旧格式（从前往后）：current-open-file → contexts → 用户内容
 *
 * @param content 消息内容块数组
 * @returns 解析后的结果
 */
export function parseUserMessage(content: ContentBlock[]): ParsedUserMessage {
  // 尝试新格式解析
  const newFormatResult = parseUserMessageNewFormat(content)
  if (newFormatResult) {
    return newFormatResult
  }

  // 回退到旧格式解析
  return parseUserMessageOldFormat(content)
}

/**
 * 新格式解析（从后往前）
 *
 * 结构：
 * 1. 用户内容（文本 + 图片）
 * 2. <system-reminder type="open-file" .../>
 * 3. <system-reminder type="select-lines" ...>...</system-reminder>
 * 4. <system-reminder type="attachment">
 * 5.   contexts...
 * 6. </system-reminder>
 */
function parseUserMessageNewFormat(content: ContentBlock[]): ParsedUserMessage | null {
  // 检测是否是新格式：从后往前找 </system-reminder> 结束标签
  let hasNewFormat = false
  for (let i = content.length - 1; i >= 0; i--) {
    const block = content[i]
    if (block.type === 'text' && 'text' in block) {
      const text = (block as TextBlock).text.trim()
      if (isSystemReminderTag(text)) {
        hasNewFormat = true
        break
      }
    }
  }

  if (!hasNewFormat) {
    return null
  }

  const contexts: ContextReference[] = []
  const contextImages: ImageBlock[] = []
  const userContent: ContentBlock[] = []
  let openFile: ParsedOpenFileReminder | undefined
  let selectedLines: ParsedSelectLinesReminder | undefined

  // 从后往前扫描
  let inAttachmentSection = false
  let i = content.length - 1

  while (i >= 0) {
    const block = content[i]

    // 跳过 tool_result / tool_use
    if (block.type === 'tool_result' || block.type === 'tool_use') {
      i--
      continue
    }

    if (block.type === 'text' && 'text' in block) {
      const text = (block as TextBlock).text.trim()

      // 解析 system-reminder 标签
      const reminder = parseSystemReminder(text)
      if (reminder) {
        switch (reminder.type) {
          case 'attachment':
            if (!reminder.isStart) {
              // </system-reminder> 结束标签，开始收集 attachment
              inAttachmentSection = true
            } else {
              // <system-reminder type="attachment"> 开始标签，结束收集
              inAttachmentSection = false
            }
            break

          case 'open-file':
            openFile = reminder
            break

          case 'select-lines':
            selectedLines = reminder
            break
        }
        i--
        continue
      }

      // 在 attachment 区域内，收集 contexts
      if (inAttachmentSection) {
        const fileRefMatch = text.match(/^@file:\/\/(.+)$/)
        if (fileRefMatch) {
          const filePath = fileRefMatch[1]
          // 插入到开头（因为是从后往前遍历）
          contexts.unshift({
            type: 'file',
            uri: `file://${filePath}`,
            path: filePath,
            displayType: 'TAG'
          })
        } else if (text) {
          // 其他文本也作为 context（比如 URL）
          contexts.unshift({
            type: 'web',
            uri: text,
            url: text,
            displayType: 'TAG'
          })
        }
        i--
        continue
      }

      // 不在特殊区域，是用户内容
      if (text) {
        userContent.unshift({ type: 'text', text } as TextBlock)
      }
    } else if (block.type === 'image') {
      const imageBlock = block as ImageBlock

      if (inAttachmentSection) {
        // attachment 区域内的图片是 context 图片
        contextImages.unshift(imageBlock)
      } else {
        // 否则是用户输入的图片
        userContent.unshift(block)
      }
    }

    i--
  }

  return {
    contexts,
    contextImages,
    userContent,
    openFile,
    selectedLines
  }
}

/**
 * 旧格式解析（从前往后）- 兼容历史消息
 *
 * 结构：
 * 1. <current-open-file .../>
 * 2. contexts（@file://... 或图片）
 * 3. 用户内容
 */
function parseUserMessageOldFormat(content: ContentBlock[]): ParsedUserMessage {
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
  /** 不再使用，open-file 由 ChatInput.vue 的 appendSystemReminders 处理 */
  activeFile?: {
    path: string
    line?: number
  }
}

/**
 * 构建用户消息内容
 *
 * 新的结构（与 Claude Code CLI 对齐）：
 * 1. 用户输入内容在前（文本 + 图片）
 * 2. contexts 在后，用 <system-reminder type="attachment"> 包裹
 *
 * 注意：open-file 和 select-lines 由 ChatInput.vue 的 appendSystemReminders 处理
 */
export function buildUserMessageContent(options: BuildUserMessageContentOptions): ContentBlock[] {
  const content: ContentBlock[] = []
  const contexts: ContextReference[] = [...(options.contexts ?? [])]

  // 1. 首先添加用户输入的文本（在前面）
  if (options.text.trim()) {
    content.push({ type: 'text', text: options.text.trim() } as TextBlock)
  }

  // 2. 然后添加 contexts（在后面），用 <system-reminder type="attachment"> 包裹
  if (contexts.length > 0) {
    // 开始标签
    content.push({ type: 'text', text: '<system-reminder type="attachment">' } as TextBlock)

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

    // 结束标签
    content.push({ type: 'text', text: '</system-reminder>' } as TextBlock)
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

/**
 * XML 转义辅助函数
 */
function escapeXml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 将 IDE 上下文（ActiveFileInfo）转换为 XML ContentBlock 数组
 *
 * 新格式（与 Claude Code CLI 对齐）：
 * - <system-reminder type="open-file" path="xxx"/>
 * - <system-reminder type="select-lines" path="xxx" start="N" end="M">选中内容</system-reminder>
 *
 * @param ideContext IDE 上下文信息
 * @returns ContentBlock 数组，追加到消息末尾发送给后端
 */
export function ideContextToContentBlocks(ideContext: ActiveFileInfo | null | undefined): ContentBlock[] {
  if (!ideContext) {
    return []
  }

  const file = ideContext
  const fileType = file.fileType || 'text'
  const reminders: ContentBlock[] = []

  // 1. 生成 open-file 标签
  let openFileTag = `<system-reminder type="open-file" path="${file.relativePath}"`
  if (fileType !== 'text') {
    openFileTag += ` file-type="${fileType}"`
  }
  if (fileType === 'diff' && file.diffTitle) {
    openFileTag += ` diff-title="${escapeXml(file.diffTitle)}"`
  }
  openFileTag += '/>'

  reminders.push({
    type: 'text',
    text: openFileTag
  } as TextBlock)

  // 2. 如果有选区，生成 select-lines 标签
  if (file.hasSelection && file.startLine && file.endLine) {
    let selectLinesTag = `<system-reminder type="select-lines" path="${file.relativePath}" start="${file.startLine}" end="${file.endLine}"`
    if (file.startColumn) {
      selectLinesTag += ` start-column="${file.startColumn}"`
    }
    if (file.endColumn) {
      selectLinesTag += ` end-column="${file.endColumn}"`
    }

    if (file.selectedContent) {
      // 有选中内容，使用非自闭合标签
      selectLinesTag += `>${file.selectedContent}</system-reminder>`
    } else {
      // 无选中内容，使用自闭合标签
      selectLinesTag += '/>'
    }

    reminders.push({
      type: 'text',
      text: selectLinesTag
    } as TextBlock)
  }

  // 3. 处理 Diff 内容（作为额外的 ContentBlock）
  if (fileType === 'diff') {
    if (file.diffOldContent !== undefined && file.diffOldContent !== null) {
      reminders.push({
        type: 'text',
        text: `<diff-old-content><![CDATA[${file.diffOldContent}]]></diff-old-content>`
      } as TextBlock)
    }
    if (file.diffNewContent !== undefined && file.diffNewContent !== null) {
      reminders.push({
        type: 'text',
        text: `<diff-new-content><![CDATA[${file.diffNewContent}]]></diff-new-content>`
      } as TextBlock)
    }
  }

  return reminders
}
