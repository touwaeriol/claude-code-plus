<template>
  <div class="thinking-config-panel">
    <!-- Claude Thinking Configuration -->
    <div v-if="backendType === 'claude'" class="thinking-config-claude">
      <!-- Enable/Disable Toggle -->
      <div class="config-row">
        <label class="config-label">
          <span>启用思考</span>
          <input
            type="checkbox"
            :checked="claudeConfig.enabled"
            @change="updateClaudeEnabled($event)"
            class="toggle-checkbox"
          />
        </label>
      </div>

      <!-- Token Budget Section (only when enabled) -->
      <div v-if="claudeConfig.enabled" class="config-section">
        <!-- Preset Dropdown -->
        <div class="config-row">
          <label class="config-label">
            <span>预设级别</span>
            <select
              :value="currentClaudePreset.id"
              @change="updateClaudePreset($event)"
              class="config-select"
            >
              <option
                v-for="preset in claudePresets"
                :key="preset.id"
                :value="preset.id"
              >
                {{ preset.label }}
              </option>
            </select>
          </label>
        </div>

        <!-- Token Budget Slider -->
        <div class="config-row">
          <label class="config-label">
            <span>Token 预算: {{ formatTokens(claudeConfig.tokenBudget) }}</span>
          </label>
          <input
            type="range"
            :value="claudeConfig.tokenBudget"
            @input="updateClaudeTokenBudget($event)"
            min="0"
            max="32768"
            step="1024"
            class="token-slider"
          />
        </div>

        <!-- Token Budget Input -->
        <div class="config-row">
          <label class="config-label">
            <span>自定义 Token 数</span>
            <input
              type="number"
              :value="claudeConfig.tokenBudget"
              @input="updateClaudeTokenBudgetInput($event)"
              min="0"
              max="65536"
              step="1024"
              class="token-input"
            />
          </label>
        </div>
      </div>

      <!-- Validation Error -->
      <div v-if="validationError" class="validation-error">
        {{ validationError }}
      </div>
    </div>

    <!-- Codex Thinking Configuration -->
    <div v-else-if="backendType === 'codex'" class="thinking-config-codex">
      <!-- Effort Level Dropdown -->
      <div class="config-row">
        <label class="config-label">
          <span>推理强度</span>
          <select
            :value="codexConfig.effort || 'OFF'"
            @change="updateCodexEffort($event)"
            class="config-select"
          >
            <option
              v-for="level in codexEffortLevels"
              :key="level.id || 'OFF'"
              :value="level.id || 'OFF'"
            >
              {{ level.label }}
            </option>
          </select>
        </label>
        <div v-if="selectedCodexEffort" class="config-description">
          {{ selectedCodexEffort.description }}
        </div>
      </div>

      <!-- Summary Mode Dropdown (only when reasoning is enabled) -->
      <div v-if="codexConfig.effort !== null" class="config-row">
        <label class="config-label">
          <span>摘要模式</span>
          <select
            :value="codexConfig.summary"
            @change="updateCodexSummary($event)"
            class="config-select"
          >
            <option
              v-for="mode in codexSummaryModes"
              :key="mode.id"
              :value="mode.id"
            >
              {{ mode.label }}
            </option>
          </select>
        </label>
        <div v-if="selectedCodexSummary" class="config-description">
          {{ selectedCodexSummary.description }}
        </div>
      </div>

      <!-- Validation Error -->
      <div v-if="validationError" class="validation-error">
        {{ validationError }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BackendType } from '../../types/backend'
import type {
  ThinkingConfig,
  ClaudeThinkingConfig,
  CodexThinkingConfig,
} from '../../types/thinking'
import {
  CLAUDE_THINKING_PRESETS,
  CODEX_EFFORT_LEVELS,
  getClaudeThinkingPresets,
  getCodexEffortLevels,
  getCodexSummaryModes,
  findClaudePresetByTokens,
  isClaudeThinking,
  isCodexThinking,
} from '../../types/thinking'

// Props
const props = defineProps<{
  backendType: BackendType
  modelValue: ThinkingConfig
}>()

// Emits
const emit = defineEmits<{
  'update:modelValue': [value: ThinkingConfig]
}>()

// Validation error state
const validationError = ref<string | null>(null)

// Claude Config
const claudeConfig = computed<ClaudeThinkingConfig>(() => {
  if (isClaudeThinking(props.modelValue)) {
    return props.modelValue
  }
  // Fallback to default
  return {
    type: 'claude',
    enabled: true,
    tokenBudget: 8096,
  }
})

// Codex Config
const codexConfig = computed<CodexThinkingConfig>(() => {
  if (isCodexThinking(props.modelValue)) {
    return props.modelValue
  }
  // Fallback to default
  return {
    type: 'codex',
    effort: 'medium',
    summary: 'auto',
  }
})

// Claude Presets
const claudePresets = getClaudeThinkingPresets()
const currentClaudePreset = computed(() => {
  return findClaudePresetByTokens(claudeConfig.value.tokenBudget)
})

// Codex Options
const codexEffortLevels = getCodexEffortLevels()
const codexSummaryModes = getCodexSummaryModes()

const selectedCodexEffort = computed(() => {
  const effortValue = codexConfig.value.effort || null
  return codexEffortLevels.find((level) => level.id === effortValue)
})

const selectedCodexSummary = computed(() => {
  return codexSummaryModes.find((mode) => mode.id === codexConfig.value.summary)
})

