<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    @click="handleCardClick"
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
import { toolEnhancement } from '@/services/toolEnhancement'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeEditToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看详情
const expanded = ref(false)

import DiffViewer from './DiffViewer.vue'

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

// 处理卡片点击：使用拦截器统一处理增强
async function handleCardClick() {
  // 尝试获取增强动作
  const action = await toolEnhancement.intercept(props.toolCall as any, props.toolCall.result as any)
  
  if (action) {
    // 在 JCEF 环境中，执行增强动作（显示 Diff）
    const context = {
      toolType: props.toolCall.toolType,
      input: props.toolCall.input,
      result: props.toolCall.result,
      isSuccess: props.toolCall.status === 'SUCCESS'
    }
    await toolEnhancement.executeAction(action, context)
  } else {
    // 非 JCEF 环境或没有增强规则，切换展开状态
    expanded.value = !expanded.value
  }
}

const oldString = computed(() => props.toolCall.input.old_string || '')
const newString = computed(() => props.toolCall.input.new_string || '')
const replaceAll = computed(() => props.toolCall.input.replace_all || false)
</script>

<style scoped>
.edit-tool {
  border-color: var(--ide-error, #f9826c);
}

.edit-tool .tool-name {
  color: var(--ide-error, #d73a49);
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  margin: 12px 0;
}

.diff-section {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.diff-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.diff-header.old {
  background: #ffeef0;
  color: var(--ide-error, #d73a49);
}

.diff-header.new {
  background: #e6ffed;
  color: var(--ide-success, #22863a);
}

.diff-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 200px;
  overflow: auto;
  background: var(--ide-background, white);
  color: var(--ide-code-foreground, #24292e);
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
  color: var(--ide-foreground, #586069);
  opacity: 0.7;
}

.replace-mode {
  margin-top: 8px;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  background: var(--ide-accent, #0366d6);
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
}

/* 暗色主题适配 */
.theme-dark .diff-header.old {
  background: #3d1f1f;
}

.theme-dark .diff-header.new {
  background: #1f3d1f;
}

.theme-dark .diff-content.old {
  background: #3d1f1f;
}

.theme-dark .diff-content.new {
  background: #1f3d1f;
}
</style>
