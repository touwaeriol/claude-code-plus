<template>
  <div
    class="thinking-select"
    :class="{ 'is-disabled': !canToggle }"
    :title="tooltipText"
  >
    <el-select
      v-model="selectedLevelId"
      :disabled="!canToggle"
      class="level-select"
      placement="top"
      size="small"
      @change="onLevelChange"
    >
      <el-option
        v-for="level in levels"
        :key="level.id"
        :value="level.id"
        :label="level.name"
      />
    </el-select>
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

const props = defineProps<Props>()

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

.level-select {
  width: 80px;
}

.level-select :deep(.el-select__wrapper) {
  font-size: 11px;
  min-height: 22px;
  padding: 2px 6px;
  background: transparent;
  box-shadow: none !important;
  border: none !important;
  border-radius: 4px;
  gap: 2px;
}

.level-select :deep(.el-select__wrapper:hover) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
}

.level-select :deep(.el-select__wrapper.is-focused) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
}
</style>
