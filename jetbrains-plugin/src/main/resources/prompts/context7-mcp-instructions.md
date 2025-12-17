
# Context7 MCP Server Instructions

Use this server to retrieve up-to-date documentation and code examples for any library.

## Available Tools

| Tool | Description |
|------|-------------|
| `mcp__context7__resolve-library-id` | Resolves a library/package name to a Context7-compatible library ID. Call this first before fetching docs. |
| `mcp__context7__get-library-docs` | Fetches up-to-date documentation for a library. Use `mode='code'` for API references and examples, `mode='info'` for conceptual guides. |

## Usage Guidelines

1. When user asks about a library's latest API, usage patterns, or best practices, use Context7 to get current documentation
2. Always call `resolve-library-id` first to get the correct library ID, unless user provides one in `/org/project` format
3. Use `topic` parameter to focus on specific features (e.g., "hooks", "routing", "authentication")
4. Prefer `mode='code'` for implementation questions, `mode='info'` for architecture/concept questions
