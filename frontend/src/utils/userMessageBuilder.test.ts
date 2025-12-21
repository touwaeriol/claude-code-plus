import { describe, it, expect } from 'vitest'
import { parseUserMessage, buildUserMessageContent, isFileReference } from './userMessageBuilder'
import type { ContentBlock, TextBlock, ImageBlock } from '@/types/message'

describe('parseUserMessage', () => {
  describe('新格式解析（从后往前）', () => {
    it('应该正确解析只有用户文本的消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: 'Hello, world!' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('Hello, world!')
      expect(result.contexts).toHaveLength(0)
      expect(result.openFile).toBeUndefined()
      expect(result.selectedLines).toBeUndefined()
    })

    it('应该正确解析带 open-file 的新格式消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '请帮我分析这段代码' } as TextBlock,
        { type: 'text', text: '<system-reminder type="open-file" path="src/app.ts"/>' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('请帮我分析这段代码')
      expect(result.openFile).toEqual({
        type: 'open-file',
        path: 'src/app.ts',
        fileType: undefined,
        diffTitle: undefined
      })
    })

    it('应该正确解析带 select-lines 的新格式消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '优化这段代码' } as TextBlock,
        { type: 'text', text: '<system-reminder type="open-file" path="src/utils.ts"/>' } as TextBlock,
        { type: 'text', text: '<system-reminder type="select-lines" path="src/utils.ts" start="10" end="20">function foo() {}</system-reminder>' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect(result.openFile?.path).toBe('src/utils.ts')
      expect(result.selectedLines).toBeDefined()
      expect(result.selectedLines?.start).toBe(10)
      expect(result.selectedLines?.end).toBe(20)
      expect(result.selectedLines?.content).toContain('function foo()')
    })

    it('应该正确解析带 attachment 的新格式消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '比较这两个文件' } as TextBlock,
        { type: 'text', text: '<system-reminder type="open-file" path="src/main.ts"/>' } as TextBlock,
        { type: 'text', text: '<system-reminder type="attachment-start"/>' } as TextBlock,
        { type: 'text', text: '@file://src/file1.ts' } as TextBlock,
        { type: 'text', text: '@file://src/file2.ts' } as TextBlock,
        { type: 'text', text: '<system-reminder type="attachment-end"/>' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('比较这两个文件')
      expect(result.contexts).toHaveLength(2)
      expect(result.contexts[0].path).toBe('src/file1.ts')
      expect(result.contexts[1].path).toBe('src/file2.ts')
      expect(result.openFile?.path).toBe('src/main.ts')
    })

    it('应该正确解析带图片上下文的新格式消息', () => {
      const imageBlock: ImageBlock = {
        type: 'image',
        source: {
          type: 'base64',
          media_type: 'image/png',
          data: 'iVBORw0KGgo...'
        }
      }

      const content: ContentBlock[] = [
        { type: 'text', text: '分析这张图片' } as TextBlock,
        { type: 'text', text: '<system-reminder type="attachment-start"/>' } as TextBlock,
        imageBlock,
        { type: 'text', text: '<system-reminder type="attachment-end"/>' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect(result.contextImages).toHaveLength(1)
      expect(result.contextImages[0].source.type).toBe('base64')
    })

    it('应该正确处理完整的新格式消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '帮我重构代码' } as TextBlock,
        { type: 'image', source: { type: 'base64', media_type: 'image/png', data: 'xxx' } } as ImageBlock,
        { type: 'text', text: '<system-reminder type="open-file" path="src/app.ts"/>' } as TextBlock,
        { type: 'text', text: '<system-reminder type="select-lines" path="src/app.ts" start="1" end="10"/>' } as TextBlock,
        { type: 'text', text: '<system-reminder type="attachment-start"/>' } as TextBlock,
        { type: 'text', text: '@file://src/utils.ts' } as TextBlock,
        { type: 'text', text: '<system-reminder type="attachment-end"/>' } as TextBlock
      ]
      const result = parseUserMessage(content)

      // 用户内容：文本 + 图片
      expect(result.userContent).toHaveLength(2)
      expect((result.userContent[0] as TextBlock).text).toBe('帮我重构代码')
      expect(result.userContent[1].type).toBe('image')

      // open-file
      expect(result.openFile?.path).toBe('src/app.ts')

      // select-lines
      expect(result.selectedLines?.start).toBe(1)
      expect(result.selectedLines?.end).toBe(10)

      // contexts
      expect(result.contexts).toHaveLength(1)
      expect(result.contexts[0].path).toBe('src/utils.ts')
    })
  })

  describe('旧格式解析（从前往后）- 兼容性', () => {
    it('应该正确解析旧格式的 current-open-file 消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '<current-open-file path="src/old.ts" line="5" column="10"/>' } as TextBlock,
        { type: 'text', text: '这是用户输入' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.currentOpenFile).toBeDefined()
      expect(result.currentOpenFile?.path).toBe('src/old.ts')
      expect(result.currentOpenFile?.line).toBe(5)
      expect(result.currentOpenFile?.column).toBe(10)
      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('这是用户输入')
    })

    it('应该正确解析旧格式的 contexts 在前的消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '@file://src/context1.ts' } as TextBlock,
        { type: 'text', text: '@file://src/context2.ts' } as TextBlock,
        { type: 'text', text: '请分析这些文件' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.contexts).toHaveLength(2)
      expect(result.contexts[0].path).toBe('src/context1.ts')
      expect(result.contexts[1].path).toBe('src/context2.ts')
      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('请分析这些文件')
    })

    it('应该正确解析旧格式的完整消息', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '<current-open-file path="src/main.ts"/>' } as TextBlock,
        { type: 'text', text: '@file://src/utils.ts' } as TextBlock,
        { type: 'image', source: { type: 'base64', media_type: 'image/png', data: 'xxx' } } as ImageBlock,
        { type: 'text', text: '帮我优化代码' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.currentOpenFile?.path).toBe('src/main.ts')
      expect(result.contexts).toHaveLength(1)
      expect(result.contextImages).toHaveLength(1)
      expect(result.userContent).toHaveLength(1)
    })
  })

  describe('边界情况', () => {
    it('应该处理空数组', () => {
      const result = parseUserMessage([])
      expect(result.userContent).toHaveLength(0)
      expect(result.contexts).toHaveLength(0)
      expect(result.contextImages).toHaveLength(0)
    })

    it('应该跳过 tool_result 和 tool_use 类型', () => {
      const content: ContentBlock[] = [
        { type: 'tool_result', tool_use_id: '123', content: [] } as any,
        { type: 'text', text: '用户输入' } as TextBlock,
        { type: 'tool_use', id: '456', name: 'test', input: {} } as any
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('用户输入')
    })

    it('应该处理空白文本块', () => {
      const content: ContentBlock[] = [
        { type: 'text', text: '   ' } as TextBlock,
        { type: 'text', text: '实际内容' } as TextBlock
      ]
      const result = parseUserMessage(content)

      expect(result.userContent).toHaveLength(1)
      expect((result.userContent[0] as TextBlock).text).toBe('实际内容')
    })
  })
})

