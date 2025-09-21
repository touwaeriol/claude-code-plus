package com.claudecodeplus.ui.models

import com.claudecodeplus.core.logging.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 会话元数据
 * 
 * 用于持久化会话的配置信息，包括：
 * - 选择的 AI 模型
 * - 权限模式
 * - 其他会话级别的设置
 * 
 * 这些信息会保存在 Claude CLI 的会话文件元数据中
 */
@Serializable
data class SessionMetadata(
    val modelName: String? = null,
    val permissionMode: String? = null,
    val skipPermissions: Boolean? = null,
    val customSystemPrompt: String? = null,
    val createdWithVersion: String? = null,
    val additionalSettings: Map<String, String> = emptyMap()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
        
        /**
         * 从 JSON 字符串解析元数据
         */
        fun fromJson(jsonString: String): SessionMetadata? {
            return try {
                json.decodeFromString<SessionMetadata>(jsonString)
            } catch (e: Exception) {
    //                 logD("[SessionMetadata] 解析失败: ${e.message}")
                null
            }
        }
        
        /**
         * 转换为 JSON 字符串
         */
        fun SessionMetadata.toJson(): String {
            return json.encodeToString(serializer(), this)
        }
        
        /**
         * 从 SessionObject 创建元数据
         */
        fun fromSessionObject(session: SessionObject): SessionMetadata {
            return SessionMetadata(
                modelName = session.selectedModel.name,
                permissionMode = session.selectedPermissionMode.name,
                skipPermissions = session.skipPermissions,
                createdWithVersion = "1.0.0" // TODO: 从应用版本获取
            )
        }
        
        /**
         * 应用元数据到 SessionObject
         */
        fun SessionMetadata.applyToSessionObject(session: SessionObject) {
            modelName?.let { name ->
                AiModel.values().find { it.name == name }?.let {
                    session.selectedModel = it
                }
            }
            
            permissionMode?.let { mode ->
                PermissionMode.values().find { it.name == mode }?.let {
                    session.selectedPermissionMode = it
                }
            }
            
            skipPermissions?.let {
                session.skipPermissions = it
            }
        }
    }
}
