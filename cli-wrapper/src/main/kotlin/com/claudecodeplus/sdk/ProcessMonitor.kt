package com.claudecodeplus.sdk

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 进程监控器
 * 管理所有运行中的 Claude CLI 进程，提供统一的生命周期管理
 * 确保进程正确清理，防止资源泄漏
 */
class ProcessMonitor {
    
    private val activeProcesses = ConcurrentHashMap<String, ProcessInfo>()
    private val processCounter = AtomicLong(0)
    
    /**
     * 进程信息
     */
    data class ProcessInfo(
        val process: Process,
        val sessionId: String?,
        val startTime: Long = System.currentTimeMillis(),
        val projectPath: String
    )
    
    /**
     * 注册进程
     * 
     * @param sessionId 会话ID（可能为null，新会话）
     * @param process 进程实例
     * @param projectPath 项目路径
     * @return 进程跟踪ID
     */
    fun registerProcess(sessionId: String?, process: Process, projectPath: String): String {
        val trackingId = "process_${processCounter.incrementAndGet()}_${System.currentTimeMillis()}"
        val processInfo = ProcessInfo(
            process = process,
            sessionId = sessionId,
            projectPath = projectPath
        )
        
        activeProcesses[trackingId] = processInfo
        
        println("Registered process: $trackingId (PID: ${process.pid()}, Session: $sessionId)")
        
        // 启动进程监控（异步）
        monitorProcess(trackingId, processInfo)
        
        return trackingId
    }
    
    /**
     * 终止指定会话的进程
     */
    fun terminateSession(sessionId: String, forceful: Boolean = false) {
        val processesToTerminate = activeProcesses.filter { (_, info) ->
            info.sessionId == sessionId
        }
        
        processesToTerminate.forEach { (trackingId, info) ->
            terminateProcess(trackingId, forceful)
        }
    }
    
    /**
     * 终止指定的进程
     */
    fun terminateProcess(trackingId: String, forceful: Boolean = false) {
        val processInfo = activeProcesses[trackingId] ?: return
        
        try {
            val process = processInfo.process
            if (process.isAlive) {
                if (forceful) {
                    process.destroyForcibly()
                    println("Forcefully terminated process: $trackingId (PID: ${process.pid()})")
                } else {
                    process.destroy()
                    println("Gracefully terminated process: $trackingId (PID: ${process.pid()})")
                }
            }
        } catch (e: Exception) {
            println("Error terminating process $trackingId: ${e.message}")
        } finally {
            activeProcesses.remove(trackingId)
        }
    }
    
    /**
     * 终止所有进程
     */
    fun terminateAll(forceful: Boolean = false) {
        val processIds = activeProcesses.keys.toList()
        processIds.forEach { trackingId ->
            terminateProcess(trackingId, forceful)
        }
        println("Terminated ${processIds.size} processes")
    }
    
    /**
     * 获取活动进程信息
     */
    fun getActiveProcesses(): Map<String, ProcessInfo> {
        // 清理已结束的进程
        cleanupDeadProcesses()
        return activeProcesses.toMap()
    }
    
    /**
     * 获取指定会话的进程
     */
    fun getSessionProcesses(sessionId: String): List<Pair<String, ProcessInfo>> {
        cleanupDeadProcesses()
        return activeProcesses.filter { (_, info) ->
            info.sessionId == sessionId
        }.toList()
    }
    
    /**
     * 检查进程是否存活
     */
    fun isProcessAlive(trackingId: String): Boolean {
        val processInfo = activeProcesses[trackingId]
        return processInfo?.process?.isAlive ?: false
    }
    
    /**
     * 监控进程（异步）
     */
    private fun monitorProcess(trackingId: String, processInfo: ProcessInfo) {
        Thread {
            try {
                val exitCode = processInfo.process.waitFor()
                val duration = System.currentTimeMillis() - processInfo.startTime
                
                println("Process $trackingId finished with exit code $exitCode after ${duration}ms")
                
                // 进程结束后自动清理
                activeProcesses.remove(trackingId)
            } catch (e: InterruptedException) {
                println("Process monitor interrupted for $trackingId")
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                println("Error monitoring process $trackingId: ${e.message}")
            }
        }.apply {
            name = "ProcessMonitor-$trackingId"
            isDaemon = true
            start()
        }
    }
    
    /**
     * 清理已结束的进程
     */
    private fun cleanupDeadProcesses() {
        val deadProcesses = activeProcesses.filter { (_, info) ->
            !info.process.isAlive
        }
        
        deadProcesses.forEach { (trackingId, _) ->
            activeProcesses.remove(trackingId)
        }
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): ProcessStatistics {
        cleanupDeadProcesses()
        val processes = activeProcesses.values
        
        return ProcessStatistics(
            totalProcesses = processes.size,
            sessionProcesses = processes.groupBy { it.sessionId }.size,
            oldestProcessTime = processes.minOfOrNull { it.startTime },
            averageLifetime = if (processes.isNotEmpty()) {
                processes.map { System.currentTimeMillis() - it.startTime }.average()
            } else 0.0
        )
    }
    
    /**
     * 进程统计信息
     */
    data class ProcessStatistics(
        val totalProcesses: Int,
        val sessionProcesses: Int,
        val oldestProcessTime: Long?,
        val averageLifetime: Double
    )
    
    companion object {
        /**
         * 全局进程监控器实例
         */
        val instance = ProcessMonitor()
    }
}