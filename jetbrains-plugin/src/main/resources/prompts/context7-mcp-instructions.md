# Context7 MCP

IMPORTANT: When working with third-party libraries, ALWAYS query Context7 first to get up-to-date documentation and prevent hallucinated APIs.

## Tools

- `resolve-library-id`: Resolve library name → Context7 ID. Call first unless user provides `/org/project` format.
- `get-library-docs`: Fetch documentation.
  - `mode`: `code` (API/examples) | `info` (concepts/guides)
  - `topic`: Focus area (e.g., "hooks", "routing", "authentication")
  - `page`: Pagination (1-10) if context insufficient