// Format tokens for display
function formatTokens(tokens: number): string {
  if (tokens === 0) return '关闭'
  if (tokens >= 1024) {
    return `${Math.round(tokens / 1024)}K`
  }
  return `${tokens}`
}

// Update handlers for Claude
function updateClaudeEnabled(event: Event) {
  const enabled = (event.target as HTMLInputElement).checked
  const updated: ClaudeThinkingConfig = {
    ...claudeConfig.value,
    enabled,
  }
  emit('update:modelValue', updated)
  validateConfig(updated)
}

function updateClaudePreset(event: Event) {
  const presetId = (event.target as HTMLSelectElement).value
  const preset = claudePresets.find((p) => p.id === presetId)
  if (preset) {
    const updated: ClaudeThinkingConfig = {
      ...claudeConfig.value,
      tokenBudget: preset.tokens,
      enabled: preset.tokens > 0,
    }
    emit('update:modelValue', updated)
    validateConfig(updated)
  }
}

function updateClaudeTokenBudget(event: Event) {
  const tokenBudget = parseInt((event.target as HTMLInputElement).value, 10)
  const updated: ClaudeThinkingConfig = {
    ...claudeConfig.value,
    tokenBudget,
  }
  emit('update:modelValue', updated)
  validateConfig(updated)
}

function updateClaudeTokenBudgetInput(event: Event) {
  const tokenBudget = parseInt((event.target as HTMLInputElement).value, 10)
  if (!isNaN(tokenBudget)) {
    const updated: ClaudeThinkingConfig = {
      ...claudeConfig.value,
      tokenBudget,
    }
    emit('update:modelValue', updated)
    validateConfig(updated)
  }
}

// Update handlers for Codex
function updateCodexEffort(event: Event) {
  const effortValue = (event.target as HTMLSelectElement).value
  const effort = effortValue === 'OFF' ? null : (effortValue as any)
  const updated: CodexThinkingConfig = {
    ...codexConfig.value,
    effort,
  }
  emit('update:modelValue', updated)
  validateConfig(updated)
}

function updateCodexSummary(event: Event) {
  const summary = (event.target as HTMLSelectElement).value as any
  const updated: CodexThinkingConfig = {
    ...codexConfig.value,
    summary,
  }
  emit('update:modelValue', updated)
  validateConfig(updated)
}

// Validation
function validateConfig(config: ThinkingConfig) {
  validationError.value = null

  if (isClaudeThinking(config)) {
    if (config.enabled && config.tokenBudget < 0) {
      validationError.value = 'Token 预算不能为负数'
    } else if (config.tokenBudget > 65536) {
      validationError.value = 'Token 预算不能超过 65536'
    }
  }
}

// Watch for external changes
watch(
  () => props.modelValue,
  (newValue) => {
    validateConfig(newValue)
  },
  { immediate: true }
)
</script>

<style scoped>
.thinking-config-panel {
  padding: 16px;
  background: var(--vscode-editor-background, #1e1e1e);
  color: var(--vscode-editor-foreground, #d4d4d4);
  border-radius: 4px;
}

.thinking-config-claude,
.thinking-config-codex {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 16px;
  border-left: 2px solid var(--vscode-panel-border, #3c3c3c);
}

.config-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  gap: 12px;
}

.config-label span {
  flex: 1;
}

.toggle-checkbox {
  width: 40px;
  height: 20px;
  cursor: pointer;
}

.config-select {
  padding: 6px 8px;
  background: var(--vscode-input-background, #3c3c3c);
  color: var(--vscode-input-foreground, #d4d4d4);
  border: 1px solid var(--vscode-input-border, #3c3c3c);
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  min-width: 200px;
}

.config-select:hover {
  background: var(--vscode-input-hoverBackground, #4c4c4c);
}

.config-select:focus {
  outline: 1px solid var(--vscode-focusBorder, #007acc);
  outline-offset: -1px;
}

.token-slider {
  width: 100%;
  height: 4px;
  background: var(--vscode-input-background, #3c3c3c);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}

.token-slider::-webkit-slider-thumb {
  appearance: none;
  width: 16px;
  height: 16px;
  background: var(--vscode-button-background, #0e639c);
  border-radius: 50%;
  cursor: pointer;
}

.token-slider::-webkit-slider-thumb:hover {
  background: var(--vscode-button-hoverBackground, #1177bb);
}

.token-slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  background: var(--vscode-button-background, #0e639c);
  border-radius: 50%;
  cursor: pointer;
  border: none;
}

.token-slider::-moz-range-thumb:hover {
  background: var(--vscode-button-hoverBackground, #1177bb);
}

.token-input {
  padding: 6px 8px;
  background: var(--vscode-input-background, #3c3c3c);
  color: var(--vscode-input-foreground, #d4d4d4);
  border: 1px solid var(--vscode-input-border, #3c3c3c);
  border-radius: 4px;
  font-size: 13px;
  min-width: 120px;
}

.token-input:hover {
  background: var(--vscode-input-hoverBackground, #4c4c4c);
}

.token-input:focus {
  outline: 1px solid var(--vscode-focusBorder, #007acc);
  outline-offset: -1px;
}

.config-description {
  font-size: 12px;
  color: var(--vscode-descriptionForeground, #858585);
  padding-left: 4px;
  font-style: italic;
}

.validation-error {
  padding: 8px 12px;
  background: var(--vscode-inputValidation-errorBackground, #5a1d1d);
  color: var(--vscode-inputValidation-errorForeground, #f48771);
  border: 1px solid var(--vscode-inputValidation-errorBorder, #be1100);
  border-radius: 4px;
  font-size: 12px;
}
</style>
