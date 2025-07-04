# 内联引用扩展指南

## 概述

新的内联引用系统设计为可扩展架构，可以轻松添加新的引用格式。

## 当前支持的引用类型

| 类型 | 格式 | 显示 | 描述 |
|------|------|------|------|
| 文件 | `@file://src/main.kt` | `@main.kt` | 本地文件引用 |
| HTTP | `@http://example.com/page` | `@example.com` | HTTP网页链接 |
| HTTPS | `@https://example.com/page` | `@example.com` | HTTPS网页链接 |
| Git | `@git://github.com/user/repo.git` | `@repo` | Git仓库引用 |
| 符号 | `@symbol://MyClass.method` | `@method` | 代码符号引用 |
| 终端 | `@terminal://command` | `@terminal` | 终端命令引用 |
| 工作区 | `@workspace://` | `@workspace` | 工作区引用 |

## 如何添加新的引用类型

### 1. 在 InlineReferenceScheme 中添加新类型

```kotlin
enum class InlineReferenceScheme(
    val prefix: String,
    val displayName: String,
    val description: String
) {
    // 现有类型...
    
    // 添加新类型
    DATABASE("db://", "数据库", "数据库连接引用"),
    API("api://", "API", "API接口引用"),
    DOCKER("docker://", "Docker", "Docker容器引用")
}
```

### 2. 更新显示逻辑

在 `ExtractedReference.getDisplayText()` 中添加新类型的显示规则：

```kotlin
fun getDisplayText(): String = when (scheme) {
    // 现有规则...
    
    InlineReferenceScheme.DATABASE -> {
        val dbName = path.substringAfterLast('/')
        "@$dbName"
    }
    InlineReferenceScheme.API -> {
        val endpoint = path.substringAfterLast('/')
        "@$endpoint"
    }
    InlineReferenceScheme.DOCKER -> {
        val containerName = path.substringBefore(':')
        "@$containerName"
    }
}
```

### 3. 添加验证规则（可选）

在 `ReferenceValidator` 中添加特定的验证逻辑：

```kotlin
object ReferenceValidator {
    fun validateReference(scheme: InlineReferenceScheme, path: String): Boolean {
        return when (scheme) {
            // 现有验证...
            
            InlineReferenceScheme.DATABASE -> validateDatabaseConnection(path)
            InlineReferenceScheme.API -> validateApiEndpoint(path)
            InlineReferenceScheme.DOCKER -> validateDockerReference(path)
            else -> path.isNotBlank()
        }
    }
    
    private fun validateDatabaseConnection(path: String): Boolean {
        // 数据库连接格式验证
        return path.matches(Regex("[a-zA-Z0-9._/-]+"))
    }
    
    private fun validateApiEndpoint(path: String): Boolean {
        // API端点格式验证
        return path.matches(Regex("[a-zA-Z0-9._/-]+"))
    }
    
    private fun validateDockerReference(path: String): Boolean {
        // Docker引用格式验证
        return path.matches(Regex("[a-zA-Z0-9._/-:]+"))
    }
}
```

### 4. 更新上下文选择器（如需要）

如果新类型需要特殊的选择器，在上下文选择器中添加相应的处理逻辑。

## 使用示例

添加新类型后，用户可以这样使用：

```
我需要连接到 @db://users/production 数据库
请调用 @api://users/create 接口
启动 @docker://nginx:latest 容器
```

显示效果：
```
我需要连接到 @production 数据库
请调用 @create 接口  
启动 @nginx 容器
```

## 设计优势

1. **向后兼容**：添加新类型不会影响现有功能
2. **类型安全**：使用枚举确保类型安全
3. **集中管理**：所有引用类型在一个地方定义
4. **自动生成**：正则表达式自动根据定义生成
5. **易于维护**：添加新类型只需修改几个地方

## 注意事项

1. 新的引用前缀不应与现有前缀冲突
2. 前缀应该具有足够的描述性
3. 显示逻辑应该简洁明了
4. 验证规则应该严格但不过于限制
5. 考虑与AI的兼容性，确保Claude能理解新格式

## 扩展示例：添加Jira引用

```kotlin
// 1. 添加枚举值
JIRA("jira://", "Jira", "Jira issue引用")

// 2. 添加显示逻辑  
InlineReferenceScheme.JIRA -> {
    val issueKey = path.substringAfterLast('/')
    "@$issueKey"
}

// 3. 添加验证
InlineReferenceScheme.JIRA -> validateJiraIssue(path)

private fun validateJiraIssue(path: String): Boolean {
    return path.matches(Regex("[A-Z]+-\\d+"))
}
```

使用效果：
- 输入：`@jira://PROJECT-123`
- 显示：`@PROJECT-123`
- 发送给AI：`@jira://PROJECT-123`