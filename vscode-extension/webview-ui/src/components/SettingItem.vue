<template>
  <div class="setting-item" :class="{ 'checkbox-item': type === 'checkbox' }">
    <!-- 下拉选择 -->
    <template v-if="type === 'select'">
      <label v-if="label" class="setting-label">{{ label }}</label>
      <el-select
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :placeholder="placeholder"
        :disabled="disabled"
        style="width: 100%; max-width: 400px;"
      >
        <el-option
          v-for="option in options"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <p v-if="description" class="setting-description">{{ description }}</p>
    </template>
    
    <!-- 数字输入 -->
    <template v-else-if="type === 'number'">
      <label v-if="label" class="setting-label">{{ label }}</label>
      <el-input-number
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :min="min"
        :max="max"
        :step="step"
        :disabled="disabled"
        controls-position="right"
        style="width: 150px;"
      />
      <p v-if="description" class="setting-description">{{ description }}</p>
    </template>
    
    <!-- 复选框 -->
    <template v-else-if="type === 'checkbox'">
      <el-checkbox
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :disabled="disabled"
      >
        {{ label }}
      </el-checkbox>
      <p v-if="description" class="setting-description checkbox-desc">{{ description }}</p>
    </template>
    
    <!-- 文件路径输入 -->
    <template v-else-if="type === 'path'">
      <label v-if="label" class="setting-label">{{ label }}</label>
      <div class="path-input">
        <el-input
          :model-value="modelValue"
          @update:model-value="$emit('update:modelValue', $event)"
          :placeholder="placeholder"
          :disabled="disabled"
        />
        <el-button @click="$emit('browse')" :disabled="disabled">Browse...</el-button>
      </div>
      <p v-if="description" class="setting-description">{{ description }}</p>
    </template>
    
    <!-- 多行文本 -->
    <template v-else-if="type === 'textarea'">
      <label v-if="label" class="setting-label">{{ label }}</label>
      <el-input
        type="textarea"
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :placeholder="placeholder"
        :rows="rows"
        :disabled="disabled"
        style="font-family: monospace;"
      />
      <p v-if="description" class="setting-description">{{ description }}</p>
    </template>
    
    <!-- 文本输入 -->
    <template v-else>
      <label v-if="label" class="setting-label">{{ label }}</label>
      <el-input
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        :placeholder="placeholder"
        :disabled="disabled"
        style="max-width: 400px;"
      />
      <p v-if="description" class="setting-description">{{ description }}</p>
    </template>
  </div>
</template>

<script setup lang="ts">
interface Option {
  label: string
  value: string | number | boolean
}

interface Props {
  label?: string
  description?: string
  type?: 'text' | 'number' | 'select' | 'checkbox' | 'path' | 'textarea'
  modelValue?: any
  options?: Option[]
  min?: number
  max?: number
  step?: number
  placeholder?: string
  disabled?: boolean
  rows?: number
}

withDefaults(defineProps<Props>(), {
  type: 'text',
  disabled: false,
  rows: 4
})

defineEmits<{
  'update:modelValue': [value: any]
  'browse': []
}>()
</script>

<style scoped>
.setting-item {
  margin-bottom: 16px;
}

.setting-item:last-child {
  margin-bottom: 0;
}

.setting-label {
  display: block;
  font-weight: 500;
  margin-bottom: 6px;
  color: var(--vscode-foreground, #cccccc);
}

.setting-description {
  font-size: 12px;
  color: var(--vscode-textLink-foreground, #3794ff);
  margin-top: 4px;
  line-height: 1.4;
}

.checkbox-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.checkbox-desc {
  margin-left: 24px;
}

.path-input {
  display: flex;
  gap: 8px;
  max-width: 500px;
}

.path-input .el-input {
  flex: 1;
}
</style>
