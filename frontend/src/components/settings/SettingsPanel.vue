<template>
  <div
    v-if="show"
    class="settings-panel-overlay"
    @click.self="close"
  >
    <div
      class="settings-panel"
    >
      <!-- 头部 -->
      <div class="panel-header">
        <h2>设置</h2>
        <el-button
          class="btn-close"
          title="关闭"
          text
          circle
          @click="close"
        >
          ✕
        </el-button>
      </div>

      <!-- 内容 -->
      <div class="panel-content">
        <!-- 模型选择 -->
        <div class="setting-section">
          <h3 class="section-title">
            模型配置
          </h3>

          <div class="setting-item">
            <label class="setting-label">AI 模型</label>
            <el-select
              v-model="localSettings.model"
              class="setting-select"
              placement="top-start"
              :teleported="false"
              :popper-options="{
                strategy: 'fixed',
                modifiers: [
                  {
                    name: 'flip',
                    options: {
                      fallbackPlacements: ['top-start', 'top'],
                    }
                  }
                ]
              }"
              @change="handleModelChange"
            >
              <el-option
                v-for="(label, model) in MODEL_LABELS"
                :key="model"
                :value="model"
                :label="label"
              />
            </el-select>
            <p class="setting-description">
              选择使用的 Claude AI 模型。Sonnet 提供最佳性价比,Opus 提供最强性能。
            </p>
          </div>
        </div>

        <!-- 权限模式 -->
        <div class="setting-section">
          <h3 class="section-title">
            权限控制
          </h3>

          <div class="setting-item">
            <label class="setting-label">权限模式</label>
            <el-select
              v-model="localSettings.permissionMode"
              class="setting-select"
              placement="top-start"
              :teleported="false"
              :popper-options="{
                strategy: 'fixed',
                modifiers: [
                  {
                    name: 'flip',
                    options: {
                      fallbackPlacements: ['top-start', 'top'],
                    }
                  }
                ]
              }"
              @change="handlePermissionModeChange"
            >
              <el-option
                v-for="(label, mode) in PERMISSION_MODE_LABELS"
                :key="mode"
                :value="mode"
                :label="label"
              />
            </el-select>
            <p class="setting-description">
              {{ PERMISSION_MODE_DESCRIPTIONS[localSettings.permissionMode] }}
            </p>
          </div>
        </div>

        <!-- 会话控制 -->
        <div class="setting-section">
          <h3 class="section-title">
            会话控制
          </h3>

          <div class="setting-item">
            <label class="setting-label">系统提示词</label>
            <el-input
              v-model="localSettings.systemPrompt"
              class="setting-input"
              type="textarea"
              :rows="3"
              placeholder="输入自定义系统提示词（可选，留空使用默认）"
              @blur="handleSystemPromptChange"
            />
            <p class="setting-description">
              自定义系统提示词，用于控制 Claude 的行为和角色。留空使用默认的 claude_code 提示词。
            </p>
          </div>

          <div class="setting-item">
            <label class="setting-label">最大对话轮次</label>
            <el-input-number
              v-model="localSettings.maxTurns"
              class="setting-input"
              :min="1"
              :max="100"
              placeholder="不限制"
              controls-position="right"
              @blur="handleMaxTurnsChange"
            />
            <p class="setting-description">
              限制单次对话的最大轮次,防止无限循环。留空表示不限制。
            </p>
          </div>

          <div class="setting-item">
            <el-checkbox
              v-model="localSettings.continueConversation"
              class="setting-checkbox"
              @change="handleContinueConversationChange"
            >
              继续上次对话
            </el-checkbox>
            <p class="setting-description">
              新建会话时自动加载上次对话的上下文。
            </p>
          </div>
        </div>

        <!-- 高级选项 -->
        <div class="setting-section">
          <h3 class="section-title">
            高级选项
          </h3>

          <div class="setting-item">
            <el-checkbox
              v-model="localSettings.thinkingEnabled"
              class="setting-checkbox"
              @change="handleThinkingEnabledChange"
            >
              启用扩展思考
            </el-checkbox>
            <p class="setting-description">
              开启后,服务端会根据 Claude settings 中的 MAX_THINKING_TOKENS 或默认值 1024 分配思考令牌预算;关闭则完全禁用思考过程。
            </p>
          </div>

          <div class="setting-item">
            <label class="setting-label">最大生成令牌数</label>
            <el-input-number
              v-model="localSettings.maxTokens"
              class="setting-input"
              :min="100"
              :max="8000"
              placeholder="默认"
              controls-position="right"
              @blur="handleMaxTokensChange"
            />
            <p class="setting-description">
              限制模型单次响应的最大令牌数。留空使用模型默认值。
            </p>
          </div>

          <div class="setting-item">
            <label class="setting-label">思考令牌数</label>
            <el-input-number
              v-model="localSettings.maxThinkingTokens"
              class="setting-input"
              :min="1000"
              :max="16000"
              controls-position="right"
              @blur="handleMaxThinkingTokensChange"
            />
            <p class="setting-description">
              设置模型的思考令牌预算。默认 8096。
            </p>
          </div>

          <div class="setting-item">
            <label class="setting-label">温度 (Temperature)</label>
            <el-input-number
              v-model="localSettings.temperature"
              class="setting-input"
              :min="0"
              :max="1"
              :step="0.1"
              :precision="1"
              placeholder="默认"
              controls-position="right"
              @blur="handleTemperatureChange"
            />
            <p class="setting-description">
              控制模型的创造性。0 = 确定性, 1 = 高创造性。留空使用默认值。
            </p>
          </div>

          <div class="setting-item">
            <el-checkbox
              v-model="localSettings.verbose"
              class="setting-checkbox"
              @change="handleVerboseChange"
            >
              详细日志模式
            </el-checkbox>
            <p class="setting-description">
              输出详细的调试日志,用于问题排查。
            </p>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="panel-footer">
        <el-button
          class="btn-secondary"
          @click="resetSettings"
        >
          重置为默认
        </el-button>
        <div class="footer-actions">
          <el-button
            class="btn-secondary"
            @click="close"
          >
            取消
          </el-button>
          <el-button
            class="btn-primary"
            type="primary"
            @click="saveAndClose"
          >
            保存
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import {
  MODEL_LABELS,
  PERMISSION_MODE_LABELS,
  PERMISSION_MODE_DESCRIPTIONS,
  type Settings
} from '@/types/settings'

