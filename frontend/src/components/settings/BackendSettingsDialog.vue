<template>
  <div v-if="visible" class="settings-dialog-overlay" @click.self="handleCancel">
    <div class="settings-dialog">
      <!-- Header -->
      <div class="dialog-header">
        <span class="dialog-title">Backend Settings</span>
        <div class="dialog-actions">
          <button class="close-btn" @click="handleCancel" title="Close">×</button>
        </div>
      </div>

      <!-- Tab Navigation -->
      <div class="tab-nav">
        <button
          v-for="backend in backends"
          :key="backend"
          class="tab-btn"
          :class="{ active: activeTab === backend }"
          @click="activeTab = backend"
        >
          <span class="tab-icon">{{ getBackendIcon(backend) }}</span>
          <span class="tab-label">{{ getBackendDisplayName(backend) }}</span>
        </button>
      </div>

      <!-- Tab Content -->
      <div class="dialog-content">
        <!-- Claude Settings Tab -->
        <div v-show="activeTab === 'claude'" class="settings-tab">
          <div class="settings-section">
            <label class="settings-label">
              API Key
              <span class="label-hint">(Optional, uses environment variable if empty)</span>
            </label>
            <input
              v-model="localClaudeConfig.apiKey"
              type="password"
              class="settings-input"
              placeholder="sk-ant-..."
            />
          </div>

          <div class="settings-section">
            <label class="settings-label">Default Model</label>
            <select
              v-model="localClaudeConfig.modelId"
              class="settings-select"
            >
              <option
                v-for="model in claudeModels"
                :key="model.id"
                :value="model.id"
              >
                {{ model.displayName }}
              </option>
            </select>
            <p v-if="selectedClaudeModel" class="model-description">
              {{ selectedClaudeModel.description }}
            </p>
          </div>

          <div class="settings-section">
            <label class="settings-label">Default Thinking Configuration</label>
            <div class="thinking-controls">
              <div class="toggle-row">
                <label class="toggle-label">
                  <input
                    v-model="localClaudeConfig.thinkingEnabled"
                    type="checkbox"
                    class="toggle-checkbox"
                  />
                  <span class="toggle-slider"></span>
                  <span class="toggle-text">Enable Thinking</span>
                </label>
              </div>
              <div v-if="localClaudeConfig.thinkingEnabled" class="thinking-options">
                <label class="sub-label">Token Budget</label>
                <select
                  v-model.number="localClaudeConfig.thinkingTokenBudget"
                  class="settings-select"
                >
                  <option
                    v-for="preset in thinkingPresets"
                    :key="preset.id"
                    :value="preset.tokens"
                  >
                    {{ preset.label }}
                  </option>
                </select>
              </div>
            </div>
          </div>

          <div class="settings-section">
            <label class="toggle-label">
              <input
                v-model="localClaudeConfig.includePartialMessages"
                type="checkbox"
                class="toggle-checkbox"
              />
              <span class="toggle-slider"></span>
              <span class="toggle-text">Include Partial Messages</span>
            </label>
            <p class="setting-hint">
              Stream intermediate assistant messages during tool execution
            </p>
          </div>
        </div>

        <!-- Codex Settings Tab -->
        <div v-show="activeTab === 'codex'" class="settings-tab">
          <div class="settings-section">
            <label class="settings-label">Binary Path</label>
            <div class="input-with-button">
              <input
                v-model="localCodexConfig.binaryPath"
                type="text"
                class="settings-input"
                placeholder="/path/to/codex"
              />
              <button class="browse-btn" @click="handleBrowseBinary">Browse</button>
            </div>
            <p class="setting-hint">Path to the Codex binary executable</p>
          </div>

          <div class="settings-section">
            <label class="settings-label">Model Provider</label>
            <select
              v-model="localCodexConfig.modelProvider"
              class="settings-select"
            >
              <option value="openai">OpenAI</option>
              <option value="ollama">Ollama</option>
              <option value="anthropic">Anthropic</option>
              <option value="custom">Custom</option>
            </select>
          </div>

          <div class="settings-section">
            <label class="settings-label">Default Model</label>
            <select
              v-model="localCodexConfig.modelId"
              class="settings-select"
            >
              <option
                v-for="model in codexModels"
                :key="model.id"
                :value="model.id"
              >
                {{ model.displayName }}
              </option>
            </select>
            <p v-if="selectedCodexModel" class="model-description">
              {{ selectedCodexModel.description }}
            </p>
          </div>

          <div class="settings-section">
            <label class="settings-label">Default Sandbox Mode</label>
            <select
              v-model="localCodexConfig.sandboxMode"
              class="settings-select"
            >
              <option
                v-for="option in sandboxOptions"
                :key="option.id"
                :value="option.id"
              >
                {{ option.label }}
              </option>
            </select>
            <p v-if="selectedSandboxOption" class="setting-hint">
              {{ selectedSandboxOption.description }}
            </p>
          </div>

          <div class="settings-section">
            <label class="settings-label">Default Reasoning Effort</label>
            <select
              v-model="localCodexConfig.reasoningEffort"
              class="settings-select"
            >
              <option :value="null">Off</option>
              <option value="minimal">Minimal</option>
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
              <option value="xhigh">Extra High</option>
            </select>
          </div>

          <div class="settings-section">
            <label class="settings-label">Reasoning Summary Mode</label>
            <select
              v-model="localCodexConfig.reasoningSummary"
              class="settings-select"
            >
              <option value="auto">Auto</option>
              <option value="concise">Concise</option>
              <option value="detailed">Detailed</option>
              <option value="none">None</option>
            </select>
          </div>
        </div>
      </div>

      <!-- Footer Actions -->
      <div class="dialog-footer">
        <div v-if="validationError" class="validation-error">
          {{ validationError }}
        </div>
        <div class="footer-actions">
          <button class="btn btn-secondary" @click="handleCancel">
            Cancel
          </button>
          <button class="btn btn-primary" @click="handleSave" :disabled="!isValid">
            Save
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type {
  BackendType,
  ClaudeBackendConfig,
  CodexBackendConfig,
  SandboxMode,
} from '@/types/backend'
import {
  BackendTypes,
  DEFAULT_CLAUDE_CONFIG,
  DEFAULT_CODEX_CONFIG,
} from '@/types/backend'
import {
  getModels,
  getSandboxOptions,
  getBackendDisplayName,
} from '@/services/backendCapabilities'
import { getClaudeThinkingPresets } from '@/types/thinking'

