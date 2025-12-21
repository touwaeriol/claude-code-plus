import { describe, it, expect } from 'vitest'
import {
  parseXmlTag,
  parseSystemReminder,
  isSystemReminderTag,
  isAttachmentStartTag,
  isAttachmentEndTag,
  escapeXml,
  parseCurrentOpenFileTag,
  hasCurrentOpenFileTag
} from './xmlTagParser'

describe('parseXmlTag', () => {
  describe('结束标签解析', () => {
    it('应该正确解析 </system-reminder>', () => {
      const result = parseXmlTag('</system-reminder>')
      expect(result).toEqual({
        tagName: 'system-reminder',
        attributes: {},
        isEndTag: true,
        isSelfClosing: false
      })
    })

    it('应该正确解析带空格的结束标签', () => {
      const result = parseXmlTag('  </system-reminder>  ')
      expect(result).toEqual({
        tagName: 'system-reminder',
        attributes: {},
        isEndTag: true,
        isSelfClosing: false
      })
    })
  })

  describe('自闭合标签解析', () => {
    it('应该正确解析 <system-reminder type="open-file" path="xxx"/>', () => {
      const result = parseXmlTag('<system-reminder type="open-file" path="src/app.ts"/>')
      expect(result).not.toBeNull()
      expect(result?.tagName).toBe('system-reminder')
      expect(result?.attributes['type']).toBe('open-file')
      expect(result?.attributes['path']).toBe('src/app.ts')
      expect(result?.isSelfClosing).toBe(true)
      expect(result?.isEndTag).toBe(false)
    })

    it('应该正确解析带多个属性的自闭合标签', () => {
      const result = parseXmlTag('<system-reminder type="select-lines" path="test.ts" start="10" end="20"/>')
      expect(result).not.toBeNull()
      expect(result?.attributes['type']).toBe('select-lines')
      expect(result?.attributes['path']).toBe('test.ts')
      expect(result?.attributes['start']).toBe('10')
      expect(result?.attributes['end']).toBe('20')
    })
  })

  describe('开始标签解析', () => {
    it('应该正确解析 <system-reminder type="attachment">', () => {
      const result = parseXmlTag('<system-reminder type="attachment">')
      expect(result).not.toBeNull()
      expect(result?.tagName).toBe('system-reminder')
      expect(result?.attributes['type']).toBe('attachment')
      expect(result?.isSelfClosing).toBe(false)
      expect(result?.isEndTag).toBe(false)
    })
  })

  describe('带内容的标签解析', () => {
    it('应该正确解析带内容的标签', () => {
      const result = parseXmlTag('<system-reminder type="select-lines" path="test.ts" start="1" end="5">选中的代码内容</system-reminder>')
      expect(result).not.toBeNull()
      expect(result?.tagName).toBe('system-reminder')
      expect(result?.textContent).toBe('选中的代码内容')
    })
  })

  describe('非 XML 文本', () => {
    it('应该对普通文本返回 null', () => {
      expect(parseXmlTag('hello world')).toBeNull()
      expect(parseXmlTag('@file://path/to/file.ts')).toBeNull()
      expect(parseXmlTag('')).toBeNull()
    })
  })
})

