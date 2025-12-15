<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #header-actions>
      <button v-if="isIdeEnvironment() && plan" class="btn-view-idea" @click.stop="openPlanInIdea">
        {{ t('permission.viewInIdea') }}
      </button>
    </template>
    <template #details>
      <div class="exitplan-details">
        <div v-if="plan" class="params-section">
          <div class="section-title">Plan</div>
          <div class="plan-content">
            <MarkdownRenderer :content="plan" />
          </div>
        </div>
        <div v-if="hasError" class="error-section">
          <div class="section-title">Error</div>
          <pre class="error-content">{{ errorText }}</pre>
        </div>
        <div v-else-if="hasResult" class="result-section">
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
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import { jetbrainsBridge, isIdeEnvironment } from '@/services/jetbrainsApi'
import { useI18n } from '@/composables/useI18n'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
const { t } = useI18n()
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))
const plan = computed(() => props.toolCall.input?.plan || '')

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  return JSON.stringify(r.content, null, 2)
})

const errorText = computed(() => {
  const r = props.toolCall.result
  if (!r || !r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  return JSON.stringify(r.content, null, 2)
})

const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

const hasError = computed(() => {
  const r = props.toolCall.result
  return r && r.is_error
})

const hasDetails = computed(() => !!plan.value || !!hasResult.value || !!hasError.value)

// 在 IDEA 中打开 plan
async function openPlanInIdea() {
  if (!plan.value) return

  const success = await jetbrainsBridge.showMarkdown({
    content: plan.value,
    title: t('permission.planPreviewTitle')
  })

  if (!success) {
    console.warn('[ExitPlanModeToolDisplay] Failed to open plan in IDEA')
  }
}
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
  color: var(--theme-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.plan-content {
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.plan-content :deep(pre) {
  margin: 0;
  padding: 8px;
  background: var(--theme-background, #fff);
  border-radius: 4px;
}

.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
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
}

.error-section {
  border-top: 1px solid var(--theme-error, #dc3545);
  padding-top: 8px;
}

.error-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-error-subtle, rgba(220, 53, 69, 0.1));
  border: 1px solid var(--theme-error, #dc3545);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  color: var(--theme-error, #dc3545);
}

.btn-view-idea {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--theme-accent-subtle, #e8f1fb);
  color: var(--theme-accent, #0366d6);
  border: 1px solid var(--theme-accent, #0366d6);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-view-idea:hover {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}
</style>
