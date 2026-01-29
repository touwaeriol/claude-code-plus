<template>
  <div class="codex-page">
    <h2 class="page-title">Codex Settings</h2>
    
    <!-- Default Permissions -->
    <SettingsGroup title="Default Permissions">
      <SettingItem
        type="checkbox"
        label="Default bypass permissions"
        description="Skip confirmation dialogs for file edits and bash commands. Use with caution."
        v-model="settings.codex.defaultBypassPermissions"
      />
      <SettingItem
        type="checkbox"
        label="Default auto cleanup contexts"
        description="Enabled contexts are cleared after send; disabled contexts stay."
        v-model="settings.codex.defaultAutoCleanupContexts"
      />
    </SettingsGroup>

    <!-- Runtime Settings -->
    <SettingsGroup title="Runtime Settings">
      <PathInput
        label="Codex Path"
        description="Custom Codex CLI executable path (optional)"
        v-model="settings.codex.codexPath"
        placeholder="Leave empty to use bundled Codex"
        :detecting="settings.detectingCodex"
        :detected-path="settings.detectedCodex?.path"
        :detected-version="settings.detectedCodex?.version"
        @browse="handleBrowseCodexPath"
      />
      <SettingItem
        type="checkbox"
        label="Web search"
        description="Allow Codex to request web searches (features.web_search_request)."
        v-model="settings.codex.webSearch"
      />
    </SettingsGroup>

    <!-- Model Settings -->
    <SettingsGroup title="Model Settings">
      <SettingItem
        type="select"
        label="Default model"
        description="gpt-5.2-codex = Codex optimized | gpt-5.2 = Base model"
        v-model="settings.codex.defaultModelId"
        :options="modelOptions"
      />
    </SettingsGroup>

    <!-- Custom Models -->
    <CollapsibleGroup title="Custom Models" name="customModels">
      <ModelTable
        :models="settings.codex.customModels"
        @add="handleAddModel"
        @edit="handleEditModel"
        @remove="handleRemoveModel"
      />
    </CollapsibleGroup>

    <!-- Session Defaults -->
    <SettingsGroup title="Session Defaults">
      <SettingItem
        type="select"
        label="Reasoning effort"
        description="Controls reasoning depth for Codex responses."
        v-model="settings.codex.reasoningEffort"
        :options="reasoningEffortOptions"
      />
      <SettingItem
        type="select"
        label="Reasoning summary"
        description="Summary style for reasoning output when supported."
        v-model="settings.codex.reasoningSummary"
        :options="reasoningSummaryOptions"
      />
      <SettingItem
        type="select"
        label="Sandbox mode"
        description="Controls file system and network access permissions."
        v-model="settings.codex.sandboxMode"
        :options="sandboxModeOptions"
      />
    </SettingsGroup>
  </div>
</template>

<script setup lang="ts">
import { useSettingsStore } from '@/stores/settingsStore'
import SettingsGroup from '@/components/SettingsGroup.vue'
import CollapsibleGroup from '@/components/CollapsibleGroup.vue'
import SettingItem from '@/components/SettingItem.vue'
import PathInput from '@/components/PathInput.vue'
import ModelTable from '@/components/ModelTable.vue'

const settings = useSettingsStore()

// 下拉选项
const modelOptions = [
  { label: 'gpt-5.2-codex', value: 'gpt-5.2-codex' },
  { label: 'gpt-5.2', value: 'gpt-5.2' },
  { label: 'o3', value: 'o3' },
  { label: 'o4-mini', value: 'o4-mini' },
  { label: 'codex-mini-latest', value: 'codex-mini-latest' }
]

const reasoningEffortOptions = [
  { label: 'Minimal', value: 'minimal' },
  { label: 'Low', value: 'low' },
  { label: 'Medium', value: 'medium' },
  { label: 'High', value: 'high' },
  { label: 'Extra High', value: 'xhigh' }
]

const reasoningSummaryOptions = [
  { label: 'Auto', value: 'auto' },
  { label: 'Concise', value: 'concise' },
  { label: 'Detailed', value: 'detailed' },
  { label: 'None', value: 'none' }
]

const sandboxModeOptions = [
  { label: 'Read Only', value: 'read-only' },
  { label: 'Workspace Write', value: 'workspace-write' },
  { label: 'Full Access', value: 'danger-full-access' }
]

// 方法
const handleBrowseCodexPath = () => {
  settings.browseFile('codex.codexPath')
}

const handleAddModel = () => {
  // TODO: 打开添加模型对话框
}

const handleEditModel = (_model: { displayName: string; modelId: string }) => {
  // TODO: 打开编辑模型对话框
}

const handleRemoveModel = (model: { displayName: string; modelId: string }) => {
  const index = settings.codex.customModels.findIndex(m => m.modelId === model.modelId)
  if (index !== -1) {
    settings.codex.customModels.splice(index, 1)
  }
}
</script>

<style scoped>
.codex-page {
  max-width: 800px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--vscode-foreground, #cccccc);
}
</style>
