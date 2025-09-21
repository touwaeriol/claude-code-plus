package com.claudecodeplus.ui.services

import com.claudecodeplus.core.logging.*
import com.claudecodeplus.ui.models.SessionObject
import com.claudecodeplus.ui.models.SessionMetadata
import com.claudecodeplus.ui.models.SessionMetadata.Companion.toJson
import com.claudecodeplus.ui.models.SessionMetadata.Companion.fromSessionObject
import com.claudecodeplus.ui.models.SessionMetadata.Companion.applyToSessionObject
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * 会话持久化服务
 * 
 * 负责保存和恢复会话的配置信息
 * 配置信息保存在项目目录下的 .claude/sessions 目录中
 */
class SessionPersistenceService {
    companion object {
        private const val SESSIONS_DIR = ".claude/sessions"
        private const val METADATA_SUFFIX = ".metadata.json"
        
        /**
         * 保存会话元数据
         * 
         * @param sessionId 会话 ID
         * @param sessionObject 会话对象
         * @param projectPath 项目路径
         */
        suspend fun saveSessionMetadata(
            sessionId: String,
            sessionObject: SessionObject,
            projectPath: String
        ) = withContext(Dispatchers.IO) {
            try {
                val sessionsDir = File(projectPath, SESSIONS_DIR)
                if (!sessionsDir.exists()) {
                    sessionsDir.mkdirs()
                }
                
                val metadata = fromSessionObject(sessionObject)
                val metadataFile = File(sessionsDir, "$sessionId$METADATA_SUFFIX")
                
                metadataFile.writeText(metadata.toJson())
                
    //                 logD("[SessionPersistence] 保存会话元数据成功: $sessionId")
    //                 logD("  - 模型: ${metadata.modelName}")
    //                 logD("  - 权限模式: ${metadata.permissionMode}")
    //                 logD("  - 跳过权限: ${metadata.skipPermissions}")
            } catch (e: Exception) {
    //                 logD("[SessionPersistence] 保存会话元数据失败: ${e.message}")
                logE("Exception caught", e)
            }
        }
        
        /**
         * 加载会话元数据
         * 
         * @param sessionId 会话 ID
         * @param projectPath 项目路径
         * @return 会话元数据，如果不存在返回 null
         */
        suspend fun loadSessionMetadata(
            sessionId: String,
            projectPath: String
        ): SessionMetadata? = withContext(Dispatchers.IO) {
            try {
                val metadataFile = File(projectPath, "$SESSIONS_DIR/$sessionId$METADATA_SUFFIX")
                if (!metadataFile.exists()) {
    //                     logD("[SessionPersistence] 元数据文件不存在: $sessionId")
                    return@withContext null
                }
                
                val jsonContent = metadataFile.readText()
                val metadata = SessionMetadata.fromJson(jsonContent)
                
                if (metadata != null) {
    //                     logD("[SessionPersistence] 加载会话元数据成功: $sessionId")
    //                     logD("  - 模型: ${metadata.modelName}")
    //                     logD("  - 权限模式: ${metadata.permissionMode}")
    //                     logD("  - 跳过权限: ${metadata.skipPermissions}")
                }
                
                metadata
            } catch (e: Exception) {
    //                 logD("[SessionPersistence] 加载会话元数据失败: ${e.message}")
                logE("Exception caught", e)
                null
            }
        }
        
        /**
         * 从元数据恢复会话配置
         * 
         * @param sessionId 会话 ID
         * @param sessionObject 要恢复配置的会话对象
         * @param projectPath 项目路径
         * @return 是否成功恢复
         */
        suspend fun restoreSessionConfig(
            sessionId: String,
            sessionObject: SessionObject,
            projectPath: String
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val metadata = loadSessionMetadata(sessionId, projectPath)
                if (metadata != null) {
                    metadata.applyToSessionObject(sessionObject)
    //                     logD("[SessionPersistence] 恢复会话配置成功: $sessionId")
                    true
                } else {
    //                     logD("[SessionPersistence] 没有找到会话元数据，使用默认配置: $sessionId")
                    false
                }
            } catch (e: Exception) {
    //                 logD("[SessionPersistence] 恢复会话配置失败: ${e.message}")
                logE("Exception caught", e)
                false
            }
        }
        
        /**
         * 删除会话元数据
         * 
         * @param sessionId 会话 ID
         * @param projectPath 项目路径
         */
        suspend fun deleteSessionMetadata(
            sessionId: String,
            projectPath: String
        ) = withContext(Dispatchers.IO) {
            try {
                val metadataFile = File(projectPath, "$SESSIONS_DIR/$sessionId$METADATA_SUFFIX")
                if (metadataFile.exists()) {
                    metadataFile.delete()
    //                     logD("[SessionPersistence] 删除会话元数据成功: $sessionId")
                }
            } catch (e: Exception) {
    //                 logD("[SessionPersistence] 删除会话元数据失败: ${e.message}")
                logE("Exception caught", e)
            }
        }
        
        /**
         * 列出项目下所有的会话元数据
         * 
         * @param projectPath 项目路径
         * @return 会话 ID 列表
         */
        suspend fun listSessionIds(projectPath: String): List<String> = withContext(Dispatchers.IO) {
            try {
                val sessionsDir = File(projectPath, SESSIONS_DIR)
                if (!sessionsDir.exists()) {
                    return@withContext emptyList()
                }
                
                sessionsDir.listFiles { file ->
                    file.name.endsWith(METADATA_SUFFIX)
                }?.map { file ->
                    file.name.removeSuffix(METADATA_SUFFIX)
                } ?: emptyList()
            } catch (e: Exception) {
    //                 logD("[SessionPersistence] 列出会话失败: ${e.message}")
                logE("Exception caught", e)
                emptyList()
            }
        }
    }
}
