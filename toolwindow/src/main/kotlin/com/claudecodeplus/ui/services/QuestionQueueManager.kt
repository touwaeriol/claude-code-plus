package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import com.claudecodeplus.sdk.ClaudeCliWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID

/**
 * 批量问题队列管理器
 */
class QuestionQueueManager(
    private val cliWrapper: ClaudeCliWrapper
) {
    private val _queue = MutableStateFlow<List<QueuedQuestion>>(emptyList())
    val queue: StateFlow<List<QueuedQuestion>> = _queue.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _currentQuestion = MutableStateFlow<QueuedQuestion?>(null)
    val currentQuestion: StateFlow<QueuedQuestion?> = _currentQuestion.asStateFlow()
    
    private val _progress = MutableStateFlow(QueueProgress(0, 0))
    val progress: StateFlow<QueueProgress> = _progress.asStateFlow()
    
    private var processingJob: Job? = null
    
    /**
     * 添加问题到队列
     */
    fun addQuestion(
        content: String,
        context: List<ContextItem> = emptyList(),
        priority: Int = 0
    ): String {
        val question = QueuedQuestion(
            content = content,
            context = context,
            priority = priority
        )
        
        _queue.value = (_queue.value + question).sortedByDescending { it.priority }
        updateProgress()
        
        return question.id
    }
    
    /**
     * 批量添加问题
     */
    fun addQuestions(questions: List<Pair<String, List<ContextItem>>>) {
        val newQuestions = questions.mapIndexed { index, (content, context) ->
            QueuedQuestion(
                content = content,
                context = context,
                priority = questions.size - index // 按添加顺序设置优先级
            )
        }
        
        _queue.value = (_queue.value + newQuestions).sortedByDescending { it.priority }
        updateProgress()
    }
    
    /**
     * 开始处理队列
     */
    suspend fun startProcessing(
        sessionId: String? = null,
        onQuestionComplete: suspend (QueuedQuestion) -> Unit = {},
        onError: suspend (QueuedQuestion, Throwable) -> Unit = { _, _ -> }
    ) {
        if (_isProcessing.value) return
        
        _isProcessing.value = true
        processingJob = coroutineScope {
            launch {
                processQueue(sessionId, onQuestionComplete, onError)
            }
        }
    }
    
    /**
     * 暂停处理
     */
    fun pauseProcessing() {
        processingJob?.cancel()
        _isProcessing.value = false
        
        // 将当前问题状态改回待处理
        _currentQuestion.value?.let { question ->
            updateQuestionStatus(question.id, QueuedQuestion.QuestionStatus.PENDING)
            _currentQuestion.value = null
        }
    }
    
    /**
     * 停止处理并清空队列
     */
    fun stopAndClear() {
        pauseProcessing()
        _queue.value = emptyList()
        _currentQuestion.value = null
        updateProgress()
    }
    
    /**
     * 移除问题
     */
    fun removeQuestion(questionId: String) {
        _queue.value = _queue.value.filter { it.id != questionId }
        updateProgress()
    }
    
    /**
     * 调整优先级
     */
    fun updatePriority(questionId: String, newPriority: Int) {
        _queue.value = _queue.value.map { question ->
            if (question.id == questionId) {
                question.copy(priority = newPriority)
            } else {
                question
            }
        }.sortedByDescending { it.priority }
    }
    
    /**
     * 重试失败的问题
     */
    fun retryFailed() {
        _queue.value = _queue.value.map { question ->
            if (question.status == QueuedQuestion.QuestionStatus.FAILED) {
                question.copy(
                    status = QueuedQuestion.QuestionStatus.PENDING,
                    error = null
                )
            } else {
                question
            }
        }
    }
    
    /**
     * 获取队列统计
     */
    fun getStatistics(): QueueStatistics {
        val questions = _queue.value
        return QueueStatistics(
            total = questions.size,
            pending = questions.count { it.status == QueuedQuestion.QuestionStatus.PENDING },
            processing = questions.count { it.status == QueuedQuestion.QuestionStatus.PROCESSING },
            completed = questions.count { it.status == QueuedQuestion.QuestionStatus.COMPLETED },
            failed = questions.count { it.status == QueuedQuestion.QuestionStatus.FAILED },
            cancelled = questions.count { it.status == QueuedQuestion.QuestionStatus.CANCELLED }
        )
    }
    
    /**
     * 导出结果
     */
    fun exportResults(): String {
        val completedQuestions = _queue.value.filter { 
            it.status == QueuedQuestion.QuestionStatus.COMPLETED 
        }
        
        return buildString {
            appendLine("# 批量问题处理结果")
            appendLine()
            appendLine("处理时间: ${Instant.now()}")
            appendLine("完成数量: ${completedQuestions.size}")
            appendLine()
            
            completedQuestions.forEach { question ->
                appendLine("## 问题 ${_queue.value.indexOf(question) + 1}")
                appendLine()
                appendLine("**提问：**")
                appendLine(question.content)
                appendLine()
                appendLine("**回答：**")
                appendLine(question.result ?: "无结果")
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }
    
    private suspend fun processQueue(
        sessionId: String?,
        onQuestionComplete: suspend (QueuedQuestion) -> Unit,
        onError: suspend (QueuedQuestion, Throwable) -> Unit
    ) {
        while (_isProcessing.value) {
            val nextQuestion = _queue.value.firstOrNull { 
                it.status == QueuedQuestion.QuestionStatus.PENDING 
            }
            
            if (nextQuestion == null) {
                // 队列处理完成
                _isProcessing.value = false
                _currentQuestion.value = null
                break
            }
            
            _currentQuestion.value = nextQuestion
            updateQuestionStatus(nextQuestion.id, QueuedQuestion.QuestionStatus.PROCESSING)
            
            try {
                // 准备上下文
                val contextString = prepareContext(nextQuestion.context)
                val fullQuestion = if (contextString.isNotEmpty()) {
                    "$contextString\n\n${nextQuestion.content}"
                } else {
                    nextQuestion.content
                }
                
                // 发送问题并收集响应
                val response = withContext(Dispatchers.IO) {
                    val responseBuilder = StringBuilder()
                    val options = ClaudeCliWrapper.QueryOptions(
                        resume = sessionId,
                        continueConversation = sessionId == null
                    )
                    
                    cliWrapper.query(fullQuestion, options).collect { message ->
                        when (message.type) {
                            com.claudecodeplus.sdk.MessageType.TEXT -> {
                                message.data.text?.let { responseBuilder.append(it) }
                            }
                            else -> {
                                // 忽略其他类型的消息
                            }
                        }
                    }
                    responseBuilder.toString()
                }
                
                // 更新结果
                updateQuestionResult(nextQuestion.id, response)
                onQuestionComplete(nextQuestion.copy(result = response))
                
            } catch (e: CancellationException) {
                // 处理被取消
                updateQuestionStatus(nextQuestion.id, QueuedQuestion.QuestionStatus.CANCELLED)
                throw e
            } catch (e: Exception) {
                // 处理错误
                updateQuestionError(nextQuestion.id, e.message ?: "未知错误")
                onError(nextQuestion, e)
            }
            
            // 处理间隔
            if (_isProcessing.value) {
                delay(1000) // 避免请求过快
            }
        }
    }
    
    private fun updateQuestionStatus(questionId: String, status: QueuedQuestion.QuestionStatus) {
        _queue.value = _queue.value.map { question ->
            if (question.id == questionId) {
                question.copy(
                    status = status,
                    processedAt = if (status == QueuedQuestion.QuestionStatus.PROCESSING) {
                        Instant.now()
                    } else {
                        question.processedAt
                    }
                )
            } else {
                question
            }
        }
        updateProgress()
    }
    
    private fun updateQuestionResult(questionId: String, result: String) {
        _queue.value = _queue.value.map { question ->
            if (question.id == questionId) {
                question.copy(
                    status = QueuedQuestion.QuestionStatus.COMPLETED,
                    result = result,
                    processedAt = question.processedAt ?: Instant.now()
                )
            } else {
                question
            }
        }
        updateProgress()
    }
    
    private fun updateQuestionError(questionId: String, error: String) {
        _queue.value = _queue.value.map { question ->
            if (question.id == questionId) {
                question.copy(
                    status = QueuedQuestion.QuestionStatus.FAILED,
                    error = error,
                    processedAt = question.processedAt ?: Instant.now()
                )
            } else {
                question
            }
        }
        updateProgress()
    }
    
    private fun updateProgress() {
        val stats = getStatistics()
        _progress.value = QueueProgress(
            completed = stats.completed,
            total = stats.total
        )
    }
    
    private fun prepareContext(context: List<ContextItem>): String {
        if (context.isEmpty()) return ""
        
        return buildString {
            appendLine("上下文信息：")
            context.forEach { item ->
                when (item) {
                    is ContextItem.File -> {
                        appendLine("文件: ${item.path}")
                    }
                    is ContextItem.Folder -> {
                        appendLine("文件夹: ${item.path}")
                    }
                    is ContextItem.CodeBlock -> {
                        appendLine("代码 (${item.language}):")
                        appendLine("```${item.language}")
                        appendLine(item.content)
                        appendLine("```")
                    }
                }
            }
        }
    }
}

/**
 * 队列进度
 */
data class QueueProgress(
    val completed: Int,
    val total: Int
) {
    val percentage: Float get() = if (total > 0) completed.toFloat() / total else 0f
    val remaining: Int get() = total - completed
}

/**
 * 队列统计
 */
data class QueueStatistics(
    val total: Int,
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val cancelled: Int
)