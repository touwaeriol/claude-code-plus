<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    :tool-call="toolCallData"
    @toggle="expanded = !expanded"
  >
    <template #details>
      <div class="edit-tool-details">
        <DiffViewer :old-content="oldString" :new-content="newString" />

        <div v-if="replaceAll" class="replace-mode">
          <span class="badge">{{ t('tools.replaceAll') }}</span>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeEditToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import DiffViewer from './DiffViewer.vue'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeEditToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看详情
const expanded = ref(false)

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

// 构造拦截器所需的工具调用数据
const toolCallData = computed(() => ({
  toolType: 'Edit',
  input: props.toolCall.input as Record<string, unknown>,
  result: props.toolCall.result
}))

const oldString = computed(() => props.toolCall.input.old_string || '')
const newString = computed(() => props.toolCall.input.new_string || '')
const replaceAll = computed(() => props.toolCall.input.replace_all || false)
</script>

<style scoped>
.edit-tool {
  border-color: var(--theme-error, #f9826c);
}

.edit-tool .tool-name {
  color: var(--theme-error, #d73a49);
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  margin: 12px 0;
}

.diff-section {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.diff-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
}

.diff-header.old {
  background: #ffeef0;
  color: var(--theme-error, #d73a49);
}

.diff-header.new {
  background: #e6ffed;
  color: var(--theme-success, #22863a);
}

.diff-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 200px;
  overflow: auto;
  background: var(--theme-background, white);
  color: var(--theme-code-foreground, #24292e);
}

.diff-content.old {
  background: #ffeef0;
}

.diff-content.new {
  background: #e6ffed;
}

.diff-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--theme-foreground, #586069);
  opacity: 0.7;
}

.replace-mode {
  margin-top: 8px;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  background: var(--theme-accent, #0366d6);
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
}
</style>
