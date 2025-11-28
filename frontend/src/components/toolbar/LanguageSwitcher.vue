<template>
  <div class="language-switcher">
    <el-dropdown trigger="click" @command="handleCommand">
      <button class="lang-btn" type="button" :title="currentLocaleName">
        <span class="lang-code">{{ currentLocaleCode }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="item in supportedLocales"
            :key="item.value"
            :command="item.value"
            :class="{ active: locale === item.value }"
          >
            <span class="lang-label">{{ item.label }}</span>
            <span v-if="locale === item.value" class="check-mark">✓</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { setLocale, type SupportedLocale } from '@/i18n'

const { locale } = useI18n()

const supportedLocales = [
  { value: 'zh-CN', label: '中文', code: '中' },
  { value: 'en-US', label: 'English', code: 'EN' },
  { value: 'ko-KR', label: '한국어', code: '한' },
  { value: 'ja-JP', label: '日本語', code: '日' }
]

// 当前语言的缩写（显示在按钮上）
const currentLocaleCode = computed(() => {
  const found = supportedLocales.find(l => l.value === locale.value)
  return found?.code || 'EN'
})

// 当前语言的完整名称（显示在 tooltip 中）
const currentLocaleName = computed(() => {
  const found = supportedLocales.find(l => l.value === locale.value)
  return found?.label || 'English'
})

function handleCommand(newLocale: SupportedLocale) {
  if (newLocale === locale.value) return
  setLocale(newLocale)
  // 不需要刷新！vue-i18n 是响应式的
}
</script>

<style scoped>
.language-switcher {
  display: inline-flex;
}

.lang-btn {
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-card-background, #ffffff);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.lang-btn:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--ide-accent, #0366d6);
}

.lang-btn:active {
  transform: translateY(1px);
}

.lang-code {
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  color: var(--ide-foreground, #24292e);
}

:global(.theme-dark) .lang-btn {
  background: var(--ide-card-background, #161b22);
  border-color: var(--ide-border, #30363d);
}

:global(.theme-dark) .lang-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--ide-accent, #58a6ff);
}

:global(.theme-dark) .lang-code {
  color: var(--ide-foreground, #e6edf3);
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 100px;
}

:deep(.el-dropdown-menu__item.active) {
  color: var(--ide-accent, #0366d6);
  font-weight: 500;
}

.lang-label {
  flex: 1;
}

.check-mark {
  margin-left: 8px;
  color: var(--ide-success, #28a745);
}
</style>