describe('buildUserMessageContent', () => {
  it('应该正确构建只有文本的消息', () => {
    const result = buildUserMessageContent({
      text: 'Hello, world!'
    })

    expect(result).toHaveLength(1)
    expect((result[0] as TextBlock).text).toBe('Hello, world!')
  })

  it('应该正确构建带 contexts 的消息（新格式）', () => {
    const result = buildUserMessageContent({
      text: '分析这些文件',
      contexts: [
        { type: 'file', uri: 'file://src/app.ts', path: 'src/app.ts', displayType: 'TAG' },
        { type: 'file', uri: 'file://src/utils.ts', path: 'src/utils.ts', displayType: 'TAG' }
      ]
    })

    // 结构应该是：用户文本 + attachment-start + contexts + attachment-end
    expect(result.length).toBeGreaterThan(1)

    // 第一个是用户文本
    expect((result[0] as TextBlock).text).toBe('分析这些文件')

    // 应该有 attachment-start 标签
    const startTagIndex = result.findIndex(b =>
      b.type === 'text' && (b as TextBlock).text === '<system-reminder type="attachment-start"/>'
    )
    expect(startTagIndex).toBeGreaterThan(0)

    // 应该有 attachment-end 标签
    const endTagIndex = result.findIndex(b =>
      b.type === 'text' && (b as TextBlock).text === '<system-reminder type="attachment-end"/>'
    )
    expect(endTagIndex).toBeGreaterThan(startTagIndex)

    // contexts 应该在开始和结束标签之间
    const contextBlocks = result.filter(b =>
      b.type === 'text' && (b as TextBlock).text.startsWith('@file://')
    )
    expect(contextBlocks).toHaveLength(2)
  })

  it('应该正确构建带图片 context 的消息', () => {
    const result = buildUserMessageContent({
      text: '分析图片',
      contexts: [
        {
          type: 'image',
          uri: 'image://test',
          displayType: 'TAG',
          mimeType: 'image/png',
          base64Data: 'iVBORw0KGgo...'
        }
      ]
    })

    // 应该有图片块在 attachment 区域内
    const imageBlocks = result.filter(b => b.type === 'image')
    expect(imageBlocks).toHaveLength(1)
  })

  it('应该处理空 contexts', () => {
    const result = buildUserMessageContent({
      text: '只有文本',
      contexts: []
    })

    // 没有 contexts 时不应该有 attachment 标签
    expect(result).toHaveLength(1)
    expect((result[0] as TextBlock).text).toBe('只有文本')
  })

  it('应该处理空文本', () => {
    const result = buildUserMessageContent({
      text: '   ',
      contexts: [
        { type: 'file', uri: 'file://test.ts', path: 'test.ts', displayType: 'TAG' }
      ]
    })

    // 空文本不应该创建文本块
    const textBlocks = result.filter(b =>
      b.type === 'text' && !(b as TextBlock).text.startsWith('<') && !(b as TextBlock).text.startsWith('@')
    )
    expect(textBlocks).toHaveLength(0)
  })
})

