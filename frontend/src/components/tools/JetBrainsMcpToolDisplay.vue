<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="jetbrains-tool-details">
        <!-- 参数区域：key: value 形式一行一行展示 -->
        <div class="params-section">
          <div class="section-title">Params</div>
          <div class="params-list">
            <div v-for="(value, key) in params" :key="key" class="param-row">
              <span class="param-key">{{ key }}:</span>
              <span class="param-value">{{ formatValue(value) }}</span>
            </div>
          </div>
        </div>
        <!-- 结果区域：渲染成 Markdown -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">Result</div>
          <div class="result-content">
            <MarkdownRenderer :content="resultText" />
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

/**
 * 格式化参数值
 * - 如果是 JSON 对象/数组，直接 JSON.stringify 展示
 * - 其他类型直接转字符串
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

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  if (Array.isArray(r.content)) {
    return (r.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  return JSON.stringify(r.content, null, 2)
})

const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

const hasDetails = computed(() => Object.keys(params.value).length > 0 || hasResult.value)
</script>

<style scoped>
.jetbrains-tool-details {
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
  font-family: monospace;
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
}
</style>
