<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="websearch-details">
        <!-- 参数区域 -->
        <div class="params-section">
          <div class="info-row">
            <span class="label">Query:</span>
            <span class="value query">"{{ query }}"</span>
          </div>
          <div v-if="allowedDomains.length > 0" class="info-row">
            <span class="label">Allowed:</span>
            <div class="domain-list">
              <span v-for="(domain, index) in allowedDomains" :key="index" class="domain-tag allowed">
                {{ domain }}
              </span>
            </div>
          </div>
          <div v-if="blockedDomains.length > 0" class="info-row">
            <span class="label">Blocked:</span>
            <div class="domain-list">
              <span v-for="(domain, index) in blockedDomains" :key="index" class="domain-tag blocked">
                {{ domain }}
              </span>
            </div>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
<div class="section-title">Results</div>
          <pre class="result-content">{{ resultText }}</pre>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// WebSearch 默认折叠（搜索结果可能很长）
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const query = computed(() => props.toolCall.input?.query || '')
const allowedDomains = computed(() => (props.toolCall.input?.allowed_domains as string[]) || [])
const blockedDomains = computed(() => (props.toolCall.input?.blocked_domains as string[]) || [])

// 结果文本
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

// 是否有结果
const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

// 始终有参数可展示
const hasDetails = computed(() => !!query.value)
</script>

<style scoped>
.websearch-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  align-items: baseline;
}

.label {
  color: var(--theme-secondary-foreground, #586069);
  min-width: 70px;
  flex-shrink: 0;
}

.value {
  color: var(--theme-foreground, #24292e);
}

.value.query {
  color: var(--theme-accent, #4338ca);
  font-weight: 500;
}

.domain-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.domain-tag {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
}

.domain-tag.allowed {
  background: #e6ffed;
  color: #22863a;
}

.domain-tag.blocked {
  background: #ffeef0;
  color: #d73a49;
}

.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.result-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>
