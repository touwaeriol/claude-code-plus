<template>
  <div class="compacting-card">
    <div class="spinner"></div>
    <span class="text">{{ $t('compact.compacting') }}</span>
  </div>
</template>

<script setup lang="ts">
// CompactingCard 组件
// 显示压缩进行中的动画状态
</script>

<style scoped>
.compacting-card {
  margin: 12px 16px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
  display: flex;
  align-items: center;
  gap: 12px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.8;
  }
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.text {
  font-size: 14px;
  font-weight: 500;
}
</style>
