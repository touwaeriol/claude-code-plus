<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="skill-details">
        <div class="params-section">
          <div class="info-row">
            <span class="label">Skill:</span>
            <code class="value">{{ skill }}</code>
          </div>
        </div>
        <div v-if="hasResult" class="result-section">
          <div class="section-title">Result</div>
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
const skill = computed(() => props.toolCall.input?.skill || '')

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

const hasDetails = computed(() => !!skill.value)
</script>

<style scoped>
.skill-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  align-items: baseline;
}

.label {
  color: var(--theme-secondary-foreground, #586069);
  min-width: 50px;
}

.value {
  color: var(--theme-foreground, #24292e);
}

code.value {
  background: var(--theme-code-background, #f0f4f8);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: var(--theme-editor-font-family);
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
  font-family: var(--theme-editor-font-family);
  white-space: pre-wrap;
}
</style>
