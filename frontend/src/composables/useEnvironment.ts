/**
 * 环境检测 Composable
 *
 * 提供运行环境的全局状态管理
 * - IDE 模式: 在 IDEA 插件中运行（通过 RSocket 与后端通信）
 * - Browser 模式: 在浏览器中访问（使用默认 URL）
 */

import { ref, computed } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'

const bridgeMode = ref<'ide' | 'browser'>('browser')
const environmentReady = ref(false)

export function useEnvironment() {
  const isInIde = computed(() => bridgeMode.value === 'ide')
  const isInBrowser = computed(() => bridgeMode.value === 'browser')

  async function detectEnvironment() {
    if (environmentReady.value) return

    await ideaBridge.waitForReady()

    // 使用 ideaBridge 的模式检测
    bridgeMode.value = ideaBridge.getMode()

    environmentReady.value = true
    console.log(`🔍 [useEnvironment] 环境检测完成: ${bridgeMode.value}`)
  }

  return {
    isInIde,
    isInBrowser,
    environmentReady,
    detectEnvironment
  }
}
