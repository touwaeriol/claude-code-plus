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
    
    <!-- 编辑服务器对话框 -->
    <el-dialog
      v-model="serverDialogVisible"
      :title="editingServer ? 'Edit MCP Server' : 'Add MCP Server'"
      width="600px"
    >
      <el-form :model="serverForm" label-width="120px">
        <el-form-item label="Name" required>
          <el-input v-model="serverForm.name" :disabled="serverForm.isBuiltIn" />
        </el-form-item>
        <el-form-item label="Enabled">
          <el-switch v-model="serverForm.enabled" />
        </el-form-item>
        <el-form-item label="Backends">
          <el-radio-group v-model="serverForm.backends">
            <el-radio label="All">All</el-radio>
            <el-radio label="Claude">Claude</el-radio>
            <el-radio label="Codex">Codex</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Level">
          <el-radio-group v-model="serverForm.level">
            <el-radio label="Global">Global</el-radio>
            <el-radio label="Project">Project</el-radio>
          </el-radio-group>
        </el-form-item>
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
      <template #footer>
        <el-button @click="serverDialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveServer">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { useSettingsStore, type McpServer } from '@/stores/settingsStore'

const settings = useSettingsStore()

const currentRow = ref<McpServer | null>(null)
const backendsDialogVisible = ref(false)
const serverDialogVisible = ref(false)
const editingServer = ref<McpServer | null>(null)
const editingBackends = ref('All')

const serverForm = reactive({
  name: '',
  enabled: true,
  backends: 'All',
  level: 'Global',
  configuration: '',
  isBuiltIn: false
})

// 方法
const handleCurrentChange = (row: McpServer | null) => {
  currentRow.value = row
}

const handleRowDblClick = (row: McpServer) => {
  if (row.isBuiltIn) {
    // 编辑内置服务器（只能修改 enabled, backends, level）
    editingServer.value = row
    serverForm.name = row.name
    serverForm.enabled = row.enabled
    serverForm.backends = row.backends
    serverForm.level = row.level
    serverForm.configuration = row.configuration || ''
    serverForm.isBuiltIn = true
    serverDialogVisible.value = true
  } else {
    handleEdit()
  }
}

const handleAdd = () => {
  editingServer.value = null
  serverForm.name = ''
  serverForm.enabled = true
  serverForm.backends = 'All'
  serverForm.level = 'Project'
  serverForm.configuration = ''
  serverForm.isBuiltIn = false
  serverDialogVisible.value = true
}

const handleEdit = () => {
  if (!currentRow.value || currentRow.value.isBuiltIn) return
  
  editingServer.value = currentRow.value
  serverForm.name = currentRow.value.name
  serverForm.enabled = currentRow.value.enabled
  serverForm.backends = currentRow.value.backends
  serverForm.level = currentRow.value.level
  serverForm.configuration = currentRow.value.configuration || ''
  serverForm.isBuiltIn = false
  serverDialogVisible.value = true
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

const saveBackends = () => {
  if (editingServer.value) {
    editingServer.value.backends = editingBackends.value
  }
  backendsDialogVisible.value = false
}

const saveServer = () => {
  if (editingServer.value) {
    // 更新现有服务器
    editingServer.value.name = serverForm.name
    editingServer.value.enabled = serverForm.enabled
    editingServer.value.backends = serverForm.backends
    editingServer.value.level = serverForm.level
    if (!serverForm.isBuiltIn) {
      editingServer.value.configuration = serverForm.configuration
    }
  } else {
    // 添加新服务器
    settings.mcp.servers.push({
      name: serverForm.name,
      enabled: serverForm.enabled,
      backends: serverForm.backends,
      level: serverForm.level,
      configuration: serverForm.configuration,
      isBuiltIn: false
    })
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
</style>
