<template>
  <div
    class="thinking-select"
    :class="{ 'is-disabled': !canToggle }"
    :title="tooltipText"
  >
    <span class="select-label">{{ $t('thinking.label') }}</span>
    <select
      v-model="selectedLevelId"
      :disabled="!canToggle"
      class="level-select"
      @change="onLevelChange"
    >
      <option
        v-for="level in levels"
        :key="level.id"
        :value="level.id"
      >
        {{ level.name }}
      </option>
    </select>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ThinkingMode } from '@/constants/models'
import type { ThinkingLevelConfig } from '@/services/jetbrainsRSocket'

const { t } = useI18n()

interface Props {
  /** 思考模式（由模型决定） */
  thinkingMode: ThinkingMode
  /** 当前思考 token 数量 */
  thinkingTokens: number
  /** 可用的思考级别列表 */
  thinkingLevels?: ThinkingLevelConfig[]
}

const props = withDefaults(defineProps<Props>(), {
  thinkingLevels: () => [
    { id: 'off', name: 'Off', tokens: 0, isCustom: false },
    { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
    { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
  ]
})

const emit = defineEmits<{
  (e: 'change', tokens: number): void
}>()

/** 可用级别列表 */
const levels = computed(() => props.thinkingLevels)

/** 根据当前 token 数量找到对应的级别 ID */
function findLevelIdByTokens(tokens: number): string {
  // 精确匹配
  const exactMatch = props.thinkingLevels.find(l => l.tokens === tokens)
  if (exactMatch) return exactMatch.id

  // 如果 tokens 为 0，返回 off
  if (tokens === 0) return 'off'

  // 否则返回最接近的级别
  let closest = props.thinkingLevels[0]
  let minDiff = Math.abs(tokens - closest.tokens)
  for (const level of props.thinkingLevels) {
    const diff = Math.abs(tokens - level.tokens)
    if (diff < minDiff) {
      minDiff = diff
      closest = level
    }
  }
  return closest.id
}

/** 当前选中的级别 ID */
const selectedLevelId = ref(findLevelIdByTokens(props.thinkingTokens))

/** 监听 props 变化更新选中项 */
watch(() => props.thinkingTokens, (newTokens) => {
  selectedLevelId.value = findLevelIdByTokens(newTokens)
})

/** 是否可以切换 */
const canToggle = computed(() => props.thinkingMode === 'optional')

/** 提示文本 */
const tooltipText = computed(() => {
  switch (props.thinkingMode) {
    case 'always':
      return t('thinking.alwaysOn')
    case 'never':
      return t('thinking.notSupported')
    case 'optional':
      return t('thinking.selectLevel')
  }
})

/** 处理级别切换 */
function onLevelChange() {
  if (!canToggle.value) return

  const selectedLevel = props.thinkingLevels.find(l => l.id === selectedLevelId.value)
  if (selectedLevel) {
    emit('change', selectedLevel.tokens)
  }
}
</script>

<style scoped>
.thinking-select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  user-select: none;
}

.thinking-select.is-disabled {
  opacity: 0.5;
}

.select-label {
  color: var(--theme-secondary-foreground, #6b7280);
  font-weight: 500;
}

.level-select {
  padding: 2px 6px;
  border: 1px solid var(--theme-border-color, rgba(0, 0, 0, 0.1));
  border-radius: 4px;
  background: var(--theme-panel-background, rgba(0, 0, 0, 0.05));
  color: var(--theme-foreground, #333);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  min-width: 70px;
}

.level-select:hover:not(:disabled) {
  border-color: var(--theme-accent, #0366d6);
}

.level-select:focus {
  outline: none;
  border-color: var(--theme-accent, #0366d6);
  box-shadow: 0 0 0 2px rgba(3, 102, 214, 0.2);
}

.level-select:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.level-select option {
  background: var(--theme-background, #fff);
  color: var(--theme-foreground, #333);
}
</style>