interface Props {
  visible: boolean
  claudeConfig: ClaudeBackendConfig
  codexConfig: CodexBackendConfig
}

interface CodexConfigWithBinaryPath extends CodexBackendConfig {
  binaryPath: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save', claudeConfig: ClaudeBackendConfig, codexConfig: CodexBackendConfig): void
}>()

// Tab state
const activeTab = ref<BackendType>('claude')
const backends: BackendType[] = [BackendTypes.CLAUDE, BackendTypes.CODEX]

// Local config copies
const localClaudeConfig = ref<ClaudeBackendConfig>({ ...DEFAULT_CLAUDE_CONFIG })
const localCodexConfig = ref<CodexConfigWithBinaryPath>({
  ...DEFAULT_CODEX_CONFIG,
  binaryPath: '',
})

// Validation
const validationError = ref<string | null>(null)

// Watch for prop changes to update local config
watch(
  () => props.claudeConfig,
  (newConfig) => {
    localClaudeConfig.value = { ...newConfig }
  },
  { immediate: true }
)

watch(
  () => props.codexConfig,
  (newConfig) => {
    localCodexConfig.value = {
      ...newConfig,
      binaryPath: (newConfig as any).binaryPath || '',
    }
  },
  { immediate: true }
)

// Reset when dialog opens/closes
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      localClaudeConfig.value = { ...props.claudeConfig }
      localCodexConfig.value = {
        ...props.codexConfig,
        binaryPath: (props.codexConfig as any).binaryPath || '',
      }
      validationError.value = null
      activeTab.value = 'claude'
    }
  }
)

// Available options
const claudeModels = computed(() => getModels('claude'))
const codexModels = computed(() => getModels('codex'))
const thinkingPresets = computed(() => getClaudeThinkingPresets())
const sandboxOptions = computed(() => getSandboxOptions())

// Selected items
const selectedClaudeModel = computed(() =>
  claudeModels.value.find((m) => m.id === localClaudeConfig.value.modelId)
)
const selectedCodexModel = computed(() =>
  codexModels.value.find((m) => m.id === localCodexConfig.value.modelId)
)
const selectedSandboxOption = computed(() =>
  sandboxOptions.value.find((o) => o.id === localCodexConfig.value.sandboxMode)
)

