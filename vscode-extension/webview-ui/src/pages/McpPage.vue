<template>
  <div class="mcp-page">
    <h2 class="page-title">MCP Server Settings</h2>
    
    <p class="page-comment">
      Configure MCP (Model Context Protocol) servers. 
      <a href="https://modelcontextprotocol.io" target="_blank" class="learn-more">Learn more</a>
    </p>
    <p class="page-comment muted">
      MCP servers provide additional capabilities to Claude and Codex.
    </p>
    
    <!-- MCP 服务器表格 -->
    <div class="mcp-table-container">
      <div class="table-toolbar">
        <el-button size="small" @click="handleAdd">
          <el-icon><Plus /></el-icon> Add
        </el-button>
        <el-button size="small" @click="handleEdit" :disabled="!currentRow || currentRow.isBuiltIn">
          <el-icon><Edit /></el-icon> Edit
        </el-button>
        <el-button size="small" @click="handleRemove" :disabled="!currentRow || currentRow.isBuiltIn">
          <el-icon><Delete /></el-icon> Remove
        </el-button>
      </div>
      
      <el-table
        :data="settings.mcp.servers"
        border
        @current-change="handleCurrentChange"
        @row-dblclick="handleRowDblClick"
        highlight-current-row
        style="width: 100%"
        max-height="400"
      >
        <el-table-column label="Status" width="70" align="center">
          <template #default="{ row }">
            <span :class="['status-dot', row.enabled ? 'enabled' : 'disabled']" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="Name" min-width="150" />
        <el-table-column prop="configuration" label="Configuration" min-width="120" />
        <el-table-column label="Backends" width="100">
          <template #default="{ row }">
            <el-tag 
              size="small" 
              @click.stop="handleEditBackends(row)"
              style="cursor: pointer;"
            >
              {{ row.backends }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="Level" width="90" />
      </el-table>
    </div>
    
    <p class="warning-text">
      ⚠️ Proceed with caution and only connect to trusted servers.
    </p>
    
    <!-- 编辑后端对话框 -->
    <el-dialog
      v-model="backendsDialogVisible"
      title="Edit Backends"
      width="400px"
    >
      <el-radio-group v-model="editingBackends">
        <el-radio label="All">All</el-radio>
        <el-radio label="Claude">Claude only</el-radio>
        <el-radio label="Codex">Codex only</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="backendsDialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveBackends">Save</el-button>
      </template>
    </el-dialog>
    
    <!-- 编辑服务器对话框 (带标签页) -->
    <el-dialog
      v-model="serverDialogVisible"
      :title="editingServer ? `Configure ${serverForm.name}` : 'Add MCP Server'"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-tabs v-model="activeTab" type="card">
        <!-- General 标签页 -->
        <el-tab-pane label="General" name="general">
          <el-form :model="serverForm" label-width="150px" class="dialog-form">
            <!-- 基本设置 -->
            <el-form-item label="Name" required>
              <el-input v-model="serverForm.name" :disabled="serverForm.isBuiltIn" />
            </el-form-item>
            
            <el-form-item label="Enabled">
              <el-switch v-model="serverForm.enabled" />
              <span class="inline-label">Enabled in:</span>
              <el-checkbox-group v-model="serverForm.backendsList" class="inline-checkboxes">
                <el-checkbox label="all" @change="handleBackendChange('all')">All</el-checkbox>
                <el-checkbox label="claude" :disabled="serverForm.backendsList.includes('all')">Claude</el-checkbox>
                <el-checkbox label="codex" :disabled="serverForm.backendsList.includes('all')">Codex</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            
            <!-- Context7 API Key -->
            <el-form-item v-if="serverForm.name === 'Context7'" label="API Key">
              <el-input v-model="serverForm.apiKey" placeholder="(optional, for authenticated access)" />
            </el-form-item>
            
            <!-- Terminal MCP 配置 -->
            <template v-if="serverForm.name === 'Terminal'">
              <el-divider content-position="left">Terminal Configuration</el-divider>
              <el-form-item label="Default Shell">
                <el-select v-model="serverForm.terminalDefaultShell" placeholder="Auto detect">
                  <el-option label="Auto" value="" />
                  <el-option v-for="shell in availableShells" :key="shell" :label="shell" :value="shell" />
                </el-select>
              </el-form-item>
              <el-form-item label="Available Shells">
                <el-checkbox-group v-model="serverForm.terminalShellsList">
                  <el-checkbox v-for="shell in allShellTypes" :key="shell" :label="shell">{{ shell }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="Max Lines">
                    <el-input-number v-model="serverForm.terminalMaxOutputLines" :min="100" :max="10000" :step="100" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="Max Chars">
                    <el-input-number v-model="serverForm.terminalMaxOutputChars" :min="1000" :max="100000" :step="1000" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="Read Timeout">
                    <el-input-number v-model="serverForm.terminalReadTimeout" :min="1" :max="300" /> sec
                  </el-form-item>
                </el-col>
              </el-row>
            </template>
            
            <!-- Git MCP 配置 -->
            <template v-if="serverForm.name === 'Git'">
              <el-divider content-position="left">Git Configuration</el-divider>
              <el-form-item label="Commit Language">
                <el-select v-model="serverForm.gitCommitLanguage">
                  <el-option label="English" value="en" />
                  <el-option label="中文" value="zh" />
                  <el-option label="日本語" value="ja" />
                  <el-option label="한국어" value="ko" />
                  <el-option label="Auto (detect from system)" value="auto" />
                </el-select>
                <div class="form-help">AI will generate commit messages in this language.</div>
              </el-form-item>
            </template>
            
            <!-- File MCP 配置 -->
            <template v-if="serverForm.name === 'JetBrains File'">
              <el-divider content-position="left">File Access Configuration</el-divider>
              <el-form-item label="Allow External">
                <el-switch v-model="serverForm.fileAllowExternal" />
                <span class="form-help-inline">Allow access to files outside project</span>
              </el-form-item>
              <el-form-item v-if="serverForm.fileAllowExternal" label="External Rules">
                <div class="external-rules-container">
                  <el-input
                    v-model="newExternalRule"
                    placeholder="Add folder path..."
                    class="external-rule-input"
                    @keyup.enter="addExternalRule"
                  >
                    <template #append>
                      <el-button @click="addExternalRule">+</el-button>
                    </template>
                  </el-input>
                  <div class="external-rules-list">
                    <el-tag
                      v-for="(rule, index) in serverForm.externalRulesList"
                      :key="index"
                      closable
                      @close="removeExternalRule(index)"
                      class="external-rule-tag"
                    >
                      {{ rule }}
                    </el-tag>
                  </div>
                </div>
              </el-form-item>
            </template>
            
            <el-divider />
            
            <!-- Tool Timeout -->
            <el-form-item label="Tool Call Timeout">
              <el-input-number v-model="serverForm.toolTimeoutSec" :min="1" :max="7200" /> seconds
              <div class="form-help">Minimum 1 second. Set to 3600 for User Interaction.</div>
            </el-form-item>
            
            <!-- 自定义服务器 JSON 配置 -->
            <el-form-item v-if="!serverForm.isBuiltIn" label="Configuration">
              <el-input
                type="textarea"
                v-model="serverForm.configuration"
                :rows="6"
                placeholder='{"command": "node", "args": ["server.js"]}'
                style="font-family: monospace;"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        <!-- Claude Code 标签页 -->
        <el-tab-pane label="Claude Code" name="claude">
          <el-form :model="serverForm" label-width="180px" class="dialog-form">
            <div class="tab-header">
              <span class="tab-title">Appended System Prompt (Claude Code Override)</span>
              <el-button link type="primary" @click="resetClaudeInstructions">Reset to Default</el-button>
            </div>
            <p class="tab-description">
              Customize the system prompt for Claude Code. Edit to override, or reset to use the default prompt.
            </p>
            <el-input
              type="textarea"
              v-model="serverForm.instructionsClaude"
              :rows="10"
              placeholder="Enter custom system prompt for Claude Code..."
              style="font-family: monospace;"
            />
            
            <!-- 禁用工具 (Terminal 和 File MCP) -->
            <template v-if="hasDisableToolsToggle">
              <el-divider />
              <el-form-item label="Disabled Tools">
                <div class="tags-input-container">
                  <el-input
                    v-model="newDisabledTool"
                    placeholder="Tool name (e.g., Bash)"
                    class="tag-input"
                    @keyup.enter="addDisabledTool"
                  >
                    <template #append>
                      <el-button @click="addDisabledTool">+</el-button>
                    </template>
                  </el-input>
                  <el-button @click="resetDisabledTools" size="small">Reset</el-button>
                </div>
                <div class="tags-list disabled-tools">
                  <el-tag
                    v-for="(tool, index) in serverForm.disabledTools"
                    :key="index"
                    closable
                    type="danger"
                    @close="removeDisabledTool(index)"
                  >
                    {{ tool }}
                  </el-tag>
                </div>
              </el-form-item>
            </template>
          </el-form>
        </el-tab-pane>
        
        <!-- Codex 标签页 -->
        <el-tab-pane label="Codex" name="codex">
          <el-form :model="serverForm" label-width="180px" class="dialog-form">
            <div class="tab-header">
              <span class="tab-title">Appended System Prompt (Codex Override)</span>
              <el-button link type="primary" @click="resetCodexInstructions">Reset to Default</el-button>
            </div>
            <p class="tab-description">
              Customize the system prompt for Codex. Edit to override, or reset to use the default prompt.
            </p>
            <el-input
              type="textarea"
              v-model="serverForm.instructionsCodex"
              :rows="10"
              placeholder="Enter custom system prompt for Codex..."
              style="font-family: monospace;"
            />
            
            <!-- Codex 禁用功能 -->
            <template v-if="hasDisableToolsToggle">
              <el-divider />
              <el-form-item label="Disabled Features">
                <div class="tags-input-container">
                  <el-input
                    v-model="newCodexDisabledFeature"
                    placeholder="Feature name (e.g., shell_tool)"
                    class="tag-input"
                    @keyup.enter="addCodexDisabledFeature"
                  >
                    <template #append>
                      <el-button @click="addCodexDisabledFeature">+</el-button>
                    </template>
                  </el-input>
                  <el-button @click="resetCodexDisabledFeatures" size="small">Reset</el-button>
                </div>
                <p class="form-help">Available: shell_tool, apply_patch_freeform, unified_exec, view_image_tool, web_search_request, skills</p>
                <div class="tags-list codex-features">
                  <el-tag
                    v-for="(feature, index) in serverForm.codexDisabledFeatures"
                    :key="index"
                    closable
                    type="success"
                    @close="removeCodexDisabledFeature(index)"
                  >
                    {{ feature }}
                  </el-tag>
                </div>
              </el-form-item>
            </template>
            
            <!-- Codex 自动批准工具 -->
            <template v-if="hasAutoApprovedTools">
              <el-divider />
              <el-form-item label="Auto-Approved Tools">
                <div class="tags-input-container">
                  <el-input
                    v-model="newAutoApprovedTool"
                    placeholder="Tool name"
                    class="tag-input"
                    @keyup.enter="addAutoApprovedTool"
                  >
                    <template #append>
                      <el-button @click="addAutoApprovedTool">+</el-button>
                    </template>
                  </el-input>
                  <el-button @click="resetAutoApprovedTools" size="small">Reset</el-button>
                </div>
                <div class="tags-list auto-approved">
                  <el-tag
                    v-for="(tool, index) in serverForm.codexAutoApprovedTools"
                    :key="index"
                    closable
                    type="warning"
                    @close="removeAutoApprovedTool(index)"
                  >
                    {{ tool }}
                  </el-tag>
                </div>
              </el-form-item>
            </template>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <el-button @click="serverDialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveServer">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { useSettingsStore, type McpServer, getDefaultInstructions } from '@/stores/settingsStore'

const settings = useSettingsStore()

const currentRow = ref<McpServer | null>(null)
const backendsDialogVisible = ref(false)
const serverDialogVisible = ref(false)
const editingServer = ref<McpServer | null>(null)
const editingBackends = ref('All')
const activeTab = ref('general')

// Shell types
const allShellTypes = ['powershell', 'cmd', 'git-bash', 'wsl']

// 新增输入字段
const newDisabledTool = ref('')
const newCodexDisabledFeature = ref('')
const newAutoApprovedTool = ref('')
const newExternalRule = ref('')

// 默认禁用工具
const defaultDisabledTools: Record<string, string[]> = {
  'Terminal': ['Bash'],
  'JetBrains File': ['Read', 'Write', 'Edit']
}

// 默认 Codex 禁用功能
const defaultCodexDisabledFeatures: Record<string, string[]> = {
  'Terminal': ['shell_tool'],
  'JetBrains File': ['apply_patch_freeform']
}

// 默认自动批准工具
const defaultAutoApprovedTools: Record<string, string[]> = {
  'User Interaction': ['mcp__user_interaction__AskUserQuestion'],
  'JetBrains LSP': ['mcp__jetbrains-lsp__DirectoryTree', 'mcp__jetbrains-lsp__FileIndex', 'mcp__jetbrains-lsp__CodeSearch'],
  'JetBrains File': ['mcp__jetbrains-file__ReadFile'],
  'Terminal': ['mcp__jetbrains-terminal__TerminalList', 'mcp__jetbrains-terminal__TerminalRead'],
  'Git': ['mcp__jetbrains_git__GetVcsStatus', 'mcp__jetbrains_git__GetVcsChanges']
}

const serverForm = reactive({
  name: '',
  enabled: true,
  backends: 'All',
  backendsList: ['all'] as string[],
  level: 'Global',
  configuration: '',
  isBuiltIn: false,
  // Instructions
  instructionsClaude: '',
  instructionsCodex: '',
  // Timeout
  toolTimeoutSec: 60,
  // Disabled Tools
  disabledTools: [] as string[],
  codexDisabledFeatures: [] as string[],
  codexAutoApprovedTools: [] as string[],
  // Context7
  apiKey: '',
  // Terminal
  terminalMaxOutputLines: 500,
  terminalMaxOutputChars: 50000,
  terminalReadTimeout: 30,
  terminalDefaultShell: '',
  terminalShellsList: ['powershell', 'cmd', 'git-bash', 'wsl'] as string[],
  // Git
  gitCommitLanguage: 'en',
  // File
  fileAllowExternal: true,
  externalRulesList: [] as string[]
})

// 计算属性
const hasDisableToolsToggle = computed(() => 
  serverForm.name === 'Terminal' || serverForm.name === 'JetBrains File'
)

const hasAutoApprovedTools = computed(() =>
  Object.keys(defaultAutoApprovedTools).includes(serverForm.name)
)

const availableShells = computed(() => 
  serverForm.terminalShellsList.length > 0 ? serverForm.terminalShellsList : allShellTypes
)

// 方法
const handleCurrentChange = (row: McpServer | null) => {
  currentRow.value = row
}

const handleRowDblClick = (row: McpServer) => {
  openEditDialog(row)
}

const openEditDialog = (row: McpServer) => {
  editingServer.value = row
  serverForm.name = row.name
  serverForm.enabled = row.enabled
  serverForm.backends = row.backends
  serverForm.level = row.level
  serverForm.configuration = row.configuration || ''
  serverForm.isBuiltIn = row.isBuiltIn
  
  // Parse backends to list
  if (row.backends === 'All') {
    serverForm.backendsList = ['all']
  } else if (row.backends === 'Claude') {
    serverForm.backendsList = ['claude']
  } else if (row.backends === 'Codex') {
    serverForm.backendsList = ['codex']
  } else {
    serverForm.backendsList = ['all']
  }
  
  // Instructions
  serverForm.instructionsClaude = row.instructionsClaude || getDefaultInstructions(row.name)
  serverForm.instructionsCodex = row.instructionsCodex || getDefaultInstructions(row.name)
  
  // Timeout
  serverForm.toolTimeoutSec = row.toolTimeoutSec || 60
  
  // Disabled Tools
  serverForm.disabledTools = [...(row.disabledTools || [])]
  serverForm.codexDisabledFeatures = [...(row.codexDisabledFeatures || [])]
  serverForm.codexAutoApprovedTools = [...(row.codexAutoApprovedTools || defaultAutoApprovedTools[row.name] || [])]
  
  // Context7
  serverForm.apiKey = row.apiKey || ''
  
  // Terminal
  serverForm.terminalMaxOutputLines = row.terminalMaxOutputLines || 500
  serverForm.terminalMaxOutputChars = row.terminalMaxOutputChars || 50000
  serverForm.terminalReadTimeout = row.terminalReadTimeout || 30
  serverForm.terminalDefaultShell = row.terminalDefaultShell || ''
  serverForm.terminalShellsList = row.terminalAvailableShells 
    ? row.terminalAvailableShells.split(',').filter(s => s.trim())
    : [...allShellTypes]
  
  // Git
  serverForm.gitCommitLanguage = row.gitCommitLanguage || 'en'
  
  // File
  serverForm.fileAllowExternal = row.fileAllowExternal ?? true
  try {
    serverForm.externalRulesList = row.fileExternalRules ? JSON.parse(row.fileExternalRules) : []
  } catch {
    serverForm.externalRulesList = []
  }
  
  activeTab.value = 'general'
  serverDialogVisible.value = true
}

const handleAdd = () => {
  editingServer.value = null
  serverForm.name = ''
  serverForm.enabled = true
  serverForm.backends = 'All'
  serverForm.backendsList = ['all']
  serverForm.level = 'Project'
  serverForm.configuration = ''
  serverForm.isBuiltIn = false
  serverForm.instructionsClaude = ''
  serverForm.instructionsCodex = ''
  serverForm.toolTimeoutSec = 60
  serverForm.disabledTools = []
  serverForm.codexDisabledFeatures = []
  serverForm.codexAutoApprovedTools = []
  serverForm.apiKey = ''
  serverForm.terminalMaxOutputLines = 500
  serverForm.terminalMaxOutputChars = 50000
  serverForm.terminalReadTimeout = 30
  serverForm.terminalDefaultShell = ''
  serverForm.terminalShellsList = [...allShellTypes]
  serverForm.gitCommitLanguage = 'en'
  serverForm.fileAllowExternal = true
  serverForm.externalRulesList = []
  
  activeTab.value = 'general'
  serverDialogVisible.value = true
}

const handleEdit = () => {
  if (!currentRow.value || currentRow.value.isBuiltIn) return
  openEditDialog(currentRow.value)
}

const handleRemove = () => {
  if (!currentRow.value || currentRow.value.isBuiltIn) return
  
  const index = settings.mcp.servers.findIndex(s => s.name === currentRow.value!.name)
  if (index !== -1) {
    settings.mcp.servers.splice(index, 1)
  }
  currentRow.value = null
}

const handleEditBackends = (row: McpServer) => {
  editingServer.value = row
  editingBackends.value = row.backends
  backendsDialogVisible.value = true
}

const handleBackendChange = (value: string) => {
  if (value === 'all' && serverForm.backendsList.includes('all')) {
    serverForm.backendsList = ['all']
  } else if (value === 'all' && !serverForm.backendsList.includes('all')) {
    // Keep as is
  } else {
    // Remove 'all' if other is selected
    const idx = serverForm.backendsList.indexOf('all')
    if (idx !== -1) {
      serverForm.backendsList.splice(idx, 1)
    }
  }
}

const saveBackends = () => {
  if (editingServer.value) {
    editingServer.value.backends = editingBackends.value
  }
  backendsDialogVisible.value = false
}

// Instructions reset
const resetClaudeInstructions = () => {
  serverForm.instructionsClaude = getDefaultInstructions(serverForm.name)
}

const resetCodexInstructions = () => {
  serverForm.instructionsCodex = getDefaultInstructions(serverForm.name)
}

// Disabled tools management
const addDisabledTool = () => {
  const tool = newDisabledTool.value.trim()
  if (tool && !serverForm.disabledTools.includes(tool)) {
    serverForm.disabledTools.push(tool)
    newDisabledTool.value = ''
  }
}

const removeDisabledTool = (index: number) => {
  serverForm.disabledTools.splice(index, 1)
}

const resetDisabledTools = () => {
  serverForm.disabledTools = [...(defaultDisabledTools[serverForm.name] || [])]
}

// Codex disabled features management
const addCodexDisabledFeature = () => {
  const feature = newCodexDisabledFeature.value.trim()
  if (feature && !serverForm.codexDisabledFeatures.includes(feature)) {
    serverForm.codexDisabledFeatures.push(feature)
    newCodexDisabledFeature.value = ''
  }
}

const removeCodexDisabledFeature = (index: number) => {
  serverForm.codexDisabledFeatures.splice(index, 1)
}

const resetCodexDisabledFeatures = () => {
  serverForm.codexDisabledFeatures = [...(defaultCodexDisabledFeatures[serverForm.name] || [])]
}

// Auto-approved tools management
const addAutoApprovedTool = () => {
  const tool = newAutoApprovedTool.value.trim()
  if (tool && !serverForm.codexAutoApprovedTools.includes(tool)) {
    serverForm.codexAutoApprovedTools.push(tool)
    newAutoApprovedTool.value = ''
  }
}

const removeAutoApprovedTool = (index: number) => {
  serverForm.codexAutoApprovedTools.splice(index, 1)
}

const resetAutoApprovedTools = () => {
  serverForm.codexAutoApprovedTools = [...(defaultAutoApprovedTools[serverForm.name] || [])]
}

// External rules management
const addExternalRule = () => {
  const rule = newExternalRule.value.trim()
  if (rule && !serverForm.externalRulesList.includes(rule)) {
    serverForm.externalRulesList.push(rule)
    newExternalRule.value = ''
  }
}

const removeExternalRule = (index: number) => {
  serverForm.externalRulesList.splice(index, 1)
}

const saveServer = () => {
  // Convert backendsList to backends string
  let backends = 'All'
  if (serverForm.backendsList.includes('all')) {
    backends = 'All'
  } else if (serverForm.backendsList.length === 1) {
    backends = serverForm.backendsList[0] === 'claude' ? 'Claude' : 'Codex'
  } else if (serverForm.backendsList.length === 2) {
    backends = 'All'
  }
  
  const updatedServer: Partial<McpServer> = {
    name: serverForm.name,
    enabled: serverForm.enabled,
    backends: backends,
    level: serverForm.level,
    instructionsClaude: serverForm.instructionsClaude,
    instructionsCodex: serverForm.instructionsCodex,
    toolTimeoutSec: serverForm.toolTimeoutSec,
    disabledTools: serverForm.disabledTools,
    codexDisabledFeatures: serverForm.codexDisabledFeatures,
    codexAutoApprovedTools: serverForm.codexAutoApprovedTools,
    apiKey: serverForm.apiKey,
    terminalMaxOutputLines: serverForm.terminalMaxOutputLines,
    terminalMaxOutputChars: serverForm.terminalMaxOutputChars,
    terminalReadTimeout: serverForm.terminalReadTimeout,
    terminalDefaultShell: serverForm.terminalDefaultShell,
    terminalAvailableShells: serverForm.terminalShellsList.join(','),
    gitCommitLanguage: serverForm.gitCommitLanguage,
    fileAllowExternal: serverForm.fileAllowExternal,
    fileExternalRules: JSON.stringify(serverForm.externalRulesList)
  }
  
  if (editingServer.value) {
    // 更新现有服务器
    Object.assign(editingServer.value, updatedServer)
    if (!serverForm.isBuiltIn) {
      editingServer.value.configuration = serverForm.configuration
    }
  } else {
    // 添加新服务器
    settings.mcp.servers.push({
      ...updatedServer,
      configuration: serverForm.configuration,
      isBuiltIn: false
    } as McpServer)
  }
  serverDialogVisible.value = false
}
</script>

<style scoped>
.mcp-page {
  max-width: 900px;
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

.learn-more {
  color: var(--vscode-textLink-foreground, #3794ff);
  text-decoration: none;
}

.learn-more:hover {
  text-decoration: underline;
}

.mcp-table-container {
  margin-top: 20px;
  margin-bottom: 16px;
}

.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.status-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.enabled {
  background: var(--vscode-testing-iconPassed, #4caf50);
}

.status-dot.disabled {
  background: var(--vscode-testing-iconSkipped, #9e9e9e);
}

.warning-text {
  font-size: 13px;
  color: #b07800;
  margin-top: 16px;
}

/* 对话框样式 */
.dialog-form {
  max-height: 500px;
  overflow-y: auto;
  padding-right: 10px;
}

.inline-label {
  margin-left: 20px;
  margin-right: 8px;
  color: var(--vscode-descriptionForeground);
}

.inline-checkboxes {
  display: inline-flex;
  gap: 8px;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tab-title {
  font-weight: 600;
  font-size: 14px;
}

.tab-description {
  font-size: 12px;
  color: var(--vscode-descriptionForeground);
  margin-bottom: 12px;
}

.form-help {
  font-size: 12px;
  color: var(--vscode-descriptionForeground);
  margin-top: 4px;
}

.form-help-inline {
  margin-left: 12px;
  font-size: 12px;
  color: var(--vscode-descriptionForeground);
}

.tags-input-container {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.tag-input {
  flex: 1;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.external-rules-container {
  width: 100%;
}

.external-rule-input {
  margin-bottom: 8px;
}

.external-rules-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.external-rule-tag {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
