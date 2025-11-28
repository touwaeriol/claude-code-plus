<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="exitplan-details">
        <div v-if="plan" class="params-section">
          <div class="section-title">Plan</div>
          <pre class="plan-content">{{ plan }}</pre>
        </div>
        <div v-if="hasResult" class="result-section">
          <div class="section-title">结果</div>
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
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))
const plan = computed(() => props.toolCall.input?.plan || '')

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  return JSON.stringify(r.content, null, 2)
})

const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

const hasDetails = computed(() => !!plan.value || hasResult.value)
</script>

<style scoped>
.exitplan-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.plan-content {
  margin: 0;
  padding: 8px;
  background: var(--ide-code-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.result-section {
  border-top: 1px solid var(--ide-border, #e1e4e8);
  padding-top: 8px;
}

.result-content {
  margin: 0;
  padding: 8px;
  background: var(--ide-code-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
}
</style>