interface Props {
  show: boolean
}

interface Emits {
  (e: 'close'): void
  (e: 'save', settings: Settings): void
}

defineProps<Props>()

const emit = defineEmits<Emits>()
const settingsStore = useSettingsStore()

// 本地设置副本(用于编辑,不立即保存)
const localSettings = ref<Settings>({ ...settingsStore.settings })

// 监听 store 设置变化,同步到本地副本
watch(() => settingsStore.settings, (newSettings) => {
  localSettings.value = { ...newSettings }
}, { deep: true })

// 自动保存的处理函数
async function handleModelChange() {
  await settingsStore.updateModel(localSettings.value.model)
}

async function handlePermissionModeChange() {
  await settingsStore.updatePermissionMode(localSettings.value.permissionMode)
}

async function handleSystemPromptChange() {
  await settingsStore.saveSettings({
    systemPrompt: localSettings.value.systemPrompt
  })
}

async function handleMaxTurnsChange() {
  await settingsStore.updateMaxTurns(localSettings.value.maxTurns)
}

async function handleContinueConversationChange() {
  await settingsStore.saveSettings({
    continueConversation: localSettings.value.continueConversation
  })
}

async function handleMaxTokensChange() {
  await settingsStore.saveSettings({
    maxTokens: localSettings.value.maxTokens
  })
}

async function handleThinkingEnabledChange() {
  await settingsStore.saveSettings({
    thinkingEnabled: localSettings.value.thinkingEnabled
  })
}

async function handleMaxThinkingTokensChange() {
  await settingsStore.saveSettings({
    maxThinkingTokens: localSettings.value.maxThinkingTokens
  })
}

async function handleTemperatureChange() {
  await settingsStore.saveSettings({
    temperature: localSettings.value.temperature
  })
}

async function handleVerboseChange() {
  await settingsStore.saveSettings({
    verbose: localSettings.value.verbose
  })
}

async function resetSettings() {
  if (confirm('确定要重置所有设置为默认值吗?')) {
    await settingsStore.resetToDefaults()
  }
}

function close() {
  emit('close')
}

async function saveAndClose() {
  await settingsStore.saveSettings(localSettings.value)
  emit('save', localSettings.value)
  close()
}
</script>

<style scoped>
.settings-panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.settings-panel {
  background: var(--theme-background);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.3s;
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--theme-border);
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-foreground);
}

.btn-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  font-size: 18px;
  color: var(--theme-foreground);
  transition: background 0.2s;
}

.btn-close:hover {
  background: rgba(0, 0, 0, 0.1);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.setting-section {
  margin-bottom: 32px;
}

.setting-section:last-child {
  margin-bottom: 0;
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-accent);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.setting-item {
  margin-bottom: 20px;
}

.setting-item:last-child {
  margin-bottom: 0;
}

.setting-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-foreground);
}

.setting-select,
.setting-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--theme-input-border);
  border-radius: 4px;
  background: var(--theme-input-background);
  color: var(--theme-input-foreground);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.setting-select:focus,
.setting-input:focus {
  border-color: var(--theme-accent);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.setting-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.setting-checkbox input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
}

.setting-checkbox span {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-foreground);
}

.setting-description {
  margin: 8px 0 0 0;
  font-size: 12px;
  color: var(--theme-foreground);
  opacity: 0.7;
  line-height: 1.5;
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-top: 1px solid var(--theme-border);
  background: var(--theme-panel-background);
}

.footer-actions {
  display: flex;
  gap: 12px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--theme-button-background);
  color: var(--theme-button-foreground);
}

.btn-primary:hover {
  background: var(--theme-button-hover-background);
}

.btn-secondary {
  background: var(--theme-panel-background);
  color: var(--theme-foreground);
  border: 1px solid var(--theme-border);
}

.btn-secondary:hover {
  background: var(--theme-border);
}
</style>
