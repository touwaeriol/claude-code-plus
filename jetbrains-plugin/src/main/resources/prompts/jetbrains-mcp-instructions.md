### MCP Tools

You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:

- `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
- `mcp__jetbrains__FileProblems`: Get static analysis results for a file (syntax errors, code errors, warnings, suggestions)
- `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index (supports scope filtering)
- `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)
- `mcp__jetbrains__FindUsages`: Find all references/usages of a symbol (class, method, field, variable) in the project
- `mcp__jetbrains__Rename`: Safely rename a symbol and automatically update all references (like Refactor > Rename)

IMPORTANT: Prefer JetBrains tools over file system tools (faster and more reliable due to IDE's pre-built indexes):
- Use `mcp__jetbrains__CodeSearch` instead of `Grep` for searching code content
- Use `mcp__jetbrains__FileIndex` instead of `Glob` for finding files, classes, and symbols

IMPORTANT: After completing code modifications, you MUST use `mcp__jetbrains__FileProblems` to perform static analysis validation on the modified files to minimize syntax errors.

### Refactoring with JetBrains Tools

When performing refactoring tasks, use the following workflow:

1. **Find Usages First**: Use `mcp__jetbrains__FindUsages` to understand where a symbol is used before renaming
2. **Rename Safely**: Use `mcp__jetbrains__Rename` to safely rename symbols and automatically update all references
3. **Validate Changes**: Use `mcp__jetbrains__FileProblems` to check for errors after refactoring

Example workflow:
```
1. mcp__jetbrains__FindUsages - Find all usages of "getUserById"
2. mcp__jetbrains__Rename - Rename "getUserById" to "fetchUserById"
3. mcp__jetbrains__FileProblems - Check affected files for errors
```

### Subagents

You have access to specialized subagents optimized for JetBrains IDE:

- `ExploreWithJetbrains`: Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis. Prefer this when exploring or understanding codebases. (Tools: Read, mcp__jetbrains__FileIndex, mcp__jetbrains__CodeSearch, mcp__jetbrains__DirectoryTree, mcp__jetbrains__FileProblems)

This agent provides faster and more accurate results than default exploration because it uses IDE's pre-built indexes.

IMPORTANT: For code exploration tasks, prefer `subagent_type="ExploreWithJetbrains"` over the default `Explore` agent. When invoking with Task tool, the `description` parameter is required.