describe('parseSystemReminder', () => {
  describe('open-file 类型', () => {
    it('应该正确解析 open-file 标签', () => {
      const result = parseSystemReminder('<system-reminder type="open-file" path="src/app.ts"/>')
      expect(result).toEqual({
        type: 'open-file',
        path: 'src/app.ts',
        fileType: undefined,
        diffTitle: undefined
      })
    })

    it('应该正确解析带 file-type 的 open-file 标签', () => {
      const result = parseSystemReminder('<system-reminder type="open-file" path="image.png" file-type="image"/>')
      expect(result).toEqual({
        type: 'open-file',
        path: 'image.png',
        fileType: 'image',
        diffTitle: undefined
      })
    })

    it('应该正确解析带 diff-title 的 open-file 标签', () => {
      const result = parseSystemReminder('<system-reminder type="open-file" path="file.ts" file-type="diff" diff-title="My Diff"/>')
      expect(result).toEqual({
        type: 'open-file',
        path: 'file.ts',
        fileType: 'diff',
        diffTitle: 'My Diff'
      })
    })
  })

  describe('select-lines 类型', () => {
    it('应该正确解析 select-lines 标签（自闭合）', () => {
      const result = parseSystemReminder('<system-reminder type="select-lines" path="test.ts" start="10" end="20"/>')
      expect(result).toEqual({
        type: 'select-lines',
        path: 'test.ts',
        start: 10,
        end: 20,
        startColumn: undefined,
        endColumn: undefined,
        content: undefined
      })
    })

    it('应该正确解析带列信息的 select-lines 标签', () => {
      const result = parseSystemReminder('<system-reminder type="select-lines" path="test.ts" start="10" end="20" start-column="5" end-column="30"/>')
      expect(result).toEqual({
        type: 'select-lines',
        path: 'test.ts',
        start: 10,
        end: 20,
        startColumn: 5,
        endColumn: 30,
        content: undefined
      })
    })

    it('应该正确解析带内容的 select-lines 标签', () => {
      const result = parseSystemReminder('<system-reminder type="select-lines" path="test.ts" start="1" end="3">function foo() {\n  return 42\n}</system-reminder>')
      expect(result).not.toBeNull()
      expect(result?.type).toBe('select-lines')
      if (result?.type === 'select-lines') {
        expect(result.path).toBe('test.ts')
        expect(result.start).toBe(1)
        expect(result.end).toBe(3)
        expect(result.content).toContain('function foo()')
      }
    })

    it('应该正确解析带 XML 转义内容的 select-lines 标签', () => {
      // 内容在构建时已经被 XML 转义，DOMParser 可以正确解析
      // 原始内容: <a href="README.md">English</a>
      // 转义后: &lt;a href="README.md"&gt;English&lt;/a&gt;
      const escapedContent = `<system-reminder type="select-lines" path="README.md" start="19" end="27" start-column="32" end-column="24">&lt;a href="README.md"&gt;English&lt;/a&gt; |
&lt;a href="README_zh-CN.md"&gt;简体中文&lt;/a&gt;</system-reminder>`
      const result = parseSystemReminder(escapedContent)
      expect(result).not.toBeNull()
      expect(result?.type).toBe('select-lines')
      if (result?.type === 'select-lines') {
        expect(result.path).toBe('README.md')
        expect(result.start).toBe(19)
        expect(result.end).toBe(27)
        expect(result.startColumn).toBe(32)
        expect(result.endColumn).toBe(24)
        // DOMParser 的 textContent 会自动反转义
        expect(result.content).toContain('<a href="README.md">English</a>')
      }
    })
  })

  describe('attachment-start/end 类型', () => {
    it('应该正确解析 attachment-start 标签', () => {
      const result = parseSystemReminder('<system-reminder type="attachment-start"/>')
      expect(result).toEqual({
        type: 'attachment-start'
      })
    })

    it('应该正确解析 attachment-end 标签', () => {
      const result = parseSystemReminder('<system-reminder type="attachment-end"/>')
      expect(result).toEqual({
        type: 'attachment-end'
      })
    })

    it('应该对 </system-reminder> 返回 null', () => {
      // 结束标签不再作为 attachment 标记
      const result = parseSystemReminder('</system-reminder>')
      expect(result).toBeNull()
    })
  })

  describe('无效输入', () => {
    it('应该对非 system-reminder 标签返回 null', () => {
      expect(parseSystemReminder('<div>hello</div>')).toBeNull()
      expect(parseSystemReminder('<current-open-file path="test.ts"/>')).toBeNull()
      expect(parseSystemReminder('hello world')).toBeNull()
    })

    it('应该对缺少必要属性的标签返回 null', () => {
      // open-file 缺少 path
      expect(parseSystemReminder('<system-reminder type="open-file"/>')).toBeNull()
      // select-lines 缺少 start/end
      expect(parseSystemReminder('<system-reminder type="select-lines" path="test.ts"/>')).toBeNull()
    })
  })
})

