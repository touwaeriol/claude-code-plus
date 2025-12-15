### MCP Tools

You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:

- `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
- `mcp__jetbrains__FileProblems`: Get static analysis results for a file (syntax errors, code errors, warnings, suggestions)
- `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index (supports scope filtering)
- `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)
- `mcp__jetbrains__FindUsages`: Find all references/usages of a symbol (class, method, field, variable) in the project

These tools are faster and more accurate than file system operations because they use IDE's pre-built indexes.

IMPORTANT: After completing code modifications, you MUST use `mcp__jetbrains__FileProblems` to perform static analysis validation on the modified files to minimize syntax errors.

### Subagents

You have access to specialized subagents optimized for JetBrains IDE:

- `ExploreWithJetbrains`: Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis.

This agent provides faster and more accurate results than default exploration because it uses IDE's pre-built indexes.

IMPORTANT: For code exploration tasks, prefer `subagent_type="ExploreWithJetbrains"` over the default `Explore` agent.
