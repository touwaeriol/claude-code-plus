<template>
  <div
    class="chrome-status-indicator"
    :class="statusClass"
    :title="tooltip"
    @click="handleClick"
  >
    <!-- Chrome 图标 -->
    <span class="chrome-icon">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
        <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
        <circle cx="12" cy="12" r="4" fill="currentColor"/>
        <path d="M12 2 L12 8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        <path d="M12 16 L12 22" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        <path d="M2 12 L8 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        <path d="M16 12 L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
    </span>
    <span class="status-label">Chrome</span>
    <span class="status-dot" :class="dotClass" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface ChromeStatus {
  installed: boolean
  enabled: boolean
  connected: boolean
  mcpServerStatus?: string
  extensionVersion?: string
}

interface Props {
  /** Chrome 扩展状态 */
  status: ChromeStatus | null
  /** 是否正在加载 */
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<{
  /** 点击事件 - 根据状态决定行为 */
  (e: 'click', status: ChromeStatus | null): void
  /** 请求启用 Chrome */
  (e: 'enableChrome'): void
}>()

const CHROME_WEBSTORE_URL = 'https://chromewebstore.google.com/detail/claude/fcoeoabgfenejglbffodgkkbkcdhcgfn'

/** 状态类名 */
const statusClass = computed(() => {
  if (props.loading) return 'is-loading'
  if (!props.status) return 'is-unknown'
  if (props.status.connected) return 'is-connected'
  if (props.status.installed && props.status.enabled) return 'is-enabled'
  if (props.status.installed) return 'is-installed'
  return 'is-not-installed'
})

/** 状态点类名 */
const dotClass = computed(() => {
  if (props.loading) return 'loading'
  if (!props.status) return 'unknown'
  if (props.status.connected) return 'connected'
  if (props.status.installed && props.status.enabled) return 'enabled'
  if (props.status.installed) return 'installed'
  return 'not-installed'
})

/** 提示文字 */
const tooltip = computed(() => {
  if (props.loading) return '正在检查 Chrome 扩展状态...'
  if (!props.status) return 'Chrome 扩展状态未知'

  if (props.status.connected) {
    const version = props.status.extensionVersion ? ` (v${props.status.extensionVersion})` : ''
    return `Chrome 扩展已连接${version}\n点击查看详情`
  }

  if (props.status.installed && props.status.enabled) {
    return 'Chrome 扩展已启用但未连接\n请确保 Chrome 浏览器已打开'
  }

  if (props.status.installed) {
    return 'Chrome 扩展已安装但未启用\n点击启用扩展（下次连接时生效）'
  }

  return 'Chrome 扩展未安装\n点击前往 Chrome Web Store 安装'
})

/** 点击处理 */
function handleClick() {
  emit('click', props.status)

  if (!props.status || !props.status.installed) {
    // 未安装 - 打开 Chrome Web Store
    window.open(CHROME_WEBSTORE_URL, '_blank')
    return
  }

  if (props.status.installed && !props.status.enabled) {
    // 已安装但未启用 - 触发启用事件
    emit('enableChrome')
  }
}
</script>

<style scoped>
.chrome-status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  color: var(--theme-secondary-foreground, #6b7280);
  background: transparent;
}

.chrome-status-indicator:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05));
}

.chrome-status-indicator.is-loading {
  opacity: 0.6;
  cursor: wait;
}

.chrome-status-indicator.is-connected {
  color: var(--theme-success, #22c55e);
}

.chrome-status-indicator.is-enabled {
  color: var(--theme-warning, #f59e0b);
}

.chrome-status-indicator.is-installed {
  color: var(--theme-secondary-foreground, #6b7280);
}

.chrome-status-indicator.is-not-installed {
  color: var(--theme-secondary-foreground, #9ca3af);
  opacity: 0.7;
}

.chrome-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
}

.chrome-icon svg {
  width: 14px;
  height: 14px;
}

.status-label {
  font-weight: 500;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-left: 2px;
  transition: background-color 0.2s ease;
}

.status-dot.loading {
  background: var(--theme-secondary-foreground, #9ca3af);
  animation: pulse 1.5s ease-in-out infinite;
}

.status-dot.connected {
  background: var(--theme-success, #22c55e);
}

.status-dot.enabled {
  background: var(--theme-warning, #f59e0b);
}

.status-dot.installed {
  background: var(--theme-secondary-foreground, #6b7280);
}

.status-dot.not-installed,
.status-dot.unknown {
  background: var(--theme-secondary-foreground, #9ca3af);
  opacity: 0.5;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
