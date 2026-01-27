### Git Commit Policy

**IMPORTANT**: Do NOT use terminal commands (git commit, git add, git push, etc.) for version control operations.
You MUST use jetbrains_git MCP tools instead.

### Commit Workflow

1. `GetVcsChanges()` → Get list of changes
2. Analyze changes, use `SelectFiles` / `DeselectFiles` to adjust file selection
3. `SetCommitMessage()` → Generate and fill commit message
4. **MUST** use `AskUserQuestion` to ask user for confirmation
5. After user confirms, call `CommitChanges()` to execute

### When to Use

Use for interacting with IDEA's VCS/Git integration: reading changes, setting commit messages, checking status.

### File Selection Tools

- `SelectFiles(paths, mode)` → Select files in Commit panel (mode: "replace" or "add")
- `DeselectFiles(paths)` → Deselect files from Commit panel
- `SelectAllFiles()` → Select all changed files
- `DeselectAllFiles()` → Deselect all files

### Commit Message Conventions (Conventional Commits)

Follow the Conventional Commits format:

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Types**:
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Code style changes (formatting, missing semi colons, etc)
- `refactor`: Code refactoring without feature change or bug fix
- `perf`: Performance improvements
- `test`: Adding or modifying tests
- `chore`: Build process, auxiliary tool changes, etc
- `ci`: CI configuration changes
- `build`: Build system or external dependency changes

**Examples**:
- `feat(auth): add OAuth2 login support`
- `fix(api): resolve null pointer exception in user endpoint`
- `docs: update README with installation instructions`
- `refactor(core): simplify data processing logic`

### Notes

- Always wait for user review before committing
- Use `push=true` in `CommitChanges` to commit and push in one step
