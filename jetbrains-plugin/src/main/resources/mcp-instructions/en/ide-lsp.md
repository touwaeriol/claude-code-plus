### When to Use

CRITICAL: For code search and file discovery, prefer JetBrains MCP tools over any built-in search tools:
- ALWAYS use `CodeSearch` instead of built-in grep/search tools
- ALWAYS use `FileIndex` instead of built-in glob/find tools
- Only fall back to built-in tools if JetBrains tools return errors

IMPORTANT: After completing code modifications, you MUST use `FileProblems` to validate for syntax errors.

### Refactoring Workflow

When renaming symbols:
1. `FindUsages` or `CodeSearch` → get line number
2. `Rename(line=N, newName="...")` → safe rename across project
3. `FileProblems` → validate changes

**Note**: `Rename` requires `line` parameter. Use `Rename` for symbols; use Edit tool for other text changes.

### Reading Library Source Code

To read dependencies (JAR files, JDK sources, decompiled .class):
1. `FileIndex(query="ClassName", searchType="Classes", scope="All")`
2. `ReadFile(filePath="<path from FileIndex>")`

**Key**: Use `scope="All"` to include libraries, not just project files.
