<template>
  <div class="bash-tool-display">
    <!-- 紧凑卡片（未展开状态） -->
    <CompactToolCard
      :display-info="displayInfo"
      :is-expanded="expanded"
      :has-details="true"
      @click="expanded = !expanded"
    >
      <!-- 展开内容 -->
      <template #details>
        <div class="bash-details">
          <!-- Command -->
          <div class="command-section">
            <pre class="command-text">{{ command }}</pre>
          </div>

          <!-- Output -->
          <div v-if="result" class="output-section">
            <pre v-if="stdout" class="output stdout">{{ stdout }}</pre>
            <pre v-if="stderr" class="output stderr">{{ stderr }}</pre>
            <div v-if="!stdout && !stderr" class="no-output">No output</div>
          </div>
        </div>
      </template>
    </CompactToolCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

// 获取显示信息
const displayInfo = computed(() => {
  return extractToolDisplayInfo(props.toolUse, props.result)
})

// 命令相关
const command = computed(() => props.toolUse.input.command || '')
const description = computed(() => props.toolUse.input.description || '')
const cwd = computed(() => props.toolUse.input.cwd || '')
const timeout = computed(() => props.toolUse.input.timeout)

const timeoutText = computed(() => {
  if (!timeout.value) return ''
  const seconds = timeout.value / 1000
  return `${seconds}s`
})

// 输出解析
const stdout = computed(() => {
  if (!props.result || !props.result.content) return ''
  if (typeof props.result.content === 'string') {
    return props.result.content
  }
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
.bash-tool-display {
  width: 100%;
}

.bash-details {
  padding: 12px;
  background: #f6f8fa;
  border-top: 1px solid #e1e4e8;
}

.command-section,
.cwd-section,
.description-section,
.output-section {
  margin-bottom: 16px;
}

.command-section:last-child,
.cwd-section:last-child,
.description-section:last-child,
.output-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 2px;
}

.timeout {
  font-size: 11px;
  color: #586069;
  background: #ffffff;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid #e1e4e8;
}

.command-text {
  margin: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow-x: auto;
  color: #24292e;
}

.cwd-text {
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #586069;
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
  margin-bottom: 2px;
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
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}
</style>
