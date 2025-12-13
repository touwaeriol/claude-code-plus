<template>
  <div
    v-if="visible"
    ref="popupRef"
    class="slash-command-popup"
    :style="popupStyle"
  >
    <div class="command-list">
      <div
        v-for="(cmd, index) in filteredCommands"
        :key="cmd.name"
        class="command-item"
        :class="{ selected: selectedIndex === index }"
        @click="selectCommand(cmd)"
        @mouseenter="selectedIndex = index"
      >
        <span class="command-name">{{ cmd.name }}</span>
        <span class="command-desc">{{ cmd.description }}</span>
      </div>
    </div>
    <div v-if="filteredCommands.length === 0" class="no-results">
      {{ t('chat.noMatchingCommands') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'

interface SlashCommand {
  name: string
  description: string
}

interface Props {
  visible: boolean
  query: string  // 用户输入的查询（/后面的内容）
  anchorElement: HTMLElement | null
}

interface Emits {
  (e: 'select', command: SlashCommand): void
  (e: 'dismiss'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()

const popupRef = ref<HTMLElement>()
const selectedIndex = ref(0)

// 可用的斜杠命令
const commands = computed<SlashCommand[]>(() => [
  { name: '/compact', description: t('slashCommand.compact') },
  { name: '/context', description: t('slashCommand.context') },
  { name: '/rename', description: t('slashCommand.rename') },
])

// 根据查询过滤命令（只匹配命令名称，使用 startsWith）
const filteredCommands = computed(() => {
  const q = props.query.toLowerCase()
  if (!q) return commands.value
  // 只匹配命令名称（去掉 / 后比较）
  return commands.value.filter(cmd =>
    cmd.name.slice(1).toLowerCase().startsWith(q)
  )
})

// 弹窗位置样式
const popupStyle = computed(() => {
  if (!props.anchorElement) {
    return { display: 'none' }
  }

  const rect = props.anchorElement.getBoundingClientRect()
  return {
    position: 'fixed' as const,
    left: `${rect.left}px`,
    bottom: `${window.innerHeight - rect.top + 8}px`,
    minWidth: '280px',
    maxWidth: '360px',
  }
})

// 重置选中索引
watch(() => props.visible, (newVal) => {
  if (newVal) {
    selectedIndex.value = 0
  }
})

watch(() => props.query, () => {
  selectedIndex.value = 0
})

// 选择命令
function selectCommand(cmd: SlashCommand) {
  emit('select', cmd)
}

// 键盘导航
function handleKeydown(event: KeyboardEvent) {
  if (!props.visible) return

  switch (event.key) {
    case 'ArrowUp':
      event.preventDefault()
      event.stopPropagation()
      selectedIndex.value = Math.max(0, selectedIndex.value - 1)
      break
    case 'ArrowDown':
      event.preventDefault()
      event.stopPropagation()
      selectedIndex.value = Math.min(filteredCommands.value.length - 1, selectedIndex.value + 1)
      break
    case 'Enter':
    case 'Tab':
      event.preventDefault()
      event.stopPropagation()
      if (filteredCommands.value.length > 0) {
        selectCommand(filteredCommands.value[selectedIndex.value])
      }
      break
    case 'Escape':
      event.preventDefault()
      event.stopPropagation()
      emit('dismiss')
      break
  }
}

// 点击外部关闭
function handleClickOutside(event: MouseEvent) {
  if (!props.visible) return
  if (popupRef.value && !popupRef.value.contains(event.target as Node)) {
    emit('dismiss')
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown, true)
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown, true)
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.slash-command-popup {
  background: var(--theme-background);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  z-index: 10001;
  overflow: hidden;
}

.command-list {
  max-height: 200px;
  overflow-y: auto;
}

.command-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.command-item:hover,
.command-item.selected {
  background: var(--theme-hover-background);
}

.command-item.selected {
  background: var(--theme-accent);
}

.command-item.selected .command-name,
.command-item.selected .command-desc {
  color: var(--theme-background);
}

.command-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-accent);
  font-family: monospace;
  flex-shrink: 0;
}

.command-desc {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.no-results {
  padding: 16px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--theme-secondary-foreground);
}
</style>
