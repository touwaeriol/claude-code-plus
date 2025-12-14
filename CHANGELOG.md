# Changelog

All notable changes to this project will be documented in this file.

## [1.0.7] - 2025-12-15

### Added
- Full Plan Mode support for implementation planning workflow
- JetBrains IDE session command and theme change notifications
- Collapsible thinking display and force send functionality
- System prompt appendix support for MCP servers
- History sessions with custom titles and enhanced UI
- RSocket connection management improvements
- JetBrains MCP Server integration

### Changed
- Migrate RPC calls from JSON to Protobuf serialization
- Update Claude CLI to 2.0.69
- Simplify project instructions (AGENTS.md, CLAUDE.md)
- Improve token stats display with cumulative statistics
- Improve error handling and interrupt message styling

### Fixed
- Register JetBrains MCP Server when client connects
- Prevent auto-scroll from overriding user scroll during streaming
- Replace internal API StartupUiUtil.isDarkTheme with public JBColor.isBright()
- Session ID resume on auto-reconnect
- Windows argument quoting for tool names with special characters

### SDK
- Add appendSystemPromptFile option for MCP system prompt appendix
- Simplify CLI discovery to only use bundled CLI
- Improve system prompt temp file organization

## [1.0.6] - 2025-12-14

### Added
- Multi-language README support (English/Chinese)
- Scroll-to-bottom button visibility improvements during streaming

### Changed
- Major UI/UX improvements and codebase refactoring
- Replace hardcoded Chinese text with English in UI components
- Tool IDEA actions now triggered only on user click
- i18n improvements for slash commands

### Fixed
- Dropdown menu theme adaptation
- Gradle task dependencies and production build optimization

### Docs
- Update system requirements to clarify bundled CLI
- Update screenshots and descriptions

## [1.0.5] - 2025-12-10

Initial stable release with core features:
- Claude AI integration in IntelliJ IDEA
- Chat interface with streaming responses
- File operations (Read, Write, Edit)
- Terminal command execution
- MCP server support
- Theme synchronization with IDE
