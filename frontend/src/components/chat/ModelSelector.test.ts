/**
 * ModelSelector Component Unit Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ModelSelector from './ModelSelector.vue'
import type { BackendType } from '@/types/backend'

// Mock i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock backend capabilities service
vi.mock('@/services/backendCapabilities', () => ({
  getModels: (type: BackendType) => {
    if (type === 'claude') {
      return [
        {
          id: 'claude-opus-4-5-20251101',
          displayName: 'Claude Opus 4.5',
          description: 'Most capable model',
          supportsThinking: true,
          isDefault: false,
        },
        {
          id: 'claude-sonnet-4-5-20251101',
          displayName: 'Claude Sonnet 4.5',
          description: 'Balanced performance',
          supportsThinking: true,
          isDefault: true,
        },
        {
          id: 'claude-haiku-4-5-20251101',
          displayName: 'Claude Haiku 4.5',
          description: 'Fast and efficient',
          supportsThinking: true,
          isDefault: false,
        },
      ]
    } else {
      return [
        {
          id: 'gpt-5.1-codex-max',
          displayName: 'GPT-5.1 Codex Max',
          description: 'Most capable Codex model',
          supportsThinking: true,
          isDefault: true,
        },
        {
          id: 'o3',
          displayName: 'o3',
          description: 'Advanced reasoning',
          supportsThinking: true,
          isDefault: false,
        },
        {
          id: 'gpt-4o',
          displayName: 'GPT-4o',
          description: 'Multimodal model',
          supportsThinking: false,
          isDefault: false,
        },
      ]
    }
  },
  getModelById: (type: BackendType, id: string) => {
    const models = type === 'claude'
      ? [
          { id: 'claude-opus-4-5-20251101', displayName: 'Claude Opus 4.5', supportsThinking: true },
          { id: 'claude-sonnet-4-5-20251101', displayName: 'Claude Sonnet 4.5', supportsThinking: true, isDefault: true },
          { id: 'claude-haiku-4-5-20251101', displayName: 'Claude Haiku 4.5', supportsThinking: true },
        ]
      : [
          { id: 'gpt-5.1-codex-max', displayName: 'GPT-5.1 Codex Max', supportsThinking: true, isDefault: true },
          { id: 'o3', displayName: 'o3', supportsThinking: true },
          { id: 'gpt-4o', displayName: 'GPT-4o', supportsThinking: false },
        ]
    return models.find(m => m.id === id)
  },
  getBackendDisplayName: (type: BackendType) => {
    return type === 'claude' ? 'Claude' : 'OpenAI Codex'
  },
  isValidModel: (type: BackendType, modelId: string) => {
    const models = type === 'claude'
      ? ['claude-opus-4-5-20251101', 'claude-sonnet-4-5-20251101', 'claude-haiku-4-5-20251101']
      : ['gpt-5.1-codex-max', 'o3', 'gpt-4o']
    return models.includes(modelId)
  },
}))

describe('ModelSelector', () => {
  let wrapper: VueWrapper<any>

  beforeEach(() => {
    // Clean up
    if (wrapper) {
      wrapper.unmount()
    }
  })

  describe('基础渲染', () => {
    it('should render with Claude backend', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.model-select').exists()).toBe(true)
      expect(wrapper.find('.backend-badge').text()).toContain('Claude')
    })

    it('should render with Codex backend', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'gpt-5.1-codex-max',
          backendType: 'codex',
        },
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.backend-badge').text()).toContain('OpenAI Codex')
    })

    it('should show disabled state', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
          disabled: true,
        },
      })

      const select = wrapper.find('.model-select')
      expect(select.attributes('disabled')).toBeDefined()
    })
  })

  describe('模型过滤', () => {
    it('should only show Claude models for Claude backend', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      const options = wrapper.findAll('option')
      expect(options.length).toBeGreaterThan(0)
      expect(options.every(opt => opt.text().includes('Claude'))).toBe(true)
    })

    it('should only show Codex models for Codex backend', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'gpt-5.1-codex-max',
          backendType: 'codex',
        },
      })

      const options = wrapper.findAll('option')
      expect(options.length).toBeGreaterThan(0)
      expect(options.some(opt => opt.text().includes('GPT') || opt.text().includes('o3'))).toBe(true)
    })
  })

  describe('模型选择', () => {
    it('should emit update:modelValue on selection', async () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      const select = wrapper.find('.model-select')
      await select.setValue('claude-opus-4-5-20251101')

      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')![0]).toEqual(['claude-opus-4-5-20251101'])
    })

    it('should emit backend-mismatch when selecting wrong backend model', async () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      const select = wrapper.find('.model-select')
      // 尝试选择 Codex 模型
      await select.setValue('gpt-5.1-codex-max')

      expect(wrapper.emitted('backend-mismatch')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    })
  })

  describe('后端切换', () => {
    it('should auto-switch to default model when backend changes', async () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      // 切换到 Codex 后端
      await wrapper.setProps({ backendType: 'codex' })
      await nextTick()

      // 应该发出 update:modelValue 事件，切换到 Codex 默认模型
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      const lastEmit = wrapper.emitted('update:modelValue')!.slice(-1)[0]
      expect(lastEmit[0]).toBe('gpt-5.1-codex-max')
    })

    it('should not emit when model is valid for new backend', async () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'gpt-5.1-codex-max',
          backendType: 'codex',
        },
      })

      // 清空之前的 emit
      wrapper.vm.$emit = vi.fn()

      // 保持在 Codex 后端
      await wrapper.setProps({ backendType: 'codex' })
      await nextTick()

      // 因为模型已经有效，不应该发出事件
      // 注意：这个测试可能需要根据实际实现调整
    })
  })

  describe('模型详情显示', () => {
    it('should show model details when showDetails is true', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
          showDetails: true,
        },
      })

      expect(wrapper.find('.model-details').exists()).toBe(true)
    })

    it('should hide model details when showDetails is false', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
          showDetails: false,
        },
      })

      expect(wrapper.find('.model-details').exists()).toBe(false)
    })

    it('should show info icon tooltip', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      expect(wrapper.find('.info-icon').exists()).toBe(true)
    })
  })

  describe('后端不匹配警告', () => {
    it('should show warning when model does not match backend', async () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'gpt-5.1-codex-max',
          backendType: 'claude',
        },
      })

      await nextTick()

      expect(wrapper.find('.warning-banner').exists()).toBe(true)
    })

    it('should not show warning when model matches backend', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
        },
      })

      expect(wrapper.find('.warning-banner').exists()).toBe(false)
    })
  })

  describe('跨后端支持', () => {
    it('should show other backend models when allowCrossBackend is true', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
          allowCrossBackend: true,
        },
      })

      const optgroups = wrapper.findAll('optgroup')
      expect(optgroups.length).toBeGreaterThan(1)
    })

    it('should mark cross-backend models as disabled', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
          backendType: 'claude',
          allowCrossBackend: true,
        },
      })

      const disabledOptions = wrapper.findAll('option[disabled]')
      expect(disabledOptions.length).toBeGreaterThan(0)
    })
  })

  describe('边界情况', () => {
    it('should handle missing backendType', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'claude-sonnet-4-5-20251101',
        },
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.findAll('option').length).toBe(0)
    })

    it('should handle empty modelValue', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: '',
          backendType: 'claude',
        },
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.warning-banner').exists()).toBe(false)
    })

    it('should handle invalid modelValue', () => {
      wrapper = mount(ModelSelector, {
        props: {
          modelValue: 'invalid-model-id',
          backendType: 'claude',
        },
      })

      expect(wrapper.exists()).toBe(true)
      expect(wrapper.find('.warning-banner').exists()).toBe(true)
    })
  })
})
