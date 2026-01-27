<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    :supports-background="supportsBackground"
    :tool-call="toolCallData"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="terminal-tool-details">
        <!-- 参数区域：key: value 形式 -->
        <div class="params-section">
          <div class="section-title">Params</div>
          <div class="params-list">
            <div v-for="(value, key) in params" :key="key" class="param-row">
              <span class="param-key">{{ key }}:</span>
              <span class="param-value">{{ formatValue(value) }}</span>
            </div>
          </div>
        </div>
        <!-- 结果区域：Markdown 渲染 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">Result</div>
          <div class="result-content">
            <MarkdownRenderer :content="formattedResult" />
          </div>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const params = computed(() => props.toolCall.input || {})

// 只有 Terminal 工具（执行命令）支持后台运行
const supportsBackground = computed(() => {
  const toolName = props.toolCall.toolName || ''
  // mcp__ide-terminal__Terminal 支持后台运行
  // 排除 TerminalRead, TerminalList, TerminalKill 等
  return toolName.endsWith('__Terminal') && !toolName.includes('Read') && !toolName.includes('List')
})

// 传递给 CompactToolCard 的工具调用数据
const toolCallData = computed(() => ({
  toolType: props.toolCall.toolName || '',
  toolUseId: props.toolCall.id,
  input: props.toolCall.input || {},
  result: props.toolCall.result
}))

/**
 * 格式化参数值
 */
