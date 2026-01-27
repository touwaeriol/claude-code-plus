/**
 * 工具 Diff 辅助函数
 *
 * 为 Edit/Write 工具提供 Diff 内容获取逻辑。
 * 支持从 LocalHistory 获取历史快照（通过 RSocket），或回退到参数 Diff。
 */

import { parseJbMeta } from './jbMetaParser'
import { ideaRSocket } from '@/services/ideaRSocket'

export interface DiffContent {
  /** 旧内容（修改前） */
  oldContent: string
  /** 新内容（修改后） */
  newContent: string
  /** 内容来源 */
  source: 'history' | 'params' | 'file'
}

export interface EditToolParams {
  file_path: string
  old_string: string
  new_string: string
}

export interface WriteToolParams {
  file_path: string
  content: string
}

/**
 * 获取 Edit 工具的 Diff 内容
 *
 * 优先级：
 * 1. 如果有 historyTs，尝试从 LocalHistory 获取完整文件快照（通过 RSocket）
 * 2. 回退到参数 Diff（oldString → newString）
 *
 * @param params Edit 工具参数
 * @param result 工具执行结果
 * @param currentContent 当前文件内容（可选，用于完整 Diff）
 * @returns Diff 内容
 */
export async function getEditDiffContent(
  params: EditToolParams,
  result: string,
  currentContent?: string
): Promise<DiffContent> {
  const { meta } = parseJbMeta(result)

  // 尝试从 LocalHistory 获取历史内容（通过 RSocket）
  if (meta.historyTs && ideaRSocket.isConnected()) {
    try {
      const content = await ideaRSocket.getFileHistoryContent(params.file_path, meta.historyTs)
      if (content) {
        // 成功获取历史内容，使用完整文件 Diff
        return {
          oldContent: content,
          newContent: currentContent || '', // 需要获取当前内容
          source: 'history'
        }
      }
    } catch (e) {
      console.warn('[toolDiffHelper] Failed to get history content via RSocket:', e)
    }
  }

  // 回退到参数 Diff
  return {
    oldContent: params.old_string,
    newContent: params.new_string,
    source: 'params'
  }
}

/**
 * 获取 Write 工具的 Diff 内容
 *
 * 优先级：
 * 1. 如果是覆写且有 historyTs，尝试从 LocalHistory 获取原始内容（通过 RSocket）
 * 2. 如果是新建文件，oldContent 为空
 * 3. 回退到空内容 → 新内容
 *
 * @param params Write 工具参数
 * @param result 工具执行结果
 * @returns Diff 内容
 */
export async function getWriteDiffContent(
  params: WriteToolParams,
  result: string
): Promise<DiffContent> {
  const { meta } = parseJbMeta(result)

  // 如果是覆写操作，尝试获取历史内容（通过 RSocket）
  if (meta.isOverwrite && meta.historyTs && ideaRSocket.isConnected()) {
    try {
      const content = await ideaRSocket.getFileHistoryContent(params.file_path, meta.historyTs)
      if (content) {
        return {
          oldContent: content,
          newContent: params.content,
          source: 'history'
        }
      }
    } catch (e) {
      console.warn('[toolDiffHelper] Failed to get history content via RSocket:', e)
    }
  }

  // 新建文件或无法获取历史：空 → 新内容
  return {
    oldContent: meta.isOverwrite === false ? '' : '(无法获取原始内容)',
    newContent: params.content,
    source: meta.isOverwrite === false ? 'params' : 'file'
  }
}

/**
 * 检查是否可以显示完整文件 Diff
 *
 * @param result 工具执行结果
 * @returns 是否有足够信息显示完整 Diff
 */
export function canShowFullDiff(result: string): boolean {
  const { meta } = parseJbMeta(result)
  return meta.historyTs !== undefined
}
