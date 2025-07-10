package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * 对话搜索引擎
 */
class ChatSearchEngine {
    
    /**
     * 搜索对话
     */
    suspend fun search(
        query: String,
        tabs: List<ChatTab>,
        options: SearchOptions = SearchOptions()
    ): List<ChatSearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        
        val results = mutableListOf<ChatSearchResult>()
        val queryTerms = tokenizeQuery(query)
        
        for (tab in tabs) {
            val matchedMessages = mutableListOf<MessageMatch>()
            var relevanceScore = 0f
            
            // 搜索标题
            if (options.searchInTitles) {
                val titleMatches = findMatches(tab.title, queryTerms)
                if (titleMatches.isNotEmpty()) {
                    relevanceScore += 2.0f // 标题匹配权重更高
                    matchedMessages.add(
                        MessageMatch(
                            messageId = "title",
                            snippet = tab.title,
                            highlights = titleMatches,
                            matchType = MessageMatch.MatchType.TITLE
                        )
                    )
                }
            }
            
            // 搜索消息内容
            if (options.searchInContent) {
                for ((index, message) in tab.messages.withIndex()) {
                    if (options.maxMessagesPerChat != null && 
                        matchedMessages.count { it.matchType == MessageMatch.MatchType.CONTENT } >= options.maxMessagesPerChat) {
                        break
                    }
                    
                    val contentMatches = findMatches(message.content, queryTerms)
                    if (contentMatches.isNotEmpty()) {
                        relevanceScore += 1.0f
                        
                        val snippet = extractSnippet(
                            message.content,
                            contentMatches.first(),
                            options.snippetLength
                        )
                        
                        matchedMessages.add(
                            MessageMatch(
                                messageId = message.id,
                                snippet = snippet,
                                highlights = adjustHighlightsForSnippet(
                                    contentMatches,
                                    message.content,
                                    snippet
                                ),
                                matchType = MessageMatch.MatchType.CONTENT
                            )
                        )
                    }
                }
            }
            
            // 搜索标签
            if (options.searchInTags) {
                for (tag in tab.tags) {
                    val tagMatches = findMatches(tag.name, queryTerms)
                    if (tagMatches.isNotEmpty()) {
                        relevanceScore += 1.5f
                        matchedMessages.add(
                            MessageMatch(
                                messageId = "tag-${tag.id}",
                                snippet = tag.name,
                                highlights = tagMatches,
                                matchType = MessageMatch.MatchType.TAG
                            )
                        )
                    }
                }
            }
            
            // 搜索上下文
            if (options.searchInContext) {
                for (context in tab.context) {
                    val contextText = when (context) {
                        is ContextItem.File -> context.path.substringAfterLast('/')
                        is ContextItem.Folder -> context.path.substringAfterLast('/')
                        is ContextItem.CodeBlock -> context.description ?: context.language
                    }
                    
                    val contextMatches = findMatches(contextText, queryTerms)
                    if (contextMatches.isNotEmpty()) {
                        relevanceScore += 0.5f
                        matchedMessages.add(
                            MessageMatch(
                                messageId = "context-${context.id}",
                                snippet = contextText,
                                highlights = contextMatches,
                                matchType = MessageMatch.MatchType.CONTEXT
                            )
                        )
                    }
                }
            }
            
            // 添加搜索结果
            if (matchedMessages.isNotEmpty()) {
                results.add(
                    ChatSearchResult(
                        chatId = tab.id,
                        chatTitle = tab.title,
                        matchedMessages = matchedMessages,
                        relevanceScore = relevanceScore,
                        lastModified = tab.lastModified
                    )
                )
            }
        }
        
        // 按相关性排序
        results.sortByDescending { it.relevanceScore }
        
