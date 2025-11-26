package com.asakii.server.services

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * 临时图片存储服务
 *
 * 功能：
 * 1. 管理系统临时目录中的图片文件
 * 2. 提供图片保存、读取功能
 * 3. 定期清理超过 24 小时的临时文件
 */
object TempImageService {
    private val logger = Logger.getLogger(TempImageService::class.java.name)

    // 临时目录路径
    private val tempDir: File by lazy {
        val systemTempDir = System.getProperty("java.io.tmpdir")
        val dir = File(systemTempDir, "claude-code-plus/images")

        if (!dir.exists()) {
            dir.mkdirs()
            logger.info("Created temp image directory: ${dir.absolutePath}")
        }

        dir
    }

    // 定期清理任务
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "TempImageCleanup").apply { isDaemon = true }
    }

    init {
        // 启动定期清理任务（每小时执行一次）
        cleanupExecutor.scheduleAtFixedRate(
            { cleanupOldFiles() },
            1, // 初始延迟 1 小时
            1, // 每 1 小时执行一次
            TimeUnit.HOURS
        )

        logger.info("TempImageService initialized, temp dir: ${tempDir.absolutePath}")
    }

    /**
     * 保存图片文件到临时目录
     *
     * @param fileBytes 图片文件字节数组
     * @param originalFilename 原始文件名（用于提取扩展名）
     * @return 保存后的文件绝对路径
     */
    fun saveImage(fileBytes: ByteArray, originalFilename: String): String {
        // 生成唯一文件名
        val extension = originalFilename.substringAfterLast('.', "png")
        val uniqueFilename = "${UUID.randomUUID()}.$extension"

        val targetFile = File(tempDir, uniqueFilename)

        // 保存文件
        targetFile.writeBytes(fileBytes)

        logger.info("Saved temp image: ${targetFile.absolutePath} (${fileBytes.size} bytes)")

        return targetFile.absolutePath
    }

    /**
     * 根据文件名读取临时图片
     *
     * @param filename 文件名（不是完整路径）
     * @return 图片文件对象，如果不存在返回 null
     */
    fun getImage(filename: String): File? {
        val file = File(tempDir, filename)

        if (!file.exists() || !file.isFile) {
            logger.warning("Temp image not found: $filename")
            return null
        }

        return file
    }


    /**
     * 根据绝对路径读取图片
     *
     * @param absolutePath 图片的绝对路径
     * @return 图片文件对象，如果不存在或不在临时目录中返回 null
     */
    fun getImageByPath(absolutePath: String): File? {
        val file = File(absolutePath)

        // 安全检查：确保文件在临时目录中
        if (!file.canonicalPath.startsWith(tempDir.canonicalPath)) {
            logger.warning("Image path is outside temp directory: $absolutePath")
            return null
        }

        if (!file.exists() || !file.isFile) {
            logger.warning("Image not found: $absolutePath")
            return null
        }

        return file
    }

    /**
     * 清理超过 24 小时的临时文件
     */
    private fun cleanupOldFiles() {
        try {
            val now = System.currentTimeMillis()
            val maxAge = TimeUnit.HOURS.toMillis(24)

            var deletedCount = 0
            var deletedSize = 0L

            tempDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val age = now - file.lastModified()
                    if (age > maxAge) {
                        val size = file.length()
                        if (file.delete()) {
                            deletedCount++
                            deletedSize += size
                            logger.fine("Deleted old temp image: ${file.name} (age: ${age / 1000 / 60 / 60}h)")
                        }
                    }
                }
            }

            if (deletedCount > 0) {
                logger.info("Cleanup completed: deleted $deletedCount files (${deletedSize / 1024} KB)")
            }
        } catch (e: Exception) {
            logger.log(java.util.logging.Level.SEVERE, "Error during temp image cleanup", e)
        }
    }

    /**
     * 获取临时目录路径
     */
    fun getTempDirPath(): String = tempDir.absolutePath

    /**
     * 关闭服务（清理资源）
     */
    fun shutdown() {
        cleanupExecutor.shutdown()
        logger.info("TempImageService shutdown")
    }
}