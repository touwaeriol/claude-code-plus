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

const isDark = ref(false)
const themeMode = ref<ThemeMode>('system')
let unsubscribe: (() => void) | null = null

onMounted(() => {
  isDark.value = themeService.isDarkTheme()
  themeMode.value = themeService.getThemeMode()

  // 监听主题变化
  unsubscribe = themeService.onThemeChange((theme) => {
    isDark.value = theme.isDark
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
  border: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-card-background, #ffffff);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  padding: 0;
}

.theme-switcher:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--ide-accent, #0366d6);
  transform: scale(1.05);
}

.theme-switcher:active {
  transform: scale(0.95);
}

.theme-icon {
  font-size: 14px;
  line-height: 1;
}

:global(.theme-dark) .theme-switcher {
  background: var(--ide-card-background, #161b22);
  border-color: var(--ide-border, #30363d);
}

:global(.theme-dark) .theme-switcher:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--ide-accent, #58a6ff);
}
</style>
