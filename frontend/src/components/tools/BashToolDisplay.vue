<template>
  <div class="bash-tool-display">
    <CompactToolCard
      :display-info="displayInfo"
      :is-expanded="expanded"
      :has-details="true"
      :supports-background="true"
      @click="expanded = !expanded"
    >
      <template #details>
        <div class="bash-details">
          <div class="command-section">
            <pre class="command-text">{{ command }}</pre>
            <div v-if="cwd" class="cwd">cwd: {{ cwd }}</div>
            <div v-if="timeoutText" class="timeout">timeout: {{ timeoutText }}</div>
          </div>
          <div v-if="stdout || stderr" class="output-section">
            <pre v-if="stdout" class="output stdout">{{ stdout }}</pre>
            <pre v-if="stderr" class="output stderr">{{ stderr }}</pre>
          </div>
          <div v-else class="no-output">No output</div>
        </div>
      </template>
    </CompactToolCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import type { ClaudeBashToolCall } from '@/types/display'

interface Props {
  toolCall: ClaudeBashToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const command = computed(() => props.toolCall.input.command || '')
const cwd = computed(() => props.toolCall.input.cwd || '')
const timeout = computed(() => props.toolCall.input.timeout)

const timeoutText = computed(() => {
  if (!timeout.value) return ''
  const seconds = timeout.value / 1000
  return `${seconds}s`
})

const stdout = computed(() => {
  const result = props.toolCall.result
  if (!result) return ''
  // 使用后端格式：非错误时显示 content
  if (!result.is_error) {
    return typeof result.content === 'string' ? result.content : JSON.stringify(result.content)
  }
  return ''
})

const stderr = computed(() => {
  const result = props.toolCall.result
  // 使用后端格式：is_error 为 true 时显示错误
  if (result && result.is_error) {
    return typeof result.content === 'string' ? result.content : JSON.stringify(result.content)
  }
  return ''
})
</script>

<style scoped>
.bash-tool-display {
  width: 100%;
}

.bash-details {
  padding: 12px;
  background: var(--theme-panel-background);
  border-top: 1px solid var(--theme-border);
}

.command-section {
  margin-bottom: 12px;
}

.command-text {
  background: var(--theme-code-background);
  color: var(--theme-foreground);
  padding: 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
  margin: 0;
}

.cwd,
.timeout {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
  margin-top: 4px;
}

.output-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.output {
  background: var(--theme-code-background);
  color: var(--theme-foreground);
  padding: 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
  max-height: 240px;
  overflow-y: auto;
  margin: 0;
}

.output.stderr {
  background: var(--theme-info-background);
  color: var(--theme-error);
}

.no-output {
  color: var(--theme-secondary-foreground);
  font-size: 12px;
}
</style>