function formatValue(value: any): string {
  if (value === null || value === undefined) {
    return 'null'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

/**
 * 解析结果文本为结构化数据
 */
function parseResultJson(text: string): Record<string, any> | null {
  try {
    // 尝试直接解析 JSON
    return JSON.parse(text)
  } catch {
    // 如果解析失败，尝试从 Kotlin Map toString 格式解析
    // 格式: {key=value, key2=value2}
    const match = text.match(/^\{(.+)\}$/)
    if (match) {
      const content = match[1]
      const result: Record<string, any> = {}
      // 简单解析，不处理嵌套
      const pairs = content.split(/, (?=[a-z_]+=)/)
      for (const pair of pairs) {
        const eqIndex = pair.indexOf('=')
        if (eqIndex > 0) {
          const key = pair.substring(0, eqIndex)
          let value: any = pair.substring(eqIndex + 1)
          // 尝试转换类型
          if (value === 'true') value = true
          else if (value === 'false') value = false
          else if (/^\d+$/.test(value)) value = parseInt(value)
          result[key] = value
        }
      }
      return result
    }
    return null
  }
}

/**
 * 获取原始结果文本
 */
const rawResultText = computed(() => {
  const r = props.toolCall.result
  if (!r) return ''
  if (typeof r.content === 'string') return r.content
  if (Array.isArray(r.content)) {
    return (r.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  return JSON.stringify(r.content, null, 2)
})

/**
 * 将结果格式化为 Markdown
 */
const formattedResult = computed(() => {
  const text = rawResultText.value
  if (!text) return ''

  const data = parseResultJson(text)
  if (!data) {
    // 无法解析，直接显示代码块
    return '```\n' + text + '\n```'
  }

  const toolName = props.toolCall.toolName || ''
  return formatTerminalResult(toolName, data)
})

/**
 * 根据工具类型格式化结果
 */
function formatTerminalResult(toolName: string, data: Record<string, any>): string {
  const lines: string[] = []

  // 状态指示
  if (data.success === true) {
    lines.push('**Status:** ✅ Success')
  } else if (data.success === false) {
    lines.push('**Status:** ❌ Failed')
    if (data.error) {
      lines.push(`**Error:** ${data.error}`)
    }
    return lines.join('\n\n')
  }

  // 根据工具类型格式化
  if (toolName.includes('Terminal') && !toolName.includes('Read') && !toolName.includes('List') &&
      !toolName.includes('Kill') && !toolName.includes('Types') && !toolName.includes('Rename') &&
      !toolName.includes('Interrupt')) {
    // Terminal (执行命令)
    if (data.session_id) lines.push(`**Session:** \`${data.session_id}\``)
    if (data.session_name) lines.push(`**Name:** ${data.session_name}`)
    if (data.background) lines.push(`**Mode:** Background`)
    if (data.message) lines.push(`**Message:** ${data.message}`)
    if (data.output) {
      lines.push('')
      lines.push('**Output:**')
      lines.push('```')
      lines.push(data.output)
      lines.push('```')
    }
    if (data.truncated) {
      lines.push(`> ⚠️ Output truncated (${data.total_lines || '?'} lines, ${data.total_chars || '?'} chars)`)
    }
  } else if (toolName.includes('TerminalRead')) {
    // TerminalRead
    if (data.session_id) lines.push(`**Session:** \`${data.session_id}\``)
    if (data.status) lines.push(`**Status:** ${data.status === 'running' ? '🔄 Running' : '⏸️ Idle'}`)

    if (data.matches && Array.isArray(data.matches)) {
      // 搜索模式
      lines.push(`**Matches:** ${data.match_count || data.matches.length}`)
      lines.push('')
      for (const match of data.matches) {
        lines.push(`- Line ${match.line_number}: \`${match.line}\``)
      }
    } else if (data.output) {
      // 普通读取
      if (data.line_count) lines.push(`**Lines:** ${data.line_count}`)
      lines.push('')
      lines.push('**Output:**')
      lines.push('```')
      lines.push(data.output)
      lines.push('```')
    }
  } else if (toolName.includes('TerminalList')) {
    // TerminalList
    lines.push(`**Sessions:** ${data.count || 0}`)
    if (data.sessions && Array.isArray(data.sessions)) {
      lines.push('')
      for (const session of data.sessions) {
        const status = session.is_running ? '🔄' : '⏸️'
        lines.push(`- ${status} \`${session.id}\` (${session.name || 'unnamed'}) - ${session.shell_type || 'unknown'}`)
      }
    }
  } else if (toolName.includes('TerminalKill')) {
    // TerminalKill
    if (data.killed && Array.isArray(data.killed)) {
      lines.push(`**Killed:** ${data.killed.join(', ') || 'none'}`)
    }
    if (data.failed && Array.isArray(data.failed) && data.failed.length > 0) {
      lines.push(`**Failed:** ${data.failed.join(', ')}`)
    }
    if (data.message) lines.push(`**Message:** ${data.message}`)
  } else if (toolName.includes('TerminalTypes')) {
    // TerminalTypes
    lines.push(`**Platform:** ${data.platform || 'unknown'}`)
    lines.push(`**Default:** ${data.default_type || 'unknown'}`)
    if (data.types && Array.isArray(data.types)) {
      lines.push('')
      lines.push('**Available Types:**')
      for (const type of data.types) {
        const isDefault = type.is_default ? ' ⭐' : ''
        lines.push(`- **${type.display_name || type.name}**${isDefault}`)
      }
    }
  } else if (toolName.includes('TerminalRename')) {
    // TerminalRename
    if (data.session_id) lines.push(`**Session:** \`${data.session_id}\``)
    if (data.new_name) lines.push(`**New Name:** ${data.new_name}`)
    if (data.message) lines.push(`**Message:** ${data.message}`)
  } else if (toolName.includes('TerminalInterrupt')) {
    // TerminalInterrupt
    if (data.session_id) lines.push(`**Session:** \`${data.session_id}\``)
    if (data.message) lines.push(`**Message:** ${data.message}`)
  } else {
    // 通用格式化
    for (const [key, value] of Object.entries(data)) {
      if (key === 'success') continue
      if (typeof value === 'object') {
        lines.push(`**${key}:**`)
        lines.push('```json')
        lines.push(JSON.stringify(value, null, 2))
        lines.push('```')
      } else {
        lines.push(`**${key}:** ${value}`)
      }
    }
  }

  return lines.join('\n')
}

const hasResult = computed(() => {
  const r = props.toolCall.result
  return !!(r && rawResultText.value)
})

const hasDetails = computed(() => Object.keys(params.value).length > 0 || hasResult.value)
</script>

<style scoped>
.terminal-tool-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.params-section {
  display: flex;
  flex-direction: column;
}

.params-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
}

.param-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  line-height: 1.4;
}

.param-key {
  color: var(--theme-accent, #0366d6);
  font-weight: 600;
  flex-shrink: 0;
}

.param-value {
  color: var(--theme-foreground, #24292e);
  word-break: break-all;
}

.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.result-content {
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
}

/* Markdown 内容样式覆盖 */
.result-content :deep(.markdown-body) {
  font-size: 12px;
}

.result-content :deep(.markdown-body pre) {
  background: var(--theme-background, #fff);
  max-height: 200px;
  overflow-y: auto;
}

.result-content :deep(.markdown-body code) {
  font-size: 11px;
}
</style>
