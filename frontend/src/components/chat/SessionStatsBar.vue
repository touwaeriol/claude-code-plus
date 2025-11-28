<template>
  <div v-if="hasStats" class="session-stats-bar">
    <span v-if="stats.filesChanged > 0" class="stat-item">
      <span class="stat-icon">📝</span>
      <span class="stat-value">{{ stats.filesChanged }}</span>
      <span class="stat-label">Files Changed</span>
    </span>
    <span v-if="stats.filesRead > 0" class="stat-item">
      <span class="stat-icon">📄</span>
      <span class="stat-value">{{ stats.filesRead }}</span>
      <span class="stat-label">Files Read</span>
    </span>
    <span v-if="stats.commandsRun > 0" class="stat-item">
      <span class="stat-icon">💻</span>
      <span class="stat-value">{{ stats.commandsRun }}</span>
      <span class="stat-label">Commands</span>
    </span>
    <span v-if="stats.searchesPerformed > 0" class="stat-item">
      <span class="stat-icon">🔍</span>
      <span class="stat-value">{{ stats.searchesPerformed }}</span>
      <span class="stat-label">Searches</span>
    </span>
    <span class="stat-item stat-total">
      <span class="stat-icon">🔧</span>
      <span class="stat-value">{{ stats.toolsUsed }}</span>
      <span class="stat-label">Tools Used</span>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolUsageStats } from '@/utils/toolStatistics'
import { hasToolStats } from '@/utils/toolStatistics'

interface Props {
  stats: ToolUsageStats
}

const props = defineProps<Props>()

const hasStats = computed(() => hasToolStats(props.stats))
</script>

<style scoped>
.session-stats-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 16px;
  background: var(--ide-panel-background, rgba(0, 0, 0, 0.02));
  border-top: 1px solid var(--ide-border, rgba(0, 0, 0, 0.08));
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.6));
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-icon {
  font-size: 14px;
}

.stat-value {
  font-weight: 600;
  color: var(--ide-foreground, #1a1a1a);
}

.stat-label {
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.5));
}

.stat-total {
  margin-left: auto;
}

/* 暗色主题 */
.theme-dark .session-stats-bar {
  background: var(--ide-panel-background, rgba(255, 255, 255, 0.02));
  border-top-color: var(--ide-border, rgba(255, 255, 255, 0.08));
}

.theme-dark .stat-value {
  color: var(--ide-foreground, #e0e0e0);
}

.theme-dark .stat-label {
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.5));
}
</style>
