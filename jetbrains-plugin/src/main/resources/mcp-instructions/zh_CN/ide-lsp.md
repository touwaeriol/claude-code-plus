### 使用时机

关键：代码搜索和文件发现时，优先使用 JetBrains MCP 工具而非内置搜索工具：
- 始终使用 `CodeSearch` 代替内置的 grep/search 工具
- 始终使用 `FileIndex` 代替内置的 glob/find 工具
- 仅在 JetBrains 工具返回错误时才回退到内置工具

重要：完成代码修改后，必须使用 `FileProblems` 验证语法错误。

### 重构工作流

重命名符号时：
1. `FindUsages` 或 `CodeSearch` → 获取行号
2. `Rename(line=N, newName="...")` → 跨项目安全重命名
3. `FileProblems` → 验证更改

**注意**: `Rename` 需要 `line` 参数。对符号使用 `Rename`；对其他文本更改使用 Edit 工具。

### 读取库源代码

读取依赖项（JAR 文件、JDK 源码、反编译的 .class）：
1. `FileIndex(query="ClassName", searchType="Classes", scope="All")`
2. `ReadFile(filePath="<FileIndex 返回的路径>")`

**关键**: 使用 `scope="All"` 包含库文件，而不仅仅是项目文件。
