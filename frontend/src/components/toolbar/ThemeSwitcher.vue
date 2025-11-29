<template>
  <button
    class="theme-switcher icon-btn"
    type="button"
    :title="currentThemeTitle"
    @click="handleClick"
  >
    <span class="theme-icon">{{ currentIcon }}</span>
  </button>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { themeService, type ThemeMode } from '@/services/themeService'

const themeMode = ref<ThemeMode>('system')
let unsubscribe: (() => void) | null = null

/**
 * 通过背景色亮度判断当前是否为暗色主题
 */
function isCurrentThemeDark(): boolean {
  const theme = themeService.getCurrentTheme()
  if (!theme) return false

  const hex = theme.background.replace('#', '')
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
  return luminance < 128
}

const isDark = ref(false)

onMounted(() => {
  isDark.value = isCurrentThemeDark()
  themeMode.value = themeService.getThemeMode()

  // 监听主题变化
  unsubscribe = themeService.onThemeChange(() => {
    isDark.value = isCurrentThemeDark()
    themeMode.value = themeService.getThemeMode()
  })
})

onUnmounted(() => {
  unsubscribe?.()
})

const currentIcon = computed(() => {
  if (themeMode.value === 'system') return '🖥️'
  return isDark.value ? '🌙' : '☀️'
})

const currentThemeTitle = computed(() => {
  const labels: Record<ThemeMode, string> = {
    'system': 'System',
    'light': 'Light',
    'dark': 'Dark'
  }
  return `Theme: ${labels[themeMode.value]}`
})

function handleClick() {
  // 循环切换: system → light → dark → system
  const nextMode: Record<ThemeMode, ThemeMode> = {
    'system': 'light',
    'light': 'dark',
    'dark': 'system'
  }
  themeService.setTheme(nextMode[themeMode.value])
}
</script>

<style scoped>
.theme-switcher {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-card-background, #ffffff);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  padding: 0;
}

.theme-switcher:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--theme-accent, #0366d6);
  transform: scale(1.05);
}

.theme-switcher:active {
  transform: scale(0.95);
}

.theme-icon {
  font-size: 14px;
  line-height: 1;
}
</style>
