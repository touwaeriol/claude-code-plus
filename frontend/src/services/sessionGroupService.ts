/**
 * 会话分组和标签管理服务
 */

import type { SessionGroup, SessionTag } from '@/types/sessionGroup'

export class SessionGroupService {
  private groups: Map<string, SessionGroup> = new Map()
  private tags: Map<string, SessionTag> = new Map()
  private sessionGroupMap: Map<string, string> = new Map() // sessionId -> groupId
  private sessionTagsMap: Map<string, Set<string>> = new Map() // sessionId -> Set<tagId>

  // ==================== 分组管理 ====================

  /**
   * 创建分组
   */
  createGroup(group: Omit<SessionGroup, 'id'>): SessionGroup {
    const id = this.generateId()
    const newGroup: SessionGroup = { ...group, id }
    this.groups.set(id, newGroup)
    this.saveToStorage()
    return newGroup
  }

  /**
   * 更新分组
   */
  updateGroup(id: string, updates: Partial<SessionGroup>): SessionGroup | null {
    const group = this.groups.get(id)
    if (!group) return null

    const updated = { ...group, ...updates, id } // 确保 id 不被覆盖
    this.groups.set(id, updated)
    this.saveToStorage()
    return updated
  }

  /**
   * 删除分组
   */
  deleteGroup(id: string): boolean {
    const deleted = this.groups.delete(id)
    if (deleted) {
      // 移除所有会话的分组关联
      for (const [sessionId, groupId] of this.sessionGroupMap.entries()) {
        if (groupId === id) {
          this.sessionGroupMap.delete(sessionId)
        }
      }
      this.saveToStorage()
    }
    return deleted
  }

  /**
   * 获取所有分组
   */
  getAllGroups(): SessionGroup[] {
    return Array.from(this.groups.values()).sort((a, b) => a.order - b.order)
  }

  /**
   * 获取分组
   */
  getGroup(id: string): SessionGroup | null {
    return this.groups.get(id) || null
  }

  /**
   * 切换分组折叠状态
   */
  toggleGroupCollapse(id: string): boolean {
    const group = this.groups.get(id)
    if (!group) return false

    group.isCollapsed = !group.isCollapsed
    this.groups.set(id, group)
    this.saveToStorage()
    return group.isCollapsed
  }

  // ==================== 标签管理 ====================

  /**
   * 创建标签
   */
  createTag(tag: Omit<SessionTag, 'id'>): SessionTag {
    const id = this.generateId()
    const newTag: SessionTag = { ...tag, id }
    this.tags.set(id, newTag)
    this.saveToStorage()
    return newTag
  }

  /**
   * 更新标签
   */
  updateTag(id: string, updates: Partial<SessionTag>): SessionTag | null {
    const tag = this.tags.get(id)
    if (!tag) return null

    const updated = { ...tag, ...updates, id }
    this.tags.set(id, updated)
    this.saveToStorage()
    return updated
  }

  /**
   * 删除标签
   */
  deleteTag(id: string): boolean {
    const deleted = this.tags.delete(id)
    if (deleted) {
      // 移除所有会话的标签关联
      for (const tagSet of this.sessionTagsMap.values()) {
        tagSet.delete(id)
      }
      this.saveToStorage()
    }
    return deleted
  }

  /**
   * 获取所有标签
   */
  getAllTags(): SessionTag[] {
    return Array.from(this.tags.values())
  }

  /**
   * 获取标签
   */
  getTag(id: string): SessionTag | null {
    return this.tags.get(id) || null
  }

  // ==================== 会话关联管理 ====================

  /**
   * 设置会话的分组
   */
  setSessionGroup(sessionId: string, groupId: string | null): void {
    if (groupId === null) {
      this.sessionGroupMap.delete(sessionId)
    } else {
      this.sessionGroupMap.set(sessionId, groupId)
    }
    this.saveToStorage()
  }

  /**
   * 获取会话的分组
   */
  getSessionGroup(sessionId: string): string | null {
    return this.sessionGroupMap.get(sessionId) || null
  }

  /**
   * 添加会话标签
   */
  addSessionTag(sessionId: string, tagId: string): void {
    if (!this.sessionTagsMap.has(sessionId)) {
      this.sessionTagsMap.set(sessionId, new Set())
    }
    this.sessionTagsMap.get(sessionId)!.add(tagId)
    this.saveToStorage()
  }

  /**
   * 移除会话标签
   */
  removeSessionTag(sessionId: string, tagId: string): void {
    const tagSet = this.sessionTagsMap.get(sessionId)
    if (tagSet) {
      tagSet.delete(tagId)
      if (tagSet.size === 0) {
        this.sessionTagsMap.delete(sessionId)
      }
      this.saveToStorage()
    }
  }

  /**
   * 获取会话的所有标签
   */
  getSessionTags(sessionId: string): SessionTag[] {
    const tagIds = this.sessionTagsMap.get(sessionId)
    if (!tagIds) return []

    return Array.from(tagIds)
      .map(id => this.tags.get(id))
      .filter((tag): tag is SessionTag => tag !== undefined)
  }

  /**
   * 获取分组中的所有会话
   */
  getSessionsInGroup(groupId: string): string[] {
    const sessions: string[] = []
    for (const [sessionId, gId] of this.sessionGroupMap.entries()) {
      if (gId === groupId) {
        sessions.push(sessionId)
      }
    }
    return sessions
  }

  /**
   * 获取带有特定标签的所有会话
   */
  getSessionsWithTag(tagId: string): string[] {
    const sessions: string[] = []
    for (const [sessionId, tagSet] of this.sessionTagsMap.entries()) {
      if (tagSet.has(tagId)) {
        sessions.push(sessionId)
      }
    }
    return sessions
  }

  // ==================== 持久化 ====================

  /**
   * 保存到 localStorage
   */
  private saveToStorage(): void {
    const data = {
      groups: Array.from(this.groups.entries()),
      tags: Array.from(this.tags.entries()),
      sessionGroupMap: Array.from(this.sessionGroupMap.entries()),
      sessionTagsMap: Array.from(this.sessionTagsMap.entries()).map(([sessionId, tagSet]) => [
        sessionId,
        Array.from(tagSet)
      ])
    }
    localStorage.setItem('sessionGroupData', JSON.stringify(data))
  }

  /**
   * 从 localStorage 加载
   */
  loadFromStorage(): void {
    const data = localStorage.getItem('sessionGroupData')
    if (!data) return

    try {
      const parsed = JSON.parse(data)
      this.groups = new Map(parsed.groups || [])
      this.tags = new Map(parsed.tags || [])
      this.sessionGroupMap = new Map(parsed.sessionGroupMap || [])
      this.sessionTagsMap = new Map(
        (parsed.sessionTagsMap || []).map(([sessionId, tagIds]: [string, string[]]) => [
          sessionId,
          new Set(tagIds)
        ])
      )
    } catch (error) {
      console.error('Failed to load session group data:', error)
    }
  }

  /**
   * 生成唯一 ID
   */
  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  }
}

// 导出单例
export const sessionGroupService = new SessionGroupService()
sessionGroupService.loadFromStorage()


