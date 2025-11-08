/**
 * 待处理任务类型定义
 * 对应 Kotlin 的 PendingTask 数据类
 */

/**
 * 任务类型枚举
 */
export enum TaskType {
  SWITCH_MODEL = 'SWITCH_MODEL',
  QUERY = 'QUERY'
}

/**
 * 任务状态枚举
 */
export enum TaskStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED'
}

/**
 * 待处理任务接口
 */
export interface PendingTask {
  id: string
  type: TaskType
  text: string
  alias?: string
  status: TaskStatus
  realModelId?: string
  error?: string
}

/**
 * 创建一个新的待处理任务
 */
export function createPendingTask(
  type: TaskType,
  text: string,
  alias?: string
): PendingTask {
  return {
    id: `task-${Date.now()}-${Math.random()}`,
    type,
    text,
    alias,
    status: TaskStatus.PENDING
  }
}

/**
 * 创建模型切换任务
 */
export function createSwitchModelTask(
  modelAlias: string,
  realModelId?: string
): PendingTask {
  return {
    id: `switch-${Date.now()}-${Math.random()}`,
    type: TaskType.SWITCH_MODEL,
    text: `切换到 ${modelAlias}`,
    alias: modelAlias,
    status: TaskStatus.PENDING,
    realModelId
  }
}

/**
 * 创建查询任务
 */
export function createQueryTask(queryText: string): PendingTask {
  return {
    id: `query-${Date.now()}-${Math.random()}`,
    type: TaskType.QUERY,
    text: queryText,
    status: TaskStatus.PENDING
  }
}

/**
 * 更新任务状态
 */
export function updateTaskStatus(
  task: PendingTask,
  status: TaskStatus,
  error?: string
): PendingTask {
  return {
    ...task,
    status,
    error
  }
}

/**
 * 检查任务是否完成(成功或失败)
 */
export function isTaskCompleted(task: PendingTask): boolean {
  return task.status === TaskStatus.SUCCESS || task.status === TaskStatus.FAILED
}

/**
 * 检查任务是否正在运行
 */
export function isTaskRunning(task: PendingTask): boolean {
  return task.status === TaskStatus.RUNNING
}

/**
 * 检查任务是否待处理
 */
export function isTaskPending(task: PendingTask): boolean {
  return task.status === TaskStatus.PENDING
}

/**
 * 获取任务显示文本
 */
export function getTaskDisplayText(task: PendingTask): string {
  if (task.type === TaskType.SWITCH_MODEL) {
    return task.alias || task.text
  }
  // 截断过长的查询文本
  const maxLength = 50
  if (task.text.length > maxLength) {
    return task.text.substring(0, maxLength) + '...'
  }
  return task.text
}

/**
 * 获取任务状态显示文本
 */
export function getTaskStatusText(status: TaskStatus): string {
  const statusMap: Record<TaskStatus, string> = {
    [TaskStatus.PENDING]: '等待',
    [TaskStatus.RUNNING]: '执行中',
    [TaskStatus.SUCCESS]: '成功',
    [TaskStatus.FAILED]: '失败'
  }
  return statusMap[status] || status
}
