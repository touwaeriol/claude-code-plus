<template>
  <div class="local-command-output" :class="outputType">
    <span class="icon">{{ outputType === 'stdout' ? '✓' : '✗' }}</span>
    <span class="command">{{ command }}</span>
  </div>
</template>

<script setup lang="ts">
interface Props {
  command: string
  outputType: 'stdout' | 'stderr'
}

defineProps<Props>()
</script>

<style scoped>
.local-command-output {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 13px;
  font-family: var(--font-mono);
  margin: 8px 16px;
}

.local-command-output.stdout {
  background: var(--theme-success-background, rgba(34, 197, 94, 0.15));
  color: var(--theme-success, #22c55e);
  border: 1px solid var(--theme-success-border, rgba(34, 197, 94, 0.3));
}

.local-command-output.stderr {
  background: var(--theme-error-background, rgba(239, 68, 68, 0.15));
  color: var(--theme-error, #ef4444);
  border: 1px solid var(--theme-error-border, rgba(239, 68, 68, 0.3));
}

.icon {
  font-size: 14px;
}

.command {
  font-weight: 500;
}
</style>
