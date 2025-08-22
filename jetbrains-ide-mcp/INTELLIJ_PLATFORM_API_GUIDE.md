# IntelliJ Platform API 集成指南

基于官方文档研究的 IntelliJ Platform API 使用指南，用于实现真正的 IDE 代码分析功能。

## 📚 官方文档研究总结

### 1. 核心 API 概念

#### PSI (Program Structure Interface)
- **作用**: PSI 是 IntelliJ Platform 中代码分析的核心
- **功能**: 将源代码表示为分层的元素树结构
- **基类**: `PsiElement` 是所有 PSI 元素的基类
- **获取方式**: 
  - 通过 Action 事件获取
  - 在 PSI 文件中查找元素
  - 解析引用获取

**关键 API**:
```kotlin
// 获取 PSI 文件
val psiFile = PsiManager.getInstance(project).findFile(virtualFile)

// 获取文件内容
val fileText = psiFile.text

// 获取文档对象用于行号/列号转换
val document = psiFile.viewProvider.document
```

#### Code Inspections (代码检查)
- **作用**: IntelliJ 的静态代码分析工具
- **类型**: 本地检查 (`LocalInspectionTool`) 和全局检查 (`GlobalInspectionTool`)
- **实现方式**: 
  - 继承 `AbstractBaseJavaLocalInspectionTool`
  - 提供 `PsiElementVisitor` 遍历代码
  - 可选的快速修复 (`QuickFix`)

**关键 API**:
```kotlin
// 获取检查管理器
val inspectionManager = InspectionManager.getInstance(project)

// 创建全局检查上下文
val globalContext = (inspectionManager as? InspectionManagerEx)?.createNewGlobalContext()
```

#### 语法高亮和错误检测
- **Lexer**: 使用 `SyntaxHighlighter` 为标记类型分配 `TextAttributesKey`
- **Parser**: 在解析期间识别无效的标记序列
- **Annotator**: 提供高级语法和语义分析，使用 PSI

### 2. 线程模型和安全性

#### 线程规则
- **EDT (Event Dispatch Thread)**: UI 线程，用于界面更新
- **BGT (Background Threads)**: 后台线程，用于计算密集型任务
- **读写锁机制**: 控制数据访问安全

#### 安全的后台处理
```kotlin
// 非阻塞读操作 (推荐方式)
ReadAction.nonBlocking(Callable {
    // 在这里执行 PSI 读取操作
    performCodeAnalysis(psiFile)
}).submit(NonUrgentExecutor.getInstance())

// 检查对象有效性
if (psiElement.isValid) {
    // 安全执行操作
}
```

#### 重要原则
- PSI 和 VFS 数据结构不是线程安全的
- 读取操作可以在任何线程执行
- 写操作当前只能在 EDT 执行
- 避免在 EDT 上执行长时间运行的操作

### 3. 实用的 PSI 操作

#### 文件和类查找
```kotlin
// 按名称查找文件
FilenameIndex.getFilesByName(project, "filename", scope)

// 查找类的继承者
ClassInheritorsSearch.search(psiClass, scope, true)

// 查找使用位置
ReferencesSearch.search(psiElement, scope)
```

#### 遍历 PSI 树
```kotlin
// 使用访问者模式遍历 Java 代码
object : JavaElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
        // 处理方法
        super.visitMethod(method)
    }
    
    override fun visitClass(aClass: PsiClass) {
        // 处理类
        super.visitClass(aClass)
    }
}
```

## 🔧 实际实现策略

### 1. 文件错误检查实现

```kotlin
class FileErrorChecker(private val project: Project) {
    fun checkErrors(filePath: String): List<CodeIssue> {
        return ReadAction.nonBlocking(Callable {
            val virtualFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://$filePath") ?: return@Callable emptyList()
            
            val psiFile = PsiManager.getInstance(project)
                .findFile(virtualFile) ?: return@Callable emptyList()
            
            // 执行错误检查
            detectSyntaxErrors(psiFile) + runInspections(psiFile)
        }).submit(NonUrgentExecutor.getInstance()).get()
    }
    
    private fun detectSyntaxErrors(psiFile: PsiFile): List<CodeIssue> {
        // 实现语法错误检测
        // 注意: 真实实现需要使用 HighlightInfo 或其他错误检测 API
    }
    
    private fun runInspections(psiFile: PsiFile): List<CodeIssue> {
        // 运行代码检查工具
        // 使用 InspectionManager 执行检查
    }
}
```

### 2. 代码质量分析实现

