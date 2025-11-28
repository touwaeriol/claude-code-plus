<template>
  <div class="tool-display ask-tool">
    <div class="tool-header">
      <span class="tool-icon">❓</span>
      <span class="tool-name">AskUser</span>
      <span class="question-count">{{ t('tools.questions', { count: questions.length }) }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div
        v-for="(q, index) in questions"
        :key="index"
        class="question-item"
      >
        <div class="question-text">{{ q.question }}</div>
        <div v-if="q.options && q.options.length > 0" class="options">
          <span
            v-for="(opt, idx) in q.options"
            :key="idx"
            class="option-tag"
          >{{ opt }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { GenericToolCall } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看所有问题
const expanded = ref(false)

const questions = computed(() => (props.toolCall.input as any)?.questions || [])
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-panel-background, #f6f8fa);
  margin: 8px 0;
  padding: 8px 12px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
}

.question-count {
  margin-left: auto;
  font-size: 12px;
  color: #586069;
}

.tool-content {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.question-item {
  padding: 6px 8px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  background: #fff;
}

.question-text {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}

.options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.option-tag {
  padding: 2px 6px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
}
</style>
