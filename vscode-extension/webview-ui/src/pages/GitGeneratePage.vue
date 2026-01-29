<template>
  <div class="git-generate-page">
    <h2 class="page-title">Git Generate Settings</h2>
    
    <p class="page-comment">Configure AI-powered Git commit message generation.</p>
    
    <SettingItem
      type="checkbox"
      label="Enable Git Generate"
      v-model="settings.gitGenerate.enabled"
    />
    
    <p class="page-comment muted">Git Generate uses built-in Git MCP and default permissions automatically.</p>
    
    <el-divider />
    
    <div class="form-row">
      <SettingItem
        type="select"
        label="Backend"
        v-model="settings.gitGenerate.backend"
        :options="backendOptions"
      />
    </div>
    
    <div class="form-row">
      <SettingItem
        type="select"
        label="Model"
        v-model="settings.gitGenerate.modelId"
        :options="currentModelOptions"
      />
    </div>
    
    <!-- Thinking -->
    <SettingsGroup title="Thinking">
      <template v-if="settings.gitGenerate.backend === 'claude'">
        <SettingItem
          type="select"
          label="Claude Thinking Level"
          v-model="settings.gitGenerate.claudeThinkingLevel"
          :options="thinkingLevelOptions"
        />
      </template>
      <template v-else>
        <SettingItem
          type="select"
          label="Codex Reasoning Effort"
          v-model="settings.gitGenerate.codexReasoningEffort"
          :options="reasoningEffortOptions"
        />
      </template>
    </SettingsGroup>
    
    <SettingItem
      type="checkbox"
      label="Save session"
      v-model="settings.gitGenerate.saveSession"
    />
    
    <el-divider />
    
    <!-- System Prompt -->
    <SettingsGroup title="System Prompt">
      <p class="setting-description" style="margin-bottom: 12px;">
        Instructions for the AI on how to generate commit messages.
      </p>
      <el-input
        type="textarea"
        v-model="settings.gitGenerate.systemPrompt"
        :rows="8"
        placeholder="Enter system prompt..."
        style="font-family: monospace;"
      />
    </SettingsGroup>
    
    <!-- User Prompt -->
    <SettingsGroup title="User Prompt">
      <p class="setting-description" style="margin-bottom: 12px;">
        Runtime prompt sent with the code changes. Customize analysis focus here.
      </p>
      <el-input
        type="textarea"
        v-model="settings.gitGenerate.userPrompt"
        :rows="6"
        placeholder="Enter user prompt..."
        style="font-family: monospace;"
      />
    </SettingsGroup>
    
    <el-button @click="resetToDefault">Reset to Default</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import SettingsGroup from '@/components/SettingsGroup.vue'
import SettingItem from '@/components/SettingItem.vue'

const settings = useSettingsStore()

// 下拉选项
const backendOptions = [
  { label: 'Claude', value: 'claude' },
  { label: 'Codex', value: 'codex' }
]

const claudeModelOptions = [
  { label: 'Claude Opus 4.5', value: 'claude-opus-4-5-20251101' },
  { label: 'Claude Sonnet 4.5', value: 'claude-sonnet-4-5-20250929' },
  { label: 'Claude Sonnet 4', value: 'claude-sonnet-4-20250514' },
  { label: 'Claude Haiku 4.5', value: 'claude-haiku-4-5-20251001' }
]

const codexModelOptions = [
  { label: 'gpt-5.2-codex', value: 'gpt-5.2-codex' },
  { label: 'gpt-5.2', value: 'gpt-5.2' },
  { label: 'o3', value: 'o3' },
  { label: 'o4-mini', value: 'o4-mini' }
]

const currentModelOptions = computed(() => {
  return settings.gitGenerate.backend === 'claude' ? claudeModelOptions : codexModelOptions
})

const thinkingLevelOptions = [
  { label: 'Off', value: 'off' },
  { label: 'Low', value: 'low' },
  { label: 'Medium', value: 'medium' },
  { label: 'High', value: 'high' },
  { label: 'Very High', value: 'very_high' },
  { label: 'Ultra', value: 'ultra' }
]

const reasoningEffortOptions = [
  { label: 'low', value: 'low' },
  { label: 'medium', value: 'medium' },
  { label: 'high', value: 'high' },
  { label: 'xhigh', value: 'xhigh' }
]

// 方法
const resetToDefault = () => {
  settings.gitGenerate.enabled = false
  settings.gitGenerate.backend = 'claude'
  settings.gitGenerate.modelId = ''
  settings.gitGenerate.claudeThinkingLevel = 'ultra'
  settings.gitGenerate.codexReasoningEffort = 'xhigh'
  settings.gitGenerate.saveSession = false
  settings.gitGenerate.systemPrompt = ''
  settings.gitGenerate.userPrompt = ''
}
</script>

<style scoped>
.git-generate-page {
  max-width: 800px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--vscode-foreground, #cccccc);
}

.page-comment {
  font-size: 13px;
  color: var(--vscode-foreground, #cccccc);
  margin-bottom: 12px;
}

.page-comment.muted {
  color: var(--vscode-descriptionForeground, #8b8b8b);
}

.form-row {
  margin-bottom: 16px;
}

.setting-description {
  font-size: 12px;
  color: var(--vscode-textLink-foreground, #3794ff);
  line-height: 1.4;
}
</style>