// Validation
const isValid = computed(() => {
  // Claude validation
  if (!localClaudeConfig.value.modelId) {
    return false
  }

  // Codex validation
  if (!localCodexConfig.value.modelId) {
    return false
  }

  return true
})

// Icon mapping
function getBackendIcon(type: BackendType): string {
  return type === 'claude' ? '🤖' : '🔧'
}

// Actions
function handleCancel() {
  emit('close')
}

function handleSave() {
  // Validate
  if (!isValid.value) {
    validationError.value = 'Please fill in all required fields'
    return
  }

  validationError.value = null

  // Remove binaryPath from codexConfig before emitting (it's a UI-only field)
  const { binaryPath, ...codexConfigWithoutPath } = localCodexConfig.value

  emit('save', localClaudeConfig.value, codexConfigWithoutPath as CodexBackendConfig)
  emit('close')
}

function handleBrowseBinary() {
  // In a real implementation, this would open a file picker
  // For now, we'll just show an alert
  alert('File picker integration would be implemented here')
}
</script>

<style scoped>
.settings-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.settings-dialog {
  background: var(--theme-panel-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
}

.dialog-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--theme-foreground, #24292e);
}

.dialog-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: var(--theme-muted-foreground, #656d76);
  transition: background 0.15s;
  font-size: 20px;
  font-weight: 300;
}

.close-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
}

.tab-nav {
  display: flex;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
  padding: 0 16px;
  gap: 4px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--theme-muted-foreground, #656d76);
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
  position: relative;
  top: 1px;
}

.tab-btn:hover {
  color: var(--theme-foreground, #24292e);
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.03));
}

.tab-btn.active {
  color: var(--theme-accent, #0366d6);
  border-bottom-color: var(--theme-accent, #0366d6);
  font-weight: 500;
}

.tab-icon {
  font-size: 16px;
}

.tab-label {
  white-space: nowrap;
}

.dialog-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.settings-tab {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.settings-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.label-hint {
  font-size: 11px;
  font-weight: 400;
  color: var(--theme-muted-foreground, #656d76);
}

.sub-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--theme-muted-foreground, #656d76);
  margin-top: 8px;
}

.settings-input,
.settings-select {
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 6px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  transition: border-color 0.15s;
}

.settings-input:focus,
.settings-select:focus {
  outline: none;
  border-color: var(--theme-accent, #0366d6);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.settings-select {
  cursor: pointer;
}

.input-with-button {
  display: flex;
  gap: 8px;
}

.input-with-button .settings-input {
  flex: 1;
}

.browse-btn {
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 6px;
  background: var(--theme-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.browse-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--theme-border-hover, #adb5bd);
}

.browse-btn:active {
  transform: scale(0.98);
}

.model-description,
.setting-hint {
  font-size: 11px;
  color: var(--theme-muted-foreground, #656d76);
  margin: 0;
  line-height: 1.5;
}

.thinking-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toggle-row {
  display: flex;
  align-items: center;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.toggle-checkbox {
  display: none;
}

.toggle-slider {
  position: relative;
  width: 40px;
  height: 22px;
  background: var(--theme-muted-background, #d0d7de);
  border-radius: 11px;
  transition: background 0.2s;
  flex-shrink: 0;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle-checkbox:checked + .toggle-slider {
  background: var(--theme-accent, #0366d6);
}

.toggle-checkbox:checked + .toggle-slider::before {
  transform: translateX(18px);
}

.toggle-text {
  font-size: 12px;
  color: var(--theme-foreground, #24292e);
}

.thinking-options {
  margin-left: 50px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dialog-footer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-panel-background, #f6f8fa);
}

.validation-error {
  font-size: 12px;
  color: #dc3545;
  padding: 8px 12px;
  background: rgba(220, 53, 69, 0.1);
  border-radius: 6px;
  border-left: 3px solid #dc3545;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.btn-secondary {
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  border-color: var(--theme-border, #d0d7de);
}

.btn-secondary:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
}

.btn-primary {
  background: var(--theme-accent, #0366d6);
  color: #ffffff;
  border-color: var(--theme-accent, #0366d6);
}

.btn-primary:hover:not(:disabled) {
  background: var(--theme-accent-hover, #0256c2);
  border-color: var(--theme-accent-hover, #0256c2);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}
</style>
