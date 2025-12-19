# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2025-12-19

### Added
- Add copy button to UserMessageBubble and CompactSummaryCard components
- Implement height-based overflow detection for auto-collapse in user messages
- Add gradient fade effect when message content is collapsed

### Changed
- Update Java version to 21
- Centralize file sync operations in IdeaPlatformService
- Migrate TextFieldWithBrowseButton to Kotlin UI DSL 2.0 API
- Move expand/collapse button to top-right corner for better UX

### Fixed
- Resolve IDEA 2024.2-2025.2 compatibility issues (CefBrowser.openDevTools NoSuchMethodError)
- Resolve WebStorm compatibility by using reflection for Java PSI classes
- Improve IntelliJ API compatibility for Diff editor and file chooser
- Use reflection for cross-version IntelliJ Diff API compatibility
- Suppress deprecated API warning for IDEA 2024.2 compatibility
- Wait for smart mode before MCP searching to ensure index freshness

### Performance
- Optimize patchCli task to skip when enhanced CLI is up-to-date
- Save only target file instead of all documents (faster file sync)

### Build
- Add java-library plugin to ai-agent-proto for proper OrBuilder exposure
- Remove cleanCli dependency from clean task to preserve committed CLI files
- Explicitly declare protobuf-java as api dependency for OrBuilder interfaces
- Add comprehensive IDE verification matrix for CI

### Docs
- Add new screenshots to all README versions
- Add Claude Code Plus usage guide for promotion

## [1.0.9] - 2025-12-16

### Added
- Context size snapshot display on user messages (shows input tokens at send time)
- Dynamic plugin support declaration (require-restart="false")

### Changed
- Centralize version and changelog in gradle.properties and CHANGELOG.md
- Exit edit mode immediately before resending message for better UX

### Fixed
- Fix inline editor not closing after edit-and-resend
- Fix bundled CLI filename (ast-enhanced -> enhanced)

## [1.0.8] - 2025-12-15

### Added
- Dynamic MCP tool allowlist for flexible tool permissions
- SchemaValidator for MCP tool schema validation
- ResourceLoaderTest for resource loading validation
- Edit-and-resend message functionality improvements

### Changed
- Improve UserInteractionMcpServer with better question handling
- Enhance MCP tools (CodeSearch, DirectoryTree, FileProblems)
- Improve HistorySessionAction and SessionTabsAction UI
- Update tools.json schema definitions
- Session tabs animation changed from pulse to spinner effect
- Allow closing the last session tab (resets instead of removes)
- Simplify session architecture and improve history UI
- Hide edit button during streaming response

### Fixed
- Avoid calling override-only actionPerformed method directly
- Correct Logger.warn to Logger.warning
- Resolve compiler warnings in jetbrains-plugin
- Escape agents JSON for Windows command line
- Resolve IntelliJ 253 compatibility issues with optional Java plugin

### Removed
- Remove deprecated ClaudeActionHandler and SessionActionHandler
- Remove obsolete IdeActionBridge and IdeActionBridgeImpl
- Remove unused AgentDefinitionsProvider and ClaudeSessionManager
- Remove deprecated ClaudeCodeOptions typealias

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
- Switch to Node.js direct execution of official bundled cli.js (no longer fallback to global CLI)
- Improve system prompt temp file organization in dedicated subdirectory

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
