package com.claudecodeplus.server.services

import com.claudecodeplus.sdk.types.*
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.Base64

/**
 * 消息内容解析器
 * 
 * 功能：
 * 1. 解析用户消息中的 @ 引用
 * 2. 识别图片路径并转换为 ImageBlock
 * 3. 保留普通文件引用给 Claude Code 处理
 */
object MessageContentParser {
    private val logger = Logger.getInstance(MessageContentParser::class.java)
    
    // 图片文件扩展名
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg")
    
    /**
     * 解析消息文本，提取 @ 引用并转换为 ContentBlock 列表
     * 
     * @param text 用户输入的消息文本
     * @return ContentBlock 列表（TextBlock 和 ImageBlock）
     */
    fun parseMessageContent(text: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val pattern = """@([^\s]+)""".toRegex() // 匹配 @ 后面的路径（到空格为止）
        
        var lastIndex = 0
        
        pattern.findAll(text).forEach { match ->
            // 添加前面的文本块
            if (match.range.first > lastIndex) {
                val textContent = text.substring(lastIndex, match.range.first)
                if (textContent.isNotBlank()) {
                    blocks.add(TextBlock(textContent))
                }
            }
            
            val path = match.groupValues[1]
            
            // 判断是否为图片路径
            if (isImagePath(path)) {
                // ImageBlock 在当前 SDK 版本中不存在，暂时作为文本处理
                blocks.add(TextBlock("[图片: ${File(path).name}]"))
            } else {
                // 普通文件引用，保留原样（Claude Code 会处理）
                blocks.add(TextBlock("@$path"))
            }
            
            lastIndex = match.range.last + 1
        }
        
        // 添加剩余文本
        if (lastIndex < text.length) {
            val textContent = text.substring(lastIndex)
            if (textContent.isNotBlank()) {
                blocks.add(TextBlock(textContent))
            }
        }
        
        // 如果没有任何块，返回一个空文本块
        if (blocks.isEmpty()) {
            blocks.add(TextBlock(text))
        }
        
        return blocks
    }
    
    /**
     * 判断路径是否为图片文件
     */
    private fun isImagePath(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase()
        return IMAGE_EXTENSIONS.contains(extension)
    }
    
    /**
     * 将图片路径转换为 ImageBlock
     *
     * ImageBlock 在当前 SDK 版本中不存在，此方法已废弃
     *
     * @param path 图片文件路径（绝对路径或相对路径）
     * @return ImageBlock 或 null（如果文件不存在或读取失败）
     */
    @Deprecated("ImageBlock type does not exist in current SDK version")
    private fun convertToImageBlock(path: String): Any? {
        return try {
            val file = File(path)

            if (!file.exists() || !file.isFile) {
                logger.warn("Image file not found: $path")
                return null
            }

            // ImageBlock 在当前 SDK 版本中不存在，返回 null
            logger.warn("ImageBlock is not supported in current SDK version")
            return null

            // 以下代码已注释，因为 ImageBlock 和 ImageSource 类型不存在
            /*
            // 读取文件内容
            val fileBytes = file.readBytes()

            // 转换为 base64
            val base64Data = Base64.getEncoder().encodeToString(fileBytes)

            // 检测 MIME 类型
            val mimeType = detectMimeType(file)

            logger.info("Converted image to ImageBlock: ${file.name} (${fileBytes.size} bytes)")

            ImageBlock(
                source = ImageSource(
                    type = "base64",
                    mediaType = mimeType,
                    data = base64Data
                )
            )
            */
        } catch (e: Exception) {
            logger.error("Failed to convert image to ImageBlock: $path", e)
            null
        }
    }
    
    /**
     * 检测图片的 MIME 类型
     */
    private fun detectMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "image/png" // 默认
        }
    }
}

