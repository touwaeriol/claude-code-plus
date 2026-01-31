/**
 * MCP 服务器默认系统提示词
 * 
 * 翻译自 JetBrains 版本的 mcp-instructions/*.md 文件
 * 
 * 这些提示词用于指导 AI 如何正确使用各个 MCP 服务器的工具
 */

/**
 * User Interaction MCP 默认提示词
 */
export const USER_INTERACTION_INSTRUCTIONS = `When you need clarification from the user, especially when presenting multiple options or choices, use the MCP server \`user_interaction\` tool \`AskUserQuestion\` to ask questions.
Tool identifiers may differ across providers. Do not assume a fixed prefix or delimiter; select the tool that matches this server + tool pair.
The user's response will be returned to you through the same tool.`

/**
 * IDE LSP MCP 默认提示词
 */
export const IDE_LSP_INSTRUCTIONS = `### When to Use

CRITICAL: For code search and file discovery, prefer IDE MCP tools over any built-in search tools:
- ALWAYS use \`CodeSearch\` instead of built-in grep/search tools
- ALWAYS use \`FileIndex\` instead of built-in glob/find tools
- Only fall back to built-in tools if IDE tools return errors

IMPORTANT: After completing code modifications, you MUST use \`FileProblems\` to validate for syntax errors.

### Refactoring Workflow

When renaming symbols:
1. \`FindUsages\` or \`CodeSearch\` → get line number
2. \`Rename(line=N, newName="...")\` → safe rename across project
3. \`FileProblems\` → validate changes

**Note**: \`Rename\` requires \`line\` parameter. Use \`Rename\` for symbols; use Edit tool for other text changes.

### Reading Library Source Code

To read dependencies (JAR files, JDK sources, decompiled .class):
1. \`FileIndex(query="ClassName", searchType="Classes", scope="All")\`
2. \`ReadFile(filePath="<path from FileIndex>")\`

**Key**: Use \`scope="All"\` to include libraries, not just project files.`

/**
 * IDE File MCP 默认提示词
 */
export const IDE_FILE_INSTRUCTIONS = `### When to Use

Use for file operations with relative path support (to project root).

**Note**: For reading library source code (JAR files, decompiled .class), use \`jetbrains / ReadFile\` from JetBrains LSP MCP instead.`

/**
 * Context7 MCP 默认提示词
 */
export const CONTEXT7_INSTRUCTIONS = `### When to Use

IMPORTANT: When working with third-party libraries, ALWAYS query Context7 first to get up-to-date documentation and prevent hallucinated APIs.

### Workflow

1. \`resolve-library-id\` → get Context7 ID (unless user provides \`/org/project\` format)
2. \`get-library-docs\` → fetch documentation`

/**
 * Terminal MCP 默认提示词
 */
export const IDE_TERMINAL_INSTRUCTIONS = `### When to Use

Use IDE's integrated terminal instead of built-in Bash tool for command execution.

### Best Practices

- **Reuse sessions**: Always reuse existing sessions via \`session_id\`
- **Multiple terminals**: Only create multiple sessions for concurrent commands (e.g., dev server + tests)
- **Cleanup**: Close sessions with \`TerminalKill\` when no longer needed`

/**
 * Git MCP 默认提示词
 */
export const IDE_GIT_INSTRUCTIONS = `### Git Commit Policy

**IMPORTANT**: Do NOT use terminal commands (git commit, git add, git push, etc.) for version control operations.
You MUST use IDE Git MCP tools instead.

### Commit Workflow

1. \`GetVcsChanges()\` → Get list of changes
2. Analyze changes, use \`SelectFiles\` / \`DeselectFiles\` to adjust file selection
3. \`SetCommitMessage()\` → Generate and fill commit message
4. **MUST** use \`AskUserQuestion\` to ask user for confirmation
5. After user confirms, call \`CommitChanges()\` to execute

### When to Use

Use for interacting with IDE's VCS/Git integration: reading changes, setting commit messages, checking status.

### File Selection Tools

- \`SelectFiles(paths, mode)\` → Select files in Commit panel (mode: "replace" or "add")
- \`DeselectFiles(paths)\` → Deselect files from Commit panel
- \`SelectAllFiles()\` → Select all changed files
- \`DeselectAllFiles()\` → Deselect all files

### Commit Message Conventions (Conventional Commits)

Follow the Conventional Commits format:

\`\`\`
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
\`\`\`

**Types**:
- \`feat\`: A new feature
- \`fix\`: A bug fix
- \`docs\`: Documentation only changes
- \`style\`: Code style changes (formatting, missing semi colons, etc)
- \`refactor\`: Code refactoring without feature change or bug fix
- \`perf\`: Performance improvements
- \`test\`: Adding or modifying tests
- \`chore\`: Build process, auxiliary tool changes, etc
- \`ci\`: CI configuration changes
- \`build\`: Build system or external dependency changes

**Examples**:
- \`feat(auth): add OAuth2 login support\`
- \`fix(api): resolve null pointer exception in user endpoint\`
- \`docs: update README with installation instructions\`
- \`refactor(core): simplify data processing logic\`

### Notes

- Always wait for user review before committing
- Use \`push=true\` in \`CommitChanges\` to commit and push in one step`

/**
 * 默认提示词映射
 */
export const MCP_DEFAULT_INSTRUCTIONS: Record<string, string> = {
  'user-interaction': USER_INTERACTION_INSTRUCTIONS,
  'ide-lsp': IDE_LSP_INSTRUCTIONS,
  'ide-file': IDE_FILE_INSTRUCTIONS,
  'context7': CONTEXT7_INSTRUCTIONS,
  'ide-terminal': IDE_TERMINAL_INSTRUCTIONS,
  'ide-git': IDE_GIT_INSTRUCTIONS,
}

/**
 * 获取指定 MCP 服务器的默认提示词
 */
export function getDefaultInstructions(mcpServerName: string): string | undefined {
  return MCP_DEFAULT_INSTRUCTIONS[mcpServerName]
}
