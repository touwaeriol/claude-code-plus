<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="edits.length > 0"
    :tool-call="toolCallData"
    @toggle="expanded = !expanded"
  >
    <template #details>
      <div class="multi-edit-details">
        <div class="edits-list">
          <div
            v-for="(edit, index) in edits"
            :key="index"
            class="edit-item"
          >
            <div
              class="edit-header"
              @click="toggleEdit(index)"
            >
              <span class="edit-number">{{ t('tools.changeNumber', { number: index + 1 }) }}</span>
              <span
                v-if="edit.replace_all"
                class="badge replace-all"
              >{{ t('tools.replaceAll') }}</span>
              <span class="expand-icon">{{ expandedEdits.has(index) ? '▲' : '▼' }}</span>
            </div>

            <div
              v-if="expandedEdits.has(index)"
              class="edit-content"
            >
              <div class="edit-preview">
                <div class="diff-section">
                  <div class="diff-header old">
                    <span></span>
                    <button
                      class="copy-btn"
                      :title="t('common.copy')"
                      @click="copyText(edit.old_string)"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                      </svg>
                    </button>
                  </div>
                  <pre class="diff-content old">{{ edit.old_string }}</pre>
                </div>
                <div class="diff-arrow">
                  →
                </div>
                <div class="diff-section">
                  <div class="diff-header new">
                    <span></span>
                    <button
                      class="copy-btn"
                      :title="t('common.copy')"
                      @click="copyText(edit.new_string)"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                      </svg>
                    </button>
                  </div>
                  <pre class="diff-content new">{{ edit.new_string }}</pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeMultiEditToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeMultiEditToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看详情
const expanded = ref(false)
const expandedEdits = ref<Set<number>>(new Set())

const filePath = computed(() => props.toolCall.input.file_path || props.toolCall.input.path || '')
const fileName = computed(() => {
  const path = filePath.value
  return path.split(/[\\/]/).pop() || path
})

const edits = computed(() => props.toolCall.input.edits || [])

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

// 构造拦截器所需的工具调用数据
const toolCallData = computed(() => ({
  toolType: 'MultiEdit',
  input: props.toolCall.input as Record<string, unknown>,
  result: props.toolCall.result
}))

function toggleEdit(index: number) {
  const set = new Set(expandedEdits.value)
  if (set.has(index)) {
    set.delete(index)
  } else {
    set.add(index)
  }
  expandedEdits.value = set
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text || '')
}
</script>

<style scoped>
.multi-edit-details {
  padding: 4px 0;
}

.edits-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-item {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  background: #fff;
}

.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--theme-panel-background, #f6f8fa);
  cursor: pointer;
}

.edit-number {
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
}

.badge.replace-all {
  padding: 2px 6px;
  border-radius: 10px;
  background: #e55353;
  color: #fff;
  font-size: 11px;
}

.expand-icon {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #666);
}

.edit-content {
  padding: 8px 10px;
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 6px;
}

.diff-section {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  background: #fdfdfd;
}

.diff-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 600;
}

.diff-header.old {
  background: #fff5f5;
  color: #c53030;
}

.diff-header.new {
  background: #f0fff4;
  color: #2f855a;
}

.diff-content {
  margin: 0;
  padding: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

.diff-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--theme-secondary-foreground, #666);
}

.copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: inherit;
}

.copy-btn:hover {
  opacity: 0.8;
}
</style>