        // 限制结果数量
        if (options.maxResults != null && results.size > options.maxResults) {
            results.take(options.maxResults)
        } else {
            results
        }
    }
    
    /**
     * 高级搜索（支持过滤器）
     */
    suspend fun advancedSearch(
        query: String,
        tabs: List<ChatTab>,
        filters: SearchFilters
    ): List<ChatSearchResult> = withContext(Dispatchers.Default) {
        // 先按过滤条件筛选
        val filteredTabs = tabs.filter { tab ->
            // 时间范围过滤
            val matchesTimeRange = when {
                filters.startTime != null && filters.endTime != null -> {
                    tab.lastModified >= filters.startTime && tab.lastModified <= filters.endTime
                }
                filters.startTime != null -> tab.lastModified >= filters.startTime
                filters.endTime != null -> tab.lastModified <= filters.endTime
                else -> true
            }
            
            // 分组过滤
            val matchesGroup = filters.groupIds.isEmpty() || 
                tab.groupId in filters.groupIds
            
            // 标签过滤
            val matchesTags = filters.tagIds.isEmpty() || 
                tab.tags.any { it.id in filters.tagIds }
            
            // 状态过滤
            val matchesStatus = filters.statuses.isEmpty() || 
                tab.status in filters.statuses
            
            matchesTimeRange && matchesGroup && matchesTags && matchesStatus
        }
        
        // 执行搜索
        search(query, filteredTabs, filters.searchOptions)
    }
    
    /**
     * 获取搜索建议
     */
    fun getSuggestions(
        partialQuery: String,
        tabs: List<ChatTab>,
        limit: Int = 10
    ): List<SearchSuggestion> {
        if (partialQuery.isBlank()) return emptyList()
        
        val suggestions = mutableSetOf<SearchSuggestion>()
        val lowerQuery = partialQuery.lowercase()
        
        // 从标题获取建议
        tabs.forEach { tab ->
            if (tab.title.lowercase().contains(lowerQuery)) {
                suggestions.add(
                    SearchSuggestion(
                        text = tab.title,
                        type = SearchSuggestion.Type.TITLE,
                        relevance = calculateRelevance(tab.title, partialQuery)
                    )
                )
            }
        }
        
        // 从标签获取建议
        tabs.flatMap { it.tags }.distinctBy { it.name }.forEach { tag ->
            if (tag.name.lowercase().contains(lowerQuery)) {
                suggestions.add(
                    SearchSuggestion(
                        text = tag.name,
                        type = SearchSuggestion.Type.TAG,
                        relevance = calculateRelevance(tag.name, partialQuery)
                    )
                )
            }
        }
        
        // 从常用词获取建议
        val commonWords = extractCommonWords(tabs)
        commonWords.forEach { word ->
            if (word.lowercase().startsWith(lowerQuery)) {
                suggestions.add(
                    SearchSuggestion(
                        text = word,
                        type = SearchSuggestion.Type.KEYWORD,
                        relevance = calculateRelevance(word, partialQuery)
                    )
                )
            }
        }
        
        return suggestions
            .sortedByDescending { it.relevance }
            .take(limit)
            .toList()
    }
    
    /**
     * 分词
     */
    private fun tokenizeQuery(query: String): List<String> {
        return query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }
    
    /**
     * 查找匹配
     */
    private fun findMatches(text: String, queryTerms: List<String>): List<IntRange> {
        val matches = mutableListOf<IntRange>()
        val lowerText = text.lowercase()
        
        for (term in queryTerms) {
            var index = 0
            while (true) {
                index = lowerText.indexOf(term, index)
                if (index == -1) break
                matches.add(IntRange(index, index + term.length))
                index += term.length
            }
        }
        
        // 合并重叠的范围
        return mergeRanges(matches)
    }
    
    /**
     * 提取片段
     */
    private fun extractSnippet(
        text: String,
        firstMatch: IntRange,
        maxLength: Int
    ): String {
        val contextBefore = 30
        val contextAfter = maxLength - contextBefore - firstMatch.count()
        
        val start = (firstMatch.first - contextBefore).coerceAtLeast(0)
        val end = (firstMatch.last + contextAfter).coerceAtMost(text.length)
        
        var snippet = text.substring(start, end)
        
        // 添加省略号
        if (start > 0) snippet = "...$snippet"
        if (end < text.length) snippet = "$snippet..."
        
        return snippet
    }
    
    /**
     * 调整高亮范围
     */
    private fun adjustHighlightsForSnippet(
        highlights: List<IntRange>,
        fullText: String,
        snippet: String
    ): List<IntRange> {
        val snippetStart = fullText.indexOf(snippet.trimStart('.'))
        if (snippetStart == -1) return emptyList()
        
        return highlights
            .filter { it.first >= snippetStart && it.last <= snippetStart + snippet.length }
            .map { IntRange(it.first - snippetStart, it.last - snippetStart) }
    }
    
    /**
     * 合并重叠范围
     */
    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        var current = sorted.first()
        
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (current.last >= next.first - 1) {
                current = IntRange(current.first, maxOf(current.last, next.last))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return merged
    }
    
    /**
     * 计算相关性
     */
    private fun calculateRelevance(text: String, query: String): Float {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        
        return when {
            lowerText == lowerQuery -> 1.0f
            lowerText.startsWith(lowerQuery) -> 0.8f
            lowerText.contains(lowerQuery) -> 0.6f
            else -> 0.4f
        }
    }
    
    /**
     * 提取常用词
     */
    private fun extractCommonWords(tabs: List<ChatTab>): Set<String> {
        val wordCounts = mutableMapOf<String, Int>()
        
        tabs.forEach { tab ->
            tab.messages.forEach { message ->
                val words = message.content
                    .split(Regex("[\\s\\p{Punct}]+"))
                    .filter { it.length > 3 }
                
                words.forEach { word ->
                    wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
                }
            }
        }
        
        return wordCounts
            .filter { it.value > 2 }
            .keys
            .take(100)
            .toSet()
    }
}

/**
 * 搜索选项
 */
data class SearchOptions(
    val searchInTitles: Boolean = true,
    val searchInContent: Boolean = true,
    val searchInTags: Boolean = true,
    val searchInContext: Boolean = true,
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val snippetLength: Int = 100,
    val maxMessagesPerChat: Int? = 5,
    val maxResults: Int? = 50
)

/**
 * 搜索过滤器
 */
data class SearchFilters(
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val groupIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val statuses: Set<ChatTab.TabStatus> = emptySet(),
    val searchOptions: SearchOptions = SearchOptions()
)

/**
 * 搜索建议
 */
data class SearchSuggestion(
    val text: String,
    val type: Type,
    val relevance: Float
) {
    enum class Type {
        TITLE,
        TAG,
        KEYWORD,
        RECENT
    }
}