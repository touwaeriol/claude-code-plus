<template>
  <div class="claude-code-page">
    <h2 class="page-title">Claude Code Settings</h2>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane label="General" name="general">
        <!-- Default Permissions -->
        <SettingsGroup title="Default Permissions">
          <SettingItem
            type="checkbox"
            label="Default bypass permissions"
            description="Skip confirmation dialogs for file edits and bash commands."
            v-model="settings.claude.defaultBypassPermissions"
          />
          <SettingItem
            type="checkbox"
            label="Default auto cleanup contexts"
            description="Enabled contexts are cleared after send; disabled contexts stay."
            v-model="settings.claude.defaultAutoCleanupContexts"
          />
          <SettingItem
            type="select"
            label="Permission Mode"
            description="default = Ask for each action | bypassPermissions = Auto-approve all"
            v-model="settings.claude.permissionMode"
            :options="permissionModeOptions"
          />
          <SettingItem
            type="checkbox"
            label="Include partial messages in stream"
            description="Include partial messages in UI during streaming"
            v-model="settings.claude.includePartialMessages"
            :disabled="true"
          />
        </SettingsGroup>

        <!-- Runtime Settings -->
        <SettingsGroup title="Runtime Settings">
          <PathInput
            label="Node.js path"
            description="Path to Node.js executable. Leave empty to auto-detect from system PATH."
            v-model="settings.claude.nodePath"
            placeholder="Auto-detect from system PATH"
            :detecting="settings.detectingNode"
            :detected-path="settings.detectedNode?.path"
            :detected-version="settings.detectedNode?.version"
            @browse="handleBrowseNodePath"
          />
          <SettingItem
            type="select"
            label="Default model"
            description="Opus 4.5 = Most capable | Sonnet 4.5 = Balanced | Haiku 4.5 = Fastest"
            v-model="settings.claude.defaultModelId"
            :options="modelOptions"
          />
        </SettingsGroup>

        <!-- Custom Models -->
        <CollapsibleGroup title="Custom Models" name="customModels">
          <ModelTable
            :models="settings.claude.customModels"
            @add="handleAddModel"
            @edit="handleEditModel"
            @remove="handleRemoveModel"
          />
        </CollapsibleGroup>

        <!-- Thinking Configuration -->
        <SettingsGroup title="Thinking Configuration">
          <SettingItem
            type="select"
            label="Default thinking"
            v-model="settings.claude.defaultThinkingLevel"
            :options="thinkingLevelOptions"
          />
          <div class="tokens-row">
            <SettingItem
              type="number"
              label="Think tokens"
              v-model="settings.claude.thinkTokens"
              :min="1"
              :max="128000"
              :step="256"
            />
            <SettingItem
              type="number"
              label="Ultra tokens"
              v-model="settings.claude.ultraTokens"
              :min="1"
              :max="128000"
              :step="256"
            />
          </div>
        </SettingsGroup>
      </el-tab-pane>
      
      <el-tab-pane label="Agents" name="agents">
        <p class="page-comment">Configure custom agents that extend Claude's capabilities.</p>
        <p class="page-comment muted">Requires JetBrains MCP to be enabled</p>
        
        <!-- ExploreWithJetbrains -->
        <CollapsibleGroup title="ExploreWithJetbrains" name="explore">
          <div class="agent-header">
            <el-checkbox v-model="exploreAgent.enabled">Enable</el-checkbox>
            <div class="agent-model">
              <span>Model:</span>
              <el-select v-model="exploreAgent.model" style="width: 120px;">
                <el-option label="(inherit)" value="" />
                <el-option label="opus" value="opus" />
                <el-option label="sonnet" value="sonnet" />
                <el-option label="haiku" value="haiku" />
              </el-select>
            </div>
          </div>
          
          <el-divider />
          
          <SettingItem
            type="textarea"
            label="Description"
            v-model="exploreAgent.description"
            :rows="2"
          />
          <SettingItem
            type="textarea"
            label="System Prompt"
            v-model="exploreAgent.prompt"
            :rows="6"
          />
          <SettingItem
            type="textarea"
            label="Appended System Prompt"
            description="Appended to CLI's system prompt. Tells AI when/how to use this agent."
            v-model="exploreAgent.selectionHint"
            :rows="3"
          />
          
          <el-divider />
          
          <div class="tools-section">
            <label class="setting-label">Allowed Tools</label>
            <div class="tools-input">
              <el-select v-model="newExploreTool" filterable allow-create style="flex: 1;">
                <el-option v-for="tool in knownTools" :key="tool" :label="tool" :value="tool" />
              </el-select>
              <el-button @click="addExploreTool">+</el-button>
            </div>
            <div class="tools-tags">
              <el-tag
                v-for="tool in exploreAgent.tools"
                :key="tool"
                closable
                @close="removeExploreTool(tool)"
              >
                {{ tool }}
              </el-tag>
            </div>
          </div>
        </CollapsibleGroup>
        
        <!-- CodeWithJetbrains -->
        <CollapsibleGroup title="CodeWithJetbrains" name="code">
          <div class="agent-header">
            <el-checkbox v-model="codeAgent.enabled">Enable</el-checkbox>
            <div class="agent-model">
              <span>Model:</span>
              <el-select v-model="codeAgent.model" style="width: 120px;">
                <el-option label="(inherit)" value="" />
                <el-option label="opus" value="opus" />
                <el-option label="sonnet" value="sonnet" />
                <el-option label="haiku" value="haiku" />
              </el-select>
            </div>
          </div>
          
          <el-divider />
          
          <SettingItem
            type="textarea"
            label="Description"
            v-model="codeAgent.description"
            :rows="2"
          />
          <SettingItem
            type="textarea"
            label="System Prompt"
            v-model="codeAgent.prompt"
            :rows="6"
          />
          <SettingItem
            type="textarea"
            label="Appended System Prompt"
            description="Appended to CLI's system prompt. Tells AI when/how to use this agent."
            v-model="codeAgent.selectionHint"
            :rows="3"
          />
          
          <el-divider />
          
          <div class="tools-section">
            <label class="setting-label">Allowed Tools</label>
            <div class="tools-input">
              <el-select v-model="newCodeTool" filterable allow-create style="flex: 1;">
                <el-option v-for="tool in knownTools" :key="tool" :label="tool" :value="tool" />
              </el-select>
              <el-button @click="addCodeTool">+</el-button>
            </div>
            <div class="tools-tags">
              <el-tag
                v-for="tool in codeAgent.tools"
                :key="tool"
                closable
                @close="removeCodeTool(tool)"
              >
                {{ tool }}
              </el-tag>
            </div>
          </div>
        </CollapsibleGroup>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import SettingsGroup from '@/components/SettingsGroup.vue'
