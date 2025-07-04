/*
 * CachedMarkdownParser.kt
 * 
 * 带缓存的 Markdown 解析器，优化性能
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 缓存的 Markdown 解析器
 * 使用 LRU 缓存策略，避免重复解析相同的文本
 */
class CachedMarkdownParser(
    private val maxCacheSize: Int = 100
) {
    private val cache = LRUCache<CacheKey, AnnotatedString>(maxCacheSize)
    private val mutex = Mutex()
    
    /**
     * 解析 Markdown 文本，优先从缓存获取
     */
    suspend fun parse(
        markdown: String,
        linkColor: Color? = null,
        linkBackgroundAlpha: Float = 0.1f
    ): AnnotatedString {
        val key = CacheKey(markdown, linkColor, linkBackgroundAlpha)
        
        // 尝试从缓存获取
        cache.get(key)?.let { return it }
        
        // 缓存未命中，解析并存入缓存
        return mutex.withLock {
            // 双重检查，避免并发时重复解析
            cache.get(key)?.let { return@withLock it }
            
            val result = parseMarkdownToAnnotatedString(
                markdown = markdown,
                linkColor = linkColor,
                linkBackgroundAlpha = linkBackgroundAlpha
            )
            
            cache.put(key, result)
            result
        }
    }
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        cache.clear()
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = cache.size,
            maxSize = maxCacheSize,
            hitRate = cache.hitRate
        )
    }
    
    /**
     * 缓存键
     */
    private data class CacheKey(
        val markdown: String,
        val linkColor: Color?,
        val linkBackgroundAlpha: Float
    )
    
    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitRate: Float
    )
}

/**
 * 简单的 LRU 缓存实现
 */
private class LRUCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var hits = 0
    private var misses = 0
    
    val size: Int get() = map.size
    val hitRate: Float get() = if (hits + misses == 0) 0f else hits.toFloat() / (hits + misses)
    
    fun get(key: K): V? {
        val value = map[key]
        if (value != null) {
            hits++
        } else {
            misses++
        }
        return value
    }
    
    fun put(key: K, value: V) {
        map[key] = value
        if (map.size > maxSize) {
            val iterator = map.iterator()
            iterator.next()
            iterator.remove()
        }
    }
    
    fun clear() {
        map.clear()
        hits = 0
        misses = 0
    }
}

/**
 * 全局缓存实例
 */
private val globalParser = CachedMarkdownParser()

/**
 * Composable 函数：解析 Markdown 并缓存结果
 */
@Composable
fun rememberParsedMarkdown(
    markdown: String,
    linkColor: Color? = null,
    linkBackgroundAlpha: Float = 0.1f
): AnnotatedString {
    return produceState(
        initialValue = AnnotatedString(markdown),
        key1 = markdown,
        key2 = linkColor,
        key3 = linkBackgroundAlpha
    ) {
        value = globalParser.parse(markdown, linkColor, linkBackgroundAlpha)
    }.value
}

/**
 * 同步版本的缓存解析器（用于非协程环境）
 */
object SyncCachedMarkdownParser {
    private val cache = ConcurrentHashMap<String, AnnotatedString>()
    private const val MAX_CACHE_SIZE = 100
    
    fun parse(
        markdown: String,
        linkColor: Color? = null,
        linkBackgroundAlpha: Float = 0.1f
    ): AnnotatedString {
        val cacheKey = "$markdown|${linkColor?.value}|$linkBackgroundAlpha"
        
        return cache.computeIfAbsent(cacheKey) {
            // 当缓存过大时，清理一半的条目
            if (cache.size > MAX_CACHE_SIZE) {
                val keysToRemove = cache.keys.take(MAX_CACHE_SIZE / 2)
                keysToRemove.forEach { cache.remove(it) }
            }
            
            parseMarkdownToAnnotatedString(
                markdown = markdown,
                linkColor = linkColor,
                linkBackgroundAlpha = linkBackgroundAlpha
            )
        }
    }
    
    fun clearCache() {
        cache.clear()
    }
}