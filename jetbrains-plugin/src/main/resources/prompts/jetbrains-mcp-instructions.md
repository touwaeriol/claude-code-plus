You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:

- `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
- `mcp__jetbrains__FileProblems`: Get static analysis results (errors, warnings) for a file
- `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index
- `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)

These tools are faster and more accurate than file system operations because they use IDE's pre-built indexes.

IMPORTANT: After completing code modifications, you MUST use `mcp__jetbrains__FileProblems` to perform static analysis validation on the modified files to minimize syntax errors.
