<template>
  <div class="bash-tool-display">
    <CompactToolCard
      :display-info="displayInfo"
      :is-expanded="expanded"
      :has-details="true"
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
  if (result.type === 'success') {
    return typeof result.output === 'string' ? result.output : JSON.stringify(result.output)
  }
  return ''
})

const stderr = computed(() => {
  const result = props.toolCall.result
  if (result && result.type === 'error') {
    return result.error || ''
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
  background: #f6f8fa;
  border-top: 1px solid #e1e4e8;
}

.command-section {
  margin-bottom: 12px;
}

.command-text {
  background: #2d2d2d;
  color: #e6e6e6;
  padding: 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
}

.cwd,
.timeout {
  font-size: 12px;
  color: #586069;
  margin-top: 4px;
}

.output-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.output {
  background: #111;
  color: #eee;
  padding: 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
  max-height: 240px;
  overflow-y: auto;
}

.output.stderr {
  background: #2d1a1a;
  color: #ffb3b3;
}

.no-output {
  color: #888;
  font-size: 12px;
}
</style>