describe('isSystemReminderTag', () => {
  it('应该识别 system-reminder 标签', () => {
    expect(isSystemReminderTag('<system-reminder type="open-file" path="test.ts"/>')).toBe(true)
    expect(isSystemReminderTag('<system-reminder type="attachment-start"/>')).toBe(true)
    expect(isSystemReminderTag('<system-reminder type="attachment-end"/>')).toBe(true)
    expect(isSystemReminderTag('</system-reminder>')).toBe(true)
  })

  it('应该拒绝非 system-reminder 标签', () => {
    expect(isSystemReminderTag('<current-open-file path="test.ts"/>')).toBe(false)
    expect(isSystemReminderTag('@file://test.ts')).toBe(false)
    expect(isSystemReminderTag('hello world')).toBe(false)
  })

  it('应该识别带 XML 转义内容的 select-lines 标签', () => {
    // 内容在构建时已经被 XML 转义，DOMParser 可以正确解析
    const escapedContent = `<system-reminder type="select-lines" path="README.md" start="19" end="27">&lt;a href="README.md"&gt;English&lt;/a&gt;</system-reminder>`
    expect(isSystemReminderTag(escapedContent)).toBe(true)
  })
})

describe('isAttachmentStartTag', () => {
  it('应该识别 attachment-start 标签', () => {
    expect(isAttachmentStartTag('<system-reminder type="attachment-start"/>')).toBe(true)
  })

  it('应该拒绝其他标签', () => {
    expect(isAttachmentStartTag('</system-reminder>')).toBe(false)
    expect(isAttachmentStartTag('<system-reminder type="open-file" path="test.ts"/>')).toBe(false)
    expect(isAttachmentStartTag('<system-reminder type="attachment-end"/>')).toBe(false)
  })
})

describe('isAttachmentEndTag', () => {
  it('应该识别 attachment-end 标签', () => {
    expect(isAttachmentEndTag('<system-reminder type="attachment-end"/>')).toBe(true)
  })

  it('应该拒绝其他标签', () => {
    expect(isAttachmentEndTag('<system-reminder type="attachment-start"/>')).toBe(false)
    expect(isAttachmentEndTag('<system-reminder type="open-file" path="test.ts"/>')).toBe(false)
    expect(isAttachmentEndTag('</system-reminder>')).toBe(false)
  })
})

describe('escapeXml', () => {
  it('应该正确转义 XML 特殊字符', () => {
    expect(escapeXml('hello & world')).toBe('hello &amp; world')
    expect(escapeXml('<tag>')).toBe('&lt;tag&gt;')
    expect(escapeXml('"quoted"')).toBe('&quot;quoted&quot;')
    expect(escapeXml('a & b < c > d "e"')).toBe('a &amp; b &lt; c &gt; d &quot;e&quot;')
  })

  it('应该保持普通文本不变', () => {
    expect(escapeXml('hello world')).toBe('hello world')
    expect(escapeXml('123')).toBe('123')
  })
})

describe('旧格式兼容性 - parseCurrentOpenFileTag', () => {
  it('应该正确解析旧格式的 current-open-file 标签', () => {
    const result = parseCurrentOpenFileTag('<current-open-file path="src/app.ts"/>')
    expect(result).toEqual({
      path: 'src/app.ts'
    })
  })

  it('应该正确解析带行列信息的标签', () => {
    const result = parseCurrentOpenFileTag('<current-open-file path="src/app.ts" line="10" column="5"/>')
    expect(result).toEqual({
      path: 'src/app.ts',
      line: 10,
      column: 5
    })
  })

  it('应该正确解析带选区信息的标签', () => {
    const result = parseCurrentOpenFileTag('<current-open-file path="src/app.ts" start-line="10" start-column="5" end-line="20" end-column="30"/>')
    expect(result).not.toBeNull()
    expect(result?.path).toBe('src/app.ts')
    expect(result?.startLine).toBe(10)
    expect(result?.startColumn).toBe(5)
    expect(result?.endLine).toBe(20)
    expect(result?.endColumn).toBe(30)
  })
})

describe('hasCurrentOpenFileTag', () => {
  it('应该识别旧格式标签', () => {
    expect(hasCurrentOpenFileTag('<current-open-file path="test.ts"/>')).toBe(true)
    expect(hasCurrentOpenFileTag('some text <current-open-file path="test.ts"/> more text')).toBe(true)
  })

  it('应该拒绝新格式标签', () => {
    expect(hasCurrentOpenFileTag('<system-reminder type="open-file" path="test.ts"/>')).toBe(false)
  })
})
