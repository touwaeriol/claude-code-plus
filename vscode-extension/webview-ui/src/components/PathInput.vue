<template>
  <div class="path-input-container">
    <label v-if="label" class="setting-label">{{ label }}</label>
    <div class="path-input">
      <el-input
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :placeholder="displayPlaceholder"
        :disabled="disabled"
      />
      <el-button @click="$emit('browse')" :disabled="disabled" type="primary">
        Browse...
      </el-button>
    </div>
    <p v-if="description" class="setting-description">{{ description }}</p>
    <!-- 检测状态显示 -->
    <p v-if="detecting" class="detecting-text">
      <el-icon class="is-loading"><Loading /></el-icon>
      Detecting...
    </p>
    <p v-else-if="detectedInfo" class="detected-text">
      <el-icon><CircleCheck /></el-icon>
      {{ detectedInfo }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading, CircleCheck } from '@element-plus/icons-vue'

interface Props {
  label?: string
  description?: string
  modelValue?: string
  placeholder?: string
  disabled?: boolean
  detecting?: boolean
  detectedPath?: string
  detectedVersion?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  detecting: false
})

defineEmits<{
  'update:modelValue': [value: string]
  'browse': []
}>()

const displayPlaceholder = computed(() => {
  if (props.modelValue) return ''
  return props.placeholder || 'Auto-detect from system PATH'
})

const detectedInfo = computed(() => {
  if (!props.detectedPath) return ''
  if (props.detectedVersion) {
    return `${props.detectedPath} (${props.detectedVersion})`
  }
  return props.detectedPath
})
</script>

<style scoped>
.path-input-container {
  margin-bottom: 16px;
}

.path-input-container:last-child {
  margin-bottom: 0;
}

.setting-label {
  display: block;
  font-weight: 500;
  margin-bottom: 6px;
  color: var(--vscode-foreground, #cccccc);
}

.path-input {
  display: flex;
  gap: 8px;
  max-width: 600px;
}

.path-input .el-input {
  flex: 1;
}

.setting-description {
  font-size: 12px;
  color: var(--vscode-textLink-foreground, #3794ff);
  margin-top: 4px;
  line-height: 1.4;
}

.detecting-text {
  font-size: 12px;
  color: var(--vscode-descriptionForeground, #8b8b8b);
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.detected-text {
  font-size: 12px;
  color: var(--vscode-testing-iconPassed, #4caf50);
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.is-loading {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