describe('isFileReference', () => {
  it('应该识别文件引用格式', () => {
    expect(isFileReference('@file://src/app.ts')).toBe(true)
    expect(isFileReference('@file:///absolute/path/file.ts')).toBe(true)
    expect(isFileReference('@file://C:/Users/test/file.ts')).toBe(true)
  })

  it('应该拒绝非文件引用格式', () => {
    expect(isFileReference('file://src/app.ts')).toBe(false)
    expect(isFileReference('@src/app.ts')).toBe(false)
    expect(isFileReference('hello world')).toBe(false)
    expect(isFileReference('<system-reminder type="open-file" path="test.ts"/>')).toBe(false)
  })
})

describe('新旧格式互操作', () => {
  it('新格式消息应该能被正确解析', () => {
    // 模拟 buildUserMessageContent 生成的消息
    const built = buildUserMessageContent({
      text: '测试消息',
      contexts: [
        { type: 'file', uri: 'file://test.ts', path: 'test.ts', displayType: 'TAG' }
      ]
    })

    // 解析应该能还原
    const parsed = parseUserMessage(built)
    expect(parsed.userContent).toHaveLength(1)
    expect((parsed.userContent[0] as TextBlock).text).toBe('测试消息')
    expect(parsed.contexts).toHaveLength(1)
    expect(parsed.contexts[0].path).toBe('test.ts')
  })

  it('旧格式消息仍然能被正确解析', () => {
    // 模拟旧格式消息
    const oldFormat: ContentBlock[] = [
      { type: 'text', text: '<current-open-file path="old.ts"/>' } as TextBlock,
      { type: 'text', text: '@file://context.ts' } as TextBlock,
      { type: 'text', text: '旧格式消息' } as TextBlock
    ]

    const parsed = parseUserMessage(oldFormat)
    expect(parsed.currentOpenFile?.path).toBe('old.ts')
    expect(parsed.contexts).toHaveLength(1)
    expect(parsed.userContent).toHaveLength(1)
    expect((parsed.userContent[0] as TextBlock).text).toBe('旧格式消息')
  })
})
