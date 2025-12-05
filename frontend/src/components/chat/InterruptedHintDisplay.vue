<template>
  <div class="interrupted-hint">
    <span class="hint-text">{{ message }}</span>
  </div>
</template>

<script setup lang="ts">
interface Props {
  message: string
}

defineProps<Props>()
</script>

<style scoped>
.interrupted-hint {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  margin: 4px 0;
  /* 使用主题错误色，兼容 Element Plus/自定义主题/兜底红色 */
  color: var(--el-color-danger, var(--theme-error, #dc3545));
  font-size: 13px;
  font-family: monospace;
  /* 右侧对齐：收缩为内容宽度并靠右 */
  width: fit-content;
  margin-left: auto;
}

.hint-text {
  font-weight: 500;
}
</style>
