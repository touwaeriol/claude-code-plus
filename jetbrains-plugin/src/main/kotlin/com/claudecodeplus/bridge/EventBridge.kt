package com.claudecodeplus.bridge

/**
 * 事件桥接接口
 * 用于后端向前端推送事件
 */
interface EventBridge {
    /**
     * 推送事件到前端
     */
    fun pushEvent(event: IdeEvent)
}
