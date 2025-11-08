<template>
  <div class="tool-display bash-tool">
    <div class="tool-header">
      <span class="tool-icon">⚡</span>
      <span class="tool-name">Bash</span>
      <code class="tool-command">{{ commandPreview }}</code>
      <span
        v-if="timeout"
        class="timeout"
      >{{ timeoutText }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="command-section">
        <div class="section-header">
          执行命令
        </div>
        <pre class="command-text">{{ command }}</pre>
      </div>
      <div
        v-if="description"
        class="description-section"
      >
        <div class="section-header">
          说明
        </div>
        <p class="description-text">
          {{ description }}
        </p>
      </div>
      <div
        v-if="result"
        class="output-section"
      >
        <div class="section-header">
          <span>执行结果</span>
          <span
            class="exit-code"
            :class="exitCodeClass"
          >{{ exitCodeText }}</span>
        </div>
        <pre
          v-if="stdout"
          class="output stdout"
        >{{ stdout }}</pre>
        <pre
          v-if="stderr"
          class="output stderr"
        >{{ stderr }}</pre>
        <div
          v-if="!stdout && !stderr"
          class="no-output"
        >
          无输出
        </div>
      </div>
    </div>
    <button
      class="expand-btn"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

const command = computed(() => props.toolUse.input.command || '')
const description = computed(() => props.toolUse.input.description || '')
const timeout = computed(() => props.toolUse.input.timeout)

const commandPreview = computed(() => {
  const cmd = command.value
  const maxLength = 60
  if (cmd.length <= maxLength) return cmd
  return cmd.substring(0, maxLength) + '...'
})

const timeoutText = computed(() => {
  if (!timeout.value) return ''
  const seconds = timeout.value / 1000
  return `${seconds}s`
})

const stdout = computed(() => {
  if (!props.result || !props.result.content) return ''
  if (typeof props.result.content === 'string') {
    return props.result.content
  }
  // 假设结构: { stdout: "...", stderr: "...", exit_code: 0 }
  if (typeof props.result.content === 'object' && 'stdout' in props.result.content) {
    return (props.result.content as any).stdout || ''
  }
  return ''
})

const stderr = computed(() => {
  if (!props.result || !props.result.content) return ''
  if (typeof props.result.content === 'object' && 'stderr' in props.result.content) {
    return (props.result.content as any).stderr || ''
  }
  return ''
})

const exitCode = computed(() => {
  if (!props.result || !props.result.content) return null
  if (typeof props.result.content === 'object' && 'exit_code' in props.result.content) {
    return (props.result.content as any).exit_code
  }
  return null
})

const exitCodeClass = computed(() => {
  if (exitCode.value === null) return 'unknown'
  return exitCode.value === 0 ? 'success' : 'error'
})

const exitCodeText = computed(() => {
  if (exitCode.value === null) return '执行中'
  return exitCode.value === 0 ? `成功 (${exitCode.value})` : `失败 (${exitCode.value})`
})
</script>

<style scoped>
.bash-tool {
  border-color: #6f42c1;
}

.bash-tool .tool-name {
  color: #6f42c1;
}

.tool-command {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: rgba(111, 66, 193, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  color: #24292e;
}

.timeout {
  font-size: 11px;
  color: #586069;
  background: #f6f8fa;
  padding: 2px 6px;
  border-radius: 3px;
}

.command-section,
.description-section,
.output-section {
  margin: 12px 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 6px;
}

.command-text {
  margin: 0;
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow-x: auto;
}

.description-text {
  margin: 0;
  padding: 8px 12px;
  background: #f1f8ff;
  border: 1px solid #c8e1ff;
  border-radius: 4px;
  font-size: 13px;
  color: #0366d6;
}

.exit-code {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.exit-code.success {
  background: #e6ffed;
  color: #22863a;
}

.exit-code.error {
  background: #ffeef0;
  color: #d73a49;
}

.exit-code.unknown {
  background: #f1f8ff;
  color: #0366d6;
}

.output {
  margin: 0;
  padding: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.output.stdout {
  background: #ffffff;
  color: #24292e;
  margin-bottom: 8px;
}

.output.stderr {
  background: #ffeef0;
  color: #d73a49;
  border-color: #f97583;
}

.no-output {
  padding: 12px;
  text-align: center;
  color: #586069;
  font-size: 13px;
  font-style: italic;
}
</style>