import CollapsibleGroup from '@/components/CollapsibleGroup.vue'
import SettingItem from '@/components/SettingItem.vue'
import PathInput from '@/components/PathInput.vue'
import ModelTable from '@/components/ModelTable.vue'

const settings = useSettingsStore()
const activeTab = ref('general')

// 下拉选项
const modelOptions = [
  { label: 'Claude Opus 4.5', value: 'claude-opus-4-5-20251101' },
  { label: 'Claude Sonnet 4.5', value: 'claude-sonnet-4-5-20250929' },
  { label: 'Claude Sonnet 4', value: 'claude-sonnet-4-20250514' },
  { label: 'Claude Haiku 4.5', value: 'claude-haiku-4-5-20251001' }
]

const thinkingLevelOptions = [
  { label: 'Off', value: 'off' },
  { label: 'Think', value: 'think' },
  { label: 'Ultra', value: 'ultra' }
]

const permissionModeOptions = [
  { label: 'default', value: 'default' },
  { label: 'acceptEdits', value: 'acceptEdits' },
  { label: 'plan', value: 'plan' },
  { label: 'bypassPermissions', value: 'bypassPermissions' }
]

const knownTools = [
  'Read', 'Write', 'Edit', 'Bash', 'Glob', 'Grep', 'Task',
  'mcp__jetbrains__FileIndex', 'mcp__jetbrains__CodeSearch',
  'mcp__jetbrains__DirectoryTree', 'mcp__jetbrains__FindUsages'
]

// Agents
const exploreAgent = reactive({
  enabled: true,
  model: '',
  description: '',
  prompt: '',
  selectionHint: '',
  tools: [] as string[]
})

const codeAgent = reactive({
  enabled: true,
  model: '',
  description: '',
  prompt: '',
  selectionHint: '',
  tools: [] as string[]
})

const newExploreTool = ref('')
const newCodeTool = ref('')

// 方法
const handleBrowseNodePath = () => {
  settings.browseFile('claude.nodePath')
}

const handleAddModel = () => {
  // TODO: 打开添加模型对话框
}

const handleEditModel = (_model: { displayName: string; modelId: string }) => {
  // TODO: 打开编辑模型对话框
}

const handleRemoveModel = (model: { displayName: string; modelId: string }) => {
  const index = settings.claude.customModels.findIndex(m => m.modelId === model.modelId)
  if (index !== -1) {
    settings.claude.customModels.splice(index, 1)
  }
}

const addExploreTool = () => {
  if (newExploreTool.value && !exploreAgent.tools.includes(newExploreTool.value)) {
    exploreAgent.tools.push(newExploreTool.value)
    newExploreTool.value = ''
  }
}

const removeExploreTool = (tool: string) => {
  const index = exploreAgent.tools.indexOf(tool)
  if (index !== -1) {
    exploreAgent.tools.splice(index, 1)
  }
}

const addCodeTool = () => {
  if (newCodeTool.value && !codeAgent.tools.includes(newCodeTool.value)) {
    codeAgent.tools.push(newCodeTool.value)
    newCodeTool.value = ''
  }
}

const removeCodeTool = (tool: string) => {
  const index = codeAgent.tools.indexOf(tool)
  if (index !== -1) {
    codeAgent.tools.splice(index, 1)
  }
}
</script>

<style scoped>
.claude-code-page {
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
  margin-bottom: 8px;
}

.page-comment.muted {
  color: var(--vscode-descriptionForeground, #8b8b8b);
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 24px;
}

.agent-model {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tools-section {
  margin-top: 16px;
}

.tools-input {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  max-width: 400px;
}

.tools-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.setting-label {
  font-weight: 500;
  color: var(--vscode-foreground, #cccccc);
}

.tokens-row {
  display: flex;
  gap: 24px;
}

.tokens-row > * {
  flex: 1;
}
</style>
