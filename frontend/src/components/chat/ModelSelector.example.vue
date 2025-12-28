<template>
  <div class="model-selector-examples">
    <h1>ModelSelector 组件示例</h1>

    <div class="example-section">
      <h2>示例 1: 基础用法</h2>
      <p>简单的模型选择器，支持后端类型切换</p>

      <div class="demo-controls">
        <label>
          后端类型:
          <select v-model="example1Backend" class="backend-select">
            <option value="claude">Claude</option>
            <option value="codex">OpenAI Codex</option>
          </select>
        </label>
      </div>

      <div class="demo-component">
        <ModelSelector
          v-model="example1Model"
          :backend-type="example1Backend"
        />
      </div>

      <div class="demo-output">
        <strong>当前选择:</strong>
        <pre>{{ JSON.stringify({ backend: example1Backend, model: example1Model }, null, 2) }}</pre>
      </div>
    </div>

    <div class="example-section">
      <h2>示例 2: 显示详细信息</h2>
      <p>显示模型的详细信息，包括描述和支持的功能</p>

      <div class="demo-component">
        <ModelSelector
          v-model="example2Model"
          :backend-type="example2Backend"
          :show-details="true"
        />
      </div>

      <div class="demo-controls">
        <button @click="toggleBackend2">切换后端</button>
        <span>当前后端: {{ example2Backend }}</span>
      </div>
    </div>

    <div class="example-section">
      <h2>示例 3: 禁用状态</h2>
      <p>会话进行中时禁用选择器</p>

      <div class="demo-controls">
        <label>
          <input v-model="example3Disabled" type="checkbox">
          禁用选择器（模拟会话进行中）
        </label>
      </div>

      <div class="demo-component">
        <ModelSelector
          v-model="example3Model"
          :backend-type="example3Backend"
          :disabled="example3Disabled"
        />
      </div>
    </div>

    <div class="example-section">
      <h2>示例 4: 跨后端支持</h2>
      <p>允许显示其他后端的模型（标记为不可用）</p>

      <div class="demo-component">
        <ModelSelector
          v-model="example4Model"
          :backend-type="example4Backend"
          :allow-cross-backend="true"
          @backend-mismatch="handleBackendMismatch"
        />
      </div>

      <div class="demo-output" v-if="lastMismatch">
        <strong>后端不匹配事件:</strong>
        <pre>{{ JSON.stringify(lastMismatch, null, 2) }}</pre>
      </div>
    </div>

    <div class="example-section">
      <h2>示例 5: 在设置面板中使用</h2>
      <p>完整的设置面板，包括后端选择、模型选择和 Thinking 配置</p>

      <div class="demo-component settings-panel">
        <div class="setting-row">
          <label>后端类型</label>
          <select v-model="example5Backend" class="backend-select">
            <option value="claude">Claude</option>
            <option value="codex">OpenAI Codex</option>
          </select>
        </div>

        <div class="setting-row">
          <label>模型</label>
          <ModelSelector
            v-model="example5Model"
            :backend-type="example5Backend"
            :show-details="true"
          />
        </div>

        <div class="setting-row">
          <label>Thinking 配置</label>
          <div class="thinking-config">
            <template v-if="example5Backend === 'claude'">
              <label>
                <input v-model="example5ThinkingEnabled" type="checkbox">
                启用 Thinking
              </label>
              <label v-if="example5ThinkingEnabled">
                Token Budget:
                <input
                  v-model.number="example5TokenBudget"
                  type="number"
                  min="0"
                  max="20000"
                  step="1024"
                >
              </label>
            </template>
            <template v-else>
              <label>
                Reasoning Effort:
                <select v-model="example5ReasoningEffort">
                  <option value="minimal">Minimal</option>
                  <option value="low">Low</option>
                  <option value="medium">Medium</option>
                  <option value="high">High</option>
                  <option value="xhigh">Extra High</option>
                </select>
              </label>
            </template>
          </div>
        </div>

        <div class="setting-row">
          <button class="primary-btn" @click="saveSettings">保存设置</button>
        </div>
      </div>
    </div>

    <div class="example-section">
      <h2>示例 6: 自动切换测试</h2>
      <p>测试后端切换时的自动模型切换逻辑</p>

      <div class="demo-controls">
        <button @click="autoSwitchTest">执行自动切换测试</button>
      </div>

      <div class="demo-component">
        <ModelSelector
          v-model="example6Model"
          :backend-type="example6Backend"
        />
      </div>

      <div class="demo-output">
        <strong>测试日志:</strong>
        <div class="log-output">
          <div v-for="(log, index) in testLogs" :key="index" class="log-entry">
            {{ log }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ModelSelector from './ModelSelector.vue'
import type { BackendType } from '@/types/backend'

// Example 1: 基础用法
const example1Backend = ref<BackendType>('claude')
const example1Model = ref('claude-sonnet-4-5-20251101')

// Example 2: 显示详细信息
const example2Backend = ref<BackendType>('claude')
const example2Model = ref('claude-sonnet-4-5-20251101')

function toggleBackend2() {
  example2Backend.value = example2Backend.value === 'claude' ? 'codex' : 'claude'
}

// Example 3: 禁用状态
const example3Backend = ref<BackendType>('claude')
const example3Model = ref('claude-sonnet-4-5-20251101')
const example3Disabled = ref(false)

// Example 4: 跨后端支持
const example4Backend = ref<BackendType>('claude')
const example4Model = ref('claude-sonnet-4-5-20251101')
const lastMismatch = ref<{ modelId: string; expectedBackend: BackendType } | null>(null)

function handleBackendMismatch(modelId: string, expectedBackend: BackendType) {
  lastMismatch.value = { modelId, expectedBackend }
  console.log('Backend mismatch:', modelId, expectedBackend)
}

// Example 5: 在设置面板中使用
const example5Backend = ref<BackendType>('claude')
const example5Model = ref('claude-sonnet-4-5-20251101')
const example5ThinkingEnabled = ref(true)
const example5TokenBudget = ref(8096)
const example5ReasoningEffort = ref('medium')

function saveSettings() {
  const settings = {
    backend: example5Backend.value,
    model: example5Model.value,
    thinking: example5Backend.value === 'claude'
      ? { enabled: example5ThinkingEnabled.value, tokenBudget: example5TokenBudget.value }
      : { effort: example5ReasoningEffort.value },
  }
  console.log('保存设置:', settings)
  alert('设置已保存！请查看控制台输出。')
}

// Example 6: 自动切换测试
const example6Backend = ref<BackendType>('claude')
const example6Model = ref('claude-sonnet-4-5-20251101')
const testLogs = ref<string[]>([])

async function autoSwitchTest() {
  testLogs.value = []

  function log(message: string) {
    testLogs.value.push(`[${new Date().toLocaleTimeString()}] ${message}`)
  }

  log('开始测试...')
  log(`初始状态: ${example6Backend.value} - ${example6Model.value}`)

  await new Promise(resolve => setTimeout(resolve, 500))

  log('切换到 Codex 后端...')
  example6Backend.value = 'codex'

  await new Promise(resolve => setTimeout(resolve, 500))
  log(`切换后模型: ${example6Model.value}`)

  await new Promise(resolve => setTimeout(resolve, 500))

  log('切换回 Claude 后端...')
  example6Backend.value = 'claude'

  await new Promise(resolve => setTimeout(resolve, 500))
  log(`切换后模型: ${example6Model.value}`)

  log('测试完成！')
}
</script>

<style scoped>
.model-selector-examples {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

h1 {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 40px;
  color: var(--theme-foreground, #24292e);
}

h2 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--theme-foreground, #24292e);
}

.example-section {
  margin-bottom: 48px;
  padding: 24px;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 8px;
  background: var(--theme-background, #ffffff);
}

.example-section > p {
  margin-bottom: 16px;
  color: var(--theme-muted-foreground, #656d76);
  font-size: 14px;
}

.demo-controls {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.demo-controls label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--theme-foreground, #24292e);
}

.demo-controls button {
  padding: 6px 12px;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 6px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.demo-controls button:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--theme-accent, #0366d6);
}

.backend-select {
  padding: 6px 10px;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 6px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  font-size: 13px;
  cursor: pointer;
}

.demo-component {
  margin-bottom: 16px;
}

.demo-output {
  padding: 12px;
  background: var(--theme-code-background, #f6f8fa);
  border-radius: 6px;
  font-size: 13px;
}

.demo-output pre {
  margin: 8px 0 0 0;
  font-family: 'Monaco', 'Courier New', monospace;
  color: var(--theme-foreground, #24292e);
}

.settings-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.setting-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setting-row > label {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
}

.thinking-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: var(--theme-code-background, #f6f8fa);
  border-radius: 6px;
}

.thinking-config label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.thinking-config input[type="number"],
.thinking-config select {
  padding: 6px 10px;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 6px;
  background: var(--theme-background, #ffffff);
  font-size: 13px;
}

.primary-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: var(--theme-accent, #0366d6);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease;
}

.primary-btn:hover {
  background: var(--theme-accent-hover, #0256c2);
}

.log-output {
  max-height: 200px;
  overflow-y: auto;
  margin-top: 8px;
  padding: 8px;
  background: #1e1e1e;
  border-radius: 4px;
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.log-entry {
  color: #d4d4d4;
  padding: 2px 0;
}

.log-entry:nth-child(odd) {
  background: rgba(255, 255, 255, 0.02);
}
</style>
