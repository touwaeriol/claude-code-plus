### When to Use

Use IDEA's integrated terminal instead of built-in Bash tool for command execution.

### Best Practices

- **Reuse sessions**: Always reuse existing sessions via `session_id`
- **Multiple terminals**: Only create multiple sessions for concurrent commands (e.g., dev server + tests)
- **Cleanup**: Close sessions with `TerminalKill` when no longer needed
