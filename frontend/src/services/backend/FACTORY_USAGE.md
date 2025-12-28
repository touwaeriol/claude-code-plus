# BackendSessionFactory 使用指南

## 概述

`BackendSessionFactory` 提供了统一的接口来创建和管理不同类型的后端会话（Claude、Codex）。

## 核心功能

### 1. 创建会话

#### 基本创建
```typescript
import { createSession } from '@/services/backend'

// 创建 Claude 会话
const claudeSession = createSession('claude')

// 创建 Codex 会话
const codexSession = createSession('codex')
```

#### 使用自定义配置
```typescript
import { createSession } from '@/services/backend'
import type { ClaudeBackendConfig } from '@/services/backend'

const config: ClaudeBackendConfig = {
  type: 'claude',
  modelId: 'claude-opus-4-5-20251101',
  permissionMode: 'auto-approve',
  skipPermissions: true,
  thinkingEnabled: true,
  thinkingTokenBudget: 10000,
  includePartialMessages: true,
}

const session = createSession('claude', config)
```

#### 创建并立即连接
```typescript
import { createAndConnectSession } from '@/services/backend'
import type { SessionConnectOptions } from '@/services/backend'

const options: SessionConnectOptions = {
  config: {
    type: 'claude',
    modelId: 'claude-sonnet-4-5-20251101',
    permissionMode: 'default',
    skipPermissions: false,
    thinkingEnabled: true,
    thinkingTokenBudget: 8096,
    includePartialMessages: true,
  },
  projectPath: '/path/to/project',
  continueConversation: false,
}

const session = await createAndConnectSession('claude', options)

// 会话已连接，可以直接使用
session.sendMessage({
  contents: [{ type: 'text', text: 'Hello!' }]
})
```

### 2. 使用默认后端

```typescript
import { createDefaultSession } from '@/services/backend'

// 自动选择最佳可用后端（会检查可用性）
const session = await createDefaultSession()

// 或者不检查可用性，直接使用首选后端
const sessionNoCheck = await createDefaultSession(undefined, false)
```

### 3. 检查后端可用性

#### 检查单个后端
```typescript
import { isBackendAvailable } from '@/services/backend'

const claudeAvailable = await isBackendAvailable('claude')
if (claudeAvailable) {
  console.log('Claude backend is ready')
}

const codexAvailable = await isBackendAvailable('codex')
if (codexAvailable) {
  console.log('Codex backend is ready')
}
```

#### 获取所有可用后端
```typescript
import { getAvailableBackendTypes } from '@/services/backend'

const availableBackends = await getAvailableBackendTypes()
console.log('Available backends:', availableBackends)
// 输出: ['claude', 'codex'] 或 ['claude'] 或 ['codex'] 等
```

#### 获取已注册的后端
```typescript
import { getRegisteredBackendTypes } from '@/services/backend'

// 不检查可用性，只返回已注册的后端类型
const registered = getRegisteredBackendTypes()
console.log('Registered backends:', registered)
```

### 4. 获取默认后端类型

#### 异步版本（检查可用性）
```typescript
import { getDefaultBackendType } from '@/services/backend'

// 根据可用性和偏好顺序选择默认后端
const defaultType = await getDefaultBackendType()
console.log('Default backend:', defaultType)

// 不检查可用性，只返回偏好列表中的第一个
const defaultTypeNoCheck = await getDefaultBackendType(false)
```

#### 同步版本（不检查可用性）
```typescript
import { getDefaultBackendTypeSync } from '@/services/backend'

// 立即返回默认后端类型（基于注册和偏好）
const defaultType = getDefaultBackendTypeSync()
```

### 5. 注册会话实现

当 `ClaudeSession` 和 `CodexSession` 实现完成后，需要注册它们：

```typescript
import { registerSessionImplementation } from '@/services/backend'
import { ClaudeSession } from './ClaudeSession'
import { CodexSession } from './CodexSession'

// 在应用初始化时注册
registerSessionImplementation('claude', ClaudeSession)
registerSessionImplementation('codex', CodexSession)
```

或者在各自的模块中自动注册：

```typescript
// ClaudeSession.ts
import { registerSessionImplementation } from './BackendSessionFactory'

export class ClaudeSession extends BaseBackendSession {
  // ... implementation
}

// 自动注册
registerSessionImplementation('claude', ClaudeSession)
```

## 错误处理

```typescript
import {
  createSession,
  BackendFactoryError
} from '@/services/backend'

try {
  const session = createSession('claude')
  await session.connect({ config: myConfig })
} catch (error) {
  if (error instanceof BackendFactoryError) {
    console.error('Factory error:', error.message)
    console.error('Backend type:', error.backendType)
    console.error('Cause:', error.cause)
  } else {
    console.error('Unknown error:', error)
  }
}
```

## 完整示例

### UI 组件中使用

```typescript
import { ref, onMounted } from 'vue'
import {
  createSession,
  getAvailableBackendTypes,
  type BackendType,
  type BackendSession,
} from '@/services/backend'

export default {
  setup() {
    const availableBackends = ref<BackendType[]>([])
    const selectedBackend = ref<BackendType>('claude')
    const session = ref<BackendSession | null>(null)

    // 加载可用后端
    onMounted(async () => {
      availableBackends.value = await getAvailableBackendTypes()
      if (availableBackends.value.length > 0) {
        selectedBackend.value = availableBackends.value[0]
      }
    })

    // 创建会话
    const createNewSession = async () => {
      try {
        session.value = createSession(selectedBackend.value)
        await session.value.connect({
          config: session.value.getState().config,
        })
        console.log('Session connected!')
      } catch (error) {
        console.error('Failed to create session:', error)
      }
    }

    // 发送消息
    const sendMessage = (text: string) => {
      if (session.value && session.value.isConnected()) {
        session.value.sendMessage({
          contents: [{ type: 'text', text }]
        })
      }
    }

    return {
      availableBackends,
      selectedBackend,
      createNewSession,
      sendMessage,
    }
  }
}
```

## 注意事项

1. **可用性检查是异步的**：`isBackendAvailable()` 和 `getAvailableBackendTypes()` 会进行网络请求，需要 await
2. **注册必须在创建之前**：确保在调用 `createSession()` 之前已经注册了对应的实现
3. **错误处理**：始终使用 try-catch 包裹会话创建和连接操作
4. **资源清理**：不再使用会话时调用 `session.disconnect()` 释放资源

## API 参考

### 函数

- `createSession(type, config?)` - 创建会话
- `createAndConnectSession(type, options)` - 创建并连接会话
- `createDefaultSession(config?, checkAvailability?)` - 创建默认后端会话
- `isBackendAvailable(type)` - 检查后端是否可用
- `getAvailableBackendTypes()` - 获取所有可用后端
- `getRegisteredBackendTypes()` - 获取所有已注册后端
- `getDefaultBackendType(checkAvailability?)` - 获取默认后端类型（异步）
- `getDefaultBackendTypeSync()` - 获取默认后端类型（同步）
- `registerSessionImplementation(type, sessionClass)` - 注册会话实现

### 对象

- `BackendSessionFactory` - 包含所有工厂方法的对象
- `backendFactory` - 默认导出，同 `BackendSessionFactory`

### 异常

- `BackendFactoryError` - 工厂操作失败时抛出的异常
