<template>
  <div class="codex-toolbar">
    <!-- 模型选择器 -->
    <el-select
      v-model="localModel"
      class="cursor-selector model-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleModelChange"
    >
      <el-option
        v-for="model in codexModels"
        :key="model.id"
        :value="model.id"
        :label="model.displayName"
      >
        <span class="model-option-label">{{ model.displayName }}</span>
      </el-option>
    </el-select>

    <!-- 推理深度选择器（始终显示） -->
    <el-select
      v-model="localReasoningEffort"
      class="cursor-selector reasoning-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleReasoningChange"
    >
      <template #prefix>
        <span class="mode-prefix-icon">●</span>
      </template>
      <el-option
        v-for="option in reasoningOptions"
        :key="option.value"
        :value="option.value"
        :label="option.shortLabel"
      >
        <span class="mode-option-label">
          <span class="mode-icon">●</span>
          <span>{{ option.label }}</span>
        </span>
      </el-option>
    </el-select>

    <!-- 审批模式选择器 -->
    <el-select
      v-model="localApprovalMode"
      class="cursor-selector approval-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown mode-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleApprovalChange"
    >
      <template #prefix>
        <span class="mode-prefix-icon">{{ getApprovalModeIcon(localApprovalMode) }}</span>
      </template>
      <el-option
        v-for="option in approvalOptions"
        :key="option.value"
        :value="option.value"
        :label="option.label"
      >
        <span class="mode-option-label">
          <span class="mode-icon">{{ option.icon }}</span>
          <span>{{ option.label }}</span>
        </span>
      </el-option>
    </el-select>

    <!-- 沙盒模式选择器 -->
    <el-select
      v-model="localSandboxMode"
      class="cursor-selector sandbox-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown mode-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleSandboxChange"
    >
      <template #prefix>
        <span class="mode-prefix-icon">{{ getSandboxModeIcon(localSandboxMode) }}</span>
      </template>
      <el-option
        v-for="option in sandboxOptions"
        :key="option.value"
        :value="option.value"
        :label="option.label"
      >
        <span class="mode-option-label">
          <span class="mode-icon">{{ option.icon }}</span>
          <span>{{ option.label }}</span>
        </span>
      </el-option>
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  CODEX_MODELS,
  APPROVAL_MODE_OPTIONS,
  SANDBOX_MODE_OPTIONS,
  REASONING_EFFORT_OPTIONS,
  getApprovalModeIcon,
  getSandboxModeIcon,
  type CodexApprovalMode,
  type CodexSandboxMode,
  type CodexReasoningEffort,
} from '@/types/codex'

interface Props {
  model: string
  approvalMode: CodexApprovalMode
  sandboxMode: CodexSandboxMode
  reasoningEffort: CodexReasoningEffort
  disabled?: boolean
}

interface Emits {
  (e: 'update:model', value: string): void
  (e: 'update:approvalMode', value: CodexApprovalMode): void
  (e: 'update:sandboxMode', value: CodexSandboxMode): void
  (e: 'update:reasoningEffort', value: CodexReasoningEffort): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<Emits>()

// Local state
const localModel = ref(props.model)
const localApprovalMode = ref(props.approvalMode)
const localSandboxMode = ref(props.sandboxMode)
const localReasoningEffort = ref(props.reasoningEffort)

// Options
const codexModels = CODEX_MODELS
const approvalOptions = APPROVAL_MODE_OPTIONS
const sandboxOptions = SANDBOX_MODE_OPTIONS
const reasoningOptions = REASONING_EFFORT_OPTIONS

// Watch props
watch(() => props.model, (val) => { localModel.value = val })
watch(() => props.approvalMode, (val) => { localApprovalMode.value = val })
watch(() => props.sandboxMode, (val) => { localSandboxMode.value = val })
watch(() => props.reasoningEffort, (val) => { localReasoningEffort.value = val })

// Handlers
function handleModelChange(value: string) {
  emit('update:model', value)
}

function handleApprovalChange(value: CodexApprovalMode) {
  emit('update:approvalMode', value)
}

function handleSandboxChange(value: CodexSandboxMode) {
  emit('update:sandboxMode', value)
}

function handleReasoningChange(value: CodexReasoningEffort) {
  emit('update:reasoningEffort', value)
}
</script>

<style scoped>
.codex-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* ========== Cursor 风格选择器 - 与 ChatInput.vue 保持一致 ========== */
.cursor-selector {
  font-size: 11px;
}

.cursor-selector.model-selector {
  width: auto;
  min-width: 100px;
}

.cursor-selector.reasoning-selector {
  width: auto;
  min-width: 60px;
}

.cursor-selector.approval-selector {
  width: auto;
  min-width: 90px;
}

.cursor-selector.sandbox-selector {
  width: auto;
  min-width: 90px;
}

/* 移除边框和背景，使用纯文字样式（与 ChatInput 一致） */
.cursor-selector :deep(.el-select__wrapper) {
  padding: 2px 4px;
  border: none !important;
  border-radius: 4px;
  background: transparent !important;
  box-shadow: none !important;
  min-height: 20px;
  gap: 1px;
}

.cursor-selector :deep(.el-select__wrapper):hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05)) !important;
}

.cursor-selector :deep(.el-select__wrapper.is-focused) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05)) !important;
  box-shadow: none !important;
}

.cursor-selector :deep(.el-select__placeholder) {
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
}

.cursor-selector :deep(.el-select__selection) {
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
}

.cursor-selector :deep(.el-select__suffix) {
  color: var(--theme-secondary-foreground, #9ca3af);
  margin-left: 0;
}

.cursor-selector :deep(.el-select__suffix .el-icon) {
  font-size: 12px;
}

.cursor-selector.is-disabled :deep(.el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 前缀图标样式 */
.mode-prefix-icon {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  margin-right: 1px;
}

/* 推理选择器前缀图标颜色 - 使用主题强调色 */
.reasoning-selector .mode-prefix-icon {
  color: var(--theme-accent, #3b82f6);
}

/* ========== 模式选择器下拉选项样式 ========== */
.model-option-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.mode-option-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mode-option-label .mode-icon {
  font-size: 14px;
  width: 16px;
  text-align: center;
  color: var(--theme-secondary-foreground, #6a737d);
}
</style>