```kotlin
class CodeQualityAnalyzer(private val project: Project) {
    fun analyzeQuality(filePath: String): QualityMetrics {
        return ReadAction.nonBlocking(Callable {
            val psiFile = getPsiFile(filePath) ?: return@Callable defaultMetrics()
            
            QualityMetrics(
                fileMetrics = calculateFileMetrics(psiFile),
                complexityMetrics = calculateComplexity(psiFile),
                maintainabilityMetrics = calculateMaintainability(psiFile)
            )
        }).submit(NonUrgentExecutor.getInstance()).get()
    }
    
    private fun calculateComplexity(psiFile: PsiFile): ComplexityMetrics {
        var complexity = 1 // 基础复杂度
        var methodCount = 0
        
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                complexity++
                super.visitIfStatement(statement)
            }
            
            override fun visitWhileStatement(statement: PsiWhileStatement) {
                complexity++
                super.visitWhileStatement(statement)
            }
            
            override fun visitMethod(method: PsiMethod) {
                methodCount++
                super.visitMethod(method)
            }
        })
        
        return ComplexityMetrics(
            cyclomaticComplexity = complexity,
            methodCount = methodCount,
            averageComplexity = if (methodCount > 0) complexity.toDouble() / methodCount else 0.0
        )
    }
}
```

### 3. 语法验证实现

```kotlin
class SyntaxValidator(private val project: Project) {
    fun validateSyntax(filePath: String): SyntaxValidationResult {
        return ReadAction.nonBlocking(Callable {
            val psiFile = getPsiFile(filePath) ?: return@Callable invalidResult(filePath)
            
            val errors = mutableListOf<SyntaxError>()
            
            // 检查 PSI 解析错误
            checkParsingErrors(psiFile, errors)
            
            SyntaxValidationResult(
                filePath = filePath,
                isValid = errors.isEmpty(),
                language = psiFile.language.displayName,
                errors = errors
            )
        }).submit(NonUrgentExecutor.getInstance()).get()
    }
    
    private fun checkParsingErrors(psiFile: PsiFile, errors: MutableList<SyntaxError>) {
        // 遍历 PSI 树查找错误元素
        psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                val document = psiFile.viewProvider.document
                if (document != null) {
                    val line = document.getLineNumber(element.textOffset) + 1
                    val column = element.textOffset - document.getLineStartOffset(line - 1) + 1
                    
                    errors.add(SyntaxError(
                        message = element.errorDescription ?: "Syntax error",
                        line = line,
                        column = column,
                        errorCode = "SYNTAX_ERROR",
                        errorType = "PARSE"
                    ))
                }
                super.visitErrorElement(element)
            }
        })
    }
}
```

## ⚠️ 重要注意事项

### 1. API 限制和挑战

- **复杂性**: IntelliJ Platform API 非常复杂，需要深入理解
- **版本兼容性**: 不同版本的 API 可能有变化
- **环境依赖**: 某些 API 只能在完整的 IDE 环境中使用
- **线程安全**: 必须严格遵循线程模型规则

### 2. 推荐的开发方法

1. **渐进式实现**: 从简单功能开始，逐步增加复杂性
2. **充分测试**: 在真实的 IntelliJ 插件环境中测试
3. **错误处理**: 对所有可能的异常情况进行处理
4. **性能优化**: 避免阻塞 UI 线程，合理使用缓存

### 3. 替代方案

对于某些复杂的分析任务，可以考虑：
- 使用外部工具 (如 SpotBugs, PMD)
- 实现简化的分析逻辑
- 结合多种检测方法

## 📋 API 参考清单

### 核心类和接口

| 类/接口 | 用途 | 包路径 |
|---------|------|--------|
| `PsiElement` | PSI 元素基类 | `com.intellij.psi` |
| `PsiFile` | PSI 文件表示 | `com.intellij.psi` |
| `PsiManager` | PSI 管理器 | `com.intellij.psi` |
| `VirtualFileManager` | 虚拟文件系统管理 | `com.intellij.openapi.vfs` |
| `InspectionManager` | 检查管理器 | `com.intellij.codeInspection` |
| `ReadAction` | 读操作封装 | `com.intellij.openapi.application` |
| `Project` | 项目对象 | `com.intellij.openapi.project` |

### 访问者模式类

| 类名 | 用途 | 适用语言 |
|------|------|----------|
| `JavaElementVisitor` | Java 元素访问者 | Java |
| `JavaRecursiveElementVisitor` | Java 递归访问者 | Java |
| `PsiElementVisitor` | 通用元素访问者 | 通用 |
| `PsiRecursiveElementWalkingVisitor` | 递归遍历访问者 | 通用 |

## 🎯 下一步计划

1. **完善错误检测**: 实现更精确的语法和语义错误检测
2. **增强质量分析**: 添加更多代码质量指标
3. **性能优化**: 优化大文件和批量处理的性能
4. **扩展语言支持**: 支持更多编程语言
5. **集成测试**: 在真实的 IntelliJ 插件环境中全面测试

---

*此文档基于 IntelliJ Platform Plugin SDK 官方文档研究整理，版本：2024.3*