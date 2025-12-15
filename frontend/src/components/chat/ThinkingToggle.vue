<template>
  <StatusToggle
    :label="$t('thinking.label')"
    :enabled="isEnabled"
    :disabled="!canToggle"
    :show-icon="thinkingMode !== 'never'"
    :tooltip="tooltipText"
    @toggle="handleToggle"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ThinkingMode } from '@/constants/models'
import StatusToggle from './StatusToggle.vue'

const { t } = useI18n()

interface Props {
  /** 思考模式 */
  thinkingMode: ThinkingMode
  /** 当前是否开启（仅 optional 模式有效） */
  enabled: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'toggle', value: boolean): void
}>()

/** 是否可以切换 */
const canToggle = computed(() => props.thinkingMode === 'optional')

/** 实际的开启状态 */
const isEnabled = computed(() => {
  switch (props.thinkingMode) {
    case 'always':
      return true
    case 'never':
      return false
    case 'optional':
      return props.enabled
  }
})

/** 提示文本 */
const tooltipText = computed(() => {
  switch (props.thinkingMode) {
    case 'always':
      return t('thinking.alwaysOn')
    case 'never':
      return t('thinking.notSupported')
    case 'optional':
      return props.enabled ? t('thinking.toggleOff') : t('thinking.toggleOn')
  }
})

function handleToggle(value: boolean) {
  emit('toggle', value)
}
</script>
